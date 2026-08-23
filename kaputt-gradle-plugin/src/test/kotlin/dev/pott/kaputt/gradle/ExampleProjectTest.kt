package dev.pott.kaputt.gradle

import org.gradle.testkit.runner.GradleRunner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the example projects and pins what they report. They are the only place
 * target selection, forks and the reports run end to end, so a change that
 * breaks a consumer shows up here rather than in someone else's build.
 *
 * The Android examples need an SDK and are skipped when none is configured; the
 * instrumented one also needs a connected device.
 */
class ExampleProjectTest {
    @Test
    fun `jvm example finds the untested boundaries`() {
        val result = runExample("jvm-junit4-app")

        assertEquals(3, result.untestedBehaviors, result.output)
        assertEquals(12, result.mutants, result.output)
        assertTrue("Basket.kt:13: replaced '>' with '>='" in result.output, result.output)
    }

    @Test
    fun `junit 5 example reports what the junit 4 one does`() {
        assertSameAsJUnit4("jvm-junit5-app")
    }

    /** JUnit 5 carries platform 1.x and JUnit 6 carries platform 6.x. */
    @Test
    fun `junit 6 example reports what the junit 4 one does`() {
        assertSameAsJUnit4("jvm-junit6-app")
    }

    private fun assertSameAsJUnit4(example: String) {
        val platform = runExample(example)
        val junit4 = runExample("jvm-junit4-app")

        assertEquals(junit4.mutants, platform.mutants, platform.output)
        assertEquals(junit4.untestedBehaviors, platform.untestedBehaviors, platform.output)
        assertTrue("Basket.kt:13: replaced '>' with '>='" in platform.output, platform.output)
    }

    @Test
    fun `multiplatform example finds the untested attempt limit`() {
        val result = runExample("kmp-library")

        assertEquals(1, result.untestedBehaviors, result.output)
        assertEquals(7, result.mutants, result.output)
        assertTrue("Retry.kt:12: replaced '<' with '<='" in result.output, result.output)
    }

    @Test
    fun `multiplatform example reports the same mutants on a native target`() {
        val result = runExample("kmp-library", "-Pmutation.targets=jvm,$hostNativeTarget")

        assertEquals(7, result.mutants, result.output)
        val manifests =
            File(exampleDir("kmp-library"), "build/mutation")
                .listFiles()
                .orEmpty()
                .map { target -> target.resolve("mutations.tsv").readLines().map { it.substringBefore('\t') } }
        assertEquals(2, manifests.size, "expected a manifest per target")
        assertEquals(manifests.first().toSet(), manifests.last().toSet(), "ids differ across targets")
    }

    @Test
    fun `android example runs without mutation configuration`() {
        if (skipWithoutAndroidSdk()) return

        val result = runExample("android-app")

        assertEquals(1, result.untestedBehaviors, result.output)
        assertEquals(6, result.mutants, result.output)
    }

    @Test
    fun `instrumented android example verifies its mutants on a device`() {
        if (skipWithoutAndroidSdk() || skipWithoutDevice()) return

        val result = runExample("android-instrumented")

        assertEquals(6, result.mutants, result.output)
        assertEquals(1, result.untestedBehaviors, result.output)
        assertTrue("SyncWindow.kt:9: replaced '>' with '>='" in result.output, result.output)
    }

    @Test
    fun `flavoured android example measures the variant it names`() {
        if (skipWithoutAndroidSdk()) return

        val result = runExample("android-flavors")

        assertEquals(11, result.mutants, result.output)
        assertEquals(2, result.untestedBehaviors, result.output)
        assertTrue(
            File(exampleDir("android-flavors"), "build/mutation/jvm-paidStaging").isDirectory,
            "expected the named variant to carry the manifest",
        )
    }

    private data class Run(
        val output: String,
    ) {
        val mutants: Int = NUMBERS.find(output, "mutants:")
        val untestedBehaviors: Int = NUMBERS.find(output, "untested behaviors")
    }

    private fun runExample(
        name: String,
        vararg args: String,
    ): Run {
        val output =
            GradleRunner
                .create()
                .withProjectDir(exampleDir(name))
                .withArguments("mutationTest", *args, "--stacktrace")
                .forwardOutput()
                .build()
                .output
        return Run(output)
    }

    /** The example declares both; only the one matching the host can be compiled. */
    private val hostNativeTarget: String
        get() = if ("mac" in System.getProperty("os.name").lowercase()) "macosArm64" else "linuxX64"

    private fun exampleDir(name: String): File = File(System.getProperty("mutation.test.examplesDir"), name)

    private fun skipWithoutDevice(): Boolean {
        val adb = File(androidSdk(), "platform-tools/adb")
        val connected =
            adb.canExecute() &&
                AndroidInstrumentation.connectedDevices(adb.absolutePath).isNotEmpty()
        if (!connected) println("No Android device connected, skipping the instrumented example")
        return !connected
    }

    private fun androidSdk(): String =
        System.getenv("ANDROID_HOME")
            ?: System.getenv("ANDROID_SDK_ROOT")
            ?: File(exampleDir("android-app"), "local.properties")
                .takeIf(File::isFile)
                ?.readLines()
                ?.firstOrNull { it.startsWith("sdk.dir=") }
                ?.substringAfter('=')
                .orEmpty()

    private fun skipWithoutAndroidSdk(): Boolean {
        val present =
            System.getenv("ANDROID_HOME") != null ||
                System.getenv("ANDROID_SDK_ROOT") != null ||
                File(exampleDir("android-app"), "local.properties").isFile
        if (!present) println("No Android SDK configured, skipping the Android examples")
        return !present
    }

    private companion object NUMBERS {
        /** Reads the count in front of a phrase in the summary. */
        fun find(
            output: String,
            phrase: String,
        ): Int =
            Regex("(\\d+) ${Regex.escape(phrase)}")
                .find(output)
                ?.groupValues
                ?.get(1)
                ?.toInt()
                ?: -1
    }
}
