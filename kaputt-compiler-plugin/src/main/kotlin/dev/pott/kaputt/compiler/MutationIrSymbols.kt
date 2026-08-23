package dev.pott.kaputt.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrClassifierSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Symbol lookups shared by every [MutationTransformer] of one module: they only
 * depend on the compilation, so resolving them once per module keeps the
 * per-file transformers cheap to build.
 */
class MutationIrSymbols(
    context: IrPluginContext,
) {
    private val irBuiltIns = context.irBuiltIns

    val numberClassSymbols: Set<IrClassSymbol> =
        setOf(
            irBuiltIns.intClass,
            irBuiltIns.longClass,
            irBuiltIns.shortClass,
            irBuiltIns.byteClass,
            irBuiltIns.floatClass,
            irBuiltIns.doubleClass,
        )

    val mathReplacements: Map<String, Pair<String, String>> =
        mapOf(
            "plus" to ("minus" to "replaced '+' with '-'"),
            "minus" to ("plus" to "replaced '-' with '+'"),
            "times" to ("div" to "replaced '*' with '/'"),
            "div" to ("times" to "replaced '/' with '*'"),
            "rem" to ("times" to "replaced '%' with '*'"),
        )

    val boundarySwaps: Map<IrSimpleFunctionSymbol, Pair<IrSimpleFunctionSymbol, String>> =
        buildMap {
            fun register(
                from: Map<IrClassifierSymbol, IrSimpleFunctionSymbol>,
                to: Map<IrClassifierSymbol, IrSimpleFunctionSymbol>,
                fromText: String,
                toText: String,
            ) {
                from.forEach { (classifier, symbol) ->
                    to[classifier]?.let { target ->
                        put(symbol, target to "replaced '$fromText' with '$toText'")
                    }
                }
            }
            register(
                irBuiltIns.lessFunByOperandType,
                irBuiltIns.lessOrEqualFunByOperandType,
                "<",
                "<=",
            )
            register(
                irBuiltIns.lessOrEqualFunByOperandType,
                irBuiltIns.lessFunByOperandType,
                "<=",
                "<",
            )
            register(
                irBuiltIns.greaterFunByOperandType,
                irBuiltIns.greaterOrEqualFunByOperandType,
                ">",
                ">=",
            )
            register(
                irBuiltIns.greaterOrEqualFunByOperandType,
                irBuiltIns.greaterFunByOperandType,
                ">=",
                ">",
            )
        }

    val comparisonTexts: Map<IrSimpleFunctionSymbol, String> =
        buildMap {
            put(irBuiltIns.eqeqSymbol, "==")
            irBuiltIns.ieee754equalsFunByOperandType.values.forEach { put(it, "==") }
            irBuiltIns.lessFunByOperandType.values.forEach { put(it, "<") }
            irBuiltIns.lessOrEqualFunByOperandType.values.forEach { put(it, "<=") }
            irBuiltIns.greaterFunByOperandType.values.forEach { put(it, ">") }
            irBuiltIns.greaterOrEqualFunByOperandType.values.forEach { put(it, ">=") }
        }

    val rangeReceiverClasses: Set<IrClassSymbol> =
        setOf(irBuiltIns.intClass, irBuiltIns.longClass)

    /** `until` extensions from kotlin.ranges whose receiver and bound types match. */
    val untilSymbols: Map<IrSimpleFunctionSymbol, IrClassSymbol> =
        context
            .referenceFunctions(CallableId(FqName("kotlin.ranges"), Name.identifier("until")))
            .mapNotNull { symbol ->
                val receiver =
                    symbol.owner.parameters
                        .firstOrNull { it.kind == IrParameterKind.ExtensionReceiver }
                        ?.type
                        ?.classifierOrNull as? IrClassSymbol
                        ?: return@mapNotNull null
                val bound = symbol.owner.singleRegularParameterType()?.classifierOrNull
                if (receiver in rangeReceiverClasses && bound == receiver) symbol to receiver else null
            }.toMap()

    val emptyListSymbol: IrSimpleFunctionSymbol = context.referenceCollectionFactory("emptyList")
    val emptySetSymbol: IrSimpleFunctionSymbol = context.referenceCollectionFactory("emptySet")
    val emptyMapSymbol: IrSimpleFunctionSymbol = context.referenceCollectionFactory("emptyMap")

    private fun IrPluginContext.referenceCollectionFactory(name: String): IrSimpleFunctionSymbol =
        referenceFunctions(CallableId(FqName("kotlin.collections"), Name.identifier(name))).single()
}

internal fun IrFunction.singleRegularParameterType(): IrType? = parameters.singleOrNull { it.kind == IrParameterKind.Regular }?.type
