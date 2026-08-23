package dev.pott.kaputt.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import java.io.File

class MutationIrGenerationExtension(
    private val outputDir: File,
    private val filter: MutationTargetFilter,
    private val operators: Set<MutationOperator>,
    private val maxMutationsPerFunction: Int,
) : IrGenerationExtension {
    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
    ) {
        val guard =
            pluginContext
                .referenceFunctions(
                    CallableId(
                        FqName(MutationPluginNames.RUNTIME_PACKAGE),
                        Name.identifier(MutationPluginNames.GUARD_FUNCTION),
                    ),
                ).singleOrNull()
                ?: error(
                    "Mutation runtime (dev.pott.kaputt:runtime) is missing from the compile " +
                        "classpath. The dev.pott.kaputt Gradle plugin adds it automatically.",
                )
        val nullPointer =
            pluginContext
                .referenceFunctions(
                    CallableId(
                        FqName(MutationPluginNames.RUNTIME_PACKAGE),
                        Name.identifier(MutationPluginNames.NULL_POINTER_FUNCTION),
                    ),
                ).single()

        val deadline =
            pluginContext
                .referenceFunctions(
                    CallableId(
                        FqName(MutationPluginNames.RUNTIME_PACKAGE),
                        Name.identifier(MutationPluginNames.DEADLINE_FUNCTION),
                    ),
                ).single()

        val symbols = MutationIrSymbols(pluginContext)
        val points = mutableListOf<MutationPoint>()
        val skipped = mutableListOf<MutationTransformer.SkippedFunction>()
        moduleFragment.files.forEach { file ->
            val filePath = file.fileEntry.name
            val packageAndFileName = "${file.packageFqName.asString()}.${File(filePath).name}"
            if (!filter.matches(filePath, packageAndFileName)) return@forEach
            file.transform(
                MutationTransformer(
                    context = pluginContext,
                    symbols = symbols,
                    guard = guard,
                    nullPointerMutant = nullPointer,
                    deadlineCheck = deadline,
                    file = file,
                    operators = operators,
                    maxMutationsPerFunction = maxMutationsPerFunction,
                    sink = points::add,
                    skippedSink = skipped::add,
                ),
                null,
            )
        }

        outputDir.mkdirs()
        outputDir
            .resolve(MutationPluginNames.MUTATIONS_FILE_NAME)
            .writeText(MutationPointCodec.encode(points))
        outputDir
            .resolve(MutationPluginNames.SKIPPED_FILE_NAME)
            .writeText(SkippedFunctionCodec.encode(skipped))
    }
}
