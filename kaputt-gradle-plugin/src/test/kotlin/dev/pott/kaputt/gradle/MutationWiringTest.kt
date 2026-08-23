package dev.pott.kaputt.gradle

import org.gradle.testkit.runner.GradleRunner
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

/**
 * Drives a real Gradle build, which is the only place the target wiring is
 * exercised: manifests, per-target logs and the guard that stops a target from
 * silently verifying nothing.
 */
class MutationWiringTest {
    private val projectDir: File =
        File.createTempFile("mutation-wiring", "").let { file ->
            file.delete()
            file.mkdirs()
            file
        }

    @AfterTest
    fun cleanUp() {
        projectDir.deleteRecursively()
    }

    @Test
    fun `GIVEN a jvm module WHEN mutationTest runs THEN it reports per-mutant verdicts`() {
        writeProject(withTest = true)

        val output = run("mutationTest").output

        assertContains(output, "mutants:")
        val logs = File(projectDir, "build/reports/mutation-test/logs").listFiles().orEmpty()
        assertTrue(
            logs.any {
                it.name.startsWith("baseline-")
            },
            "no baseline log: ${logs.toList()}",
        )
        assertTrue(
            File(projectDir, "build/mutation/jvm/mutations.tsv").readLines().isNotEmpty(),
            "manifest is empty",
        )
    }

    @Test
    fun `GIVEN an exclude is configured WHEN mutationTest runs THEN the compile still succeeds`() {
        writeProject(withTest = true, extraConfig = "mutation { excludes.add(\"*Generated*\") }")

        val output = run("mutationTest").output

        assertContains(output, "mutants:")
    }

    @Test
    fun `GIVEN a module without tests WHEN mutationTest runs THEN it says so instead of scoring zero`() {
        writeProject(withTest = false)

        val output = run("mutationTest").output

        assertContains(output, "has tests")
        assertTrue("survived" !in output, "a module with no tests must not report survivors")
    }

    private fun run(vararg args: String) =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(*args, "--stacktrace")
            .forwardOutput()
            .build()

    private fun writeProject(
        withTest: Boolean,
        extraConfig: String = "",
    ) {
        File(projectDir, "settings.gradle.kts").writeText("rootProject.name = \"wiring\"\n")
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("org.jetbrains.kotlin.jvm") version "$KOTLIN_VERSION"
                id("dev.pott.kaputt")
            }
            repositories { mavenCentral(); mavenLocal() }
            dependencies { testImplementation(kotlin("test-junit")) }
            $extraConfig
            """.trimIndent(),
        )
        val main = File(projectDir, "src/main/kotlin").apply { mkdirs() }
        File(main, "Score.kt").writeText("fun isWinning(a: Int, b: Int): Boolean = a > b\n")
        if (withTest) {
            val test = File(projectDir, "src/test/kotlin").apply { mkdirs() }
            File(test, "ScoreTest.kt").writeText(
                """
                import kotlin.test.Test
                import kotlin.test.assertEquals

                class ScoreTest {
                    @Test
                    fun boundary() {
                        assertEquals(false, isWinning(1, 1))
                        assertEquals(true, isWinning(2, 1))
                    }
                }
                """.trimIndent(),
            )
        }
    }

    private companion object {
        val KOTLIN_VERSION: String = System.getProperty("mutation.test.kotlinVersion")
    }
}
