package com.example.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.engine.StoreEngine
import com.example.model.StoreCategory
import com.example.model.StoreItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StoreUiState(
    val sourceInput: String = StoreEngine.SOURCE_ALL_INTERNET,
    val customFeedInput: String = "GoldHEN/GoldHEN_Plugins_Repository",
    val isSyncingFeed: Boolean = false,
    val feedItems: List<StoreItem> = emptyList(),
    val busyItemId: String? = null,
    val progressFraction: Float = 0f,
    val statusMessage: String? = null
)

class StoreViewModel : ViewModel() {
    private val _state = MutableStateFlow(StoreUiState())
    val state: StateFlow<StoreUiState> = _state.asStateFlow()

    init {
        syncExternalSource(StoreEngine.SOURCE_ALL_INTERNET) { _, _, _, _ -> }
    }

    fun updateCustomFeedInput(value: String) {
        _state.update { it.copy(customFeedInput = value) }
    }

    fun clearMessage() {
        _state.update { it.copy(statusMessage = null) }
    }

    fun syncExternalSource(
        source: String = _state.value.customFeedInput,
        bearerToken: String = "",
        onLog: (String, String, String, String) -> Unit
    ) {
        val clean = source.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isSyncingFeed = true,
                    sourceInput = clean,
                    statusMessage = "Syncing live internet catalog..."
                )
            }
            val result = when {
                clean == StoreEngine.SOURCE_ALL_INTERNET -> StoreEngine.fetchCuratedInternetCatalog(bearerToken)
                clean == StoreEngine.SOURCE_GTA_MODS -> StoreEngine.fetchGtaVModsBundle(bearerToken)
                clean == StoreEngine.SOURCE_COD_MODS -> StoreEngine.fetchCallOfDutyBundle(bearerToken)
                clean == StoreEngine.SOURCE_PKG_BUNDLES -> StoreEngine.fetchHomebrewPkgBundle(bearerToken)
                clean == StoreEngine.SOURCE_POLYHAVEN_TEXTURES -> StoreEngine.fetchPolyHavenTextures(limit = 20)
                clean.startsWith("https://") -> StoreEngine.fetchJsonManifestFeed(clean)
                else -> StoreEngine.fetchGitHubRepoResources(clean, bearerToken)
            }
            result.onSuccess { items ->
                _state.update {
                    it.copy(
                        isSyncingFeed = false,
                        feedItems = items,
                        statusMessage = "Synced ${items.size} live internet resource(s)"
                    )
                }
                onLog("STORE", "INFO", "STORE_FEED_SYNC", "$clean (${items.size} items)")
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        isSyncingFeed = false,
                        statusMessage = "Internet sync failed: ${err.localizedMessage}"
                    )
                }
                onLog("STORE", "ERROR", "STORE_FEED_FAIL", err.localizedMessage ?: "Network error")
            }
        }
    }

    fun downloadToDevice(
        context: Context,
        item: StoreItem,
        destUri: Uri,
        onLog: (String, String, String, String) -> Unit
    ) {
        viewModelScope.launch {
            _state.update { it.copy(busyItemId = item.id, progressFraction = 0.05f) }
            val res = runCatching {
                context.contentResolver.openOutputStream(destUri)?.use { out ->
                    StoreEngine.downloadItemToStream(item, out) { copied, total ->
                        if (total > 0L) {
                            _state.update { it.copy(progressFraction = (copied.toFloat() / total).coerceIn(0f, 1f)) }
                        }
                    }.getOrThrow()
                } ?: error("Cannot write destination file")
            }
            res.onSuccess { written ->
                _state.update {
                    it.copy(
                        busyItemId = null,
                        progressFraction = 0f,
                        statusMessage = "Saved ${item.fileName} ($written bytes)"
                    )
                }
                onLog("STORE", "INFO", "STORE_DOWNLOAD", "${item.fileName} ($written bytes)")
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        busyItemId = null,
                        progressFraction = 0f,
                        statusMessage = "Download error: ${err.localizedMessage}"
                    )
                }
            }
        }
    }

    fun deployToPs4Console(
        item: StoreItem,
        host: String,
        ftpPort: Int,
        binLoaderPort: Int,
        username: String,
        password: String,
        sendAsBinPayload: Boolean,
        onLog: (String, String, String, String) -> Unit
    ) {
        viewModelScope.launch {
            _state.update { it.copy(busyItemId = item.id, progressFraction = 0.1f) }
            StoreEngine.deployItemToConsole(
                item = item,
                host = host,
                ftpPort = ftpPort,
                binLoaderPort = binLoaderPort,
                username = username,
                password = password,
                sendAsBinPayload = sendAsBinPayload
            ) { copied, total ->
                if (total > 0L) {
                    _state.update { it.copy(progressFraction = (copied.toFloat() / total).coerceIn(0f, 1f)) }
                }
            }.onSuccess { msg ->
                _state.update {
                    it.copy(
                        busyItemId = null,
                        progressFraction = 0f,
                        statusMessage = msg
                    )
                }
                onLog("STORE", "INFO", "STORE_DEPLOY_PS4", msg.take(200))
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        busyItemId = null,
                        progressFraction = 0f,
                        statusMessage = "PS4 deploy failed: ${err.localizedMessage}"
                    )
                }
                onLog("STORE", "ERROR", "STORE_DEPLOY_FAIL", "${item.fileName}: ${err.localizedMessage}")
            }
        }
    }

    fun openInResourceStudio(
        item: StoreItem,
        pkgStudioViewModel: PkgStudioViewModel,
        onNavigateToPkgStudio: () -> Unit,
        onLog: (String, String, String, String) -> Unit
    ) {
        viewModelScope.launch {
            _state.update { it.copy(busyItemId = item.id, progressFraction = 0.2f) }
            StoreEngine.downloadItemBytes(item, maxBytes = 16 * 1024 * 1024) { copied, total ->
                if (total > 0L) {
                    _state.update { it.copy(progressFraction = (copied.toFloat() / total).coerceIn(0f, 1f)) }
                }
            }.onSuccess { bytes ->
                _state.update { it.copy(busyItemId = null, progressFraction = 0f) }
                when (StoreCategory.safeValueOf(item.category)) {
                    StoreCategory.TEXTURE -> {
                        pkgStudioViewModel.importTextureBytes(item.fileName, bytes, onLog)
                        onNavigateToPkgStudio()
                    }
                    StoreCategory.MODEL_3D -> {
                        pkgStudioViewModel.importMeshBytes(item.fileName, bytes, onLog)
                        onNavigateToPkgStudio()
                    }
                    else -> {
                        _state.update { it.copy(statusMessage = "Downloaded ${bytes.size} bytes (${item.fileName})") }
                    }
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        busyItemId = null,
                        progressFraction = 0f,
                        statusMessage = "Studio import failed: ${err.localizedMessage}"
                    )
                }
            }
        }
    }
}
