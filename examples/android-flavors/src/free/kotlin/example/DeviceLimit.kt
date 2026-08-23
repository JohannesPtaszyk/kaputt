package example

/** Free tier: one device, checked by the shared test. */
object DeviceLimit {

    const val MAX_DEVICES = 1

    fun overLimit(devices: Int): Boolean = devices > MAX_DEVICES
}
