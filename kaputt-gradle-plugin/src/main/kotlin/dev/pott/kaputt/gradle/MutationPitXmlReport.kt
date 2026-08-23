package dev.pott.kaputt.gradle

/**
 * `mutations.xml` in PIT's report format, understood by SonarQube's
 * mutation-analysis plugin and other PIT-aware tools. (Stryker uses its own
 * schema, see [MutationStrykerReport]. GitHub consumes [MutationSarifReport].)
 *
 * Field mapping: `mutatedClass` carries the source file path minus extension
 * (this plugin mutates files, not JVM classes), `mutator` the operator key.
 */
object MutationPitXmlReport {
    fun render(results: List<MutantResult>): String =
        buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<mutations partial="false">""")
            results.forEach { result ->
                val point = result.point
                val detected =
                    result.status == MutantStatus.KILLED ||
                        result.status == MutantStatus.TIMED_OUT
                appendLine(
                    "  <mutation detected=\"$detected\" status=\"${pitStatus(result.status)}\" " +
                        "numberOfTestsRun=\"0\">",
                )
                appendLine(
                    "    <sourceFile>${escape(
                        point.filePath.substringAfterLast('/'),
                    )}</sourceFile>",
                )
                appendLine(
                    "    <mutatedClass>${escape(
                        point.filePath.removeSuffix(".kt"),
                    )}</mutatedClass>",
                )
                appendLine("    <mutatedMethod>${escape(point.function)}</mutatedMethod>")
                appendLine("    <methodDescription>${escape(point.function)}</methodDescription>")
                appendLine("    <lineNumber>${point.line}</lineNumber>")
                appendLine("    <mutator>${escape(point.operator)}</mutator>")
                appendLine("    <indexes><index>1</index></indexes>")
                appendLine("    <blocks><block>0</block></blocks>")
                appendLine("    <killingTest/>")
                appendLine("    <description>${escape(point.description)}</description>")
                appendLine("  </mutation>")
            }
            append("</mutations>")
        }

    private fun pitStatus(status: MutantStatus): String =
        when (status) {
            MutantStatus.KILLED -> "KILLED"

            MutantStatus.TIMED_OUT -> "TIMED_OUT"

            // PIT has no baselined status; ignored mutants stay SURVIVED here.
            MutantStatus.SURVIVED, MutantStatus.IGNORED -> "SURVIVED"

            MutantStatus.ERROR -> "RUN_ERROR"
        }

    internal fun escape(value: String): String = MarkupText.escape(value)
}
