package dev.pott.kaputt.gradle

import dev.pott.kaputt.compiler.MutationPoint
import kotlin.test.Test
import kotlin.test.assertEquals

class MutationBaselineTest {
    @Test
    fun `GIVEN comments and blank lines WHEN parse THEN only trimmed ids remain`() {
        val text =
            """
            # Accepted equivalent mutants, one id per line.
            # Regenerate with -Pmutation.updateBaseline=true.

              aaaabbbbcccc
            ddddeeeeffff
            """.trimIndent()

        assertEquals(setOf("aaaabbbbcccc", "ddddeeeeffff"), MutationBaseline.parse(text))
    }

    @Test
    fun `GIVEN rendered baseline WHEN parse THEN the same ids come back`() {
        val ids = listOf("aaaabbbbcccc", "ddddeeeeffff")

        assertEquals(ids.toSet(), MutationBaseline.parse(MutationBaseline.render(ids)))
    }

    @Test
    fun `GIVEN no survivors WHEN render THEN only the header lines are written`() {
        assertEquals(
            """
            # Accepted equivalent mutants, one id per line.
            # Regenerate with -Pmutation.updateBaseline=true.

            """.trimIndent(),
            MutationBaseline.render(emptyList()),
        )
    }

    @Test
    fun `GIVEN accepted ids WHEN apply THEN only accepted survivors become ignored`() {
        val results =
            listOf(
                result("accepted", MutantStatus.SURVIVED),
                result("unknown", MutantStatus.SURVIVED),
                result("killed", MutantStatus.KILLED),
            )

        val applied = MutationBaseline.apply(results, setOf("accepted", "killed"))

        assertEquals(
            listOf(MutantStatus.IGNORED, MutantStatus.SURVIVED, MutantStatus.KILLED),
            applied.map { it.status },
        )
    }

    private fun result(
        id: String,
        status: MutantStatus,
    ) = MutantResult(
        point =
            MutationPoint(
                id = id,
                operator = "math",
                filePath = "/src/Foo.kt",
                line = 1,
                function = "foo",
                description = "replaced '+' with '-'",
            ),
        status = status,
        durationMs = 10,
    )
}
