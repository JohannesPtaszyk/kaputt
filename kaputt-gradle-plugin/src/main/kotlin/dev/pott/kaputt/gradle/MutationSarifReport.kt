package dev.pott.kaputt.gradle

import dev.pott.kaputt.gradle.JsonText.quote

/**
 * SARIF 2.1.0 export of the surviving mutants, for GitHub code scanning
 * (`github/codeql-action/upload-sarif`) and any other SARIF consumer.
 * Survivors are warnings anchored to their exact source span; killed mutants
 * are not findings and stay out of the file.
 */
object MutationSarifReport {
    fun render(
        results: List<MutantResult>,
        toolVersion: String,
        relativize: (String) -> String,
    ): String {
        val survivors = results.filter { it.status == MutantStatus.SURVIVED }
        val operators = survivors.map { it.point.operator }.distinct().sorted()
        return buildString {
            appendLine("{")
            appendLine("  \"\$schema\": \"https://json.schemastore.org/sarif-2.1.0.json\",")
            appendLine("  \"version\": \"2.1.0\",")
            appendLine("  \"runs\": [")
            appendLine("    {")
            appendLine("      \"tool\": {")
            appendLine("        \"driver\": {")
            appendLine("          \"name\": \"kotlin-mutation-testing\",")
            appendLine("          \"version\": ${quote(toolVersion)},")
            appendLine(
                "          \"informationUri\": \"https://github.com/JohannesPtaszyk/kaputt\",",
            )
            appendLine("          \"rules\": [")
            appendJsonRows(operators.map(::renderRule))
            appendLine("          ]")
            appendLine("        }")
            appendLine("      },")
            appendLine("      \"results\": [")
            appendJsonRows(survivors.map { renderResult(it, relativize) })
            appendLine("      ]")
            appendLine("    }")
            appendLine("  ]")
            append("}")
        }
    }

    private fun renderRule(operator: String): String =
        "            {\"id\": ${quote(ruleId(operator))}, " +
            "\"shortDescription\": {\"text\": " +
            "${quote("Mutant of operator '$operator' survived the test suite")}}}"

    private fun renderResult(
        result: MutantResult,
        relativize: (String) -> String,
    ): String {
        val point = result.point
        return buildString {
            appendLine("        {")
            appendLine("          \"ruleId\": ${quote(ruleId(point.operator))},")
            appendLine("          \"level\": \"warning\",")
            appendLine(
                "          \"message\": {\"text\": " +
                    "${quote(
                        "Survived mutant in '${point.function}': ${point.description}. " +
                            "No test detected this change.",
                    )}},",
            )
            appendLine("          \"locations\": [")
            appendLine("            {")
            appendLine("              \"physicalLocation\": {")
            appendLine(
                "                \"artifactLocation\": {\"uri\": " +
                    "${quote(relativize(point.filePath))}},",
            )
            append("                \"region\": {\"startLine\": ${point.line}")
            if (point.column > 0) append(", \"startColumn\": ${point.column}")
            if (point.endLine >= point.line && point.endColumn > 0) {
                append(", \"endLine\": ${point.endLine}, \"endColumn\": ${point.endColumn}")
            }
            appendLine("}")
            appendLine("              }")
            appendLine("            }")
            appendLine("          ]")
            append("        }")
        }
    }

    private fun ruleId(operator: String): String = "mutation-survived-$operator"
}
