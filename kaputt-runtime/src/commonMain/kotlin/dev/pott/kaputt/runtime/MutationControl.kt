package dev.pott.kaputt.runtime

import kotlin.time.TimeSource

/** Environment variable selecting the active mutant; readable on every target. */
const val MUTATION_ENV: String = "KOTLIN_MUTATION"

/** JVM-only system property override, used by in-process tooling tests. */
const val MUTATION_PROPERTY: String = "kotlin.mutation"

/**
 * Environment variable pointing at the file a coverage run writes the ids of
 * every executed mutation point to. Set on the baseline run only.
 */
const val MUTATION_COVERAGE_ENV: String = "KOTLIN_MUTATION_COVERAGE"

/**
 * Environment variable holding how long a single mutant may run, in
 * milliseconds. Unset or zero leaves the loop deadline off.
 */
const val MUTATION_DEADLINE_ENV: String = "KOTLIN_MUTATION_DEADLINE_MS"

internal expect fun readActiveMutation(): String?

internal expect fun readDeadlineMillis(): Long

/** Records an executed mutation point; a no-op unless a coverage run is active. */
internal expect fun recordReached(id: String)

internal expect fun isCoverageRun(): Boolean

internal expect fun writeCoverage()

/**
 * Names the test now executing so coverage can record which tests reach each
 * mutation point. A mutant is then verified by exactly those tests: one that
 * never executes the mutated code cannot detect it.
 */
expect fun mutationCurrentTest(name: String?)

/**
 * Writes the coverage recorded so far. Called explicitly because the runner
 * halts the process instead of letting shutdown hooks run.
 */
fun flushMutationCoverage() {
    if (coverageRun) writeCoverage()
}

@Suppress("ObjectPropertyName")
private var _activeMutation: String? = readActiveMutation()

/**
 * Switches the active mutant inside a running process. Batch mode uses this to
 * verify many mutants per JVM: starting one costs far more than running the
 * handful of tests that cover a mutation point.
 */
fun setActiveMutation(id: String?) {
    _activeMutation = id
    startedAt = if (id != null && deadlineMillis > 0) TimeSource.Monotonic.markNow() else null
}

private val deadlineMillis: Long = readDeadlineMillis()
private var startedAt: TimeSource.Monotonic.ValueTimeMark? = null

/**
 * Thrown by the check compiled into every loop of instrumented code when the
 * active mutant has outrun its deadline. An [Error] rather than an exception,
 * because a mutant that broke a loop guard must not be swallowed by the
 * `catch` of the code under test.
 */
class MutationDeadlineExceeded internal constructor(
    id: String?,
) : Error("mutation: '$id' exceeded its deadline of $deadlineMillis ms in a loop")

/**
 * Compiled into the head of every loop body. A mutation that breaks a
 * loop-termination guard would otherwise run until the whole test process is
 * killed, which costs the full timeout and takes the rest of that run's
 * mutants down with it.
 */
fun mutationCheckDeadline() {
    val started = startedAt ?: return
    if (started.elapsedNow().inWholeMilliseconds >= deadlineMillis) {
        startedAt = null
        throw MutationDeadlineExceeded(_activeMutation)
    }
}

private val coverageRun: Boolean = isCoverageRun()

/**
 * Guard injected around every generated mutant. The active mutation is fixed
 * for the lifetime of the process so instrumented code pays a single read.
 * During a coverage run it also records that this point was executed: a
 * mutation no test ever reaches cannot be detected, so it never has to be run.
 */
fun isMutationActive(id: String): Boolean {
    if (coverageRun) recordReached(id)
    return id == _activeMutation
}

/** Called by generated safe-call mutants: emulates `!!` on a null receiver. */
fun mutationNullPointer(): Nothing = throw NullPointerException("mutation: safe call replaced with '!!' and the receiver was null")
