package com.autu.lio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

class ScriptFilePickerCoordinatorTest {
    @Test
    fun returnsTheResultCompletedByTheActivityBridge() {
        val id = ScriptFilePickerCoordinator.register()
        try {
            val expected = ScriptFilePickerResult.Imported("imports/report.csv", 25, "report.csv")
            assertTrue(ScriptFilePickerCoordinator.complete(id, expected))
            assertEquals(expected, ScriptFilePickerCoordinator.await(id, 500) { false })
        } finally {
            ScriptFilePickerCoordinator.remove(id)
        }
    }

    @Test
    fun rejectsDuplicateCompletionAndUnknownRequests() {
        val id = ScriptFilePickerCoordinator.register()
        try {
            assertTrue(ScriptFilePickerCoordinator.complete(id, ScriptFilePickerResult.Cancelled))
            assertFalse(ScriptFilePickerCoordinator.complete(id, ScriptFilePickerResult.Exported(0)))
        } finally {
            ScriptFilePickerCoordinator.remove(id)
        }
        assertFalse(ScriptFilePickerCoordinator.complete(id, ScriptFilePickerResult.Cancelled))
    }

    @Test
    fun canBeCancelledWhileWaitingForThePicker() {
        val id = ScriptFilePickerCoordinator.register()
        val started = CountDownLatch(1)
        val error = AtomicReference<Throwable?>()
        val waiter = Thread {
            try {
                started.countDown()
                ScriptFilePickerCoordinator.await(id, 5_000) { true }
            } catch (failure: Throwable) {
                error.set(failure)
            }
        }
        try {
            waiter.start()
            assertTrue(started.await(1, TimeUnit.SECONDS))
            waiter.join(1_000)
            assertFalse(waiter.isAlive)
            assertTrue(error.get() is InterruptedException)
        } finally {
            ScriptFilePickerCoordinator.remove(id)
        }
    }

    @Test
    fun enforcesTheRequestTimeout() {
        val id = ScriptFilePickerCoordinator.register()
        try {
            val error = runCatching { ScriptFilePickerCoordinator.await(id, 20) { false } }.exceptionOrNull()
            assertTrue(error is TimeoutException)
        } finally {
            ScriptFilePickerCoordinator.remove(id)
        }
    }
}
