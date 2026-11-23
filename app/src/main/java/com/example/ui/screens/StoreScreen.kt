package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import coil.compose.SubcomposeAsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.UiState
import com.example.engine.StoreEngine
import com.example.model.StoreCategory
import com.example.model.StoreItem
import com.example.ui.components.GlassStatusPill
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.PermissionLockedPanel
import com.example.ui.components.SectionHeaderRow
import com.example.ui.components.lStr
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IridescentIndigo
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.VioletAccent
import com.example.viewmodel.AiModViewModel
import com.example.viewmodel.ConsoleDebugState
import com.example.viewmodel.PkgStudioViewModel
import com.example.viewmodel.StoreViewModel

@Composable
fun StoreScreen(
    storeViewModel: StoreViewModel,
    aiModViewModel: AiModViewModel,
    pkgStudioViewModel: PkgStudioViewModel,
    consoleState: ConsoleDebugState,
    cloudItemsState: UiState<List<StoreItem>>,
    currentUserUid: String,
    isAdmin: Boolean,
    canPublish: Boolean,
    canDeployToConsole: Boolean,
    roleName: String,
    onPublishItem: (StoreItem) -> Unit,
    onDeleteItem: (String) -> Unit,
    onNavigateToPkgStudio: () -> Unit,
    onLog: (String, String, String, String) -> Unit
) {
    val context = LocalContext.current
    val state by storeViewModel.state.collectAsStateWithLifecycle()
    var activeTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<StoreCategory?>(null) }
    var pendingDownloadItem by remember { mutableStateOf<StoreItem?>(null) }

    val fileSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val item = pendingDownloadItem
        if (uri != null && item != null) {
            storeViewModel.downloadToDevice(context, item, uri, onLog)
        }
        pendingDownloadItem = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header + Active PS4 Target Indicator
        LiquidGlassCard(accentColor = ElectricCyan) {
            SectionHeaderRow(
                title = lStr(R.string.store_title),
                subtitle = lStr(R.string.store_subtitle),
                icon = Icons.Rounded.Storefront,
                trailing = {
                    GlassStatusPill(
                        text = "PS4 ${consoleState.host}",
                        color = if (consoleState.isConnected) EmeraldSuccess else ElectricCyan,
                        icon = Icons.Rounded.Router
                    )
                }
            )
            if (state.busyItemId != null) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { state.progressFraction.coerceIn(0.05f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (!state.statusMessage.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.statusMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = ElectricCyan
                )
            }
        }

        // Source Tabs (0 = Internet Catalog, 1 = AI Forge, 2 = Cloud Store, 3 = Publish)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                lStr(R.string.store_tab_feeds),
                lStr(R.string.store_tab_ai),
                lStr(R.string.store_tab_cloud),
                lStr(R.string.store_tab_publish)
            ).forEachIndexed { idx, label ->
                FilterChip(
                    selected = activeTab == idx,
                    onClick = { activeTab = idx },
                    label = { Text(label) },
                    modifier = Modifier.testTag("store_tab_$idx")
                )
            }
        }

        if (activeTab == 0 || activeTab == 2) {
            if (activeTab == 0) {
                LiveFeedSyncCard(
                    activeSourceKey = state.sourceInput,
                    customFeedInput = state.customFeedInput,
                    isSyncing = state.isSyncingFeed,
                    onCustomInputChange = storeViewModel::updateCustomFeedInput,
                    onSync = { src -> storeViewModel.syncExternalSource(src, onLog = onLog) }
                )
            }

            // Search & Category Filter Bar
            LiquidGlassCard(contentPadding = PaddingValues(12.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(lStr(R.string.store_search_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("store_search_input")
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("All") },
                        modifier = Modifier.testTag("store_cat_all")
                    )
                    StoreCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.label) },
                            modifier = Modifier.testTag("store_cat_${cat.name}")
                        )
                    }
                }
            }

            val sourceItems: List<StoreItem> = if (activeTab == 2) {
                when (cloudItemsState) {
                    is UiState.Success -> cloudItemsState.data
                    else -> emptyList()
                }
            } else {
                state.feedItems
            }

            if (activeTab == 2 && cloudItemsState is UiState.Loading) {
                Text("Syncing cloud catalog...", style = MaterialTheme.typography.bodyMedium)
            } else if (activeTab == 2 && cloudItemsState is UiState.Error) {
                Text(cloudItemsState.message, color = CoralError)
            }

            val filtered = sourceItems.filter { item ->
                (selectedCategory == null || item.category == selectedCategory?.name) &&
                    (searchQuery.isBlank() ||
                        item.title.contains(searchQuery, true) ||
                        item.gameTitle.contains(searchQuery, true) ||
                        item.titleId.contains(searchQuery, true) ||
                        item.fileName.contains(searchQuery, true))
            }

            if (filtered.isEmpty()) {
                LiquidGlassCard(accentColor = IridescentIndigo) {
                    Text(
                        text = when {
                            activeTab == 0 && state.isSyncingFeed -> "Loading live resources from GitHub & Poly Haven..."
                            activeTab == 2 -> lStr(R.string.store_empty_cloud)
                            else -> lStr(R.string.store_empty_feed)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (activeTab == 0 && !state.isSyncingFeed) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = {
                            storeViewModel.syncExternalSource(state.sourceInput, onLog = onLog)
                        }) {
                            Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Retry Internet Sync")
                        }
                    }
                }
            } else {
                filtered.forEach { item ->
                    StoreItemCard(
                        item = item,
                        consoleHost = consoleState.host,
                        isBusy = state.busyItemId == item.id,
                        canDelete = activeTab == 2 && (isAdmin || item.authorUid == currentUserUid),
                        canDeploy = canDeployToConsole,
                        onDownload = {
                            pendingDownloadItem = item
                            fileSaver.launch(item.fileName.ifBlank { "asset.bin" })
                        },
                        onDeployToConsole = { asPayload ->
                            storeViewModel.deployToPs4Console(
                                item = item,
                                host = consoleState.host,
                                ftpPort = consoleState.ftpPort,
                                binLoaderPort = consoleState.binLoaderPort,
                                username = consoleState.username,
                                password = consoleState.password,
                                sendAsBinPayload = asPayload,
                                onLog = onLog
                            )
                        },
                        onOpenStudio = {
                            storeViewModel.openInResourceStudio(
                                item = item,
                                pkgStudioViewModel = pkgStudioViewModel,
                                onNavigateToPkgStudio = onNavigateToPkgStudio,
                                onLog = onLog
                            )
                        },
                        onDelete = { onDeleteItem(item.id) }
                    )
                }
            }
        } else if (activeTab == 1) {
            AiModForgePanel(
                aiViewModel = aiModViewModel,
                consoleHost = consoleState.host,
                canDeployToConsole = canDeployToConsole,
                canPublishCloud = canPublish,
                onDownloadItem = { item ->
                    pendingDownloadItem = item
                    fileSaver.launch(item.fileName.ifBlank { "ai_mod.txt" })
                },
                onDeployToConsole = { item ->
                    storeViewModel.deployToPs4Console(
                        item = item,
                        host = consoleState.host,
                        ftpPort = consoleState.ftpPort,
                        binLoaderPort = consoleState.binLoaderPort,
                        username = consoleState.username,
                        password = consoleState.password,
                        sendAsBinPayload = false,
                        onLog = onLog
                    )
                },
                onOpenInStudio = { item ->
                    storeViewModel.openInResourceStudio(
                        item = item,
                        pkgStudioViewModel = pkgStudioViewModel,
                        onNavigateToPkgStudio = onNavigateToPkgStudio,
                        onLog = onLog
                    )
                },
                onPublishToCloud = { item ->
                    onPublishItem(item)
                    activeTab = 2
                },
                onLog = onLog
            )
        } else {
            if (!canPublish) {
                PermissionLockedPanel(roleName = roleName, permissionName = "ADMIN / DEVELOPER")
            } else {
                PublishStoreItemCard(onPublish = {
                    onPublishItem(it)
                    activeTab = 2
                })
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LiveFeedSyncCard(
    activeSourceKey: String,
    customFeedInput: String,
    isSyncing: Boolean,
    onCustomInputChange: (String) -> Unit,
    onSync: (String) -> Unit
) {
    LiquidGlassCard(accentColor = IridescentIndigo) {
        SectionHeaderRow(
            title = "Live Internet PS4 & 3D Catalog",
            subtitle = "Real PKGs, GoldHEN mods/cheats, .bin payloads, CC0 textures & 3D .obj models",
            icon = Icons.Rounded.CloudDownload
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StoreEngine.quickPresets.forEach { preset ->
                FilterChip(
                    selected = activeSourceKey == preset.sourceKey,
                    onClick = {
                        if (!preset.sourceKey.startsWith("internet:") && !preset.sourceKey.startsWith("polyhaven:")) {
                            onCustomInputChange(preset.sourceKey)
                        }
                        onSync(preset.sourceKey)
                    },
                    label = { Text(preset.label) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = customFeedInput,
                onValueChange = onCustomInputChange,
                label = { Text("Custom GitHub owner/repo or https://...json") },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("store_feed_input")
            )
            Button(
                onClick = { onSync(customFeedInput) },
                enabled = !isSyncing && customFeedInput.isNotBlank(),
                modifier = Modifier.testTag("store_feed_sync_btn")
            ) {
                Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Sync")
            }
        }
        if (isSyncing) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun StoreItemCard(
    item: StoreItem,
    consoleHost: String,
    isBusy: Boolean,
    canDelete: Boolean,
    canDeploy: Boolean,
    onDownload: () -> Unit,
    onDeployToConsole: (Boolean) -> Unit,
    onOpenStudio: () -> Unit,
    onDelete: () -> Unit
) {
    val category = StoreCategory.safeValueOf(item.category)
    val accent = categoryAccentColor(category)
    val isStudioEditable = category == StoreCategory.TEXTURE || category == StoreCategory.MODEL_3D
    val isBinPayload = category == StoreCategory.PAYLOAD && item.fileName.endsWith(".bin", true)

    LiquidGlassCard(accentColor = accent) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StoreResourceThumbnail(
                    item = item,
                    category = category,
                    accent = accent
                )
                Column {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.gameTitle} • ${item.titleId} • v${item.version}",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent
                    )
                }
            }
            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete item", tint = CoralError)
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            GlassStatusPill(category.label, accent)
            GlassStatusPill(item.fileName, ElectricCyan)
            if (item.sizeBytes > 0L) {
                GlassStatusPill(formatSize(item.sizeBytes), EmeraldSuccess)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "PS4 Target: ${item.targetConsolePath}",
            fontFamily = JetBrainsMonoFontFamily,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (item.description.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onDeployToConsole(isBinPayload) },
                enabled = !isBusy && canDeploy,
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = if (isBinPayload) Icons.Rounded.RocketLaunch else Icons.Rounded.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (isBinPayload) "Inject -> $consoleHost" else "Import -> PS4 FTP")
            }
            OutlinedButton(
                onClick = onDownload,
                enabled = !isBusy,
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Download")
            }
            if (isStudioEditable) {
                OutlinedButton(
                    onClick = onOpenStudio,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Studio")
                }
            }
        }
    }
}

@Composable
private fun PublishStoreItemCard(
    onPublish: (StoreItem) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(StoreCategory.TEXTURE) }
    var gameTitle by remember { mutableStateOf("") }
    var titleId by remember { mutableStateOf("CUSA00001") }
    var version by remember { mutableStateOf("1.0.0") }
    var fileName by remember { mutableStateOf("custom_texture.dds") }
    var targetPath by remember { mutableStateOf(StoreCategory.TEXTURE.defaultConsoleDir) }
    var downloadUrl by remember { mutableStateOf("") }
    var inlineContent by remember { mutableStateOf("") }
    var sha256 by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    val scriptPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { ins ->
                    val raw = ins.readBytes()
                    if (raw.size <= 15000) {
                        inlineContent = String(raw, Charsets.UTF_8)
                    }
                }
            }
        }
    }

    LiquidGlassCard(accentColor = EmeraldSuccess) {
        SectionHeaderRow(
            title = "Publish to Cloud Store",
            subtitle = "Share textures, mods, scripts, PKGs, payloads or 3D models in real time",
            icon = Icons.Rounded.AddCircle
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StoreCategory.entries.forEach { cat ->
                FilterChip(
                    selected = category == cat,
                    onClick = {
                        category = cat
                        targetPath = cat.defaultConsoleDir
                    },
                    label = { Text(cat.label) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Resource Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("publish_store_title")
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = gameTitle,
                onValueChange = { gameTitle = it },
                label = { Text("Game Name") },
                singleLine = true,
                modifier = Modifier.weight(1.2f)
            )
            OutlinedTextField(
                value = titleId,
                onValueChange = { titleId = it.uppercase().take(16) },
                label = { Text("Title ID") },
                singleLine = true,
                modifier = Modifier.weight(0.8f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = fileName,
                onValueChange = { fileName = it },
                label = { Text("File Name (.dds/.lua/.pkg/.obj)") },
                singleLine = true,
                modifier = Modifier.weight(1.3f)
            )
            OutlinedTextField(
                value = version,
                onValueChange = { version = it },
                label = { Text("Ver") },
                singleLine = true,
                modifier = Modifier.weight(0.7f)
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = targetPath,
            onValueChange = { targetPath = it },
            label = { Text("PS4 Target Directory (e.g. /data/pkg)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = downloadUrl,
            onValueChange = { downloadUrl = it },
            label = { Text("HTTPS Download URL (optional if inline script below)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("publish_store_url")
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Or Inline Script / JSON / OBJ Content:", style = MaterialTheme.typography.labelMedium)
            OutlinedButton(onClick = { scriptPicker.launch(arrayOf("text/*", "*/*")) }) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Load Text File")
            }
        }
        OutlinedTextField(
            value = inlineContent,
            onValueChange = { inlineContent = it.take(15500) },
            label = { Text("Script / Patch / OBJ content") },
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth().testTag("publish_store_inline")
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = sha256,
            onValueChange = { sha256 = it },
            label = { Text("SHA-256 Checksum (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description & Install Notes") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                onPublish(
                    StoreItem(
                        title = title,
                        category = category.name,
                        gameTitle = gameTitle.ifBlank { "PS4 Universal" },
                        titleId = titleId.ifBlank { "CUSA00000" },
                        version = version.ifBlank { "1.0.0" },
                        fileName = fileName.ifBlank { "asset.bin" },
                        targetConsolePath = targetPath,
                        downloadUrl = downloadUrl.trim(),
                        inlineContent = inlineContent,
                        sha256 = sha256.trim(),
                        sizeBytes = if (inlineContent.isNotEmpty()) inlineContent.toByteArray().size.toLong() else 0L,
                        description = description
                    )
                )
            },
            enabled = title.isNotBlank() && (downloadUrl.trim().startsWith("https://") || inlineContent.isNotBlank()),
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("publish_store_submit_btn"),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Publish to Cloud Store", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StoreResourceThumbnail(
    item: StoreItem,
    category: StoreCategory,
    accent: Color
) {
    val imageUrl = item.previewImageUrl.ifBlank {
        if (item.fileName.endsWith(".png", true) || item.fileName.endsWith(".jpg", true)) {
            item.downloadUrl
        } else {
            ""
        }
    }
    val gameBadge = when {
        item.gameTitle.contains("GTA", true) || item.titleId in listOf("CUSA00419", "CUSA00411", "CUSA26616") -> "GTA V"
        item.gameTitle.contains("Call of Duty", true) || item.title.contains("Black Ops", true) || item.title.contains("Modern Warfare", true) -> "COD"
        item.gameTitle.contains("Red Dead", true) || item.titleId == "CUSA03041" -> "RDR2"
        item.gameTitle.contains("Bloodborne", true) || item.titleId == "CUSA00900" -> "BB"
        else -> when (category) {
            StoreCategory.TEXTURE -> "TEX"
            StoreCategory.MOD -> "MOD"
            StoreCategory.SCRIPT -> "SCR"
            StoreCategory.PKG -> "PKG"
            StoreCategory.MODEL_3D -> "3D"
            StoreCategory.PAYLOAD -> "BIN"
        }
    }

    Surface(
        modifier = Modifier.size(72.dp),
        shape = RoundedCornerShape(16.dp),
        color = accent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (imageUrl.startsWith("https://")) {
                SubcomposeAsyncImage(
                    model = imageUrl,
                    contentDescription = "${item.title} preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp)),
                    loading = {
                        ThumbnailFallbackArt(category = category, accent = accent)
                    },
                    error = {
                        ThumbnailFallbackArt(category = category, accent = accent)
                    }
                )
            } else {
                ThumbnailFallbackArt(category = category, accent = accent)
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = gameBadge,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ThumbnailFallbackArt(
    category: StoreCategory,
    accent: Color
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        accent.copy(alpha = 0.30f),
                        IridescentIndigo.copy(alpha = 0.22f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = categoryIcon(category),
            contentDescription = category.label,
            tint = accent,
            modifier = Modifier.size(32.dp)
        )
    }
}

private fun categoryIcon(cat: StoreCategory): ImageVector = when (cat) {
    StoreCategory.TEXTURE -> Icons.Rounded.Image
    StoreCategory.MOD -> Icons.Rounded.Extension
    StoreCategory.SCRIPT -> Icons.Rounded.Code
    StoreCategory.PKG -> Icons.Rounded.Inventory2
    StoreCategory.MODEL_3D -> Icons.Rounded.ViewInAr
    StoreCategory.PAYLOAD -> Icons.Rounded.RocketLaunch
}

private fun categoryAccentColor(cat: StoreCategory): Color = when (cat) {
    StoreCategory.TEXTURE -> ElectricCyan
    StoreCategory.MOD -> VioletAccent
    StoreCategory.SCRIPT -> EmeraldSuccess
    StoreCategory.PKG -> IridescentIndigo
    StoreCategory.MODEL_3D -> AmberWarning
    StoreCategory.PAYLOAD -> CoralError
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "${bytes / (1024L * 1024L)} MB"
    bytes >= 1024L -> "${bytes / 1024L} KB"
    else -> "$bytes B"
}
