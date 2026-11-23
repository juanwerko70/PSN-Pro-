package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Publish
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.StoreCategory
import com.example.model.StoreItem
import com.example.ui.components.GlassStatusPill
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.SectionHeaderRow
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IridescentIndigo
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.VioletAccent
import com.example.viewmodel.AiGenState
import com.example.viewmodel.AiModArtifactType
import com.example.viewmodel.AiModViewModel

private data class GamePreset(val label: String, val gameName: String, val titleId: String)

private val aiGamePresets = listOf(
    GamePreset("GTA V", "Grand Theft Auto V", "CUSA00419"),
    GamePreset("CoD BO3", "Call of Duty: Black Ops III", "CUSA02624"),
    GamePreset("CoD MW", "Call of Duty: Modern Warfare", "CUSA15240"),
    GamePreset("RDR 2", "Red Dead Redemption 2", "CUSA03041"),
    GamePreset("Bloodborne", "Bloodborne", "CUSA00900"),
    GamePreset("God of War", "God of War", "CUSA07408"),
    GamePreset("Minecraft", "Minecraft PS4", "CUSA00265")
)

@Composable
fun AiModForgePanel(
    aiViewModel: AiModViewModel,
    consoleHost: String,
    canDeployToConsole: Boolean,
    canPublishCloud: Boolean,
    onDownloadItem: (StoreItem) -> Unit,
    onDeployToConsole: (StoreItem) -> Unit,
    onOpenInStudio: (StoreItem) -> Unit,
    onPublishToCloud: (StoreItem) -> Unit,
    onLog: (String, String, String, String) -> Unit
) {
    val genState by aiViewModel.genState.collectAsStateWithLifecycle()
    var aiMode by remember { mutableIntStateOf(0) } // 0 = Forge File, 1 = Streaming Assistant
    var gameName by remember { mutableStateOf("Grand Theft Auto V") }
    var titleId by remember { mutableStateOf("CUSA00419") }
    var artifactType by remember { mutableStateOf(AiModArtifactType.GOLDHEN_CHEAT) }
    var promptInput by remember { mutableStateOf("") }

    LiquidGlassCard(accentColor = VioletAccent) {
        SectionHeaderRow(
            title = "AI Mod & Asset Forge",
            subtitle = "Generate real mods, cheats, XML patches, Lua scripts, 3D .obj models & textures for any game",
            icon = Icons.Rounded.AutoAwesome,
            trailing = { GlassStatusPill("Gemini 3.8", VioletAccent) }
        )
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = aiMode == 0,
                onClick = { aiMode = 0 },
                label = { Text("Create File / Mod") },
                modifier = Modifier.testTag("ai_mode_forge")
            )
            FilterChip(
                selected = aiMode == 1,
                onClick = { aiMode = 1 },
                label = { Text("Streaming Advisor") },
                modifier = Modifier.testTag("ai_mode_chat")
            )
        }

        Spacer(Modifier.height(8.dp))
        Text("Target Game Quick Select:", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            aiGamePresets.forEach { preset ->
                FilterChip(
                    selected = titleId == preset.titleId,
                    onClick = {
                        gameName = preset.gameName
                        titleId = preset.titleId
                    },
                    label = { Text(preset.label) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = gameName,
                onValueChange = { gameName = it },
                label = { Text("Game Title") },
                singleLine = true,
                modifier = Modifier.weight(1.3f).testTag("ai_game_input")
            )
            OutlinedTextField(
                value = titleId,
                onValueChange = { titleId = it.uppercase().take(16) },
                label = { Text("Title ID") },
                singleLine = true,
                modifier = Modifier.weight(0.7f).testTag("ai_titleid_input")
            )
        }

        if (aiMode == 0) {
            Spacer(Modifier.height(8.dp))
            Text("Output Resource Format:", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AiModArtifactType.entries.forEach { type ->
                    FilterChip(
                        selected = artifactType == type,
                        onClick = { artifactType = type },
                        label = { Text(type.label) },
                        modifier = Modifier.testTag("ai_type_${type.name}")
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val suggestions = if (aiMode == 0) {
                listOf(
                    "Infinite ammo, god mode & super jump mod",
                    "60FPS unlock & skip intro XML patch",
                    "Neon tactical camo texture 32x32",
                    "Low-poly tactical weapon crate 3D .obj"
                )
            } else {
                listOf(
                    "How do I find infinite ammo offsets in PS4 RAM?",
                    "Explain GoldHEN PRX plugin hooking for GTA V",
                    "Best format for PS4 game texture replacement?"
                )
            }
            suggestions.forEach { hint ->
                FilterChip(
                    selected = false,
                    onClick = { promptInput = hint },
                    label = { Text(hint.take(30) + if (hint.length > 30) "..." else "") }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = promptInput,
            onValueChange = { promptInput = it },
            label = {
                Text(
                    if (aiMode == 0) "Describe the mod, script, texture or 3D model to create"
                    else "Ask anything about PS4 modding, offsets, scripts or textures"
                )
            },
            minLines = 2,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth().testTag("ai_prompt_input")
        )
        Spacer(Modifier.height(10.dp))

        Button(
            onClick = {
                if (aiMode == 0) {
                    aiViewModel.generateModArtifact(
                        gameName = gameName,
                        titleId = titleId,
                        artifactType = artifactType,
                        userPrompt = promptInput,
                        onLog = onLog
                    )
                } else {
                    aiViewModel.askModdingAssistantStream(
                        gameName = gameName,
                        question = promptInput,
                        onLog = onLog
                    )
                }
            },
            enabled = promptInput.isNotBlank() && genState !is AiGenState.Loading && genState !is AiGenState.Streaming,
            colors = ButtonDefaults.buttonColors(containerColor = VioletAccent, contentColor = Color.White),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("ai_generate_btn")
        ) {
            Icon(
                imageVector = if (aiMode == 0) Icons.Rounded.AutoAwesome else Icons.Rounded.Psychology,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (aiMode == 0) "Generate with AI" else "Ask AI Assistant",
                fontWeight = FontWeight.Bold
            )
        }
    }

    when (val state = genState) {
        is AiGenState.Idle -> Unit
        is AiGenState.Loading -> {
            LiquidGlassCard(accentColor = ElectricCyan) {
                Text("Generating with Gemini AI...", style = MaterialTheme.typography.titleSmall, color = ElectricCyan)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        is AiGenState.Streaming -> {
            LiquidGlassCard(accentColor = ElectricCyan) {
                SectionHeaderRow(title = "AI Response (Live Stream)", icon = Icons.Rounded.Psychology)
                Spacer(Modifier.height(8.dp))
                Text(state.partialText, style = MaterialTheme.typography.bodyMedium)
            }
        }
        is AiGenState.SuccessAdvice -> {
            LiquidGlassCard(accentColor = EmeraldSuccess) {
                SectionHeaderRow(title = "AI Modding Guide", icon = Icons.Rounded.Psychology)
                Spacer(Modifier.height(8.dp))
                Text(state.markdownText, style = MaterialTheme.typography.bodyMedium)
            }
        }
        is AiGenState.SuccessArtifact -> {
            val item = state.item
            val cat = StoreCategory.safeValueOf(item.category)
            val isStudioEditable = cat == StoreCategory.TEXTURE || cat == StoreCategory.MODEL_3D
            LiquidGlassCard(accentColor = EmeraldSuccess) {
                SectionHeaderRow(
                    title = item.title,
                    subtitle = "${item.gameTitle} • ${item.titleId} • ${item.fileName}",
                    icon = Icons.Rounded.AutoAwesome,
                    trailing = { GlassStatusPill("AI Ready", EmeraldSuccess) }
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "PS4 Target: ${item.targetConsolePath}/${item.fileName}",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan
                )
                if (state.notes.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(state.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                LiquidGlassCard(
                    accentColor = IridescentIndigo,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(10.dp)
                ) {
                    Text(
                        text = item.inlineContent.take(1200) +
                            if (item.inlineContent.length > 1200) "\n... (${item.inlineContent.length} chars)" else "",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall
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
                        onClick = { onDeployToConsole(item) },
                        enabled = canDeployToConsole,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import -> $consoleHost")
                    }
                    OutlinedButton(
                        onClick = { onDownloadItem(item) },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Download")
                    }
                    if (isStudioEditable) {
                        OutlinedButton(
                            onClick = { onOpenInStudio(item) },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Studio")
                        }
                    }
                    if (canPublishCloud) {
                        OutlinedButton(
                            onClick = { onPublishToCloud(item) },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Publish, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Publish Cloud")
                        }
                    }
                }
            }
        }
        is AiGenState.Error -> {
            LiquidGlassCard(accentColor = CoralError) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoralError,
                    modifier = Modifier.padding(4.dp)
                )
            }
        }
    }
}
