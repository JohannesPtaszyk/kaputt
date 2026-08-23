package example

/** Paid tier: more devices, and a grace allowance the tests never pin down. */
object DeviceLimit {

    const val MAX_DEVICES = 10
    const val GRACE_DEVICES = 2

    fun overLimit(devices: Int): Boolean = devices > MAX_DEVICES + GRACE_DEVICES
}
