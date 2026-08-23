package dev.pott.kaputt.runtime

import org.junit.runner.Description
import org.junit.runner.notification.RunListener

/** Instrumentation argument naming the mutant an on-device run verifies. */
const val MUTATION_INSTRUMENTATION_ARG: String = "kotlinMutation"

/**
 * Activates a mutant for instrumented Android tests, which run inside the app
 * process where neither the environment nor system properties can be set from
 * the outside. Register it per run:
 *
 * ```
 * adb shell am instrument -w -e listener dev.pott.kaputt.runtime.MutationRunListener ...
 * ```
 */
class MutationRunListener : RunListener() {
    override fun testRunStarted(description: Description) {
        setActiveMutation(instrumentationArgument(MUTATION_INSTRUMENTATION_ARG))
    }
}

/**
 * Read reflectively so the runtime stays a plain JVM artifact: it is compiled
 * without android.jar and without a dependency on the test infrastructure.
 */
private fun instrumentationArgument(name: String): String? =
    runCatching {
        val registry = Class.forName("androidx.test.platform.app.InstrumentationRegistry")
        val arguments = registry.getMethod("getArguments").invoke(null)
        arguments.javaClass
            .getMethod("getString", String::class.java)
            .invoke(arguments, name) as String?
    }.getOrNull()?.takeIf(String::isNotEmpty)
