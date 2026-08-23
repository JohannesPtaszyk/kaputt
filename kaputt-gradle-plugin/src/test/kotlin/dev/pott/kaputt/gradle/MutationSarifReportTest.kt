package dev.pott.kaputt.gradle

import dev.pott.kaputt.compiler.MutationPoint
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MutationSarifReportTest {
    @Test
    fun `GIVEN survivors WHEN rendering sarif THEN warnings with regions and rules appear`() {
        val sarif =
            MutationSarifReport.render(
                results = listOf(result(MutantStatus.SURVIVED), result(MutantStatus.KILLED)),
                toolVersion = "0.1.0",
                relativize = { path -> path.removePrefix("/repo/") },
            )

        assertContains(sarif, "\"version\": \"2.1.0\"")
        assertContains(sarif, "\"name\": \"kotlin-mutation-testing\"")
        assertContains(sarif, "\"id\": \"mutation-survived-conditional-boundary\"")
        assertContains(sarif, "\"uri\": \"src/commonMain/kotlin/Score.kt\"")
        assertContains(
            sarif,
            "\"startLine\": 12, \"startColumn\": 16, \"endLine\": 12, \"endColumn\": 21",
        )
        assertContains(sarif, "Survived mutant in 'isWinning': replaced '<' with '<='")
    }

    @Test
    fun `GIVEN killed mutants only WHEN rendering sarif THEN no results emitted`() {
        val sarif =
            MutationSarifReport.render(
                results = listOf(result(MutantStatus.KILLED), result(MutantStatus.TIMED_OUT)),
                toolVersion = "0.1.0",
                relativize = { it },
            )

        assertFalse(sarif.contains("\"ruleId\""))
        assertTrue(sarif.contains("\"results\": ["))
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
