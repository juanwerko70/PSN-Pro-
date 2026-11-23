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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.components.GlassMetricTile
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
import com.example.viewmodel.AdvancedHackerState
import com.example.viewmodel.AdvancedHackerViewModel
import com.example.viewmodel.ConsoleDebugState
import java.util.Locale

@Composable
fun ForensicsAndElfToolPanel(
    consoleState: ConsoleDebugState,
    hackerState: AdvancedHackerState,
    hackerViewModel: AdvancedHackerViewModel,
    onLog: (String, String, String, String) -> Unit
) {
    val context = LocalContext.current
    val binaryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) hackerViewModel.analyzeImportedBinary(context, uri, onLog)
    }

    LiquidGlassCard(accentColor = VioletAccent) {
        SectionHeaderRow(
            title = "ELF64 / RAM Forensics & Entropy",
            subtitle = "Shannon entropy map, ELF64/SELF segments, ASCII/UTF-16 strings & XOR decoder",
            icon = Icons.Rounded.Analytics,
            trailing = {
                if (hackerState.analyzedBytes.isNotEmpty()) {
                    GlassStatusPill(text = "${hackerState.analyzedBytes.size}B", color = VioletAccent)
                }
            }
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { binaryPicker.launch(arrayOf("*/*")) },
                modifier = Modifier.weight(1f).testTag("forensics_open_bin_btn"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Inspect .ELF/.BIN")
            }
            OutlinedButton(
                onClick = {
                    hackerViewModel.analyzeBytes(
                        sourceName = "PS4 RAM @ 0x${consoleState.lastPeekAddress.toString(16).uppercase()}",
                        baseAddress = consoleState.lastPeekAddress,
                        bytes = consoleState.lastPeekBytes
                    )
                },
                enabled = consoleState.lastPeekBytes.isNotEmpty(),
                modifier = Modifier.weight(1f).testTag("forensics_analyze_ram_btn"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Analyze Peeked RAM")
            }
        }

        val entropy = hackerState.entropyReport
        if (entropy != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassMetricTile(
                    label = "Entropy",
                    value = String.format(Locale.US, "%.2f / 8.0", entropy.overallEntropy),
                    accent = if (entropy.overallEntropy >= 7.2) CoralError else ElectricCyan,
                    modifier = Modifier.weight(1f)
                )
                GlassMetricTile(
                    label = "ASCII %",
                    value = "${(entropy.asciiRatio * 100f).toInt()}%",
                    accent = EmeraldSuccess,
                    modifier = Modifier.weight(1f)
                )
                GlassMetricTile(
                    label = "Null %",
                    value = "${(entropy.nullRatio * 100f).toInt()}%",
                    accent = AmberWarning,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${hackerState.analyzedSourceName} — ${entropy.classification}",
                style = MaterialTheme.typography.labelMedium,
                color = VioletAccent,
                fontFamily = JetBrainsMonoFontFamily
            )
            if (entropy.sliceEntropies.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF060B17))
                        .padding(6.dp)
                ) {
                    val count = entropy.sliceEntropies.size
                    val barW = (size.width / count) * 0.72f
                    val gap = (size.width / count) * 0.28f
                    entropy.sliceEntropies.forEachIndexed { idx, ratio ->
                        val h = (ratio * size.height).coerceAtLeast(3f)
                        val barColor = when {
                            ratio >= 0.88f -> CoralError
                            ratio >= 0.65f -> AmberWarning
                            else -> ElectricCyan
                        }
                        drawRect(
                            color = barColor,
                            topLeft = Offset(idx * (barW + gap), size.height - h),
                            size = Size(barW, h)
                        )
                    }
                }
            }
        }

        val elf = hackerState.elfReport
        if (elf != null) {
            Spacer(Modifier.height(10.dp))
            LiquidGlassCard(shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(10.dp)) {
                Text(
                    text = "${elf.formatLabel} | ${elf.typeLabel} | ${elf.machineLabel}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (elf.isValidElf64) EmeraldSuccess else AmberWarning,
                    fontFamily = JetBrainsMonoFontFamily
                )
                if (elf.isValidElf64) {
                    Text(
                        text = "Entry: 0x${elf.entryPoint.toString(16).uppercase()} | PHDRs: ${elf.phNum} | SHDRs: ${elf.shNum}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = ElectricCyan
                    )
                    elf.programHeaders.take(6).forEach { ph ->
                        Text(
                            text = "${ph.typeLabel.padEnd(18)} [${ph.flagsLabel}] VA=0x${ph.virtualAddress.toString(16).uppercase()} Mem=${ph.memSize}B",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (hackerState.analyzedBytes.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                hackerViewModel.disassembleBuffer(
                                    label = hackerState.analyzedSourceName,
                                    baseAddr = elf.entryPoint,
                                    bytes = hackerState.analyzedBytes
                                )
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Disassemble First 1KB")
                        }
                    }
                }
            }
        }
    }

    // XOR Brute-Force & Custom Key Decoder
    if (hackerState.analyzedBytes.isNotEmpty()) {
        LiquidGlassCard(accentColor = AmberWarning) {
            SectionHeaderRow(
                title = "XOR Key Hunter & Decoder",
                subtitle = "Single-byte brute-force + custom hex/ASCII multi-byte XOR",
                icon = Icons.Rounded.Key
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = hackerState.xorKeyInput,
                    onValueChange = hackerViewModel::updateXorKeyInput,
                    label = { Text("XOR Key (Hex e.g. AA BB or ASCII)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("xor_key_input")
                )
                Button(
                    onClick = hackerViewModel::decodeWithCustomXorKey,
                    modifier = Modifier.testTag("xor_decode_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Decode")
                }
            }
            if (hackerState.xorBruteCandidates.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Top 1-Byte Brute-Force Hits:", style = MaterialTheme.typography.labelMedium, color = AmberWarning)
                Spacer(Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    hackerState.xorBruteCandidates.take(4).forEach { cand ->
                        Text(
                            text = "KEY 0x%02X (${(cand.printableScore * 100).toInt()}%%): ${cand.preview}".format(cand.keyByte),
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    hackerViewModel.updateXorKeyInput("%02X".format(cand.keyByte))
                                    hackerViewModel.decodeWithCustomXorKey()
                                }
                                .padding(4.dp)
                        )
                    }
                }
            }
            if (hackerState.xorDecodedPreview.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF060B17))
                        .padding(10.dp)
                ) {
                    Text(
                        text = hackerState.xorDecodedPreview,
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldSuccess
                    )
                }
            }
        }

        // Extracted Strings Card
        if (hackerState.extractedStrings.isNotEmpty()) {
            LiquidGlassCard(accentColor = ElectricCyan) {
                SectionHeaderRow(
                    title = "Extracted Strings (${hackerState.extractedStrings.size})",
                    subtitle = "ASCII & UTF-16LE sequences discovered in buffer",
                    icon = Icons.Rounded.Memory
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF060B17))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        hackerState.extractedStrings.take(30).forEach { s ->
                            Text(
                                text = "0x${s.offsetAddress.toString(16).uppercase()} [${s.encoding}] ${s.text}",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (s.encoding == "UTF-16") ElectricCyan else EmeraldSuccess,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReconAndRawFtpPanel(
    consoleState: ConsoleDebugState,
    hackerState: AdvancedHackerState,
    hackerViewModel: AdvancedHackerViewModel,
    onExecuteRawFtp: suspend (String) -> Result<String>,
    onLog: (String, String, String, String) -> Unit
) {
    // 1. TCP Service Fingerprinter & Banner Grabber
    LiquidGlassCard(accentColor = ElectricCyan) {
        SectionHeaderRow(
            title = "PS4 / LAN Service Fingerprinter",
            subtitle = "TCP port probe & live service banner grabbing on ${consoleState.host}",
            icon = Icons.Rounded.Radar,
            trailing = {
                val open = hackerState.bannerResults.count { it.open }
                if (hackerState.bannerResults.isNotEmpty()) {
                    GlassStatusPill(text = "$open OPEN", color = EmeraldSuccess)
                }
            }
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = hackerState.bannerPortsInput,
                onValueChange = hackerViewModel::updateBannerPortsInput,
                label = { Text("Target Ports (CSV)") },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("recon_ports_input")
            )
            Button(
                onClick = { hackerViewModel.probeServiceBanners(consoleState.host, onLog) },
                enabled = !hackerState.isProbingBanners,
                modifier = Modifier.testTag("recon_probe_btn"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (hackerState.isProbingBanners) "Probing..." else "Grab Banners")
            }
        }
        if (hackerState.isProbingBanners) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (hackerState.bannerResults.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                hackerState.bannerResults.forEach { res ->
                    LiquidGlassCard(
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(10.dp),
                        accentColor = if (res.open) EmeraldSuccess else CoralError
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = ":${res.port} — ${res.serviceGuess}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (res.open) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            GlassStatusPill(
                                text = if (res.open) "${res.latencyMs}ms" else "CLOSED",
                                color = if (res.open) EmeraldSuccess else CoralError
                            )
                        }
                        if (res.open) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = res.banner,
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.bodySmall,
                                color = ElectricCyan
                            )
                        }
                    }
                }
            }
        }
    }

    // 2. Interactive Raw FTP / GoldHEN Command Terminal
    LiquidGlassCard(accentColor = IridescentIndigo) {
        SectionHeaderRow(
            title = "Raw FTP / GoldHEN Terminal",
            subtitle = if (consoleState.isConnected) "Active socket @ ${consoleState.host}:${consoleState.ftpPort}" else "Connect FTP Client first",
            icon = Icons.Rounded.Terminal
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("SYST", "FEAT", "STAT", "PWD", "HELP", "NOOP").forEach { quick ->
                FilterChip(
                    selected = hackerState.ftpCmdInput.equals(quick, ignoreCase = true),
                    onClick = {
                        hackerViewModel.sendRawFtpCommand(
                            isFtpConnected = consoleState.isConnected,
                            commandOverride = quick,
                            executeRaw = onExecuteRawFtp,
                            onLog = onLog
                        )
                    },
                    label = { Text(quick, fontFamily = JetBrainsMonoFontFamily) }
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
                value = hackerState.ftpCmdInput,
                onValueChange = hackerViewModel::updateFtpCmdInput,
                label = { Text("Raw Command (e.g. SITE ...)") },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("raw_ftp_cmd_input")
            )
            Button(
                onClick = {
                    hackerViewModel.sendRawFtpCommand(
                        isFtpConnected = consoleState.isConnected,
                        executeRaw = onExecuteRawFtp,
                        onLog = onLog
                    )
                },
                enabled = consoleState.isConnected && hackerState.ftpCmdInput.isNotBlank(),
                modifier = Modifier.testTag("raw_ftp_send_btn"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Exec")
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF060B17))
                .padding(10.dp)
        ) {
            Text(
                text = if (hackerState.ftpTerminalLines.isEmpty()) {
                    "> Connect FTP Client and execute SYST / FEAT / STAT / custom FTP commands..."
                } else {
                    hackerState.ftpTerminalLines.joinToString("\n")
                },
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.bodySmall,
                color = EmeraldSuccess
            )
        }
    }
}
