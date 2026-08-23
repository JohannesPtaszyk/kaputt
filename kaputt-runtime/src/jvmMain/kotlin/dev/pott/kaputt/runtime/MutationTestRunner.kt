package dev.pott.kaputt.runtime

import java.io.File

/**
 * Minimal JUnit 4 launcher used by the `mutationTest` Gradle task. It runs the
 * already-compiled test classes inside a fresh JVM so each mutant gets its own
 * `kotlin.mutation` system property.
 *
 * Exit codes: 0 = all tests passed, 1 = at least one failure,
 * 2 = internal error, 3 = no test classes discovered.
 */
object MutationTestRunner {
    const val EXIT_SUCCESS = 0
    const val EXIT_FAILURES = 1
    const val EXIT_INTERNAL_ERROR = 2
    const val EXIT_NO_TESTS = 3

    const val FILTER_ARG_PREFIX = "--filter="

    const val INCLUDE_ARG_PREFIX = "--include="
    const val EXCLUDE_ARG_PREFIX = "--exclude="

    /** `Class#method` selected from coverage; only these tests run. */
    const val METHOD_ARG_PREFIX = "--method="

    /** File of `id<TAB>Class#method,...` lines, one mutant per line. */
    const val BATCH_ARG_PREFIX = "--batch="

    /** Prefix of a per-mutant verdict line in batch mode. */
    const val VERDICT_PREFIX = "MUTANT "

    /** Prefix of the line a driver prints for each failing test. */
    const val FAILURE_PREFIX = "FAILED: "

    /** Printed once when a suite runs tests coverage cannot see. */
    const val OPAQUE_RUNNER_PREFIX = "MUTATION-OPAQUE-RUNNER "

    @JvmStatic
    fun main(args: Array<String>) {
        val exit =
            try {
                val batch = args.firstOrNull { it.startsWith(BATCH_ARG_PREFIX) }
                if (batch != null) {
                    runBatch(File(batch.removePrefix(BATCH_ARG_PREFIX)))
                } else {
                    run(
                        classDirs =
                            args
                                .filterNot { arg -> PREFIXES.any(arg::startsWith) }
                                .map(::File),
                        filter = args.valuesOf(FILTER_ARG_PREFIX).firstOrNull(),
                        includes = args.valuesOf(INCLUDE_ARG_PREFIX),
                        excludes = args.valuesOf(EXCLUDE_ARG_PREFIX),
                        methods = args.valuesOf(METHOD_ARG_PREFIX),
                    )
                }
            } catch (t: Throwable) {
                t.printStackTrace()
                EXIT_INTERNAL_ERROR
            }
        // Shutdown hooks are skipped: a mutant can leave native state where
        // teardown segfaults, turning a clean verdict into a jvm abort and a
        // report. The verdict is already decided here, so leave immediately.
        flushMutationCoverage()
        Runtime.getRuntime().halt(exit)
    }

    private fun Array<String>.valuesOf(prefix: String): List<String> = filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }

    /**
     * Gradle-style test filters, so a mutant run covers exactly the tests the
     * module's own test task covers. Matching is class-level: a pattern naming
     * a method keeps its class, and only a pattern matching the class itself
     * excludes it.
     */
    fun selectByPatterns(
        classNames: List<String>,
        includes: List<String>,
        excludes: List<String>,
    ): List<String> {
        val included =
            if (includes.isEmpty()) {
                classNames
            } else {
                classNames.filter { name ->
                    includes.any { pattern ->
                        patternRegex(pattern).matches(name) || pattern.startsWith("$name.")
                    }
                }
            }
        if (excludes.isEmpty()) return included
        return included.filterNot { name ->
            excludes.any { pattern -> patternRegex(pattern).matches(name) }
        }
    }

    private fun patternRegex(pattern: String): Regex = Regex(pattern.split('*').joinToString(".*") { Regex.escape(it) })

    private fun run(
        classDirs: List<File>,
        filter: String?,
        includes: List<String>,
        excludes: List<String>,
        methods: List<String> = emptyList(),
    ): Int {
        val driver = driver() ?: return EXIT_NO_TESTS
        if (methods.isNotEmpty()) return driver.runMethods(methods)
        val allNames =
            selectByPatterns(
                classNames = classDirs.flatMap(::scanClassNames).distinct(),
                includes = includes,
                excludes = excludes,
            )
        val selectedNames = selectByFilter(allNames, filter)
        val selected = driver.testClassNames(selectedNames)
        // A filter that matches no test class must never look like an empty
        // suite: the full list decides whether there is anything to run.
        val classes =
            if (selected.isEmpty() && selectedNames.size < allNames.size) {
                driver.testClassNames(allNames)
            } else {
                selected
            }
        if (classes.isEmpty()) return EXIT_NO_TESTS
        return driver.runClasses(classes)
    }

    /**
     * Whichever framework the consumer's test classpath carries. Each driver
     * is only loaded once chosen, so the absent one is never linked.
     */
    private fun driver(): TestDriver? =
        TestDrivers.forClasspath(Thread.currentThread().contextClassLoader).also {
            if (it == null) println("No JUnit 4 or JUnit Platform on the test classpath")
        }

    /**
     * Verifies many mutants in one process. Starting a JVM and loading the test
     * classpath costs far more than the few tests covering a mutation point, so
     * the classes are loaded once and the active mutant is switched between
     * runs. Each verdict is printed as it is decided, which also keeps the
     * parent's stall detection fed.
     */
    private fun runBatch(batchFile: File): Int {
        val driver = driver() ?: return EXIT_NO_TESTS
        batchFile.readLines().filter(String::isNotBlank).forEach { line ->
            val id = line.substringBefore('\t')
            val methods =
                line
                    .substringAfter('\t', "")
                    .split(',')
                    .filter(String::isNotBlank)
            setActiveMutation(id)
            val code =
                try {
                    driver.runMethods(methods)
                } catch (t: Throwable) {
                    t.printStackTrace()
                    EXIT_INTERNAL_ERROR
                }
            setActiveMutation(null)
            println("$VERDICT_PREFIX$id $code")
            System.out.flush()
        }
        return EXIT_SUCCESS
    }

    /**
     * Targeted mode: run only test classes whose simple name contains the
     * mutated file's base name. Matching happens on the scanned names so a
     * targeted run never loads the whole suite. Falls back to every name when
     * nothing matches, so a missing naming convention can never fake a
     * kill-proof run.
     */
    fun selectByFilter(
        classNames: List<String>,
        filter: String?,
    ): List<String> {
        if (filter.isNullOrBlank()) return classNames
        val matching =
            classNames.filter { name ->
                name.substringAfterLast('.').contains(filter, ignoreCase = false)
            }
        return matching.ifEmpty { classNames }
    }

    fun scanClassNames(dir: File): List<String> {
        if (!dir.isDirectory) return emptyList()
        return dir
            .walkTopDown()
            .filter { it.isFile && it.extension == "class" }
            .map {
                it
                    .relativeTo(dir)
                    .path
                    .removeSuffix(".class")
                    .replace(File.separatorChar, '.')
            }.filterNot { it.endsWith("module-info") || it.endsWith("package-info") }
            .toList()
    }

    private val PREFIXES =
        listOf(
            FILTER_ARG_PREFIX,
            INCLUDE_ARG_PREFIX,
            EXCLUDE_ARG_PREFIX,
            METHOD_ARG_PREFIX,
            BATCH_ARG_PREFIX,
        )

    private val OPAQUE_RUNNERS = listOf("Robolectric", "AndroidJUnit4")

    private const val MAX_PRINTED_FAILURES = 10
}
