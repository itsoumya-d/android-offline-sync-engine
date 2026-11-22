package io.github.itsoumyad.sync

import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

data class RetryPolicy(
    val maxRetries: Int = 3,
    val initialDelayMs: Long = 500L,
    val maxDelayMs: Long = 10_000L,
    val backoffMultiplier: Double = 2.0,
    val enableJitter: Boolean = true
) {
    fun shouldRetry(currentRetryCount: Int): Boolean {
        return currentRetryCount < maxRetries
    }

    fun calculateDelayMs(retryCount: Int): Long {
        if (retryCount <= 0) return 0L
        val exponential = (initialDelayMs * backoffMultiplier.pow((retryCount - 1).toDouble())).toLong()
        val capped = min(exponential, maxDelayMs)
        return if (enableJitter && capped > 0) {
            val jitter = Random.nextLong(0, capped / 2)
            capped + jitter
        } else {
            capped
        }
    }
}
