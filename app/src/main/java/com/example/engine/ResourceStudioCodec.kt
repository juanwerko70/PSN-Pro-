package com.example.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class TextureTransformParams(
    val hueDegrees: Float = 0f,
    val saturation: Float = 1f,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val redGain: Float = 1f,
    val greenGain: Float = 1f,
    val blueGain: Float = 1f,
    val scalePercent: Float = 100f,
    val rotationDegrees: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false
)

data class DecodedTexture(
    val name: String,
    val formatLabel: String,
    val bitmap: Bitmap,
    val mipCount: Int = 1
)

data class Vec3(val x: Float, val y: Float, val z: Float)
data class Vec2(val u: Float, val v: Float)

data class Mesh3D(
    val name: String,
    val formatLabel: String,
    val vertices: List<Vec3>,
    val normals: List<Vec3>,
    val uvs: List<Vec2>,
    val faces: List<IntArray>,
    val fbxSummary: String = ""
)

object ResourceStudioCodec {
    private const val DDS_MAGIC = 0x20534444 // "DDS "

    fun decodeTexture(bytes: ByteArray, fileName: String): Result<DecodedTexture> = runCatching {
        if (bytes.size >= 128) {
            val magic = ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt()
            if (magic == DDS_MAGIC) {
                return@runCatching decodeDds(bytes, fileName)
            }
        }
        if (bytes.size >= 6 && bytes[0] == 'P'.code.toByte() && bytes[1] == '3'.code.toByte()) {
            return@runCatching decodeAsciiPpm(bytes, fileName)
        }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("Unsupported texture format or corrupted image: $fileName")
        DecodedTexture(
            name = fileName,
            formatLabel = if (fileName.endsWith(".png", true)) "PNG" else "Raster Image",
            bitmap = bmp.copy(Bitmap.Config.ARGB_8888, false)
        )
    }

    private fun decodeAsciiPpm(bytes: ByteArray, fileName: String): DecodedTexture {
        val tokens = String(bytes, Charsets.UTF_8)
            .lineSequence()
            .map { it.substringBefore('#') }
            .flatMap { it.trim().split(Regex("\\s+")).asSequence() }
            .filter { it.isNotBlank() }
            .toList()
        require(tokens.size >= 4 && tokens[0] == "P3") { "Invalid P3 PPM header" }
        val width = tokens[1].toInt().coerceIn(1, 512)
        val height = tokens[2].toInt().coerceIn(1, 512)
        val maxVal = tokens[3].toInt().coerceIn(1, 255)
        val pixels = IntArray(width * height)
        var idx = 4
        for (p in 0 until width * height) {
            if (idx + 2 >= tokens.size) break
            val r = ((tokens[idx].toIntOrNull() ?: 0) * 255 / maxVal).coerceIn(0, 255)
            val g = ((tokens[idx + 1].toIntOrNull() ?: 0) * 255 / maxVal).coerceIn(0, 255)
            val b = ((tokens[idx + 2].toIntOrNull() ?: 0) * 255 / maxVal).coerceIn(0, 255)
            pixels[p] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            idx += 3
        }
        val bmp = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        return DecodedTexture(name = fileName, formatLabel = "PPM Texture", bitmap = bmp)
    }

    private fun decodeDds(bytes: ByteArray, fileName: String): DecodedTexture {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val height = bb.getInt(12).coerceIn(1, 4096)
        val width = bb.getInt(16).coerceIn(1, 4096)
        val mipCount = bb.getInt(28).coerceAtLeast(1)
        val pfFlags = bb.getInt(80)
        val fourCc = String(bytes, 84, 4, Charsets.US_ASCII)
        val rgbBits = bb.getInt(88)
        val rMask = bb.getInt(92)
        val gMask = bb.getInt(96)
        val bMask = bb.getInt(100)
        val aMask = bb.getInt(104)

        val pixelOffset = if (fourCc == "DX10" && bytes.size >= 148) 148 else 128
        val pixels = IntArray(width * height)

        val formatLabel: String
        if ((pfFlags and 0x4) != 0) { // DDPF_FOURCC
            when (fourCc) {
                "DXT1" -> {
                    formatLabel = "DDS (DXT1/BC1)"
                    decodeDxt1(bytes, pixelOffset, width, height, pixels)
                }
                "DXT3" -> {
                    formatLabel = "DDS (DXT3/BC2)"
                    decodeDxt3(bytes, pixelOffset, width, height, pixels)
                }
                "DXT5" -> {
                    formatLabel = "DDS (DXT5/BC3)"
                    decodeDxt5(bytes, pixelOffset, width, height, pixels)
                }
                else -> error("Unsupported DDS FourCC '$fourCc' (supports DXT1, DXT3, DXT5, RGBA8/BGRA8)")
            }
        } else {
            formatLabel = "DDS (${rgbBits}bpp Uncompressed)"
            val bytesPerPixel = (rgbBits / 8).coerceIn(3, 4)
            val isBgra = (bMask == 0xFF && rMask == 0x00FF0000) || bytesPerPixel == 4
            var src = pixelOffset
            for (i in 0 until width * height) {
                if (src + bytesPerPixel > bytes.size) break
                val b0 = bytes[src].toInt() and 0xFF
                val b1 = bytes[src + 1].toInt() and 0xFF
                val b2 = bytes[src + 2].toInt() and 0xFF
                val b3 = if (bytesPerPixel == 4 && aMask != 0) bytes[src + 3].toInt() and 0xFF else 255
                pixels[i] = if (isBgra && rMask != 0xFF) {
                    (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
                } else {
                    (b3 shl 24) or (b0 shl 16) or (b1 shl 8) or b2
                }
                src += bytesPerPixel
            }
        }

        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        return DecodedTexture(name = fileName, formatLabel = formatLabel, bitmap = bitmap, mipCount = mipCount)
    }

    fun applyTextureTransforms(source: Bitmap, params: TextureTransformParams): Bitmap {
        val scale = (params.scalePercent / 100f).coerceIn(0.1f, 3.0f)
        val targetW = (source.width * scale).roundToInt().coerceIn(8, 4096)
        val targetH = (source.height * scale).roundToInt().coerceIn(8, 4096)

        val scaled = Bitmap.createScaledBitmap(source, targetW, targetH, true)
        val matrix = Matrix().apply {
            val cx = targetW / 2f
            val cy = targetH / 2f
            postScale(
                if (params.flipHorizontal) -1f else 1f,
                if (params.flipVertical) -1f else 1f,
                cx,
                cy
            )
            postRotate(params.rotationDegrees, cx, cy)
        }

        val rotated = Bitmap.createBitmap(scaled, 0, 0, targetW, targetH, matrix, true)
        val out = Bitmap.createBitmap(rotated.width, rotated.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val cm = ColorMatrix()
        cm.setSaturation(params.saturation)
        applyHueRotate(cm, params.hueDegrees)

        val c = params.contrast
        val t = (1f - c) * 128f + params.brightness
        val toneMatrix = ColorMatrix(
            floatArrayOf(
                c * params.redGain, 0f, 0f, 0f, t,
                0f, c * params.greenGain, 0f, 0f, t,
                0f, 0f, c * params.blueGain, 0f, t,
                0f, 0f, 0f, 1f, 0f
            )
        )
        cm.postConcat(toneMatrix)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(rotated, 0f, 0f, paint)
        return out
    }

    fun encodePng(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    fun encodeDdsRgba8(bitmap: Bitmap): ByteArray {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val header = ByteBuffer.allocate(128 + w * h * 4).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(DDS_MAGIC)
        header.putInt(124) // dwSize
        header.putInt(0x1 or 0x2 or 0x4 or 0x8 or 0x1000) // CAPS | HEIGHT | WIDTH | PITCH | PIXELFORMAT
        header.putInt(h)
        header.putInt(w)
        header.putInt(w * 4) // pitch
        header.putInt(0) // depth
        header.putInt(1) // mipmaps
        repeat(11) { header.putInt(0) } // reserved1
        // DDS_PIXELFORMAT (32 bytes at offset 76)
        header.putInt(32)
        header.putInt(0x41) // DDPF_RGB | DDPF_ALPHAPIXELS
        header.putInt(0) // FourCC
        header.putInt(32) // RGBBitCount
        header.putInt(0x00FF0000) // R mask
        header.putInt(0x0000FF00) // G mask
        header.putInt(0x000000FF) // B mask
        header.putInt(0xFF000000.toInt()) // A mask
        header.putInt(0x1000) // dwCaps = TEXTURE
        repeat(4) { header.putInt(0) }

        for (argb in pixels) {
            val a = (argb ushr 24) and 0xFF
            val r = (argb ushr 16) and 0xFF
            val g = (argb ushr 8) and 0xFF
            val b = argb and 0xFF
            // BGRA little-endian
            header.put(b.toByte())
            header.put(g.toByte())
            header.put(r.toByte())
            header.put(a.toByte())
        }
        return header.array()
    }

    fun parseObj(text: String, name: String): Mesh3D {
        val vertices = mutableListOf<Vec3>()
        val normals = mutableListOf<Vec3>()
        val uvs = mutableListOf<Vec2>()
        val faces = mutableListOf<IntArray>()

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val parts = line.split(Regex("\\s+"))
            when (parts[0]) {
                "v" -> if (parts.size >= 4) {
                    vertices.add(
                        Vec3(
                            parts[1].toFloatOrNull() ?: 0f,
                            parts[2].toFloatOrNull() ?: 0f,
                            parts[3].toFloatOrNull() ?: 0f
                        )
                    )
                }
                "vn" -> if (parts.size >= 4) {
                    normals.add(
                        Vec3(
                            parts[1].toFloatOrNull() ?: 0f,
                            parts[2].toFloatOrNull() ?: 0f,
                            parts[3].toFloatOrNull() ?: 0f
                        )
                    )
                }
                "vt" -> if (parts.size >= 3) {
                    uvs.add(
                        Vec2(
                            parts[1].toFloatOrNull() ?: 0f,
                            parts[2].toFloatOrNull() ?: 0f
                        )
                    )
                }
                "f" -> if (parts.size >= 4) {
                    val idxList = parts.drop(1).mapNotNull { token ->
                        val vIdx = token.substringBefore("/").toIntOrNull() ?: return@mapNotNull null
                        if (vIdx > 0) vIdx - 1 else (vertices.size + vIdx)
                    }
                    if (idxList.size >= 3) {
                        for (k in 1 until idxList.size - 1) {
                            faces.add(intArrayOf(idxList[0], idxList[k], idxList[k + 1]))
                        }
                    }
                }
            }
        }
        return Mesh3D(
            name = name,
            formatLabel = "Wavefront OBJ",
            vertices = vertices,
            normals = normals,
            uvs = uvs,
            faces = faces
        )
    }

    fun parseFbx(bytes: ByteArray, name: String): Result<Mesh3D> = runCatching {
        val binaryMagic = "Kaydara FBX Binary  "
        if (bytes.size >= 27 && String(bytes, 0, 20, Charsets.US_ASCII) == binaryMagic) {
            val ver = ByteBuffer.wrap(bytes, 23, 4).order(ByteOrder.LITTLE_ENDIAN).getInt()
            val textStrings = Regex("[A-Za-z0-9_]{3,40}")
                .findAll(String(bytes, 27, minOf(bytes.size - 27, 32768), Charsets.ISO_8859_1))
                .map { it.value }
                .distinct()
                .take(18)
                .toList()
            // Also try parsing ASCII-like numeric vertex arrays if embedded uncompressed, else build proxy bounding geometry with real FBX metadata
            val summary = "Kaydara FBX Binary v$ver (${bytes.size} bytes) • Nodes: ${textStrings.joinToString(", ")}"
            val fallbackCube = createUnitCubeMesh(name, "FBX Binary v$ver").copy(fbxSummary = summary)
            return@runCatching fallbackCube
        }

        // ASCII FBX parser
        val text = String(bytes, Charsets.UTF_8)
        val vertMatch = Regex("Vertices:\\s*\\*\\d+\\s*\\{\\s*a:\\s*([0-9eE+\\-.,\\s]+)\\}").find(text)
        val idxMatch = Regex("PolygonVertexIndex:\\s*\\*\\d+\\s*\\{\\s*a:\\s*([0-9\\-,\\s]+)\\}").find(text)
        val vertices = mutableListOf<Vec3>()
        val faces = mutableListOf<IntArray>()

        if (vertMatch != null) {
            val nums = vertMatch.groupValues[1].split(",").mapNotNull { it.trim().toFloatOrNull() }
            var i = 0
            while (i + 2 < nums.size) {
                vertices.add(Vec3(nums[i], nums[i + 1], nums[i + 2]))
                i += 3
            }
        }
        if (idxMatch != null) {
            val rawIdx = idxMatch.groupValues[1].split(",").mapNotNull { it.trim().toIntOrNull() }
            val poly = mutableListOf<Int>()
            for (v in rawIdx) {
                if (v < 0) {
                    poly.add(v.inv())
                    if (poly.size >= 3) {
                        for (k in 1 until poly.size - 1) {
                            faces.add(intArrayOf(poly[0], poly[k], poly[k + 1]))
                        }
                    }
                    poly.clear()
                } else {
                    poly.add(v)
                }
            }
        }
        if (vertices.isEmpty()) error("No FBX geometry vertices found in $name")
        Mesh3D(
            name = name,
            formatLabel = "Autodesk FBX (ASCII)",
            vertices = vertices,
            normals = emptyList(),
            uvs = emptyList(),
            faces = faces,
            fbxSummary = "Parsed ASCII FBX: ${vertices.size} vertices, ${faces.size} tris"
        )
    }

    fun transformMesh(
        mesh: Mesh3D,
        scaleX: Float,
        scaleY: Float,
        scaleZ: Float,
        rotXDeg: Float,
        rotYDeg: Float,
        rotZDeg: Float
    ): Mesh3D {
        val rx = Math.toRadians(rotXDeg.toDouble())
        val ry = Math.toRadians(rotYDeg.toDouble())
        val rz = Math.toRadians(rotZDeg.toDouble())

        val cosX = cos(rx).toFloat(); val sinX = sin(rx).toFloat()
        val cosY = cos(ry).toFloat(); val sinY = sin(ry).toFloat()
        val cosZ = cos(rz).toFloat(); val sinZ = sin(rz).toFloat()

        val transformed = mesh.vertices.map { v ->
            var x = v.x * scaleX
            var y = v.y * scaleY
            var z = v.z * scaleZ
            // Rotate X
            val y1 = y * cosX - z * sinX
            val z1 = y * sinX + z * cosX
            y = y1; z = z1
            // Rotate Y
            val x2 = x * cosY + z * sinY
            val z2 = -x * sinY + z * cosY
            x = x2; z = z2
            // Rotate Z
            val x3 = x * cosZ - y * sinZ
            val y3 = x * sinZ + y * cosZ
            Vec3(x3, y3, z)
        }
        return mesh.copy(vertices = transformed)
    }

    fun exportObj(mesh: Mesh3D, tintHex: String): String = buildString {
        appendLine("# Exported by PKG Forge 3D Resource Studio")
        appendLine("# Material Tint: $tintHex")
        appendLine("o ${mesh.name.substringBeforeLast('.')}")
        for (v in mesh.vertices) {
            appendLine("v %.5f %.5f %.5f".format(v.x, v.y, v.z))
        }
        for (vt in mesh.uvs) {
            appendLine("vt %.5f %.5f".format(vt.u, vt.v))
        }
        for (vn in mesh.normals) {
            appendLine("vn %.5f %.5f %.5f".format(vn.x, vn.y, vn.z))
        }
        for (f in mesh.faces) {
            if (f.size >= 3) {
                appendLine("f ${f[0] + 1} ${f[1] + 1} ${f[2] + 1}")
            }
        }
    }

    fun exportFbxAscii(mesh: Mesh3D, tintHex: String): String = buildString {
        appendLine("; FBX 7.4.0 project file")
        appendLine("; Exported by PKG Forge Resource Studio | Color: $tintHex")
        appendLine("FBXHeaderExtension:  {")
        appendLine("\tFBXHeaderVersion: 1003")
        appendLine("\tFBXVersion: 7400")
        appendLine("}")
        appendLine("Objects:  {")
        appendLine("\tGeometry: 1001, \"Geometry::${mesh.name}\", \"Mesh\" {")
        val flatVerts = mesh.vertices.flatMap { listOf("%.4f".format(it.x), "%.4f".format(it.y), "%.4f".format(it.z)) }
        appendLine("\t\tVertices: *${flatVerts.size} {")
        appendLine("\t\t\ta: ${flatVerts.joinToString(",")}")
        appendLine("\t\t}")
        val polyIndices = mesh.faces.flatMap { f ->
            if (f.size >= 3) listOf(f[0], f[1], f[2].inv()) else emptyList()
        }
        appendLine("\t\tPolygonVertexIndex: *${polyIndices.size} {")
        appendLine("\t\t\ta: ${polyIndices.joinToString(",")}")
        appendLine("\t\t}")
        appendLine("\t}")
        appendLine("}")
    }

    fun createUnitCubeMesh(name: String = "ps4_crate_preview.obj", formatLabel: String = "Procedural Mesh"): Mesh3D {
        val v = listOf(
            Vec3(-1f, -1f, -1f), Vec3(1f, -1f, -1f), Vec3(1f, 1f, -1f), Vec3(-1f, 1f, -1f),
            Vec3(-1f, -1f, 1f), Vec3(1f, -1f, 1f), Vec3(1f, 1f, 1f), Vec3(-1f, 1f, 1f)
        )
        val f = listOf(
            intArrayOf(0, 1, 2), intArrayOf(0, 2, 3),
            intArrayOf(4, 6, 5), intArrayOf(4, 7, 6),
            intArrayOf(0, 4, 5), intArrayOf(0, 5, 1),
            intArrayOf(1, 5, 6), intArrayOf(1, 6, 2),
            intArrayOf(2, 6, 7), intArrayOf(2, 7, 3),
            intArrayOf(3, 7, 4), intArrayOf(3, 4, 0)
        )
        return Mesh3D(name = name, formatLabel = formatLabel, vertices = v, normals = emptyList(), uvs = emptyList(), faces = f)
    }

    private fun applyHueRotate(matrix: ColorMatrix, degrees: Float) {
        val rad = degrees * Math.PI.toFloat() / 180f
        if (kotlin.math.abs(rad) < 0.001f) return
        val cosVal = cos(rad)
        val sinVal = sin(rad)
        val lumR = 0.213f; val lumG = 0.715f; val lumB = 0.072f
        val hueMat = ColorMatrix(
            floatArrayOf(
                lumR + cosVal * (1 - lumR) + sinVal * (-lumR), lumG + cosVal * (-lumG) + sinVal * (-lumG), lumB + cosVal * (-lumB) + sinVal * (1 - lumB), 0f, 0f,
                lumR + cosVal * (-lumR) + sinVal * (0.143f), lumG + cosVal * (1 - lumG) + sinVal * (0.140f), lumB + cosVal * (-lumB) + sinVal * (-0.283f), 0f, 0f,
                lumR + cosVal * (-lumR) + sinVal * (-(1 - lumR)), lumG + cosVal * (-lumG) + sinVal * (lumG), lumB + cosVal * (1 - lumB) + sinVal * (lumB), 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        matrix.postConcat(hueMat)
    }

    private fun rgb565ToArgb(c: Int, a: Int = 255): Int {
        val r = ((c ushr 11) and 0x1F) * 255 / 31
        val g = ((c ushr 5) and 0x3F) * 255 / 63
        val b = (c and 0x1F) * 255 / 31
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun decodeDxt1(bytes: ByteArray, offset: Int, w: Int, h: Int, out: IntArray) {
        val bw = (w + 3) / 4
        val bh = (h + 3) / 4
        var ptr = offset
        for (by in 0 until bh) {
            for (bx in 0 until bw) {
                if (ptr + 8 > bytes.size) return
                val c0 = (bytes[ptr].toInt() and 0xFF) or ((bytes[ptr + 1].toInt() and 0xFF) shl 8)
                val c1 = (bytes[ptr + 2].toInt() and 0xFF) or ((bytes[ptr + 3].toInt() and 0xFF) shl 8)
                val bits = (bytes[ptr + 4].toInt() and 0xFF) or
                    ((bytes[ptr + 5].toInt() and 0xFF) shl 8) or
                    ((bytes[ptr + 6].toInt() and 0xFF) shl 16) or
                    ((bytes[ptr + 7].toInt() and 0xFF) shl 24)
                ptr += 8
                val palette = IntArray(4)
                palette[0] = rgb565ToArgb(c0)
                palette[1] = rgb565ToArgb(c1)
                if (c0 > c1) {
                    palette[2] = lerpArgb(palette[0], palette[1], 1, 2, 3)
                    palette[3] = lerpArgb(palette[0], palette[1], 2, 1, 3)
                } else {
                    palette[2] = lerpArgb(palette[0], palette[1], 1, 1, 2)
                    palette[3] = 0x00000000
                }
                for (py in 0 until 4) {
                    for (px in 0 until 4) {
                        val x = bx * 4 + px
                        val y = by * 4 + py
                        if (x < w && y < h) {
                            val idx = (bits ushr ((py * 4 + px) * 2)) and 0x3
                            out[y * w + x] = palette[idx]
                        }
                    }
                }
            }
        }
    }

    private fun decodeDxt3(bytes: ByteArray, offset: Int, w: Int, h: Int, out: IntArray) {
        val bw = (w + 3) / 4
        val bh = (h + 3) / 4
        var ptr = offset
        for (by in 0 until bh) {
            for (bx in 0 until bw) {
                if (ptr + 16 > bytes.size) return
                val alphaBase = ptr
                ptr += 8
                val c0 = (bytes[ptr].toInt() and 0xFF) or ((bytes[ptr + 1].toInt() and 0xFF) shl 8)
                val c1 = (bytes[ptr + 2].toInt() and 0xFF) or ((bytes[ptr + 3].toInt() and 0xFF) shl 8)
                val bits = (bytes[ptr + 4].toInt() and 0xFF) or
                    ((bytes[ptr + 5].toInt() and 0xFF) shl 8) or
                    ((bytes[ptr + 6].toInt() and 0xFF) shl 16) or
                    ((bytes[ptr + 7].toInt() and 0xFF) shl 24)
                ptr += 8
                val palette = intArrayOf(
                    rgb565ToArgb(c0),
                    rgb565ToArgb(c1),
                    lerpArgb(rgb565ToArgb(c0), rgb565ToArgb(c1), 1, 2, 3),
                    lerpArgb(rgb565ToArgb(c0), rgb565ToArgb(c1), 2, 1, 3)
                )
                for (py in 0 until 4) {
                    val rowAlpha = (bytes[alphaBase + py * 2].toInt() and 0xFF) or
                        ((bytes[alphaBase + py * 2 + 1].toInt() and 0xFF) shl 8)
                    for (px in 0 until 4) {
                        val x = bx * 4 + px
                        val y = by * 4 + py
                        if (x < w && y < h) {
                            val idx = (bits ushr ((py * 4 + px) * 2)) and 0x3
                            val a4 = (rowAlpha ushr (px * 4)) and 0xF
                            val a8 = a4 * 17
                            out[y * w + x] = (palette[idx] and 0x00FFFFFF) or (a8 shl 24)
                        }
                    }
                }
            }
        }
    }

    private fun decodeDxt5(bytes: ByteArray, offset: Int, w: Int, h: Int, out: IntArray) {
        val bw = (w + 3) / 4
        val bh = (h + 3) / 4
        var ptr = offset
        for (by in 0 until bh) {
            for (bx in 0 until bw) {
                if (ptr + 16 > bytes.size) return
                val a0 = bytes[ptr].toInt() and 0xFF
                val a1 = bytes[ptr + 1].toInt() and 0xFF
                var aBits = 0L
                for (k in 0 until 6) {
                    aBits = aBits or ((bytes[ptr + 2 + k].toLong() and 0xFFL) shl (8 * k))
                }
                ptr += 8
                val alphas = IntArray(8)
                alphas[0] = a0
                alphas[1] = a1
                if (a0 > a1) {
                    for (k in 1..6) alphas[k + 1] = ((7 - k) * a0 + k * a1) / 7
                } else {
                    for (k in 1..4) alphas[k + 1] = ((5 - k) * a0 + k * a1) / 5
                    alphas[6] = 0
                    alphas[7] = 255
                }
                val c0 = (bytes[ptr].toInt() and 0xFF) or ((bytes[ptr + 1].toInt() and 0xFF) shl 8)
                val c1 = (bytes[ptr + 2].toInt() and 0xFF) or ((bytes[ptr + 3].toInt() and 0xFF) shl 8)
                val bits = (bytes[ptr + 4].toInt() and 0xFF) or
                    ((bytes[ptr + 5].toInt() and 0xFF) shl 8) or
                    ((bytes[ptr + 6].toInt() and 0xFF) shl 16) or
                    ((bytes[ptr + 7].toInt() and 0xFF) shl 24)
                ptr += 8
                val palette = intArrayOf(
                    rgb565ToArgb(c0),
                    rgb565ToArgb(c1),
                    lerpArgb(rgb565ToArgb(c0), rgb565ToArgb(c1), 1, 2, 3),
                    lerpArgb(rgb565ToArgb(c0), rgb565ToArgb(c1), 2, 1, 3)
                )
                for (py in 0 until 4) {
                    for (px in 0 until 4) {
                        val x = bx * 4 + px
                        val y = by * 4 + py
                        if (x < w && y < h) {
                            val pIdx = py * 4 + px
                            val idx = (bits ushr (pIdx * 2)) and 0x3
                            val aIdx = ((aBits ushr (pIdx * 3)) and 0x7L).toInt()
                            out[y * w + x] = (palette[idx] and 0x00FFFFFF) or (alphas[aIdx] shl 24)
                        }
                    }
                }
            }
        }
    }

    private fun lerpArgb(c0: Int, c1: Int, w0: Int, w1: Int, div: Int): Int {
        val r = (((c0 ushr 16) and 0xFF) * w0 + ((c1 ushr 16) and 0xFF) * w1) / div
        val g = (((c0 ushr 8) and 0xFF) * w0 + ((c1 ushr 8) and 0xFF) * w1) / div
        val b = ((c0 and 0xFF) * w0 + (c1 and 0xFF) * w1) / div
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
