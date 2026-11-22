package io.github.itsoumyad.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface SyncQueue {
    suspend fun enqueue(operation: SyncOperation): Boolean
    suspend fun peekNext(): SyncOperation?
    suspend fun dequeue(): SyncOperation?
    suspend fun update(operation: SyncOperation)
    suspend fun size(): Int
    suspend fun clear()
    suspend fun getAllPending(): List<SyncOperation>
}

class MemorySyncQueue : SyncQueue {
    private val mutex = Mutex()
    private val queue = mutableListOf<SyncOperation>()

    override suspend fun enqueue(operation: SyncOperation): Boolean = mutex.withLock {
        queue.add(operation)
        true
    }

    override suspend fun peekNext(): SyncOperation? = mutex.withLock {
        queue.firstOrNull()
    }

    override suspend fun dequeue(): SyncOperation? = mutex.withLock {
        if (queue.isNotEmpty()) {
            queue.removeAt(0)
        } else {
            null
        }
    }

    override suspend fun update(operation: SyncOperation): Unit = mutex.withLock {
        val index = queue.indexOfFirst { it.id == operation.id }
        if (index >= 0) {
            queue[index] = operation
        }
    }

    override suspend fun size(): Int = mutex.withLock {
        queue.size
    }

    override suspend fun clear(): Unit = mutex.withLock {
        queue.clear()
    }

    override suspend fun getAllPending(): List<SyncOperation> = mutex.withLock {
        queue.toList()
    }
}
