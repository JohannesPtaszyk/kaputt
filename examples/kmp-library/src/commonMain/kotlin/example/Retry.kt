package example

/** Backoff shared by every platform, in milliseconds. */
object Retry {

    const val MAX_ATTEMPTS = 5
    const val BASE_DELAY_MS = 100

    fun delayFor(attempt: Int): Int = BASE_DELAY_MS * (attempt + 1)

    fun shouldRetry(attempt: Int, failed: Boolean): Boolean =
        failed && attempt < MAX_ATTEMPTS
}
