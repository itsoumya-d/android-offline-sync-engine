package io.github.itsoumyad.sync

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SyncEngineTest {

    @Test
    fun testQueueEnqueueDequeueOrder() = runTest {
        val queue = MemorySyncQueue()
        val op1 = SyncOperation(entityType = "user", entityId = "u1", operationType = OperationType.CREATE)
        val op2 = SyncOperation(entityType = "profile", entityId = "p1", operationType = OperationType.UPDATE)

        queue.enqueue(op1)
        queue.enqueue(op2)
        assertEquals(2, queue.size())

        val peeked = queue.peekNext()
        assertEquals(op1.id, peeked?.id)

        val dequeued1 = queue.dequeue()
        assertEquals(op1.id, dequeued1?.id)
        assertEquals(1, queue.size())

        val dequeued2 = queue.dequeue()
        assertEquals(op2.id, dequeued2?.id)
        assertEquals(0, queue.size())
    }

    @Test
    fun testRetryPolicyCalculations() {
        val policy = RetryPolicy(
            maxRetries = 3,
            initialDelayMs = 100L,
            maxDelayMs = 1000L,
            backoffMultiplier = 2.0,
            enableJitter = false
        )

        assertTrue(policy.shouldRetry(0))
        assertTrue(policy.shouldRetry(1))
        assertTrue(policy.shouldRetry(2))
        assertFalse(policy.shouldRetry(3))

        assertEquals(0L, policy.calculateDelayMs(0))
        assertEquals(100L, policy.calculateDelayMs(1))
        assertEquals(200L, policy.calculateDelayMs(2))
        assertEquals(400L, policy.calculateDelayMs(3))
    }

    @Test
    fun testConflictResolvers() {
        val localOp = SyncOperation(
            entityType = "note",
            entityId = "n1",
            operationType = OperationType.UPDATE,
            payload = mapOf("title" to "Local Title"),
            clientTimestamp = 2000L
        )
        val serverPayload = mapOf("title" to "Server Title")
        val serverTimestamp = 1000L

        // ClientWins
        val clientResult = ConflictResolver.ClientWins.resolve(localOp, serverPayload, serverTimestamp)
        assertEquals("Local Title", clientResult.acceptedPayload["title"])

        // ServerWins
        val serverResult = ConflictResolver.ServerWins.resolve(localOp, serverPayload, serverTimestamp)
        assertEquals("Server Title", serverResult.acceptedPayload["title"])

        // Timestamp - Local is newer
        val tsResultLocal = ConflictResolver.TimestampResolution.resolve(localOp, serverPayload, 1000L)
        assertEquals("Local Title", tsResultLocal.acceptedPayload["title"])

        // Timestamp - Server is newer
        val tsResultServer = ConflictResolver.TimestampResolution.resolve(localOp, serverPayload, 3000L)
        assertEquals("Server Title", tsResultServer.acceptedPayload["title"])
    }

    @Test
    fun testSyncManagerSuccessfulSync() = runTest {
        val queue = MemorySyncQueue()
        val op = SyncOperation(
            entityType = "document",
            entityId = "d1",
            operationType = OperationType.CREATE,
            payload = mapOf("content" to "hello")
        )
        queue.enqueue(op)

        val mockClient = object : RemoteSyncClient {
            override suspend fun execute(operation: SyncOperation): RemoteSyncClient.RemoteResponse {
                return RemoteSyncClient.RemoteResponse.Success()
            }
        }

        val manager = SyncManager(queue, mockClient)
        val result = manager.syncAll()

        assertEquals(1, result.processedCount)
        assertEquals(0, result.failedCount)
        assertTrue(result.isComplete)
        assertEquals(SyncStatus.IDLE, manager.status.value)
    }

    @Test
    fun testSyncManagerConflictResolution() = runTest {
        val queue = MemorySyncQueue()
        val op = SyncOperation(
            entityType = "account",
            entityId = "a1",
            operationType = OperationType.UPDATE,
            payload = mapOf("email" to "local@entelect.co.nz"),
            clientTimestamp = 5000L
        )
        queue.enqueue(op)

        var callCount = 0
        val mockClient = object : RemoteSyncClient {
            override suspend fun execute(operation: SyncOperation): RemoteSyncClient.RemoteResponse {
                callCount++
                return if (callCount == 1) {
                    RemoteSyncClient.RemoteResponse.Conflict(
                        serverPayload = mapOf("email" to "server@entelect.co.nz"),
                        serverTimestamp = 4000L
                    )
                } else {
                    RemoteSyncClient.RemoteResponse.Success()
                }
            }
        }

        val manager = SyncManager(
            queue = queue,
            client = mockClient,
            conflictResolver = ConflictResolver.TimestampResolution
        )
        val result = manager.syncAll()

        assertEquals(1, result.processedCount)
        assertEquals(2, callCount) // 1 initial conflict, 1 resolved success
        assertTrue(result.isComplete)
    }

    @Test
    fun testSyncManagerDeadLetterOnPermanentFailure() = runTest {
        val queue = MemorySyncQueue()
        val op = SyncOperation(
            entityType = "order",
            entityId = "ord-99",
            operationType = OperationType.DELETE
        )
        queue.enqueue(op)

        val mockClient = object : RemoteSyncClient {
            override suspend fun execute(operation: SyncOperation): RemoteSyncClient.RemoteResponse {
                return RemoteSyncClient.RemoteResponse.Error("Bad Request - 400", isTransient = false)
            }
        }

        val manager = SyncManager(queue, mockClient)
        val result = manager.syncAll()

        assertEquals(0, result.processedCount)
        assertEquals(1, result.failedCount)
        assertTrue(result.isComplete) // Dequeued to dead letters
        assertEquals(1, manager.getDeadLetters().size)
        assertEquals("Bad Request - 400", manager.getDeadLetters().first().lastError)
        assertEquals(SyncStatus.ERROR, manager.status.value)
    }
}
