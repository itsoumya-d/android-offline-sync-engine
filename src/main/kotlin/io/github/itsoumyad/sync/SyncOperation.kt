package io.github.itsoumyad.sync

import java.util.UUID

enum class OperationType {
    CREATE,
    UPDATE,
    DELETE
}

data class SyncOperation(
    val id: String = UUID.randomUUID().toString(),
    val entityType: String,
    val entityId: String,
    val operationType: OperationType,
    val payload: Map<String, Any?> = emptyMap(),
    val clientTimestamp: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val lastError: String? = null
) {
    fun withIncrementedRetry(error: String?): SyncOperation {
        return copy(
            retryCount = retryCount + 1,
            lastError = error
        )
    }
}
