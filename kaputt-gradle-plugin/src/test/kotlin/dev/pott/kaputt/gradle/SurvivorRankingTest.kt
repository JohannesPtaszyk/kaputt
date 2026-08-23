package dev.pott.kaputt.gradle

import dev.pott.kaputt.compiler.MutationPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SurvivorRankingTest {
    @Test
    fun `GIVEN mixed survivors WHEN grouping THEN control flow changes come first`() {
        val ranked =
            SurvivorRanking.group(
                listOf(
                    survivor("number-literal", line = 1),
                    survivor("conditional-boundary", line = 2),
                    survivor("string-literal", line = 3),
                    survivor("negate-conditional", line = 4),
                ),
            )

        assertEquals(
            listOf(
                "conditional-boundary",
                "negate-conditional",
                "number-literal",
                "string-literal",
            ),
            ranked.map { it.first.point.operator },
        )
    }

    @Test
    fun `GIVEN the same survivor repeated WHEN grouping THEN it appears once with a count`() {
        val ranked = SurvivorRanking.group(List(3) { survivor("math", line = 7) })

        assertEquals(1, ranked.size)
        assertEquals(3, ranked.single().count)
    }

    @Test
    fun `GIVEN killed mutants WHEN grouping THEN they are left out`() {
        val ranked =
            SurvivorRanking.group(
                listOf(survivor("math", line = 1, status = MutantStatus.KILLED)),
            )

        assertTrue(ranked.isEmpty())
    }

    @Test
    fun `GIVEN operators WHEN asking for signal THEN literals are not high signal`() {
        assertTrue(SurvivorRanking.isHighSignal(survivor("conditional-boundary", 1)))
        assertTrue(SurvivorRanking.isHighSignal(survivor("math", 1)))
        // A forced return or a dropped call says a behavior is unasserted.
        assertTrue(SurvivorRanking.isHighSignal(survivor("empty-return", 1)))
        assertTrue(SurvivorRanking.isHighSignal(survivor("void-call-removal", 1)))
        assertFalse(SurvivorRanking.isHighSignal(survivor("number-literal", 1)))
        assertFalse(SurvivorRanking.isHighSignal(survivor("boolean-literal", 1)))
    }

    @Test
    fun `GIVEN survivors across files WHEN listing worst THEN busiest logic file leads`() {
        val worst =
            SurvivorRanking.worstFiles(
                listOf(
                    survivor("conditional-boundary", 1, file = "/a/Score.kt"),
                    survivor("negate-conditional", 2, file = "/a/Score.kt"),
                    survivor("math", 3, file = "/a/Other.kt"),
                    survivor("number-literal", 4, file = "/a/Dto.kt"),
                ),
            )

        assertEquals(listOf("Score.kt" to 2, "Other.kt" to 1), worst)
    }

    private fun survivor(
        operator: String,
        line: Int,
        file: String = "/repo/Score.kt",
        status: MutantStatus = MutantStatus.SURVIVED,
    ) = MutantResult(
        point =
            MutationPoint(
                id = "id$operator$line",
                operator = operator,
                filePath = file,
                line = line,
                function = "fn",
                description = "changed something",
            ),
        status = status,
        durationMs = 1,
    )
}
