package example

/** Shared by both tiers, so every variant mutates it. */
object Sync {

    fun backoffMinutes(consecutiveFailures: Int): Int = consecutiveFailures * 5

    fun maySync(pendingChanges: Int): Boolean = pendingChanges > 0
}
