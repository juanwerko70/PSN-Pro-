package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Visibility
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.engine.MemoryValueType
import com.example.ui.components.GlassStatusPill
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.SectionHeaderRow
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IridescentIndigo
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.VioletAccent
import com.example.viewmodel.ConsoleDebugState
import com.example.viewmodel.ConsoleDebugViewModel

@Composable
fun ConsoleTelemetrySparklineCard(
    samples: List<Long>,
    lastThroughputKbps: Long
) {
    LiquidGlassCard(accentColor = ElectricCyan) {
        SectionHeaderRow(
            title = "Live Link Telemetry",
            subtitle = if (lastThroughputKbps > 0L) "Last FTP throughput: $lastThroughputKbps KB/s" else "RTT latency & throughput history",
            icon = Icons.Rounded.ShowChart,
            trailing = {
                GlassStatusPill(
                    text = "${samples.lastOrNull() ?: 0} ${if (lastThroughputKbps > 0L) "KB/s" else "ms"}",
                    color = ElectricCyan
                )
            }
        )
        Spacer(Modifier.height(10.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF060B17))
                .padding(8.dp)
        ) {
            if (samples.isEmpty()) return@Canvas
            val maxVal = (samples.maxOrNull() ?: 1L).coerceAtLeast(1L).toFloat()
            val stepX = if (samples.size <= 1) size.width else size.width / (samples.size - 1)
            val path = Path()
            samples.forEachIndexed { idx, v ->
                val x = idx * stepX
                val y = size.height - ((v / maxVal) * size.height * 0.85f) - (size.height * 0.08f)
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                drawCircle(ElectricCyan, radius = 3.5f, center = Offset(x, y))
            }
            drawPath(
                path = path,
                brush = Brush.horizontalGradient(listOf(IridescentIndigo, ElectricCyan, EmeraldSuccess)),
                style = Stroke(width = 3f, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
fun PayloadMacroQueueCard(
    state: ConsoleDebugState,
    onAddPayload: () -> Unit,
    onRemovePayload: (String) -> Unit,
    onClearQueue: () -> Unit,
    onLaunchQueue: () -> Unit
) {
    LiquidGlassCard(accentColor = VioletAccent) {
        SectionHeaderRow(
            title = "Payload Macro Sequence",
            subtitle = "Queue multiple .bin payloads and fire sequentially to :${state.binLoaderPort}",
            icon = Icons.Rounded.RocketLaunch,
            trailing = {
                GlassStatusPill(text = "${state.payloadQueue.size} queued", color = VioletAccent)
            }
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onAddPayload,
                modifier = Modifier.weight(1f).testTag("queue_add_payload_btn"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Queue .bin")
            }
            Button(
                onClick = onLaunchQueue,
                enabled = state.payloadQueue.isNotEmpty() && !state.isBusy,
                modifier = Modifier.weight(1f).testTag("queue_launch_btn"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Fire Sequence")
            }
            if (state.payloadQueue.isNotEmpty()) {
                IconButton(onClick = onClearQueue, modifier = Modifier.testTag("queue_clear_btn")) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Clear payload queue", tint = CoralError)
                }
            }
        }
        if (state.payloadQueue.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.payloadQueue.forEachIndexed { idx, item ->
                    LiquidGlassCard(shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${idx + 1}. ${item.name} (${item.sizeBytes} B)",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onRemovePayload(item.id) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Remove payload", tint = CoralError)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MemoryHackerToolsPanel(
    state: ConsoleDebugState,
    viewModel: ConsoleDebugViewModel,
    onLog: (String, String, String, String) -> Unit
) {
    var watchLabel by remember { mutableStateOf("") }

    // 1. Live Memory Value Scanner
    LiquidGlassCard(accentColor = ElectricCyan) {
        SectionHeaderRow(
            title = "Memory Scanner (PS4Debug)",
            subtitle = if (state.selectedPid != null) "Target PID ${state.selectedPid}" else "Select a process PID above first",
            icon = Icons.Rounded.Search
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MemoryValueType.entries.forEach { t ->
                FilterChip(
                    selected = state.scanValueType == t,
                    onClick = { viewModel.updateMemoryEditorFields(scanValueType = t) },
                    label = { Text(t.label) }
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
                value = state.scanQuery,
                onValueChange = { viewModel.updateMemoryEditorFields(scanQuery = it) },
                label = { Text("Value (${state.scanValueType.label})") },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("mem_scan_query_input")
            )
            Button(
                onClick = { viewModel.scanMemory(onLog) },
                enabled = state.selectedPid != null && state.scanQuery.isNotBlank() && !state.isScanningMemory,
                modifier = Modifier.testTag("mem_scan_btn")
            ) {
                Text("Scan")
            }
        }
        if (state.isScanningMemory) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (state.scanHits.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.scanHits.take(15).forEach { hit ->
                    val hexAddr = hit.address.toString(16).uppercase()
                    LiquidGlassCard(
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(10.dp),
                        onClick = {
                            viewModel.updateMemoryEditorFields(peekAddressHex = hexAddr)
                            viewModel.peekMemoryAt(hexAddr)
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "0x$hexAddr  [${hit.regionName}]",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ElectricCyan
                                )
                                Text(
                                    text = hit.previewHex,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    viewModel.addWatchItem(
                                        addressHex = hexAddr,
                                        label = watchLabel.ifBlank { hit.regionName },
                                        type = state.scanValueType,
                                        targetValue = state.scanQuery
                                    )
                                }
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "Pin to watchlist", tint = EmeraldSuccess)
                            }
                        }
                    }
                }
            }
        }
    }

    // 2. Hex Peek & Live Poke
    LiquidGlassCard(accentColor = AmberWarning) {
        SectionHeaderRow(
            title = "Live Hex Peek & Memory Poke",
            subtitle = "Read/Patch RAM bytes directly at any 64-bit address",
            icon = Icons.Rounded.Edit
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.peekAddressHex,
                onValueChange = { viewModel.updateMemoryEditorFields(peekAddressHex = it) },
                label = { Text("Hex Address (0x...)") },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("mem_peek_addr_input")
            )
            Button(
                onClick = { viewModel.peekMemoryAt(state.peekAddressHex) },
                enabled = state.selectedPid != null && !state.isBusy,
                modifier = Modifier.testTag("mem_peek_btn")
            ) {
                Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Peek")
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MemoryValueType.entries.forEach { t ->
                FilterChip(
                    selected = state.pokeValueType == t,
                    onClick = { viewModel.updateMemoryEditorFields(pokeValueType = t) },
                    label = { Text(t.label) }
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
                value = state.pokeValueInput,
                onValueChange = { viewModel.updateMemoryEditorFields(pokeValueInput = it) },
                label = { Text("Write ${state.pokeValueType.label}") },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("mem_poke_val_input")
            )
            Button(
                onClick = {
                    viewModel.pokeMemoryAt(
                        addressHex = state.peekAddressHex,
                        type = state.pokeValueType,
                        valueInput = state.pokeValueInput,
                        onLog = onLog
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = AmberWarning, contentColor = Color.Black),
                enabled = state.selectedPid != null && !state.isBusy,
                modifier = Modifier.testTag("mem_poke_btn")
            ) {
                Text("Poke", fontWeight = FontWeight.Bold)
            }
        }
        if (state.peekHexDump.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF050810))
                    .padding(10.dp)
            ) {
                Text(
                    text = state.peekHexDump,
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                )
            }
        }
    }

    // 3. Memory Watchlist & Value Freeze Loop
    LiquidGlassCard(accentColor = EmeraldSuccess) {
        SectionHeaderRow(
            title = "Watchlist & Value Freeze",
            subtitle = "Pin addresses & auto-refresh or freeze values every 500ms",
            icon = Icons.Rounded.Lock,
            trailing = {
                GlassStatusPill(
                    text = if (state.isFreezeLoopActive) "ACTIVE" else "IDLE",
                    color = if (state.isFreezeLoopActive) EmeraldSuccess else AmberWarning
                )
            }
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = watchLabel,
                onValueChange = { watchLabel = it },
                label = { Text("Watch Label") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = {
                    viewModel.addWatchItem(
                        addressHex = state.peekAddressHex,
                        label = watchLabel,
                        type = state.pokeValueType,
                        targetValue = state.pokeValueInput
                    )
                    watchLabel = ""
                },
                modifier = Modifier.testTag("mem_watch_add_btn")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Pin")
            }
            Button(
                onClick = { viewModel.toggleFreezeLoop() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isFreezeLoopActive) CoralError else EmeraldSuccess
                ),
                modifier = Modifier.testTag("mem_freeze_loop_btn")
            ) {
                Icon(if (state.isFreezeLoopActive) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (state.isFreezeLoopActive) "Stop" else "Loop")
            }
        }
        if (state.watchlist.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.watchlist.forEach { w ->
                    LiquidGlassCard(
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(10.dp),
                        accentColor = if (w.isFrozen) AmberWarning else ElectricCyan
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${w.label} (0x${w.address.toString(16).uppercase()})",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    text = "${w.valueType.label} | Live: ${w.liveValue} | Target: ${w.targetValue}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (w.isFrozen) AmberWarning else EmeraldSuccess
                                )
                            }
                            Row {
                                IconButton(onClick = { viewModel.toggleWatchFreeze(w.id) }) {
                                    Icon(
                                        imageVector = if (w.isFrozen) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                        contentDescription = "Toggle freeze",
                                        tint = if (w.isFrozen) AmberWarning else ElectricCyan
                                    )
                                }
                                IconButton(onClick = { viewModel.removeWatchItem(w.id) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Remove watch", tint = CoralError)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
