package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.engine.Mesh3D
import com.example.engine.ParamSfoCodec
import com.example.engine.PkgEntryInfo
import com.example.engine.PkgHeaderInfo
import com.example.engine.PkgParser
import com.example.engine.ResourceStudioCodec
import com.example.engine.SfoDiagnosticIssue
import com.example.engine.SfoDocument
import com.example.engine.SfoEntry
import com.example.engine.SfoValueFormat
import com.example.engine.TextureTransformParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PkgStudioState(
    val isLoading: Boolean = false,
    val pkgUri: Uri? = null,
    val pkgHeader: PkgHeaderInfo? = null,
    val sfoDoc: SfoDocument? = null,
    val sfoIssues: List<SfoDiagnosticIssue> = emptyList(),
    val patchedContentId: String = "",
    val hexOffset: Long = 0L,
    val hexDump: String = "",
    val statusMessage: String? = null,
    // Resource Studio - Texture
    val textureName: String = "",
    val textureFormat: String = "",
    val sourceBitmap: Bitmap? = null,
    val previewBitmap: Bitmap? = null,
    val textureParams: TextureTransformParams = TextureTransformParams(),
    // Resource Studio - 3D Mesh
    val sourceMesh: Mesh3D? = null,
    val previewMesh: Mesh3D? = null,
    val meshScaleX: Float = 1f,
    val meshScaleY: Float = 1f,
    val meshScaleZ: Float = 1f,
    val meshRotX: Float = 20f,
    val meshRotY: Float = 35f,
    val meshRotZ: Float = 0f,
    val meshTintHex: String = "#38BDF8"
)

class PkgStudioViewModel : ViewModel() {
    private val _state = MutableStateFlow(PkgStudioState())
    val state: StateFlow<PkgStudioState> = _state.asStateFlow()

    fun clearMessage() {
        _state.update { it.copy(statusMessage = null) }
    }

    fun importPkg(context: Context, uri: Uri, onLog: (String, String, String, String) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, statusMessage = null) }
            val res = withContext(Dispatchers.IO) { PkgParser.parseFromUri(context, uri) }
            res.onSuccess { header ->
                val hex = PkgParser.formatHexDump(header.hexPreviewBytes, header.hexPreviewOffset)
                var loadedSfo: SfoDocument? = null
                var sfoIssues = emptyList<SfoDiagnosticIssue>()

                val sfoEntry = header.entries.firstOrNull { it.id == 0x1000L || it.name.equals("param.sfo", true) }
                if (sfoEntry != null && !sfoEntry.encrypted) {
                    withContext(Dispatchers.IO) {
                        PkgParser.readEntryBytes(context, uri, sfoEntry)
                    }.onSuccess { sfoBytes ->
                        runCatching { ParamSfoCodec.parse(sfoBytes, "${header.fileName}/param.sfo") }
                            .onSuccess { doc ->
                                loadedSfo = doc
                                sfoIssues = ParamSfoCodec.validateParameters(doc)
                            }
                    }
                }

                // Auto-preview icon0.png if unencrypted
                val iconEntry = header.entries.firstOrNull { it.id == 0x1200L || it.name.equals("icon0.png", true) }
                var decodedTexBitmap: Bitmap? = _state.value.sourceBitmap
                var decodedTexName = _state.value.textureName
                var decodedTexFmt = _state.value.textureFormat
                if (iconEntry != null && !iconEntry.encrypted) {
                    withContext(Dispatchers.IO) {
                        PkgParser.readEntryBytes(context, uri, iconEntry)
                    }.onSuccess { bytes ->
                        ResourceStudioCodec.decodeTexture(bytes, iconEntry.name).onSuccess { tex ->
                            decodedTexBitmap = tex.bitmap
                            decodedTexName = tex.name
                            decodedTexFmt = tex.formatLabel
                        }
                    }
                }

                _state.update {
                    it.copy(
                        isLoading = false,
                        pkgUri = uri,
                        pkgHeader = header,
                        patchedContentId = header.contentId,
                        sfoDoc = loadedSfo ?: it.sfoDoc,
                        sfoIssues = if (loadedSfo != null) sfoIssues else it.sfoIssues,
                        hexOffset = 0L,
                        hexDump = hex,
                        sourceBitmap = decodedTexBitmap,
                        previewBitmap = decodedTexBitmap?.let { bmp ->
                            ResourceStudioCodec.applyTextureTransforms(bmp, it.textureParams)
                        },
                        textureName = decodedTexName,
                        textureFormat = decodedTexFmt,
                        statusMessage = "Loaded ${header.fileName} (${header.entries.size} entries)"
                    )
                }
                onLog("PKG", "INFO", "IMPORTED_PKG", "${header.fileName} (${header.contentId})")
            }.onFailure { err ->
                _state.update { it.copy(isLoading = false, statusMessage = err.localizedMessage ?: "PKG parse failed") }
                onLog("PKG", "ERROR", "PKG_PARSE_ERROR", err.localizedMessage ?: "Unknown error")
            }
        }
    }

    fun importStandaloneSfo(context: Context, uri: Uri, onLog: (String, String, String, String) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Cannot read SFO file")
                    val name = PkgParser.resolveFileName(context, uri)
                    ParamSfoCodec.parse(bytes, name)
                }
            }
            res.onSuccess { doc ->
                val issues = ParamSfoCodec.validateParameters(doc)
                _state.update {
                    it.copy(
                        isLoading = false,
                        sfoDoc = doc,
                        sfoIssues = issues,
                        statusMessage = "Loaded ${doc.sourceLabel} (${doc.entries.size} parameters)"
                    )
                }
                onLog("PKG", "INFO", "IMPORTED_SFO", "${doc.sourceLabel} (${doc.entries.size} keys)")
            }.onFailure { err ->
                _state.update { it.copy(isLoading = false, statusMessage = err.localizedMessage) }
            }
        }
    }

    fun createTemplateSfo(onLog: (String, String, String, String) -> Unit) {
        val doc = ParamSfoCodec.createDefaultPs4Template()
        _state.update {
            it.copy(
                sfoDoc = doc,
                sfoIssues = ParamSfoCodec.validateParameters(doc),
                statusMessage = "Created PS4 PARAM.SFO template"
            )
        }
        onLog("PKG", "INFO", "CREATED_SFO_TEMPLATE", "CUSA00001")
    }

    fun updateSfoEntry(key: String, newValue: String) {
        val current = _state.value.sfoDoc ?: return
        val updated = current.entries.map { entry ->
            if (entry.key == key) {
                if (entry.format == SfoValueFormat.INT32) {
                    val parsed = if (newValue.trim().startsWith("0x", true)) {
                        newValue.trim().substring(2).toLongOrNull(16) ?: entry.intValue
                    } else {
                        newValue.trim().toLongOrNull() ?: entry.intValue
                    }
                    entry.copy(stringValue = newValue, intValue = parsed)
                } else {
                    entry.copy(stringValue = newValue)
                }
            } else entry
        }
        val doc = current.copy(entries = updated)
        _state.update { it.copy(sfoDoc = doc, sfoIssues = ParamSfoCodec.validateParameters(doc)) }
    }

    fun addSfoEntry(key: String, format: SfoValueFormat, value: String) {
        val cleanKey = key.trim().uppercase().replace(" ", "_")
        if (cleanKey.isBlank()) return
        val current = _state.value.sfoDoc ?: ParamSfoCodec.createDefaultPs4Template()
        val intVal = if (format == SfoValueFormat.INT32) {
            value.removePrefix("0x").toLongOrNull(16) ?: value.toLongOrNull() ?: 0L
        } else 0L
        val next = current.copy(
            entries = current.entries.filterNot { it.key == cleanKey } +
                SfoEntry(cleanKey, format, maxOf(64, value.length + 1), value, intVal)
        )
        _state.update {
            it.copy(
                sfoDoc = next,
                sfoIssues = ParamSfoCodec.validateParameters(next),
                statusMessage = "Added SFO key $cleanKey"
            )
        }
    }

    fun removeSfoEntry(key: String) {
        val current = _state.value.sfoDoc ?: return
        val next = current.copy(entries = current.entries.filterNot { it.key == key })
        _state.update { it.copy(sfoDoc = next, sfoIssues = ParamSfoCodec.validateParameters(next)) }
    }

    fun applyFirmwareBackport(targetSdkHex: String, onLog: (String, String, String, String) -> Unit) {
        val current = _state.value.sfoDoc ?: return
        val patched = ParamSfoCodec.patchSystemVersion(current, targetSdkHex)
        _state.update {
            it.copy(
                sfoDoc = patched,
                sfoIssues = ParamSfoCodec.validateParameters(patched),
                statusMessage = "Patched SYSTEM_VER & PUBTOOLINFO to 0x$targetSdkHex"
            )
        }
        onLog("PKG", "INFO", "PATCHED_SDK_VER", "Target 0x$targetSdkHex")
    }

    fun updatePatchedContentId(cid: String) {
        _state.update { it.copy(patchedContentId = cid.take(36)) }
    }

    fun exportSfo(context: Context, destUri: Uri, onLog: (String, String, String, String) -> Unit) {
        val doc = _state.value.sfoDoc ?: return
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = ParamSfoCodec.serialize(doc)
                    context.contentResolver.openOutputStream(destUri)?.use { it.write(bytes) }
                        ?: error("Cannot open output stream")
                    bytes.size
                }
            }
            res.onSuccess { size ->
                _state.update { it.copy(statusMessage = "Exported PARAM.SFO ($size bytes)") }
                onLog("PKG", "INFO", "EXPORTED_SFO", "$size bytes")
            }.onFailure { err ->
                _state.update { it.copy(statusMessage = "Export failed: ${err.localizedMessage}") }
            }
        }
    }

    fun exportPatchedPkg(context: Context, destUri: Uri, onLog: (String, String, String, String) -> Unit) {
        val srcUri = _state.value.pkgUri ?: return
        val header = _state.value.pkgHeader ?: return
        val sfoBytes = _state.value.sfoDoc?.let { runCatching { ParamSfoCodec.serialize(it) }.getOrNull() }
        val sfoEntry = header.entries.firstOrNull { it.id == 0x1000L || it.name.equals("param.sfo", true) }
        val cid = _state.value.patchedContentId.takeIf { it.isNotBlank() && it != header.contentId }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val res = withContext(Dispatchers.IO) {
                PkgParser.exportPatchedPkgCopy(context, srcUri, destUri, cid, sfoBytes, sfoEntry)
            }
            res.onSuccess { msg ->
                _state.update { it.copy(isLoading = false, statusMessage = msg) }
                onLog("PKG", "INFO", "EXPORTED_PATCHED_PKG", msg)
            }.onFailure { err ->
                _state.update { it.copy(isLoading = false, statusMessage = err.localizedMessage) }
            }
        }
    }

    fun loadEntryIntoStudio(context: Context, entry: PkgEntryInfo, onLog: (String, String, String, String) -> Unit) {
        val uri = _state.value.pkgUri ?: return
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) { PkgParser.readEntryBytes(context, uri, entry) }
            res.onSuccess { bytes ->
                when {
                    entry.name.endsWith(".png", true) || entry.name.endsWith(".dds", true) -> {
                        ResourceStudioCodec.decodeTexture(bytes, entry.name).onSuccess { tex ->
                            _state.update {
                                it.copy(
                                    textureName = tex.name,
                                    textureFormat = tex.formatLabel,
                                    sourceBitmap = tex.bitmap,
                                    previewBitmap = ResourceStudioCodec.applyTextureTransforms(tex.bitmap, it.textureParams),
                                    statusMessage = "Loaded ${entry.name} into Texture Studio"
                                )
                            }
                            onLog("PKG", "INFO", "EXTRACTED_TEXTURE", entry.name)
                        }.onFailure { err ->
                            _state.update { it.copy(statusMessage = err.localizedMessage) }
                        }
                    }
                    entry.name.endsWith(".obj", true) -> {
                        val mesh = ResourceStudioCodec.parseObj(String(bytes, Charsets.UTF_8), entry.name)
                        applyMeshUpdate(mesh)
                        onLog("PKG", "INFO", "EXTRACTED_MESH", entry.name)
                    }
                    else -> {
                        _state.update {
                            it.copy(
                                hexOffset = entry.offset,
                                hexDump = PkgParser.formatHexDump(bytes.copyOfRange(0, minOf(512, bytes.size)), entry.offset),
                                statusMessage = "Loaded ${entry.name} into Hex Inspector"
                            )
                        }
                    }
                }
            }.onFailure { err ->
                _state.update { it.copy(statusMessage = err.localizedMessage) }
            }
        }
    }

    fun loadHexWindow(context: Context, newOffset: Long) {
        val uri = _state.value.pkgUri ?: return
        val safeOffset = newOffset.coerceAtLeast(0L)
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) { PkgParser.readHexSlice(context, uri, safeOffset, 256) }
            res.onSuccess { bytes ->
                _state.update {
                    it.copy(
                        hexOffset = safeOffset,
                        hexDump = PkgParser.formatHexDump(bytes, safeOffset)
                    )
                }
            }
        }
    }

    fun extractEntryToUri(context: Context, entry: PkgEntryInfo, destUri: Uri, onLog: (String, String, String, String) -> Unit) {
        val srcUri = _state.value.pkgUri ?: return
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                PkgParser.readEntryBytes(context, srcUri, entry, maxBytes = 16 * 1024 * 1024).mapCatching { bytes ->
                    context.contentResolver.openOutputStream(destUri)?.use { it.write(bytes) }
                        ?: error("Cannot write extracted entry")
                    bytes.size
                }
            }
            res.onSuccess { sz ->
                _state.update { it.copy(statusMessage = "Extracted ${entry.name} ($sz bytes)") }
                onLog("PKG", "INFO", "EXTRACTED_ENTRY", "${entry.name} ($sz bytes)")
            }.onFailure { err ->
                _state.update { it.copy(statusMessage = err.localizedMessage) }
            }
        }
    }

    // --- Resource Studio: Textures (DDS / PNG) ---
    fun importTexture(context: Context, uri: Uri, onLog: (String, String, String, String) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Cannot read texture")
                    val name = PkgParser.resolveFileName(context, uri)
                    ResourceStudioCodec.decodeTexture(bytes, name).getOrThrow()
                }
            }
            res.onSuccess { tex ->
                val defaultParams = TextureTransformParams()
                _state.update {
                    it.copy(
                        isLoading = false,
                        textureName = tex.name,
                        textureFormat = tex.formatLabel,
                        sourceBitmap = tex.bitmap,
                        textureParams = defaultParams,
                        previewBitmap = tex.bitmap,
                        statusMessage = "Loaded ${tex.name} (${tex.bitmap.width}x${tex.bitmap.height} ${tex.formatLabel})"
                    )
                }
                onLog("PKG", "INFO", "IMPORTED_TEXTURE", "${tex.name} (${tex.formatLabel})")
            }.onFailure { err ->
                _state.update { it.copy(isLoading = false, statusMessage = err.localizedMessage) }
            }
        }
    }

    fun importTextureBytes(name: String, bytes: ByteArray, onLog: (String, String, String, String) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val res = withContext(Dispatchers.IO) {
                ResourceStudioCodec.decodeTexture(bytes, name)
            }
            res.onSuccess { tex ->
                val defaultParams = TextureTransformParams()
                _state.update {
                    it.copy(
                        isLoading = false,
                        textureName = tex.name,
                        textureFormat = tex.formatLabel,
                        sourceBitmap = tex.bitmap,
                        textureParams = defaultParams,
                        previewBitmap = tex.bitmap,
                        statusMessage = "Loaded ${tex.name} (${tex.bitmap.width}x${tex.bitmap.height} ${tex.formatLabel})"
                    )
                }
                onLog("PKG", "INFO", "IMPORTED_TEXTURE", "${tex.name} (${tex.formatLabel})")
            }.onFailure { err ->
                _state.update { it.copy(isLoading = false, statusMessage = err.localizedMessage) }
            }
        }
    }

    fun updateTextureParams(transform: (TextureTransformParams) -> TextureTransformParams) {
        val current = _state.value
        val nextParams = transform(current.textureParams)
        _state.update { it.copy(textureParams = nextParams) }
        val src = current.sourceBitmap ?: return
        viewModelScope.launch {
            val rendered = withContext(Dispatchers.Default) {
                ResourceStudioCodec.applyTextureTransforms(src, nextParams)
            }
            _state.update { it.copy(previewBitmap = rendered) }
        }
    }

    fun exportTexture(context: Context, destUri: Uri, asDds: Boolean, onLog: (String, String, String, String) -> Unit) {
        val bmp = _state.value.previewBitmap ?: _state.value.sourceBitmap ?: return
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = if (asDds) {
                        ResourceStudioCodec.encodeDdsRgba8(bmp)
                    } else {
                        ResourceStudioCodec.encodePng(bmp)
                    }
                    context.contentResolver.openOutputStream(destUri)?.use { it.write(bytes) }
                        ?: error("Cannot open output stream")
                    bytes.size
                }
            }
            res.onSuccess { size ->
                val fmt = if (asDds) "DDS" else "PNG"
                _state.update { it.copy(statusMessage = "Exported $fmt texture ($size bytes)") }
                onLog("PKG", "INFO", "EXPORTED_TEXTURE", "$fmt ($size bytes)")
            }.onFailure { err ->
                _state.update { it.copy(statusMessage = err.localizedMessage) }
            }
        }
    }

    // --- Resource Studio: 3D Models (OBJ / FBX) ---
    fun import3dModel(context: Context, uri: Uri, onLog: (String, String, String, String) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Cannot read 3D model")
                    val name = PkgParser.resolveFileName(context, uri)
                    if (name.endsWith(".fbx", true)) {
                        ResourceStudioCodec.parseFbx(bytes, name).getOrThrow()
                    } else {
                        ResourceStudioCodec.parseObj(String(bytes, Charsets.UTF_8), name)
                    }
                }
            }
            res.onSuccess { mesh ->
                _state.update { it.copy(isLoading = false) }
                applyMeshUpdate(mesh)
                _state.update { it.copy(statusMessage = "Loaded ${mesh.name} (${mesh.vertices.size} verts, ${mesh.faces.size} tris)") }
                onLog("PKG", "INFO", "IMPORTED_3D_MODEL", "${mesh.name} (${mesh.formatLabel})")
            }.onFailure { err ->
                _state.update { it.copy(isLoading = false, statusMessage = err.localizedMessage) }
            }
        }
    }

    fun importMeshBytes(name: String, bytes: ByteArray, onLog: (String, String, String, String) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    if (name.endsWith(".fbx", true)) {
                        ResourceStudioCodec.parseFbx(bytes, name).getOrThrow()
                    } else {
                        ResourceStudioCodec.parseObj(String(bytes, Charsets.UTF_8), name)
                    }
                }
            }
            res.onSuccess { mesh ->
                _state.update { it.copy(isLoading = false) }
                applyMeshUpdate(mesh)
                _state.update { it.copy(statusMessage = "Loaded ${mesh.name} (${mesh.vertices.size} verts, ${mesh.faces.size} tris)") }
                onLog("PKG", "INFO", "IMPORTED_3D_MODEL", "${mesh.name} (${mesh.formatLabel})")
            }.onFailure { err ->
                _state.update { it.copy(isLoading = false, statusMessage = err.localizedMessage) }
            }
        }
    }

    fun loadSample3dCrate() {
        val mesh = ResourceStudioCodec.createUnitCubeMesh()
        applyMeshUpdate(mesh)
        _state.update { it.copy(statusMessage = "Loaded 3D mesh workspace (${mesh.vertices.size} verts)") }
    }

    fun updateMeshTransform(
        scaleX: Float = _state.value.meshScaleX,
        scaleY: Float = _state.value.meshScaleY,
        scaleZ: Float = _state.value.meshScaleZ,
        rotX: Float = _state.value.meshRotX,
        rotY: Float = _state.value.meshRotY,
        rotZ: Float = _state.value.meshRotZ,
        tintHex: String = _state.value.meshTintHex
    ) {
        val src = _state.value.sourceMesh
        _state.update {
            it.copy(
                meshScaleX = scaleX,
                meshScaleY = scaleY,
                meshScaleZ = scaleZ,
                meshRotX = rotX,
                meshRotY = rotY,
                meshRotZ = rotZ,
                meshTintHex = tintHex,
                previewMesh = src?.let { m ->
                    ResourceStudioCodec.transformMesh(m, scaleX, scaleY, scaleZ, rotX, rotY, rotZ)
                }
            )
        }
    }

    fun export3dModel(context: Context, destUri: Uri, asFbx: Boolean, onLog: (String, String, String, String) -> Unit) {
        val mesh = _state.value.previewMesh ?: _state.value.sourceMesh ?: return
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val content = if (asFbx) {
                        ResourceStudioCodec.exportFbxAscii(mesh, _state.value.meshTintHex)
                    } else {
                        ResourceStudioCodec.exportObj(mesh, _state.value.meshTintHex)
                    }
                    context.contentResolver.openOutputStream(destUri)?.use {
                        it.write(content.toByteArray(Charsets.UTF_8))
                    } ?: error("Cannot write 3D model")
                    content.length
                }
            }
            res.onSuccess { len ->
                val fmt = if (asFbx) "FBX" else "OBJ"
                _state.update { it.copy(statusMessage = "Exported $fmt model ($len chars)") }
                onLog("PKG", "INFO", "EXPORTED_3D_MODEL", "$fmt ($len chars)")
            }.onFailure { err ->
                _state.update { it.copy(statusMessage = err.localizedMessage) }
            }
        }
    }

    private fun applyMeshUpdate(mesh: Mesh3D) {
        val st = _state.value
        _state.update {
            it.copy(
                sourceMesh = mesh,
                previewMesh = ResourceStudioCodec.transformMesh(
                    mesh, st.meshScaleX, st.meshScaleY, st.meshScaleZ, st.meshRotX, st.meshRotY, st.meshRotZ
                )
            )
        }
    }
}
