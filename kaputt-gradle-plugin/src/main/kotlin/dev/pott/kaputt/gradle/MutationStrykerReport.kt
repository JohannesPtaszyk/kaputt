package dev.pott.kaputt.gradle

import dev.pott.kaputt.gradle.JsonText.quote

/**
 * Stryker's mutation-testing-report-schema (v2) JSON, consumed by the Stryker
 * dashboard and the `mutation-testing-elements` interactive viewer. The
 * schema requires each file's full source, so files whose sources cannot be
 * read are skipped.
 */
object MutationStrykerReport {
    fun render(
        results: List<MutantResult>,
        sourceLookup: (String) -> List<String>?,
        relativize: (String) -> String,
    ): String {
        val files =
            results
                .groupBy { it.point.filePath }
                .mapNotNull { (path, fileResults) ->
                    val source = sourceLookup(path) ?: return@mapNotNull null
                    ReportedFile(relativize(path), source, fileResults)
                }.sortedBy { it.path }
        return buildString {
            appendLine("{")
            appendLine("  \"schemaVersion\": \"2\",")
            appendLine("  \"thresholds\": {\"high\": 80, \"low\": 60},")
            appendLine("  \"files\": {")
            appendJsonRows(files.map(::renderFile))
            appendLine("  }")
            append("}")
        }
    }

    private class ReportedFile(
        val path: String,
        val source: List<String>,
        val results: List<MutantResult>,
    )

    private fun renderFile(file: ReportedFile): String =
        buildString {
            appendLine("    ${quote(file.path)}: {")
            appendLine("      \"language\": \"kotlin\",")
            appendLine("      \"source\": ${quote(file.source.joinToString(separator = "\n"))},")
            appendLine("      \"mutants\": [")
            appendJsonRows(file.results.map(::renderMutant))
            appendLine("      ]")
            append("    }")
        }

    private fun renderMutant(result: MutantResult): String {
        val point = result.point
        val endLine = if (point.endLine >= point.line) point.endLine else point.line
        val endColumn = if (point.endColumn > 0) point.endColumn else point.column + 1
        return "        {" +
            "\"id\": ${quote(point.id)}, " +
            "\"mutatorName\": ${quote(point.operator)}, " +
            "\"description\": ${quote(point.description)}, " +
            "\"status\": ${quote(strykerStatus(result.status))}, " +
            "\"location\": {" +
            "\"start\": {\"line\": ${point.line}, \"column\": ${point.column.coerceAtLeast(1)}}, " +
            "\"end\": {\"line\": $endLine, \"column\": $endColumn}" +
            "}" +
            "}"
    }

    private fun strykerStatus(status: MutantStatus): String =
        when (status) {
            MutantStatus.KILLED -> "Killed"
            MutantStatus.SURVIVED -> "Survived"
            MutantStatus.IGNORED -> "Ignored"
            MutantStatus.TIMED_OUT -> "Timeout"
            MutantStatus.ERROR -> "RuntimeError"
        }
}
