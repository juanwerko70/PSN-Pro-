package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.UiState
import com.example.engine.FtpFileEntry
import com.example.engine.Ps4MemoryRegion
import com.example.model.ConsoleProfile
import com.example.ui.components.GlassMetricTile
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
import com.example.viewmodel.AdvancedHackerViewModel
import com.example.viewmodel.ConsoleDebugViewModel
import com.example.viewmodel.CrashCapture

@Composable
fun ConsoleDebugScreen(
    viewModel: ConsoleDebugViewModel,
    hackerViewModel: AdvancedHackerViewModel,
    profilesState: UiState<List<ConsoleProfile>>,
    roleName: String,
    canAccessConsole: Boolean,
    onSaveProfile: (ConsoleProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onLog: (String, String, String, String) -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var activeTab by remember { mutableIntStateOf(0) }
    var profileName by remember { mutableStateOf("PS4 Pro Lab") }
    var newDirName by remember { mutableStateOf("") }
    var klogFilter by remember { mutableStateOf("") }
    var pendingDownload by remember { mutableStateOf<FtpFileEntry?>(null) }
    var pendingDumpRegion by remember { mutableStateOf<Ps4MemoryRegion?>(null) }
    var pendingCrashExport by remember { mutableStateOf<CrashCapture?>(null) }

    val uploadPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.uploadLocalFile(context, uri, onLog)
    }
    val downloadSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val entry = pendingDownload
        if (uri != null && entry != null) viewModel.downloadRemoteFile(context, entry, uri, onLog)
        pendingDownload = null
    }
    val payloadPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.sendBinPayload(context, uri, onLog)
    }
    val payloadQueuePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.addPayloadToQueue(context, uri)
    }
    val klogExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(state.klogLines.joinToString("\n").toByteArray(Charsets.UTF_8))
            }
        }
    }
    val crashExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val cap = pendingCrashExport
        if (uri != null && cap != null) {
            val content = "=== CRASH CAPTURE (${cap.timestamp}) ===\nTRIGGER: ${cap.triggerLine}\n\n" +
                cap.contextLines.joinToString("\n")
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(content.toByteArray(Charsets.UTF_8))
            }
        }
        pendingCrashExport = null
    }
    val regionDumper = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val reg = pendingDumpRegion
        if (uri != null && reg != null) viewModel.dumpRegionToUri(context, reg, uri, onLog)
        pendingDumpRegion = null
    }

    if (!canAccessConsole) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            PermissionLockedPanel(roleName = roleName, permissionName = "FTP_CONSOLE")
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Saved Console Profiles
        if (profilesState is UiState.Success && profilesState.data.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                profilesState.data.forEach { prof ->
                    FilterChip(
                        selected = state.host == prof.host && state.ftpPort == prof.port.toInt(),
                        onClick = {
                            viewModel.updateConnectionFields(
                                host = prof.host,
                                ftpPort = prof.port.toInt(),
                                binLoaderPort = prof.binLoaderPort.toInt(),
                                klogPort = prof.klogPort.toInt(),
                                username = prof.username
                            )
                            profileName = prof.name
                        },
                        label = { Text("${prof.name} (${prof.host})") },
                        trailingIcon = {
                            IconButton(
                                onClick = { onDeleteProfile(prof.id) },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(Icons.Rounded.Delete, "Delete profile", Modifier.size(14.dp))
                            }
                        }
                    )
                }
            }
        }

        // Connection Setup + LAN Auto-Radar Card
        LiquidGlassCard(accentColor = if (state.isConnected) EmeraldSuccess else ElectricCyan) {
            SectionHeaderRow(
                title = "PS4 Target Console",
                subtitle = if (state.isConnected) "Connected @ ${state.host}:${state.ftpPort}" else "GoldHEN FTP / BinLoader / PS4Debug",
                icon = Icons.Rounded.Link,
                trailing = {
                    GlassStatusPill(
                        text = if (state.isConnected) "ONLINE" else "IDLE",
                        color = if (state.isConnected) EmeraldSuccess else AmberWarning
                    )
                }
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.host,
                    onValueChange = { viewModel.updateConnectionFields(host = it) },
                    label = { Text("PS4 IP") },
                    singleLine = true,
                    modifier = Modifier.weight(1.4f).testTag("ps4_host_input")
                )
                OutlinedTextField(
                    value = state.ftpPort.toString(),
                    onValueChange = { viewModel.updateConnectionFields(ftpPort = it.toIntOrNull() ?: 2121) },
                    label = { Text("FTP") },
                    singleLine = true,
                    modifier = Modifier.weight(0.7f)
                )
                OutlinedTextField(
                    value = state.klogPort.toString(),
                    onValueChange = { viewModel.updateConnectionFields(klogPort = it.toIntOrNull() ?: 3232) },
                    label = { Text("Klog") },
                    singleLine = true,
                    modifier = Modifier.weight(0.7f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text("Profile Label") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = state.debugPort.toString(),
                    onValueChange = { viewModel.updateConnectionFields(debugPort = it.toIntOrNull() ?: 744) },
                    label = { Text("Debug Port") },
                    singleLine = true,
                    modifier = Modifier.weight(0.7f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (state.isConnected) viewModel.disconnectFtp()
                        else viewModel.connectFtp(onLog)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isConnected) CoralError else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.weight(1f).testTag("ftp_connect_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(if (state.isConnected) Icons.Rounded.LinkOff else Icons.Rounded.Link, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.isConnected) lStr(R.string.ps4_disconnect) else lStr(R.string.ps4_connect))
                }
                OutlinedButton(
                    onClick = { viewModel.scanLanConsoles(onLog) },
                    enabled = !state.isScanningLan,
                    modifier = Modifier.testTag("lan_radar_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Radar, "LAN Radar", Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (state.isScanningLan) "${state.lanScanProgress}/254" else "LAN Radar")
                }
                IconButton(
                    onClick = {
                        onSaveProfile(
                            ConsoleProfile(
                                name = profileName,
                                host = state.host,
                                port = state.ftpPort.toLong(),
                                binLoaderPort = state.binLoaderPort.toLong(),
                                klogPort = state.klogPort.toLong(),
                                username = state.username
                            )
                        )
                    },
                    modifier = Modifier.testTag("save_console_profile_btn")
                ) {
                    Icon(Icons.Rounded.BookmarkAdd, "Save profile", tint = ElectricCyan)
                }
            }

            if (state.discoveredConsoles.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.discoveredConsoles.forEach { found ->
                        GlassStatusPill(
                            text = "${found.host} [${found.openPorts.joinToString(",")}] (${found.latencyMs}ms)",
                            color = EmeraldSuccess,
                            icon = Icons.Rounded.Radar,
                            modifier = Modifier.clickable { viewModel.selectDiscoveredConsole(found) }
                        )
                    }
                }
            }

            if (state.isBusy || state.isScanningLan) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (!state.statusMessage.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(state.statusMessage.orEmpty(), style = MaterialTheme.typography.bodySmall, color = ElectricCyan)
            }
        }

        // Sub-tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                lStr(R.string.ps4_tab_ftp),
                lStr(R.string.ps4_tab_klog),
                lStr(R.string.ps4_tab_proc),
                lStr(R.string.ps4_tab_hack)
            ).forEachIndexed { idx, label ->
                FilterChip(
                    selected = activeTab == idx,
                    onClick = { activeTab = idx },
                    label = { Text(label) },
                    modifier = Modifier.testTag("ps4_subtab_$idx")
                )
            }
        }

        when (activeTab) {
            0 -> {
                if (state.telemetrySamples.isNotEmpty()) {
                    ConsoleTelemetrySparklineCard(
                        samples = state.telemetrySamples,
                        lastThroughputKbps = state.lastThroughputKbps
                    )
                }

                PayloadMacroQueueCard(
                    state = state,
                    onAddPayload = { payloadQueuePicker.launch(arrayOf("*/*")) },
                    onRemovePayload = viewModel::removeQueuedPayload,
                    onClearQueue = viewModel::clearPayloadQueue,
                    onLaunchQueue = { viewModel.launchPayloadQueue(context, onLog) }
                )

                // FTP Explorer
                LiquidGlassCard(accentColor = ElectricCyan) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            IconButton(
                                onClick = { viewModel.navigateUp() },
                                enabled = state.isConnected
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Up")
                            }
                            Text(
                                text = state.currentPath,
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelLarge,
                                color = ElectricCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { viewModel.navigateDirectory(state.currentPath) },
                            enabled = state.isConnected
                        ) {
                            Icon(Icons.Rounded.Refresh, "Refresh")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { uploadPicker.launch(arrayOf("*/*")) },
                            enabled = state.isConnected,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.CloudUpload, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(lStr(R.string.ps4_upload_file))
                        }
                        OutlinedButton(
                            onClick = { payloadPicker.launch(arrayOf("*/*")) },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.RocketLaunch, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(lStr(R.string.ps4_send_payload))
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newDirName,
                            onValueChange = { newDirName = it },
                            label = { Text("New Folder") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                viewModel.createRemoteDir(newDirName)
                                newDirName = ""
                            },
                            enabled = state.isConnected && newDirName.isNotBlank()
                        ) {
                            Icon(Icons.Rounded.CreateNewFolder, "Create Directory", tint = EmeraldSuccess)
                        }
                    }

                    if (state.transferBytes > 0L) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Last transfer: ${state.transferBytes} bytes (${state.lastThroughputKbps} KB/s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldSuccess
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    if (state.entries.isEmpty()) {
                        Text(
                            text = if (state.isConnected) "Directory empty" else "Connect to PS4 FTP server to browse /user, /data, /mnt/usb0.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.entries.forEach { entry ->
                                LiquidGlassCard(
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(10.dp),
                                    onClick = {
                                        if (entry.isDirectory) {
                                            val next = if (state.currentPath.endsWith("/")) {
                                                "${state.currentPath}${entry.name}"
                                            } else {
                                                "${state.currentPath}/${entry.name}"
                                            }
                                            viewModel.navigateDirectory(next)
                                        }
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (entry.isDirectory) Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile,
                                                contentDescription = null,
                                                tint = if (entry.isDirectory) AmberWarning else ElectricCyan
                                            )
                                            Column {
                                                Text(entry.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(
                                                    text = "${entry.permissions} • ${entry.sizeBytes} B",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        if (!entry.isDirectory) {
                                            Row {
                                                IconButton(onClick = {
                                                    pendingDownload = entry
                                                    downloadSaver.launch(entry.name)
                                                }) {
                                                    Icon(Icons.Rounded.Download, lStr(R.string.ps4_download_file), tint = EmeraldSuccess)
                                                }
                                                IconButton(onClick = { viewModel.deleteRemoteFile(entry, onLog) }) {
                                                    Icon(Icons.Rounded.Delete, "Delete", tint = CoralError)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // Real-time Klog Debug Console with Regex & Crash Capture
                LiquidGlassCard(accentColor = VioletAccent) {
                    SectionHeaderRow(
                        title = "Real-Time Console Klog",
                        subtitle = "TCP Stream ${state.host}:${state.klogPort}",
                        icon = Icons.Rounded.Terminal,
                        trailing = {
                            GlassStatusPill(
                                text = if (state.isKlogStreaming) "STREAMING" else "STOPPED",
                                color = if (state.isKlogStreaming) EmeraldSuccess else AmberWarning
                            )
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.toggleKlogStream(onLog) },
                            modifier = Modifier.weight(1f).testTag("klog_toggle_btn"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(if (state.isKlogStreaming) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (state.isKlogStreaming) "Stop Capture" else "Start Klog")
                        }
                        FilterChip(
                            selected = state.klogRegexMode,
                            onClick = { viewModel.toggleKlogRegex() },
                            label = { Text("Regex") },
                            modifier = Modifier.testTag("klog_regex_chip")
                        )
                        OutlinedButton(onClick = { viewModel.clearKlog() }) {
                            Text("Clear")
                        }
                        OutlinedButton(
                            onClick = { klogExporter.launch("ps4_klog.txt") },
                            enabled = state.klogLines.isNotEmpty()
                        ) {
                            Icon(Icons.Rounded.Download, "Export Klog", Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = klogFilter,
                        onValueChange = { klogFilter = it },
                        label = { Text(if (state.klogRegexMode) "Regex filter (e.g. panic|0x[0-9a-f]+)" else "Filter debug output") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF050810))
                            .padding(12.dp)
                    ) {
                        val compiledRegex = remember(klogFilter, state.klogRegexMode) {
                            if (state.klogRegexMode && klogFilter.isNotBlank()) {
                                runCatching { Regex(klogFilter, RegexOption.IGNORE_CASE) }.getOrNull()
                            } else null
                        }
                        val filteredLines = state.klogLines.filter { line ->
                            when {
                                klogFilter.isBlank() -> true
                                compiledRegex != null -> compiledRegex.containsMatchIn(line)
                                else -> line.contains(klogFilter, ignoreCase = true)
                            }
                        }
                        if (filteredLines.isEmpty()) {
                            Text(
                                text = "Waiting for Klog socket stream on ${state.host}:${state.klogPort}...",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldSuccess
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .horizontalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                filteredLines.takeLast(80).forEach { line ->
                                    val lineColor = when {
                                        line.contains("panic", true) || line.contains("err", true) ||
                                            line.contains("fault", true) || line.contains("fail", true) -> CoralError
                                        line.contains("0x", true) || line.contains("goldhen", true) -> ElectricCyan
                                        else -> EmeraldSuccess
                                    }
                                    Text(
                                        text = line,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = lineColor
                                    )
                                }
                            }
                        }
                    }
                }

                if (state.crashCaptures.isNotEmpty()) {
                    LiquidGlassCard(accentColor = CoralError) {
                        SectionHeaderRow(
                            title = "Auto-Captured Crash Snapshots (${state.crashCaptures.size})",
                            subtitle = "Triggered automatically by panic / segfault / fatal patterns",
                            icon = Icons.Rounded.BugReport,
                            trailing = {
                                OutlinedButton(onClick = { viewModel.clearCrashCaptures() }) {
                                    Text("Clear")
                                }
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.crashCaptures.reversed().forEach { cap ->
                                LiquidGlassCard(shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("[${cap.timestamp}] ${cap.triggerLine}", style = MaterialTheme.typography.labelMedium, color = CoralError, maxLines = 2)
                                            Text("${cap.contextLines.size} context lines captured", style = MaterialTheme.typography.labelSmall)
                                        }
                                        IconButton(onClick = {
                                            pendingCrashExport = cap
                                            crashExporter.launch("ps4_crash_${cap.timestamp.replace(":", "")}.txt")
                                        }) {
                                            Icon(Icons.Rounded.Download, "Export crash report", tint = ElectricCyan)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                // Process Control & Memory Telemetry
                LiquidGlassCard(accentColor = IridescentIndigo) {
                    SectionHeaderRow(
                        title = "Process & Memory Telemetry",
                        subtitle = "PS4Debug Daemon (${state.host}:${state.debugPort})",
                        icon = Icons.Rounded.Memory
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.refreshProcesses(onLog) },
                            modifier = Modifier.weight(1f).testTag("refresh_processes_btn"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Fetch Processes")
                        }
                        OutlinedButton(
                            onClick = { viewModel.probeConsolePorts() },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Probe Ports")
                        }
                    }

                    if (state.portProbes.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.portProbes.forEach { probe ->
                                GlassStatusPill(
                                    text = "${probe.serviceName} :${probe.port} (${if (probe.reachable) "${probe.latencyMs}ms" else "CLOSED"})",
                                    color = if (probe.reachable) EmeraldSuccess else CoralError
                                )
                            }
                        }
                    }

                    val mem = state.memorySnapshot
                    if (mem != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "PID ${state.selectedPid} Memory Analysis (${mem.totalRegions} regions — tap region to Peek or Dump .bin)",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GlassMetricTile("Mapped", "${mem.totalMappedKb / 1024} MB", ElectricCyan, Modifier.weight(1f))
                            GlassMetricTile("Exec", "${mem.executableKb / 1024} MB", IridescentIndigo, Modifier.weight(1f))
                            GlassMetricTile("Heap/RW", "${mem.writableHeapKb / 1024} MB", EmeraldSuccess, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(10.dp))
                        MemoryBarChart(
                            execKb = mem.executableKb,
                            heapKb = mem.writableHeapKb,
                            sharedKb = mem.sharedLibKb
                        )
                        Spacer(Modifier.height(10.dp))
                        mem.topRegions.take(10).forEach { r ->
                            val startHex = r.startAddress.toString(16).uppercase()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.updateMemoryEditorFields(peekAddressHex = startHex)
                                        viewModel.peekMemoryAt(startHex)
                                    }
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${r.name} (0x$startHex)",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${r.sizeKb} KB",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricCyan
                                )
                                IconButton(
                                    onClick = {
                                        pendingDumpRegion = r
                                        regionDumper.launch("pid${state.selectedPid}_${r.name}_0x$startHex.bin")
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Rounded.Download, "Dump region to .bin", tint = EmeraldSuccess, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    if (state.processes.isEmpty()) {
                        Text(
                            text = "Tap 'Fetch Processes' while ps4debug payload is active on your console to inspect PIDs, start/resume/stop processes, peek/poke RAM, or dump regions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.processes.forEach { proc ->
                                LiquidGlassCard(
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(10.dp),
                                    accentColor = if (state.selectedPid == proc.pid) EmeraldSuccess else IridescentIndigo,
                                    onClick = { viewModel.inspectProcessMemory(proc.pid) }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(proc.name, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                text = "PID ${proc.pid}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ElectricCyan
                                            )
                                        }
                                        Row {
                                            IconButton(onClick = { viewModel.sendProcessSignal(proc.pid, 18, "RESUME", onLog) }) {
                                                Icon(Icons.Rounded.PlayArrow, "Resume/Start", tint = EmeraldSuccess)
                                            }
                                            IconButton(onClick = { viewModel.sendProcessSignal(proc.pid, 19, "SUSPEND", onLog) }) {
                                                Icon(Icons.Rounded.Pause, "Suspend", tint = AmberWarning)
                                            }
                                            IconButton(onClick = { viewModel.sendProcessSignal(proc.pid, 9, "KILL", onLog) }) {
                                                Icon(Icons.Rounded.Stop, "Kill", tint = CoralError)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                MemoryHackerToolsPanel(
                    state = state,
                    viewModel = viewModel,
                    onLog = onLog
                )
            }

            3 -> {
                ConsoleAdvancedHackerPanel(
                    consoleState = state,
                    hackerViewModel = hackerViewModel,
                    onExecuteRawFtp = viewModel::executeRawFtp,
                    onLog = onLog
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MemoryBarChart(execKb: Long, heapKb: Long, sharedKb: Long) {
    val total = (execKb + heapKb + sharedKb).coerceAtLeast(1L).toFloat()
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0xFF0A1120))
    ) {
        val execW = size.width * (execKb / total)
        val heapW = size.width * (heapKb / total)
        val sharedW = size.width * (sharedKb / total)
        drawRect(IridescentIndigo, Offset.Zero, Size(execW, size.height))
        drawRect(EmeraldSuccess, Offset(execW, 0f), Size(heapW, size.height))
        drawRect(ElectricCyan, Offset(execW + heapW, 0f), Size(sharedW, size.height))
    }
}
