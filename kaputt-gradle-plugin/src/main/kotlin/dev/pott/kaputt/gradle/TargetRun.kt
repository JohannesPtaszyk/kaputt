package dev.pott.kaputt.gradle

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.jvm.toolchain.JavaLauncher

/**
 * One Kotlin target a mutant is verified against. Jvm targets fork a JVM over
 * the test classpath; native targets execute the kotlin-test binary produced
 * by the target's `linkDebugTest` task (through `simctl spawn` on simulators);
 * an Android device target instruments the installed apks over adb.
 */
abstract class TargetRun {
    @get:Input
    abstract val targetName: Property<String>

    @get:Input
    abstract val kind: Property<TargetKind>

    /** Manifest directory of the compilation this run verifies; defaults to the name. */
    @get:Input
    @get:Optional
    abstract val manifestName: Property<String>

    @get:Classpath
    abstract val classpath: ConfigurableFileCollection

    @get:Classpath
    abstract val testClassesDirs: ConfigurableFileCollection

    /** JVM arguments of the module's own test task, so the fork matches it. */
    @get:Input
    abstract val jvmArgs: ListProperty<String>

    @get:Input
    abstract val environment: MapProperty<String, String>

    /** Gradle test filter patterns of the module's test task. */
    @get:Input
    abstract val includePatterns: ListProperty<String>

    @get:Input
    abstract val excludePatterns: ListProperty<String>

    /** The JVM the mutant runs fork; unset for native targets. */
    @get:Nested
    @get:Optional
    abstract val javaLauncher: Property<JavaLauncher>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val testBinary: ConfigurableFileCollection

    @get:Input
    @get:Optional
    abstract val simulatorDevice: Property<String>

    /** Android SDK adb, used to drive instrumented runs. */
    @get:Input
    @get:Optional
    abstract val adbPath: Property<String>

    /** Serial of the device to run on; unset picks the only connected one. */
    @get:Input
    @get:Optional
    abstract val deviceSerial: Property<String>

    /** Build outputs of the androidTest apk, which name its applicationId. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val androidTestOutputs: ConfigurableFileCollection

    enum class TargetKind { JVM, NATIVE, NATIVE_SIMULATOR, ANDROID_DEVICE }
}
