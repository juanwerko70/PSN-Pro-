package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engine.AdvancedHackerEngine
import com.example.engine.AobRegionScope
import com.example.engine.MemoryValueType
import com.example.engine.NextScanMode
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

@Composable
fun ConsoleAdvancedHackerPanel(
    consoleState: ConsoleDebugState,
    hackerViewModel: AdvancedHackerViewModel,
    onExecuteRawFtp: suspend (String) -> Result<String>,
    onLog: (String, String, String, String) -> Unit
) {
    val hackerState by hackerViewModel.state.collectAsStateWithLifecycle()

    // Tool switcher bar
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            "Multi-Scan / AoB",
            "x86-64 Disasm",
            "Pointers & Cheats",
            "ELF & Entropy",
            "Recon & Raw FTP"
        ).forEachIndexed { idx, label ->
            FilterChip(
                selected = hackerState.activeToolIndex == idx,
                onClick = { hackerViewModel.selectTool(idx) },
                label = { Text(label) },
                modifier = Modifier.testTag("hack_tool_tab_$idx")
            )
        }
    }

    if (hackerState.isBusy) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    if (!hackerState.statusMessage.isNullOrBlank()) {
        Text(
            text = hackerState.statusMessage.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = ElectricCyan,
            fontFamily = JetBrainsMonoFontFamily
        )
    }

    when (hackerState.activeToolIndex) {
        0 -> MultiScanAndAobPanel(
            consoleState = consoleState,
            hackerState = hackerState,
            hackerViewModel = hackerViewModel,
            onLog = onLog
        )
        1 -> X86DisassemblerPanel(
            consoleState = consoleState,
            hackerState = hackerState,
            hackerViewModel = hackerViewModel,
            onLog = onLog
        )
        2 -> PointerAndCheatBuilderPanel(
            consoleState = consoleState,
            hackerState = hackerState,
            hackerViewModel = hackerViewModel,
            onLog = onLog
        )
        3 -> ForensicsAndElfToolPanel(
            consoleState = consoleState,
            hackerState = hackerState,
            hackerViewModel = hackerViewModel,
            onLog = onLog
        )
        4 -> ReconAndRawFtpPanel(
            consoleState = consoleState,
            hackerState = hackerState,
            hackerViewModel = hackerViewModel,
            onExecuteRawFtp = onExecuteRawFtp,
            onLog = onLog
        )
    }
}

@Composable
private fun MultiScanAndAobPanel(
    consoleState: ConsoleDebugState,
    hackerState: AdvancedHackerState,
    hackerViewModel: AdvancedHackerViewModel,
    onLog: (String, String, String, String) -> Unit
) {
    // 1. Multi-Pass Narrowing Engine
    LiquidGlassCard(accentColor = ElectricCyan) {
        SectionHeaderRow(
            title = "Multi-Pass RAM Narrowing",
            subtitle = if (consoleState.selectedPid != null) {
                "Cheat Engine style refinement (PID ${consoleState.selectedPid}) • Pass #${hackerState.passCount}"
            } else {
                "Select a PS4 PID & load memory maps in Processes & RAM first"
            },
            icon = Icons.Rounded.FilterAlt,
            trailing = {
                GlassStatusPill(text = "${hackerState.narrowHits.size} hits", color = ElectricCyan)
            }
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
                    selected = hackerState.multiValueType == t,
                    onClick = { hackerViewModel.updateScanFields(valueType = t) },
                    label = { Text(t.label) }
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            NextScanMode.entries.forEach { mode ->
                FilterChip(
                    selected = hackerState.nextScanMode == mode,
                    onClick = { hackerViewModel.updateScanFields(nextMode = mode) },
                    label = { Text(mode.label) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = hackerState.multiQuery,
            onValueChange = { hackerViewModel.updateScanFields(query = it) },
            label = { Text("Target Value (${hackerState.multiValueType.label}) for Exact / > / <") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("multi_scan_query_input")
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { hackerViewModel.startInitialScan(consoleState, onLog) },
                enabled = consoleState.selectedPid != null && hackerState.multiQuery.isNotBlank() && !hackerState.isBusy,
                modifier = Modifier.weight(1f).testTag("multi_first_scan_btn"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("First Scan")
            }
            Button(
                onClick = { hackerViewModel.runNextScan(consoleState, onLog) },
                enabled = consoleState.selectedPid != null && hackerState.narrowHits.isNotEmpty() && !hackerState.isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                modifier = Modifier.weight(1f).testTag("multi_next_scan_btn"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Next Scan")
            }
            if (hackerState.narrowHits.isNotEmpty()) {
                OutlinedButton(
                    onClick = hackerViewModel::resetMultiPassScan,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Reset")
                }
            }
        }
        if (hackerState.narrowHits.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                hackerState.narrowHits.take(16).forEach { hit ->
                    LiquidGlassCard(shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "0x${hit.address.toString(16).uppercase()} (${hit.regionName})",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ElectricCyan
                                )
                                Text(
                                    text = hit.previewHex,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            TextButton(onClick = {
                                hackerViewModel.sendAddressToCheatBuilder(hit.address, hit.previewHex)
                            }) {
                                Text("Cheat")
                            }
                        }
                    }
                }
            }
        }
    }

    // 2. AoB Wildcard Signature Scanner
    LiquidGlassCard(accentColor = VioletAccent) {
        SectionHeaderRow(
            title = "AoB Signature Scanner (Wildcards)",
            subtitle = "Search x86-64 opcode patterns with ?? wildcards",
            icon = Icons.Rounded.Fingerprint,
            trailing = {
                GlassStatusPill(text = "${hackerState.aobHits.size} sigs", color = VioletAccent)
            }
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AobRegionScope.entries.forEach { scope ->
                FilterChip(
                    selected = hackerState.aobScope == scope,
                    onClick = { hackerViewModel.updateScanFields(aobScope = scope) },
                    label = { Text(scope.label) }
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
                value = hackerState.aobPattern,
                onValueChange = { hackerViewModel.updateScanFields(aobPattern = it) },
                label = { Text("AoB Pattern (e.g. 48 8B 05 ?? ?? ?? ??)") },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("aob_pattern_input")
            )
            Button(
                onClick = { hackerViewModel.runAobScan(consoleState, onLog) },
                enabled = consoleState.selectedPid != null && !hackerState.isBusy,
                modifier = Modifier.testTag("aob_scan_btn"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Find AoB")
            }
        }
        if (hackerState.aobHits.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                hackerState.aobHits.take(16).forEach { hit ->
                    LiquidGlassCard(shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "0x${hit.address.toString(16).uppercase()} [${hit.regionName}]",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = VioletAccent
                                )
                                Text(
                                    text = hit.previewHex,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            TextButton(onClick = {
                                val hexAddr = hit.address.toString(16).uppercase()
                                hackerViewModel.selectTool(1)
                                hackerViewModel.disassembleRemoteRam(consoleState, hexAddr, onLog)
                            }) {
                                Text("Disasm")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun X86DisassemblerPanel(
    consoleState: ConsoleDebugState,
    hackerState: AdvancedHackerState,
    hackerViewModel: AdvancedHackerViewModel,
    onLog: (String, String, String, String) -> Unit
) {
    LiquidGlassCard(accentColor = CoralError) {
        SectionHeaderRow(
            title = "Live x86-64 Disassembler & Patcher",
            subtitle = hackerState.disasmSourceLabel,
            icon = Icons.Rounded.Code,
            trailing = {
                if (hackerState.patchUndoStack.isNotEmpty()) {
                    GlassStatusPill(text = "${hackerState.patchUndoStack.size} patches", color = AmberWarning)
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
                value = hackerState.disasmAddressHex,
                onValueChange = { hackerViewModel.updateDisasmFields(addressHex = it) },
                label = { Text("RIP / Addr Hex") },
                singleLine = true,
                modifier = Modifier.weight(1.3f).testTag("disasm_addr_input")
            )
            OutlinedTextField(
                value = hackerState.disasmByteLen.toString(),
                onValueChange = { hackerViewModel.updateDisasmFields(byteLen = it.toIntOrNull() ?: 128) },
                label = { Text("Bytes") },
                singleLine = true,
                modifier = Modifier.weight(0.7f)
            )
            Button(
                onClick = { hackerViewModel.disassembleRemoteRam(consoleState, null, onLog) },
                enabled = consoleState.selectedPid != null && !hackerState.isBusy,
                modifier = Modifier.testTag("disasm_read_btn"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Disasm")
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    hackerViewModel.disassembleBuffer(
                        label = "Peeked RAM @ 0x${consoleState.lastPeekAddress.toString(16).uppercase()}",
                        baseAddr = consoleState.lastPeekAddress,
                        bytes = consoleState.lastPeekBytes
                    )
                },
                enabled = consoleState.lastPeekBytes.isNotEmpty(),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Rounded.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Disasm Peeked Buffer")
            }
            if (hackerState.patchUndoStack.isNotEmpty()) {
                Button(
                    onClick = { hackerViewModel.undoLastPatch(consoleState, onLog) },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                    modifier = Modifier.testTag("disasm_undo_patch_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Undo Patch")
                }
            }
        }

        if (hackerState.disasmInsns.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Tap Disasm on a connected PID executable address or disassemble your Peeked RAM / ELF64 buffer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                hackerState.disasmInsns.take(32).forEach { insn ->
                    val isCtrl = insn.mnemonic in listOf("call", "jmp", "jmp short", "ret", "syscall") ||
                        insn.mnemonic.startsWith("j")
                    val isNop = insn.mnemonic == "nop"
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF060B17))
                            .padding(8.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "0x${insn.address.toString(16).uppercase()}  ${insn.hexBytes}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${insn.mnemonic} ${insn.operands}".trim(),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        isNop -> EmeraldSuccess
                                        isCtrl -> AmberWarning
                                        else -> ElectricCyan
                                    }
                                )
                            }
                            if (consoleState.selectedPid != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "NOP (${insn.length}B)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldSuccess,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(EmeraldSuccess.copy(alpha = 0.14f))
                                            .clickable {
                                                hackerViewModel.patchInstruction(
                                                    consoleState = consoleState,
                                                    insn = insn,
                                                    patchBytes = AdvancedHackerEngine.nopPatch(insn.length),
                                                    label = "NOP ${insn.mnemonic}",
                                                    onLog = onLog
                                                )
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                    Text(
                                        text = "RET (C3)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AmberWarning,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(AmberWarning.copy(alpha = 0.14f))
                                            .clickable {
                                                hackerViewModel.patchInstruction(
                                                    consoleState = consoleState,
                                                    insn = insn,
                                                    patchBytes = AdvancedHackerEngine.retPatch(),
                                                    label = "Force RET",
                                                    onLog = onLog
                                                )
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                    Text(
                                        text = "RET 0 (31 C0 C3)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CoralError,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(CoralError.copy(alpha = 0.14f))
                                            .clickable {
                                                hackerViewModel.patchInstruction(
                                                    consoleState = consoleState,
                                                    insn = insn,
                                                    patchBytes = AdvancedHackerEngine.retZeroPatch(),
                                                    label = "Stub Return 0",
                                                    onLog = onLog
                                                )
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                    Text(
                                        text = "RET 1 (B8 01..C3)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricCyan,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(ElectricCyan.copy(alpha = 0.14f))
                                            .clickable {
                                                hackerViewModel.patchInstruction(
                                                    consoleState = consoleState,
                                                    insn = insn,
                                                    patchBytes = AdvancedHackerEngine.retOnePatch(),
                                                    label = "Stub Return 1",
                                                    onLog = onLog
                                                )
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PointerAndCheatBuilderPanel(
    consoleState: ConsoleDebugState,
    hackerState: AdvancedHackerState,
    hackerViewModel: AdvancedHackerViewModel,
    onLog: (String, String, String, String) -> Unit
) {
    val context = LocalContext.current
    val cheatSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null) {
            val content = hackerState.buildCheatPreview()
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(content.toByteArray(Charsets.UTF_8))
            }
            onLog("HACK", "INFO", "CHEAT_EXPORT", "${hackerState.cheatTitleId} (${if (hackerState.cheatUseJson) "JSON" else "SHN"})")
        }
    }

    // 1. 64-bit Multi-Level Pointer Dereferencer
    LiquidGlassCard(accentColor = ElectricCyan) {
        SectionHeaderRow(
            title = "64-Bit Pointer Chain Resolver",
            subtitle = "Follow multi-level LE64 pointers live on PS4 RAM",
            icon = Icons.Rounded.Link
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = hackerState.pointerBaseHex,
            onValueChange = { hackerViewModel.updatePointerFields(baseHex = it) },
            label = { Text("Base Address Hex (e.g. 142A0F80)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("ptr_base_input")
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = hackerState.pointerOffsetsInput,
            onValueChange = { hackerViewModel.updatePointerFields(offsetsInput = it) },
            label = { Text("Offset Chain (e.g. +0x18, +0x240, +0x10)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("ptr_offsets_input")
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MemoryValueType.entries.forEach { t ->
                FilterChip(
                    selected = hackerState.pointerValueType == t,
                    onClick = { hackerViewModel.updatePointerFields(valueType = t) },
                    label = { Text(t.label) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { hackerViewModel.resolvePointerChain(consoleState, onLog) },
            enabled = consoleState.selectedPid != null && !hackerState.isBusy,
            modifier = Modifier.fillMaxWidth().testTag("ptr_resolve_btn"),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Dereference Chain")
        }
        hackerState.pointerResult?.let { res ->
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF060B17))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    res.hops.forEach { hop ->
                        Text(
                            text = "[L${hop.level}] *0x${hop.readAddress.toString(16).uppercase()} -> 0x${hop.derefValue.toString(16).uppercase()} (+0x${hop.offsetApplied.toString(16).uppercase()})",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.bodySmall,
                            color = ElectricCyan
                        )
                    }
                    Text(
                        text = "FINAL @ 0x${res.finalAddress.toString(16).uppercase()} = ${res.formattedValue}",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelLarge,
                        color = EmeraldSuccess
                    )
                }
            }
        }
    }

    // 2. GoldHEN JSON / SHN Cheat Script Generator
    LiquidGlassCard(accentColor = IridescentIndigo) {
        SectionHeaderRow(
            title = "GoldHEN Cheat Script Builder",
            subtitle = "Export ready-to-deploy .json or .shn patches for /data/GoldHEN/cheats/",
            icon = Icons.Rounded.Security
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = hackerState.cheatUseJson,
                onClick = { hackerViewModel.updateCheatBuilder(useJson = true) },
                label = { Text("GoldHEN .json") }
            )
            FilterChip(
                selected = !hackerState.cheatUseJson,
                onClick = { hackerViewModel.updateCheatBuilder(useJson = false) },
                label = { Text("PS4 .shn XML") }
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = hackerState.cheatTitleId,
                onValueChange = { hackerViewModel.updateCheatBuilder(titleId = it) },
                label = { Text("Title ID") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = hackerState.cheatVersion,
                onValueChange = { hackerViewModel.updateCheatBuilder(version = it) },
                label = { Text("Version") },
                singleLine = true,
                modifier = Modifier.weight(0.7f)
            )
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = hackerState.cheatGameTitle,
            onValueChange = { hackerViewModel.updateCheatBuilder(gameTitle = it) },
            label = { Text("Game Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = hackerState.cheatModName,
            onValueChange = { hackerViewModel.updateCheatBuilder(modName = it) },
            label = { Text("Mod / Cheat Label") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = hackerState.cheatAddressHex,
                onValueChange = { hackerViewModel.updateCheatBuilder(addressHex = it) },
                label = { Text("Offset Hex") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = hackerState.cheatOnHex,
                onValueChange = { hackerViewModel.updateCheatBuilder(onHex = it) },
                label = { Text("ON Bytes") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = hackerState.cheatOffHex,
                onValueChange = { hackerViewModel.updateCheatBuilder(offHex = it) },
                label = { Text("OFF Bytes") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
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
                text = hackerState.buildCheatPreview(),
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.bodySmall,
                color = EmeraldSuccess
            )
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val ext = if (hackerState.cheatUseJson) "json" else "shn"
                val name = "${hackerState.cheatTitleId.uppercase()}_${hackerState.cheatVersion}.$ext"
                cheatSaver.launch(name)
            },
            modifier = Modifier.fillMaxWidth().testTag("cheat_export_btn"),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Export ${if (hackerState.cheatUseJson) "GoldHEN .json" else "PS4 .shn"}")
        }
    }
}
