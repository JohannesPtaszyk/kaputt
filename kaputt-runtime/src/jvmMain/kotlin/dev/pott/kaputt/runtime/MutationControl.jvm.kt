package dev.pott.kaputt.runtime

import java.io.File
import java.util.concurrent.ConcurrentHashMap

internal actual fun readActiveMutation(): String? = System.getProperty(MUTATION_PROPERTY) ?: System.getenv(MUTATION_ENV)

internal actual fun readDeadlineMillis(): Long = System.getenv(MUTATION_DEADLINE_ENV)?.toLongOrNull() ?: 0L

/** Mutation id to the tests that executed it; [ANY_TEST] means "attribution unknown". */
private val reachedByTest = ConcurrentHashMap<String, MutableSet<String>>()

private val coverageFile: File? = System.getenv(MUTATION_COVERAGE_ENV)?.let(::File)

@Volatile
private var currentTest: String? = null

actual fun mutationCurrentTest(name: String?) {
    currentTest = name
}

internal actual fun isCoverageRun(): Boolean = coverageFile != null

internal actual fun recordReached(id: String) {
    // Code reached outside a test, such as class initialisers or a thread
    // outliving the test that started it, cannot be attributed, so the mutant
    // falls back to the whole suite rather than being written off as unreached.
    val test = currentTest ?: ANY_TEST
    reachedByTest.computeIfAbsent(id) { ConcurrentHashMap.newKeySet() }.add(test)
}

internal actual fun writeCoverage() {
    val file = coverageFile ?: return
    file.parentFile?.mkdirs()
    file.writeText(
        reachedByTest.entries.joinToString(separator = "\n") { (id, tests) ->
            "$id\t${tests.joinToString(separator = ",")}"
        },
    )
}

const val ANY_TEST: String = "*"
