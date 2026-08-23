package dev.pott.kaputt.compiler

import dev.pott.kaputt.runtime.MUTATION_PROPERTY
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.URLClassLoader
import kotlin.io.path.createTempDirectory

/**
 * Compiles fixture sources with the real K2 compiler and the freshly built
 * plugin jar, then loads the result in an isolated classloader so each mutant
 * activation gets a fresh static initializer for the mutation runtime.
 */
object MutationCompilerHarness {
    private val pluginJar = File(requireNotNull(System.getProperty("mutation.test.pluginJar")))
    private val fixtureClasspath =
        requireNotNull(
            System.getProperty("mutation.test.fixtureClasspath"),
        ).split(File.pathSeparator)
            .map(::File)

    data class CompiledFixture(
        val outputDir: File,
        val mutations: List<MutationPoint>,
        val skipped: List<MutationTransformer.SkippedFunction>,
    )

    fun compile(
        source: String,
        extraPluginOptions: List<String> = emptyList(),
    ): CompiledFixture {
        val workDir = createTempDirectory("mutation-plugin-test").toFile()
        val sourceFile = workDir.resolve("Fixture.kt").apply { writeText(source) }
        val outputDir = workDir.resolve("classes")
        val mutationDir = workDir.resolve("mutation")

        val args =
            buildList {
                add("-classpath")
                add(fixtureClasspath.joinToString(File.pathSeparator) { it.absolutePath })
                add("-d")
                add(outputDir.absolutePath)
                add("-Xplugin=${pluginJar.absolutePath}")
                addPluginOption("enabled=true")
                addPluginOption("outputDir=${mutationDir.absolutePath}")
                extraPluginOptions.forEach(::addPluginOption)
                add(sourceFile.absolutePath)
            }

        val messages = ByteArrayOutputStream()
        val exitCode = K2JVMCompiler().exec(PrintStream(messages), *args.toTypedArray())
        check(exitCode == ExitCode.OK) { "Fixture compilation failed:\n$messages" }

        val mutationsFile = mutationDir.resolve(MutationPluginNames.MUTATIONS_FILE_NAME)
        check(mutationsFile.isFile) {
            "Compiler plugin did not write ${mutationsFile.absolutePath}"
        }
        val skippedFile = mutationDir.resolve(MutationPluginNames.SKIPPED_FILE_NAME)
        return CompiledFixture(
            outputDir = outputDir,
            mutations = MutationPointCodec.decode(mutationsFile.readText()),
            skipped =
                if (skippedFile.isFile) {
                    SkippedFunctionCodec.decode(skippedFile.readText())
                } else {
                    emptyList()
                },
        )
    }

    fun <T> withMutant(
        compiled: CompiledFixture,
        mutationId: String?,
        block: (ClassLoader) -> T,
    ): T {
        if (mutationId == null) {
            System.clearProperty(MUTATION_PROPERTY)
        } else {
            System.setProperty(MUTATION_PROPERTY, mutationId)
        }
        val urls =
            (listOf(compiled.outputDir) + fixtureClasspath)
                .map { it.toURI().toURL() }
                .toTypedArray()
        return try {
            URLClassLoader(urls, ClassLoader.getPlatformClassLoader()).use(block)
        } finally {
            System.clearProperty(MUTATION_PROPERTY)
        }
    }

    fun invokeStatic(
        loader: ClassLoader,
        className: String,
        methodName: String,
        vararg args: Any?,
    ): Any? {
        val method =
            loader
                .loadClass(className)
                .methods
                .single { it.name == methodName }
        return method.invoke(null, *args)
    }
}

private fun MutableList<String>.addPluginOption(option: String) {
    add("-P")
    add("plugin:${MutationPluginNames.PLUGIN_ID}:$option")
}
