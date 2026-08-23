package dev.pott.kaputt.gradle

import dev.pott.kaputt.compiler.MutationPluginNames
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.konan.target.HostManager
import java.io.File

/**
 * Applies the mutation compiler plugin to the main compilations of every jvm
 * and native Kotlin target and registers the `mutationTest` task.
 * Instrumentation only happens in invocations that actually run
 * `mutationTest` (or set `-Pmutation.enabled=true`), so regular builds
 * compile untouched code.
 */
class MutationGradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) {
        target.extensions.create(EXTENSION_NAME, MutationExtension::class.java).apply {
            failOnSurvived.convention(false)
            scoreThreshold.convention(0.0)
            targetedTests.convention(false)
            instrumentAndroid.convention(false)
            coverageFiltering.convention(true)
            timeoutFactor.convention(DEFAULT_TIMEOUT_FACTOR)
            stallSeconds.convention(DEFAULT_STALL_SECONDS)
            batchSize.convention(DEFAULT_BATCH_SIZE)
            baselineTimeoutSeconds.convention(DEFAULT_BASELINE_TIMEOUT_SECONDS)
        }
        if (target === target.rootProject) {
            target.subprojects { subproject ->
                subproject.pluginManager.apply(MutationGradlePlugin::class.java)
            }
        }
        registerMutationTestTasks(target)
        if (target.isMutationRequested()) {
            // The manifest is written while compiling, so a cached, up-to-date
            // or incremental compile leaves the run with no mutants. It cannot
            // be declared as a task output: every compilation of the module
            // would claim the same directory.
            val mutationDir =
                target.layout.buildDirectory
                    .dir(MUTATION_DIR)
                    .get()
                    .asFile
            val sourceDir = target.file("src")
            target.tasks.configureEach { task ->
                if (task is org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>) {
                    task.outputs.cacheIf("mutation instrumentation is active") { false }
                    task.outputs.upToDateWhen { manifestsNewerThanSources(mutationDir, sourceDir) }
                    (task as? KotlinCompile)?.incremental = false
                }
            }
        }
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        val project = kotlinCompilation.target.project
        if (!project.isMutationRequested()) return false
        if (kotlinCompilation.platformType == KotlinPlatformType.androidJvm) {
            // An Android-only project has no other target to fall back to, so
            // its debug compilation is instrumented without opting in. In a
            // multiplatform module the jvm target already covers common code.
            if (project.isAndroidOnly()) return kotlinCompilation.name == project.androidVariant()
            val extension = project.extensions.findByType(MutationExtension::class.java)
            return extension?.instrumentAndroid?.getOrElse(false) == true &&
                kotlinCompilation.name in ANDROID_MAIN_COMPILATIONS
        }
        return kotlinCompilation.name == KotlinCompilation.MAIN_COMPILATION_NAME &&
            kotlinCompilation.platformType in INSTRUMENTED_PLATFORMS &&
            project.isSelectedTarget(kotlinCompilation.target)
    }

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        kotlinCompilation.dependencies {
            implementation("$GROUP:kaputt-runtime:$VERSION")
        }
        val extension = project.extensions.getByType(MutationExtension::class.java)
        val compilationSuffix =
            kotlinCompilation.name
                .takeIf { it != KotlinCompilation.MAIN_COMPILATION_NAME }
                ?.let { "-$it" }
                .orEmpty()
        val buildDir = project.layout.buildDirectory
        val outputDir =
            buildDir
                .dir("$MUTATION_DIR/${kotlinCompilation.target.mutationDirName()}$compilationSuffix")
        val targetProperty = project.providers.gradleProperty(TARGET_PROPERTY)
        return project.provider {
            buildList {
                add(SubpluginOption("enabled", "true"))
                add(SubpluginOption("outputDir", outputDir.get().asFile.absolutePath))
                val includes = extension.includes.get() + listOfNotNull(targetProperty.orNull)
                includes.forEach { add(SubpluginOption("includes", it)) }
                // Everything under the build directory is generated code, which
                // no test should be expected to pin down.
                val excludes =
                    extension.excludes.get() +
                        "${buildDir.get().asFile.absolutePath}/*"
                excludes.forEach { add(SubpluginOption("excludes", it)) }
                val operators = extension.operators.get()
                operators.forEach { add(SubpluginOption("operators", it)) }
                extension.maxMutationsPerFunction.orNull?.let { cap ->
                    add(SubpluginOption("maxMutationsPerFunction", cap.toString()))
                }
            }
        }
    }

    override fun getCompilerPluginId(): String = MutationPluginNames.PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact(groupId = GROUP, artifactId = "kaputt-compiler-plugin", version = VERSION)

    private fun registerMutationTestTasks(project: Project) {
        project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            registerMutationTestTask(project) { task, extension ->
                val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
                selectTargets(project, extension, kotlin.targets.toList()).forEach { kotlinTarget ->
                    task.targetRuns.add(buildTargetRun(project, task, kotlinTarget))
                }
            }
        }
        project.pluginManager.withPlugin("org.jetbrains.kotlin.android") {
            registerMutationTestTask(project) { task, extension ->
                val variant = project.androidVariant()
                if (extension.instrumentedTests.getOrElse(false)) {
                    task.targetRuns.add(androidDeviceTargetRun(project, task, variant, extension))
                    return@registerMutationTestTask
                }
                task.targetRuns.add(
                    jvmTargetRun(
                        project,
                        targetName = "jvm-$variant",
                        testTaskName = project.androidTestTask(variant),
                    ),
                )
            }
        }
        project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            registerMutationTestTask(project) { task, _ ->
                val kotlin =
                    project.extensions
                        .getByType(
                            org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension::class.java,
                        )
                task.targetRuns.add(
                    jvmTargetRun(
                        project,
                        targetName = kotlin.target.mutationDirName(),
                        testTaskName = "test",
                    ),
                )
            }
        }
    }

    private fun registerMutationTestTask(
        project: Project,
        configureRuns: (MutationTestTask, MutationExtension) -> Unit,
    ) {
        if (TASK_NAME in project.tasks.names) return
        val extension = project.extensions.getByType(MutationExtension::class.java)
        val runLock =
            project.gradle.sharedServices.registerIfAbsent(
                "mutationRunLock",
                MutationRunLock::class.java,
            ) { spec -> spec.maxParallelUsages.set(1) }
        project.tasks.register(TASK_NAME, MutationTestTask::class.java) { task ->
            warnOnUnsupportedKotlin(project)
            task.usesService(runLock)
            task.group = "verification"
            task.description =
                "Runs the Kotlin test suites once per generated mutant and reports undetected mutations."
            task.mutationDir.set(project.layout.buildDirectory.dir(MUTATION_DIR))
            task.timeoutSeconds.set(extension.timeoutSeconds)
            task.timeoutFactor.set(extension.timeoutFactor)
            task.stallSeconds.set(extension.stallSeconds)
            task.batchSize.set(
                project.providers
                    .gradleProperty(BATCH_SIZE_PROPERTY)
                    .map(String::toInt)
                    .orElse(extension.batchSize),
            )
            task.baselineTimeoutSeconds.set(extension.baselineTimeoutSeconds)
            task.coverageFiltering.set(
                project.providers
                    .gradleProperty(COVERAGE_FILTERING_PROPERTY)
                    .map { it != "false" }
                    .orElse(extension.coverageFiltering),
            )
            task.parallelism.set(
                project.providers
                    .gradleProperty(PARALLELISM_PROPERTY)
                    .map(String::toInt)
                    .orElse(extension.parallelism),
            )
            task.failOnSurvived.set(extension.failOnSurvived)
            task.scoreThreshold.set(extension.scoreThreshold)
            task.reportsDir.set(project.layout.buildDirectory.dir("reports/mutation-test"))
            task.rootDirPath.set(project.rootDir.absolutePath)
            task.since.set(
                project.providers.gradleProperty(SINCE_PROPERTY).orElse(extension.since),
            )
            task.targetedTests.set(
                project.providers
                    .gradleProperty(TARGETED_PROPERTY)
                    .map { it != "false" }
                    .orElse(extension.targetedTests),
            )
            task.baselinePath.set(
                project.layout.projectDirectory
                    .file(BASELINE_FILE_NAME)
                    .asFile.absolutePath,
            )
            task.updateBaseline.set(
                project.providers
                    .gradleProperty(UPDATE_BASELINE_PROPERTY)
                    .map { it != "false" }
                    .orElse(false),
            )
            configureRuns(task, extension)
            extension.additionalJvmTestTasks.get().forEach { (targetName, testTaskName) ->
                task.targetRuns.add(jvmTargetRun(project, targetName, testTaskName))
            }
        }
    }

    /**
     * Instrumenting a target also puts the runtime on its compile classpath,
     * so a target that will not be verified is left alone: a project can then
     * declare platforms the runtime is not published for.
     */
    private fun Project.isSelectedTarget(target: KotlinTarget): Boolean {
        val requested =
            requestedTargetNames(
                this,
                extensions.findByType(MutationExtension::class.java),
            )
        return requested?.contains(target.name)
            ?: (target.platformType == KotlinPlatformType.jvm)
    }

    private fun requestedTargetNames(
        project: Project,
        extension: MutationExtension?,
    ): List<String>? =
        project.providers
            .gradleProperty(TARGETS_PROPERTY)
            .orNull
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?: extension?.targets?.get()?.ifEmpty { null }

    /**
     * Default selection: every jvm target. Native targets are opt-in via the
     * `mutation { targets }` extension or `-Pmutation.targets=jvm,iosSimulatorArm64`
     * because each mutant re-runs their test binaries too; targets whose test
     * binary cannot run on the current host are skipped with a warning.
     */
    private fun selectTargets(
        project: Project,
        extension: MutationExtension,
        targets: List<KotlinTarget>,
    ): List<KotlinTarget> {
        val requested = requestedTargetNames(project, extension)
        val candidates =
            if (requested == null) {
                targets.filter { it.platformType == KotlinPlatformType.jvm }
            } else {
                requested.map { name ->
                    targets.find { it.name == name }
                        ?: throw IllegalArgumentException(
                            "Unknown mutation target '$name' in ${project.path}. " +
                                "Known targets: ${targets.joinToString { it.name }}",
                        )
                }
            }
        return candidates.filter { kotlinTarget ->
            when {
                kotlinTarget.platformType == KotlinPlatformType.jvm -> {
                    true
                }

                kotlinTarget is KotlinNativeTarget &&
                    HostManager().isEnabled(kotlinTarget.konanTarget) -> {
                    true
                }

                else -> {
                    project.logger.warn(
                        "Skipping mutation target '${kotlinTarget.name}': " +
                            "its tests cannot run on this host.",
                    )
                    false
                }
            }
        }
    }

    private fun buildTargetRun(
        project: Project,
        task: MutationTestTask,
        kotlinTarget: KotlinTarget,
    ): TargetRun =
        when {
            kotlinTarget.platformType == KotlinPlatformType.jvm -> {
                jvmTargetRun(project, kotlinTarget.mutationDirName(), "${kotlinTarget.name}Test")
            }

            kotlinTarget is KotlinNativeTarget -> {
                nativeTargetRun(project, task, kotlinTarget)
            }

            else -> {
                error("Unsupported mutation target '${kotlinTarget.name}'")
            }
        }

    /**
     * Verifies mutants against the instrumented tests of a variant. Both apks
     * carry every mutant, so they are installed once and each run only names
     * the mutant to activate.
     */
    private fun androidDeviceTargetRun(
        project: Project,
        task: MutationTestTask,
        variant: String,
        extension: MutationExtension,
    ): TargetRun {
        val capitalised = variant.replaceFirstChar(Char::uppercase)
        val installTest = "install${capitalised}AndroidTest"
        check(installTest in project.tasks.names) {
            "No task '$installTest' in ${project.path}. Instrumented mutation testing needs " +
                "androidTest sources for variant '$variant'."
        }
        task.dependsOn(installTest)
        // A library instruments itself and has no application apk to install.
        val installApp = "install$capitalised"
        if (installApp in project.tasks.names) task.dependsOn(installApp)

        val run = project.objects.newInstance(TargetRun::class.java)
        run.targetName.set("android-$variant")
        run.kind.set(TargetRun.TargetKind.ANDROID_DEVICE)
        // Instrumented tests verify the same compilation the unit tests do.
        run.manifestName.set("jvm-$variant")
        run.adbPath.set(
            project.provider { AndroidInstrumentation.adbExecutable(project.androidSdkDir()) },
        )
        run.deviceSerial.set(extension.deviceSerial)
        run.androidTestOutputs.from(
            project.layout.buildDirectory.dir("outputs/apk/androidTest"),
        )
        return run
    }

    /** The SDK the Android plugin already resolved for this build. */
    private fun Project.androidSdkDir(): File {
        val configured =
            providers.gradleProperty("android.sdk.dir").orNull
                ?: rootProject
                    .file("local.properties")
                    .takeIf(File::isFile)
                    ?.readLines()
                    ?.firstOrNull { it.startsWith("sdk.dir=") }
                    ?.substringAfter('=')
                ?: System.getenv("ANDROID_HOME")
                ?: System.getenv("ANDROID_SDK_ROOT")
        return File(
            checkNotNull(configured) {
                "No Android SDK found: set sdk.dir in local.properties or ANDROID_HOME."
            },
        )
    }

    private fun jvmTargetRun(
        project: Project,
        targetName: String,
        testTaskName: String,
    ): TargetRun {
        val run = project.objects.newInstance(TargetRun::class.java)
        run.targetName.set(targetName)
        run.kind.set(TargetRun.TargetKind.JVM)
        // Reading through a provider drops the task provenance, so mutationTest
        // depends on the compile tasks behind the classpath rather than on a
        // full test run. Its baseline covers what that run would have verified.
        val testTask = project.tasks.named(testTaskName, Test::class.java)
        run.classpath.from(project.provider { testTask.get().classpath })
        run.testClassesDirs.from(project.provider { testTask.get().testClassesDirs })
        // allJvmArgs, because Gradle renders immutable system properties such
        // as the locale only here. The systemProperties map alone drops them.
        run.jvmArgs.set(project.provider { jvmArgumentsOf(testTask.get()) })
        run.environment.set(
            project.provider {
                testTask.get().environment.mapValues { (_, value) -> value?.toString().orEmpty() }
            },
        )
        run.includePatterns.set(
            project.provider {
                testTask
                    .get()
                    .filter.includePatterns
                    .toList()
            },
        )
        run.excludePatterns.set(
            project.provider {
                testTask
                    .get()
                    .filter.excludePatterns
                    .toList()
            },
        )
        run.javaLauncher.set(project.provider { testTask.get().javaLauncher.get() })
        return run
    }

    /**
     * Gradle's own fork arguments, minus the classpath it passes separately and
     * minus coverage agents: line coverage is meaningless for a mutant run, and
     * re-instrumenting every class in every fork dominated the runtime.
     */
    private fun jvmArgumentsOf(test: Test): List<String> {
        val args = test.allJvmArgs
        val filtered = mutableListOf<String>()
        var index = 0
        while (index < args.size) {
            val arg = args[index]
            when {
                arg == "-cp" || arg == "-classpath" -> {
                    index += 2
                }

                arg.isCoverageAgent() -> {
                    index++
                }

                else -> {
                    filtered += arg
                    index++
                }
            }
        }
        return filtered
    }

    private fun String.isCoverageAgent(): Boolean = startsWith("-javaagent:") && COVERAGE_AGENTS.any { it in lowercase() }

    private fun nativeTargetRun(
        project: Project,
        task: MutationTestTask,
        kotlinTarget: KotlinNativeTarget,
    ): TargetRun {
        val run = project.objects.newInstance(TargetRun::class.java)
        run.targetName.set(kotlinTarget.mutationDirName())
        val binary =
            kotlinTarget.binaries
                .filterIsInstance<TestExecutable>()
                .firstOrNull { it.buildType == NativeBuildType.DEBUG }
                ?: error(
                    "Target '${kotlinTarget.name}' has no debug test executable to run mutants with",
                )
        // The nested TargetRun does not propagate its file dependencies, so
        // without this a stale binary from an earlier build would run.
        task.dependsOn(binary.linkTaskProvider)
        run.testBinary.from(binary.linkTaskProvider.flatMap { it.outputFile })
        val testTask = project.tasks.findByName("${kotlinTarget.name}Test")
        if (testTask is KotlinNativeSimulatorTest) {
            run.kind.set(TargetRun.TargetKind.NATIVE_SIMULATOR)
            run.simulatorDevice.set(testTask.device)
        } else {
            run.kind.set(TargetRun.TargetKind.NATIVE)
        }
        return run
    }

    private fun KotlinTarget.mutationDirName(): String = name.ifEmpty { "jvm" }

    /**
     * The compiler plugin links against `kotlin-compiler-embeddable` internals
     * of one Kotlin minor; a mismatched host must hear about it up front
     * instead of failing with an opaque IR error mid-compile.
     */
    private fun warnOnUnsupportedKotlin(project: Project) {
        val kotlinVersion = project.getKotlinPluginVersion()
        if (!kotlinVersion.startsWith(SUPPORTED_KOTLIN_PREFIX)) {
            project.logger.warn(
                "The dev.pott.kaputt plugin $VERSION supports Kotlin " +
                    "${SUPPORTED_KOTLIN_PREFIX}x, but this build uses Kotlin $kotlinVersion. " +
                    "Instrumentation may fail. Use a plugin release matching your Kotlin version.",
            )
        }
    }

    /**
     * Gradle may only skip an instrumented compile when a manifest from a
     * previous one is still current. Nothing else writes the manifest, so a
     * missing or older one has to mean recompiling.
     */
    private fun manifestsNewerThanSources(
        mutationDir: File,
        sourceDir: File,
    ): Boolean {
        val manifests =
            mutationDir
                .walkTopDown()
                .filter { it.name == MutationPluginNames.MUTATIONS_FILE_NAME }
                .toList()
        // An empty manifest is worth recompiling for: a module with no mutants
        // has nothing to run anyway, and it is how a stale one looks after its
        // sources are deleted.
        if (manifests.isEmpty() || manifests.any { it.length() == 0L }) return false
        val newestSource =
            sourceDir
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .maxOfOrNull { it.lastModified() }
                ?: return true
        return manifests.minOf { it.lastModified() } >= newestSource
    }

    /**
     * Variant whose compilation carries the mutants. A project with flavours
     * has no plain `debug`, so it names one such as `freeDebug` itself.
     */
    private fun Project.androidVariant(): String =
        extensions
            .findByType(MutationExtension::class.java)
            ?.androidVariant
            ?.getOrElse(DEFAULT_ANDROID_VARIANT)
            ?: DEFAULT_ANDROID_VARIANT

    /**
     * Unit-test task for a variant. Names the alternatives when it is missing,
     * because the default only fits a project without flavours.
     */
    private fun Project.androidTestTask(variant: String): String {
        val name = "test${variant.replaceFirstChar(Char::uppercase)}UnitTest"
        if (name in tasks.names) return name
        val available =
            tasks.names
                .filter { it.startsWith("test") && it.endsWith("UnitTest") }
                .sorted()
        throw IllegalStateException(
            "No task '$name' in $path. Set the variant that carries the mutants, " +
                "for example mutation { androidVariant.set(\"freeDebug\") }. " +
                "Available: ${available.joinToString()}",
        )
    }

    private fun Project.isAndroidOnly(): Boolean =
        pluginManager.hasPlugin("org.jetbrains.kotlin.android") &&
            !pluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform")

    private fun Project.isMutationRequested(): Boolean {
        val requestedByTask =
            gradle.startParameter.taskNames.any { taskName ->
                taskName.substringAfterLast(':') == TASK_NAME
            }
        val requestedByProperty =
            providers
                .gradleProperty(ENABLED_PROPERTY)
                .map { it != "false" }
                .getOrElse(false)
        return requestedByTask || requestedByProperty
    }

    companion object {
        const val TASK_NAME = "mutationTest"
        const val EXTENSION_NAME = "mutation"
        const val GROUP = "dev.pott.kaputt"

        /** Version this plugin was built as, generated into the jar at build time. */
        val VERSION: String by lazy {
            MutationGradlePlugin::class.java.classLoader
                .getResourceAsStream("dev.pott.kaputt.version")
                ?.bufferedReader()
                ?.use { reader -> reader.readText().trim() }
                ?: error("dev.pott.kaputt.version resource missing from the plugin jar")
        }

        const val ENABLED_PROPERTY = "mutation.enabled"
        const val TARGET_PROPERTY = "mutation.target"
        const val TARGETS_PROPERTY = "mutation.targets"
        const val SINCE_PROPERTY = "mutation.since"
        const val TARGETED_PROPERTY = "mutation.targetedTests"
        const val UPDATE_BASELINE_PROPERTY = "mutation.updateBaseline"
        const val COVERAGE_FILTERING_PROPERTY = "mutation.coverageFiltering"
        const val PARALLELISM_PROPERTY = "mutation.parallelism"
        const val BATCH_SIZE_PROPERTY = "mutation.batchSize"
        private val COVERAGE_AGENTS = listOf("kover", "jacoco", "intellij-coverage")
        const val BASELINE_FILE_NAME = "mutation-baseline.txt"
        private const val DEFAULT_TIMEOUT_FACTOR = 15.0
        private const val DEFAULT_STALL_SECONDS = 30L
        private const val DEFAULT_BATCH_SIZE = 25
        private const val DEFAULT_BASELINE_TIMEOUT_SECONDS = 1800L
        const val SUPPORTED_KOTLIN_PREFIX = "2.4."
        private const val MUTATION_DIR = "mutation"

        private val INSTRUMENTED_PLATFORMS =
            setOf(KotlinPlatformType.jvm, KotlinPlatformType.native)
        private val ANDROID_MAIN_COMPILATIONS = setOf("main", "debug", "release")
        private const val DEFAULT_ANDROID_VARIANT = "debug"
    }
}
