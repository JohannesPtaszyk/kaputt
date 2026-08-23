package dev.pott.kaputt.compiler

import dev.pott.kaputt.compiler.MutationCompilerHarness.withMutant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MutationTrickyCodeTest {
    @Test
    fun `GIVEN tricky constructs WHEN instrumenting THEN compilation succeeds with unique ids`() {
        val ids = fixture.mutations.map(MutationPoint::id)

        assertTrue(fixture.mutations.isNotEmpty())
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `GIVEN value class WHEN instrumenting THEN its body is mutated`() {
        assertTrue(
            fixture.mutations.any {
                it.function == "plusOne" &&
                    it.operator == MutationOperator.MATH.key
            },
        )
    }

    @Test
    fun `GIVEN exhaustive when over sealed type WHEN mutant active THEN branch math flips`() {
        val starMutants =
            fixture.mutations.filter { point ->
                point.function == "area" &&
                    point.operator == MutationOperator.MATH.key &&
                    point.description == "replaced '*' with '/'"
            }
        val circleBranch = starMutants.groupBy(MutationPoint::line).values.single { it.size == 2 }
        val innerStar = circleBranch.minBy(MutationPoint::endColumn)

        assertEquals(12.56, circleArea(null, 2.0))
        assertEquals(3.14, circleArea(innerStar.id, 2.0))
    }

    @Test
    fun `GIVEN prefix and postfix increment WHEN instrumenting THEN code still compiles and behaves`() {
        assertEquals(3, invoke(null, "prefixIncrement", 3))
        assertEquals(4, invoke(null, "postfixIncrement", 4))
        assertEquals(3, invoke(null, "prefixDecrement", 5))
        assertEquals(6, invoke(null, "indexedIncrement", intArrayOf(1, 2, 3)))
        assertEquals(3, invoke(null, "decrementInCondition", 1, 3))
        assertEquals(3, invoke(null, "compoundAssign", 3))
        assertEquals(8.0, invoke(null, "compoundAssignDouble", 3))
        assertEquals(3, invoke(null, "incrementAsValue", 3))
    }

    @Test
    fun `GIVEN tailrec function WHEN compiling THEN baseline behavior is intact`() {
        assertEquals(4, invoke(null, "gcd", 12, 8))
    }

    @Test
    fun `GIVEN destructuring vararg lambda-return delegation and lateinit THEN baselines are intact`() {
        assertEquals(5, firstPairOf(null, 2, 3))
        assertEquals("a-b", invoke(null, "joinAll", arrayOf("a", "b")))
        assertEquals(42, invoke(null, "labeled", listOf(1, 42, 2)))
        assertEquals(-1, invoke(null, "labeled", listOf(1, 2)))
    }

    private fun firstPairOf(
        mutationId: String?,
        first: Int,
        second: Int,
    ): Any? =
        withMutant(fixture, mutationId) { loader ->
            val pair =
                loader
                    .loadClass("kotlin.Pair")
                    .getDeclaredConstructor(Any::class.java, Any::class.java)
                    .newInstance(first, second)
            loader
                .loadClass("fixture.TrickyKt")
                .methods
                .single { it.name == "firstPair" }
                .invoke(null, listOf(pair))
        }

    @Test
    fun `GIVEN enum with logic WHEN boundary mutant active THEN comparison flips`() {
        val mutation =
            fixture.mutations.single { point ->
                point.function == "above" && point.operator == MutationOperator.CONDITIONAL_BOUNDARY.key
            }

        assertEquals(false, levelAbove(null, "LOW", "LOW"))
        assertEquals(true, levelAbove(mutation.id, "LOW", "LOW"))
    }

    @Test
    fun `GIVEN object expression WHEN string mutant active THEN greeting changes`() {
        val mutation =
            fixture.mutations.single { point ->
                point.function == "greet" && point.operator == MutationOperator.STRING_LITERAL.key
            }

        assertEquals("hi kim", greet(null, "kim"))
        assertEquals("kim", greet(mutation.id, "kim"))
    }

    private fun invoke(
        mutationId: String?,
        method: String,
        vararg args: Any?,
    ): Any? =
        withMutant(fixture, mutationId) { loader ->
            loader
                .loadClass("fixture.TrickyKt")
                .methods
                .single {
                    it.name == method
                }.invoke(null, *args)
        }

    private fun circleArea(
        mutationId: String?,
        radius: Double,
    ): Any? =
        withMutant(fixture, mutationId) { loader ->
            val circle =
                loader
                    .loadClass("fixture.Circle")
                    .getDeclaredConstructor(Double::class.java)
                    .newInstance(radius)
            loader
                .loadClass("fixture.TrickyKt")
                .methods
                .single {
                    it.name == "area"
                }.invoke(null, circle)
        }

    private fun levelAbove(
        mutationId: String?,
        left: String,
        right: String,
    ): Any? =
        withMutant(fixture, mutationId) { loader ->
            val enum = loader.loadClass("fixture.Level")
            val valueOf = enum.getMethod("valueOf", String::class.java)
            enum
                .getMethod("above", enum)
                .invoke(valueOf.invoke(null, left), valueOf.invoke(null, right))
        }

    private fun greet(
        mutationId: String?,
        name: String,
    ): Any? =
        withMutant(fixture, mutationId) { loader ->
            val greeter =
                loader
                    .loadClass("fixture.TrickyKt")
                    .methods
                    .single { it.name == "greeterOf" }
                    .invoke(null, name)
            greeter.javaClass.getMethod("greet").invoke(greeter)
        }

    companion object {
        private val TRICKY_SOURCE =
            """
            @file:JvmName("TrickyKt")

            package fixture

            @JvmInline
            value class Meters(val value: Int) {
                fun plusOne(): Meters = Meters(value + 1)
            }

            sealed interface Shape
            class Circle(val r: Double) : Shape
            class Square(val side: Double) : Shape

            fun area(s: Shape): Double = when (s) {
                is Circle -> 3.14 * s.r * s.r
                is Square -> s.side * s.side
            }

            tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

            fun firstPair(pairs: List<Pair<Int, Int>>): Int {
                val (a, b) = pairs.first()
                return a + b
            }

            fun joinAll(vararg parts: String): String = parts.joinToString("-")

            fun labeled(items: List<Int>): Int {
                items.forEach { item ->
                    if (item > 10) return item
                }
                return -1
            }

            interface Greeter {
                fun greet(): String
            }

            fun greeterOf(name: String): Greeter = object : Greeter {
                override fun greet(): String = "hi " + name
            }

            class Wrapper(list: List<Int>) : List<Int> by list

            enum class Level(val rank: Int) {
                LOW(1),
                HIGH(2),
                ;

                fun above(other: Level): Boolean = rank > other.rank
            }

            class Holder {
                lateinit var label: String

                fun ready(): Boolean = ::label.isInitialized
            }

            fun <T : Comparable<T>> maxOfThree(a: T, b: T, c: T): T = maxOf(a, maxOf(b, c))

            fun prefixIncrement(times: Int): Int {
                var i = 0
                while (i < times) {
                    ++i
                }
                return i
            }

            fun postfixIncrement(times: Int): Int {
                var i = 0
                var guard = 0
                while (guard < times) {
                    i++
                    guard++
                }
                return i
            }

            fun prefixDecrement(from: Int): Int {
                var i = from
                --i
                i--
                return i
            }

            fun compoundAssign(steps: Int): Int {
                var index = 0
                var count = 0
                while (count < steps) {
                    index += 2
                    index -= 1
                    count += 1
                }
                return index
            }

            fun compoundAssignDouble(steps: Int): Double {
                var total = 1.0
                var count = 0
                while (count < steps) {
                    total *= 2.0
                    total /= 1.0
                    count += 1
                }
                return total
            }

            fun decrementInCondition(candidate: Int, chainStart: Int): Int {
                var chain = chainStart
                var seen = 0
                while (candidate >= 0 && chain-- > 0) {
                    seen++
                }
                return seen
            }

            fun incrementAsValue(limit: Int): Int {
                var i = 0
                var sum = 0
                while (i < limit) {
                    sum = sum + (i++)
                }
                return sum
            }

            fun indexedIncrement(values: IntArray): Int {
                var i = 0
                var sum = 0
                while (i < values.size) {
                    sum = sum + values[i++]
                }
                return sum
            }
            """.trimIndent()

        private val fixture: MutationCompilerHarness.CompiledFixture by lazy {
            MutationCompilerHarness.compile(TRICKY_SOURCE)
        }
    }
}
