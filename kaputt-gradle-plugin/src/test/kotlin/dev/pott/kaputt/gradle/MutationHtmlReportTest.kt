package dev.pott.kaputt.gradle

import dev.pott.kaputt.compiler.MutationPoint
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MutationHtmlReportTest {
    private val survivor = result(MutantStatus.SURVIVED, description = "replaced '<' with '<='")
    private val killed =
        result(MutantStatus.KILLED, description = "replaced '+' with '-'", line = 20)

    @Test
    fun `GIVEN results WHEN rendering html THEN score tiles and survivor rows appear`() {
        val html = MutationHtmlReport.render(":demo", listOf(survivor, killed))

        assertContains(html, "<title>Mutation report: :demo</title>")
        assertContains(html, "50.0%")
        assertContains(html, "Survivors")
        assertContains(html, "SURVIVED")
        assertContains(html, "isWinning")
        assertContains(html, "<th>File</th>")
        assertContains(html, "<code>Score.kt</code>")
    }

    @Test
    fun `GIVEN html-sensitive characters WHEN rendering THEN they are escaped`() {
        val html = MutationHtmlReport.render(":demo", listOf(survivor))

        assertContains(html, "replaced &#39;&lt;&#39; with &#39;&lt;=&#39;")
        assertFalse(html.contains("replaced '<' with '<='"))
    }

    @Test
    fun `GIVEN no results WHEN rendering THEN full score without survivors section`() {
        val html = MutationHtmlReport.render(":demo", emptyList())

        assertContains(html, "100.0%")
        assertFalse(html.contains("<h2>Survivors"))
    }

    @Test
    fun `GIVEN files with survivors WHEN rendering THEN their details section is open`() {
        val html = MutationHtmlReport.render(":demo", listOf(survivor, killed))

        assertTrue(html.contains("<details open>"))
    }

    @Test
    fun `GIVEN source lookup WHEN rendering THEN expandable diff with context is included`() {
        val source =
            listOf(
                "package demo",
                "",
                "object Score {",
                "    fun isWinning(a: Int, b: Int): Boolean {",
                "        return a < b",
                "    }",
                "}",
            )
        val spanned =
            survivor.copy(
                point = survivor.point.copy(line = 5, column = 16, endLine = 5, endColumn = 21),
            )

        val html = MutationHtmlReport.render(":demo", listOf(spanned)) { source }

        assertContains(html, "<details class=\"diffbox\">")
        // Single inline-diff row: only the minimal changed region is marked,
        // and the block is dedented by the window's common indentation.
        assertContains(html, "±   5      return a &lt;<ins>=</ins> b")
        assertContains(html, "    4  fun isWinning(a: Int, b: Int): Boolean {</span>")
        assertContains(html, "    6  }</span>")
    }

    @Test
    fun `GIVEN a removal mutation WHEN rendering diff THEN removed region is struck through`() {
        val source =
            listOf(
                "fun save(entry: Entry) {",
                "    repository.persist(entry)",
                "}",
            )
        val spanned =
            survivor.copy(
                point =
                    survivor.point.copy(
                        description = "removed call to 'persist'",
                        operator = "void-call-removal",
                        line = 2,
                        column = 5,
                        endLine = 2,
                        endColumn = 30,
                    ),
            )

        val html = MutationHtmlReport.render(":demo", listOf(spanned)) { source }

        assertContains(html, "<del>repository.persist(entry)</del>")
    }

    @Test
    fun `GIVEN no source available WHEN rendering THEN plain description without details`() {
        val html = MutationHtmlReport.render(":demo", listOf(survivor)) { null }

        assertFalse(html.contains("<details class=\"diffbox\">"))
        assertContains(html, "replaced &#39;&lt;&#39; with &#39;&lt;=&#39;")
    }

    private fun result(
        status: MutantStatus,
        description: String,
        line: Int = 12,
    ) = MutantResult(
        point =
            MutationPoint(
                id = "aaaabbbbcccc",
                operator = "conditional-boundary",
                filePath = "/repo/src/commonMain/kotlin/Score.kt",
                line = line,
                function = "isWinning",
                description = description,
            ),
        status = status,
        durationMs = 250,
    )
}
