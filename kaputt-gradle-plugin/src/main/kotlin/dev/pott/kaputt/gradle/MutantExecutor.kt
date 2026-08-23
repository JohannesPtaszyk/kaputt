package dev.pott.kaputt.gradle

import dev.pott.kaputt.runtime.MUTATION_ENV
import dev.pott.kaputt.runtime.MUTATION_PROPERTY
import dev.pott.kaputt.runtime.MutationTestRunner
import java.io.File
import java.util.concurrent.TimeUnit

data class ProcessOutcome(
    val exitCode: Int?,
    val timedOut: Boolean,
    val durationMs: Long,
)

/** Launches one process per mutant run and classifies by exit code. */
class MutantExecutor(
    private val logsDir: File,
) {
    init {
        logsDir.mkdirs()
    }

    fun execute(
        command: List<String>,
        environment: Map<String, String>,
        logName: String,
        timeoutSeconds: Long,
        stallSeconds: Long = 0,
    ): ProcessOutcome {
        val logFile = logsDir.resolve("$logName.log")
        val start = System.nanoTime()
        val builder =
            ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(logFile)
        builder.environment().putAll(environment)
        val process = builder.start()
        val finished = process.awaitWhileProgressing(logFile, timeoutSeconds, stallSeconds)
        if (!finished) {
            process.destroyForcibly()
            process.waitFor(KILL_GRACE_SECONDS, TimeUnit.SECONDS)
        }
        return ProcessOutcome(
            exitCode = if (finished) process.exitValue() else null,
            timedOut = !finished,
            durationMs = (System.nanoTime() - start) / NANOS_PER_MILLI,
        )
    }

    /**
     * Waits for the run, treating a lack of progress as a hang. The fork emits
     * a byte per finished test, so a suite that is merely slow keeps the log
     * growing while a mutant that broke a loop-termination guard stops it
     * immediately. The absolute cap stays as a backstop for a fork that wedges
     * before it can report anything.
     */
    private fun Process.awaitWhileProgressing(
        logFile: File,
        timeoutSeconds: Long,
        stallSeconds: Long,
    ): Boolean {
        if (stallSeconds <= 0) return waitFor(timeoutSeconds, TimeUnit.SECONDS)
        val startedAt = System.nanoTime()
        var lastSize = -1L
        var lastProgressAt = startedAt
        while (true) {
            if (waitFor(POLL_MILLIS, TimeUnit.MILLISECONDS)) return true
            val now = System.nanoTime()
            val size = logFile.length()
            if (size != lastSize) {
                lastSize = size
                lastProgressAt = now
            }
            val verdict =
                StallPolicy.evaluate(
                    elapsedNanos = now - startedAt,
                    sinceProgressNanos = now - lastProgressAt,
                    timeoutSeconds = timeoutSeconds,
                    stallSeconds = stallSeconds,
                )
            if (verdict != StallPolicy.Verdict.RUNNING) return false
        }
    }

    companion object {
        private const val POLL_MILLIS = 250L
        private const val KILL_GRACE_SECONDS = 10L
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}

/**
 * Command line for the jvm target: a forked JVM over the regular test
 * classpath running the kotlin-test suite (via its JUnit mapping on this
 * target). The classpath lives in an `@argfile` to stay clear of OS limits.
 */
object JvmTestCommand {
    val RUNNER_MAIN_CLASS: String = MutationTestRunner::class.java.name

    fun writeArgFile(
        argFile: File,
        classpath: List<File>,
        testClassesDirs: List<File>,
        jvmArgs: List<String> = emptyList(),
        includePatterns: List<String> = emptyList(),
        excludePatterns: List<String> = emptyList(),
    ): File {
        argFile.parentFile.mkdirs()
        argFile.writeText(
            buildString {
                jvmArgs.forEach { arg -> appendLine(quoteArg(arg)) }
                appendLine("-cp")
                appendLine(quoteArg(classpath.joinToString(File.pathSeparator) { it.absolutePath }))
                appendLine(RUNNER_MAIN_CLASS)
                testClassesDirs.forEach { dir ->
                    appendLine(quoteArg(dir.absolutePath))
                }
                includePatterns.forEach { p ->
                    appendLine(quoteArg("${MutationTestRunner.INCLUDE_ARG_PREFIX}$p"))
                }
                excludePatterns.forEach { p ->
                    appendLine(quoteArg("${MutationTestRunner.EXCLUDE_ARG_PREFIX}$p"))
                }
            },
        )
        return argFile
    }

    fun command(
        javaExecutable: String,
        argFile: File,
        mutationId: String?,
        testFilter: String? = null,
        includePatterns: List<String> = emptyList(),
        batchFile: File? = null,
        errorFileDir: File? = null,
    ): List<String> =
        buildList {
            add(javaExecutable)
            addAll(FAST_FORK_ARGS)
            // A mutant can abort the JVM outright, which still counts as
            // detected. Keep the crash log out of the consumer's project root.
            if (errorFileDir != null) {
                add("-XX:ErrorFile=${errorFileDir.absolutePath}/hs_err_%p.log")
            }
            if (mutationId != null) add("-D$MUTATION_PROPERTY=$mutationId")
            add("@${argFile.absolutePath}")
            if (batchFile !=
                null
            ) {
                add("${MutationTestRunner.BATCH_ARG_PREFIX}${batchFile.absolutePath}")
            }
            if (testFilter != null) add("$FILTER_ARG_PREFIX$testFilter")
            includePatterns.forEach { pattern ->
                val prefix =
                    if ('#' in pattern) {
                        MutationTestRunner.METHOD_ARG_PREFIX
                    } else {
                        MutationTestRunner.INCLUDE_ARG_PREFIX
                    }
                add("$prefix$pattern")
            }
        }

    /**
     * Mutant forks are short-lived, so paying for C2 compilation and a
     * concurrent collector rarely pays off before the process exits.
     */
    private val FAST_FORK_ARGS =
        listOf(
            "-XX:TieredStopAtLevel=1",
            "-XX:+UseSerialGC",
            "-Xshare:auto",
            // Without this a fork touching AWT/Skiko registers as a macOS GUI app
            // and steals focus. The module's own jvmArgs still win.
            "-Dapple.awt.UIElement=true",
        )

    const val FILTER_ARG_PREFIX = MutationTestRunner.FILTER_ARG_PREFIX

    fun quoteArg(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}

/**
 * Command line for native targets: kotlin-test's runner is compiled into the
 * test executable, so the binary is launched directly with no test framework or
 * JVM involved. Simulator targets go through `xcrun simctl spawn`, which only
 * forwards environment variables prefixed with `SIMCTL_CHILD_`.
 */
object NativeTestCommand {
    fun command(
        binary: File,
        simulatorDevice: String?,
    ): List<String> =
        if (simulatorDevice == null) {
            listOf(binary.absolutePath)
        } else {
            listOf(
                "/usr/bin/xcrun",
                "simctl",
                "spawn",
                "--standalone",
                simulatorDevice,
                binary.absolutePath,
            )
        }

    fun environment(
        mutationId: String?,
        simulator: Boolean,
    ): Map<String, String> {
        if (mutationId == null) return emptyMap()
        val variable = if (simulator) "SIMCTL_CHILD_$MUTATION_ENV" else MUTATION_ENV
        return mapOf(variable to mutationId)
    }
}
