package dev.pott.kaputt.compiler

import kotlin.test.Test
import kotlin.test.assertEquals

class MutationPointCodecTest {
    @Test
    fun `GIVEN points with special characters WHEN encode and decode THEN roundtrip is lossless`() {
        val points =
            listOf(
                MutationPoint(
                    id = "abc123def456",
                    operator = "math",
                    filePath = "/tmp/My Project/Söme File.kt",
                    line = 42,
                    function = "calculate score",
                    description = "replaced '+' with '-'",
                    column = 17,
                    endLine = 42,
                    endColumn = 22,
                ),
                MutationPoint(
                    id = "0011aabbccdd",
                    operator = "void-call-removal",
                    filePath = "C:\\weird\\tab\there.kt",
                    line = 7,
                    function = "<set-value>",
                    description = "removed call to 'save'",
                ),
            )

        assertEquals(points, MutationPointCodec.decode(MutationPointCodec.encode(points)))
    }

    @Test
    fun `GIVEN empty list WHEN encode and decode THEN empty list`() {
        assertEquals(emptyList(), MutationPointCodec.decode(MutationPointCodec.encode(emptyList())))
    }
}
