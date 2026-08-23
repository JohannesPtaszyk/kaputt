package example

/** Decides when queued work may reach the network. */
object SyncWindow {

    const val LOW_BATTERY_PERCENT = 20

    fun maySync(batteryPercent: Int, metered: Boolean): Boolean =
        !metered && batteryPercent > LOW_BATTERY_PERCENT

    fun backoffMinutes(consecutiveFailures: Int): Int = consecutiveFailures * 5
}
