package com.example.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.min

data class FtpFileEntry(
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val permissions: String,
    val modifiedLabel: String
)

data class Ps4ProcessInfo(
    val pid: Int,
    val name: String,
    val titleId: String = "",
    val rssKb: Long = 0L,
    val virtualKb: Long = 0L,
    val status: String = "RUNNING"
)

data class Ps4MemoryRegion(
    val name: String,
    val startAddress: Long,
    val endAddress: Long,
    val protection: Int
) {
    val sizeKb: Long get() = ((endAddress - startAddress).coerceAtLeast(0L)) / 1024L
}

data class Ps4MemorySnapshot(
    val totalRegions: Int,
    val totalMappedKb: Long,
    val executableKb: Long,
    val writableHeapKb: Long,
    val sharedLibKb: Long,
    val topRegions: List<Ps4MemoryRegion>
)

data class PortProbeResult(
    val port: Int,
    val serviceName: String,
    val reachable: Boolean,
    val latencyMs: Long
)

data class DiscoveredConsole(
    val host: String,
    val openPorts: List<Int>,
    val latencyMs: Long
)

enum class MemoryValueType(val label: String, val byteSize: Int) {
    INT32("INT32", 4),
    INT64("INT64", 8),
    FLOAT("FLOAT", 4),
    HEX_BYTES("HEX", 0),
    UTF8_TEXT("UTF-8", 0)
}

data class MemorySearchHit(
    val address: Long,
    val regionName: String,
    val previewHex: String
)

data class PreviousScanEntry(
    val address: Long,
    val regionName: String,
    val lastBytes: ByteArray
) {
    val previewHex: String
        get() = lastBytes.take(8).joinToString(" ") { "%02X".format(it) }
}

data class PointerHop(
    val level: Int,
    val readAddress: Long,
    val offsetApplied: Long,
    val derefValue: Long
)

data class PointerChainResult(
    val hops: List<PointerHop>,
    val finalAddress: Long,
    val finalBytes: ByteArray,
    val formattedValue: String
)

data class ServiceBannerResult(
    val port: Int,
    val serviceGuess: String,
    val open: Boolean,
    val latencyMs: Long,
    val banner: String
)

class Ps4FtpClient {
    private var controlSocket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    private var connectedHost: String = ""

    val isConnected: Boolean
        get() = controlSocket?.isConnected == true && controlSocket?.isClosed == false

    suspend fun connect(
        host: String,
        port: Int,
        username: String = "anonymous",
        password: String = ""
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            disconnect()
            val logs = mutableListOf<String>()
            val socket = Socket()
            socket.connect(InetSocketAddress(host.trim(), port), 6000)
            socket.soTimeout = 10000
            controlSocket = socket
            connectedHost = host.trim()
            reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))

            logs.add(readReply())
            logs.add(sendCommand("USER ${username.ifBlank { "anonymous" }}"))
            val passReply = sendCommand("PASS $password")
            logs.add(passReply)
            logs.add(sendCommand("TYPE I"))
            logs.add(sendCommand("SYST"))
            logs
        }
    }

    suspend fun pwd(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val reply = sendCommand("PWD")
            val match = Regex("\"([^\"]+)\"").find(reply)
            match?.groupValues?.get(1) ?: "/"
        }
    }

    suspend fun listDirectory(path: String): Result<List<FtpFileEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val cwdReply = sendCommand("CWD $path")
            if (cwdReply.startsWith("5")) error(cwdReply)
            val dataSocket = openPasvDataSocket()
            dataSocket.use { ds ->
                val listReply = sendCommand("LIST")
                if (!listReply.startsWith("1")) error(listReply)
                val lines = BufferedReader(InputStreamReader(ds.getInputStream(), Charsets.UTF_8)).readLines()
                readReply() // 226 Transfer complete
                lines.mapNotNull { parseUnixListLine(it) }
                    .sortedWith(compareBy<FtpFileEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
            }
        }
    }

    suspend fun downloadFile(
        remotePath: String,
        output: OutputStream,
        onBytes: (Long) -> Unit = {}
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val dataSocket = openPasvDataSocket()
            var total = 0L
            dataSocket.use { ds ->
                val reply = sendCommand("RETR $remotePath")
                if (!reply.startsWith("1")) error(reply)
                val ins = ds.getInputStream()
                val buf = ByteArray(32768)
                var n: Int
                while (ins.read(buf).also { n = it } != -1) {
                    output.write(buf, 0, n)
                    total += n
                    onBytes(total)
                }
                output.flush()
                readReply()
            }
            total
        }
    }

    suspend fun uploadFile(
        remotePath: String,
        input: InputStream,
        onBytes: (Long) -> Unit = {}
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val dataSocket = openPasvDataSocket()
            var total = 0L
            dataSocket.use { ds ->
                val reply = sendCommand("STOR $remotePath")
                if (!reply.startsWith("1")) error(reply)
                val out = ds.getOutputStream()
                val buf = ByteArray(32768)
                var n: Int
                while (input.read(buf).also { n = it } != -1) {
                    out.write(buf, 0, n)
                    total += n
                    onBytes(total)
                }
                out.flush()
            }
            readReply()
            total
        }
    }

    suspend fun deleteFile(remotePath: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val reply = sendCommand("DELE $remotePath")
            if (reply.startsWith("5")) error(reply)
            reply
        }
    }

    suspend fun makeDirectory(dirPath: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val reply = sendCommand("MKD $dirPath")
            if (reply.startsWith("5")) error(reply)
            reply
        }
    }

    suspend fun executeRaw(command: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching { sendCommand(command) }
    }

    fun disconnect() {
        runCatching { writer?.write("QUIT\r\n"); writer?.flush() }
        runCatching { reader?.close() }
        runCatching { writer?.close() }
        runCatching { controlSocket?.close() }
        reader = null
        writer = null
        controlSocket = null
    }

    private fun openPasvDataSocket(): Socket {
        val pasvReply = sendCommand("PASV")
        val match = Regex("\\((\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)\\)").find(pasvReply)
            ?: error("PASV failed: $pasvReply")
        val g = match.groupValues
        val ip = "${g[1]}.${g[2]}.${g[3]}.${g[4]}"
        val dataPort = (g[5].toInt() shl 8) or g[6].toInt()
        val targetHost = if (ip == "0.0.0.0" || ip.startsWith("127.")) connectedHost else ip
        return Socket().apply {
            connect(InetSocketAddress(targetHost, dataPort), 6000)
            soTimeout = 15000
        }
    }

    private fun sendCommand(cmd: String): String {
        val w = writer ?: error("FTP control socket not connected")
        w.write("$cmd\r\n")
        w.flush()
        return readReply()
    }

    private fun readReply(): String {
        val r = reader ?: error("FTP reader unavailable")
        val first = r.readLine() ?: error("FTP connection closed by console")
        if (first.length >= 4 && first[3] == '-') {
            val code = first.substring(0, 3)
            val sb = StringBuilder(first)
            while (true) {
                val line = r.readLine() ?: break
                sb.append('\n').append(line)
                if (line.startsWith("$code ")) break
            }
            return sb.toString()
        }
        return first
    }

    private fun parseUnixListLine(line: String): FtpFileEntry? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("total ")) return null
        val parts = trimmed.split(Regex("\\s+"), limit = 9)
        if (parts.size < 9) {
            return FtpFileEntry(
                name = trimmed,
                isDirectory = false,
                sizeBytes = 0L,
                permissions = "-",
                modifiedLabel = ""
            )
        }
        val perms = parts[0]
        val isDir = perms.startsWith("d", ignoreCase = true)
        val size = parts[4].toLongOrNull() ?: 0L
        val mod = "${parts[5]} ${parts[6]} ${parts[7]}"
        val name = parts[8]
        if (name == "." || name == "..") return null
        return FtpFileEntry(
            name = name,
            isDirectory = isDir,
            sizeBytes = size,
            permissions = perms,
            modifiedLabel = mod
        )
    }
}

object Ps4DebugBridge {
    private const val PACKET_MAGIC = 0xFFAABBCC.toInt()
    private const val CMD_PROC_LIST = 0xBDAA0001.toInt()
    private const val CMD_PROC_READ = 0xBDAA0004.toInt()
    private const val CMD_PROC_WRITE = 0xBDAA0005.toInt()
    private const val CMD_PROC_MAPS = 0xBDAA0003.toInt()
    private const val CMD_PROC_SIGNAL = 0xBDAA0009.toInt()

    fun detectLocalSubnetPrefix(fallbackHost: String): String {
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
                val nif = interfaces.nextElement()
                if (!nif.isUp || nif.isLoopback) continue
                val addrs = nif.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        val parts = ip.split('.')
                        if (parts.size == 4) {
                            return "${parts[0]}.${parts[1]}.${parts[2]}"
                        }
                    }
                }
            }
        }
        val fallbackParts = fallbackHost.trim().split('.')
        return if (fallbackParts.size == 4) {
            "${fallbackParts[0]}.${fallbackParts[1]}.${fallbackParts[2]}"
        } else {
            "192.168.1"
        }
    }

    suspend fun scanSubnetForConsoles(
        subnetPrefix: String,
        ports: List<Int>,
        onHostScanned: (Int) -> Unit = {}
    ): List<DiscoveredConsole> = withContext(Dispatchers.IO) {
        val cleanPrefix = subnetPrefix.trim().trimEnd('.')
        val semaphore = Semaphore(32)
        var scannedCount = 0
        coroutineScope {
            val jobs = (1..254).map { hostOctet ->
                async {
                    semaphore.withPermit {
                        val ip = "$cleanPrefix.$hostOctet"
                        val open = mutableListOf<Int>()
                        var bestLatency = Long.MAX_VALUE
                        for (port in ports) {
                            val start = System.currentTimeMillis()
                            val ok = try {
                                Socket().use { s ->
                                    s.connect(InetSocketAddress(ip, port), 220)
                                    true
                                }
                            } catch (_: Exception) {
                                false
                            }
                            if (ok) {
                                open.add(port)
                                val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                                if (elapsed < bestLatency) bestLatency = elapsed
                            }
                        }
                        val progressNow: Int
                        synchronized(ports) {
                            scannedCount += 1
                            progressNow = scannedCount
                        }
                        if (progressNow % 16 == 0 || progressNow == 254) {
                            onHostScanned(progressNow)
                        }
                        if (open.isNotEmpty()) {
                            DiscoveredConsole(ip, open, if (bestLatency == Long.MAX_VALUE) 1L else bestLatency)
                        } else null
                    }
                }
            }
            jobs.awaitAll().filterNotNull().sortedBy { it.latencyMs }
        }
    }

    suspend fun sendBinPayload(host: String, port: Int, payload: ByteArray): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host.trim(), port), 6000)
                    socket.getOutputStream().use { os ->
                        os.write(payload)
                        os.flush()
                    }
                }
                payload.size
            }
        }

    fun streamKlog(host: String, port: Int): Flow<String> = flow {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host.trim(), port), 6000)
            socket.soTimeout = 2500
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            while (currentCoroutineContext().isActive) {
                try {
                    val line = reader.readLine() ?: break
                    if (line.isNotBlank()) emit(line)
                } catch (_: java.net.SocketTimeoutException) {
                    // Keep listening while stream active
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun probePorts(
        host: String,
        ftpPort: Int,
        binLoaderPort: Int,
        klogPort: Int,
        debugPort: Int = 744
    ): List<PortProbeResult> = withContext(Dispatchers.IO) {
        val targets = listOf(
            ftpPort to "FTP Server",
            binLoaderPort to "BinLoader",
            klogPort to "Klog Debug",
            debugPort to "PS4Debug Daemon"
        )
        targets.map { (port, label) ->
            val start = System.currentTimeMillis()
            val ok = try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(host.trim(), port), 1500)
                    true
                }
            } catch (_: Exception) {
                false
            }
            val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
            PortProbeResult(port, label, ok, elapsed)
        }
    }

    /**
     * Queries running PS4 processes via ps4debug TCP daemon (port 744).
     */
    suspend fun fetchProcessList(host: String, debugPort: Int = 744): Result<List<Ps4ProcessInfo>> =
        withContext(Dispatchers.IO) {
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host.trim(), debugPort), 4000)
                    socket.soTimeout = 5000
                    val dos = DataOutputStream(socket.getOutputStream())
                    val dis = DataInputStream(socket.getInputStream())
                    writePacketHeader(dos, CMD_PROC_LIST, 0)
                    val status = readLeInt(dis)
                    require(status == 0x80000000.toInt() || status >= 0) {
                        "PS4Debug returned status 0x${status.toUInt().toString(16)}"
                    }
                    val count = readLeInt(dis).coerceIn(0, 512)
                    val list = ArrayList<Ps4ProcessInfo>(count)
                    for (i in 0 until count) {
                        val nameBytes = ByteArray(32)
                        dis.readFully(nameBytes)
                        val pid = readLeInt(dis)
                        val cleanName = String(nameBytes, Charsets.UTF_8).trimEnd('\u0000')
                        list.add(Ps4ProcessInfo(pid = pid, name = cleanName.ifBlank { "proc_$pid" }))
                    }
                    list
                }
            }
        }

    suspend fun fetchProcessMemory(host: String, pid: Int, debugPort: Int = 744): Result<Ps4MemorySnapshot> =
        withContext(Dispatchers.IO) {
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host.trim(), debugPort), 4000)
                    socket.soTimeout = 5000
                    val dos = DataOutputStream(socket.getOutputStream())
                    val dis = DataInputStream(socket.getInputStream())
                    val body = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(pid).array()
                    writePacketHeader(dos, CMD_PROC_MAPS, body.size)
                    dos.write(body)
                    dos.flush()
                    readLeInt(dis) // status
                    val count = readLeInt(dis).coerceIn(0, 2048)
                    val regions = ArrayList<Ps4MemoryRegion>(count)
                    for (i in 0 until count) {
                        val nameBytes = ByteArray(32)
                        dis.readFully(nameBytes)
                        val start = readLeLong(dis)
                        val end = readLeLong(dis)
                        val prot = readLeInt(dis)
                        val cleanName = String(nameBytes, Charsets.UTF_8).trimEnd('\u0000')
                        regions.add(Ps4MemoryRegion(cleanName.ifBlank { "anon" }, start, end, prot))
                    }
                    var execKb = 0L
                    var heapKb = 0L
                    var sharedKb = 0L
                    for (r in regions) {
                        when {
                            (r.protection and 0x4) != 0 -> execKb += r.sizeKb
                            (r.protection and 0x2) != 0 -> heapKb += r.sizeKb
                            else -> sharedKb += r.sizeKb
                        }
                    }
                    Ps4MemorySnapshot(
                        totalRegions = regions.size,
                        totalMappedKb = regions.sumOf { it.sizeKb },
                        executableKb = execKb,
                        writableHeapKb = heapKb,
                        sharedLibKb = sharedKb,
                        topRegions = regions.sortedByDescending { it.sizeKb }.take(24)
                    )
                }
            }
        }

    suspend fun readProcessMemory(
        host: String,
        pid: Int,
        address: Long,
        length: Int,
        debugPort: Int = 744
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val safeLen = length.coerceIn(1, 65536)
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host.trim(), debugPort), 4000)
                socket.soTimeout = 5000
                val dos = DataOutputStream(socket.getOutputStream())
                val dis = DataInputStream(socket.getInputStream())
                val body = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(pid)
                    .putLong(address)
                    .putInt(safeLen)
                    .array()
                writePacketHeader(dos, CMD_PROC_READ, body.size)
                dos.write(body)
                dos.flush()
                val status = readLeInt(dis)
                require(status == 0x80000000.toInt() || status >= 0) {
                    "PS4Debug read error 0x${status.toUInt().toString(16)}"
                }
                val out = ByteArray(safeLen)
                dis.readFully(out)
                out
            }
        }
    }

    suspend fun writeProcessMemory(
        host: String,
        pid: Int,
        address: Long,
        data: ByteArray,
        debugPort: Int = 744
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(data.isNotEmpty() && data.size <= 65536) { "Write size must be 1..65536 bytes" }
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host.trim(), debugPort), 4000)
                socket.soTimeout = 5000
                val dos = DataOutputStream(socket.getOutputStream())
                val dis = DataInputStream(socket.getInputStream())
                val headerBody = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(pid)
                    .putLong(address)
                    .putInt(data.size)
                    .array()
                writePacketHeader(dos, CMD_PROC_WRITE, headerBody.size + data.size)
                dos.write(headerBody)
                dos.write(data)
                dos.flush()
                val status = readLeInt(dis)
                require(status == 0x80000000.toInt() || status >= 0) {
                    "PS4Debug write error 0x${status.toUInt().toString(16)}"
                }
            }
        }
    }

    suspend fun scanProcessMemory(
        host: String,
        pid: Int,
        regions: List<Ps4MemoryRegion>,
        valueType: MemoryValueType,
        query: String,
        debugPort: Int = 744,
        maxHits: Int = 40
    ): Result<List<MemorySearchHit>> = withContext(Dispatchers.IO) {
        runCatching {
            val needle = encodeSearchNeedle(valueType, query)
            require(needle.isNotEmpty()) { "Invalid search value for ${valueType.label}" }
            val hits = mutableListOf<MemorySearchHit>()
            val writableRegions = regions.filter { (it.protection and 0x2) != 0 || (it.protection and 0x1) != 0 }
                .ifEmpty { regions }

            for (region in writableRegions) {
                if (hits.size >= maxHits) break
                val regionLen = (region.endAddress - region.startAddress).coerceAtMost(4L * 1024L * 1024L)
                var offset = 0L
                val chunkSize = 65536
                while (offset < regionLen && hits.size < maxHits) {
                    val toRead = min(chunkSize.toLong(), regionLen - offset).toInt()
                    val chunk = readProcessMemory(host, pid, region.startAddress + offset, toRead, debugPort)
                        .getOrNull() ?: break
                    var idx = 0
                    while (idx <= chunk.size - needle.size && hits.size < maxHits) {
                        var match = true
                        for (j in needle.indices) {
                            if (chunk[idx + j] != needle[j]) {
                                match = false
                                break
                            }
                        }
                        if (match) {
                            val endPreview = min(chunk.size, idx + 8)
                            val preview = chunk.copyOfRange(idx, endPreview)
                                .joinToString(" ") { "%02X".format(it) }
                            hits.add(
                                MemorySearchHit(
                                    address = region.startAddress + offset + idx,
                                    regionName = region.name,
                                    previewHex = preview
                                )
                            )
                            idx += needle.size
                        } else {
                            idx += 1
                        }
                    }
                    offset += chunkSize
                }
            }
            hits
        }
    }

    suspend fun dumpMemoryRegion(
        host: String,
        pid: Int,
        startAddress: Long,
        totalBytes: Long,
        output: OutputStream,
        debugPort: Int = 744,
        onProgress: (Long) -> Unit = {}
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val safeTotal = totalBytes.coerceIn(1L, 32L * 1024L * 1024L)
            var offset = 0L
            val chunkSize = 32768
            while (offset < safeTotal) {
                val len = min(chunkSize.toLong(), safeTotal - offset).toInt()
                val bytes = readProcessMemory(host, pid, startAddress + offset, len, debugPort).getOrThrow()
                output.write(bytes)
                offset += bytes.size
                onProgress(offset)
            }
            output.flush()
            offset
        }
    }

    fun encodeSearchNeedle(type: MemoryValueType, input: String): ByteArray {
        val clean = input.trim()
        return when (type) {
            MemoryValueType.INT32 -> {
                val v = if (clean.startsWith("0x", true)) {
                    clean.drop(2).toLongOrNull(16)?.toInt() ?: return ByteArray(0)
                } else {
                    clean.toIntOrNull() ?: return ByteArray(0)
                }
                ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()
            }
            MemoryValueType.INT64 -> {
                val v = if (clean.startsWith("0x", true)) {
                    clean.drop(2).toLongOrNull(16) ?: return ByteArray(0)
                } else {
                    clean.toLongOrNull() ?: return ByteArray(0)
                }
                ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v).array()
            }
            MemoryValueType.FLOAT -> {
                val v = clean.toFloatOrNull() ?: return ByteArray(0)
                ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(v).array()
            }
            MemoryValueType.UTF8_TEXT -> clean.toByteArray(Charsets.UTF_8)
            MemoryValueType.HEX_BYTES -> {
                val hex = clean.replace(Regex("[^0-9A-Fa-f]"), "")
                if (hex.isEmpty() || hex.length % 2 != 0) return ByteArray(0)
                ByteArray(hex.length / 2) { i ->
                    hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                }
            }
        }
    }

    fun formatMemoryValue(type: MemoryValueType, bytes: ByteArray): String {
        if (bytes.isEmpty()) return "-"
        return when (type) {
            MemoryValueType.INT32 -> if (bytes.size >= 4) {
                ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt().toString()
            } else "?"
            MemoryValueType.INT64 -> if (bytes.size >= 8) {
                ByteBuffer.wrap(bytes, 0, 8).order(ByteOrder.LITTLE_ENDIAN).getLong().toString()
            } else "?"
            MemoryValueType.FLOAT -> if (bytes.size >= 4) {
                "%.4f".format(Locale.US, ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat())
            } else "?"
            MemoryValueType.UTF8_TEXT -> String(bytes, Charsets.UTF_8).replace(Regex("[\\x00-\\x1F]"), ".")
            MemoryValueType.HEX_BYTES -> bytes.take(16).joinToString(" ") { "%02X".format(it) }
        }
    }

    suspend fun sendProcessCommand(
        host: String,
        debugPort: Int = 744,
        pidOrCode: Int,
        signalCode: Int
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host.trim(), debugPort), 4000)
                socket.soTimeout = 4000
                val dos = DataOutputStream(socket.getOutputStream())
                val body = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(pidOrCode)
                    .putInt(signalCode)
                    .array()
                writePacketHeader(dos, CMD_PROC_SIGNAL, body.size)
                dos.write(body)
                dos.flush()
                "Signal $signalCode dispatched to PID $pidOrCode"
            }
        }
    }

    suspend fun scanProcessAob(
        host: String,
        pid: Int,
        regions: List<Ps4MemoryRegion>,
        pattern: String,
        scope: AobRegionScope = AobRegionScope.EXEC,
        debugPort: Int = 744,
        maxHits: Int = 48
    ): Result<List<MemorySearchHit>> = withContext(Dispatchers.IO) {
        runCatching {
            val aob = AdvancedHackerEngine.parseAobPattern(pattern)
            require(aob.isNotEmpty()) { "Invalid AoB pattern (example: 48 8B 05 ?? ?? ?? ??)" }
            val filtered = when (scope) {
                AobRegionScope.EXEC -> regions.filter { (it.protection and 0x4) != 0 || (it.protection and 0x1) != 0 }
                AobRegionScope.WRITABLE -> regions.filter { (it.protection and 0x2) != 0 }
                AobRegionScope.ALL -> regions
            }.ifEmpty { regions }

            val hits = mutableListOf<MemorySearchHit>()
            for (region in filtered) {
                if (hits.size >= maxHits) break
                val regionLen = (region.endAddress - region.startAddress).coerceAtMost(4L * 1024L * 1024L)
                var offset = 0L
                val chunkSize = 65536
                while (offset < regionLen && hits.size < maxHits) {
                    val toRead = min(chunkSize.toLong(), regionLen - offset).toInt()
                    val chunk = readProcessMemory(host, pid, region.startAddress + offset, toRead, debugPort)
                        .getOrNull() ?: break
                    var idx = 0
                    while (idx <= chunk.size - aob.size && hits.size < maxHits) {
                        var match = true
                        for (j in aob.indices) {
                            val pat = aob[j]
                            if (!pat.isWildcard && chunk[idx + j] != pat.value) {
                                match = false
                                break
                            }
                        }
                        if (match) {
                            val preview = chunk.copyOfRange(idx, min(chunk.size, idx + min(12, aob.size.coerceAtLeast(8))))
                                .joinToString(" ") { "%02X".format(it) }
                            hits.add(
                                MemorySearchHit(
                                    address = region.startAddress + offset + idx,
                                    regionName = region.name,
                                    previewHex = preview
                                )
                            )
                            idx += aob.size
                        } else {
                            idx += 1
                        }
                    }
                    offset += chunkSize
                }
            }
            hits
        }
    }

    suspend fun narrowMemoryHits(
        host: String,
        pid: Int,
        previousHits: List<PreviousScanEntry>,
        valueType: MemoryValueType,
        mode: NextScanMode,
        query: String,
        debugPort: Int = 744
    ): Result<List<PreviousScanEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val needle = if (mode == NextScanMode.EXACT || mode == NextScanMode.GREATER_THAN || mode == NextScanMode.LESS_THAN) {
                encodeSearchNeedle(valueType, query)
            } else {
                ByteArray(0)
            }
            if ((mode == NextScanMode.EXACT || mode == NextScanMode.GREATER_THAN || mode == NextScanMode.LESS_THAN) && needle.isEmpty()) {
                error("Enter a valid ${valueType.label} value for ${mode.label}")
            }
            val readLen = maxOf(8, valueType.byteSize, needle.size).coerceAtMost(64)
            val kept = mutableListOf<PreviousScanEntry>()
            for (entry in previousHits) {
                val fresh = readProcessMemory(host, pid, entry.address, readLen, debugPort).getOrNull() ?: continue
                if (AdvancedHackerEngine.evaluateNextScanCondition(mode, valueType, entry.lastBytes, fresh, needle)) {
                    kept.add(entry.copy(lastBytes = fresh))
                }
            }
            kept
        }
    }

    suspend fun resolvePointerChain(
        host: String,
        pid: Int,
        baseAddress: Long,
        offsets: List<Long>,
        valueType: MemoryValueType,
        debugPort: Int = 744
    ): Result<PointerChainResult> = withContext(Dispatchers.IO) {
        runCatching {
            var currentAddr = baseAddress
            val hops = mutableListOf<PointerHop>()
            offsets.forEachIndexed { idx, off ->
                val rawPtr = readProcessMemory(host, pid, currentAddr, 8, debugPort).getOrThrow()
                val ptrVal = ByteBuffer.wrap(rawPtr, 0, 8).order(ByteOrder.LITTLE_ENDIAN).getLong()
                hops.add(PointerHop(level = idx + 1, readAddress = currentAddr, offsetApplied = off, derefValue = ptrVal))
                currentAddr = ptrVal + off
            }
            val finalLen = if (valueType.byteSize > 0) valueType.byteSize else 16
            val finalBytes = readProcessMemory(host, pid, currentAddr, finalLen, debugPort).getOrThrow()
            PointerChainResult(
                hops = hops,
                finalAddress = currentAddr,
                finalBytes = finalBytes,
                formattedValue = formatMemoryValue(valueType, finalBytes)
            )
        }
    }

    suspend fun grabServiceBanners(
        host: String,
        ports: List<Int>
    ): List<ServiceBannerResult> = withContext(Dispatchers.IO) {
        val cleanHost = host.trim()
        val semaphore = Semaphore(8)
        coroutineScope {
            ports.distinct().map { port ->
                async {
                    semaphore.withPermit {
                        val serviceGuess = when (port) {
                            21, 2121 -> "FTP / GoldHEN FTP"
                            22 -> "SSH / Dropbear"
                            80, 8080 -> "HTTP Web"
                            443 -> "HTTPS TLS"
                            744 -> "PS4Debug Daemon"
                            3232 -> "Klog Debug Stream"
                            9020, 9021, 9090 -> "BinLoader ELF/BIN"
                            9999 -> "Orbis / Remote Shell"
                            12800 -> "Orbis Net / Companion"
                            else -> "TCP/$port"
                        }
                        val start = System.currentTimeMillis()
                        try {
                            Socket().use { sock ->
                                sock.connect(InetSocketAddress(cleanHost, port), 1400)
                                sock.soTimeout = 700
                                val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                                val ins = sock.getInputStream()
                                val os = sock.getOutputStream()
                                val buf = ByteArray(512)
                                var n = try {
                                    ins.read(buf)
                                } catch (_: Exception) {
                                    -1
                                }
                                if (n <= 0 && (port == 80 || port == 8080 || port == 12800)) {
                                    runCatching {
                                        os.write("HEAD / HTTP/1.0\r\nHost: $cleanHost\r\n\r\n".toByteArray(Charsets.US_ASCII))
                                        os.flush()
                                        n = ins.read(buf)
                                    }
                                }
                                val banner = if (n > 0) {
                                    String(buf, 0, n, Charsets.UTF_8)
                                        .replace(Regex("[\\r\\n]+"), " | ")
                                        .replace(Regex("[^\\x20-\\x7E]"), ".")
                                        .take(140)
                                        .trim()
                                } else {
                                    "(Open TCP socket — silent listener)"
                                }
                                ServiceBannerResult(port, serviceGuess, true, latency, banner)
                            }
                        } catch (_: Exception) {
                            val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                            ServiceBannerResult(port, serviceGuess, false, latency, "Closed / Timeout")
                        }
                    }
                }
            }.awaitAll().sortedWith(compareBy<ServiceBannerResult> { !it.open }.thenBy { it.port })
        }
    }

    private fun writePacketHeader(dos: DataOutputStream, cmd: Int, dataLen: Int) {
        val hdr = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(PACKET_MAGIC)
            .putInt(cmd)
            .putInt(dataLen)
            .array()
        dos.write(hdr)
        dos.flush()
    }

    private fun readLeInt(dis: DataInputStream): Int {
        val b = ByteArray(4)
        dis.readFully(b)
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getInt()
    }

    private fun readLeLong(dis: DataInputStream): Long {
        val b = ByteArray(8)
        dis.readFully(b)
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getLong()
    }
}
