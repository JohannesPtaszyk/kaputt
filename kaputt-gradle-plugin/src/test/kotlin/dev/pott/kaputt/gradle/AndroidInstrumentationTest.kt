package dev.pott.kaputt.gradle

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AndroidInstrumentationTest {
    @Test
    fun `GIVEN a mutant WHEN building the command THEN it is passed as an instrumentation arg`() {
        val command =
            AndroidInstrumentation.command(
                adb = "/sdk/platform-tools/adb",
                deviceSerial = "emulator-5554",
                target = "com.example.test/androidx.test.runner.AndroidJUnitRunner",
                mutationId = "abc123",
            )

        assertEquals(listOf("/sdk/platform-tools/adb", "-s", "emulator-5554", "shell"), command.dropLast(1))
        val script = command.last()
        assertTrue("-e kotlinMutation abc123" in script, script)
        assertTrue("-e listener ${AndroidInstrumentation.LISTENER_CLASS}" in script, script)
    }

    @Test
    fun `GIVEN no mutant WHEN building the command THEN the baseline runs without the arg`() {
        val script =
            AndroidInstrumentation
                .command(
                    adb = "adb",
                    deviceSerial = null,
                    target = "t/r",
                    mutationId = null,
                ).last()

        assertTrue("kotlinMutation" !in script, script)
        assertTrue("-s" !in script.substringBefore("shell"), script)
    }

    @Test
    fun `GIVEN the device script THEN failures and crashes exit nonzero and a pass exits zero`() {
        val script = AndroidInstrumentation.command("adb", null, "t/r", "m").last()

        assertTrue("*FAILURES*|*'Process crashed'*|*'Test run failed'*) exit 1" in script, script)
        assertTrue("*'OK ('*) exit 0" in script, script)
        assertTrue("exit ${AndroidInstrumentation.EXIT_INSTRUMENTATION_ERROR}" in script, script)
    }

    @Test
    fun `GIVEN apk metadata WHEN reading the test package THEN the applicationId comes back`() {
        val dir =
            File.createTempFile("outputs", "").let { f ->
                f.delete()
                f.mkdirs().let { _ -> f }
            }
        dir.resolve("debug").mkdirs()
        dir.resolve("debug/output-metadata.json").writeText(
            """{"applicationId": "com.example.battery.test", "variantName": "debugAndroidTest"}""",
        )

        assertEquals("com.example.battery.test", AndroidInstrumentation.testPackage(dir))
    }

    @Test
    fun `GIVEN no metadata WHEN reading the test package THEN the failure names the build`() {
        val dir =
            File.createTempFile("empty", "").let { f ->
                f.delete()
                f.mkdirs().let { _ -> f }
            }

        val failure =
            assertFailsWith<IllegalStateException> {
                AndroidInstrumentation.testPackage(dir)
            }
        assertTrue("androidTest apk was not built" in failure.message.orEmpty())
    }
}
