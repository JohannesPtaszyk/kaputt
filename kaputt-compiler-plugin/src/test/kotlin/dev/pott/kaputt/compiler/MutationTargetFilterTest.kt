package dev.pott.kaputt.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MutationTargetFilterTest {
    @Test
    fun `GIVEN no patterns WHEN matching THEN everything matches`() {
        val filter = MutationTargetFilter(includes = emptyList(), excludes = emptyList())

        assertTrue(filter.matches("/src/Foo.kt", "com.example.Foo.kt"))
    }

    @Test
    fun `GIVEN include on package WHEN matching THEN only that package matches`() {
        val filter =
            MutationTargetFilter(includes = listOf("com.example.*"), excludes = emptyList())

        assertTrue(filter.matches("/src/Foo.kt", "com.example.Foo.kt"))
        assertFalse(filter.matches("/src/Bar.kt", "org.other.Bar.kt"))
    }

    @Test
    fun `GIVEN exclude on file name WHEN matching THEN that file is filtered out`() {
        val filter = MutationTargetFilter(includes = emptyList(), excludes = listOf("*Generated*"))

        assertFalse(filter.matches("/src/GeneratedThing.kt", "com.example.GeneratedThing.kt"))
        assertTrue(filter.matches("/src/Thing.kt", "com.example.Thing.kt"))
    }

    @Test
    fun `GIVEN exclude wins over include WHEN both match THEN excluded`() {
        val filter =
            MutationTargetFilter(includes = listOf("com.example.*"), excludes = listOf("*Foo*"))

        assertFalse(filter.matches("/src/Foo.kt", "com.example.Foo.kt"))
    }

    @Test
    fun `GIVEN raw comma separated string WHEN parsePatterns THEN blank entries dropped`() {
        assertEquals(
            listOf("a*", "b?c"),
            MutationTargetFilter.parsePatterns(" a* , b?c ,, "),
        )
    }
}
