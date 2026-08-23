package dev.pott.kaputt.runtime

/**
 * Runs the tests of one process. Implementations reference their framework
 * directly, so the one that is missing from a consumer's classpath is never
 * loaded.
 */
internal interface TestDriver {
    /** Runs whole test classes, returning an exit code. */
    fun runClasses(classNames: List<String>): Int

    /** Runs exactly the `Class#method` tests coverage attributed to a mutant. */
    fun runMethods(methods: List<String>): Int

    /** Class names that hold tests this driver can run. */
    fun testClassNames(candidates: List<String>): List<String>
}

internal object TestDrivers {
    fun forClasspath(loader: ClassLoader): TestDriver? =
        when {
            loader.canLoad("org.junit.runner.JUnitCore") -> JUnit4Driver(loader)
            loader.canLoad("org.junit.platform.launcher.core.LauncherFactory") -> PlatformDriver(loader)
            else -> null
        }

    private fun ClassLoader.canLoad(name: String): Boolean =
        try {
            Class.forName(name, false, this)
            true
        } catch (_: Throwable) {
            false
        }
}
