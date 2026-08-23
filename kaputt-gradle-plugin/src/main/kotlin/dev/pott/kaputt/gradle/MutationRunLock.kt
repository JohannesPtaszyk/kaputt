package dev.pott.kaputt.gradle

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * Serialises `mutationTest` across modules. Each task already saturates the
 * machine with forks, so letting two modules run at once only makes both
 * slower and distorts the timeouts derived from their baselines.
 */
abstract class MutationRunLock : BuildService<BuildServiceParameters.None>
