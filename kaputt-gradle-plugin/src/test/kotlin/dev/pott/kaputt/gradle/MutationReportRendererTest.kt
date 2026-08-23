package dev.pott.kaputt.gradle

import dev.pott.kaputt.compiler.MutationPoint
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MutationReportRendererTest {
    private val survivor =
        MutantResult(
            point =
                MutationPoint(
                    id = "aaaabbbbcccc",
                    operator = "conditional-boundary",
                    filePath = "/repo/src/commonMain/kotlin/Score.kt",
                    line = 12,
                    function = "isWinning",
                    description = "replaced '<' with '<='",
                ),
            status = MutantStatus.SURVIVED,
            durationMs = 250,
        )

    private val killed =
        MutantResult(
            point =
                MutationPoint(
                    id = "ddddeeeeffff",
                    operator = "math",
                    filePath = "/repo/src/commonMain/kotlin/Score.kt",
                    line = 20,
                    function = "total",
                    description = "replaced '+' with '-'",
                ),
            status = MutantStatus.KILLED,
            durationMs = 180,
        )

    @Test
    fun `GIVEN results WHEN consoleSummary THEN counts score and survivors listed`() {
        val summary = MutationReportRenderer.consoleSummary(":demo", listOf(survivor, killed))

        assertContains(
            summary,
            "2 mutants: 1 killed, 0 timed out, 1 survived, 0 ignored (baseline), 0 errors",
        )
        assertContains(summary, "Mutation score: 50.0%")
        assertContains(summary, "1 untested behaviors, 0 other survivors")
        assertContains(summary, "Untested behavior, most actionable first:")
        assertContains(summary, "Score.kt:12: replaced '<' with '<='")
        assertContains(summary, "Worst files:")
    }

    @Test
    fun `GIVEN survivors WHEN markdown THEN survivors table rendered`() {
        val markdown = MutationReportRenderer.markdown(":demo", listOf(survivor, killed))

        assertContains(markdown, "# Mutation report for :demo")
        assertContains(
            markdown,
            "| Score.kt:12 | isWinning | conditional-boundary | replaced '<' with '<=' |",
        )
    }

    @Test
    fun `GIVEN results WHEN json THEN parsable structure with escaped strings`() {
        val json = MutationReportRenderer.json(":demo", listOf(survivor))

        assertContains(json, "\"module\": \":demo\"")
        assertContains(json, "\"status\": \"SURVIVED\"")
        assertContains(json, "\"line\": 12")
        assertTrue(json.trim().startsWith("{") && json.trim().endsWith("}"))
    }

    @Test
    fun `GIVEN no results WHEN consoleSummary THEN empty run reported as full score`() {
        val summary = MutationReportRenderer.consoleSummary(":demo", emptyList())

        assertContains(summary, "Mutation score: 100.0%")
    }

    @Test
    fun `GIVEN quotes and newlines WHEN quoting for json THEN escaped`() {
        val tricky =
            survivor.copy(
                point = survivor.point.copy(description = "say \"hi\"\nnewline\\path"),
            )

        val json = MutationReportRenderer.json(":demo", listOf(tricky))

        assertContains(json, "say \\\"hi\\\"\\nnewline\\\\path")
    }

    @Test
    fun `GIVEN argfile values with quotes WHEN quoteArg THEN escaped and wrapped`() {
        assertEquals("\"plain\"", JvmTestCommand.quoteArg("plain"))
        assertEquals("\"a\\\\b\"", JvmTestCommand.quoteArg("a\\b"))
        assertEquals("\"say \\\"hi\\\"\"", JvmTestCommand.quoteArg("say \"hi\""))
    }

    @Test
    fun `GIVEN simulator target WHEN building native command THEN simctl spawn with child env`() {
        val binary = java.io.File("/tmp/test.kexe")

        assertEquals(
            listOf(
                "/usr/bin/xcrun",
                "simctl",
                "spawn",
                "--standalone",
                "iPhone 16",
                "/tmp/test.kexe",
            ),
            NativeTestCommand.command(binary, simulatorDevice = "iPhone 16"),
        )
        assertEquals(
            mapOf("SIMCTL_CHILD_KOTLIN_MUTATION" to "abc"),
            NativeTestCommand.environment(mutationId = "abc", simulator = true),
        )
    }

    @Test
    fun `GIVEN host native target WHEN building native command THEN binary runs directly`() {
        val binary = java.io.File("/tmp/test.kexe")

        assertEquals(
            listOf("/tmp/test.kexe"),
            NativeTestCommand.command(binary, simulatorDevice = null),
        )
        assertEquals(
            mapOf("KOTLIN_MUTATION" to "abc"),
            NativeTestCommand.environment(mutationId = "abc", simulator = false),
        )
        assertEquals(
            emptyMap(),
            NativeTestCommand.environment(mutationId = null, simulator = false),
        )
    }
}
