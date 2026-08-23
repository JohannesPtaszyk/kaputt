package dev.pott.kaputt.runtime

import org.junit.platform.engine.discovery.DiscoverySelectors
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory

/**
 * Drives JUnit Platform suites, which covers Jupiter and anything else with a
 * platform engine such as Kotest. The launcher API is the same on the 1.x
 * releases that ship with JUnit 5 and the 6.x ones.
 */
internal class PlatformDriver(
    private val loader: ClassLoader,
) : TestDriver {
    override fun runClasses(classNames: List<String>): Int = execute(classNames.map(DiscoverySelectors::selectClass))

    override fun runMethods(methods: List<String>): Int =
        execute(
            methods.map { method ->
                DiscoverySelectors.selectMethod(method.substringBefore('#'), method.substringAfter('#'))
            },
        )

    /** The platform decides what holds tests, so every candidate is offered. */
    override fun testClassNames(candidates: List<String>): List<String> = candidates

    private fun execute(selectors: List<org.junit.platform.engine.DiscoverySelector>): Int {
        if (selectors.isEmpty()) return MutationTestRunner.EXIT_NO_TESTS
        val request = LauncherDiscoveryRequestBuilder.request().selectors(selectors).build()
        val listener = CountingListener()
        val launcher = LauncherFactory.create()
        Thread.currentThread().contextClassLoader = loader
        launcher.execute(request, listener)
        println("Tests run: ${listener.run}, failures: ${listener.failures}")
        return when {
            listener.failures > 0 -> MutationTestRunner.EXIT_FAILURES
            listener.run == 0 -> MutationTestRunner.EXIT_NO_TESTS
            else -> MutationTestRunner.EXIT_SUCCESS
        }
    }

    private class CountingListener : TestExecutionListener {
        var run = 0
        var failures = 0

        override fun executionStarted(identifier: TestIdentifier) {
            if (identifier.isTest) mutationCurrentTest(identifier.testName())
        }

        override fun executionFinished(
            identifier: TestIdentifier,
            result: org.junit.platform.engine.TestExecutionResult,
        ) {
            if (!identifier.isTest) return
            run++
            if (result.status != org.junit.platform.engine.TestExecutionResult.Status.SUCCESSFUL) {
                failures++
                result.throwable.ifPresent {
                    println(
                        "${MutationTestRunner.FAILURE_PREFIX}" +
                            "${identifier.displayName}: ${it.message}",
                    )
                }
            }
            mutationCurrentTest(null)
            print('.')
            System.out.flush()
        }

        /** `Class#method`, matching the ids coverage records. */
        private fun TestIdentifier.testName(): String {
            val method =
                source
                    .filter { it is org.junit.platform.engine.support.descriptor.MethodSource }
                    .map { it as org.junit.platform.engine.support.descriptor.MethodSource }
            return if (method.isPresent) {
                "${method.get().className}#${method.get().methodName}"
            } else {
                legacyReportingName
            }
        }
    }
}
