package com.example.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.engine.DiscoveredConsole
import com.example.engine.FtpFileEntry
import com.example.engine.MemorySearchHit
import com.example.engine.MemoryValueType
import com.example.engine.PkgParser
import com.example.engine.PortProbeResult
import com.example.engine.Ps4DebugBridge
import com.example.engine.Ps4FtpClient
import com.example.engine.Ps4MemoryRegion
import com.example.engine.Ps4MemorySnapshot
import com.example.engine.Ps4ProcessInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class QueuedPayload(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val uri: Uri,
    val sizeBytes: Long
)

data class CrashCapture(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: String,
    val triggerLine: String,
    val contextLines: List<String>
)

data class MemoryWatchItem(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val address: Long,
    val valueType: MemoryValueType,
    val targetValue: String,
    val liveValue: String = "-",
    val isFrozen: Boolean = false
)

data class ConsoleDebugState(
    val host: String = "192.168.1.50",
    val ftpPort: Int = 2121,
    val binLoaderPort: Int = 9090,
    val klogPort: Int = 3232,
    val debugPort: Int = 744,
    val username: String = "anonymous",
    val password: String = "",
    val isConnected: Boolean = false,
    val isBusy: Boolean = false,
    val currentPath: String = "/",
    val entries: List<FtpFileEntry> = emptyList(),
    val ftpTranscript: List<String> = emptyList(),
    val transferBytes: Long = 0L,
    val lastThroughputKbps: Long = 0L,
    val telemetrySamples: List<Long> = emptyList(),
    // LAN Radar
    val isScanningLan: Boolean = false,
    val lanScanProgress: Int = 0,
    val discoveredConsoles: List<DiscoveredConsole> = emptyList(),
    // Payload Macro Queue
    val payloadQueue: List<QueuedPayload> = emptyList(),
    // Klog + Crash Capture
    val isKlogStreaming: Boolean = false,
    val klogRegexMode: Boolean = false,
    val klogLines: List<String> = emptyList(),
    val crashCaptures: List<CrashCapture> = emptyList(),
    // Processes & Memory Hacker
    val processes: List<Ps4ProcessInfo> = emptyList(),
    val selectedPid: Int? = null,
    val memorySnapshot: Ps4MemorySnapshot? = null,
    val portProbes: List<PortProbeResult> = emptyList(),
    val peekAddressHex: String = "400000",
    val peekLength: Int = 128,
    val peekHexDump: String = "",
    val lastPeekBytes: ByteArray = ByteArray(0),
    val lastPeekAddress: Long = 0L,
    val pokeValueType: MemoryValueType = MemoryValueType.HEX_BYTES,
    val pokeValueInput: String = "90 90 90 90",
    val scanValueType: MemoryValueType = MemoryValueType.INT32,
    val scanQuery: String = "",
    val isScanningMemory: Boolean = false,
    val scanHits: List<MemorySearchHit> = emptyList(),
    val watchlist: List<MemoryWatchItem> = emptyList(),
    val isFreezeLoopActive: Boolean = false,
    val statusMessage: String? = null
)

class ConsoleDebugViewModel : ViewModel() {
    private val ftpClient = Ps4FtpClient()
    private val _state = MutableStateFlow(ConsoleDebugState())
    val state: StateFlow<ConsoleDebugState> = _state.asStateFlow()

    private var klogJob: Job? = null
    private var freezeJob: Job? = null
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val crashRegex = Regex("(panic|segfault|segmentation fault|page fault|fatal|abort|exception)", RegexOption.IGNORE_CASE)

    fun updateConnectionFields(
        host: String = _state.value.host,
        ftpPort: Int = _state.value.ftpPort,
        binLoaderPort: Int = _state.value.binLoaderPort,
        klogPort: Int = _state.value.klogPort,
        debugPort: Int = _state.value.debugPort,
        username: String = _state.value.username,
        password: String = _state.value.password
    ) {
        _state.update {
            it.copy(
                host = host,
                ftpPort = ftpPort,
                binLoaderPort = binLoaderPort,
                klogPort = klogPort,
                debugPort = debugPort,
                username = username,
                password = password
            )
        }
    }

    fun clearMessage() {
        _state.update { it.copy(statusMessage = null) }
    }

    fun scanLanConsoles(onLog: (String, String, String, String) -> Unit) {
        if (_state.value.isScanningLan) return
        val st = _state.value
        val prefix = Ps4DebugBridge.detectLocalSubnetPrefix(st.host)
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isScanningLan = true,
                    lanScanProgress = 0,
                    discoveredConsoles = emptyList(),
                    statusMessage = "Scanning $prefix.1-254..."
                )
            }
            val found = Ps4DebugBridge.scanSubnetForConsoles(
                subnetPrefix = prefix,
                ports = listOf(st.ftpPort, st.binLoaderPort, st.klogPort, st.debugPort).distinct(),
                onHostScanned = { count ->
                    _state.update { it.copy(lanScanProgress = count) }
                }
            )
            _state.update {
                it.copy(
                    isScanningLan = false,
                    lanScanProgress = 254,
                    discoveredConsoles = found,
                    statusMessage = if (found.isEmpty()) "No PS4 ports open on $prefix.0/24" else "Found ${found.size} console(s)"
                )
            }
            onLog("FTP", "INFO", "LAN_SCAN", "Scanned $prefix.0/24 -> ${found.size} target(s)")
        }
    }

    fun selectDiscoveredConsole(console: DiscoveredConsole) {
        _state.update {
            it.copy(
                host = console.host,
                telemetrySamples = (it.telemetrySamples + console.latencyMs).takeLast(24),
                statusMessage = "Target set to ${console.host} (${console.latencyMs}ms)"
            )
        }
    }

    fun connectFtp(onLog: (String, String, String, String) -> Unit) {
        val st = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, statusMessage = null) }
            val startMs = System.currentTimeMillis()
            ftpClient.connect(st.host, st.ftpPort, st.username, st.password)
                .onSuccess { replies ->
                    val rtt = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                    val pwd = ftpClient.pwd().getOrDefault("/")
                    val list = ftpClient.listDirectory(pwd).getOrDefault(emptyList())
                    _state.update {
                        it.copy(
                            isBusy = false,
                            isConnected = true,
                            currentPath = pwd,
                            entries = list,
                            telemetrySamples = (it.telemetrySamples + rtt).takeLast(24),
                            ftpTranscript = (it.ftpTranscript + replies).takeLast(60),
                            statusMessage = "Connected to ${st.host}:${st.ftpPort} (${rtt}ms)"
                        )
                    }
                    onLog("FTP", "INFO", "FTP_CONNECTED", "${st.host}:${st.ftpPort}")
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(
                            isBusy = false,
                            isConnected = false,
                            ftpTranscript = (it.ftpTranscript + "ERR: ${err.localizedMessage}").takeLast(60),
                            statusMessage = "FTP connect failed: ${err.localizedMessage}"
                        )
                    }
                    onLog("FTP", "ERROR", "FTP_CONNECT_FAILED", err.localizedMessage ?: "Timeout")
                }
        }
    }

    fun disconnectFtp() {
        viewModelScope.launch(Dispatchers.IO) { ftpClient.disconnect() }
        _state.update { it.copy(isConnected = false, entries = emptyList(), statusMessage = "FTP disconnected") }
    }

    fun navigateDirectory(path: String) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            ftpClient.listDirectory(path)
                .onSuccess { list ->
                    _state.update {
                        it.copy(isBusy = false, currentPath = path, entries = list)
                    }
                }
                .onFailure { err ->
                    _state.update { it.copy(isBusy = false, statusMessage = err.localizedMessage) }
                }
        }
    }

    fun navigateUp() {
        val cur = _state.value.currentPath.trimEnd('/')
        val parent = if (cur.isEmpty() || !cur.contains('/')) "/" else cur.substringBeforeLast('/').ifEmpty { "/" }
        navigateDirectory(parent)
    }

    fun uploadLocalFile(context: Context, uri: Uri, onLog: (String, String, String, String) -> Unit) {
        val st = _state.value
        val fileName = PkgParser.resolveFileName(context, uri)
        val targetPath = if (st.currentPath.endsWith("/")) "${st.currentPath}$fileName" else "${st.currentPath}/$fileName"
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, transferBytes = 0L) }
            val startNs = System.nanoTime()
            val res = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { ins ->
                    ftpClient.uploadFile(targetPath, ins) { bytes ->
                        _state.update { it.copy(transferBytes = bytes) }
                    }
                } ?: Result.failure(IllegalStateException("Cannot read local file"))
            }
            res.onSuccess { total ->
                val elapsedSec = ((System.nanoTime() - startNs) / 1_000_000_000.0).coerceAtLeast(0.01)
                val kbps = ((total / 1024.0) / elapsedSec).toLong().coerceAtLeast(1L)
                navigateDirectory(st.currentPath)
                _state.update {
                    it.copy(
                        isBusy = false,
                        lastThroughputKbps = kbps,
                        telemetrySamples = (it.telemetrySamples + kbps).takeLast(24),
                        statusMessage = "Uploaded $fileName ($total B @ $kbps KB/s)"
                    )
                }
                onLog("FTP", "INFO", "FTP_UPLOAD", "$targetPath ($total bytes)")
            }.onFailure { err ->
                _state.update { it.copy(isBusy = false, statusMessage = "Upload failed: ${err.localizedMessage}") }
            }
        }
    }

    fun downloadRemoteFile(context: Context, entry: FtpFileEntry, destUri: Uri, onLog: (String, String, String, String) -> Unit) {
        val st = _state.value
        val remotePath = if (st.currentPath.endsWith("/")) "${st.currentPath}${entry.name}" else "${st.currentPath}/${entry.name}"
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, transferBytes = 0L) }
            val startNs = System.nanoTime()
            val res = withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(destUri)?.use { out ->
                    ftpClient.downloadFile(remotePath, out) { bytes ->
                        _state.update { it.copy(transferBytes = bytes) }
                    }
                } ?: Result.failure(IllegalStateException("Cannot write destination file"))
            }
            res.onSuccess { total ->
                val elapsedSec = ((System.nanoTime() - startNs) / 1_000_000_000.0).coerceAtLeast(0.01)
                val kbps = ((total / 1024.0) / elapsedSec).toLong().coerceAtLeast(1L)
                _state.update {
                    it.copy(
                        isBusy = false,
                        lastThroughputKbps = kbps,
                        telemetrySamples = (it.telemetrySamples + kbps).takeLast(24),
                        statusMessage = "Downloaded ${entry.name} ($total B @ $kbps KB/s)"
                    )
                }
                onLog("FTP", "INFO", "FTP_DOWNLOAD", "$remotePath ($total bytes)")
            }.onFailure { err ->
                _state.update { it.copy(isBusy = false, statusMessage = "Download failed: ${err.localizedMessage}") }
            }
        }
    }

    fun deleteRemoteFile(entry: FtpFileEntry, onLog: (String, String, String, String) -> Unit) {
        val st = _state.value
        val remotePath = if (st.currentPath.endsWith("/")) "${st.currentPath}${entry.name}" else "${st.currentPath}/${entry.name}"
        viewModelScope.launch {
            ftpClient.deleteFile(remotePath)
                .onSuccess {
                    navigateDirectory(st.currentPath)
                    onLog("FTP", "WARN", "FTP_DELETE", remotePath)
                }
                .onFailure { err -> _state.update { it.copy(statusMessage = err.localizedMessage) } }
        }
    }

    fun createRemoteDir(dirName: String) {
        val clean = dirName.trim()
        if (clean.isBlank()) return
        val st = _state.value
        val path = if (st.currentPath.endsWith("/")) "${st.currentPath}$clean" else "${st.currentPath}/$clean"
        viewModelScope.launch {
            ftpClient.makeDirectory(path)
                .onSuccess { navigateDirectory(st.currentPath) }
                .onFailure { err -> _state.update { it.copy(statusMessage = err.localizedMessage) } }
        }
    }

    fun sendBinPayload(context: Context, uri: Uri, onLog: (String, String, String, String) -> Unit) {
        val st = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            val bytesRes = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Cannot read payload .bin")
                }
            }
            bytesRes.onSuccess { payload ->
                Ps4DebugBridge.sendBinPayload(st.host, st.binLoaderPort, payload)
                    .onSuccess { sent ->
                        _state.update { it.copy(isBusy = false, statusMessage = "Payload sent ($sent bytes to :${st.binLoaderPort})") }
                        onLog("FTP", "INFO", "PAYLOAD_SENT", "${st.host}:${st.binLoaderPort} ($sent bytes)")
                    }
                    .onFailure { err ->
                        _state.update { it.copy(isBusy = false, statusMessage = "Payload error: ${err.localizedMessage}") }
                    }
            }.onFailure { err ->
                _state.update { it.copy(isBusy = false, statusMessage = err.localizedMessage) }
            }
        }
    }

    fun addPayloadToQueue(context: Context, uri: Uri) {
        val name = PkgParser.resolveFileName(context, uri)
        val size = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }.let {
                if (it == null || it < 0L) 0L else it
            }
        }.getOrDefault(0L)
        _state.update {
            it.copy(
                payloadQueue = it.payloadQueue + QueuedPayload(name = name, uri = uri, sizeBytes = size),
                statusMessage = "Queued payload: $name"
            )
        }
    }

    fun removeQueuedPayload(id: String) {
        _state.update { it.copy(payloadQueue = it.payloadQueue.filterNot { q -> q.id == id }) }
    }

    fun clearPayloadQueue() {
        _state.update { it.copy(payloadQueue = emptyList()) }
    }

    fun launchPayloadQueue(context: Context, onLog: (String, String, String, String) -> Unit) {
        val st = _state.value
        if (st.payloadQueue.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            var sentCount = 0
            for (item in st.payloadQueue) {
                val payload = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(item.uri)?.use { it.readBytes() } }.getOrNull()
                }
                if (payload == null) {
                    _state.update { it.copy(isBusy = false, statusMessage = "Failed reading ${item.name}") }
                    return@launch
                }
                val res = Ps4DebugBridge.sendBinPayload(st.host, st.binLoaderPort, payload)
                if (res.isFailure) {
                    _state.update {
                        it.copy(
                            isBusy = false,
                            statusMessage = "Queue stopped at ${item.name}: ${res.exceptionOrNull()?.localizedMessage}"
                        )
                    }
                    onLog("FTP", "ERROR", "PAYLOAD_QUEUE_FAIL", item.name)
                    return@launch
                }
                sentCount++
                delay(750L)
            }
            _state.update { it.copy(isBusy = false, statusMessage = "Executed $sentCount queued payload(s) on :${st.binLoaderPort}") }
            onLog("FTP", "INFO", "PAYLOAD_QUEUE_OK", "$sentCount payloads sent to ${st.host}")
        }
    }

    fun toggleKlogRegex() {
        _state.update { it.copy(klogRegexMode = !it.klogRegexMode) }
    }

    fun clearCrashCaptures() {
        _state.update { it.copy(crashCaptures = emptyList()) }
    }

    fun toggleKlogStream(onLog: (String, String, String, String) -> Unit) {
        if (_state.value.isKlogStreaming) {
            klogJob?.cancel()
            klogJob = null
            _state.update { it.copy(isKlogStreaming = false, statusMessage = "Klog capture stopped") }
            return
        }
        val st = _state.value
        _state.update {
            it.copy(
                isKlogStreaming = true,
                klogLines = (it.klogLines + "[${timeFmt.format(Date())}] Connecting Klog stream ${st.host}:${st.klogPort}...").takeLast(300)
            )
        }
        klogJob = viewModelScope.launch {
            Ps4DebugBridge.streamKlog(st.host, st.klogPort)
                .catch { err ->
                    _state.update {
                        it.copy(
                            isKlogStreaming = false,
                            klogLines = (it.klogLines + "[${timeFmt.format(Date())}] ERR: ${err.localizedMessage}").takeLast(300),
                            statusMessage = "Klog stream closed: ${err.localizedMessage}"
                        )
                    }
                    onLog("FTP", "ERROR", "KLOG_FAILED", err.localizedMessage ?: "Connection refused")
                }
                .collect { line ->
                    val ts = timeFmt.format(Date())
                    val formatted = "[$ts] $line"
                    val isCrash = crashRegex.containsMatchIn(line)
                    _state.update { prev ->
                        val nextLines = (prev.klogLines + formatted).takeLast(300)
                        val nextCrashes = if (isCrash) {
                            (prev.crashCaptures + CrashCapture(
                                timestamp = ts,
                                triggerLine = line.take(140),
                                contextLines = nextLines.takeLast(40)
                            )).takeLast(12)
                        } else prev.crashCaptures
                        prev.copy(
                            klogLines = nextLines,
                            crashCaptures = nextCrashes,
                            statusMessage = if (isCrash) "Crash signature captured in Klog!" else prev.statusMessage
                        )
                    }
                    if (isCrash) {
                        onLog("FTP", "ERROR", "KLOG_CRASH_CAPTURE", line.take(180))
                    }
                }
        }
    }

    fun clearKlog() {
        _state.update { it.copy(klogLines = emptyList()) }
    }

    fun probeConsolePorts() {
        val st = _state.value
        viewModelScope.launch {
            val probes = Ps4DebugBridge.probePorts(st.host, st.ftpPort, st.binLoaderPort, st.klogPort, st.debugPort)
            val avgReachable = probes.filter { it.reachable }.map { it.latencyMs }.average()
                .let { if (it.isNaN()) 0L else it.toLong().coerceAtLeast(1L) }
            _state.update {
                it.copy(
                    portProbes = probes,
                    telemetrySamples = if (avgReachable > 0L) (it.telemetrySamples + avgReachable).takeLast(24) else it.telemetrySamples
                )
            }
        }
    }

    fun refreshProcesses(onLog: (String, String, String, String) -> Unit) {
        val st = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            Ps4DebugBridge.fetchProcessList(st.host, st.debugPort)
                .onSuccess { list ->
                    _state.update {
                        it.copy(isBusy = false, processes = list, statusMessage = "Loaded ${list.size} PS4 processes")
                    }
                    onLog("FTP", "INFO", "PS4_PROC_LIST", "Fetched ${list.size} processes")
                }
                .onFailure { err ->
                    _state.update { it.copy(isBusy = false, statusMessage = "PS4Debug error (:${st.debugPort}): ${err.localizedMessage}") }
                }
        }
    }

    fun inspectProcessMemory(pid: Int) {
        val st = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, selectedPid = pid, scanHits = emptyList()) }
            Ps4DebugBridge.fetchProcessMemory(st.host, pid, st.debugPort)
                .onSuccess { mem ->
                    val defaultAddr = mem.topRegions.firstOrNull()?.startAddress?.toString(16)?.uppercase()
                        ?: st.peekAddressHex
                    _state.update {
                        it.copy(
                            isBusy = false,
                            memorySnapshot = mem,
                            peekAddressHex = defaultAddr
                        )
                    }
                }
                .onFailure { err ->
                    _state.update { it.copy(isBusy = false, statusMessage = "Memory read failed: ${err.localizedMessage}") }
                }
        }
    }

    fun updateMemoryEditorFields(
        peekAddressHex: String = _state.value.peekAddressHex,
        peekLength: Int = _state.value.peekLength,
        pokeValueType: MemoryValueType = _state.value.pokeValueType,
        pokeValueInput: String = _state.value.pokeValueInput,
        scanValueType: MemoryValueType = _state.value.scanValueType,
        scanQuery: String = _state.value.scanQuery
    ) {
        _state.update {
            it.copy(
                peekAddressHex = peekAddressHex,
                peekLength = peekLength.coerceIn(16, 4096),
                pokeValueType = pokeValueType,
                pokeValueInput = pokeValueInput,
                scanValueType = scanValueType,
                scanQuery = scanQuery
            )
        }
    }

    fun peekMemoryAt(addressHex: String = _state.value.peekAddressHex) {
        val st = _state.value
        val pid = st.selectedPid ?: run {
            _state.update { it.copy(statusMessage = "Select a process PID first") }
            return
        }
        val addr = addressHex.trim().removePrefix("0x").removePrefix("0X").toLongOrNull(16) ?: run {
            _state.update { it.copy(statusMessage = "Invalid hex address") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, peekAddressHex = addr.toString(16).uppercase()) }
            Ps4DebugBridge.readProcessMemory(st.host, pid, addr, st.peekLength, st.debugPort)
                .onSuccess { bytes ->
                    _state.update {
                        it.copy(
                            isBusy = false,
                            peekHexDump = PkgParser.formatHexDump(bytes, addr),
                            lastPeekBytes = bytes,
                            lastPeekAddress = addr
                        )
                    }
                }
                .onFailure { err ->
                    _state.update { it.copy(isBusy = false, statusMessage = "Peek failed: ${err.localizedMessage}") }
                }
        }
    }

    fun pokeMemoryAt(
        addressHex: String,
        type: MemoryValueType,
        valueInput: String,
        onLog: (String, String, String, String) -> Unit
    ) {
        val st = _state.value
        val pid = st.selectedPid ?: run {
            _state.update { it.copy(statusMessage = "Select a process PID first") }
            return
        }
        val addr = addressHex.trim().removePrefix("0x").removePrefix("0X").toLongOrNull(16) ?: run {
            _state.update { it.copy(statusMessage = "Invalid hex address") }
            return
        }
        val bytes = Ps4DebugBridge.encodeSearchNeedle(type, valueInput)
        if (bytes.isEmpty()) {
            _state.update { it.copy(statusMessage = "Invalid ${type.label} payload") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            Ps4DebugBridge.writeProcessMemory(st.host, pid, addr, bytes, st.debugPort)
                .onSuccess {
                    _state.update {
                        it.copy(
                            isBusy = false,
                            statusMessage = "Poked ${bytes.size}B @ 0x${addr.toString(16).uppercase()}"
                        )
                    }
                    onLog("FTP", "WARN", "PS4_MEM_POKE", "PID=$pid 0x${addr.toString(16)} (${bytes.size}B)")
                    peekMemoryAt(addr.toString(16))
                }
                .onFailure { err ->
                    _state.update { it.copy(isBusy = false, statusMessage = "Poke failed: ${err.localizedMessage}") }
                }
        }
    }

    fun scanMemory(onLog: (String, String, String, String) -> Unit) {
        val st = _state.value
        val pid = st.selectedPid ?: run {
            _state.update { it.copy(statusMessage = "Select a process PID first") }
            return
        }
        val regions = st.memorySnapshot?.topRegions.orEmpty()
        if (regions.isEmpty()) {
            _state.update { it.copy(statusMessage = "Load process memory maps first") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isScanningMemory = true, scanHits = emptyList()) }
            Ps4DebugBridge.scanProcessMemory(
                host = st.host,
                pid = pid,
                regions = regions,
                valueType = st.scanValueType,
                query = st.scanQuery,
                debugPort = st.debugPort
            ).onSuccess { hits ->
                _state.update {
                    it.copy(
                        isScanningMemory = false,
                        scanHits = hits,
                        statusMessage = "Memory scan found ${hits.size} match(es)"
                    )
                }
                onLog("FTP", "INFO", "PS4_MEM_SCAN", "PID=$pid (${st.scanValueType.label}) -> ${hits.size} hits")
            }.onFailure { err ->
                _state.update { it.copy(isScanningMemory = false, statusMessage = "Scan error: ${err.localizedMessage}") }
            }
        }
    }

    fun addWatchItem(addressHex: String, label: String, type: MemoryValueType, targetValue: String) {
        val addr = addressHex.trim().removePrefix("0x").removePrefix("0X").toLongOrNull(16) ?: return
        val item = MemoryWatchItem(
            label = label.ifBlank { "0x${addr.toString(16).uppercase()}" },
            address = addr,
            valueType = type,
            targetValue = targetValue.trim(),
            isFrozen = false
        )
        _state.update { it.copy(watchlist = it.watchlist + item, statusMessage = "Added watch @ 0x${addr.toString(16).uppercase()}") }
    }

    fun toggleWatchFreeze(id: String) {
        _state.update { st ->
            st.copy(watchlist = st.watchlist.map { w -> if (w.id == id) w.copy(isFrozen = !w.isFrozen) else w })
        }
    }

    fun removeWatchItem(id: String) {
        _state.update { st -> st.copy(watchlist = st.watchlist.filterNot { it.id == id }) }
    }

    fun toggleFreezeLoop() {
        if (_state.value.isFreezeLoopActive) {
            freezeJob?.cancel()
            freezeJob = null
            _state.update { it.copy(isFreezeLoopActive = false, statusMessage = "Watch/Freeze loop stopped") }
            return
        }
        if (_state.value.selectedPid == null) {
            _state.update { it.copy(statusMessage = "Select a process PID first") }
            return
        }
        _state.update { it.copy(isFreezeLoopActive = true, statusMessage = "Watch/Freeze loop active (500ms)") }
        freezeJob = viewModelScope.launch {
            while (isActive) {
                val st = _state.value
                val pid = st.selectedPid ?: break
                val updated = st.watchlist.map { item ->
                    if (item.isFrozen && item.targetValue.isNotBlank()) {
                        val bytes = Ps4DebugBridge.encodeSearchNeedle(item.valueType, item.targetValue)
                        if (bytes.isNotEmpty()) {
                            Ps4DebugBridge.writeProcessMemory(st.host, pid, item.address, bytes, st.debugPort)
                        }
                    }
                    val readLen = if (item.valueType.byteSize > 0) item.valueType.byteSize else 16
                    val live = Ps4DebugBridge.readProcessMemory(st.host, pid, item.address, readLen, st.debugPort)
                        .map { Ps4DebugBridge.formatMemoryValue(item.valueType, it) }
                        .getOrDefault(item.liveValue)
                    item.copy(liveValue = live)
                }
                _state.update { it.copy(watchlist = updated) }
                delay(500L)
            }
            _state.update { it.copy(isFreezeLoopActive = false) }
        }
    }

    fun dumpRegionToUri(context: Context, region: Ps4MemoryRegion, destUri: Uri, onLog: (String, String, String, String) -> Unit) {
        val st = _state.value
        val pid = st.selectedPid ?: return
        val len = (region.endAddress - region.startAddress).coerceIn(1L, 32L * 1024L * 1024L)
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, transferBytes = 0L) }
            val res = withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(destUri)?.use { out ->
                    Ps4DebugBridge.dumpMemoryRegion(st.host, pid, region.startAddress, len, out, st.debugPort) { written ->
                        _state.update { it.copy(transferBytes = written) }
                    }
                } ?: Result.failure(IllegalStateException("Cannot open destination URI"))
            }
            res.onSuccess { written ->
                _state.update { it.copy(isBusy = false, statusMessage = "Dumped ${region.name} ($written B)") }
                onLog("FTP", "INFO", "PS4_MEM_DUMP", "PID=$pid ${region.name} ($written bytes)")
            }.onFailure { err ->
                _state.update { it.copy(isBusy = false, statusMessage = "Dump failed: ${err.localizedMessage}") }
            }
        }
    }

    fun sendProcessSignal(pid: Int, signalCode: Int, label: String, onLog: (String, String, String, String) -> Unit) {
        val st = _state.value
        viewModelScope.launch {
            Ps4DebugBridge.sendProcessCommand(st.host, st.debugPort, pid, signalCode)
                .onSuccess { msg ->
                    _state.update { it.copy(statusMessage = "$label: $msg") }
                    onLog("FTP", "WARN", "PS4_PROC_$label", "PID=$pid signal=$signalCode")
                }
                .onFailure { err ->
                    _state.update { it.copy(statusMessage = "Signal failed: ${err.localizedMessage}") }
                }
        }
    }

    suspend fun executeRawFtp(command: String): Result<String> {
        return ftpClient.executeRaw(command)
    }

    override fun onCleared() {
        super.onCleared()
        klogJob?.cancel()
        freezeJob?.cancel()
        ftpClient.disconnect()
    }
}
