package me.lidan.logcatui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.adblib.AdbDeviceServices
import com.android.adblib.AdbHostServices
import com.android.adblib.AdbSession
import com.android.adblib.AdbSessionHost
import com.android.adblib.AdbServerChannelProvider
import com.android.adblib.DeviceInfo
import com.android.adblib.DeviceSelector
import com.android.adblib.ShellCommandOutputElement
import com.android.adblib.shellAsLines
import com.android.adblib.shellAsText
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val DEVICE_TRACK_RETRY_MS = 1_500L
private const val MAX_LOG_ENTRIES = 50_000
private const val PROCESS_MONITOR_INTERVAL_MS = 1_500L
private val LOGCAT_PATTERN =
    Regex(
        """^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+(\d+)\s+(\d+)\s+([VDIWEAF])\s+(.+?):\s(.*)$"""
    )
private val PROCESS_COLUMN_SPLIT = Regex("""\s+""")

data class DeviceDescriptor(
    val serial: String,
    val model: String,
    val state: String,
    val transportId: String?,
) {
    val label: String
        get() = "$model ($serial)"
}

data class ProcessDescriptor(
    val pid: Int,
    val name: String,
) {
    val label: String
        get() = "$name ($pid)"
}

enum class LogLevel(val token: Char, val label: String) {
    Verbose('V', "Verbose"),
    Debug('D', "Debug"),
    Info('I', "Info"),
    Warn('W', "Warn"),
    Error('E', "Error"),
    Assert('F', "Fatal");

    companion object {
        val dropdownValues: List<LogLevel> = entries

        fun fromToken(token: Char): LogLevel =
            entries.firstOrNull { it.token == token } ?: Verbose
    }
}

data class LogEntry(
    val id: Long,
    val timestamp: String,
    val pid: Int,
    val tid: Int,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val rawLine: String,
    val source: String = "stdout",
    val receivedAt: Instant = Instant.now(),
)

private val syntheticLogId = AtomicLong(1_000_000_000L)

class AdbLogcatService : AutoCloseable {
    private val sessionMutex = Mutex()
    private var host = AdbSessionHost()
    private var session = createSession(host)
    private val adbExecutable = AdbExecutableLocator.locate()

    fun trackDevices(): Flow<List<DeviceDescriptor>> =
        flow {
            while (currentCoroutineContext().isActive) {
                try {
                    ensureAdbServer()
                    val currentSession = snapshotSession()
                    emit(currentSession.hostServices.devices(AdbHostServices.DeviceInfoFormat.LONG_FORMAT).map(::toDeviceDescriptor))
                    currentSession.hostServices
                        .trackDevices(AdbHostServices.DeviceInfoFormat.LONG_FORMAT)
                        .map { list -> list.map(::toDeviceDescriptor) }
                        .collect { emit(it) }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    emit(emptyList())
                    delay(DEVICE_TRACK_RETRY_MS)
                }
            }
        }

    suspend fun listProcesses(serial: String): List<ProcessDescriptor> {
        ensureAdbServer()
        val selector = DeviceSelector.fromSerialNumber(serial)
        val currentSession = snapshotSession()
        val output =
            currentSession.deviceServices.runProcessCommand(
                selector = selector,
                command = "ps -A -o PID,NAME",
                fallbackCommand = "ps -A",
            )
        return parseProcesses(output)
    }

    fun streamLogs(
        serial: String,
        minimumLevel: LogLevel,
        pid: Int?,
    ): Flow<LogEntry> = flow {
        ensureAdbServer()
        val selector = DeviceSelector.fromSerialNumber(serial)
        val command = buildLogcatCommand(minimumLevel = minimumLevel, pid = pid)
        snapshotSession().deviceServices.shellAsLines(selector, command).collect { element ->
            when (element) {
                is ShellCommandOutputElement.StdoutLine -> {
                    parseLogcatLine(element.contents)?.let { emit(it) }
                }

                is ShellCommandOutputElement.StderrLine -> {
                    emit(
                        LogEntry(
                            id = logId.incrementAndGet(),
                            timestamp = "--",
                            pid = pid ?: -1,
                            tid = -1,
                            level = LogLevel.Error,
                            tag = "stderr",
                            message = element.contents,
                            rawLine = element.contents,
                            source = "stderr",
                        )
                    )
                }

                is ShellCommandOutputElement.ExitCode -> Unit
            }
        }
    }

    suspend fun restartAdb() {
        val adb = adbExecutable ?: throw IOException("Unable to locate adb. Set ANDROID_SDK_ROOT or add adb to PATH.")
        runAdbCommand(adb, "kill-server")
        runAdbCommand(adb, "start-server")
        sessionMutex.withLock {
            session.close()
            host.close()
            host = AdbSessionHost()
            session = createSession(host)
        }
    }

    suspend fun clearDeviceLogs(serial: String) {
        ensureAdbServer()
        snapshotSession().deviceServices.shellAsText(
            DeviceSelector.fromSerialNumber(serial),
            "logcat -c",
        )
    }

    private suspend fun ensureAdbServer() {
        adbExecutable?.let { runAdbCommand(it, "start-server") }
    }

    private suspend fun snapshotSession(): AdbSession = sessionMutex.withLock { session }

    private suspend fun runAdbCommand(adbPath: Path, vararg args: String) {
        withContext(Dispatchers.IO) {
            runInterruptible {
                val process = ProcessBuilder(listOf(adbPath.toString()) + args).start()
                val exitCode = process.waitFor()
                if (exitCode != 0) {
                    val stderr = process.errorStream.bufferedReader().readText().trim()
                    throw IOException(stderr.ifEmpty { "adb ${args.joinToString(" ")} failed with exit code $exitCode." })
                }
            }
        }
    }

    private fun createSession(host: AdbSessionHost): AdbSession =
        AdbSession.create(
            host = host,
            channelProvider = AdbServerChannelProvider.createOpenLocalHost(host),
        )

    override fun close() {
        session.close()
        host.close()
    }

    private companion object {
        val logId = AtomicLong()

        fun toDeviceDescriptor(info: DeviceInfo): DeviceDescriptor {
            val model = info.model ?: info.product ?: info.device ?: info.serialNumber
            return DeviceDescriptor(
                serial = info.serialNumber,
                model = model,
                state = info.deviceStateString,
                transportId = info.transportId,
            )
        }

        fun buildLogcatCommand(minimumLevel: LogLevel, pid: Int?): String {
            val pidArgument = pid?.let { "--pid=$it " } ?: ""
            return "logcat -v threadtime ${pidArgument}*:${minimumLevel.token}"
        }

        fun parseLogcatLine(line: String): LogEntry? {
            val match = LOGCAT_PATTERN.matchEntire(line) ?: return null
            val (timestamp, pid, tid, levelToken, tag, message) = match.destructured
            return LogEntry(
                id = logId.incrementAndGet(),
                timestamp = timestamp,
                pid = pid.toIntOrNull() ?: -1,
                tid = tid.toIntOrNull() ?: -1,
                level = LogLevel.fromToken(levelToken.first()),
                tag = tag.trim(),
                message = message,
                rawLine = line,
            )
        }

        fun parseProcesses(output: String): List<ProcessDescriptor> {
            val lines = output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            if (lines.isEmpty()) {
                return emptyList()
            }

            val header = PROCESS_COLUMN_SPLIT.split(lines.first())
            val pidIndex = header.indexOf("PID")
            val nameIndex =
                header.indexOfFirst { it == "NAME" || it == "CMD" || it == "COMMAND" }.takeIf { it >= 0 }
                    ?: header.lastIndex

            return lines.drop(1)
                .mapNotNull { line ->
                    val columns = PROCESS_COLUMN_SPLIT.split(line, limit = nameIndex + 1)
                    if (columns.size <= nameIndex || pidIndex !in columns.indices) {
                        return@mapNotNull null
                    }
                    val pid = columns[pidIndex].toIntOrNull() ?: return@mapNotNull null
                    val name = columns.drop(nameIndex).joinToString(" ").ifBlank { return@mapNotNull null }
                    ProcessDescriptor(pid = pid, name = name)
                }
                .distinctBy { it.pid }
                .sortedWith(compareBy(ProcessDescriptor::name, ProcessDescriptor::pid))
        }
    }
}

private suspend fun AdbDeviceServices.runProcessCommand(
    selector: DeviceSelector,
    command: String,
    fallbackCommand: String,
): String {
    return runCatching { shellAsText(selector, command).stdout }
        .recoverCatching { shellAsText(selector, fallbackCommand).stdout }
        .getOrThrow()
}

private object AdbExecutableLocator {
    fun locate(): Path? {
        val executable = if (System.getProperty("os.name").startsWith("Windows")) "adb.exe" else "adb"
        val candidates =
            buildList {
                addSystemPathCandidates(executable)
                addSdkCandidates(executable)
            }

        return candidates.firstOrNull { it.toFile().isFile }
    }

    private fun MutableList<Path>.addSdkCandidates(executable: String) {
        listOf("ANDROID_SDK_ROOT", "ANDROID_HOME")
            .mapNotNull { System.getenv(it) }
            .map { Paths.get(it).resolve("platform-tools").resolve(executable) }
            .forEach(::add)
    }

    private fun MutableList<Path>.addSystemPathCandidates(executable: String) {
        val path = System.getenv("PATH") ?: return
        path.split(File.pathSeparatorChar)
            .asSequence()
            .filter { it.isNotBlank() }
            .map { Paths.get(it).resolve(executable) }
            .forEach(::add)
    }
}

class LogcatController(
    private val service: AdbLogcatService,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var deviceTrackingJob: Job? = null
    private var processRefreshJob: Job? = null
    private var processMonitorJob: Job? = null
    private var logStreamingJob: Job? = null

    var devices by mutableStateOf<List<DeviceDescriptor>>(emptyList())
        private set
    var processes by mutableStateOf<List<ProcessDescriptor>>(emptyList())
        private set
    var selectedDeviceSerial by mutableStateOf<String?>(null)
        private set
    var selectedProcessName by mutableStateOf<String?>(null)
        private set
    var selectedProcessPid by mutableStateOf<Int?>(null)
        private set
    var selectedLevel by mutableStateOf(LogLevel.Verbose)
        private set
    var searchQuery by mutableStateOf("")
    var filterHistory by mutableStateOf<List<String>>(emptyList())
        private set
    var regexEnabled by mutableStateOf(false)
    var autoScrollToBottom by mutableStateOf(true)
    var detailExpanded by mutableStateOf(true)
    var statusMessage by mutableStateOf("Connecting to ADB server...")
        private set
    var isStreaming by mutableStateOf(false)
        private set
    var selectedLogId by mutableStateOf<Long?>(null)
        private set
    val logs = mutableStateListOf<LogEntry>()

    init {
        observeDevices()
    }

    fun selectDevice(serial: String?) {
        if (selectedDeviceSerial == serial) {
            return
        }
        selectedDeviceSerial = serial
        selectedProcessName = null
        selectedProcessPid = null
        processes = emptyList()
        clearLogs()
        if (serial == null) {
            stopStreaming()
            stopProcessMonitoring()
            statusMessage = "No device selected."
            return
        }
        refreshProcesses()
        startProcessMonitoring()
        restartStreaming()
    }

    fun selectProcess(process: ProcessDescriptor?) {
        if (selectedProcessName == process?.name && selectedProcessPid == process?.pid) {
            return
        }
        selectedProcessName = process?.name
        selectedProcessPid = process?.pid
        clearLogs()
        startProcessMonitoring()
        restartStreaming()
    }

    fun selectLevel(level: LogLevel) {
        if (selectedLevel == level) {
            return
        }
        selectedLevel = level
        clearLogs()
        restartStreaming()
    }

    fun saveCurrentFilterToHistory() {
        saveFilterToHistory(searchQuery)
    }

    fun applyFilterFromHistory(filter: String) {
        searchQuery = filter
        saveFilterToHistory(filter)
    }

    fun clearLogs() {
        logs.clear()
        selectedLogId = null
    }

    fun clearDeviceLogs() {
        val serial = selectedDeviceSerial ?: return
        clearLogs()
        scope.launch {
            runAction("Clearing device log buffers...") { service.clearDeviceLogs(serial) }
            restartStreaming()
        }
    }

    fun restartAdb() {
        scope.launch {
            stopStreaming()
            runAction("Restarting ADB server...") {
                service.restartAdb()
            }
            observeDevices()
        }
    }

    fun selectLog(entry: LogEntry?) {
        selectedLogId = entry?.id
        if (entry != null) {
            detailExpanded = true
        }
    }

    fun refreshProcesses() {
        val serial = selectedDeviceSerial ?: return
        processRefreshJob?.cancel()
        processRefreshJob =
            scope.launch {
                runAction("Loading processes for $serial...") {
                    processes = service.listProcesses(serial)
                }
            }
    }

    override fun close() {
        stopStreaming()
        deviceTrackingJob?.cancel()
        processRefreshJob?.cancel()
        processMonitorJob?.cancel()
        scope.cancel()
        service.close()
    }

    private fun observeDevices() {
        deviceTrackingJob?.cancel()
        deviceTrackingJob =
            scope.launch {
                service.trackDevices().collect { deviceList ->
                    devices = deviceList
                    val currentSerial = selectedDeviceSerial
                    val currentExists = deviceList.any { it.serial == currentSerial }
                    if (!currentExists) {
                        selectedDeviceSerial = deviceList.firstOrNull()?.serial
                        selectedProcessName = null
                        selectedProcessPid = null
                        processes = emptyList()
                        clearLogs()
                    }

                    when {
                        deviceList.isEmpty() -> {
                            stopStreaming()
                            stopProcessMonitoring()
                            processes = emptyList()
                            statusMessage = "No Android devices connected."
                        }

                        selectedDeviceSerial != null -> {
                            refreshProcesses()
                            startProcessMonitoring()
                            restartStreaming()
                        }
                    }
                }
            }
    }

    private fun restartStreaming() {
        stopStreaming()
        val serial = selectedDeviceSerial ?: return
        val trackedProcessName = selectedProcessName
        val trackedPid = selectedProcessPid
        if (trackedProcessName != null && trackedPid == null) {
            statusMessage = "Waiting for process \"$trackedProcessName\" to start..."
            return
        }
        logStreamingJob =
            scope.launch {
                isStreaming = true
                statusMessage = "Ready."
                try {
                    service.streamLogs(serial, selectedLevel, trackedPid).collect(::appendLog)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    statusMessage = error.message ?: "Logcat stream stopped unexpectedly."
                } finally {
                    isStreaming = false
                }
            }
    }

    private fun stopStreaming() {
        logStreamingJob?.cancel()
        logStreamingJob = null
        isStreaming = false
    }

    private fun startProcessMonitoring() {
        processMonitorJob?.cancel()
        val serial = selectedDeviceSerial ?: return
        val trackedProcessName = selectedProcessName ?: return
        processMonitorJob =
            scope.launch {
                while (isActive) {
                    runCatching { service.listProcesses(serial) }
                        .onSuccess { latestProcesses ->
                            processes = latestProcesses
                            val newPid = latestProcesses.firstOrNull { it.name == trackedProcessName }?.pid
                            handleTrackedProcessTransition(trackedProcessName, newPid)
                        }
                    delay(PROCESS_MONITOR_INTERVAL_MS)
                }
            }
    }

    private fun stopProcessMonitoring() {
        processMonitorJob?.cancel()
        processMonitorJob = null
    }

    private fun handleTrackedProcessTransition(processName: String, newPid: Int?) {
        if (selectedProcessName != processName || selectedProcessPid == newPid) {
            return
        }
        val previousPid = selectedProcessPid
        if (previousPid != null) {
            appendSyntheticLog("----- Process stopped: pid: $previousPid ------")
        }
        selectedProcessPid = newPid
        if (newPid != null) {
            appendSyntheticLog("----- Process started: pid: $newPid ------")
        }
        restartStreaming()
    }

    private fun appendSyntheticLog(message: String) {
        appendLog(
            LogEntry(
                id = syntheticLogId.incrementAndGet(),
                timestamp = "--",
                pid = selectedProcessPid ?: -1,
                tid = -1,
                level = LogLevel.Info,
                tag = "process-monitor",
                message = message,
                rawLine = message,
                source = "system",
            )
        )
    }

    private fun appendLog(entry: LogEntry) {
        logs += entry
        if (logs.size > MAX_LOG_ENTRIES) {
            val trimSize = logs.size - MAX_LOG_ENTRIES
            logs.subList(0, trimSize).clear()
        }
    }

    private fun saveFilterToHistory(filter: String) {
        val normalized = filter.trim()
        if (normalized.isEmpty()) {
            return
        }
        filterHistory = listOf(normalized) + filterHistory.filterNot { it == normalized }
        if (filterHistory.size > 15) {
            filterHistory = filterHistory.take(15)
        }
    }

    private suspend fun runAction(startMessage: String, action: suspend () -> Unit) {
        statusMessage = startMessage
        runCatching { action() }
            .onSuccess {
                statusMessage = "Ready."
            }
            .onFailure { error ->
                if (error is CancellationException) {
                    throw error
                }
                statusMessage = error.message ?: "Operation failed."
            }
    }
}
