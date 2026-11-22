package io.github.itsoumyad.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncStatusTest {
    @Test
    fun testInitialStatus() {
        val result = SyncResult(processedCount = 0, failedCount = 0, isComplete = true)
        assertEquals(0, result.processedCount)
        assertEquals(0, result.failedCount)
        assertTrue(result.isComplete)
    }
}
