package dev.pott.kaputt.gradle

import dev.pott.kaputt.compiler.MutationPoint
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MutationPitXmlReportTest {
    @Test
    fun `GIVEN results WHEN rendering pit xml THEN structure and statuses match pit format`() {
        val xml =
            MutationPitXmlReport.render(
                listOf(
                    result(MutantStatus.KILLED),
                    result(MutantStatus.SURVIVED),
                    result(MutantStatus.TIMED_OUT),
                    result(MutantStatus.ERROR),
                ),
            )

        assertTrue(xml.startsWith("""<?xml version="1.0" encoding="UTF-8"?>"""))
        assertContains(xml, """<mutations partial="false">""")
        assertContains(xml, """<mutation detected="true" status="KILLED"""")
        assertContains(xml, """<mutation detected="false" status="SURVIVED"""")
        assertContains(xml, """<mutation detected="true" status="TIMED_OUT"""")
        assertContains(xml, """<mutation detected="false" status="RUN_ERROR"""")
        assertContains(xml, "<sourceFile>Score.kt</sourceFile>")
        assertContains(xml, "<mutatedClass>/repo/src/commonMain/kotlin/Score</mutatedClass>")
        assertContains(xml, "<mutatedMethod>isWinning</mutatedMethod>")
        assertContains(xml, "<lineNumber>12</lineNumber>")
        assertContains(xml, "<mutator>conditional-boundary</mutator>")
        assertTrue(xml.trimEnd().endsWith("</mutations>"))
    }

    @Test
    fun `GIVEN xml-sensitive characters WHEN rendering THEN they are escaped`() {
        val tricky =
            result(MutantStatus.SURVIVED).let { base ->
                base.copy(point = base.point.copy(description = "replaced '<' with \"&\""))
            }

        val xml = MutationPitXmlReport.render(listOf(tricky))

        assertContains(
            xml,
            "<description>replaced &#39;&lt;&#39; with &quot;&amp;&quot;</description>",
        )
    }

    @Test
    fun `GIVEN empty results WHEN rendering THEN empty mutations document`() {
        assertEquals(
            """<?xml version="1.0" encoding="UTF-8"?>
<mutations partial="false">
</mutations>""",
            MutationPitXmlReport.render(emptyList()),
        )
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
                ),
            status = status,
            durationMs = 250,
        )
}
