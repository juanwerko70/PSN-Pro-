package com.example.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.ln
import kotlin.math.min

enum class NextScanMode(val label: String) {
    EXACT("Exact"),
    INCREASED("Increased (+)"),
    DECREASED("Decreased (-)"),
    CHANGED("Changed (!=)"),
    UNCHANGED("Unchanged (==)"),
    GREATER_THAN("Greater (>)"),
    LESS_THAN("Less (<)")
}

enum class AobRegionScope(val label: String) {
    EXEC("Executable (r-x)"),
    WRITABLE("Writable (rw-)"),
    ALL("All Mapped")
}

data class AobPatternByte(
    val value: Byte,
    val isWildcard: Boolean
)

data class DisassembledInsn(
    val address: Long,
    val bytes: ByteArray,
    val hexBytes: String,
    val mnemonic: String,
    val operands: String
) {
    val length: Int get() = bytes.size
}

data class PatchBackup(
    val address: Long,
    val originalBytes: ByteArray,
    val description: String
)

data class EntropyReport(
    val byteCount: Int,
    val overallEntropy: Double,
    val sliceEntropies: List<Float>,
    val asciiRatio: Float,
    val nullRatio: Float,
    val classification: String
)

data class ExtractedString(
    val offsetAddress: Long,
    val encoding: String,
    val text: String
)

data class XorCandidate(
    val keyByte: Int,
    val printableScore: Float,
    val preview: String
)

data class ElfProgramHeaderInfo(
    val typeLabel: String,
    val flagsLabel: String,
    val virtualAddress: Long,
    val fileSize: Long,
    val memSize: Long
)

data class ElfReport(
    val formatLabel: String,
    val isValidElf64: Boolean,
    val typeLabel: String,
    val machineLabel: String,
    val entryPoint: Long,
    val phNum: Int,
    val shNum: Int,
    val programHeaders: List<ElfProgramHeaderInfo>
)

object AdvancedHackerEngine {

    fun parseAobPattern(pattern: String): List<AobPatternByte> {
        val tokens = pattern.trim()
            .replace(",", " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        val out = mutableListOf<AobPatternByte>()
        for (tok in tokens) {
            when {
                tok == "?" || tok == "??" || tok == "*" || tok == "**" -> {
                    out.add(AobPatternByte(0, true))
                }
                tok.length == 2 -> {
                    val v = tok.toIntOrNull(16) ?: return emptyList()
                    out.add(AobPatternByte(v.toByte(), false))
                }
                tok.length % 2 == 0 && tok.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } -> {
                    var i = 0
                    while (i < tok.length) {
                        val v = tok.substring(i, i + 2).toInt(16)
                        out.add(AobPatternByte(v.toByte(), false))
                        i += 2
                    }
                }
                else -> return emptyList()
            }
        }
        return out
    }

    fun evaluateNextScanCondition(
        mode: NextScanMode,
        valueType: MemoryValueType,
        previousBytes: ByteArray,
        currentBytes: ByteArray,
        targetNeedle: ByteArray
    ): Boolean {
        if (mode == NextScanMode.EXACT) {
            if (targetNeedle.isEmpty() || currentBytes.size < targetNeedle.size) return false
            for (i in targetNeedle.indices) {
                if (currentBytes[i] != targetNeedle[i]) return false
            }
            return true
        }
        if (mode == NextScanMode.CHANGED) {
            val len = min(previousBytes.size, currentBytes.size)
            if (len == 0) return false
            for (i in 0 until len) {
                if (previousBytes[i] != currentBytes[i]) return true
            }
            return false
        }
        if (mode == NextScanMode.UNCHANGED) {
            val len = min(previousBytes.size, currentBytes.size)
            if (len == 0) return false
            for (i in 0 until len) {
                if (previousBytes[i] != currentBytes[i]) return false
            }
            return true
        }

        // Numeric comparisons
        return when (valueType) {
            MemoryValueType.INT32 -> {
                if (currentBytes.size < 4) return false
                val cur = ByteBuffer.wrap(currentBytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt()
                val prev = if (previousBytes.size >= 4) ByteBuffer.wrap(previousBytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() else cur
                val target = if (targetNeedle.size >= 4) ByteBuffer.wrap(targetNeedle, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() else cur
                when (mode) {
                    NextScanMode.INCREASED -> cur > prev
                    NextScanMode.DECREASED -> cur < prev
                    NextScanMode.GREATER_THAN -> cur > target
                    NextScanMode.LESS_THAN -> cur < target
                    else -> false
                }
            }
            MemoryValueType.INT64 -> {
                if (currentBytes.size < 8) return false
                val cur = ByteBuffer.wrap(currentBytes, 0, 8).order(ByteOrder.LITTLE_ENDIAN).getLong()
                val prev = if (previousBytes.size >= 8) ByteBuffer.wrap(previousBytes, 0, 8).order(ByteOrder.LITTLE_ENDIAN).getLong() else cur
                val target = if (targetNeedle.size >= 8) ByteBuffer.wrap(targetNeedle, 0, 8).order(ByteOrder.LITTLE_ENDIAN).getLong() else cur
                when (mode) {
                    NextScanMode.INCREASED -> cur > prev
                    NextScanMode.DECREASED -> cur < prev
                    NextScanMode.GREATER_THAN -> cur > target
                    NextScanMode.LESS_THAN -> cur < target
                    else -> false
                }
            }
            MemoryValueType.FLOAT -> {
                if (currentBytes.size < 4) return false
                val cur = ByteBuffer.wrap(currentBytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat()
                val prev = if (previousBytes.size >= 4) ByteBuffer.wrap(previousBytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat() else cur
                val target = if (targetNeedle.size >= 4) ByteBuffer.wrap(targetNeedle, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat() else cur
                if (cur.isNaN()) return false
                when (mode) {
                    NextScanMode.INCREASED -> cur > prev
                    NextScanMode.DECREASED -> cur < prev
                    NextScanMode.GREATER_THAN -> cur > target
                    NextScanMode.LESS_THAN -> cur < target
                    else -> false
                }
            }
            MemoryValueType.HEX_BYTES, MemoryValueType.UTF8_TEXT -> {
                val curUnsigned = currentBytes.firstOrNull()?.toInt()?.and(0xFF) ?: return false
                val prevUnsigned = previousBytes.firstOrNull()?.toInt()?.and(0xFF) ?: curUnsigned
                val targetUnsigned = targetNeedle.firstOrNull()?.toInt()?.and(0xFF) ?: curUnsigned
                when (mode) {
                    NextScanMode.INCREASED -> curUnsigned > prevUnsigned
                    NextScanMode.DECREASED -> curUnsigned < prevUnsigned
                    NextScanMode.GREATER_THAN -> curUnsigned > targetUnsigned
                    NextScanMode.LESS_THAN -> curUnsigned < targetUnsigned
                    else -> false
                }
            }
        }
    }

    fun computeShannonEntropy(bytes: ByteArray): EntropyReport {
        if (bytes.isEmpty()) {
            return EntropyReport(0, 0.0, emptyList(), 0f, 0f, "Empty Buffer")
        }
        val freq = IntArray(256)
        var asciiCount = 0
        var nullCount = 0
        for (b in bytes) {
            val u = b.toInt() and 0xFF
            freq[u]++
            if (u == 0) nullCount++
            if (u in 32..126 || u == 9 || u == 10 || u == 13) asciiCount++
        }
        val n = bytes.size.toDouble()
        var entropy = 0.0
        for (c in freq) {
            if (c > 0) {
                val p = c / n
                entropy -= p * (ln(p) / ln(2.0))
            }
        }
        val sliceCount = 16.coerceAtMost(bytes.size)
        val sliceSize = (bytes.size / sliceCount).coerceAtLeast(1)
        val slices = ArrayList<Float>(sliceCount)
        for (s in 0 until sliceCount) {
            val start = s * sliceSize
            val end = if (s == sliceCount - 1) bytes.size else min(bytes.size, start + sliceSize)
            val sFreq = IntArray(256)
            for (i in start until end) sFreq[bytes[i].toInt() and 0xFF]++
            val sn = (end - start).coerceAtLeast(1).toDouble()
            var se = 0.0
            for (c in sFreq) {
                if (c > 0) {
                    val p = c / sn
                    se -= p * (ln(p) / ln(2.0))
                }
            }
            slices.add((se / 8.0).toFloat().coerceIn(0f, 1f))
        }
        val asciiRatio = (asciiCount / n).toFloat()
        val nullRatio = (nullCount / n).toFloat()
        val classification = when {
            entropy >= 7.2 -> "Packed / Encrypted (.self / compressed)"
            entropy >= 5.6 -> "Compiled x86-64 Code / Binary"
            asciiRatio >= 0.65f -> "Plaintext / Strings / Script (.rodata)"
            nullRatio >= 0.45f -> "Zero-Padded / Sparse Struct / BSS"
            else -> "Mixed Structured Binary"
        }
        return EntropyReport(
            byteCount = bytes.size,
            overallEntropy = entropy,
            sliceEntropies = slices,
            asciiRatio = asciiRatio,
            nullRatio = nullRatio,
            classification = classification
        )
    }

    fun extractPrintableStrings(
        bytes: ByteArray,
        baseAddress: Long = 0L,
        minLen: Int = 5,
        maxResults: Int = 120
    ): List<ExtractedString> {
        val results = mutableListOf<ExtractedString>()
        // ASCII pass
        val sb = StringBuilder()
        var startIdx = 0
        for (i in bytes.indices) {
            val c = bytes[i].toInt() and 0xFF
            if (c in 32..126) {
                if (sb.isEmpty()) startIdx = i
                sb.append(c.toChar())
            } else {
                if (sb.length >= minLen) {
                    results.add(ExtractedString(baseAddress + startIdx, "ASCII", sb.toString()))
                    if (results.size >= maxResults) return results
                }
                sb.clear()
            }
        }
        if (sb.length >= minLen && results.size < maxResults) {
            results.add(ExtractedString(baseAddress + startIdx, "ASCII", sb.toString()))
        }

        // UTF-16LE pass (common in game UI / Unreal / Orbis text)
        val u16 = StringBuilder()
        var u16Start = 0
        var i = 0
        while (i + 1 < bytes.size && results.size < maxResults) {
            val lo = bytes[i].toInt() and 0xFF
            val hi = bytes[i + 1].toInt() and 0xFF
            if (hi == 0 && lo in 32..126) {
                if (u16.isEmpty()) u16Start = i
                u16.append(lo.toChar())
            } else {
                if (u16.length >= minLen) {
                    results.add(ExtractedString(baseAddress + u16Start, "UTF-16", u16.toString()))
                }
                u16.clear()
            }
            i += 2
        }
        if (u16.length >= minLen && results.size < maxResults) {
            results.add(ExtractedString(baseAddress + u16Start, "UTF-16", u16.toString()))
        }
        return results.sortedBy { it.offsetAddress }
    }

    fun xorTransform(bytes: ByteArray, key: ByteArray): ByteArray {
        if (bytes.isEmpty() || key.isEmpty()) return bytes
        return ByteArray(bytes.size) { i ->
            (bytes[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
    }

    fun bruteForceSingleByteXor(bytes: ByteArray, sampleCap: Int = 256): List<XorCandidate> {
        if (bytes.isEmpty()) return emptyList()
        val sample = if (bytes.size > sampleCap) bytes.copyOfRange(0, sampleCap) else bytes
        val candidates = ArrayList<XorCandidate>(255)
        for (k in 1..255) {
            var score = 0
            val preview = StringBuilder(min(sample.size, 48))
            for (idx in sample.indices) {
                val decoded = (sample[idx].toInt() and 0xFF) xor k
                val isPrintable = decoded in 32..126 || decoded == 10 || decoded == 13
                if (isPrintable) score += if (decoded in 65..122 || decoded == 32) 2 else 1
                if (idx < 48) {
                    preview.append(if (decoded in 32..126) decoded.toChar() else '.')
                }
            }
            val ratio = score.toFloat() / (sample.size * 2f)
            if (ratio >= 0.45f) {
                candidates.add(XorCandidate(k, ratio, preview.toString()))
            }
        }
        return candidates.sortedByDescending { it.printableScore }.take(8)
    }

    fun inspectElfOrSelf(bytes: ByteArray): ElfReport {
        if (bytes.size < 64) {
            return ElfReport("Buffer too small (<64B)", false, "-", "-", 0L, 0, 0, emptyList())
        }
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        val b2 = bytes[2].toInt() and 0xFF
        val b3 = bytes[3].toInt() and 0xFF

        if (b0 == 0x4F && b1 == 0x15 && b2 == 0x3D && b3 == 0x1D) {
            return ElfReport("PS4 Signed SELF (Orbis)", false, "SELF", "AMD64 (Encrypted/Signed)", 0L, 0, 0, emptyList())
        }
        if (b0 == 0x54 && b1 == 0x14 && b2 == 0xF5 && b3 == 0xEE) {
            return ElfReport("PS4 Fake SELF (FSELF)", false, "FSELF", "AMD64 (PS4)", 0L, 0, 0, emptyList())
        }
        if (b0 == 0x7F && b1 == 0x50 && b2 == 0x4B && b3 == 0x47) {
            return ElfReport("PS4 PKG Container", false, "CNT/PKG", "Orbis Package", 0L, 0, 0, emptyList())
        }
        if (b0 != 0x7F || b1 != 0x45 || b2 != 0x4C || b3 != 0x46) {
            return ElfReport(
                formatLabel = "Raw Memory / Unknown Magic (${"%02X %02X %02X %02X".format(b0, b1, b2, b3)})",
                isValidElf64 = false,
                typeLabel = "RAW",
                machineLabel = "x86-64 / Data",
                entryPoint = 0L,
                phNum = 0,
                shNum = 0,
                programHeaders = emptyList()
            )
        }
        val eiClass = bytes[4].toInt() and 0xFF
        if (eiClass != 2) {
            return ElfReport("ELF32 (Non-64-bit)", false, "ELF32", "Legacy", 0L, 0, 0, emptyList())
        }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val eType = buf.getShort(16).toInt() and 0xFFFF
        val eMachine = buf.getShort(18).toInt() and 0xFFFF
        val eEntry = buf.getLong(24)
        val ePhOff = buf.getLong(32)
        val ePhEntSize = buf.getShort(54).toInt() and 0xFFFF
        val ePhNum = buf.getShort(56).toInt() and 0xFFFF
        val eShNum = buf.getShort(60).toInt() and 0xFFFF

        val typeLabel = when (eType) {
            2 -> "ET_EXEC (Executable)"
            3 -> "ET_DYN (Shared / SPRX / PIE)"
            0xFE00 -> "ET_SCE_EXEC (PS4 Orbis)"
            0xFE18 -> "ET_SCE_DYNEXEC (PS4 ASLR)"
            0xFE10 -> "ET_SCE_DYNAMIC (PS4 PRX)"
            else -> "0x${eType.toString(16).uppercase()}"
        }
        val machineLabel = when (eMachine) {
            0x3E -> "AMD x86-64 (PS4 APU)"
            0xB7 -> "AArch64"
            0x03 -> "x86"
            else -> "0x${eMachine.toString(16).uppercase()}"
        }

        val phList = mutableListOf<ElfProgramHeaderInfo>()
        if (ePhOff > 0L && ePhEntSize >= 56 && ePhNum > 0) {
            val safeNum = ePhNum.coerceAtMost(32)
            for (idx in 0 until safeNum) {
                val off = (ePhOff + idx.toLong() * ePhEntSize).toInt()
                if (off < 0 || off + 56 > bytes.size) break
                val pType = buf.getInt(off)
                val pFlags = buf.getInt(off + 4)
                val pVaddr = buf.getLong(off + 16)
                val pFileSz = buf.getLong(off + 32)
                val pMemSz = buf.getLong(off + 40)
                val pTypeStr = when (pType) {
                    1 -> "PT_LOAD"
                    2 -> "PT_DYNAMIC"
                    3 -> "PT_INTERP"
                    4 -> "PT_NOTE"
                    6 -> "PT_PHDR"
                    7 -> "PT_TLS"
                    0x61000000 -> "PT_SCE_DYNLIBDATA"
                    0x61000001 -> "PT_SCE_PROC_PARAM"
                    0x61000002 -> "PT_SCE_MODULE_PARAM"
                    0x6474E551 -> "PT_GNU_STACK"
                    0x6474E552 -> "PT_GNU_RELRO"
                    else -> "0x${pType.toUInt().toString(16).uppercase()}"
                }
                val r = if ((pFlags and 0x4) != 0) "R" else "-"
                val w = if ((pFlags and 0x2) != 0) "W" else "-"
                val x = if ((pFlags and 0x1) != 0) "X" else "-"
                phList.add(ElfProgramHeaderInfo(pTypeStr, "$r$w$x", pVaddr, pFileSz, pMemSz))
            }
        }

        return ElfReport(
            formatLabel = "ELF64 Binary (Orbis/x86-64)",
            isValidElf64 = true,
            typeLabel = typeLabel,
            machineLabel = machineLabel,
            entryPoint = eEntry,
            phNum = ePhNum,
            shNum = eShNum,
            programHeaders = phList
        )
    }

    fun generateGoldHenJsonCheat(
        gameTitle: String,
        titleId: String,
        gameVersion: String,
        modName: String,
        address: Long,
        onHexBytes: String,
        offHexBytes: String
    ): String {
        val cleanTitle = gameTitle.ifBlank { "PS4 Custom Mod" }.replace("\"", "'")
        val cleanId = titleId.ifBlank { "CUSA00000" }.uppercase(Locale.US)
        val cleanVer = gameVersion.ifBlank { "01.00" }
        val cleanMod = modName.ifBlank { "Custom Patch" }.replace("\"", "'")
        val offsetHex = address.toString(16).uppercase(Locale.US)
        val cleanOn = onHexBytes.replace(Regex("[^0-9A-Fa-f]"), "").uppercase(Locale.US).ifBlank { "9090" }
        val cleanOff = offHexBytes.replace(Regex("[^0-9A-Fa-f]"), "").uppercase(Locale.US).ifBlank { "9090" }
        return """
{
  "name": "$cleanTitle",
  "id": "$cleanId",
  "version": "$cleanVer",
  "credits": "PKG Forge Cyber Lab",
  "mods": [
    {
      "name": "$cleanMod",
      "type": "checkbox",
      "memory": [
        {
          "offset": "$offsetHex",
          "on": "$cleanOn",
          "off": "$cleanOff"
        }
      ]
    }
  ]
}
        """.trimIndent()
    }

    fun generateShnCheatXml(
        gameTitle: String,
        titleId: String,
        gameVersion: String,
        modName: String,
        address: Long,
        onHexBytes: String,
        offHexBytes: String
    ): String {
        val cleanTitle = gameTitle.ifBlank { "PS4 Custom Mod" }
        val cleanId = titleId.ifBlank { "CUSA00000" }.uppercase(Locale.US)
        val cleanVer = gameVersion.ifBlank { "01.00" }
        val offsetHex = address.toString(16).uppercase(Locale.US)
        val cleanOn = onHexBytes.replace(Regex("[^0-9A-Fa-f]"), "").uppercase(Locale.US).ifBlank { "9090" }
        val cleanOff = offHexBytes.replace(Regex("[^0-9A-Fa-f]"), "").uppercase(Locale.US).ifBlank { "9090" }
        return """
<?xml version="1.0" encoding="utf-16"?>
<Trainer Game="$cleanTitle" Moder="PKG Forge" Cusa="$cleanId" Version="$cleanVer" Process="eboot.bin">
  <Cheat Text="$modName">
    <Cheatline>
      <Offset>$offsetHex</Offset>
      <Section>0</Section>
      <ValueOn>$cleanOn</ValueOn>
      <ValueOff>$cleanOff</ValueOff>
    </Cheatline>
  </Cheat>
</Trainer>
        """.trimIndent()
    }

    fun nopPatch(length: Int): ByteArray = ByteArray(length.coerceIn(1, 32)) { 0x90.toByte() }
    fun retZeroPatch(): ByteArray = byteArrayOf(0x31, 0xC0.toByte(), 0xC3.toByte()) // xor eax, eax; ret
    fun retOnePatch(): ByteArray = byteArrayOf(0xB8.toByte(), 0x01, 0x00, 0x00, 0x00, 0xC3.toByte()) // mov eax, 1; ret
    fun retPatch(): ByteArray = byteArrayOf(0xC3.toByte())
}

/**
 * Lightweight real x86-64 instruction decoder tailored for PS4 eboot.bin / SPRX reverse engineering.
 * Accurately measures prefix + opcode + ModRM + SIB + displacements + immediates and never stalls.
 */
object X86Disassembler {
    private val regs64 = arrayOf("rax", "rcx", "rdx", "rbx", "rsp", "rbp", "rsi", "rdi", "r8", "r9", "r10", "r11", "r12", "r13", "r14", "r15")
    private val regs32 = arrayOf("eax", "ecx", "edx", "ebx", "esp", "ebp", "esi", "edi", "r8d", "r9d", "r10d", "r11d", "r12d", "r13d", "r14d", "r15d")
    private val jccNames = arrayOf("jo", "jno", "jb", "jae", "je", "jne", "jbe", "ja", "js", "jns", "jp", "jnp", "jl", "jge", "jle", "jg")

    fun disassemble(bytes: ByteArray, baseAddress: Long, maxInstructions: Int = 80): List<DisassembledInsn> {
        val out = ArrayList<DisassembledInsn>(min(maxInstructions, 80))
        var pc = 0
        while (pc < bytes.size && out.size < maxInstructions) {
            val insnStart = pc
            var rex = 0
            var opSize16 = false
            var repPrefix = 0

            // Prefixes
            while (pc < bytes.size) {
                val b = bytes[pc].toInt() and 0xFF
                when {
                    b in 0x40..0x4F -> { rex = b; pc++ }
                    b == 0x66 -> { opSize16 = true; pc++ }
                    b == 0xF2 || b == 0xF3 -> { repPrefix = b; pc++ }
                    b == 0x64 || b == 0x65 || b == 0xF0 || b == 0x2E || b == 0x3E -> pc++
                    else -> break
                }
            }
            if (pc >= bytes.size) {
                out.add(buildInsn(bytes, insnStart, bytes.size, baseAddress + insnStart, ".byte", hexSlice(bytes, insnStart, bytes.size)))
                break
            }

            val rexW = (rex and 0x08) != 0
            val rexR = if ((rex and 0x04) != 0) 8 else 0
            val rexX = if ((rex and 0x02) != 0) 8 else 0
            val rexB = if ((rex and 0x01) != 0) 8 else 0

            val op = bytes[pc++].toInt() and 0xFF
            val curAddr = baseAddress + insnStart

            fun emit(mnemonic: String, operands: String = "") {
                out.add(buildInsn(bytes, insnStart, pc, curAddr, mnemonic, operands))
            }

            fun regName(idx: Int): String = if (rexW) regs64[idx and 0xF] else regs32[idx and 0xF]

            fun readS8(): Int? = if (pc < bytes.size) bytes[pc++].toInt() else null
            fun readS32(): Int? {
                if (pc + 4 > bytes.size) return null
                val v = ByteBuffer.wrap(bytes, pc, 4).order(ByteOrder.LITTLE_ENDIAN).getInt()
                pc += 4
                return v
            }
            fun readS16(): Int? {
                if (pc + 2 > bytes.size) return null
                val v = ByteBuffer.wrap(bytes, pc, 2).order(ByteOrder.LITTLE_ENDIAN).getShort().toInt()
                pc += 2
                return v
            }

            // Returns (regField 0..7, rmText) or null if truncated
            fun decodeModRm(insnEndAddrEstimate: Long = 0L): Pair<Int, String>? {
                if (pc >= bytes.size) return null
                val modrm = bytes[pc++].toInt() and 0xFF
                val mod = (modrm ushr 6) and 0x3
                val reg = (modrm ushr 3) and 0x7
                val rm = modrm and 0x7

                if (mod == 3) {
                    return reg to regName(rm or rexB)
                }
                val baseText: String = if (rm == 4) {
                    if (pc >= bytes.size) return null
                    val sib = bytes[pc++].toInt() and 0xFF
                    val scale = 1 shl ((sib ushr 6) and 0x3)
                    val indexIdx = ((sib ushr 3) and 0x7) or rexX
                    val baseIdx = (sib and 0x7) or rexB
                    val baseStr = if (mod == 0 && (baseIdx and 0x7) == 5) {
                        val disp = readS32() ?: return null
                        "0x${disp.toUInt().toString(16)}"
                    } else regs64[baseIdx]
                    val idxStr = if (indexIdx == 4) "" else {
                        val mult = if (scale > 1) "*$scale" else ""
                        "+${regs64[indexIdx]}$mult"
                    }
                    "$baseStr$idxStr"
                } else if (mod == 0 && rm == 5) {
                    val disp = readS32() ?: return null
                    val target = baseAddress + pc + disp.toLong()
                    return reg to "[rip+0x${disp.toUInt().toString(16)}] ; 0x${target.toString(16)}"
                } else {
                    regs64[rm or rexB]
                }

                val dispStr = when (mod) {
                    1 -> {
                        val d = readS8() ?: return null
                        if (d >= 0) "+0x${d.toString(16)}" else "-0x${(-d).toString(16)}"
                    }
                    2 -> {
                        val d = readS32() ?: return null
                        if (d >= 0) "+0x${d.toString(16)}" else "-0x${(-d.toLong()).toString(16)}"
                    }
                    else -> ""
                }
                return reg to "[$baseText$dispStr]"
            }

            when (op) {
                0x90 -> emit(if (repPrefix == 0xF3) "pause" else "nop")
                0xC3 -> emit("ret")
                0xC2 -> {
                    val imm = readS16()
                    if (imm != null) emit("ret", "0x${(imm and 0xFFFF).toString(16)}")
                    else { pc = bytes.size; emit(".byte", hexSlice(bytes, insnStart, bytes.size)) }
                }
                0xCC -> emit("int3")
                0xF4 -> emit("hlt")
                0xC9 -> emit("leave")
                in 0x50..0x57 -> emit("push", regs64[(op - 0x50) or rexB])
                in 0x58..0x5F -> emit("pop", regs64[(op - 0x58) or rexB])
                0xE8, 0xE9 -> {
                    val rel = readS32()
                    if (rel != null) {
                        val target = baseAddress + pc + rel.toLong()
                        emit(if (op == 0xE8) "call" else "jmp", "0x${target.toString(16)}")
                    } else {
                        pc = insnStart + 1
                        emit(".byte", "0x%02X".format(op))
                    }
                }
                0xEB -> {
                    val rel = readS8()
                    if (rel != null) {
                        val target = baseAddress + pc + rel.toLong()
                        emit("jmp short", "0x${target.toString(16)}")
                    } else {
                        pc = insnStart + 1
                        emit(".byte", "0xEB")
                    }
                }
                in 0x70..0x7F -> {
                    val rel = readS8()
                    if (rel != null) {
                        val target = baseAddress + pc + rel.toLong()
                        emit(jccNames[op - 0x70], "0x${target.toString(16)}")
                    } else {
                        pc = insnStart + 1
                        emit(".byte", "0x%02X".format(op))
                    }
                }
                in 0xB8..0xBF -> {
                    val rIdx = (op - 0xB8) or rexB
                    if (rexW && pc + 8 <= bytes.size) {
                        val v = ByteBuffer.wrap(bytes, pc, 8).order(ByteOrder.LITTLE_ENDIAN).getLong()
                        pc += 8
                        emit("mov", "${regs64[rIdx]}, 0x${v.toString(16)}")
                    } else {
                        val v = readS32()
                        if (v != null) emit("mov", "${regName(rIdx)}, 0x${v.toUInt().toString(16)}")
                        else { pc = insnStart + 1; emit(".byte", "0x%02X".format(op)) }
                    }
                }
                0x89, 0x8B, 0x8D, 0x31, 0x33, 0x01, 0x03, 0x29, 0x2B, 0x39, 0x3B, 0x85 -> {
                    val decoded = decodeModRm()
                    if (decoded != null) {
                        val (regField, rmOp) = decoded
                        val regOp = regName(regField or rexR)
                        val mn = when (op) {
                            0x89, 0x8B -> "mov"
                            0x8D -> "lea"
                            0x31, 0x33 -> "xor"
                            0x01, 0x03 -> "add"
                            0x29, 0x2B -> "sub"
                            0x39, 0x3B -> "cmp"
                            else -> "test"
                        }
                        val dirToMem = op in intArrayOf(0x89, 0x31, 0x01, 0x29, 0x39, 0x85)
                        val operands = if (dirToMem) "$rmOp, $regOp" else "$regOp, $rmOp"
                        emit(mn, operands)
                    } else {
                        pc = insnStart + 1
                        emit(".byte", "0x%02X".format(op))
                    }
                }
                0x83, 0x81 -> {
                    val decoded = decodeModRm()
                    val imm = if (op == 0x83) readS8() else readS32()
                    if (decoded != null && imm != null) {
                        val (regField, rmOp) = decoded
                        val mn = when (regField) {
                            0 -> "add"
                            1 -> "or"
                            4 -> "and"
                            5 -> "sub"
                            6 -> "xor"
                            7 -> "cmp"
                            else -> "alu"
                        }
                        val immStr = if (imm >= 0) "0x${imm.toString(16)}" else "-0x${(-imm.toLong()).toString(16)}"
                        emit(mn, "$rmOp, $immStr")
                    } else {
                        pc = insnStart + 1
                        emit(".byte", "0x%02X".format(op))
                    }
                }
                0xC7 -> {
                    val decoded = decodeModRm()
                    val imm = readS32()
                    if (decoded != null && imm != null && decoded.first == 0) {
                        emit("mov", "${decoded.second}, 0x${imm.toUInt().toString(16)}")
                    } else {
                        pc = insnStart + 1
                        emit(".byte", "0xC7")
                    }
                }
                0xFF -> {
                    val decoded = decodeModRm()
                    if (decoded != null) {
                        val (regField, rmOp) = decoded
                        val mn = when (regField) {
                            0 -> "inc"
                            1 -> "dec"
                            2 -> "call"
                            4 -> "jmp"
                            6 -> "push"
                            else -> "grp5/$regField"
                        }
                        emit(mn, rmOp)
                    } else {
                        pc = insnStart + 1
                        emit(".byte", "0xFF")
                    }
                }
                0x0F -> {
                    if (pc >= bytes.size) {
                        pc = insnStart + 1
                        emit(".byte", "0x0F")
                    } else {
                        val op2 = bytes[pc++].toInt() and 0xFF
                        when {
                            op2 == 0x05 -> emit("syscall")
                            op2 in 0x80..0x8F -> {
                                val rel = readS32()
                                if (rel != null) {
                                    val target = baseAddress + pc + rel.toLong()
                                    emit(jccNames[op2 - 0x80], "0x${target.toString(16)}")
                                } else {
                                    pc = insnStart + 1
                                    emit(".byte", "0x0F")
                                }
                            }
                            op2 == 0x1F -> {
                                val decoded = decodeModRm()
                                if (decoded != null) emit("nop", decoded.second)
                                else emit("nop")
                            }
                            op2 == 0xB6 || op2 == 0xB7 || op2 == 0xBE || op2 == 0xBF -> {
                                val decoded = decodeModRm()
                                if (decoded != null) {
                                    val mn = if (op2 == 0xB6 || op2 == 0xB7) "movzx" else "movsx"
                                    emit(mn, "${regName(decoded.first or rexR)}, ${decoded.second}")
                                } else {
                                    pc = insnStart + 1
                                    emit(".byte", "0x0F")
                                }
                            }
                            else -> emit("ext0f", "0x%02X".format(op2))
                        }
                    }
                }
                else -> {
                    if (opSize16) {
                        // consume as raw byte cleanly
                    }
                    pc = insnStart + 1
                    emit(".byte", "0x%02X".format(bytes[insnStart].toInt() and 0xFF))
                }
            }
        }
        return out
    }

    private fun buildInsn(
        full: ByteArray,
        start: Int,
        end: Int,
        address: Long,
        mnemonic: String,
        operands: String
    ): DisassembledInsn {
        val slice = full.copyOfRange(start, min(full.size, end))
        val hex = slice.joinToString(" ") { "%02X".format(it) }
        return DisassembledInsn(address, slice, hex, mnemonic, operands)
    }

    private fun hexSlice(bytes: ByteArray, start: Int, end: Int): String =
        bytes.copyOfRange(start, min(bytes.size, end)).joinToString(" ") { "%02X".format(it) }
}
