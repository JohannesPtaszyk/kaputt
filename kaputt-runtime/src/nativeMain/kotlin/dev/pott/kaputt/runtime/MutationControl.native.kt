package dev.pott.kaputt.runtime

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

@OptIn(ExperimentalForeignApi::class)
internal actual fun readActiveMutation(): String? = getenv(MUTATION_ENV)?.toKString()

@OptIn(ExperimentalForeignApi::class)
internal actual fun readDeadlineMillis(): Long = getenv(MUTATION_DEADLINE_ENV)?.toKString()?.toLongOrNull() ?: 0L

// Coverage collection is a jvm-only optimisation for now: native test binaries
// run every mutant, which stays correct, only slower.
internal actual fun isCoverageRun(): Boolean = false

internal actual fun recordReached(id: String) = Unit

internal actual fun writeCoverage() = Unit

actual fun mutationCurrentTest(name: String?) = Unit
