package io.github.itsoumyad.sync

enum class SyncStatus {
    IDLE,
    SYNCING,
    OFFLINE,
    ERROR
}

data class SyncResult(
    val processedCount: Int,
    val failedCount: Int,
    val isComplete: Boolean
)
