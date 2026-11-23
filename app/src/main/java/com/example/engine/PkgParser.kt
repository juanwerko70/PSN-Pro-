package com.example.engine

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.CRC32

data class PkgEntryInfo(
    val id: Long,
    val name: String,
    val flags1: Long,
    val flags2: Long,
    val offset: Long,
    val size: Long,
    val encrypted: Boolean
) {
    val idHex: String get() = "0x${id.toString(16).uppercase().padStart(4, '0')}"
}

data class PkgHeaderInfo(
    val fileName: String,
    val fileSizeBytes: Long,
    val magicHex: String,
    val pkgType: Long,
    val pkgFlags: Long,
    val entryCount: Int,
    val scEntryCount: Int,
    val fileTableOffset: Long,
    val bodyOffset: Long,
    val bodySize: Long,
    val contentId: String,
    val drmType: Long,
    val contentType: Long,
    val contentTypeLabel: String,
    val headerSha256: String,
    val headerCrc32: String,
    val entries: List<PkgEntryInfo>,
    val hexPreviewOffset: Long,
    val hexPreviewBytes: ByteArray
)

object PkgParser {
    private const val PKG_MAGIC = 0x7F434E54 // "\x7FCNT"

    private val KNOWN_ENTRY_IDS = mapOf(
        0x0001L to ".digests",
        0x0010L to ".entry_keys",
        0x0020L to ".image_key",
        0x0080L to ".general_digests",
        0x0100L to ".metas",
        0x0200L to ".entry_names",
        0x0400L to "license.dat",
        0x0401L to "license.info",
        0x0402L to "nptitle.dat",
        0x0403L to "npbind.dat",
        0x0404L to "selfinfo.dat",
        0x0406L to "imageinfo.dat",
        0x0407L to "target-deltainfo.dat",
        0x0408L to "origin-deltainfo.dat",
        0x0409L to "psreserved.dat",
        0x1000L to "param.sfo",
        0x1001L to "playgo-chunk.dat",
        0x1002L to "playgo-chunk.sha",
        0x1003L to "playgo-manifest.xml",
        0x1004L to "pronunciation.xml",
        0x1005L to "pronunciation.sig",
        0x1006L to "pic1.png",
        0x1007L to "pubtoolinfo.dat",
        0x1008L to "app/playgo-chunk.dat",
        0x1200L to "icon0.png",
        0x1220L to "pic0.png",
        0x1240L to "pic1.png",
        0x1260L to "snd0.at9",
        0x1280L to "changeinfo/changeinfo.xml",
        0x1400L to "trophy/trophy00.trp",
        0x1600L to "keymap_rp/001.png",
        0x2000L to "eboot.bin"
    )

    fun parseFromUri(context: Context, uri: Uri): Result<PkgHeaderInfo> = runCatching {
        val fileName = resolveFileName(context, uri)
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("Unable to open PKG file descriptor")

        pfd.use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { fis ->
                val channel = fis.channel
                val totalSize = channel.size()
                require(totalSize >= 0x100L) { "File too small to be a PS4 PKG ($totalSize bytes)" }

                val headerSize = minOf(totalSize, 4096L).toInt()
                val headerBuf = ByteBuffer.allocate(headerSize).order(ByteOrder.BIG_ENDIAN)
                channel.position(0)
                channel.read(headerBuf)
                val headerBytes = headerBuf.array()

                val magic = ByteBuffer.wrap(headerBytes, 0, 4).order(ByteOrder.BIG_ENDIAN).getInt()
                require(magic == PKG_MAGIC) {
                    "Not a PS4 PKG file: magic=0x${magic.toUInt().toString(16).uppercase()} (expected 7F434E54)"
                }

                val bb = ByteBuffer.wrap(headerBytes).order(ByteOrder.BIG_ENDIAN)
                val pkgType = bb.getInt(0x04).toLong() and 0xFFFFFFFFL
                val pkgFlags = bb.getInt(0x08).toLong() and 0xFFFFFFFFL
                val entryCount = bb.getInt(0x10).coerceIn(0, 4096)
                val scEntryCount = bb.getShort(0x14).toInt() and 0xFFFF
                val fileTableOffset = bb.getInt(0x18).toLong() and 0xFFFFFFFFL
                val bodyOffset = bb.getLong(0x20)
                val bodySize = bb.getLong(0x28)
                val contentId = if (headerBytes.size >= 0x64) {
                    String(headerBytes, 0x40, 36, Charsets.US_ASCII).trimEnd('\u0000')
                } else ""
                val drmType = bb.getInt(0x68).toLong() and 0xFFFFFFFFL
                val contentType = bb.getInt(0x6C).toLong() and 0xFFFFFFFFL

                val entries = ArrayList<PkgEntryInfo>(entryCount)
                if (fileTableOffset > 0 && fileTableOffset + entryCount * 32L <= totalSize) {
                    val tableBytes = ByteArray(entryCount * 32)
                    val tableBuf = ByteBuffer.wrap(tableBytes)
                    channel.position(fileTableOffset)
                    while (tableBuf.hasRemaining()) {
                        if (channel.read(tableBuf) <= 0) break
                    }
                    val tbb = ByteBuffer.wrap(tableBytes).order(ByteOrder.BIG_ENDIAN)
                    var namesBytes: ByteArray? = null

                    // First pass to locate entry_names (0x0200) if unencrypted
                    for (i in 0 until entryCount) {
                        val off = i * 32
                        val id = tbb.getInt(off).toLong() and 0xFFFFFFFFL
                        val f1 = tbb.getInt(off + 8).toLong() and 0xFFFFFFFFL
                        val eOffset = tbb.getInt(off + 16).toLong() and 0xFFFFFFFFL
                        val eSize = tbb.getInt(off + 20).toLong() and 0xFFFFFFFFL
                        if (id == 0x0200L && (f1 and 0x80000000L) == 0L && eSize in 1..262144L && eOffset + eSize <= totalSize) {
                            namesBytes = readSlice(channel, eOffset, eSize.toInt())
                            break
                        }
                    }

                    for (i in 0 until entryCount) {
                        val off = i * 32
                        val id = tbb.getInt(off).toLong() and 0xFFFFFFFFL
                        val nameOff = tbb.getInt(off + 4)
                        val f1 = tbb.getInt(off + 8).toLong() and 0xFFFFFFFFL
                        val f2 = tbb.getInt(off + 12).toLong() and 0xFFFFFFFFL
                        val eOffset = tbb.getInt(off + 16).toLong() and 0xFFFFFFFFL
                        val eSize = tbb.getInt(off + 20).toLong() and 0xFFFFFFFFL
                        val encrypted = (f1 and 0x80000000L) != 0L

                        val resolvedName = resolveEntryName(id, nameOff, namesBytes)
                        entries.add(
                            PkgEntryInfo(
                                id = id,
                                name = resolvedName,
                                flags1 = f1,
                                flags2 = f2,
                                offset = eOffset,
                                size = eSize,
                                encrypted = encrypted
                            )
                        )
                    }
                }

                val sha = MessageDigest.getInstance("SHA-256")
                    .digest(headerBytes.copyOfRange(0, minOf(512, headerBytes.size)))
                    .joinToString("") { "%02x".format(it) }
                val crc = CRC32().apply {
                    update(headerBytes, 0, minOf(512, headerBytes.size))
                }.value.toString(16).uppercase().padStart(8, '0')

                val hexWindow = headerBytes.copyOfRange(0, minOf(256, headerBytes.size))

                PkgHeaderInfo(
                    fileName = fileName,
                    fileSizeBytes = totalSize,
                    magicHex = "7F 43 4E 54",
                    pkgType = pkgType,
                    pkgFlags = pkgFlags,
                    entryCount = entryCount,
                    scEntryCount = scEntryCount,
                    fileTableOffset = fileTableOffset,
                    bodyOffset = bodyOffset,
                    bodySize = bodySize,
                    contentId = contentId,
                    drmType = drmType,
                    contentType = contentType,
                    contentTypeLabel = mapContentType(contentType),
                    headerSha256 = sha,
                    headerCrc32 = crc,
                    entries = entries,
                    hexPreviewOffset = 0L,
                    hexPreviewBytes = hexWindow
                )
            }
        }
    }

    fun readEntryBytes(context: Context, uri: Uri, entry: PkgEntryInfo, maxBytes: Int = 4 * 1024 * 1024): Result<ByteArray> =
        runCatching {
            require(entry.size in 1..maxBytes.toLong()) {
                "Entry size (${entry.size} bytes) exceeds safe buffer limit ($maxBytes bytes)"
            }
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: error("Unable to open PKG file descriptor")
            pfd.use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { fis ->
                    readSlice(fis.channel, entry.offset, entry.size.toInt())
                }
            }
        }

    fun readHexSlice(context: Context, uri: Uri, offset: Long, length: Int = 256): Result<ByteArray> =
        runCatching {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: error("Unable to open PKG file descriptor")
            pfd.use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { fis ->
                    val ch = fis.channel
                    val safeOffset = offset.coerceIn(0L, (ch.size() - 1L).coerceAtLeast(0L))
                    val safeLen = minOf(length.toLong(), ch.size() - safeOffset).toInt().coerceAtLeast(0)
                    readSlice(ch, safeOffset, safeLen)
                }
            }
        }

    /**
     * Writes a patched copy of an imported PKG to [destUri], optionally updating the Content ID
     * in the header (0x40..0x64) and patching an unencrypted PARAM.SFO entry in-place if size fits.
     */
    fun exportPatchedPkgCopy(
        context: Context,
        sourceUri: Uri,
        destUri: Uri,
        newContentId: String?,
        patchedSfoBytes: ByteArray?,
        sfoEntry: PkgEntryInfo?
    ): Result<String> = runCatching {
        val input = context.contentResolver.openInputStream(sourceUri)
            ?: error("Cannot open source PKG stream")
        val outputPfd = context.contentResolver.openFileDescriptor(destUri, "rw")
            ?: error("Cannot open destination PKG stream")

        val notes = mutableListOf<String>()
        input.use { ins ->
            outputPfd.use { outPfd ->
                FileOutputStream(outPfd.fileDescriptor).use { fos ->
                    val buf = ByteArray(65536)
                    var read: Int
                    while (ins.read(buf).also { read = it } != -1) {
                        fos.write(buf, 0, read)
                    }
                    fos.flush()

                    val outChannel = fos.channel
                    if (!newContentId.isNullOrBlank() && newContentId.length <= 36) {
                        val cidBytes = ByteArray(36)
                        val raw = newContentId.toByteArray(Charsets.US_ASCII)
                        System.arraycopy(raw, 0, cidBytes, 0, raw.size)
                        outChannel.position(0x40L)
                        outChannel.write(ByteBuffer.wrap(cidBytes))
                        notes.add("Updated Content ID at 0x40")
                    }
                    if (patchedSfoBytes != null && sfoEntry != null && !sfoEntry.encrypted) {
                        if (patchedSfoBytes.size <= sfoEntry.size) {
                            val padded = ByteArray(sfoEntry.size.toInt())
                            System.arraycopy(patchedSfoBytes, 0, padded, 0, patchedSfoBytes.size)
                            outChannel.position(sfoEntry.offset)
                            outChannel.write(ByteBuffer.wrap(padded))
                            notes.add("Patched PARAM.SFO in-place at 0x${sfoEntry.offset.toString(16)}")
                        } else {
                            notes.add("Skipped in-place SFO patch (new size ${patchedSfoBytes.size} > slot ${sfoEntry.size})")
                        }
                    }
                }
            }
        }
        if (notes.isEmpty()) "Exported exact PKG copy" else notes.joinToString(" • ")
    }

    fun formatHexDump(bytes: ByteArray, baseOffset: Long = 0L): String {
        val sb = StringBuilder()
        var i = 0
        while (i < bytes.size) {
            val rowEnd = minOf(i + 16, bytes.size)
            sb.append("%08X  ".format(baseOffset + i))
            for (j in i until i + 16) {
                if (j < rowEnd) sb.append("%02X ".format(bytes[j]))
                else sb.append("   ")
                if (j == i + 7) sb.append(' ')
            }
            sb.append(" |")
            for (j in i until rowEnd) {
                val c = bytes[j].toInt() and 0xFF
                sb.append(if (c in 32..126) c.toChar() else '.')
            }
            sb.append("|\n")
            i += 16
        }
        return sb.toString()
    }

    fun resolveFileName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                return cursor.getString(idx) ?: "package.pkg"
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "package.pkg"
    }

    private fun readSlice(channel: java.nio.channels.FileChannel, offset: Long, length: Int): ByteArray {
        val out = ByteArray(length)
        val bb = ByteBuffer.wrap(out)
        channel.position(offset)
        while (bb.hasRemaining()) {
            if (channel.read(bb) <= 0) break
        }
        return out
    }

    private fun resolveEntryName(id: Long, nameOffset: Int, namesBytes: ByteArray?): String {
        if (namesBytes != null && nameOffset > 0 && nameOffset < namesBytes.size) {
            var end = nameOffset
            while (end < namesBytes.size && namesBytes[end] != 0.toByte()) end++
            val candidate = String(namesBytes, nameOffset, end - nameOffset, Charsets.UTF_8)
            if (candidate.isNotBlank()) return candidate
        }
        return KNOWN_ENTRY_IDS[id] ?: "entry_${id.toString(16).uppercase()}"
    }

    private fun mapContentType(type: Long): String = when (type) {
        0x1AL -> "GD (Game Data)"
        0x1BL -> "AC (Additional Content / DLC)"
        0x1CL -> "GP (Game Patch / Update)"
        0x1DL -> "Theme / System"
        else -> "Type 0x${type.toString(16).uppercase()}"
    }
}
