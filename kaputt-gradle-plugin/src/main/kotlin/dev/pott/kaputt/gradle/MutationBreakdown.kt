package dev.pott.kaputt.gradle

/**
 * Per-operator view of a run. The overall score says how much behavior the
 * tests pin down; this says what kind they miss.
 */
object MutationBreakdown {
    data class Row(
        val operator: String,
        val mutants: Int,
        val detected: Int,
        val survived: Int,
    ) {
        val score: Double get() = if (mutants == 0) 100.0 else detected * 100.0 / mutants
    }

    fun byOperator(results: List<MutantResult>): List<Row> =
        results
            .groupBy { it.point.operator }
            .map { (operator, group) ->
                Row(
                    operator = operator,
                    mutants = group.size,
                    detected =
                        group.count {
                            it.status == MutantStatus.KILLED || it.status == MutantStatus.TIMED_OUT
                        },
                    survived = group.count { it.status == MutantStatus.SURVIVED },
                )
            }.sortedWith(compareBy({ it.score }, { -it.mutants }))
}
