package dev.pott.kaputt.gradle

import dev.pott.kaputt.compiler.MutationPoint
import kotlin.test.Test
import kotlin.test.assertEquals

class MutationBreakdownTest {
    @Test
    fun `GIVEN results WHEN grouping THEN each operator reports its own score`() {
        val rows =
            MutationBreakdown.byOperator(
                listOf(
                    result("math", MutantStatus.KILLED),
                    result("math", MutantStatus.SURVIVED),
                    result("string-literal", MutantStatus.SURVIVED),
                    result("negate-conditional", MutantStatus.TIMED_OUT),
                ),
            )

        assertEquals(
            listOf("string-literal", "math", "negate-conditional"),
            rows.map {
                it.operator
            },
        )
        assertEquals(0.0, rows.first().score)
        assertEquals(50.0, rows[1].score)
        assertEquals(100.0, rows.last().score)
    }

    @Test
    fun `GIVEN a timeout WHEN grouping THEN it counts as detected`() {
        val rows = MutationBreakdown.byOperator(listOf(result("math", MutantStatus.TIMED_OUT)))

        assertEquals(1, rows.single().detected)
        assertEquals(0, rows.single().survived)
    }

    @Test
    fun `GIVEN worst operators WHEN grouping THEN they sort first`() {
        val rows =
            MutationBreakdown.byOperator(
                listOf(
                    result("a", MutantStatus.KILLED),
                    result("b", MutantStatus.SURVIVED),
                ),
            )

        assertEquals("b", rows.first().operator)
    }

    private fun result(
        operator: String,
        status: MutantStatus,
    ) = MutantResult(
        point =
            MutationPoint(
                id = "id-$operator-$status",
                operator = operator,
                filePath = "/src/Foo.kt",
                line = 1,
                function = "foo",
                description = "d",
            ),
        status = status,
        durationMs = 1,
    )
}
