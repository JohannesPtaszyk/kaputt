package dev.pott.kaputt.runtime

import org.junit.runner.Description
import org.junit.runner.JUnitCore
import org.junit.runner.Request
import org.junit.runner.Result
import org.junit.runner.manipulation.Filter
import org.junit.runner.notification.RunListener
import java.lang.reflect.Modifier

/** Drives JUnit 4 suites, which is what kotlin-test maps to on the jvm. */
internal class JUnit4Driver(
    private val loader: ClassLoader,
) : TestDriver {
    override fun runClasses(classNames: List<String>): Int {
        val classes = load(classNames)
        if (classes.isEmpty()) return MutationTestRunner.EXIT_NO_TESTS
        reportOpaqueRunners(classes)
        return report(newCore().run(*classes.toTypedArray()))
    }

    override fun runMethods(methods: List<String>): Int {
        val wanted = methods.toSet()
        val classes = load(methods.map { it.substringBefore('#') }.distinct())
        if (classes.isEmpty()) return MutationTestRunner.EXIT_NO_TESTS
        val request =
            Request.classes(*classes.toTypedArray()).filterWith(
                object : Filter() {
                    override fun shouldRun(description: Description): Boolean = coverageSelects(description, wanted)

                    override fun describe(): String = "mutation coverage selection"
                },
            )
        return report(newCore().run(request))
    }

    override fun testClassNames(candidates: List<String>): List<String> = load(candidates).map { it.name }

    private fun load(names: List<String>): List<Class<*>> =
        names
            .mapNotNull { name ->
                try {
                    Class.forName(name, false, loader)
                } catch (_: Throwable) {
                    null
                }
            }.filter(::isRunnableTestClass)

    private fun newCore(): JUnitCore =
        JUnitCore().apply {
            addListener(
                object : RunListener() {
                    override fun testStarted(description: Description) {
                        mutationCurrentTest("${description.className}#${description.methodName}")
                    }

                    override fun testFinished(description: Description) {
                        mutationCurrentTest(null)
                        print('.')
                        System.out.flush()
                    }
                },
            )
        }

    private fun report(result: Result): Int {
        println("Tests run: ${result.runCount}, failures: ${result.failureCount}")
        result.failures.take(MAX_PRINTED_FAILURES).forEach { failure ->
            println("${MutationTestRunner.FAILURE_PREFIX}${failure.testHeader}: ${failure.message}")
        }
        return if (result.wasSuccessful()) {
            MutationTestRunner.EXIT_SUCCESS
        } else {
            MutationTestRunner.EXIT_FAILURES
        }
    }

    /**
     * Runners that load the code under test in their own classloader, such as
     * Robolectric, keep their execution out of the coverage this process
     * records, so the run says so rather than filtering their mutants away.
     */
    private fun reportOpaqueRunners(classes: List<Class<*>>) {
        val runners =
            classes
                .mapNotNull { it.getAnnotation(org.junit.runner.RunWith::class.java)?.value?.qualifiedName }
                .filter { name -> OPAQUE_RUNNERS.any(name::contains) }
                .distinct()
        if (runners.isEmpty()) return
        println("${MutationTestRunner.OPAQUE_RUNNER_PREFIX}${runners.joinToString()}")
        System.out.flush()
    }

    private fun isRunnableTestClass(candidate: Class<*>): Boolean = Companion.isRunnableTestClass(candidate)

    internal companion object {
        fun isRunnableTestClass(candidate: Class<*>): Boolean {
            if (candidate.isInterface || candidate.isAnnotation || candidate.isEnum) return false
            if (Modifier.isAbstract(candidate.modifiers)) return false
            if (candidate.enclosingClass != null && !Modifier.isStatic(candidate.modifiers)) return false
            if (candidate.getAnnotation(org.junit.runner.RunWith::class.java) != null) return true
            return generateSequence<Class<*>>(candidate) { it.superclass }
                .takeWhile { it != Any::class.java }
                .flatMap { it.declaredMethods.asSequence() }
                .any { it.getAnnotation(org.junit.Test::class.java) != null }
        }

        private val OPAQUE_RUNNERS = listOf("Robolectric", "AndroidJUnit4")
        private const val MAX_PRINTED_FAILURES = 10

        /** Visible for tests: whether coverage attributed this description. */
        fun coverageSelects(
            description: Description,
            wanted: Set<String>,
        ): Boolean =
            if (description.isTest) {
                "${description.className}#${description.methodName}" in wanted
            } else {
                description.children.any { child -> coverageSelects(child, wanted) }
            }
    }
}
