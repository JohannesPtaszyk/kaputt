package dev.pott.kaputt.compiler

import dev.pott.kaputt.compiler.MutationCompilerHarness.withMutant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MutationStructuresTest {
    @Test
    fun `GIVEN lambda body WHEN math mutant active THEN fold flips`() {
        val mutation = single(MutationOperator.MATH, "<anonymous>", "replaced '+' with '-'")

        assertEquals(6, invoke(null, "sumOf", listOf(1, 2, 3)))
        assertEquals(-6, invoke(mutation.id, "sumOf", listOf(1, 2, 3)))
    }

    @Test
    fun `GIVEN local function WHEN math mutant active THEN inner logic flips`() {
        val mutation = single(MutationOperator.MATH, "inner", "replaced '+' with '-'")

        assertEquals(2, invoke(null, "outerLocal", 1))
        assertEquals(0, invoke(mutation.id, "outerLocal", 1))
    }

    @Test
    fun `GIVEN extension function WHEN math mutant active THEN receiver math flips`() {
        val mutation = single(MutationOperator.MATH, "doubled", "replaced '*' with '/'")

        assertEquals(10, invoke(null, "doubled", 5))
        assertEquals(2, invoke(mutation.id, "doubled", 5))
    }

    @Test
    fun `GIVEN string template WHEN math mutant active THEN rendered text changes`() {
        val mutation = single(MutationOperator.MATH, "describeCount", "replaced '+' with '-'")

        assertEquals("count: 3", invoke(null, "describeCount", 2))
        assertEquals("count: 1", invoke(mutation.id, "describeCount", 2))
    }

    @Test
    fun `GIVEN suspend function WHEN compiling THEN its body is mutated`() {
        assertTrue(
            fixture.mutations.any {
                it.function == "addSoon" &&
                    it.operator == MutationOperator.MATH.key
            },
        )
    }

    @Test
    fun `GIVEN while loop WHEN boundary mutant active THEN one extra iteration runs`() {
        val mutation =
            single(MutationOperator.CONDITIONAL_BOUNDARY, "ticks", "replaced '<' with '<='")

        assertEquals(3, invoke(null, "ticks", 3))
        assertEquals(4, invoke(mutation.id, "ticks", 3))
    }

    @Test
    fun `GIVEN try catch WHEN mutants active THEN both paths flip`() {
        val tryPath = single(MutationOperator.MATH, "safeDiv", "replaced '/' with '*'")
        val catchPath = single(MutationOperator.NUMBER_LITERAL, "safeDiv", "replaced '-1' with '0'")

        assertEquals(3, invoke(null, "safeDiv", 6, 2))
        assertEquals(12, invoke(tryPath.id, "safeDiv", 6, 2))
        assertEquals(-1, invoke(null, "safeDiv", 1, 0))
        assertEquals(0, invoke(catchPath.id, "safeDiv", 1, 0))
    }

    @Test
    fun `GIVEN default parameter value WHEN literal mutant active THEN default changes`() {
        val mutation = single(MutationOperator.NUMBER_LITERAL, "greet", "replaced '2' with '3'")

        assertEquals(20, invokeDefaulted(null))
        assertEquals(30, invokeDefaulted(mutation.id))
    }

    @Test
    fun `GIVEN custom property getter WHEN boundary mutant active THEN edge flips`() {
        val mutation =
            single(
                MutationOperator.CONDITIONAL_BOUNDARY,
                "<get-isOverdrawn>",
                "replaced '<' with '<='",
            )

        assertEquals(false, onAccount(null, balance = 0))
        assertEquals(true, onAccount(mutation.id, balance = 0))
    }

    @Test
    fun `GIVEN custom property setter WHEN math mutant active THEN stored value flips`() {
        val mutation = single(MutationOperator.MATH, "<set-stored>", "replaced '+' with '-'")

        assertEquals(6, storeIntoAccount(null, 5))
        assertEquals(4, storeIntoAccount(mutation.id, 5))
    }

    @Test
    fun `GIVEN init block WHEN math mutant active THEN construction result flips`() {
        val mutation = single(MutationOperator.MATH, "<init>", "replaced '+' with '-'")

        assertEquals(4, sessionOffset(null, 3))
        assertEquals(2, sessionOffset(mutation.id, 3))
    }

    @Test
    fun `GIVEN secondary constructor WHEN literal mutant active THEN default size changes`() {
        val mutation = single(MutationOperator.NUMBER_LITERAL, "<init>", "replaced '6' with '7'")

        assertEquals(6, boxSize(null))
        assertEquals(7, boxSize(mutation.id))
    }

    @Test
    fun `GIVEN companion object function WHEN boundary mutant active THEN edge flips`() {
        val mutation =
            single(MutationOperator.CONDITIONAL_BOUNDARY, "max", "replaced '>' with '>='")

        assertEquals(2, companionMax(null, 2, 2))
        assertEquals(2, companionMax(mutation.id, 2, 2))
        assertEquals(3, companionMax(null, 3, 2))
    }

    @Test
    fun `GIVEN object declaration WHEN literal mutant active THEN value changes`() {
        val mutation = single(MutationOperator.NUMBER_LITERAL, "limit", "replaced '10' with '11'")

        assertEquals(10, registryLimit(null))
        assertEquals(11, registryLimit(mutation.id))
    }

    @Test
    fun `GIVEN interface default method WHEN math mutant active THEN implementation flips`() {
        val mutation = single(MutationOperator.MATH, "sizeOf", "replaced '+' with '-'")

        assertEquals(3, sizeOf(null, "ab"))
        assertEquals(1, sizeOf(mutation.id, "ab"))
    }

    private fun single(
        operator: MutationOperator,
        function: String,
        description: String,
    ): MutationPoint =
        fixture.mutations.single { point ->
            point.operator == operator.key && point.function == function &&
                point.description == description
        }

    private fun invoke(
        mutationId: String?,
        method: String,
        vararg args: Any?,
    ): Any? =
        withMutant(fixture, mutationId) { loader ->
            loader
                .loadClass("fixture.StructuresKt")
                .methods
                .single {
                    it.name == method
                }.invoke(null, *args)
        }

    private fun invokeDefaulted(mutationId: String?): Any? =
        withMutant(fixture, mutationId) { loader ->
            loader
                .loadClass("fixture.StructuresKt")
                .methods
                .single { it.name == "greet\$default" }
                .invoke(null, 0, 1, null)
        }

    private fun onAccount(
        mutationId: String?,
        balance: Int,
    ): Any? =
        withMutant(fixture, mutationId) { loader ->
            val cls = loader.loadClass("fixture.Account")
            val account = cls.getDeclaredConstructor(Int::class.java).newInstance(balance)
            cls.getMethod("isOverdrawn").invoke(account)
        }

    private fun storeIntoAccount(
        mutationId: String?,
        value: Int,
    ): Any? =
        withMutant(fixture, mutationId) { loader ->
            val cls = loader.loadClass("fixture.Account")
            val account = cls.getDeclaredConstructor(Int::class.java).newInstance(0)
            cls.getMethod("setStored", Int::class.java).invoke(account, value)
            cls.getMethod("getStored").invoke(account)
        }

    private fun sessionOffset(
        mutationId: String?,
        start: Int,
    ): Any? =
        withMutant(fixture, mutationId) { loader ->
            val cls = loader.loadClass("fixture.Session")
            val session = cls.getDeclaredConstructor(Int::class.java).newInstance(start)
            cls.getMethod("getOffset").invoke(session)
        }

    private fun boxSize(mutationId: String?): Any? =
        withMutant(fixture, mutationId) { loader ->
            val cls = loader.loadClass("fixture.Box")
            cls.getMethod("getSize").invoke(cls.getDeclaredConstructor().newInstance())
        }

    private fun companionMax(
        mutationId: String?,
        a: Int,
        b: Int,
    ): Any? =
        withMutant(fixture, mutationId) { loader ->
            val cls = loader.loadClass("fixture.Score")
            val companion = cls.getField("Companion").get(null)
            companion.javaClass
                .getMethod(
                    "max",
                    Int::class.java,
                    Int::class.java,
                ).invoke(companion, a, b)
        }

    private fun registryLimit(mutationId: String?): Any? =
        withMutant(fixture, mutationId) { loader ->
            val cls = loader.loadClass("fixture.Registry")
            cls.getMethod("limit").invoke(cls.getField("INSTANCE").get(null))
        }

    private fun sizeOf(
        mutationId: String?,
        argument: String,
    ): Any? =
        withMutant(fixture, mutationId) { loader ->
            val cls = loader.loadClass("fixture.DefaultSizer")
            cls
                .getMethod(
                    "sizeOf",
                    String::class.java,
                ).invoke(cls.getDeclaredConstructor().newInstance(), argument)
        }

    companion object {
        private val STRUCTURES_SOURCE =
            """
            @file:JvmName("StructuresKt")

            package fixture

            fun sumOf(items: List<Int>): Int = items.fold(0) { acc, item -> acc + item }

            fun outerLocal(x: Int): Int {
                fun inner(y: Int): Int = y + 1
                return inner(x)
            }

            fun Int.doubled(): Int = this * 2

            fun describeCount(n: Int): String = "count: ${'$'}{n + 1}"

            suspend fun addSoon(a: Int, b: Int): Int = a + b

            fun ticks(n: Int): Int {
                var i = 0
                while (i < n) {
                    i = i + 1
                }
                return i
            }

            fun safeDiv(a: Int, b: Int): Int = try {
                a / b
            } catch (e: ArithmeticException) {
                -1
            }

            fun greet(times: Int = 2): Int = times * 10

            class Account(var balance: Int) {
                val isOverdrawn: Boolean
                    get() = balance < 0

                var stored: Int = 0
                    set(value) {
                        field = value + 1
                    }
            }

            class Session(start: Int) {
                var offset: Int = 0

                init {
                    offset = start + 1
                }
            }

            class Box(val size: Int) {
                constructor() : this(6)
            }

            class Score {
                companion object {
                    fun max(a: Int, b: Int): Int = if (a > b) a else b
                }
            }

            object Registry {
                fun limit(): Int = 10
            }

            interface Sizer {
                fun sizeOf(s: String): Int = s.length + 1
            }

            class DefaultSizer : Sizer
            """.trimIndent()

        private val fixture: MutationCompilerHarness.CompiledFixture by lazy {
            MutationCompilerHarness.compile(STRUCTURES_SOURCE)
        }
    }
}
