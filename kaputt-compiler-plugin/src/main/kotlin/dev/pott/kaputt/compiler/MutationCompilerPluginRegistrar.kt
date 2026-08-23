package dev.pott.kaputt.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import java.io.File

@OptIn(ExperimentalCompilerApi::class)
class MutationCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = MutationPluginNames.PLUGIN_ID

    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        if (configuration.get(KEY_ENABLED) != true) return
        val outputDir =
            requireNotNull(configuration.get(KEY_OUTPUT_DIR)) {
                "The '${MutationPluginNames.PLUGIN_ID}' plugin requires the 'outputDir' option when enabled"
            }
        IrGenerationExtension.registerExtension(
            MutationIrGenerationExtension(
                outputDir = File(outputDir),
                filter =
                    MutationTargetFilter(
                        includes = configuration.get(KEY_INCLUDES).orEmpty(),
                        excludes = configuration.get(KEY_EXCLUDES).orEmpty(),
                    ),
                operators = MutationOperator.fromKeys(configuration.get(KEY_OPERATORS).orEmpty()),
                maxMutationsPerFunction =
                    configuration.get(KEY_MAX_PER_FUNCTION)?.toInt()
                        ?: MutationPluginNames.DEFAULT_MAX_MUTATIONS_PER_FUNCTION,
            ),
        )
    }
}
