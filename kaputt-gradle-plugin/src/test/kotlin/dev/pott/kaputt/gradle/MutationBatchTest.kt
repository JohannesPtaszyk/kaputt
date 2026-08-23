package dev.pott.kaputt.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class MutationBatchTest {
    @Test
    fun `GIVEN entries WHEN writing the batch file THEN one mutant per line`() {
        val content =
            MutationBatch.fileContent(
                listOf(
                    MutationBatch.Entry("aaa", listOf("A#one", "B#two")),
                    MutationBatch.Entry("bbb", listOf("C#three")),
                ),
            )

        assertEquals("aaa\tA#one,B#two\nbbb\tC#three", content)
    }

    @Test
    fun `GIVEN fork output WHEN parsing THEN verdicts are read per mutant`() {
        val verdicts =
            MutationBatch.parseVerdicts(
                """
                ....
                MUTANT aaa 0
                some noise
                MUTANT bbb 1
                """.trimIndent(),
            )

        assertEquals(mapOf("aaa" to 0, "bbb" to 1), verdicts)
    }

    @Test
    fun `GIVEN a batch cut short WHEN parsing THEN only reported mutants appear`() {
        val verdicts = MutationBatch.parseVerdicts("MUTANT aaa 0\n")

        assertEquals(setOf("aaa"), verdicts.keys)
    }

    @Test
    fun `GIVEN tests in different order WHEN keyed THEN mutants needing the same classes match`() {
        val a =
            MutationBatch.groupKey(
                listOf("a.ScoreTest#one", "a.ScoreTest#two", "b.OtherTest#x"),
            )
        val b = MutationBatch.groupKey(listOf("b.OtherTest#y", "a.ScoreTest#three"))

        assertEquals(a, b)
    }

    @Test
    fun `GIVEN tests in different classes WHEN keyed THEN keys differ`() {
        assertNotEquals(
            MutationBatch.groupKey(listOf("a.ScoreTest#one")),
            MutationBatch.groupKey(listOf("b.OtherTest#one")),
        )
    }

    @Test
    fun `GIVEN a fork that died mid-batch WHEN reading the tail THEN only the last mutant remains`() {
        val log =
            """
            .FAILED: closesInOrder(): expected:<1> but was:<2>
            MUTANT aaaa1111 1
            .FAILED: closesOnCancellation(): expected:<false> but was:<true>
            """.trimIndent()

        val tail = MutationBatch.tailAfterLastVerdict(log)

        assertEquals(".FAILED: closesOnCancellation(): expected:<false> but was:<true>", tail)
    }

    @Test
    fun `GIVEN a fork that reported nothing WHEN reading the tail THEN the whole log remains`() {
        val log = ".FAILED: closesInOrder(): expected:<1> but was:<2>"

        assertEquals(log, MutationBatch.tailAfterLastVerdict(log))
    }
}
