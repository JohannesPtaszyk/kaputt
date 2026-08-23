package dev.pott.kaputt.compiler

import dev.pott.kaputt.compiler.MutationCompilerHarness.invokeStatic
import dev.pott.kaputt.compiler.MutationCompilerHarness.withMutant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MutationCompilerPluginTest {
    @Test
    fun `GIVEN plus expression WHEN math mutant active THEN operator flips to minus`() {
        val mutation = mutation("add", MutationOperator.MATH)

        assertEquals("replaced '+' with '-'", mutation.description)
        assertEquals(5, add(mutationId = null, 2, 3))
        assertEquals(-1, add(mutationId = mutation.id, 2, 3))
    }

    @Test
    fun `GIVEN greaterOrEqual WHEN boundary mutant active THEN boundary case flips`() {
        val mutation = mutation("isAdult", MutationOperator.CONDITIONAL_BOUNDARY)

        assertEquals("replaced '>=' with '>'", mutation.description)
        assertEquals(true, isAdult(mutationId = null, 18))
        assertEquals(false, isAdult(mutationId = mutation.id, 18))
        assertEquals(true, isAdult(mutationId = mutation.id, 19))
    }

    @Test
    fun `GIVEN greaterOrEqual WHEN negate mutant active THEN comparison is inverted`() {
        val mutation = mutation("isAdult", MutationOperator.NEGATE_CONDITIONAL)

        assertEquals("negated '>='", mutation.description)
        assertEquals(false, isAdult(mutationId = null, 17))
        assertEquals(true, isAdult(mutationId = mutation.id, 17))
        assertEquals(false, isAdult(mutationId = mutation.id, 18))
    }

    @Test
    fun `GIVEN boolean literal WHEN literal mutant active THEN value flips`() {
        val mutation = mutation("alwaysTrue", MutationOperator.BOOLEAN_LITERAL)

        assertEquals("replaced 'true' with 'false'", mutation.description)
        assertEquals(true, runFixture("alwaysTrue", mutationId = null))
        assertEquals(false, runFixture("alwaysTrue", mutationId = mutation.id))
    }

    @Test
    fun `GIVEN not expression WHEN remove-not mutant active THEN negation disappears`() {
        val mutation = mutation("negate", MutationOperator.REMOVE_NOT)

        assertEquals("removed '!'", mutation.description)
        assertEquals(false, runFixture("negate", mutationId = null, true))
        assertEquals(true, runFixture("negate", mutationId = mutation.id, true))
    }

    @Test
    fun `GIVEN unit call statement WHEN removal mutant active THEN side effect is gone`() {
        val mutation =
            fixture.mutations.single { point ->
                point.operator == MutationOperator.VOID_CALL_REMOVAL.key &&
                    point.description == "removed call to 'increment'"
            }

        assertEquals(1, bumpCounter(mutationId = null))
        assertEquals(0, bumpCounter(mutationId = mutation.id))
    }

    @Test
    fun `GIVEN equality on strings WHEN negate mutant active THEN comparison inverted`() {
        val mutation = mutation("sameWord", MutationOperator.NEGATE_CONDITIONAL)

        assertEquals(true, runFixture("sameWord", mutationId = null, "a", "a"))
        assertEquals(false, runFixture("sameWord", mutationId = mutation.id, "a", "a"))
    }

    @Test
    fun `GIVEN DisableMutation annotation WHEN compiling THEN no mutations inside`() {
        assertTrue(fixture.mutations.none { it.function == "untouchable" })
    }

    @Test
    fun `GIVEN inline function WHEN compiling THEN it is not mutated`() {
        assertTrue(fixture.mutations.none { it.function == "inlined" })
    }

    @Test
    fun `GIVEN elvis operator WHEN compiling THEN baseline works and negate mutant flips fallback`() {
        val mutation = mutation("orDefault", MutationOperator.NEGATE_CONDITIONAL)

        assertEquals(5, runFixture("orDefault", mutationId = null, 5))
        assertEquals(-1, runFixture("orDefault", mutationId = null, null))
        assertEquals(-1, runFixture("orDefault", mutationId = mutation.id, 5))
    }

    @Test
    fun `GIVEN comparison in if condition WHEN boundary mutant active THEN branch flips`() {
        val mutation = mutation("label", MutationOperator.CONDITIONAL_BOUNDARY)

        assertEquals("high", runFixture("label", mutationId = null, 11))
        assertEquals("low", runFixture("label", mutationId = null, 10))
        assertEquals("high", runFixture("label", mutationId = mutation.id, 10))
    }

    @Test
    fun `GIVEN when with subject WHEN negate mutant active THEN branches flip`() {
        val mutation = mutation("rank", MutationOperator.NEGATE_CONDITIONAL)

        assertEquals("zero", runFixture("rank", mutationId = null, 0))
        assertEquals("other", runFixture("rank", mutationId = null, 1))
        assertEquals("other", runFixture("rank", mutationId = mutation.id, 0))
        assertEquals("zero", runFixture("rank", mutationId = mutation.id, 1))
    }

    @Test
    fun `GIVEN boolean literals in branch results WHEN literal mutant active THEN result flips`() {
        val mutation =
            fixture.mutations.single { point ->
                point.function == "toggle" && point.description == "replaced 'false' with 'true'"
            }

        assertEquals(false, runFixture("toggle", mutationId = null, true))
        assertEquals(true, runFixture("toggle", mutationId = null, false))
        assertEquals(true, runFixture("toggle", mutationId = mutation.id, true))
    }

    @Test
    fun `GIVEN explicit not() call WHEN remove-not mutant active THEN negation disappears`() {
        val mutation = mutation("explicitNot", MutationOperator.REMOVE_NOT)

        assertEquals(false, runFixture("explicitNot", mutationId = null, true))
        assertEquals(true, runFixture("explicitNot", mutationId = mutation.id, true))
    }

    @Test
    fun `GIVEN number literal WHEN increment mutant active THEN value is incremented`() {
        val mutation =
            fixture.mutations.single { point ->
                point.function == "addPercent" && point.description == "replaced '100' with '101'"
            }

        assertEquals(101, runFixture("addPercent", mutationId = null, 1))
        assertEquals(102, runFixture("addPercent", mutationId = mutation.id, 1))
    }

    @Test
    fun `GIVEN number literal WHEN decrement mutant active THEN value is decremented`() {
        val mutation =
            fixture.mutations.single { point ->
                point.function == "addPercent" && point.description == "replaced '100' with '99'"
            }

        assertEquals(100, runFixture("addPercent", mutationId = mutation.id, 1))
    }

    @Test
    fun `GIVEN string literal WHEN string mutant active THEN empty string is used`() {
        val mutation = mutation("greeting", MutationOperator.STRING_LITERAL)

        assertEquals("replaced \"hello\" with \"\"", mutation.description)
        assertEquals("hello", runFixture("greeting", mutationId = null))
        assertEquals("", runFixture("greeting", mutationId = mutation.id))
    }

    @Test
    fun `GIVEN safe call WHEN safe-call mutant active THEN null receiver throws`() {
        val mutation = mutation("lengthOrNull", MutationOperator.SAFE_CALL)

        assertEquals("replaced '?.' with '!!'", mutation.description)
        assertEquals(null, runFixture("lengthOrNull", mutationId = null, null))
        assertEquals(2, runFixture("lengthOrNull", mutationId = null, "ab"))
        assertEquals(2, runFixture("lengthOrNull", mutationId = mutation.id, "ab"))
        assertFailsWith<java.lang.reflect.InvocationTargetException> {
            runFixture("lengthOrNull", mutationId = mutation.id, null)
        }
    }

    @Test
    fun `GIVEN mutation cap WHEN compiling THEN excess mutations are reported not silent`() {
        val capped =
            MutationCompilerHarness.compile(
                """
                package fixture

                fun many(a: Int, b: Int): Int = a + b - a * b
                """.trimIndent(),
                extraPluginOptions = listOf("maxMutationsPerFunction=1", "operators=math"),
            )

        assertEquals(1, capped.mutations.size)
        val skipped = capped.skipped.single()
        assertEquals("many", skipped.function)
        assertEquals(2, skipped.skipped)
    }

    @Test
    fun `GIVEN boolean return WHEN return mutants active THEN both values are forced`() {
        val mutants =
            fixture.mutations.filter {
                it.function == "isAdult" && it.operator == MutationOperator.EMPTY_RETURN.key
            }

        assertEquals(
            listOf("replaced return value with false", "replaced return value with true"),
            mutants.map { it.description }.sorted(),
        )
        assertEquals(true, isAdult(mutationId = null, age = 20))
        val forcedFalse = mutants.single { it.description.endsWith("false") }
        val forcedTrue = mutants.single { it.description.endsWith("true") }
        assertEquals(false, isAdult(forcedFalse.id, age = 20))
        assertEquals(true, isAdult(forcedTrue.id, age = 10))
    }

    @Test
    fun `GIVEN constant boolean return WHEN planning THEN the literal operator owns it`() {
        assertTrue(
            fixture.mutations.none {
                it.function == "alwaysTrue" && it.operator == MutationOperator.EMPTY_RETURN.key
            },
        )
    }

    @Test
    fun `GIVEN string return WHEN empty-return mutant active THEN empty string returned`() {
        val mutation = mutation("greeting", MutationOperator.EMPTY_RETURN)

        assertEquals("hello", runFixture("greeting", mutationId = null))
        assertEquals("", runFixture("greeting", mutationId = mutation.id))
    }

    @Test
    fun `GIVEN list return WHEN empty-return mutant active THEN empty list returned`() {
        val mutation = mutation("names", MutationOperator.EMPTY_RETURN)

        assertEquals(2, (runFixture("names", mutationId = null) as List<*>).size)
        assertEquals(0, (runFixture("names", mutationId = mutation.id) as List<*>).size)
    }

    @Test
    fun `GIVEN until range WHEN range mutant active THEN range becomes inclusive`() {
        val mutation = mutation("countUp", MutationOperator.RANGE_BOUNDARY)

        assertEquals("replaced 'until' with '..'", mutation.description)
        assertEquals(3, runFixture("countUp", mutationId = null, 3))
        assertEquals(4, runFixture("countUp", mutationId = mutation.id, 3))
    }

    @Test
    fun `GIVEN and expression WHEN logical mutant active THEN it behaves like or`() {
        val mutation = mutation("bothPositive", MutationOperator.LOGICAL_OPERATOR)

        assertEquals("replaced '&&' with '||'", mutation.description)
        assertEquals(false, runFixture("bothPositive", mutationId = null, 1, -1))
        assertEquals(true, runFixture("bothPositive", mutationId = mutation.id, 1, -1))
        assertEquals(false, runFixture("bothPositive", mutationId = mutation.id, -1, -1))
    }

    @Test
    fun `GIVEN or expression WHEN logical mutant active THEN it behaves like and`() {
        val mutation = mutation("eitherPositive", MutationOperator.LOGICAL_OPERATOR)

        assertEquals("replaced '||' with '&&'", mutation.description)
        assertEquals(true, runFixture("eitherPositive", mutationId = null, -1, 1))
        assertEquals(false, runFixture("eitherPositive", mutationId = mutation.id, -1, 1))
        assertEquals(true, runFixture("eitherPositive", mutationId = mutation.id, 1, 1))
    }

    @Test
    fun `GIVEN each math operator WHEN its mutant is active THEN the operation flips`() {
        val minus = mutation("subtract", MutationOperator.MATH)
        val times = mutation("scale", MutationOperator.MATH)
        val div = mutation("ratio", MutationOperator.MATH)
        val rem = mutation("remainder", MutationOperator.MATH)

        assertEquals("replaced '-' with '+'", minus.description)
        assertEquals(3, runFixture("subtract", mutationId = null, 5, 2))
        assertEquals(7, runFixture("subtract", mutationId = minus.id, 5, 2))

        assertEquals("replaced '*' with '/'", times.description)
        assertEquals(12, runFixture("scale", mutationId = null, 3, 4))
        assertEquals(0, runFixture("scale", mutationId = times.id, 3, 4))

        assertEquals("replaced '/' with '*'", div.description)
        assertEquals(4, runFixture("ratio", mutationId = null, 8, 2))
        assertEquals(16, runFixture("ratio", mutationId = div.id, 8, 2))

        assertEquals("replaced '%' with '*'", rem.description)
        assertEquals(3, runFixture("remainder", mutationId = null, 7, 4))
        assertEquals(28, runFixture("remainder", mutationId = rem.id, 7, 4))
    }

    @Test
    fun `GIVEN less-than boundaries WHEN boundary mutants active THEN edges flip`() {
        val less = mutation("isMinor", MutationOperator.CONDITIONAL_BOUNDARY)
        val lessOrEqual = mutation("atMost", MutationOperator.CONDITIONAL_BOUNDARY)

        assertEquals("replaced '<' with '<='", less.description)
        assertEquals(false, runFixture("isMinor", mutationId = null, 18))
        assertEquals(true, runFixture("isMinor", mutationId = less.id, 18))

        assertEquals("replaced '<=' with '<'", lessOrEqual.description)
        assertEquals(true, runFixture("atMost", mutationId = null, 2, 2))
        assertEquals(false, runFixture("atMost", mutationId = lessOrEqual.id, 2, 2))
    }

    @Test
    fun `GIVEN double equality WHEN negate mutant active THEN ieee754 comparison inverts`() {
        val mutation = mutation("sameAmount", MutationOperator.NEGATE_CONDITIONAL)

        assertEquals(true, runFixture("sameAmount", mutationId = null, 1.0, 1.0))
        assertEquals(false, runFixture("sameAmount", mutationId = mutation.id, 1.0, 1.0))
        assertEquals(true, runFixture("sameAmount", mutationId = mutation.id, 1.0, 2.0))
    }

    @Test
    fun `GIVEN long literal WHEN literal mutant active THEN long value shifts`() {
        val mutation =
            fixture.mutations.single { point ->
                point.function == "bigOffset" && point.description == "replaced '10' with '11'"
            }

        assertEquals(11L, runFixture("bigOffset", mutationId = null, 1L))
        assertEquals(12L, runFixture("bigOffset", mutationId = mutation.id, 1L))
    }

    @Test
    fun `GIVEN empty or default values WHEN compiling THEN no equivalent mutants are generated`() {
        assertTrue(
            fixture.mutations.none {
                it.function == "blank" &&
                    it.operator == MutationOperator.STRING_LITERAL.key
            },
        )
        assertTrue(
            fixture.mutations.none {
                it.function == "zero" &&
                    it.operator == MutationOperator.EMPTY_RETURN.key
            },
        )
        assertTrue(
            fixture.mutations.none {
                it.function == "lengthOrNull" &&
                    it.operator == MutationOperator.EMPTY_RETURN.key
            },
        )
    }

    @Test
    fun `GIVEN int set and map returns WHEN empty-return mutants active THEN defaults returned`() {
        val int = mutation("add", MutationOperator.EMPTY_RETURN)
        val set = mutation("tags", MutationOperator.EMPTY_RETURN)
        val map = mutation("lookup", MutationOperator.EMPTY_RETURN)

        assertEquals(0, runFixture("add", mutationId = int.id, 2, 3))
        assertEquals(1, (runFixture("tags", mutationId = null) as Set<*>).size)
        assertEquals(0, (runFixture("tags", mutationId = set.id) as Set<*>).size)
        assertEquals(0, (runFixture("lookup", mutationId = map.id) as Map<*, *>).size)
    }

    @Test
    fun `GIVEN inclusive range WHEN range mutant active THEN range becomes exclusive`() {
        val mutation = mutation("countInclusive", MutationOperator.RANGE_BOUNDARY)

        assertEquals("replaced '..' with 'until'", mutation.description)
        assertEquals(4, runFixture("countInclusive", mutationId = null, 3))
        assertEquals(3, runFixture("countInclusive", mutationId = mutation.id, 3))
    }

    @Test
    fun `GIVEN string concatenation and data class WHEN compiling THEN they are not mutated`() {
        val generatedMembers =
            setOf("equals", "hashCode", "toString", "copy", "component1", "component2")

        assertTrue(
            fixture.mutations.none {
                it.function == "concat" &&
                    it.operator == MutationOperator.MATH.key
            },
        )
        assertTrue(fixture.mutations.none { it.function in generatedMembers })
    }

    @Test
    fun `GIVEN removal guards WHEN compiling THEN instrumentation itself is never mutated`() {
        val bumpMutations = fixture.mutations.filter { it.function == "bump" }

        assertTrue(bumpMutations.all { it.operator == MutationOperator.VOID_CALL_REMOVAL.key })
    }

    @Test
    fun `GIVEN const val usage WHEN compiling THEN compilation succeeds and baseline unchanged`() {
        assertEquals(true, runFixture("flag", mutationId = null))
    }

    @Test
    fun `GIVEN compiled fixture THEN mutation ids are unique and lines are positive`() {
        val ids = fixture.mutations.map(MutationPoint::id)

        assertEquals(ids.size, ids.distinct().size)
        assertTrue(fixture.mutations.all { it.line > 0 })
        assertTrue(fixture.mutations.all { it.filePath.endsWith("Fixture.kt") })
    }

    @Test
    fun `GIVEN exclude pattern WHEN compiling THEN no mutations are generated`() {
        val excluded =
            MutationCompilerHarness.compile(
                FIXTURE_SOURCE,
                extraPluginOptions = listOf("excludes=*Fixture*"),
            )

        assertTrue(excluded.mutations.isEmpty())
    }

    @Test
    fun `GIVEN operators option WHEN compiling THEN only requested operators emitted`() {
        val onlyMath =
            MutationCompilerHarness.compile(
                FIXTURE_SOURCE,
                extraPluginOptions = listOf("operators=math"),
            )

        assertTrue(onlyMath.mutations.isNotEmpty())
        assertTrue(onlyMath.mutations.all { it.operator == MutationOperator.MATH.key })
    }

    private fun mutation(
        function: String,
        operator: MutationOperator,
    ): MutationPoint = fixture.mutations.single { it.function == function && it.operator == operator.key }

    private fun add(
        mutationId: String?,
        a: Int,
        b: Int,
    ): Any? = runFixture("add", mutationId, a, b)

    private fun isAdult(
        mutationId: String?,
        age: Int,
    ): Any? = runFixture("isAdult", mutationId, age)

    private fun runFixture(
        method: String,
        mutationId: String?,
        vararg args: Any?,
    ): Any? =
        withMutant(fixture, mutationId) { loader ->
            invokeStatic(loader, "fixture.FixtureKt", method, *args)
        }

    private fun bumpCounter(mutationId: String?): Any? =
        withMutant(fixture, mutationId) { loader ->
            val counterClass = loader.loadClass("fixture.Counter")
            val counter = counterClass.getDeclaredConstructor().newInstance()
            counterClass.getMethod("bump").invoke(counter)
            counterClass.getMethod("getValue").invoke(counter)
        }

    companion object {
        private val FIXTURE_SOURCE =
            """
            package fixture

            import dev.pott.kaputt.runtime.DisableMutation

            const val FLAG: Boolean = true

            fun add(a: Int, b: Int): Int = a + b

            fun isAdult(age: Int): Boolean = age >= 18

            fun alwaysTrue(): Boolean = true

            fun negate(x: Boolean): Boolean = !x

            fun sameWord(a: String, b: String): Boolean = a == b

            fun flag(): Boolean = FLAG

            @DisableMutation
            fun untouchable(a: Int, b: Int): Int = a - b

            inline fun inlined(a: Int, b: Int): Int = a * b

            fun orDefault(value: Int?): Int = value ?: -1

            fun label(score: Int): String = if (score > 10) "high" else "low"

            fun rank(kind: Int): String = when (kind) {
                0 -> "zero"
                else -> "other"
            }

            fun toggle(b: Boolean): Boolean = if (b) false else true

            fun explicitNot(x: Boolean): Boolean = x.not()

            fun addPercent(x: Int): Int = x + 100

            fun greeting(): String = "hello"

            fun names(): List<String> = listOf("a", "b")

            fun countUp(limit: Int): Int {
                var count = 0
                for (i in 0 until limit) {
                    count = count + 1
                }
                return count
            }

            fun lengthOrNull(s: String?): Int? = s?.length

            fun subtract(a: Int, b: Int): Int = a - b

            fun scale(a: Int, b: Int): Int = a * b

            fun ratio(a: Int, b: Int): Int = a / b

            fun remainder(a: Int, b: Int): Int = a % b

            fun isMinor(age: Int): Boolean = age < 18

            fun atMost(a: Int, b: Int): Boolean = a <= b

            fun sameAmount(a: Double, b: Double): Boolean = a == b

            fun bigOffset(x: Long): Long = x + 10L

            fun blank(): String = ""

            fun zero(): Int = 0

            fun tags(): Set<String> = setOf("x")

            fun lookup(): Map<String, Int> = mapOf("a" to 1)

            fun countInclusive(limit: Int): Int {
                var count = 0
                for (i in 0..limit) {
                    count = count + 1
                }
                return count
            }

            fun concat(a: String, b: String): String = a + b

            data class Point(val x: Int, val y: Int)

            fun bothPositive(a: Int, b: Int): Boolean = a > 0 && b > 0

            fun eitherPositive(a: Int, b: Int): Boolean = a > 0 || b > 0

            class Counter {
                var value: Int = 0

                fun bump() {
                    increment()
                }

                fun increment() {
                    value = value + 1
                }
            }
            """.trimIndent()

        private val fixture: MutationCompilerHarness.CompiledFixture by lazy {
            MutationCompilerHarness.compile(FIXTURE_SOURCE)
        }
    }
}
