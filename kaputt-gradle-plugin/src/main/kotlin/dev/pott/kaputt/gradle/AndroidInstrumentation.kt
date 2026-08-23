package dev.pott.kaputt.gradle

import dev.pott.kaputt.runtime.MUTATION_INSTRUMENTATION_ARG
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Drives instrumented tests over adb. The mutants all live in the installed
 * apk, so a run only names the one to activate: the apks are built and
 * installed once and every mutant reuses them.
 *
 * `am instrument` reports failing tests in its output and still exits zero, so
 * the verdict is decided on the device and returned as an exit code.
 */
object AndroidInstrumentation {
    const val LISTENER_CLASS: String = "dev.pott.kaputt.runtime.MutationRunListener"

    /** Neither a pass nor a recognisable failure: the run itself did not work. */
    const val EXIT_INSTRUMENTATION_ERROR: Int = 3

    fun command(
        adb: String,
        deviceSerial: String?,
        target: String,
        mutationId: String?,
    ): List<String> =
        buildList {
            add(adb)
            deviceSerial?.let {
                add("-s")
                add(it)
            }
            add("shell")
            add(deviceScript(target, mutationId))
        }

    private fun deviceScript(
        target: String,
        mutationId: String?,
    ): String {
        val mutation = mutationId?.let { " -e $MUTATION_INSTRUMENTATION_ARG $it" }.orEmpty()
        val instrument = "am instrument -w -e listener $LISTENER_CLASS$mutation $target 2>&1"
        return "out=\$($instrument); echo \"\$out\"; " +
            "case \"\$out\" in " +
            "*FAILURES*|*'Process crashed'*|*'Test run failed'*) exit 1;; " +
            "*'OK ('*) exit 0;; " +
            "*) exit $EXIT_INSTRUMENTATION_ERROR;; " +
            "esac"
    }

    /** `<test package>/<runner>`, the argument `am instrument` identifies a run by. */
    fun resolveTarget(
        adb: String,
        deviceSerial: String?,
        testPackage: String,
    ): String {
        val listed = adbOutput(adb, deviceSerial, "shell", "pm", "list", "instrumentation")
        val target =
            listed
                .lineSequence()
                .map { it.removePrefix("instrumentation:").substringBefore(" (").trim() }
                .firstOrNull { it.startsWith("$testPackage/") }
        return target ?: error(
            "No instrumentation for '$testPackage' on the device. Installed:\n$listed",
        )
    }

    /** The applicationId AGP recorded for the built androidTest apk. */
    fun testPackage(outputsDir: File): String {
        val metadata =
            outputsDir.walkTopDown().firstOrNull { it.name == METADATA_FILE }
                ?: error("No $METADATA_FILE under $outputsDir; the androidTest apk was not built")
        return APPLICATION_ID.find(metadata.readText())?.groupValues?.get(1)
            ?: error("No applicationId in $metadata")
    }

    fun connectedDevices(adb: String): List<String> =
        adbOutput(adb, null, "devices")
            .lineSequence()
            .drop(1)
            .mapNotNull { line ->
                line.split('\t').takeIf { it.size == 2 && it[1].trim() == "device" }?.first()
            }.toList()

    /** `adb` from the Android SDK the build already resolved. */
    fun adbExecutable(sdkDir: File): String {
        val adb = sdkDir.resolve("platform-tools/adb")
        check(adb.canExecute()) { "No adb at $adb; install the platform-tools package" }
        return adb.absolutePath
    }

    private fun adbOutput(
        adb: String,
        deviceSerial: String?,
        vararg command: String,
    ): String {
        val full =
            buildList {
                add(adb)
                deviceSerial?.let {
                    add("-s")
                    add(it)
                }
                addAll(command)
            }
        val process = ProcessBuilder(full).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor(ADB_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        return output
    }

    private const val METADATA_FILE = "output-metadata.json"
    private const val ADB_TIMEOUT_SECONDS = 30L
    private val APPLICATION_ID = Regex("\"applicationId\"\\s*:\\s*\"([^\"]+)\"")
}
