package dev.pott.kaputt.gradle

/**
 * Orders survivors by how likely they are to be worth acting on. Operators
 * that change control flow point at untested behavior; a changed literal in a
 * data holder usually points at nothing a test should pin down.
 */
object SurvivorRanking {
    private val RANK =
        listOf(
            "conditional-boundary",
            "negate-conditional",
            "logical-operator",
            "range-boundary",
            "math",
            "remove-not",
            "safe-call",
            "empty-return",
            "void-call-removal",
            "boolean-literal",
            "number-literal",
            "string-literal",
        ).withIndex().associate { (index, operator) -> operator to index }

    /**
     * Survivors of an operator that changes behavior rather than a value. A
     * forced return or a dropped call is the strongest of these: it says a
     * whole branch of what the code does is unasserted.
     */
    fun isHighSignal(result: MutantResult): Boolean = (RANK[result.point.operator] ?: RANK.size) <= RANK.getValue("void-call-removal")

    data class Group(
        val first: MutantResult,
        val count: Int,
    )

    /**
     * Groups survivors repeated at the same place and orders them so the
     * control-flow changes come first.
     */
    fun group(results: List<MutantResult>): List<Group> =
        results
            .filter { it.status == MutantStatus.SURVIVED }
            .groupBy { Triple(it.point.filePath, it.point.line, it.point.description) }
            .map { (_, group) -> Group(group.first(), group.size) }
            .sortedWith(
                compareBy(
                    { RANK[it.first.point.operator] ?: RANK.size },
                    { it.first.point.filePath },
                    { it.first.point.line },
                ),
            )

    /** Files with the most control-flow survivors, worst first. */
    fun worstFiles(
        results: List<MutantResult>,
        limit: Int = 5,
    ): List<Pair<String, Int>> =
        results
            .filter { it.status == MutantStatus.SURVIVED && isHighSignal(it) }
            .groupingBy { it.point.filePath.substringAfterLast('/') }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { it.key to it.value }
}
