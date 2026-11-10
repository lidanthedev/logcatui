package me.lidan.logcatui

import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class LogcatControllerTest {
    @Test
    fun `refresh and level changes discard queued old stream entries`() = runBlocking {
        val service = FakeLogcatService()
        val controller = LogcatController(service)
        try {
            onEdt {
                controller.selectDevice("test-device")
                controller.togglePause()
            }

            val firstStream = service.awaitStream(1)
            val entry = entry(1, "replayed after refresh")
            firstStream.emit(entry)
            delay(80)

            onEdt { controller.selectLevel(LogLevel.Error) }
            val secondStream = service.awaitStream(2)
            secondStream.emit(entry(2, "stale after level change"))
            delay(80)

            onEdt { controller.refreshLogs() }
            val refreshedStream = service.awaitStream(3)
            refreshedStream.emit(entry)
            delay(80)

            onEdt { controller.togglePause() }
            awaitCondition { onEdtValue { controller.logs.isNotEmpty() } }
            delay(120) // Allow every paused append to wake before checking for late duplicates.

            assertEquals(listOf(entry), onEdtValue { controller.logs.toList() })
        } finally {
            onEdt { controller.close() }
        }
    }

    @Test
    fun `paused process lifecycle marker survives stream restart`() = runBlocking {
        val service = FakeLogcatService()
        val controller = LogcatController(service)
        try {
            onEdt {
                controller.selectDevice("test-device")
                controller.togglePause()
                controller.selectProcess(ProcessDescriptor(pid = 73, name = "example.app"))
            }

            onEdt { assertNull(controller.selectedProcessPid) }
            onEdt { controller.togglePause() }
            awaitCondition { onEdtValue { controller.logs.isNotEmpty() } }

            assertEquals(
                listOf("----- Process stopped: pid: 73 ------"),
                onEdtValue { controller.logs.map(LogEntry::rawLine) },
            )
        } finally {
            onEdt { controller.close() }
        }
    }

    private fun entry(id: Long, rawLine: String) =
        LogEntry(
            id = id,
            timestamp = "03-25 12:00:00.000",
            pid = 42,
            tid = 43,
            level = LogLevel.Error,
            tag = "Test",
            message = rawLine,
            rawLine = rawLine,
            receivedAt = Instant.EPOCH,
        )

    private suspend fun awaitCondition(condition: () -> Boolean) {
        withTimeout(2_000) {
            while (!condition()) delay(10)
        }
    }

    private fun onEdt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeAndWait(block)
    }

    private fun <T> onEdtValue(block: () -> T): T {
        var result: Result<T>? = null
        SwingUtilities.invokeAndWait { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    private class FakeLogcatService : LogcatService {
        private val streams = CopyOnWriteArrayList<MutableSharedFlow<LogEntry>>()

        override fun trackDevices(): Flow<List<DeviceDescriptor>> = emptyFlow()

        override suspend fun listProcesses(serial: String): List<ProcessDescriptor> = emptyList()

        override fun streamLogs(
            serial: String,
            minimumLevel: LogLevel,
            pid: Int?,
        ): Flow<LogEntry> = MutableSharedFlow<LogEntry>().also { streams.add(it) }

        override suspend fun restartAdb() = Unit

        override suspend fun clearDeviceLogs(serial: String) = Unit

        override fun close() = Unit

        suspend fun awaitStream(count: Int): MutableSharedFlow<LogEntry> {
            withTimeout(2_000) {
                while (streams.size < count) delay(10)
            }
            return streams[count - 1]
        }
    }
}
