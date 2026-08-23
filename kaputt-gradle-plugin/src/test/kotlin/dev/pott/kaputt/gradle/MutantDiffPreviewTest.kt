package dev.pott.kaputt.gradle

import dev.pott.kaputt.compiler.MutationPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MutantDiffPreviewTest {
    @Test
    fun `GIVEN operator replacement WHEN diff THEN operator swapped inside span`() {
        val line = "    val total = base + bonus"
        val diff = diff(line, description = "replaced '+' with '-'", column = 17, endColumn = 29)

        assertEquals("    val total = base - bonus", diff?.after)
    }

    @Test
    fun `GIVEN number literal WHEN diff THEN literal replaced`() {
        val line = "    if (score >= 18) return true"
        val diff = diff(line, description = "replaced '18' with '19'", column = 18, endColumn = 20)

        assertEquals("    if (score >= 19) return true", diff?.after)
    }

    @Test
    fun `GIVEN negated comparison WHEN diff THEN span wrapped in negation`() {
        val line = "    return age >= 18"
        val diff = diff(line, description = "negated '>='", column = 12, endColumn = 21)

        assertEquals("    return !(age >= 18)", diff?.after)
    }

    @Test
    fun `GIVEN removed not prefix WHEN diff THEN bang dropped`() {
        val line = "    return !valid"
        val diff = diff(line, description = "removed '!'", column = 12, endColumn = 18)

        assertEquals("    return valid", diff?.after)
    }

    @Test
    fun `GIVEN removed not method call WHEN diff THEN suffix dropped`() {
        val line = "    return valid.not()"
        val diff = diff(line, description = "removed '!'", column = 12, endColumn = 23)

        assertEquals("    return valid", diff?.after)
    }

    @Test
    fun `GIVEN removed void call WHEN diff THEN span disappears`() {
        val line = "        repository.save(entry)"
        val diff = diff(line, description = "removed call to 'save'", column = 9, endColumn = 31)

        assertEquals("        ", diff?.after)
    }

    @Test
    fun `GIVEN empty return WHEN diff THEN span replaced by default`() {
        val line = "    fun names(): List<String> = listOf(\"a\")"
        val diff =
            diff(
                line,
                description = "replaced return value with emptyList()",
                column = 33,
                endColumn = 44,
            )

        assertEquals("    fun names(): List<String> = emptyList()", diff?.after)
    }

    @Test
    fun `GIVEN span not on line WHEN diff THEN null`() {
        assertNull(
            diff("short", description = "replaced '+' with '-'", column = 40, endColumn = 44),
        )
    }

    @Test
    fun `GIVEN multi-line span WHEN diff THEN clamped to end of first line`() {
        val line = "    val ok = first &&"
        val diff =
            diff(
                line,
                description = "replaced '&&' with '||'",
                column = 14,
                endColumn = 10,
                endLine = 99,
            )

        assertEquals("    val ok = first ||", diff?.after)
    }

    private fun diff(
        line: String,
        description: String,
        column: Int,
        endColumn: Int,
        endLine: Int = 1,
    ): MutantDiffPreview.Diff? =
        MutantDiffPreview.diff(
            MutationPoint(
                id = "aaaabbbbcccc",
                operator = "math",
                filePath = "/src/Foo.kt",
                line = 1,
                function = "foo",
                description = description,
                column = column,
                endLine = endLine,
                endColumn = endColumn,
            ),
            line,
        )
}
