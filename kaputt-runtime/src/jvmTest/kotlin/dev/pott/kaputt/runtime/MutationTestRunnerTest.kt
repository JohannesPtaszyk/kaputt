package dev.pott.kaputt.runtime

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MutationTestRunnerTest {
    @Test
    fun `GIVEN class dir with nested packages WHEN scanClassNames THEN dotted names returned`() {
        val dir = createTempDir()
        File(dir, "foo/bar").mkdirs()
        File(dir, "foo/bar/BazTest.class").writeBytes(ByteArray(0))
        File(dir, "foo/bar/BazTest\$Inner.class").writeBytes(ByteArray(0))
        File(dir, "foo/notAClass.txt").writeBytes(ByteArray(0))

        val names = MutationTestRunner.scanClassNames(dir)

        assertEquals(listOf("foo.bar.BazTest", "foo.bar.BazTest\$Inner"), names.sorted())
    }

    @Test
    fun `GIVEN missing dir WHEN scanClassNames THEN empty`() {
        assertTrue(MutationTestRunner.scanClassNames(File("does/not/exist")).isEmpty())
    }

    @Test
    fun `GIVEN class with junit4 test method WHEN isRunnableTestClass THEN true`() {
        assertTrue(JUnit4Driver.isRunnableTestClass(SampleTest::class.java))
    }

    @Test
    fun `GIVEN class inheriting test methods WHEN isRunnableTestClass THEN true`() {
        assertTrue(JUnit4Driver.isRunnableTestClass(InheritingTest::class.java))
    }

    @Test
    fun `GIVEN abstract class WHEN isRunnableTestClass THEN false`() {
        assertFalse(JUnit4Driver.isRunnableTestClass(AbstractBase::class.java))
    }

    @Test
    fun `GIVEN class without test methods WHEN isRunnableTestClass THEN false`() {
        assertFalse(JUnit4Driver.isRunnableTestClass(NotATest::class.java))
    }

    @Test
    fun `GIVEN matching filter WHEN selectByFilter THEN only matching classes run`() {
        val classNames = listOf("foo.bar.SampleTest", "foo.bar.InheritingTest")

        assertEquals(
            listOf("foo.bar.SampleTest"),
            MutationTestRunner.selectByFilter(classNames, "Sample"),
        )
    }

    @Test
    fun `GIVEN filter without matches WHEN selectByFilter THEN falls back to full suite`() {
        val classNames = listOf("foo.bar.SampleTest", "foo.bar.InheritingTest")

        assertEquals(classNames, MutationTestRunner.selectByFilter(classNames, "NoSuchFile"))
        assertEquals(classNames, MutationTestRunner.selectByFilter(classNames, null))
    }

    private fun createTempDir(): File =
        File.createTempFile("runner-test", "").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }

    abstract class AbstractBase {
        @org.junit.Test
        fun something() = Unit
    }

    class SampleTest {
        @org.junit.Test
        fun something() = Unit
    }

    class InheritingTest : AbstractBase()

    class NotATest {
        fun plain() = Unit
    }
}
