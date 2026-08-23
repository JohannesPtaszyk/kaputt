package dev.pott.kaputt.gradle

import dev.pott.kaputt.compiler.MutationPoint
import dev.pott.kaputt.gradle.JsonText.quote

object MutationReportRenderer {
    fun consoleSummary(
        module: String,
        results: List<MutantResult>,
    ): String {
        val counts = results.groupingBy { it.status }.eachCount()
        val ranked = SurvivorRanking.group(results)
        val highSignal = ranked.filter { SurvivorRanking.isHighSignal(it.first) }
        return buildString {
            appendLine("Mutation testing summary for $module")
            appendLine(
                "  ${highSignal.size} untested behaviors, " +
                    "${ranked.size - highSignal.size} other survivors",
            )
            appendLine(
                "  ${results.size} mutants: " +
                    "${counts.count(MutantStatus.KILLED)} killed, " +
                    "${counts.count(MutantStatus.TIMED_OUT)} timed out, " +
                    "${counts.count(MutantStatus.SURVIVED)} survived, " +
                    "${counts.count(MutantStatus.IGNORED)} ignored (baseline), " +
                    "${counts.count(MutantStatus.ERROR)} errors",
            )
            appendLine("  Mutation score: ${MutationScore.format(MutationScore.score(results))}%")
            val breakdown = MutationBreakdown.byOperator(results)
            if (breakdown.size > 1) {
                appendLine("  By mutation kind (weakest first):")
                breakdown.forEach { row ->
                    appendLine(
                        "    ${row.operator.padEnd(OPERATOR_COLUMN)}" +
                            "${MutationScore.format(row.score).padStart(6)}%  " +
                            "${row.survived} of ${row.mutants} survived",
                    )
                }
            }
            val worst = SurvivorRanking.worstFiles(results)
            if (worst.isNotEmpty()) {
                appendLine("  Worst files:")
                worst.forEach { (file, count) -> appendLine("    $file: $count") }
            }
            if (highSignal.isNotEmpty()) {
                appendLine("  Untested behavior, most actionable first:")
                highSignal.take(SURVIVOR_LIMIT).forEach { (result, count) ->
                    val repeat = if (count > 1) " (x$count)" else ""
                    appendLine(
                        "    - ${result.point.shortLocation()}: ${result.point.description}$repeat",
                    )
                }
                if (highSignal.size > SURVIVOR_LIMIT) {
                    appendLine("    ... ${highSignal.size - SURVIVOR_LIMIT} more in the report")
                }
            }
        }.trimEnd()
    }

    fun markdown(
        module: String,
        results: List<MutantResult>,
    ): String =
        buildString {
            val counts = results.groupingBy { it.status }.eachCount()
            appendLine("# Mutation report for $module")
            appendLine()
            appendLine(
                "Mutation score: **${MutationScore.format(MutationScore.score(results))}%** " +
                    "(${results.size} mutants)",
            )
            appendLine()
            appendLine("| Status | Count |")
            appendLine("| --- | --- |")
            MutantStatus.entries.forEach { status ->
                appendLine("| $status | ${counts.count(status)} |")
            }
            appendLine()
            appendLine("## By mutation kind")
            appendLine()
            appendLine("| Mutation | Mutants | Survived | Score |")
            appendLine("| --- | --- | --- | --- |")
            MutationBreakdown.byOperator(results).forEach { row ->
                appendLine(
                    "| ${row.operator} | ${row.mutants} | ${row.survived} | " +
                        "${MutationScore.format(row.score)}% |",
                )
            }
            val survivors = results.filter { it.status == MutantStatus.SURVIVED }
            if (survivors.isNotEmpty()) {
                appendLine()
                appendLine("## Survivors")
                appendLine()
                appendLine("| Location | Function | Operator | Mutation |")
                appendLine("| --- | --- | --- | --- |")
                survivors.forEach { result ->
                    val point = result.point
                    appendLine(
                        "| ${point.shortLocation()} | ${point.function} | ${point.operator} | ${point.description} |",
                    )
                }
            }
        }

    fun json(
        module: String,
        results: List<MutantResult>,
    ): String =
        buildString {
            appendLine("{")
            appendLine("  \"module\": ${quote(module)},")
            appendLine("  \"score\": ${MutationScore.format(MutationScore.score(results))},")
            appendLine("  \"byOperator\": [")
            appendJsonRows(
                MutationBreakdown.byOperator(results).map { row ->
                    "    {\"operator\": ${quote(row.operator)}, \"mutants\": ${row.mutants}, " +
                        "\"detected\": ${row.detected}, \"survived\": ${row.survived}, " +
                        "\"score\": ${MutationScore.format(row.score)}}"
                },
            )
            appendLine("  ],")
            appendLine("  \"mutants\": [")
            appendJsonRows(
                results.map { result ->
                    val point = result.point
                    "    {" +
                        "\"id\": ${quote(point.id)}, " +
                        "\"status\": ${quote(result.status.name)}, " +
                        "\"operator\": ${quote(point.operator)}, " +
                        "\"file\": ${quote(point.filePath)}, " +
                        "\"line\": ${point.line}, " +
                        "\"function\": ${quote(point.function)}, " +
                        "\"description\": ${quote(point.description)}, " +
                        "\"durationMs\": ${result.durationMs}" +
                        "}"
                },
            )
            appendLine("  ]")
            append("}")
        }

    private fun Map<MutantStatus, Int>.count(status: MutantStatus): Int = this[status] ?: 0

    private fun MutationPoint.shortLocation(): String = "${filePath.substringAfterLast('/')}:$line"

    private const val SURVIVOR_LIMIT = 15
    private const val OPERATOR_COLUMN = 22
}
