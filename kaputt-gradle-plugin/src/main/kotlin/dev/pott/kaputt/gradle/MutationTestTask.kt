package dev.pott.kaputt.gradle

import dev.pott.kaputt.compiler.MutationPluginNames
import dev.pott.kaputt.compiler.MutationPoint
import dev.pott.kaputt.compiler.MutationPointCodec
import dev.pott.kaputt.compiler.MutationTransformer
import dev.pott.kaputt.compiler.SkippedFunctionCodec
import dev.pott.kaputt.runtime.ANY_TEST
import dev.pott.kaputt.runtime.MUTATION_COVERAGE_ENV
import dev.pott.kaputt.runtime.MUTATION_DEADLINE_ENV
import dev.pott.kaputt.runtime.MutationTestRunner
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.Future

@DisableCachingByDefault(
    because = "the value of this task is the fresh verdict of every mutant run",
)
abstract class MutationTestTask : DefaultTask() {
    /** Root of the per-target mutation manifests (`<dir>/<target>/mutations.tsv`). */
    @get:Internal
    abstract val mutationDir: DirectoryProperty

    @get:Nested
    abstract val targetRuns: ListProperty<TargetRun>

    @get:Input
    @get:Optional
    abstract val timeoutSeconds: Property<Long>

    @get:Input
    abstract val timeoutFactor: Property<Double>

    @get:Input
    abstract val coverageFiltering: Property<Boolean>

    @get:Input
    abstract val baselineTimeoutSeconds: Property<Long>

    @get:Input
    abstract val stallSeconds: Property<Long>

    @get:Input
    abstract val batchSize: Property<Int>

    @get:Input
    @get:Optional
    abstract val parallelism: Property<Int>

    @get:Input
    abstract val failOnSurvived: Property<Boolean>

    @get:Input
    abstract val scoreThreshold: Property<Double>

    /** Root the SARIF/Stryker exports relativize source paths against. */
    @get:Input
    abstract val rootDirPath: Property<String>

    /** Git ref limiting the run to mutants in files changed since it. */
    @get:Input
    @get:Optional
    abstract val since: Property<String>

    /** Name-matched test classes first, full-suite re-verify for survivors. */
    @get:Input
    abstract val targetedTests: Property<Boolean>

    /** Path of the checked-in baseline of accepted (ignored) survivor ids. */
    @get:Internal
    abstract val baselinePath: Property<String>

    /** When true, rewrites the baseline with the survivors of this run. */
    @get:Internal
    abstract val updateBaseline: Property<Boolean>

    @get:OutputDirectory
    abstract val reportsDir: DirectoryProperty

    init {
        // The value of this task is a fresh verdict, never a cached report.
        outputs.upToDateWhen { false }
    }

    @get:Internal
    internal val moduleName: String = project.path

    private val rootDir by lazy { File(rootDirPath.get()) }
    private val relativePaths = HashMap<String, String>()

    @TaskAction
    fun run() {
        val reports = reportsDir.get().asFile.apply { mkdirs() }
        val logsDir = reports.resolve("logs").apply { deleteRecursively() }
        val executor = MutantExecutor(logsDir)

        val runs = targetRuns.get().map { run -> PreparedRun(run, readManifest(run)) }
        val allPoints = LinkedHashMap<String, MutationPoint>()
        runs.forEach { run -> run.points.forEach { allPoints.putIfAbsent(it.id, it) } }
        reportSkippedBySizeGuard(runs)
        val points = filterSince(allPoints)
        if (points.isEmpty()) {
            logger.lifecycle("No mutations to run for $moduleName.")
            writeReports(reports, emptyList())
            return
        }

        val activeRuns = runBaselines(runs, executor, logsDir)
        warnAboutOpaqueRunners(activeRuns, logsDir)
        if (activeRuns.isEmpty()) {
            logger.lifecycle(
                "No target of $moduleName has tests. ${points.size} mutants were generated " +
                    "but nothing can verify them.",
            )
            writeReports(reports, emptyList())
            return
        }
        logger.lifecycle(
            "Running ${points.size} mutants for $moduleName against " +
                activeRuns.joinToString(", ") { run ->
                    "${run.name} (baseline ${run.baselineMs}ms, timeout ${run.timeoutSeconds}s)"
                },
        )

        activeRuns.forEach { run ->
            val covering = run.coveringTests ?: return@forEach
            val unrun = points.count { it.id !in covering }
            val perMutant = points.mapNotNull { covering[it.id]?.size }
            logger.lifecycle(
                "  ${run.name}: $unrun of ${points.size} mutants are never executed by the " +
                    "tests and survive without being run; the rest are verified by " +
                    "${if (perMutant.isEmpty()) 0 else perMutant.average().toInt()} test " +
                    "classes on average.",
            )
        }

        val rawResults = runMutants(points, activeRuns, executor)
        val results = applyBaseline(rawResults)
        writeReports(reports, results)
        logger.lifecycle(MutationReportRenderer.consoleSummary(moduleName, results))
        logger.lifecycle("HTML report: ${reports.resolve("mutation-report.html").toURI()}")
        logger.lifecycle(
            "Also written: mutation-report.md/.json, mutations.xml (PIT/SonarQube), " +
                "mutation-report.sarif (GitHub code scanning), stryker-report.json in $reports",
        )
        enforceThresholds(results)
    }

    /** Source paths as the reports and `git` see them: relative to the root, `/`-separated. */
    private fun relativize(path: String): String =
        relativePaths.getOrPut(path) {
            File(path).relativeToOrNull(rootDir)?.path?.replace('\\', '/') ?: path
        }

    private fun filterSince(points: LinkedHashMap<String, MutationPoint>): List<MutationPoint> {
        val ref = since.orNull ?: return points.values.toList()
        val changed =
            buildSet {
                addAll(gitLines(rootDir, "git", "diff", "--name-only", ref))
                gitLines(rootDir, "git", "status", "--porcelain").forEach { line ->
                    if (line.length > 3) add(line.substring(3).trim())
                }
            }
        val filtered = points.values.filter { relativize(it.filePath) in changed }
        logger.lifecycle(
            "Incremental mode: ${filtered.size} of ${points.size} mutants are in files " +
                "changed since '$ref'.",
        )
        return filtered
    }

    private fun gitLines(
        workingDir: File,
        vararg command: String,
    ): List<String> {
        val process =
            ProcessBuilder(*command)
                .directory(workingDir)
                .redirectErrorStream(true)
                .start()
        val output = process.inputStream.bufferedReader().readText()
        if (process.waitFor() != 0) {
            throw GradleException(
                "mutation.since requires a git checkout: '${command.joinToString(" ")}' " +
                    "failed with:\n$output",
            )
        }
        return output
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
    }

    private fun reportSkippedBySizeGuard(runs: List<PreparedRun>) {
        val skipped =
            runs
                .flatMap { run -> readSkipped(run) }
                .groupBy { it.filePath to it.function }
                .map { (_, entries) -> entries.maxBy { it.skipped } }
        if (skipped.isEmpty()) return
        val total = skipped.sumOf { it.skipped }
        logger.warn(
            "Size guard: $total mutations were skipped in ${skipped.size} functions to keep " +
                "compiled methods below the JVM size limit. Raise " +
                "mutation.maxMutationsPerFunction to cover them:",
        )
        skipped.forEach { entry ->
            logger.warn(
                "  - ${entry.filePath.substringAfterLast('/')}: " +
                    "'${entry.function}' (${entry.skipped} skipped)",
            )
        }
    }

    private fun readSkipped(run: PreparedRun): List<MutationTransformer.SkippedFunction> {
        val file =
            mutationDir
                .get()
                .asFile
                .resolve(run.name)
                .resolve(MutationPluginNames.SKIPPED_FILE_NAME)
        if (!file.isFile) return emptyList()
        return SkippedFunctionCodec.decode(file.readText())
    }

    private fun applyBaseline(results: List<MutantResult>): List<MutantResult> {
        val baselineFile = File(baselinePath.get())
        if (updateBaseline.get()) {
            val survivorIds =
                results
                    .filter { it.status == MutantStatus.SURVIVED }
                    .map { it.point.id }
                    .sorted()
            baselineFile.writeText(MutationBaseline.render(survivorIds))
            logger.lifecycle(
                "Baseline updated: ${survivorIds.size} survivors written to $baselineFile",
            )
            return results
        }
        if (!baselineFile.isFile) return results
        return MutationBaseline.apply(results, MutationBaseline.parse(baselineFile.readText()))
    }

    private fun readManifest(run: TargetRun): List<MutationPoint> {
        val targetName = run.targetName.get()
        val manifest =
            mutationDir
                .get()
                .asFile
                .resolve(run.manifestName.getOrElse(targetName))
                .resolve(MutationPluginNames.MUTATIONS_FILE_NAME)
        if (!manifest.isFile) {
            throw GradleException(
                "No mutation manifest for target '$targetName' of $moduleName " +
                    "(${manifest.absolutePath}). Its main compilation was not instrumented, " +
                    "so the target would verify nothing. Run with --rerun-tasks, or remove " +
                    "'$targetName' from the mutation targets.",
            )
        }
        return MutationPointCodec.decode(manifest.readText())
    }

    /**
     * Runs a target's suite once. A jvm abort is retried, because a mutant can
     * leave native state that crashes an unrelated later run, and losing the
     * baseline aborts the whole module.
     */
    private fun baseline(
        run: PreparedRun,
        executor: MutantExecutor,
        logsDir: File,
    ): ProcessOutcome {
        val first = runBaselineOnce(run, executor, logsDir)
        if (first.exitCode == 0 || first.timedOut) return first
        logger.warn(
            "Baseline for target '${run.name}' of $moduleName exited ${first.exitCode}, retrying.",
        )
        return runBaselineOnce(run, executor, logsDir)
    }

    private fun runBaselineOnce(
        run: PreparedRun,
        executor: MutantExecutor,
        logsDir: File,
    ): ProcessOutcome =
        executor.execute(
            command = run.command(mutationId = null),
            environment = run.environment(mutationId = null) + run.coverageEnvironment(logsDir),
            logName = "baseline-${run.name}",
            timeoutSeconds = baselineTimeoutSeconds.get(),
            // A false stall would abort the module, so the baseline is guarded by
            // the cap alone.
            stallSeconds = 0,
        )

    /**
     * Coverage cannot see tests a runner executes in its own classloader, so
     * mutants only those tests reach would be filtered away as unreachable
     * and reported as survived. Filtering has to be off for such a module.
     */
    private fun warnAboutOpaqueRunners(
        runs: List<PreparedRun>,
        logsDir: File,
    ) {
        if (!coverageFiltering.get()) return
        val runners =
            runs
                .mapNotNull { run -> logsDir.resolve("baseline-${run.name}.log").takeIf(File::isFile) }
                .flatMap { log -> log.readLines() }
                .filter { it.startsWith(MutationTestRunner.OPAQUE_RUNNER_PREFIX) }
                .map { it.removePrefix(MutationTestRunner.OPAQUE_RUNNER_PREFIX).trim() }
                .distinct()
        if (runners.isEmpty()) return
        logger.warn(
            "$moduleName runs tests with $runners, whose execution coverage cannot see. " +
                "Mutants those tests reach are reported as survived without running. " +
                "Set coverageFiltering to false for this module.",
        )
    }

    /**
     * Baselines are one full test-suite run per target and independent of each
     * other, so they run side by side; their verdicts are evaluated in target
     * order afterwards.
     */
    private fun runBaselines(
        runs: List<PreparedRun>,
        executor: MutantExecutor,
        logsDir: File,
    ): List<PreparedRun> {
        if (runs.isEmpty()) return emptyList()
        val pool = Executors.newFixedThreadPool(runs.size.coerceAtMost(MAX_BASELINE_THREADS))
        val outcomes =
            try {
                runs
                    .map { run ->
                        pool.submit(
                            Callable {
                                baseline(run, executor, logsDir)
                            },
                        )
                    }.map(Future<ProcessOutcome>::get)
            } finally {
                pool.shutdown()
            }
        return runs.zip(outcomes).mapNotNull { (run, outcome) ->
            when {
                run.kind == TargetRun.TargetKind.JVM &&
                    outcome.exitCode == MutationTestRunner.EXIT_NO_TESTS -> {
                    logger.warn(
                        "Target '${run.name}' has no tests, skipping it.",
                    )
                    null
                }

                outcome.timedOut || outcome.exitCode != 0 -> {
                    throw GradleException(
                        "Mutation testing aborted: the baseline run for target '${run.name}' of " +
                            "$moduleName did not pass (exit code ${outcome.exitCode}, timed out: " +
                            "${outcome.timedOut}). See ${logsDir.resolve("baseline-${run.name}.log")}",
                    )
                }

                else -> {
                    run.also {
                        it.readCoverage(logsDir)
                        it.baselineMs = outcome.durationMs
                        it.timeoutSeconds = timeoutSeconds.orNull ?: MutationTimeout.derive(
                            baselineMs = outcome.durationMs,
                            factor = timeoutFactor.get(),
                        )
                    }
                }
            }
        }
    }

    /**
     * Verifies a group of mutants in one fork. A fork that dies part-way, from
     * a crash or a hang, reports verdicts only up to that
     * point; the rest are returned unverified so the caller can retry them one
     * at a time, so batching never loses or invents a result.
     */
    private fun runBatch(
        run: PreparedRun,
        entries: List<MutationBatch.Entry>,
        executor: MutantExecutor,
    ): BatchRun {
        val batchFile = temporaryDir.resolve("batch-${run.name}-${entries.first().id}.tsv")
        batchFile.writeText(MutationBatch.fileContent(entries))
        val logName = "batch-${run.name}-${entries.first().id}"
        val outcome =
            executor.execute(
                command = run.command(mutationId = null, batchFile = batchFile),
                environment = run.environment(null),
                logName = logName,
                timeoutSeconds = run.timeoutSeconds * entries.size,
                stallSeconds = stallSeconds.get(),
            )
        val log = reportsDir.get().asFile.resolve("logs/$logName.log")
        val text = log.takeIf(File::isFile)?.readText().orEmpty()
        val statuses =
            MutationBatch.parseVerdicts(text).mapValues { (_, code) ->
                MutationScore.classify(run.kind, code, timedOut = false)
            }
        return BatchRun(
            statuses = statuses,
            durationMs = outcome.durationMs,
            outcome = outcome,
            tail = MutationBatch.tailAfterLastVerdict(text),
        )
    }

    /** What one fork over a group of mutants reported before it ended. */
    private data class BatchRun(
        val statuses: Map<String, MutantStatus>,
        val durationMs: Long,
        val outcome: ProcessOutcome,
        val tail: String,
    )

    private fun runMutants(
        points: List<MutationPoint>,
        runs: List<PreparedRun>,
        executor: MutantExecutor,
    ): List<MutantResult> {
        // Forks spend most of their time in per-test-class setup rather than on
        // cpu, so oversubscribing the cores pays.
        val workers =
            when {
                runs.any { it.kind == TargetRun.TargetKind.ANDROID_DEVICE } -> {
                    1
                }

                else -> {
                    parallelism.orNull
                        ?: (Runtime.getRuntime().availableProcessors() * 3 / 2).coerceAtLeast(1)
                }
            }
        val pool = Executors.newFixedThreadPool(workers)
        try {
            val completion = ExecutorCompletionService<List<MutantResult>>(pool)
            val groups = batchGroups(points, runs)
            groups.forEach { group ->
                completion.submit(Callable { verify(group, runs, executor) })
            }
            var reported = 0
            return groups.indices.flatMap {
                val results = completion.take().get()
                results.map { result ->
                    reported++
                    logger.lifecycle(
                        "[$reported/${points.size}] ${result.status} " +
                            "${result.point.filePath.substringAfterLast(
                                '/',
                            )}:${result.point.line} " +
                            result.point.description,
                    )
                    result
                }
            }
        } finally {
            pool.shutdownNow()
        }
    }

    /**
     * Splits the mutants into fork-sized groups. Only mutants a single jvm run
     * can verify from coverage are batched; anything needing the whole suite,
     * a native target or a name-matched fallback stays on its own.
     */
    private fun batchGroups(
        points: List<MutationPoint>,
        runs: List<PreparedRun>,
    ): List<List<MutationPoint>> {
        val size = batchSize.get()
        val jvmRun = runs.singleOrNull()?.takeIf { it.kind == TargetRun.TargetKind.JVM }
        val covering = jvmRun?.coveringTests
        if (size <= 1 || covering == null) return points.map(::listOf)
        val (batchable, single) =
            points.partition { point ->
                covering[point.id]?.let { ANY_TEST !in it && it.isNotEmpty() } == true
            }
        return batchable
            .sortedBy { point -> MutationBatch.groupKey(covering[point.id].orEmpty()) }
            .chunked(size) + single.map(::listOf)
    }

    /** Runs a group as one fork, retrying anything it did not report alone. */
    private fun verify(
        group: List<MutationPoint>,
        runs: List<PreparedRun>,
        executor: MutantExecutor,
    ): List<MutantResult> {
        if (group.size == 1) return listOf(runMutant(group.single(), runs, executor))
        val run = runs.single()
        val results = mutableListOf<MutantResult>()
        var remaining = group
        while (remaining.isNotEmpty()) {
            val entries =
                remaining.map { point ->
                    MutationBatch.Entry(
                        point.id,
                        run.coveringTests
                            ?.get(point.id)
                            .orEmpty()
                            .toList(),
                    )
                }
            val (statuses, durationMs, outcome, tail) = runBatch(run, entries, executor)
            val reported = remaining.takeWhile { statuses.containsKey(it.id) }
            val perMutant = durationMs / remaining.size.coerceAtLeast(1)
            reported.forEach { point ->
                results += MutantResult(point, statuses.getValue(point.id), perMutant)
            }
            val rest = remaining.drop(reported.size)
            if (rest.isEmpty()) break
            // The fork died on the first mutant it never reported, from a hang or
            // a jvm abort. Both count as detected; the rest are re-batched.
            val culprit = rest.first()
            results +=
                MutantResult(
                    point = culprit,
                    status =
                        MutationScore.classify(
                            kind = run.kind,
                            exitCode = outcome.exitCode,
                            timedOut = outcome.timedOut,
                            log = tail,
                        ),
                    durationMs = perMutant,
                )
            remaining = rest.drop(1)
        }
        return results
    }

    private fun runMutant(
        point: MutationPoint,
        runs: List<PreparedRun>,
        executor: MutantExecutor,
    ): MutantResult {
        val containingRuns = runs.filter { run -> point.id in run.pointIds }
        var totalDuration = 0L
        val statuses = mutableListOf<MutantStatus>()
        for (run in containingRuns) {
            val covering = run.coveringTests?.get(point.id)
            if (run.coveringTests != null && covering == null) {
                // Never executed by any test, so nothing can detect it.
                statuses += MutantStatus.SURVIVED
                continue
            }
            // Exactly the tests that executed this mutation point. Anything
            // else cannot reach the mutated code, so there is no full-suite
            // pass to fall back to. ANY_TEST means the point was also reached
            // outside a test and attribution is incomplete, so run everything.
            val selection =
                covering
                    ?.takeIf { tests -> ANY_TEST !in tests }
                    ?.toList()
            var status: MutantStatus? = null
            if (selection != null && run.kind == TargetRun.TargetKind.JVM) {
                val outcome =
                    executor.execute(
                        command = run.command(point.id, includePatterns = selection),
                        environment = run.environment(point.id),
                        logName = "${point.id}-${run.name}",
                        timeoutSeconds = run.timeoutSeconds,
                        stallSeconds = stallSeconds.get(),
                    )
                totalDuration += outcome.durationMs
                statuses += statusOf(run, outcome, "${point.id}-${run.name}")
                if (statuses.last() == MutantStatus.KILLED) break else continue
            }
            if (targetedTests.get() && run.kind == TargetRun.TargetKind.JVM) {
                val targeted =
                    executor.execute(
                        command = run.command(point.id, testFilter = point.testFilter()),
                        environment = run.environment(point.id),
                        logName = "${point.id}-${run.name}-targeted",
                        timeoutSeconds = run.timeoutSeconds,
                        stallSeconds = stallSeconds.get(),
                    )
                totalDuration += targeted.durationMs
                val fast = statusOf(run, targeted, "${point.id}-${run.name}-targeted")
                // A targeted kill is final. A targeted survival is only a hint, so
                // the full suite decides and results match a full run.
                if (fast != MutantStatus.SURVIVED) status = fast
            }
            if (status == null) {
                val outcome =
                    executor.execute(
                        command = run.command(point.id),
                        environment = run.environment(point.id),
                        logName = "${point.id}-${run.name}",
                        timeoutSeconds = run.timeoutSeconds,
                        stallSeconds = run.stallSeconds(stallSeconds.get()),
                    )
                totalDuration += outcome.durationMs
                status = statusOf(run, outcome, "${point.id}-${run.name}")
            }
            statuses += status
            if (status == MutantStatus.KILLED) break
        }
        return MutantResult(
            point = point,
            status = MutationScore.combine(statuses),
            durationMs = totalDuration,
        )
    }

    private fun statusOf(
        run: PreparedRun,
        outcome: ProcessOutcome,
        logName: String,
    ): MutantStatus {
        val log = reportsDir.get().asFile.resolve("logs/$logName.log")
        return MutationScore.classify(
            kind = run.kind,
            exitCode = outcome.exitCode,
            timedOut = outcome.timedOut,
            log = log.takeIf(File::isFile)?.readText(),
        )
    }

    private fun MutationPoint.testFilter(): String = filePath.substringAfterLast('/').removeSuffix(".kt")

    private fun writeReports(
        reports: File,
        results: List<MutantResult>,
    ) {
        reports
            .resolve("mutation-report.md")
            .writeText(MutationReportRenderer.markdown(moduleName, results))
        reports
            .resolve("mutation-report.json")
            .writeText(MutationReportRenderer.json(moduleName, results))
        val sourceCache = HashMap<String, List<String>?>()
        val sourceLookup: (String) -> List<String>? = { path ->
            sourceCache.getOrPut(path) {
                File(path).takeIf(File::isFile)?.readLines()
            }
        }
        reports
            .resolve("mutation-report.html")
            .writeText(MutationHtmlReport.render(moduleName, results, sourceLookup))
        reports
            .resolve("mutation-report.sarif")
            .writeText(
                MutationSarifReport.render(results, MutationGradlePlugin.VERSION, ::relativize),
            )
        reports
            .resolve("stryker-report.json")
            .writeText(MutationStrykerReport.render(results, sourceLookup, ::relativize))
        reports
            .resolve("mutations.xml")
            .writeText(MutationPitXmlReport.render(results))
    }

    private fun enforceThresholds(results: List<MutantResult>) {
        val survivors = results.count { it.status == MutantStatus.SURVIVED }
        val score = MutationScore.score(results)
        if (failOnSurvived.get() && survivors > 0) {
            throw GradleException(
                "$survivors mutants survived without the tests detecting them. " +
                    "See the survivors table in the mutation report.",
            )
        }
        val threshold = scoreThreshold.get()
        if (score < threshold) {
            throw GradleException(
                "Mutation score ${MutationScore.format(
                    score,
                )}% is below the threshold of $threshold%.",
            )
        }
    }

    private inner class PreparedRun(
        run: TargetRun,
        val points: List<MutationPoint>,
    ) {
        val name: String = run.targetName.get()
        val kind: TargetRun.TargetKind = run.kind.get()
        val pointIds: Set<String> = points.mapTo(HashSet()) { it.id }
        var baselineMs: Long = 0
        var timeoutSeconds: Long = 0

        /** Mutation id to the tests that executed it; null when not collected. */
        var coveringTests: Map<String, Set<String>>? = null

        /** Device runs report only when they finish, so progress says nothing. */
        fun stallSeconds(configured: Long): Long = if (kind == TargetRun.TargetKind.ANDROID_DEVICE) 0 else configured

        fun coverageEnvironment(logsDir: File): Map<String, String> =
            if (kind == TargetRun.TargetKind.JVM && coverageFiltering.get()) {
                mapOf(MUTATION_COVERAGE_ENV to coverageFile(logsDir).absolutePath)
            } else {
                emptyMap()
            }

        fun readCoverage(logsDir: File) {
            if (kind != TargetRun.TargetKind.JVM || !coverageFiltering.get()) return
            val file = coverageFile(logsDir)
            if (!file.isFile) return
            coveringTests =
                file
                    .readLines()
                    .filter(String::isNotBlank)
                    .associate { line ->
                        val id = line.substringBefore('\t')
                        val tests =
                            line
                                .substringAfter('\t', "")
                                .split(',')
                                .filter(String::isNotBlank)
                                .toSet()
                        id to tests
                    }
        }

        private fun coverageFile(logsDir: File): File = logsDir.resolve("coverage-$name.txt")

        private val crashDir: File =
            reportsDir
                .get()
                .asFile
                .resolve("crashes")
                .apply { mkdirs() }
        private val testEnvironment: Map<String, String> = run.environment.get()
        val classpathNames: List<String> = run.classpath.files.map { it.name }

        private val simulatorDevice: String? = run.simulatorDevice.orNull
        private val adbPath: String? = run.adbPath.orNull
        private val deviceSerial: String? =
            run.deviceSerial.orNull
                ?: adbPath?.let { adb ->
                    val devices = AndroidInstrumentation.connectedDevices(adb)
                    check(devices.size == 1) {
                        "Instrumented mutation testing needs exactly one device, found " +
                            "${devices.size} (${devices.joinToString()}). Name one with " +
                            "mutation { deviceSerial.set(\"...\") }."
                    }
                    devices.single()
                }
        private val instrumentationTarget: String? by lazy {
            adbPath?.let { adb ->
                AndroidInstrumentation.resolveTarget(
                    adb = adb,
                    deviceSerial = deviceSerial,
                    testPackage =
                        AndroidInstrumentation.testPackage(
                            run.androidTestOutputs.files.first(),
                        ),
                )
            }
        }
        private val binary: File? = run.testBinary.files.firstOrNull()
        private val javaLauncher: JavaLauncher? = run.javaLauncher.orNull
        private val argFile: File? =
            if (kind == TargetRun.TargetKind.JVM) {
                JvmTestCommand.writeArgFile(
                    argFile = temporaryDir.resolve("runner-$name.args"),
                    classpath = run.classpath.files.toList(),
                    testClassesDirs = run.testClassesDirs.files.toList(),
                    jvmArgs = run.jvmArgs.get(),
                    includePatterns = run.includePatterns.get(),
                    excludePatterns = run.excludePatterns.get(),
                )
            } else {
                null
            }

        fun command(
            mutationId: String?,
            testFilter: String? = null,
            includePatterns: List<String> = emptyList(),
            batchFile: File? = null,
        ): List<String> =
            when (kind) {
                TargetRun.TargetKind.JVM -> {
                    JvmTestCommand.command(
                        javaExecutable =
                            checkNotNull(javaLauncher) {
                                "No Java launcher was configured for target '$name'"
                            }.executablePath.asFile.absolutePath,
                        argFile = checkNotNull(argFile),
                        mutationId = mutationId,
                        testFilter = testFilter,
                        includePatterns = includePatterns,
                        batchFile = batchFile,
                        errorFileDir = crashDir,
                    )
                }

                TargetRun.TargetKind.ANDROID_DEVICE -> {
                    AndroidInstrumentation.command(
                        adb = checkNotNull(adbPath) { "No adb configured for target '$name'" },
                        deviceSerial = deviceSerial,
                        target = checkNotNull(instrumentationTarget),
                        mutationId = mutationId,
                    )
                }

                else -> {
                    NativeTestCommand.command(
                        binary = checkNotNull(binary) { "No test binary for target '$name'" },
                        simulatorDevice = simulatorDevice,
                    )
                }
            }

        /** Ends a runaway mutant from inside the loop it broke. */
        private fun deadlineEnvironment(): Map<String, String> =
            if (timeoutSeconds > 0) {
                mapOf(MUTATION_DEADLINE_ENV to (timeoutSeconds * MILLIS_PER_SECOND).toString())
            } else {
                emptyMap()
            }

        fun environment(mutationId: String?): Map<String, String> =
            when (kind) {
                TargetRun.TargetKind.JVM -> {
                    testEnvironment + deadlineEnvironment()
                }

                TargetRun.TargetKind.ANDROID_DEVICE -> {
                    emptyMap()
                }

                else -> {
                    NativeTestCommand.environment(
                        mutationId = mutationId,
                        simulator = kind == TargetRun.TargetKind.NATIVE_SIMULATOR,
                    ) + deadlineEnvironment()
                }
            }
    }

    companion object {
        private const val MILLIS_PER_SECOND = 1000L
        private const val MAX_BASELINE_THREADS = 4
    }
}
