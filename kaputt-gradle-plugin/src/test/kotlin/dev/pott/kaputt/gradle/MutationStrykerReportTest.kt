package dev.pott.kaputt.gradle

import dev.pott.kaputt.compiler.MutationPoint
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class MutationStrykerReportTest {
    @Test
    fun `GIVEN results WHEN rendering stryker report THEN schema fields and statuses match`() {
        val json =
            MutationStrykerReport.render(
                results =
                    listOf(
                        result(MutantStatus.KILLED),
                        result(MutantStatus.SURVIVED),
                        result(MutantStatus.TIMED_OUT),
                        result(MutantStatus.ERROR),
                    ),
                sourceLookup = { listOf("line one", "line two") },
                relativize = { path -> path.removePrefix("/repo/") },
            )

        assertContains(json, "\"schemaVersion\": \"2\"")
        assertContains(json, "\"src/commonMain/kotlin/Score.kt\": {")
        assertContains(json, "\"language\": \"kotlin\"")
        assertContains(json, "\"source\": \"line one\\nline two\"")
        assertContains(json, "\"status\": \"Killed\"")
        assertContains(json, "\"status\": \"Survived\"")
        assertContains(json, "\"status\": \"Timeout\"")
        assertContains(json, "\"status\": \"RuntimeError\"")
        assertContains(json, "\"start\": {\"line\": 12, \"column\": 16}")
        assertContains(json, "\"end\": {\"line\": 12, \"column\": 21}")
    }

    @Test
    fun `GIVEN missing sources WHEN rendering stryker report THEN file skipped`() {
        val json =
            MutationStrykerReport.render(
                results = listOf(result(MutantStatus.SURVIVED)),
                sourceLookup = { null },
                relativize = { it },
            )

        assertFalse(json.contains("Score.kt"))
        assertContains(json, "\"files\": {")
    }

    private fun result(status: MutantStatus) =
        MutantResult(
            point =
                MutationPoint(
                    id = "aaaabbbbcccc",
                    operator = "conditional-boundary",
                    filePath = "/repo/src/commonMain/kotlin/Score.kt",
                    line = 12,
                    function = "isWinning",
                    description = "replaced '<' with '<='",
                    column = 16,
                    endLine = 12,
                    endColumn = 21,
                ),
            status = status,
            durationMs = 250,
        )
}
