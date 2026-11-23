package com.example.engine

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class SfoValueFormat(val code: Int, val label: String) {
    UTF8_SPECIAL(0x0004, "UTF-8 (Raw)"),
    UTF8(0x0204, "UTF-8"),
    INT32(0x0404, "UInt32");

    companion object {
        fun fromCode(code: Int): SfoValueFormat =
            entries.firstOrNull { it.code == code } ?: UTF8
    }
}

data class SfoEntry(
    val key: String,
    val format: SfoValueFormat,
    val maxLen: Int,
    val stringValue: String = "",
    val intValue: Long = 0L
)

data class SfoDocument(
    val versionMajor: Int = 1,
    val versionMinor: Int = 1,
    val entries: List<SfoEntry> = emptyList(),
    val sourceLabel: String = "PARAM.SFO"
) {
    fun getString(key: String): String? = entries.firstOrNull { it.key == key }?.stringValue
    fun getInt(key: String): Long? = entries.firstOrNull { it.key == key }?.intValue
}

data class SfoDiagnosticIssue(
    val severity: String,
    val key: String,
    val message: String,
    val fixAvailable: Boolean = false
)

object ParamSfoCodec {
    private const val SFO_MAGIC = 0x46535000 // "\0PSF" little-endian

    fun parse(bytes: ByteArray, sourceLabel: String = "PARAM.SFO"): SfoDocument {
        require(bytes.size >= 20) { "PARAM.SFO too small (${bytes.size} bytes)" }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buf.getInt(0)
        require(magic == SFO_MAGIC) {
            "Invalid SFO magic: 0x${magic.toUInt().toString(16).uppercase()} (expected 00505346)"
        }
        val ver = buf.getInt(4)
        val verMinor = (ver ushr 8) and 0xFF
        val verMajor = ver and 0xFF
        val keyTableStart = buf.getInt(8)
        val dataTableStart = buf.getInt(12)
        val tablesEntries = buf.getInt(16)

        require(tablesEntries in 0..2048) { "Suspicious SFO entry count: $tablesEntries" }
        require(20 + tablesEntries * 16 <= bytes.size) { "Truncated SFO index table" }

        val entries = ArrayList<SfoEntry>(tablesEntries)
        for (i in 0 until tablesEntries) {
            val base = 20 + i * 16
            val keyOffset = buf.getShort(base).toInt() and 0xFFFF
            val fmtCode = buf.getShort(base + 2).toInt() and 0xFFFF
            val dataLen = buf.getInt(base + 4)
            val dataMaxLen = buf.getInt(base + 8)
            val dataOffset = buf.getInt(base + 12)

            val absKeyStart = keyTableStart + keyOffset
            val key = readNullTerminatedAscii(bytes, absKeyStart)
            val absDataStart = dataTableStart + dataOffset
            val format = SfoValueFormat.fromCode(fmtCode)

            when (format) {
                SfoValueFormat.INT32 -> {
                    val intVal = if (absDataStart + 4 <= bytes.size) {
                        buf.getInt(absDataStart).toLong() and 0xFFFFFFFFL
                    } else 0L
                    entries.add(
                        SfoEntry(
                            key = key,
                            format = format,
                            maxLen = dataMaxLen.coerceAtLeast(4),
                            stringValue = "0x${intVal.toString(16).uppercase().padStart(8, '0')}",
                            intValue = intVal
                        )
                    )
                }
                SfoValueFormat.UTF8, SfoValueFormat.UTF8_SPECIAL -> {
                    val safeEnd = (absDataStart + dataLen).coerceAtMost(bytes.size)
                    val str = if (absDataStart in 0..safeEnd) {
                        val raw = bytes.copyOfRange(absDataStart, safeEnd)
                        val nulIdx = raw.indexOf(0.toByte())
                        val trimmed = if (nulIdx >= 0 && format == SfoValueFormat.UTF8) {
                            raw.copyOfRange(0, nulIdx)
                        } else {
                            raw.dropLastWhile { it == 0.toByte() }.toByteArray()
                        }
                        String(trimmed, Charsets.UTF_8)
                    } else ""
                    entries.add(
                        SfoEntry(
                            key = key,
                            format = format,
                            maxLen = dataMaxLen.coerceAtLeast(str.toByteArray(Charsets.UTF_8).size + 1),
                            stringValue = str
                        )
                    )
                }
            }
        }
        return SfoDocument(
            versionMajor = verMajor,
            versionMinor = verMinor,
            entries = entries,
            sourceLabel = sourceLabel
        )
    }

    fun serialize(doc: SfoDocument): ByteArray {
        val sorted = doc.entries.sortedBy { it.key }
        val count = sorted.size
        val keyStream = ByteArrayOutputStream()
        val dataStream = ByteArrayOutputStream()
        val indexRows = ArrayList<ByteArray>(count)

        for (entry in sorted) {
            val keyOffset = keyStream.size()
            keyStream.write(entry.key.toByteArray(Charsets.US_ASCII))
            keyStream.write(0)

            val dataOffset = dataStream.size()
            val (dataLen, maxLen) = when (entry.format) {
                SfoValueFormat.INT32 -> {
                    val v = parseFlexibleUInt32(entry.stringValue, entry.intValue)
                    val b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v.toInt()).array()
                    dataStream.write(b)
                    4 to 4
                }
                SfoValueFormat.UTF8 -> {
                    val raw = entry.stringValue.toByteArray(Charsets.UTF_8)
                    val needed = raw.size + 1
                    val targetMax = align4(maxOf(entry.maxLen, needed, 4))
                    val actualLen = needed.coerceAtMost(targetMax)
                    val buf = ByteArray(targetMax)
                    val copyLen = raw.size.coerceAtMost(targetMax - 1)
                    System.arraycopy(raw, 0, buf, 0, copyLen)
                    buf[copyLen] = 0
                    dataStream.write(buf)
                    actualLen to targetMax
                }
                SfoValueFormat.UTF8_SPECIAL -> {
                    val raw = entry.stringValue.toByteArray(Charsets.UTF_8)
                    val targetMax = align4(maxOf(entry.maxLen, raw.size, 4))
                    val buf = ByteArray(targetMax)
                    val copyLen = raw.size.coerceAtMost(targetMax)
                    System.arraycopy(raw, 0, buf, 0, copyLen)
                    dataStream.write(buf)
                    copyLen to targetMax
                }
            }

            val row = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply {
                putShort(keyOffset.toShort())
                putShort(entry.format.code.toShort())
                putInt(dataLen)
                putInt(maxLen)
                putInt(dataOffset)
            }.array()
            indexRows.add(row)
        }

        val keyBytes = keyStream.toByteArray()
        val keyPadding = (4 - (keyBytes.size % 4)) % 4
        val keyTableStart = 20 + count * 16
        val dataTableStart = keyTableStart + keyBytes.size + keyPadding
        val dataBytes = dataStream.toByteArray()

        val out = ByteBuffer.allocate(dataTableStart + dataBytes.size).order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(SFO_MAGIC)
        out.putInt((doc.versionMinor shl 8) or doc.versionMajor)
        out.putInt(keyTableStart)
        out.putInt(dataTableStart)
        out.putInt(count)
        for (row in indexRows) {
            out.put(row)
        }
        out.put(keyBytes)
        repeat(keyPadding) { out.put(0.toByte()) }
        out.put(dataBytes)
        return out.array()
    }

    fun createDefaultPs4Template(
        titleId: String = "CUSA00001",
        title: String = "PKG Forge Homebrew",
        appVer: String = "01.00",
        systemVerHex: String = "09000000"
    ): SfoDocument {
        val sysVal = systemVerHex.removePrefix("0x").toLongOrNull(16) ?: 0x09000000L
        return SfoDocument(
            sourceLabel = "Template PARAM.SFO",
            entries = listOf(
                SfoEntry("APP_TYPE", SfoValueFormat.INT32, 4, "0x00000001", 1L),
                SfoEntry("APP_VER", SfoValueFormat.UTF8, 8, appVer),
                SfoEntry("ATTRIBUTE", SfoValueFormat.INT32, 4, "0x00000000", 0L),
                SfoEntry("CATEGORY", SfoValueFormat.UTF8, 4, "gd"),
                SfoEntry("CONTENT_ID", SfoValueFormat.UTF8, 48, "IV0000-${titleId}_00-PKGFORGE00000001"),
                SfoEntry("DOWNLOAD_DATA_SIZE", SfoValueFormat.INT32, 4, "0x00000000", 0L),
                SfoEntry("FORMAT", SfoValueFormat.UTF8, 4, "obs"),
                SfoEntry("PARENTAL_LEVEL", SfoValueFormat.INT32, 4, "0x00000001", 1L),
                SfoEntry("PUBTOOLINFO", SfoValueFormat.UTF8, 512, "c_date=20261123,sdk_ver=09000000,st_type=digital50"),
                SfoEntry("SYSTEM_VER", SfoValueFormat.INT32, 4, "0x${sysVal.toString(16).uppercase().padStart(8, '0')}", sysVal),
                SfoEntry("TITLE", SfoValueFormat.UTF8, 128, title),
                SfoEntry("TITLE_ID", SfoValueFormat.UTF8, 12, titleId),
                SfoEntry("VERSION", SfoValueFormat.UTF8, 8, "01.00")
            )
        )
    }

    fun patchSystemVersion(doc: SfoDocument, targetSdkHex: String): SfoDocument {
        val cleanHex = targetSdkHex.removePrefix("0x").uppercase().padStart(8, '0').take(8)
        val targetLong = cleanHex.toLongOrNull(16) ?: 0x09000000L
        val updated = doc.entries.map { entry ->
            when (entry.key) {
                "SYSTEM_VER" -> entry.copy(
                    stringValue = "0x$cleanHex",
                    intValue = targetLong
                )
                "PUBTOOLINFO" -> {
                    val replaced = if (entry.stringValue.contains("sdk_ver=")) {
                        entry.stringValue.replace(Regex("sdk_ver=[0-9A-Fa-f]+"), "sdk_ver=$cleanHex")
                    } else if (entry.stringValue.isBlank()) {
                        "sdk_ver=$cleanHex"
                    } else {
                        "${entry.stringValue},sdk_ver=$cleanHex"
                    }
                    entry.copy(stringValue = replaced)
                }
                else -> entry
            }
        }.toMutableList()
        if (updated.none { it.key == "SYSTEM_VER" }) {
            updated.add(SfoEntry("SYSTEM_VER", SfoValueFormat.INT32, 4, "0x$cleanHex", targetLong))
        }
        return doc.copy(entries = updated)
    }

    fun validateParameters(doc: SfoDocument): List<SfoDiagnosticIssue> {
        val issues = mutableListOf<SfoDiagnosticIssue>()
        val titleId = doc.getString("TITLE_ID").orEmpty()
        val contentId = doc.getString("CONTENT_ID").orEmpty()
        val sysVer = doc.getInt("SYSTEM_VER") ?: 0L
        val appVer = doc.getString("APP_VER").orEmpty()

        if (!titleId.matches(Regex("^(CUSA|PPSA|PCAS|PLAS|PCJS)[0-9]{5}$"))) {
            issues.add(
                SfoDiagnosticIssue(
                    severity = "WARN",
                    key = "TITLE_ID",
                    message = "TITLE_ID '$titleId' does not match standard PS4/PS5 9-char pattern (e.g. CUSA00001)."
                )
            )
        }
        if (contentId.length != 36) {
            issues.add(
                SfoDiagnosticIssue(
                    severity = "WARN",
                    key = "CONTENT_ID",
                    message = "CONTENT_ID length is ${contentId.length} (standard PS4 Content ID is 36 characters)."
                )
            )
        }
        if (sysVer > 0x09000000L) {
            val major = (sysVer ushr 24) and 0xFF
            val minor = (sysVer ushr 16) and 0xFF
            issues.add(
                SfoDiagnosticIssue(
                    severity = "INFO",
                    key = "SYSTEM_VER",
                    message = "Firmware requirement is $major.${minor.toString().padStart(2, '0')} (above 9.00). Backport patch available.",
                    fixAvailable = true
                )
            )
        }
        if (appVer.isNotBlank() && !appVer.matches(Regex("^\\d{2}\\.\\d{2}$"))) {
            issues.add(
                SfoDiagnosticIssue(
                    severity = "WARN",
                    key = "APP_VER",
                    message = "APP_VER '$appVer' should use XX.YY format (e.g. 01.00)."
                )
            )
        }
        if (issues.isEmpty()) {
            issues.add(
                SfoDiagnosticIssue(
                    severity = "OK",
                    key = "PARAM.SFO",
                    message = "All core PS4 PARAM.SFO fields & alignments passed validation."
                )
            )
        }
        return issues
    }

    private fun parseFlexibleUInt32(str: String, fallback: Long): Long {
        val clean = str.trim()
        return when {
            clean.startsWith("0x", ignoreCase = true) ->
                clean.substring(2).toLongOrNull(16) ?: fallback
            clean.isNotEmpty() ->
                clean.toLongOrNull() ?: fallback
            else -> fallback
        } and 0xFFFFFFFFL
    }

    private fun readNullTerminatedAscii(bytes: ByteArray, offset: Int): String {
        if (offset < 0 || offset >= bytes.size) return ""
        var end = offset
        while (end < bytes.size && bytes[end] != 0.toByte()) end++
        return String(bytes, offset, end - offset, Charsets.US_ASCII)
    }

    private fun align4(v: Int): Int = (v + 3) and 3.inv()
}
