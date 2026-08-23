package dev.pott.kaputt.compiler

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

object MutationPluginNames {
    const val PLUGIN_ID = "dev.pott.kaputt"
    const val RUNTIME_PACKAGE = "dev.pott.kaputt.runtime"
    const val GUARD_FUNCTION = "isMutationActive"
    const val DEADLINE_FUNCTION = "mutationCheckDeadline"
    const val NULL_POINTER_FUNCTION = "mutationNullPointer"
    const val DISABLE_ANNOTATION = "$RUNTIME_PACKAGE.DisableMutation"
    const val MUTATIONS_FILE_NAME = "mutations.tsv"
    const val SKIPPED_FILE_NAME = "skipped.tsv"
    const val DEFAULT_MAX_MUTATIONS_PER_FUNCTION = 100
}

val KEY_ENABLED = CompilerConfigurationKey<Boolean>("whether mutation instrumentation is enabled")
val KEY_OUTPUT_DIR =
    CompilerConfigurationKey<String>("directory the mutation manifest is written to")
val KEY_INCLUDES = CompilerConfigurationKey<List<String>>("include globs")
val KEY_EXCLUDES = CompilerConfigurationKey<List<String>>("exclude globs")
val KEY_OPERATORS = CompilerConfigurationKey<List<String>>("operator keys")
val KEY_MAX_PER_FUNCTION = CompilerConfigurationKey<String>("mutation cap per compiled function")

private fun CompilerConfiguration.append(
    key: CompilerConfigurationKey<List<String>>,
    value: String,
) {
    put(key, (get(key) ?: emptyList()) + value)
}

@OptIn(ExperimentalCompilerApi::class)
class MutationCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = MutationPluginNames.PLUGIN_ID

    override val pluginOptions: Collection<AbstractCliOption> =
        listOf(
            OPTION_ENABLED,
            OPTION_OUTPUT_DIR,
            OPTION_INCLUDES,
            OPTION_EXCLUDES,
            OPTION_OPERATORS,
            OPTION_MAX_PER_FUNCTION,
        )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        when (option.optionName) {
            OPTION_ENABLED.optionName -> configuration.put(KEY_ENABLED, value.toBooleanStrict())

            OPTION_OUTPUT_DIR.optionName -> configuration.put(KEY_OUTPUT_DIR, value)

            // One option per value: a comma-joined value is split by the
            // Kotlin Gradle plugin before the compiler ever sees it.
            OPTION_INCLUDES.optionName -> configuration.append(KEY_INCLUDES, value)

            OPTION_EXCLUDES.optionName -> configuration.append(KEY_EXCLUDES, value)

            OPTION_OPERATORS.optionName -> configuration.append(KEY_OPERATORS, value)

            OPTION_MAX_PER_FUNCTION.optionName -> configuration.put(KEY_MAX_PER_FUNCTION, value)

            else -> error("Unexpected plugin option ${option.optionName}")
        }
    }

    companion object {
        val OPTION_ENABLED =
            CliOption(
                optionName = "enabled",
                valueDescription = "true|false",
                description = "Whether mutation instrumentation is applied",
                required = false,
            )
        val OPTION_OUTPUT_DIR =
            CliOption(
                optionName = "outputDir",
                valueDescription = "directory",
                description = "Directory the mutation manifest is written to",
                required = false,
            )
        val OPTION_INCLUDES =
            CliOption(
                optionName = "includes",
                valueDescription = "glob",
                description = "Glob limiting mutated files; repeatable",
                required = false,
                allowMultipleOccurrences = true,
            )
        val OPTION_EXCLUDES =
            CliOption(
                optionName = "excludes",
                valueDescription = "glob",
                description = "Glob excluding files from mutation; repeatable",
                required = false,
                allowMultipleOccurrences = true,
            )
        val OPTION_OPERATORS =
            CliOption(
                optionName = "operators",
                valueDescription = "key",
                description = "Mutation operator key; repeatable, default all",
                required = false,
                allowMultipleOccurrences = true,
            )
        val OPTION_MAX_PER_FUNCTION =
            CliOption(
                optionName = "maxMutationsPerFunction",
                valueDescription = "count",
                description = "Cap of mutations per compiled function; skips are reported",
                required = false,
            )
    }
}
