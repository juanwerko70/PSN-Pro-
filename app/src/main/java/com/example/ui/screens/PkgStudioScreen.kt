package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.engine.PkgEntryInfo
import com.example.engine.SfoValueFormat
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
import com.example.viewmodel.PkgStudioState
import com.example.viewmodel.PkgStudioViewModel

@Composable
fun PkgStudioScreen(
    viewModel: PkgStudioViewModel,
    roleName: String,
    canInspect: Boolean,
    canModify: Boolean,
    onLog: (String, String, String, String) -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var activeTab by remember { mutableIntStateOf(0) }
    var entryFilter by remember { mutableStateOf("") }
    var pendingExtractEntry by remember { mutableStateOf<PkgEntryInfo?>(null) }

    val pkgPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importPkg(context, uri, onLog)
    }
    val sfoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importStandaloneSfo(context, uri, onLog)
    }
    val sfoExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) viewModel.exportSfo(context, uri, onLog)
    }
    val pkgPatchExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) viewModel.exportPatchedPkg(context, uri, onLog)
    }
    val entryExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val entry = pendingExtractEntry
        if (uri != null && entry != null) {
            viewModel.extractEntryToUri(context, entry, uri, onLog)
        }
        pendingExtractEntry = null
    }

    if (!canInspect) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            PermissionLockedPanel(roleName = roleName, permissionName = "PKG_INSPECT")
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
        // Action Bar
        LiquidGlassCard(contentPadding = PaddingValues(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { pkgPicker.launch(arrayOf("*/*")) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("import_pkg_button")
                ) {
                    Icon(Icons.Rounded.FolderOpen, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(lStr(R.string.pkg_import_btn))
                }
                OutlinedButton(
                    onClick = { sfoPicker.launch(arrayOf("*/*")) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("import_sfo_button")
                ) {
                    Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(lStr(R.string.pkg_import_sfo_btn))
                }
                OutlinedButton(
                    onClick = { viewModel.createTemplateSfo(onLog) },
                    enabled = canModify,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("create_sfo_button")
                ) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(lStr(R.string.pkg_create_sfo_btn))
                }
            }
            if (state.isLoading) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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

        // Sub-tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val tabs = listOf(
                lStr(R.string.pkg_tab_inspector),
                lStr(R.string.pkg_tab_sfo),
                lStr(R.string.pkg_tab_devfix),
                lStr(R.string.pkg_tab_resources)
            )
            tabs.forEachIndexed { idx, label ->
                FilterChip(
                    selected = activeTab == idx,
                    onClick = { activeTab = idx },
                    label = { Text(label) },
                    modifier = Modifier.testTag("pkg_subtab_$idx")
                )
            }
        }

        when (activeTab) {
            0 -> {
                val hdr = state.pkgHeader
                if (hdr == null) {
                    LiquidGlassCard(accentColor = IridescentIndigo) {
                        SectionHeaderRow(
                            title = lStr(R.string.pkg_empty_title),
                            subtitle = lStr(R.string.pkg_empty_desc),
                            icon = Icons.Rounded.Inventory2
                        )
                    }
                } else {
                    LiquidGlassCard(accentColor = ElectricCyan) {
                        SectionHeaderRow(
                            title = hdr.fileName,
                            subtitle = hdr.contentId.ifBlank { "No Content ID" },
                            icon = Icons.Rounded.Inventory2,
                            trailing = { GlassStatusPill(hdr.magicHex, EmeraldSuccess) }
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GlassMetricTile("Size", "${hdr.fileSizeBytes / (1024 * 1024)} MB", ElectricCyan, Modifier.weight(1f))
                            GlassMetricTile("Entries", "${hdr.entries.size}", IridescentIndigo, Modifier.weight(1f))
                            GlassMetricTile("CRC32", hdr.headerCrc32, EmeraldSuccess, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Type: ${hdr.contentTypeLabel} • Flags: 0x${hdr.pkgFlags.toString(16).uppercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "SHA-256 (Header): ${hdr.headerSha256.take(32)}...",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                    }

                    LiquidGlassCard(accentColor = IridescentIndigo) {
                        SectionHeaderRow(
                            title = "PKG Entry Table (${hdr.entries.size})",
                            icon = Icons.Rounded.Code
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = entryFilter,
                            onValueChange = { entryFilter = it },
                            label = { Text("Filter entries (e.g. param.sfo, icon0.png, .dds)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        val filtered = hdr.entries.filter {
                            entryFilter.isBlank() ||
                                it.name.contains(entryFilter, ignoreCase = true) ||
                                it.idHex.contains(entryFilter, ignoreCase = true)
                        }.take(80)

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            filtered.forEach { entry ->
                                LiquidGlassCard(
                                    shape = RoundedCornerShape(16.dp),
                                    contentPadding = PaddingValues(12.dp),
                                    accentColor = if (entry.encrypted) AmberWarning else EmeraldSuccess
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = entry.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${entry.idHex} • ${entry.size} B @ 0x${entry.offset.toString(16).uppercase()}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (entry.encrypted) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                                contentDescription = null,
                                                tint = if (entry.encrypted) AmberWarning else EmeraldSuccess,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            if (!entry.encrypted && entry.size > 0) {
                                                IconButton(onClick = {
                                                    viewModel.loadEntryIntoStudio(context, entry, onLog)
                                                    if (entry.name.endsWith(".png", true) || entry.name.endsWith(".dds", true) || entry.name.endsWith(".obj", true)) {
                                                        activeTab = 3
                                                    } else {
                                                        activeTab = 2
                                                    }
                                                }) {
                                                    Icon(Icons.Rounded.EditNote, "Inspect/Edit", tint = ElectricCyan)
                                                }
                                                IconButton(onClick = {
                                                    pendingExtractEntry = entry
                                                    entryExporter.launch(entry.name.substringAfterLast('/'))
                                                }) {
                                                    Icon(Icons.Rounded.Download, "Extract", tint = EmeraldSuccess)
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
                SfoEditorTab(
                    state = state,
                    canModify = canModify,
                    viewModel = viewModel,
                    onExportSfo = { sfoExporter.launch("param.sfo") },
                    onExportPatchedPkg = {
                        val name = state.pkgHeader?.fileName?.substringBeforeLast('.') ?: "patched"
                        pkgPatchExporter.launch("${name}_patched.pkg")
                    },
                    onLog = onLog
                )
            }

            2 -> {
                DevFixAndHexTab(
                    state = state,
                    canModify = canModify,
                    viewModel = viewModel,
                    onExportPatchedPkg = {
                        val name = state.pkgHeader?.fileName?.substringBeforeLast('.') ?: "patched"
                        pkgPatchExporter.launch("${name}_patched.pkg")
                    },
                    onLog = onLog
                )
            }

            3 -> {
                ResourceStudioSubScreen(
                    state = state,
                    viewModel = viewModel,
                    canModify = canModify,
                    onLog = onLog
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SfoEditorTab(
    state: PkgStudioState,
    canModify: Boolean,
    viewModel: PkgStudioViewModel,
    onExportSfo: () -> Unit,
    onExportPatchedPkg: () -> Unit,
    onLog: (String, String, String, String) -> Unit
) {
    val doc = state.sfoDoc
    var newKey by remember { mutableStateOf("") }
    var newVal by remember { mutableStateOf("") }
    var newIsInt by remember { mutableStateOf(false) }

    if (doc == null) {
        LiquidGlassCard(accentColor = AmberWarning) {
            SectionHeaderRow(
                title = "PARAM.SFO Workspace Empty",
                subtitle = "Import a PS4 .pkg, a standalone param.sfo, or tap 'New Template SFO' above.",
                icon = Icons.Rounded.Tune
            )
        }
        return
    }

    LiquidGlassCard(accentColor = ElectricCyan) {
        SectionHeaderRow(
            title = doc.sourceLabel,
            subtitle = "SFO v${doc.versionMajor}.${doc.versionMinor} • ${doc.entries.size} parameters",
            icon = Icons.Rounded.Tune
        )
        Spacer(Modifier.height(10.dp))

        // Diagnostics strip
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.sfoIssues.forEach { issue ->
                val col = when (issue.severity) {
                    "WARN" -> AmberWarning
                    "OK" -> EmeraldSuccess
                    else -> ElectricCyan
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "[${issue.key}] ${issue.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = col,
                        modifier = Modifier.weight(1f)
                    )
                    if (issue.fixAvailable && canModify) {
                        OutlinedButton(onClick = { viewModel.applyFirmwareBackport("09000000", onLog) }) {
                            Text("Fix 9.00")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onExportSfo,
                enabled = canModify,
                modifier = Modifier.weight(1f).testTag("export_sfo_btn"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.Save, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(lStr(R.string.pkg_export_sfo_btn))
            }
            if (state.pkgHeader != null) {
                OutlinedButton(
                    onClick = onExportPatchedPkg,
                    enabled = canModify,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(lStr(R.string.pkg_patch_copy_btn))
                }
            }
        }
    }

    // Parameter list
    LiquidGlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            doc.entries.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = entry.stringValue,
                        onValueChange = { if (canModify) viewModel.updateSfoEntry(entry.key, it) },
                        enabled = canModify,
                        label = { Text("${entry.key} (${entry.format.label})") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    if (canModify) {
                        IconButton(onClick = { viewModel.removeSfoEntry(entry.key) }) {
                            Icon(Icons.Rounded.Delete, "Remove ${entry.key}", tint = CoralError)
                        }
                    }
                }
            }
        }
    }

    if (canModify) {
        LiquidGlassCard(accentColor = EmeraldSuccess) {
            SectionHeaderRow(title = "Add SFO Parameter", icon = Icons.Rounded.Add)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = newKey,
                onValueChange = { newKey = it },
                label = { Text("Key (e.g. CUSTOM_FLAG)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = newVal,
                onValueChange = { newVal = it },
                label = { Text(if (newIsInt) "UInt32 Hex/Dec (e.g. 0x00000001)" else "UTF-8 Value") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = newIsInt,
                    onClick = { newIsInt = !newIsInt },
                    label = { Text(if (newIsInt) "Type: UInt32" else "Type: UTF-8") }
                )
                Button(onClick = {
                    viewModel.addSfoEntry(
                        newKey,
                        if (newIsInt) SfoValueFormat.INT32 else SfoValueFormat.UTF8,
                        newVal
                    )
                    newKey = ""
                    newVal = ""
                }) {
                    Text("Add Key")
                }
            }
        }
    }
}

@Composable
private fun DevFixAndHexTab(
    state: PkgStudioState,
    canModify: Boolean,
    viewModel: PkgStudioViewModel,
    onExportPatchedPkg: () -> Unit,
    onLog: (String, String, String, String) -> Unit
) {
    var jumpOffsetHex by remember { mutableStateOf("0") }

    LiquidGlassCard(accentColor = AmberWarning) {
        SectionHeaderRow(
            title = "PS4 PKG Developer Fix Tools",
            subtitle = "Firmware SDK backport patching & header Content ID repair",
            icon = Icons.Rounded.Build
        )
        Spacer(Modifier.height(10.dp))
        Text("One-Tap Firmware SDK Patch (SYSTEM_VER & PUBTOOLINFO):", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "05050000" to "FW 5.05",
                "06720000" to "FW 6.72",
                "07550000" to "FW 7.55",
                "09000000" to "FW 9.00",
                "11000000" to "FW 11.00"
            ).forEach { (hex, label) ->
                OutlinedButton(
                    onClick = { viewModel.applyFirmwareBackport(hex, onLog) },
                    enabled = canModify && state.sfoDoc != null
                ) {
                    Text(label)
                }
            }
        }

        if (state.pkgHeader != null) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.patchedContentId,
                onValueChange = { viewModel.updatePatchedContentId(it) },
                enabled = canModify,
                label = { Text("Header Content ID (36 chars @ 0x40)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onExportPatchedPkg,
                enabled = canModify,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Save, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(lStr(R.string.pkg_patch_copy_btn))
            }
        }
    }

    LiquidGlassCard(accentColor = ElectricCyan) {
        SectionHeaderRow(
            title = "Binary Hex Inspector",
            subtitle = "Offset: 0x${state.hexOffset.toString(16).uppercase()}",
            icon = Icons.Rounded.Code
        )
        Spacer(Modifier.height(8.dp))
        if (state.pkgUri != null) {
            val ctx = LocalContext.current
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = jumpOffsetHex,
                    onValueChange = { jumpOffsetHex = it },
                    label = { Text("Hex Offset (e.g. 400)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = {
                    val off = jumpOffsetHex.removePrefix("0x").toLongOrNull(16) ?: 0L
                    viewModel.loadHexWindow(ctx, off)
                }) {
                    Text("Jump")
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.loadHexWindow(ctx, (state.hexOffset - 256L).coerceAtLeast(0L)) }) {
                    Text("-256 B")
                }
                OutlinedButton(onClick = { viewModel.loadHexWindow(ctx, state.hexOffset + 256L) }) {
                    Text("+256 B")
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = state.hexDump.ifBlank { "Import a .PKG or inspect an entry to view raw hex bytes." },
            fontFamily = JetBrainsMonoFontFamily,
            style = MaterialTheme.typography.labelSmall,
            color = ElectricCyan,
            modifier = Modifier.horizontalScroll(rememberScrollState())
        )
    }
}
