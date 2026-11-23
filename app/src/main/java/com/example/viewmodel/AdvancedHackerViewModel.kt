package com.example.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.engine.AdvancedHackerEngine
import com.example.engine.AobRegionScope
import com.example.engine.DisassembledInsn
import com.example.engine.ElfReport
import com.example.engine.EntropyReport
import com.example.engine.ExtractedString
import com.example.engine.MemorySearchHit
import com.example.engine.MemoryValueType
import com.example.engine.NextScanMode
import com.example.engine.PatchBackup
import com.example.engine.PkgParser
import com.example.engine.PointerChainResult
import com.example.engine.PreviousScanEntry
import com.example.engine.Ps4DebugBridge
import com.example.engine.ServiceBannerResult
import com.example.engine.X86Disassembler
import com.example.engine.XorCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AdvancedHackerState(
    val activeToolIndex: Int = 0, // 0=Scan/AoB, 1=x86 Disasm, 2=Ptr/Cheat, 3=Forensics/ELF, 4=Recon/FTP
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    // Multi-pass Cheat Engine Scan
    val multiValueType: MemoryValueType = MemoryValueType.INT32,
    val multiQuery: String = "",
    val nextScanMode: NextScanMode = NextScanMode.DECREASED,
    val passCount: Int = 0,
    val narrowHits: List<PreviousScanEntry> = emptyList(),
    // AoB Wildcard Signature Scan
    val aobPattern: String = "48 8B 05 ?? ?? ?? ??",
    val aobScope: AobRegionScope = AobRegionScope.EXEC,
    val aobHits: List<MemorySearchHit> = emptyList(),
    // x86-64 Disassembler & Live Patcher
    val disasmAddressHex: String = "400000",
    val disasmByteLen: Int = 128,
    val disasmSourceLabel: String = "PS4 RAM",
    val disasmInsns: List<DisassembledInsn> = emptyList(),
    val patchUndoStack: List<PatchBackup> = emptyList(),
    // Pointer Chain Resolver
    val pointerBaseHex: String = "400000",
    val pointerOffsetsInput: String = "+0x18, +0x240, +0x10",
    val pointerValueType: MemoryValueType = MemoryValueType.INT32,
    val pointerResult: PointerChainResult? = null,
    // Forensics / ELF64 / Entropy / Strings / XOR
    val analyzedSourceName: String = "",
    val analyzedBaseAddress: Long = 0L,
    val analyzedBytes: ByteArray = ByteArray(0),
    val entropyReport: EntropyReport? = null,
    val elfReport: ElfReport? = null,
    val extractedStrings: List<ExtractedString> = emptyList(),
    val xorKeyInput: String = "AA BB",
    val xorBruteCandidates: List<XorCandidate> = emptyList(),
    val xorDecodedPreview: String = "",
    // Network Service Banner Recon & Raw FTP Terminal
    val bannerPortsInput: String = "21, 22, 80, 443, 744, 2121, 3232, 9020, 9021, 9090, 9999, 12800",
    val bannerResults: List<ServiceBannerResult> = emptyList(),
    val isProbingBanners: Boolean = false,
    val ftpCmdInput: String = "FEAT",
    val ftpTerminalLines: List<String> = emptyList(),
    // GoldHEN Cheat Builder
    val cheatGameTitle: String = "Game Mod",
    val cheatTitleId: String = "CUSA00419",
    val cheatVersion: String = "01.00",
    val cheatModName: String = "Infinite / God Patch",
    val cheatAddressHex: String = "400000",
    val cheatOnHex: String = "90 90 90 90",
    val cheatOffHex: String = "89 48 10 90",
    val cheatUseJson: Boolean = true
) {
    fun buildCheatPreview(): String {
        val addr = cheatAddressHex.trim().removePrefix("0x").removePrefix("0X").toLongOrNull(16) ?: 0x400000L
        return if (cheatUseJson) {
            AdvancedHackerEngine.generateGoldHenJsonCheat(
                gameTitle = cheatGameTitle,
                titleId = cheatTitleId,
                gameVersion = cheatVersion,
                modName = cheatModName,
                address = addr,
                onHexBytes = cheatOnHex,
                offHexBytes = cheatOffHex
            )
        } else {
            AdvancedHackerEngine.generateShnCheatXml(
                gameTitle = cheatGameTitle,
                titleId = cheatTitleId,
                gameVersion = cheatVersion,
                modName = cheatModName,
                address = addr,
                onHexBytes = cheatOnHex,
                offHexBytes = cheatOffHex
            )
        }
    }
}

class AdvancedHackerViewModel : ViewModel() {
    private val _state = MutableStateFlow(AdvancedHackerState())
    val state: StateFlow<AdvancedHackerState> = _state.asStateFlow()
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun selectTool(index: Int) {
        _state.update { it.copy(activeToolIndex = index.coerceIn(0, 4)) }
    }

    fun updateScanFields(
        valueType: MemoryValueType = _state.value.multiValueType,
        query: String = _state.value.multiQuery,
        nextMode: NextScanMode = _state.value.nextScanMode,
        aobPattern: String = _state.value.aobPattern,
        aobScope: AobRegionScope = _state.value.aobScope
    ) {
        _state.update {
            it.copy(
                multiValueType = valueType,
                multiQuery = query,
                nextScanMode = nextMode,
                aobPattern = aobPattern,
                aobScope = aobScope
            )
        }
    }

    fun startInitialScan(
        consoleState: ConsoleDebugState,
        onLog: (String, String, String, String) -> Unit
    ) {
        val pid = consoleState.selectedPid ?: run {
            _state.update { it.copy(statusMessage = "Select a PS4 PID in Processes & RAM first") }
            return
        }
        val regions = consoleState.memorySnapshot?.topRegions.orEmpty()
        if (regions.isEmpty()) {
            _state.update { it.copy(statusMessage = "Inspect process memory maps first") }
            return
        }
        val st = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, narrowHits = emptyList(), passCount = 0) }
            Ps4DebugBridge.scanProcessMemory(
                host = consoleState.host,
                pid = pid,
                regions = regions,
                valueType = st.multiValueType,
                query = st.multiQuery,
                debugPort = consoleState.debugPort,
                maxHits = 80
            ).onSuccess { hits ->
                val needle = Ps4DebugBridge.encodeSearchNeedle(st.multiValueType, st.multiQuery)
                val entries = hits.map { h ->
                    PreviousScanEntry(h.address, h.regionName, needle)
                }
                _state.update {
                    it.copy(
                        isBusy = false,
                        passCount = 1,
                        narrowHits = entries,
                        statusMessage = "Pass #1: ${entries.size} addresses cached for narrowing"
                    )
                }
                onLog("HACK", "INFO", "MEM_PASS_1", "PID=$pid hits=${entries.size}")
            }.onFailure { err ->
                _state.update { it.copy(isBusy = false, statusMessage = "Initial scan error: ${err.localizedMessage}") }
            }
        }
    }

    fun runNextScan(
        consoleState: ConsoleDebugState,
        onLog: (String, String, String, String) -> Unit
    ) {
        val pid = consoleState.selectedPid ?: run {
            _state.update { it.copy(statusMessage = "Select a PS4 PID first") }
            return
        }
        val st = _state.value
        if (st.narrowHits.isEmpty()) {
            _state.update { it.copy(statusMessage = "Run First Scan before narrowing") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            Ps4DebugBridge.narrowMemoryHits(
                host = consoleState.host,
                pid = pid,
                previousHits = st.narrowHits,
                valueType = st.multiValueType,
                mode = st.nextScanMode,
                query = st.multiQuery,
                debugPort = consoleState.debugPort
            ).onSuccess { kept ->
                val nextPass = st.passCount + 1
                _state.update {
                    it.copy(
                        isBusy = false,
                        passCount = nextPass,
                        narrowHits = kept,
                        statusMessage = "Pass #$nextPass (${st.nextScanMode.label}): ${kept.size} remaining"
                    )
                }
                onLog("HACK", "INFO", "MEM_NEXT_SCAN", "Pass=$nextPass mode=${st.nextScanMode.name} kept=${kept.size}")
            }.onFailure { err ->
                _state.update { it.copy(isBusy = false, statusMessage = "Next scan error: ${err.localizedMessage}") }
            }
        }
    }

    fun resetMultiPassScan() {
        _state.update { it.copy(passCount = 0, narrowHits = emptyList(), statusMessage = "Multi-pass scan cache cleared") }
    }

    fun runAobScan(
        consoleState: ConsoleDebugState,
        onLog: (String, String, String, String) -> Unit
    ) {
        val pid = consoleState.selectedPid ?: run {
            _state.update { it.copy(statusMessage = "Select a PS4 PID first") }
            return
        }
        val regions = consoleState.memorySnapshot?.topRegions.orEmpty()
        if (regions.isEmpty()) {
            _state.update { it.copy(statusMessage = "Inspect process memory maps first") }
            return
        }
        val st = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, aobHits = emptyList()) }
            Ps4DebugBridge.scanProcessAob(
                host = consoleState.host,
                pid = pid,
                regions = regions,
                pattern = st.aobPattern,
                scope = st.aobScope,
                debugPort = consoleState.debugPort
            ).onSuccess { hits ->
                _state.update {
                    it.copy(
                        isBusy = false,
                        aobHits = hits,
                        statusMessage = "AoB signature scan: ${hits.size} match(es)"
                    )
                }
                onLog("HACK", "INFO", "AOB_SCAN", "PID=$pid hits=${hits.size} pattern=${st.aobPattern}")
            }.onFailure { err ->
                _state.update { it.copy(isBusy = false, statusMessage = "AoB scan error: ${err.localizedMessage}") }
            }
        }
    }

    fun updateDisasmFields(
        addressHex: String = _state.value.disasmAddressHex,
        byteLen: Int = _state.value.disasmByteLen
    ) {
        _state.update {
            it.copy(
                disasmAddressHex = addressHex,
                disasmByteLen = byteLen.coerceIn(32, 1024)
            )
        }
    }

    fun disassembleRemoteRam(
        consoleState: ConsoleDebugState,
        addressHexOverride: String? = null,
        onLog: (String, String, String, String) -> Unit
    ) {
        val pid = consoleState.selectedPid ?: run {
            _state.update { it.copy(statusMessage = "Select a PS4 PID first") }
            return
        }
        val hexStr = (addressHexOverride ?: _state.value.disasmAddressHex).trim().removePrefix("0x").removePrefix("0X")
        val addr = hexStr.toLongOrNull(16) ?: run {
            _state.update { it.copy(statusMessage = "Invalid hex address") }
            return
        }
        val len = _state.value.disasmByteLen
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, disasmAddressHex = addr.toString(16).uppercase()) }
            Ps4DebugBridge.readProcessMemory(consoleState.host, pid, addr, len, consoleState.debugPort)
                .onSuccess { bytes ->
                    val insns = withContext(Dispatchers.Default) {
                        X86Disassembler.disassemble(bytes, addr)
                    }
                    _state.update {
                        it.copy(
                            isBusy = false,
                            disasmSourceLabel = "PS4 PID $pid @ 0x${addr.toString(16).uppercase()}",
                            disasmInsns = insns,
                            statusMessage = "Disassembled ${insns.size} x86-64 instructions (${bytes.size}B)"
                        )
                    }
                    onLog("HACK", "INFO", "X86_DISASM", "PID=$pid @ 0x${addr.toString(16).uppercase()} (${insns.size} insns)")
                }
                .onFailure { err ->
                    _state.update { it.copy(isBusy = false, statusMessage = "Disasm read failed: ${err.localizedMessage}") }
                }
        }
    }

    fun disassembleBuffer(label: String, baseAddr: Long, bytes: ByteArray) {
        if (bytes.isEmpty()) {
            _state.update { it.copy(statusMessage = "No bytes available to disassemble") }
            return
        }
        viewModelScope.launch {
            val insns = withContext(Dispatchers.Default) {
                X86Disassembler.disassemble(bytes.copyOfRange(0, bytes.size.coerceAtMost(1024)), baseAddr)
            }
            _state.update {
                it.copy(
                    activeToolIndex = 1,
                    disasmAddressHex = baseAddr.toString(16).uppercase(),
                    disasmSourceLabel = label,
                    disasmInsns = insns,
                    statusMessage = "Disassembled ${insns.size} instructions from $label"
                )
            }
        }
    }

    fun patchInstruction(
        consoleState: ConsoleDebugState,
        insn: DisassembledInsn,
        patchBytes: ByteArray,
        label: String,
        onLog: (String, String, String, String) -> Unit
    ) {
        val pid = consoleState.selectedPid ?: run {
            _state.update { it.copy(statusMessage = "Live patching requires a connected PS4 PID") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            val origToSave = Ps4DebugBridge.readProcessMemory(
                consoleState.host, pid, insn.address, patchBytes.size, consoleState.debugPort
            ).getOrElse { insn.bytes }

            Ps4DebugBridge.writeProcessMemory(consoleState.host, pid, insn.address, patchBytes, consoleState.debugPort)
                .onSuccess {
                    val onHex = patchBytes.joinToString(" ") { "%02X".format(it) }
                    val offHex = origToSave.joinToString(" ") { "%02X".format(it) }
                    val backup = PatchBackup(
                        address = insn.address,
                        originalBytes = origToSave,
                        description = "$label @ 0x${insn.address.toString(16).uppercase()} (${insn.mnemonic} ${insn.operands})"
                    )
                    _state.update {
                        it.copy(
                            isBusy = false,
                            patchUndoStack = (it.patchUndoStack + backup).takeLast(20),
                            cheatAddressHex = insn.address.toString(16).uppercase(),
                            cheatOnHex = onHex,
                            cheatOffHex = offHex,
                            cheatModName = label,
                            statusMessage = "Patched 0x${insn.address.toString(16).uppercase()} [$onHex]"
                        )
                    }
                    onLog("HACK", "WARN", "X86_PATCH", "PID=$pid 0x${insn.address.toString(16)} $offHex -> $onHex")
                    disassembleRemoteRam(consoleState, _state.value.disasmAddressHex, onLog)
                }
                .onFailure { err ->
                    _state.update { it.copy(isBusy = false, statusMessage = "Patch write failed: ${err.localizedMessage}") }
                }
        }
    }

    fun undoLastPatch(
        consoleState: ConsoleDebugState,
        onLog: (String, String, String, String) -> Unit
    ) {
        val pid = consoleState.selectedPid ?: return
        val last = _state.value.patchUndoStack.lastOrNull() ?: return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            Ps4DebugBridge.writeProcessMemory(consoleState.host, pid, last.address, last.originalBytes, consoleState.debugPort)
                .onSuccess {
                    _state.update {
                        it.copy(
                            isBusy = false,
                            patchUndoStack = it.patchUndoStack.dropLast(1),
                            statusMessage = "Reverted patch @ 0x${last.address.toString(16).uppercase()}"
                        )
                    }
                    onLog("HACK", "INFO", "X86_REVERT", "PID=$pid reverted 0x${last.address.toString(16)}")
                    disassembleRemoteRam(consoleState, _state.value.disasmAddressHex, onLog)
                }
                .onFailure { err ->
                    _state.update { it.copy(isBusy = false, statusMessage = "Revert failed: ${err.localizedMessage}") }
                }
        }
    }

    fun updatePointerFields(
        baseHex: String = _state.value.pointerBaseHex,
        offsetsInput: String = _state.value.pointerOffsetsInput,
        valueType: MemoryValueType = _state.value.pointerValueType
    ) {
        _state.update {
            it.copy(
                pointerBaseHex = baseHex,
                pointerOffsetsInput = offsetsInput,
                pointerValueType = valueType
            )
        }
    }

    fun resolvePointerChain(
        consoleState: ConsoleDebugState,
        onLog: (String, String, String, String) -> Unit
    ) {
        val pid = consoleState.selectedPid ?: run {
            _state.update { it.copy(statusMessage = "Select a PS4 PID first") }
            return
        }
        val st = _state.value
        val baseAddr = st.pointerBaseHex.trim().removePrefix("0x").removePrefix("0X").toLongOrNull(16) ?: run {
            _state.update { it.copy(statusMessage = "Invalid pointer base hex address") }
            return
        }
        val offsets = st.pointerOffsetsInput.split(Regex("[,\\s>]+"))
            .filter { it.isNotBlank() }
            .mapNotNull { token ->
                val sign = if (token.startsWith("-")) -1L else 1L
                val clean = token.removePrefix("+").removePrefix("-")
                val mag = if (clean.startsWith("0x", true)) clean.drop(2).toLongOrNull(16) else clean.toLongOrNull(16) ?: clean.toLongOrNull()
                mag?.let { sign * it }
            }
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            Ps4DebugBridge.resolvePointerChain(
                host = consoleState.host,
                pid = pid,
                baseAddress = baseAddr,
                offsets = offsets,
                valueType = st.pointerValueType,
                debugPort = consoleState.debugPort
            ).onSuccess { result ->
                _state.update {
                    it.copy(
                        isBusy = false,
                        pointerResult = result,
                        statusMessage = "Resolved -> 0x${result.finalAddress.toString(16).uppercase()} = ${result.formattedValue}"
                    )
                }
                onLog("HACK", "INFO", "PTR_CHAIN", "PID=$pid -> 0x${result.finalAddress.toString(16).uppercase()}")
            }.onFailure { err ->
                _state.update { it.copy(isBusy = false, statusMessage = "Pointer deref error: ${err.localizedMessage}") }
            }
        }
    }

    fun analyzeBytes(sourceName: String, baseAddress: Long, bytes: ByteArray) {
        if (bytes.isEmpty()) {
            _state.update { it.copy(statusMessage = "Buffer is empty — peek PS4 RAM or open a binary first") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            val result = withContext(Dispatchers.Default) {
                val entropy = AdvancedHackerEngine.computeShannonEntropy(bytes)
                val elf = AdvancedHackerEngine.inspectElfOrSelf(bytes)
                val strings = AdvancedHackerEngine.extractPrintableStrings(bytes, baseAddress)
                val xorCands = AdvancedHackerEngine.bruteForceSingleByteXor(bytes)
                QuadAnalysis(entropy, elf, strings, xorCands)
            }
            _state.update {
                it.copy(
                    isBusy = false,
                    analyzedSourceName = sourceName,
                    analyzedBaseAddress = baseAddress,
                    analyzedBytes = bytes,
                    entropyReport = result.entropy,
                    elfReport = result.elf,
                    extractedStrings = result.strings,
                    xorBruteCandidates = result.xorCands,
                    statusMessage = "Analyzed $sourceName (${bytes.size}B, H=${String.format(Locale.US, "%.2f", result.entropy.overallEntropy)})"
                )
            }
        }
    }

    fun analyzeImportedBinary(context: Context, uri: Uri, onLog: (String, String, String, String) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            val pair = withContext(Dispatchers.IO) {
                runCatching {
                    val name = PkgParser.resolveFileName(context, uri)
                    val raw = context.contentResolver.openInputStream(uri)?.use { ins ->
                        val buf = ByteArray(2 * 1024 * 1024)
                        val n = ins.read(buf)
                        if (n <= 0) ByteArray(0) else buf.copyOf(n)
                    } ?: ByteArray(0)
                    name to raw
                }
            }
            pair.onSuccess { (name, raw) ->
                if (raw.isEmpty()) {
                    _state.update { it.copy(isBusy = false, statusMessage = "Selected binary is empty") }
                } else {
                    analyzeBytes(name, 0L, raw)
                    onLog("HACK", "INFO", "ELF_FORENSICS", "$name (${raw.size}B)")
                }
            }.onFailure { err ->
                _state.update { it.copy(isBusy = false, statusMessage = "Binary load error: ${err.localizedMessage}") }
            }
        }
    }

    fun updateXorKeyInput(keyInput: String) {
        _state.update { it.copy(xorKeyInput = keyInput) }
    }

    fun decodeWithCustomXorKey() {
        val st = _state.value
        if (st.analyzedBytes.isEmpty()) {
            _state.update { it.copy(statusMessage = "Analyze a RAM block or ELF file first") }
            return
        }
        val cleanHex = st.xorKeyInput.trim().replace(" ", "").removePrefix("0x")
        val keyBytes = if (cleanHex.isNotEmpty() && cleanHex.length % 2 == 0 && cleanHex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            ByteArray(cleanHex.length / 2) { i -> cleanHex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        } else {
            st.xorKeyInput.toByteArray(Charsets.UTF_8)
        }
        if (keyBytes.isEmpty()) return
        val sample = st.analyzedBytes.copyOfRange(0, st.analyzedBytes.size.coerceAtMost(256))
        val decoded = AdvancedHackerEngine.xorTransform(sample, keyBytes)
        val ascii = String(decoded, Charsets.UTF_8).replace(Regex("[^\\x20-\\x7E]"), ".")
        val hex = decoded.take(64).joinToString(" ") { "%02X".format(it) }
        _state.update {
            it.copy(
                xorDecodedPreview = "ASCII:\n$ascii\n\nHEX:\n$hex",
                statusMessage = "XOR decoded ${sample.size}B with ${keyBytes.size}B key"
            )
        }
    }

    fun updateBannerPortsInput(ports: String) {
        _state.update { it.copy(bannerPortsInput = ports) }
    }

    fun probeServiceBanners(host: String, onLog: (String, String, String, String) -> Unit) {
        val ports = _state.value.bannerPortsInput.split(Regex("[,\\s]+"))
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..65535 }
            .distinct()
            .take(32)
        if (ports.isEmpty()) {
            _state.update { it.copy(statusMessage = "Enter valid TCP port numbers") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isProbingBanners = true) }
            val results = Ps4DebugBridge.grabServiceBanners(host, ports)
            val openCount = results.count { it.open }
            _state.update {
                it.copy(
                    isProbingBanners = false,
                    bannerResults = results,
                    statusMessage = "Fingerprinted ${results.size} ports on $host -> $openCount open"
                )
            }
            onLog("HACK", "INFO", "BANNER_RECON", "$host open=$openCount/${results.size}")
        }
    }

    fun updateFtpCmdInput(cmd: String) {
        _state.update { it.copy(ftpCmdInput = cmd) }
    }

    fun sendRawFtpCommand(
        isFtpConnected: Boolean,
        commandOverride: String? = null,
        executeRaw: suspend (String) -> Result<String>,
        onLog: (String, String, String, String) -> Unit
    ) {
        val cmd = (commandOverride ?: _state.value.ftpCmdInput).trim()
        if (cmd.isBlank()) return
        if (!isFtpConnected) {
            _state.update { it.copy(statusMessage = "Connect FTP Client first to send raw FTP commands") }
            return
        }
        val ts = timeFmt.format(Date())
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isBusy = true,
                    ftpCmdInput = cmd,
                    ftpTerminalLines = (it.ftpTerminalLines + "[$ts] > $cmd").takeLast(80)
                )
            }
            executeRaw(cmd)
                .onSuccess { reply ->
                    val ts2 = timeFmt.format(Date())
                    _state.update {
                        it.copy(
                            isBusy = false,
                            ftpTerminalLines = (it.ftpTerminalLines + "[$ts2] < $reply").takeLast(80)
                        )
                    }
                    onLog("HACK", "INFO", "FTP_RAW_CMD", "$cmd -> ${reply.take(80)}")
                }
                .onFailure { err ->
                    val ts2 = timeFmt.format(Date())
                    _state.update {
                        it.copy(
                            isBusy = false,
                            ftpTerminalLines = (it.ftpTerminalLines + "[$ts2] ERR: ${err.localizedMessage}").takeLast(80),
                            statusMessage = "FTP command error: ${err.localizedMessage}"
                        )
                    }
                }
        }
    }

    fun updateCheatBuilder(
        gameTitle: String = _state.value.cheatGameTitle,
        titleId: String = _state.value.cheatTitleId,
        version: String = _state.value.cheatVersion,
        modName: String = _state.value.cheatModName,
        addressHex: String = _state.value.cheatAddressHex,
        onHex: String = _state.value.cheatOnHex,
        offHex: String = _state.value.cheatOffHex,
        useJson: Boolean = _state.value.cheatUseJson
    ) {
        _state.update {
            it.copy(
                cheatGameTitle = gameTitle,
                cheatTitleId = titleId,
                cheatVersion = version,
                cheatModName = modName,
                cheatAddressHex = addressHex,
                cheatOnHex = onHex,
                cheatOffHex = offHex,
                cheatUseJson = useJson
            )
        }
    }

    fun sendAddressToCheatBuilder(address: Long, previewHex: String) {
        _state.update {
            it.copy(
                activeToolIndex = 2,
                cheatAddressHex = address.toString(16).uppercase(),
                cheatOffHex = previewHex,
                statusMessage = "Loaded 0x${address.toString(16).uppercase()} into GoldHEN Cheat Builder"
            )
        }
    }

    private data class QuadAnalysis(
        val entropy: EntropyReport,
        val elf: ElfReport,
        val strings: List<ExtractedString>,
        val xorCands: List<XorCandidate>
    )
}
