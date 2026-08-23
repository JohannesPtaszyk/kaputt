package dev.pott.kaputt.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBoolean
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irFalse
import org.jetbrains.kotlin.ir.builders.irIfThenElse
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTrue
import org.jetbrains.kotlin.ir.builders.irUnit
import org.jetbrains.kotlin.ir.declarations.IrAnonymousInitializer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrSymbolOwner
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrBranch
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstKind
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrContainerExpression
import org.jetbrains.kotlin.ir.expressions.IrElseBranch
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrLoop
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrSetValue
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrWhen
import org.jetbrains.kotlin.ir.expressions.impl.IrBranchImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrElseBranchImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrWhenImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.types.isNullable
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.FqName
import java.io.File
import java.security.MessageDigest

/**
 * Applies mutant schemata: every mutation is compiled into the code once,
 * wrapped in `if (isMutationActive("<id>")) mutant else original`, so a single
 * compilation supports one test run per mutant without recompiling.
 */
class MutationTransformer(
    private val context: IrPluginContext,
    private val symbols: MutationIrSymbols,
    private val guard: IrSimpleFunctionSymbol,
    private val nullPointerMutant: IrSimpleFunctionSymbol,
    private val deadlineCheck: IrSimpleFunctionSymbol,
    private val file: IrFile,
    private val operators: Set<MutationOperator>,
    private val maxMutationsPerFunction: Int,
    private val sink: (MutationPoint) -> Unit,
    private val skippedSink: (SkippedFunction) -> Unit,
) : IrElementTransformerVoid() {
    /** A function whose mutation count hit [maxMutationsPerFunction]. */
    data class SkippedFunction(
        val filePath: String,
        val function: String,
        val skipped: Int,
    )

    private val irBuiltIns = context.irBuiltIns
    private val filePath = file.fileEntry.name
    private val fileName = File(filePath).name
    private val digest = MessageDigest.getInstance("SHA-1")
    private var currentScope: IrSymbolOwner? = null

    // Guards grow the enclosing method, so a per-scope cap keeps large
    // functions clear of the JVM's 64K limit. Skips are reported.
    private val mutationCounts = HashMap<IrSymbolOwner, Int>()
    private val skippedCounts = LinkedHashMap<IrSymbolOwner, Int>()

    override fun visitFunction(declaration: IrFunction): IrStatement {
        if (declaration.isMutationOptOut()) return declaration
        if (!declaration.hasMutableOrigin()) return declaration
        // The inliner regenerates these bodies at every call site, which
        // miscompiles instrumented initializers inside them.
        if (declaration.isInline) return declaration
        return withScope(declaration) { super.visitFunction(declaration) }
    }

    override fun visitAnonymousInitializer(declaration: IrAnonymousInitializer): IrStatement =
        withScope(declaration) { super.visitAnonymousInitializer(declaration) }

    override fun visitClass(declaration: IrClass): IrStatement {
        if (declaration.hasAnnotation(DISABLE_ANNOTATION_FQ)) return declaration
        return withScope(null) { super.visitClass(declaration) }
    }

    override fun visitField(declaration: IrField): IrStatement {
        val isConst = declaration.correspondingPropertySymbol?.owner?.isConst == true
        if (isConst || declaration.origin != IrDeclarationOrigin.PROPERTY_BACKING_FIELD) {
            return declaration
        }
        return withScope(declaration) { super.visitField(declaration) }
    }

    override fun visitBranch(branch: IrBranch): IrBranch {
        // Constant branch conditions are part of the IrWhen contract, and
        // guarding one produces IR the backend miscompiles. Results are fine.
        if (branch.condition is IrConst) {
            branch.result = branch.result.transform(this, null)
            return branch
        }
        return super.visitBranch(branch)
    }

    override fun visitElseBranch(branch: IrElseBranch): IrElseBranch {
        branch.result = branch.result.transform(this, null)
        return branch
    }

    override fun visitConstructorCall(expression: IrConstructorCall): IrExpression {
        // Annotation arguments must stay compile-time constant.
        val constructedClass = expression.symbol.owner.parentAsClass
        if (constructedClass.kind == ClassKind.ANNOTATION_CLASS) return expression
        return super.visitConstructorCall(expression)
    }

    override fun visitSetValue(expression: IrSetValue): IrExpression {
        if (expression.origin in ASSIGNMENT_ORIGINS) return expression
        return super.visitSetValue(expression)
    }

    override fun visitCall(expression: IrCall): IrExpression {
        if (expression.origin in ASSIGNMENT_ORIGINS) return expression
        val scope = currentScope ?: return super.visitCall(expression)
        val plans = planCallMutations(expression)
        val transformed = super.visitCall(expression)
        if (plans.isEmpty()) return transformed
        return applyPlans(transformed, expression.type, plans, scope)
    }

    override fun visitConst(expression: IrConst): IrExpression {
        val scope = currentScope ?: return expression
        val plans =
            listOfNotNull(planBooleanLiteral(expression), planStringLiteral(expression)) +
                planNumberLiterals(expression)
        if (plans.isEmpty()) return expression
        return applyPlans(expression, expression.type, plans, scope)
    }

    /**
     * `a?.b` desugars to `{ tmp = a; if (tmp == null) null else tmp.b }`. Like
     * `&&`/`||`, the desugared when's shape is a backend contract: only the
     * receiver initializer and the value branch may be visited. The safe-call
     * mutant makes the null path throw (like `!!` would), so surviving means
     * the null path is untested.
     */
    override fun visitBlock(expression: IrBlock): IrExpression {
        // The backend rewrites compound assignments and ++/-- from their
        // desugared shape and casts the operands, so a guard inside crashes it.
        if (expression.origin in ASSIGNMENT_ORIGINS) return expression
        if (expression.origin != IrStatementOrigin.SAFE_CALL) return super.visitBlock(expression)
        val scope = currentScope ?: return super.visitBlock(expression)
        val shape = safeCallShape(expression) ?: return super.visitBlock(expression)
        shape.variable.initializer = shape.variable.initializer?.transform(this, null)
        shape.valueBranch.result = shape.valueBranch.result.transform(this, null)
        planSafeCallMutation(expression, shape, scope)
        return expression
    }

    private class SafeCallShape(
        val variable: IrVariable,
        val whenExpression: IrWhen,
        val nullBranch: IrBranch,
        val valueBranch: IrBranch,
    )

    private fun safeCallShape(block: IrBlock): SafeCallShape? {
        if (block.statements.size != 2) return null
        val variable = block.statements[0] as? IrVariable ?: return null
        val whenExpression = block.statements[1] as? IrWhen ?: return null
        if (whenExpression.branches.size != 2) return null
        val nullBranch =
            whenExpression.branches.firstOrNull { branch ->
                (branch.result as? IrConst)?.kind == IrConstKind.Null
            } ?: return null
        val valueBranch = whenExpression.branches.first { it !== nullBranch }
        return SafeCallShape(variable, whenExpression, nullBranch, valueBranch)
    }

    private fun planSafeCallMutation(
        block: IrBlock,
        shape: SafeCallShape,
        scope: IrSymbolOwner,
    ) {
        val plan =
            plan(MutationOperator.SAFE_CALL, "replaced '?.' with '!!'", block) { builder ->
                builder.irCall(nullPointerMutant, irBuiltIns.nothingType)
            } ?: return
        shape.nullBranch.result =
            wrapGuarded(
                plan = plan,
                type = shape.whenExpression.type,
                original = shape.nullBranch.result,
                scope = scope,
            )
    }

    private fun planBooleanLiteral(expression: IrConst): MutantPlan? {
        if (expression.kind != IrConstKind.Boolean) return null
        val value = expression.value as Boolean
        return plan(
            operator = MutationOperator.BOOLEAN_LITERAL,
            description = "replaced '$value' with '${!value}'",
            element = expression,
        ) { builder -> builder.irBoolean(!value) }
    }

    private fun planNumberLiterals(expression: IrConst): List<MutantPlan> {
        val neighbors: List<Any> =
            when (expression.kind) {
                IrConstKind.Int -> (expression.value as Int).let { listOf(it + 1, it - 1) }

                IrConstKind.Long -> (expression.value as Long).let { listOf(it + 1, it - 1) }

                IrConstKind.Short ->
                    (expression.value as Short).let {
                        listOf((it + 1).toShort(), (it - 1).toShort())
                    }

                IrConstKind.Byte ->
                    (expression.value as Byte).let {
                        listOf((it + 1).toByte(), (it - 1).toByte())
                    }

                IrConstKind.Float -> (expression.value as Float).let { listOf(it + 1f, it - 1f) }

                IrConstKind.Double -> (expression.value as Double).let { listOf(it + 1.0, it - 1.0) }

                else -> return emptyList()
            }
        return neighbors.mapNotNull { mutated ->
            plan(
                operator = MutationOperator.NUMBER_LITERAL,
                description = "replaced '${expression.value}' with '$mutated'",
                element = expression,
            ) { _ ->
                IrConstImpl(
                    expression.startOffset,
                    expression.endOffset,
                    expression.type,
                    expression.kind,
                    mutated,
                )
            }
        }
    }

    private fun planStringLiteral(expression: IrConst): MutantPlan? {
        if (expression.kind != IrConstKind.String) return null
        val value = expression.value as String
        if (value.isEmpty()) return null
        return plan(
            operator = MutationOperator.STRING_LITERAL,
            description = "replaced \"${value.take(STRING_PREVIEW_LENGTH)}\" with \"\"",
            element = expression,
        ) { builder -> builder.irString("") }
    }

    override fun visitReturn(expression: IrReturn): IrExpression {
        val scope = currentScope ?: return super.visitReturn(expression)
        val plans = planReturnMutations(expression.value)
        val transformed = super.visitReturn(expression)
        if (plans.isNotEmpty()) {
            expression.value =
                applyPlans(expression.value, expression.value.type, plans, scope)
        }
        return transformed
    }

    private fun planReturnMutations(value: IrExpression): List<MutantPlan> {
        if (value.type.classifierOrNull == irBuiltIns.booleanClass) return booleanReturnPlans(value)
        return listOfNotNull(planEmptyReturn(value))
    }

    /**
     * Both values, because either alone leaves a test that only asserts the
     * other one looking sufficient: a predicate tested solely on its `true`
     * cases is pinned down by neither of them.
     */
    private fun booleanReturnPlans(value: IrExpression): List<MutantPlan> {
        // A constant return is the boolean-literal operator's job.
        if (value is IrConst || value.type.isNullable()) return emptyList()
        return listOfNotNull(
            emptyReturnPlan(value, "false") { builder -> builder.irFalse() },
            emptyReturnPlan(value, "true") { builder -> builder.irTrue() },
        )
    }

    private fun planEmptyReturn(value: IrExpression): MutantPlan? =
        when (value.type.classifierOrNull) {
            in symbols.numberClassSymbols -> {
                // A primitive const on a nullable return miscompiles.
                val isAlreadyZero = value is IrConst && (value.value as? Number)?.toDouble() == 0.0
                if (value.type.isNullable() || isAlreadyZero) {
                    null
                } else {
                    emptyReturnPlan(value, "0") { zeroConst(value) }
                }
            }

            irBuiltIns.stringClass -> {
                val isAlreadyEmpty = value is IrConst && value.value == ""
                if (value.type.isNullable() || isAlreadyEmpty) {
                    null
                } else {
                    emptyReturnPlan(value, "\"\"") { builder -> builder.irString("") }
                }
            }

            irBuiltIns.listClass ->
                emptyCollectionPlan(
                    value,
                    symbols.emptyListSymbol,
                    "emptyList()",
                )

            irBuiltIns.setClass -> emptyCollectionPlan(value, symbols.emptySetSymbol, "emptySet()")

            irBuiltIns.mapClass -> emptyCollectionPlan(value, symbols.emptyMapSymbol, "emptyMap()")

            else -> null
        }

    private fun emptyCollectionPlan(
        value: IrExpression,
        symbol: IrSimpleFunctionSymbol,
        text: String,
    ): MutantPlan? {
        val typeArguments =
            (value.type as? IrSimpleType)
                ?.arguments
                ?.map { it.typeOrNull ?: return null }
                ?: return null
        return emptyReturnPlan(value, text) { builder ->
            builder.irCall(symbol, value.type).apply {
                typeArguments.forEachIndexed { index, argument ->
                    this.typeArguments[index] =
                        argument
                }
            }
        }
    }

    private fun emptyReturnPlan(
        value: IrExpression,
        text: String,
        mutantFactory: (DeclarationIrBuilder) -> IrExpression,
    ): MutantPlan? =
        plan(
            operator = MutationOperator.EMPTY_RETURN,
            description = "replaced return value with $text",
            element = value,
            mutantFactory = mutantFactory,
        )

    private fun zeroConst(value: IrExpression): IrExpression {
        val kind = (value as? IrConst)?.kind ?: kindFor(value)
        val zero: Any =
            when (kind) {
                IrConstKind.Long -> 0L
                IrConstKind.Short -> 0.toShort()
                IrConstKind.Byte -> 0.toByte()
                IrConstKind.Float -> 0f
                IrConstKind.Double -> 0.0
                else -> 0
            }
        return IrConstImpl(value.startOffset, value.endOffset, value.type, kind, zero)
    }

    private fun kindFor(value: IrExpression): IrConstKind =
        when (value.type.classifierOrNull) {
            irBuiltIns.longClass -> IrConstKind.Long
            irBuiltIns.shortClass -> IrConstKind.Short
            irBuiltIns.byteClass -> IrConstKind.Byte
            irBuiltIns.floatClass -> IrConstKind.Float
            irBuiltIns.doubleClass -> IrConstKind.Double
            else -> IrConstKind.Int
        }

    override fun visitWhen(expression: IrWhen): IrExpression {
        val isAnd = expression.origin == IrStatementOrigin.ANDAND
        val isOr = expression.origin == IrStatementOrigin.OROR
        if (!isAnd && !isOr) return super.visitWhen(expression)

        // The structural true/false constants of a desugared && or || are a
        // backend contract. Only the two operands may be mutated.
        if (expression.branches.size != 2) return expression
        val (first, second) = expression.branches
        if (second.condition !is IrConst) return expression

        val scope = currentScope
        if (scope == null) {
            transformLogicalOperands(first, second, isAnd)
            return expression
        }
        val plan = planLogicalMutation(expression, first, second, isAnd)
        transformLogicalOperands(first, second, isAnd)
        if (plan == null) return expression
        return applyPlans(expression, expression.type, listOf(plan), scope)
    }

    private fun transformLogicalOperands(
        first: IrBranch,
        second: IrBranch,
        isAnd: Boolean,
    ) {
        first.condition = first.condition.transform(this, null)
        if (isAnd) {
            first.result = first.result.transform(this, null)
        } else {
            second.result = second.result.transform(this, null)
        }
    }

    private fun planLogicalMutation(
        expression: IrWhen,
        first: IrBranch,
        second: IrBranch,
        isAnd: Boolean,
    ): MutantPlan? {
        val copyParent = copyParent()
        val left = first.condition.deepCopyWithSymbols(copyParent)
        val right = (if (isAnd) first.result else second.result).deepCopyWithSymbols(copyParent)
        return plan(
            operator = MutationOperator.LOGICAL_OPERATOR,
            description = if (isAnd) "replaced '&&' with '||'" else "replaced '||' with '&&'",
            element = expression,
        ) { builder ->
            val branches =
                if (isAnd) {
                    listOf(
                        IrBranchImpl(
                            expression.startOffset,
                            expression.endOffset,
                            left,
                            builder.irTrue(),
                        ),
                        IrElseBranchImpl(
                            expression.startOffset,
                            expression.endOffset,
                            builder.irTrue(),
                            right,
                        ),
                    )
                } else {
                    listOf(
                        IrBranchImpl(expression.startOffset, expression.endOffset, left, right),
                        IrElseBranchImpl(
                            expression.startOffset,
                            expression.endOffset,
                            builder.irTrue(),
                            builder.irFalse(),
                        ),
                    )
                }
            IrWhenImpl(
                expression.startOffset,
                expression.endOffset,
                irBuiltIns.booleanType,
                if (isAnd) IrStatementOrigin.OROR else IrStatementOrigin.ANDAND,
                branches,
            )
        }
    }

    /**
     * A mutation that breaks a loop-termination guard runs forever. The check
     * compiled in here ends that run from the inside, so it costs one mutant
     * rather than the whole fork and its remaining batch.
     */
    override fun visitLoop(loop: IrLoop): IrExpression {
        val result = super.visitLoop(loop)
        val scope = currentScope ?: return result
        val body = loop.body ?: return result
        val builder = DeclarationIrBuilder(context, scope.symbol, body.startOffset, body.endOffset)
        val check = builder.irCall(deadlineCheck)
        if (body is IrContainerExpression) {
            // A desugared `for` keeps its induction variable at the head of the
            // body, and the loop lowering reads it from there.
            val afterLoopVariables = body.statements.indexOfFirst { it !is IrVariable }
            val at = if (afterLoopVariables < 0) body.statements.size else afterLoopVariables
            body.statements.add(at, check)
            return result
        }
        loop.body =
            builder.irBlock(resultType = irBuiltIns.unitType) {
                +check
                +body
            }
        return result
    }

    override fun visitBlockBody(body: IrBlockBody): IrBody {
        // After the children, so the guards just built are not themselves
        // mutated.
        val result = super.visitBlockBody(body)
        val scope = currentScope ?: return result
        body.statements.replaceAll { statement ->
            if (statement is IrCall && statement.type.isUnit()) {
                guardVoidCallRemoval(statement, scope)
            } else {
                statement
            }
        }
        return result
    }

    private fun planCallMutations(call: IrCall): List<MutantPlan> =
        buildList {
            val symbol = call.symbol
            val copyParent = copyParent()

            if (symbol == irBuiltIns.booleanNotSymbol) {
                call.arguments.firstOrNull()?.let { receiver ->
                    val copy = receiver.deepCopyWithSymbols(copyParent)
                    plan(MutationOperator.REMOVE_NOT, "removed '!'", call) { copy }?.let(::add)
                }
            }

            symbols.boundarySwaps[symbol]?.let { (target, description) ->
                val copy = call.deepCopyWithSymbols(copyParent)
                plan(MutationOperator.CONDITIONAL_BOUNDARY, description, call) { builder ->
                    remapCall(builder, copy, target)
                }?.let(::add)
            }

            symbols.comparisonTexts[symbol]?.let { text ->
                val copy = call.deepCopyWithSymbols(copyParent)
                plan(MutationOperator.NEGATE_CONDITIONAL, "negated '$text'", call) { builder ->
                    builder.irNegated(copy)
                }?.let(::add)
            }

            planMathMutation(call, copyParent)?.let(::add)
            planRangeMutation(call, copyParent)?.let(::add)
        }

    private fun planRangeMutation(
        call: IrCall,
        copyParent: IrDeclarationParent,
    ): MutantPlan? {
        val owner = call.symbol.owner
        val (replacement, description) =
            when {
                call.symbol in symbols.untilSymbols -> {
                    val receiverClass = symbols.untilSymbols.getValue(call.symbol)
                    val rangeTo =
                        receiverClass.owner.declarations
                            .filterIsInstance<IrSimpleFunction>()
                            .firstOrNull { candidate ->
                                candidate.name.asString() == "rangeTo" &&
                                    candidate.singleRegularParameterType()?.classifierOrNull ==
                                    receiverClass
                            }
                            ?: return null
                    rangeTo.symbol to "replaced 'until' with '..'"
                }

                owner.name.asString() == "rangeTo" -> {
                    val parentClass = (owner.parent as? IrClass)?.symbol ?: return null
                    if (owner.singleRegularParameterType()?.classifierOrNull != parentClass) return null
                    val until =
                        symbols.untilSymbols.entries
                            .firstOrNull { it.value == parentClass }
                            ?.key
                            ?: return null
                    until to "replaced '..' with 'until'"
                }

                else -> return null
            }
        val copy = call.deepCopyWithSymbols(copyParent)
        return plan(MutationOperator.RANGE_BOUNDARY, description, call) { builder ->
            remapCall(builder, copy, replacement)
        }
    }

    private fun planMathMutation(
        call: IrCall,
        copyParent: IrDeclarationParent,
    ): MutantPlan? {
        val owner = call.symbol.owner
        val parentClass = owner.parent as? IrClass ?: return null
        if (parentClass.symbol !in symbols.numberClassSymbols) return null
        val (targetName, description) =
            symbols.mathReplacements[owner.name.asString()]
                ?: return null
        val parameterType = owner.singleRegularParameterType() ?: return null
        val replacement =
            parentClass.declarations
                .filterIsInstance<IrSimpleFunction>()
                .firstOrNull { candidate ->
                    candidate.name.asString() == targetName &&
                        candidate.singleRegularParameterType()?.classifierOrNull ==
                        parameterType.classifierOrNull
                }
                ?: return null
        val copy = call.deepCopyWithSymbols(copyParent)
        return plan(MutationOperator.MATH, description, call) { builder ->
            remapCall(builder, copy, replacement.symbol)
        }
    }

    private fun guardVoidCallRemoval(
        call: IrCall,
        scope: IrSymbolOwner,
    ): IrStatement {
        val calleeName =
            call.symbol.owner.name
                .asString()
        val plan =
            plan(MutationOperator.VOID_CALL_REMOVAL, "removed call to '$calleeName'", call) {
                it.irUnit()
            } ?: return call
        return wrapGuarded(plan, irBuiltIns.unitType, call, scope)
    }

    /**
     * The single gate every mutation passes: disabled operators and synthetic
     * elements without source offsets never become a plan. Mutant factories run
     * after the children were transformed, so anything they capture from the
     * original tree (deep copies especially) must be computed by the caller.
     */
    private fun plan(
        operator: MutationOperator,
        description: String,
        element: IrElement,
        mutantFactory: (DeclarationIrBuilder) -> IrExpression,
    ): MutantPlan? {
        if (operator !in operators) return null
        if (element.startOffset < 0) return null
        return MutantPlan(
            operator = operator,
            description = description,
            startOffset = element.startOffset,
            endOffset = element.endOffset,
            mutantFactory = mutantFactory,
        )
    }

    private fun applyPlans(
        original: IrExpression,
        type: IrType,
        plans: List<MutantPlan>,
        scope: IrSymbolOwner,
    ): IrExpression =
        plans.fold(original) { result, plan ->
            wrapGuarded(plan, type, result, scope)
        }

    private fun wrapGuarded(
        plan: MutantPlan,
        type: IrType,
        original: IrExpression,
        scope: IrSymbolOwner,
    ): IrExpression {
        val point = registerPoint(plan, scope) ?: return original
        val builder = DeclarationIrBuilder(context, scope.symbol, plan.startOffset, plan.endOffset)
        return builder.irIfThenElse(
            type = type,
            condition = builder.guardCall(point.id),
            thenPart = plan.mutantFactory(builder),
            elsePart = original,
        )
    }

    private fun DeclarationIrBuilder.guardCall(id: String): IrExpression = irCall(guard).apply { arguments[0] = irString(id) }

    private fun DeclarationIrBuilder.irNegated(expression: IrExpression): IrExpression =
        irCall(irBuiltIns.booleanNotSymbol).apply { arguments[0] = expression }

    private fun remapCall(
        builder: DeclarationIrBuilder,
        source: IrCall,
        target: IrSimpleFunctionSymbol,
    ): IrCall {
        val remapped = builder.irCall(target, source.type)
        source.arguments.forEachIndexed { index, argument -> remapped.arguments[index] = argument }
        return remapped
    }

    private fun registerPoint(
        plan: MutantPlan,
        scope: IrSymbolOwner,
    ): MutationPoint? {
        val count = mutationCounts.getOrDefault(scope, 0)
        if (count >= maxMutationsPerFunction) {
            skippedCounts.merge(scope, 1, Int::plus)
            return null
        }
        mutationCounts[scope] = count + 1
        val point =
            MutationPoint(
                id = mutationId(plan),
                operator = plan.operator.key,
                filePath = filePath,
                line = file.fileEntry.getLineNumber(plan.startOffset) + 1,
                function = scopeName(scope),
                description = plan.description,
                column = file.fileEntry.getColumnNumber(plan.startOffset) + 1,
                endLine = file.fileEntry.getLineNumber(plan.endOffset) + 1,
                endColumn = file.fileEntry.getColumnNumber(plan.endOffset) + 1,
            )
        sink(point)
        return point
    }

    private fun mutationId(plan: MutantPlan): String {
        val hash =
            digest.digest(
                "$filePath|${plan.startOffset}|${plan.endOffset}|${plan.operator.key}|${plan.description}"
                    .toByteArray(),
            )
        return buildString(hash.size * 2) {
            hash.forEach { byte ->
                val bits = byte.toInt()
                append(HEX_DIGITS[(bits shr 4) and 0xF])
                append(HEX_DIGITS[bits and 0xF])
            }
        }.take(ID_LENGTH)
    }

    private fun scopeName(scope: IrSymbolOwner?): String =
        when (scope) {
            is IrDeclarationWithName -> scope.name.asString()
            is IrAnonymousInitializer -> "<init>"
            else -> fileName
        }

    private fun copyParent(): IrDeclarationParent = (currentScope as? IrDeclarationParent) ?: file

    private fun IrFunction.hasMutableOrigin(): Boolean =
        origin == IrDeclarationOrigin.DEFINED ||
            origin == IrDeclarationOrigin.LOCAL_FUNCTION ||
            origin == IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA

    private fun IrFunction.isMutationOptOut(): Boolean = hasAnnotation(DISABLE_ANNOTATION_FQ) || hasAnnotation(COMPOSABLE_FQ)

    private inline fun <T> withScope(
        scope: IrSymbolOwner?,
        block: () -> T,
    ): T {
        val previous = currentScope
        currentScope = scope
        return try {
            block()
        } finally {
            currentScope = previous
            if (scope != null) {
                skippedCounts.remove(scope)?.let { skipped ->
                    skippedSink(
                        SkippedFunction(
                            filePath = filePath,
                            function = scopeName(scope),
                            skipped = skipped,
                        ),
                    )
                }
            }
        }
    }

    private class MutantPlan(
        val operator: MutationOperator,
        val description: String,
        val startOffset: Int,
        val endOffset: Int,
        val mutantFactory: (DeclarationIrBuilder) -> IrExpression,
    )

    companion object {
        private val ASSIGNMENT_ORIGINS =
            setOf(
                IrStatementOrigin.PLUSEQ,
                IrStatementOrigin.MINUSEQ,
                IrStatementOrigin.MULTEQ,
                IrStatementOrigin.DIVEQ,
                IrStatementOrigin.PERCEQ,
                IrStatementOrigin.PREFIX_INCR,
                IrStatementOrigin.PREFIX_DECR,
                IrStatementOrigin.POSTFIX_INCR,
                IrStatementOrigin.POSTFIX_DECR,
            )

        private const val ID_LENGTH = 12
        private const val STRING_PREVIEW_LENGTH = 40
        private const val HEX_DIGITS = "0123456789abcdef"
        private val DISABLE_ANNOTATION_FQ = FqName(MutationPluginNames.DISABLE_ANNOTATION)
        private val COMPOSABLE_FQ = FqName("androidx.compose.runtime.Composable")
    }
}
