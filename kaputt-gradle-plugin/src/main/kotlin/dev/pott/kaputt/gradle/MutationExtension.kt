package dev.pott.kaputt.gradle

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

/** Configured via `mutation { }` in a module's build script. */
abstract class MutationExtension {
    /** Only mutate files changed since this git ref, plus uncommitted changes. */
    abstract val since: Property<String>

    /** Run name-matched test classes first and re-verify survivors fully. Jvm only. */
    abstract val targetedTests: Property<Boolean>

    /** Cap of mutations per compiled function. Skipped mutations are reported. */
    abstract val maxMutationsPerFunction: Property<Int>

    /** Extra jvm Test tasks that verify mutants, keyed by manifest target name. */
    abstract val additionalJvmTestTasks: MapProperty<String, String>

    /** Also instrument `androidJvm` main compilations. Experimental. */
    abstract val instrumentAndroid: Property<Boolean>

    /** Android variant that carries the mutants, such as `debug` or `freeDebug`. */
    abstract val androidVariant: Property<String>

    /** Verify mutants with the instrumented tests of the variant, on a device. */
    abstract val instrumentedTests: Property<Boolean>

    /** Device the instrumented tests run on; unset picks the connected one. */
    abstract val deviceSerial: Property<String>

    /** Report mutants the baseline never executed as survived instead of running them. */
    abstract val coverageFiltering: Property<Boolean>

    /** Hard cap per mutant run, replacing the timeout derived from the baseline. */
    abstract val timeoutSeconds: Property<Long>

    /** Multiple of the baseline a mutant may take before it counts as hung. */
    abstract val timeoutFactor: Property<Double>

    /** How many mutants one fork verifies. Set 1 to start a fork per mutant. */
    abstract val batchSize: Property<Int>

    /** A fork reporting no finished test for this long counts as hung. Set 0 to disable. */
    abstract val stallSeconds: Property<Long>

    /** Cap for the baseline run, which executes the module's whole suite once. */
    abstract val baselineTimeoutSeconds: Property<Long>

    /** Number of mutant processes run concurrently. */
    abstract val parallelism: Property<Int>

    /** Fail the task when any mutant survives. */
    abstract val failOnSurvived: Property<Boolean>

    /** Fail the task when the mutation score in percent drops below this. */
    abstract val scoreThreshold: Property<Double>

    /** Globs limiting which files are mutated, matched against path and `package.FileName.kt`. */
    abstract val includes: ListProperty<String>

    /** Globs excluding files from mutation. */
    abstract val excludes: ListProperty<String>

    /** Operator keys to apply. Empty means all. */
    abstract val operators: ListProperty<String>

    /** Kotlin target names whose suites verify mutants. Empty means every jvm target. */
    abstract val targets: ListProperty<String>
}
