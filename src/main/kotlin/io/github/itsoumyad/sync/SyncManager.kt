package io.github.itsoumyad.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface RemoteSyncClient {
    suspend fun execute(operation: SyncOperation): RemoteResponse

    sealed interface RemoteResponse {
        data class Success(val serverTimestamp: Long = System.currentTimeMillis()) : RemoteResponse
        data class Conflict(
            val serverPayload: Map<String, Any?>,
            val serverTimestamp: Long
        ) : RemoteResponse
        data class Error(val message: String, val isTransient: Boolean) : RemoteResponse
    }
}

class SyncManager(
    private val queue: SyncQueue,
    private val client: RemoteSyncClient,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
    private val conflictResolver: ConflictResolver = ConflictResolver.TimestampResolution
) {
    private val _status = MutableStateFlow(SyncStatus.IDLE)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val syncMutex = Mutex()
    private val deadLetterQueue = mutableListOf<SyncOperation>()

    suspend fun syncAll(): SyncResult = syncMutex.withLock {
        _status.value = SyncStatus.SYNCING
        var processed = 0
        var failed = 0

        try {
            while (true) {
                val op = queue.peekNext() ?: break
                when (val response = client.execute(op)) {
                    is RemoteSyncClient.RemoteResponse.Success -> {
                        queue.dequeue()
                        processed++
                    }
                    is RemoteSyncClient.RemoteResponse.Conflict -> {
                        val resolution = conflictResolver.resolve(
                            local = op,
                            serverPayload = response.serverPayload,
                            serverTimestamp = response.serverTimestamp
                        )
                        val resolvedOp = op.copy(payload = resolution.acceptedPayload)
                        // Retry with resolved payload
                        when (client.execute(resolvedOp)) {
                            is RemoteSyncClient.RemoteResponse.Success -> {
                                queue.dequeue()
                                processed++
                            }
                            else -> {
                                handleFailure(op, "Failed after conflict resolution")
                                failed++
                            }
                        }
                    }
                    is RemoteSyncClient.RemoteResponse.Error -> {
                        if (response.isTransient && retryPolicy.shouldRetry(op.retryCount)) {
                            val updatedOp = op.withIncrementedRetry(response.message)
                            queue.update(updatedOp)
                            failed++
                            break // Pause sync for backoff
                        } else {
                            handleFailure(op, response.message)
                            failed++
                        }
                    }
                }
            }
            _status.value = if (failed > 0) SyncStatus.ERROR else SyncStatus.IDLE
        } catch (e: Exception) {
            _status.value = SyncStatus.ERROR
        }

        return SyncResult(
            processedCount = processed,
            failedCount = failed,
            isComplete = queue.size() == 0
        )
    }

    private suspend fun handleFailure(op: SyncOperation, reason: String) {
        deadLetterQueue.add(op.copy(lastError = reason))
        queue.dequeue()
    }

    fun getDeadLetters(): List<SyncOperation> = deadLetterQueue.toList()

    fun setOffline() {
        _status.value = SyncStatus.OFFLINE
    }
}
