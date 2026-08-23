package example

/** Decides when background sync may run. */
object BatterySaver {

    const val LOW_BATTERY_PERCENT = 20

    fun maySync(batteryPercent: Int, charging: Boolean): Boolean =
        charging || batteryPercent > LOW_BATTERY_PERCENT

    fun backoffMinutes(consecutiveFailures: Int): Int = consecutiveFailures * 5
}
