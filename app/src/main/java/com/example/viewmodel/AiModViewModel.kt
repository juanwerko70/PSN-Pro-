package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.StoreCategory
import com.example.model.StoreItem
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "AiModForgeVM"

enum class AiModArtifactType(
    val label: String,
    val storeCategory: StoreCategory,
    val defaultExt: String,
    val defaultDir: String
) {
    GOLDHEN_CHEAT("GoldHEN Cheat (.json)", StoreCategory.SCRIPT, "json", "/data/GoldHEN/cheats/json"),
    XML_PATCH("Game Patch (.xml)", StoreCategory.SCRIPT, "xml", "/data/GoldHEN/patches/xml"),
    LUA_MOD_SCRIPT("Lua / Shell Mod (.lua)", StoreCategory.SCRIPT, "lua", "/data/GoldHEN/scripts"),
    PRX_PLUGIN_SRC("PRX C/C++ Mod Source (.c)", StoreCategory.MOD, "c", "/data/GoldHEN/plugins"),
    MODEL_3D_OBJ("3D Mesh (.obj)", StoreCategory.MODEL_3D, "obj", "/data/pkgforge/models"),
    TEXTURE_PPM("Texture Map (.ppm)", StoreCategory.TEXTURE, "ppm", "/user/app")
}

sealed interface AiGenState {
    data object Idle : AiGenState
    data object Loading : AiGenState
    data class Streaming(val partialText: String) : AiGenState
    data class SuccessArtifact(val item: StoreItem, val notes: String) : AiGenState
    data class SuccessAdvice(val markdownText: String) : AiGenState
    data class Error(val message: String) : AiGenState
}

class AiModViewModel : ViewModel() {

    private val artifactSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string("Short mod or resource title"),
            "gameTitle" to Schema.string("Target PS4 game name"),
            "titleId" to Schema.string("PS4 Title ID like CUSA00419 or CUSA02624"),
            "fileName" to Schema.string("Output filename with proper extension"),
            "targetConsolePath" to Schema.string("PS4 target folder starting with /"),
            "installNotes" to Schema.string("Concise instructions and warnings for the user"),
            "content" to Schema.string("Complete valid raw file content (JSON, XML, Lua, C, Wavefront OBJ, or ASCII P3 PPM)")
        )
    )

    private val structuredModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = artifactSchema
            },
            systemInstruction = content {
                text(
                    "You are PKG Forge AI, an expert PlayStation 4 / GoldHEN homebrew, game modding, " +
                        "3D asset (.obj), procedural texture (ASCII P3 PPM), Lua/Shell scripting, and XML patch engineer. " +
                        "Generate clean, syntactically valid, ready-to-use files tailored to the requested game and format. " +
                        "Never wrap 'content' in markdown code fences inside the JSON string."
                )
            }
        )
    }

    private val streamingAdvisorModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    "You are PKG Forge AI, an expert PlayStation 4 modding, GoldHEN, PRX/ELF development, " +
                        "memory scanning, PARAM.SFO, and game asset assistant. Reply clearly and concisely in the user's language."
                )
            }
        )
    }

    private val _genState = MutableStateFlow<AiGenState>(AiGenState.Idle)
    val genState: StateFlow<AiGenState> = _genState.asStateFlow()

    fun resetState() {
        _genState.value = AiGenState.Idle
    }

    fun generateModArtifact(
        gameName: String,
        titleId: String,
        artifactType: AiModArtifactType,
        userPrompt: String,
        onLog: (String, String, String, String) -> Unit
    ) {
        val cleanPrompt = userPrompt.trim()
        if (cleanPrompt.isBlank()) return
        val cleanGame = gameName.trim().ifBlank { "PS4 Universal" }
        val cleanId = titleId.trim().uppercase().ifBlank { "CUSA00000" }

        viewModelScope.launch {
            _genState.value = AiGenState.Loading
            try {
                val promptText = buildString {
                    appendLine("Create a ${artifactType.label} resource for PS4 game '$cleanGame' (Title ID: $cleanId).")
                    appendLine("File extension MUST be .${artifactType.defaultExt}.")
                    appendLine("Default PS4 path hint: ${artifactType.defaultDir}.")
                    when (artifactType) {
                        AiModArtifactType.MODEL_3D_OBJ ->
                            appendLine("Output a complete, valid Wavefront .obj mesh with 'v', 'vn', and 'f' triangle indices (keep under 350 vertices so it renders smoothly on mobile).")
                        AiModArtifactType.TEXTURE_PPM ->
                            appendLine("Output a valid ASCII P3 PPM image ('P3\\n32 32\\n255\\n...') with rich themed colors/patterns matching the prompt.")
                        AiModArtifactType.GOLDHEN_CHEAT ->
                            appendLine("Output valid GoldHEN cheat JSON with 'name', 'id', 'version', 'credits', and 'mods' array.")
                        AiModArtifactType.XML_PATCH ->
                            appendLine("Output valid GoldHEN XML patch (<Patch><TitleID>...</TitleID><Metadata>...</Metadata></Patch>).")
                        AiModArtifactType.LUA_MOD_SCRIPT ->
                            appendLine("Output clean Lua script with modular functions and comments.")
                        AiModArtifactType.PRX_PLUGIN_SRC ->
                            appendLine("Output clean PS4 GoldHEN SDK C/C++ plugin source skeleton implementing the requested mod.")
                    }
                    appendLine("User request: $cleanPrompt")
                }

                val response = withTimeout(60_000L) {
                    structuredModel.generateContent(promptText)
                }
                val rawJson = response.text ?: error("AI model returned an empty response.")
                val json = JSONObject(rawJson)

                val genTitle = json.getString("title").take(96)
                val genGame = json.getString("gameTitle").ifBlank { cleanGame }.take(80)
                val genId = json.getString("titleId").uppercase().ifBlank { cleanId }.take(16)
                val rawFileName = json.getString("fileName").ifBlank { "ai_mod.${artifactType.defaultExt}" }
                val safeFileName = rawFileName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)
                val targetDir = json.getString("targetConsolePath").trim().let {
                    if (it.startsWith("/")) it else artifactType.defaultDir
                }.take(180)
                val installNotes = json.getString("installNotes").take(500)
                val contentStr = json.getString("content").take(15500)
                if (contentStr.isBlank()) error("Generated content was empty.")

                val item = StoreItem(
                    id = "ai_${System.currentTimeMillis()}",
                    title = genTitle,
                    category = artifactType.storeCategory.name,
                    gameTitle = genGame,
                    titleId = genId,
                    version = "AI-1.0",
                    fileName = safeFileName,
                    targetConsolePath = targetDir,
                    downloadUrl = "",
                    inlineContent = contentStr,
                    sizeBytes = contentStr.toByteArray(Charsets.UTF_8).size.toLong(),
                    description = installNotes,
                    publishedStatus = "PUBLISHED",
                    authorEmail = "PKG Forge AI"
                )
                _genState.value = AiGenState.SuccessArtifact(item, installNotes)
                onLog("STORE", "INFO", "AI_MOD_GENERATED", "$genGame ($safeFileName)")
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI artifact generation timed out", e)
                _genState.value = AiGenState.Error("Generation timed out after 60 seconds. Please try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI artifact generation failed", e)
                _genState.value = AiGenState.Error(e.localizedMessage ?: "AI generation failed.")
            }
        }
    }

    fun askModdingAssistantStream(
        gameName: String,
        question: String,
        onLog: (String, String, String, String) -> Unit
    ) {
        val clean = question.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            _genState.value = AiGenState.Loading
            val accumulated = StringBuilder()
            try {
                val prompt = if (gameName.isNotBlank()) {
                    "Target Game: $gameName\nQuestion: $clean"
                } else {
                    clean
                }
                withTimeout(60_000L) {
                    streamingAdvisorModel.generateContentStream(prompt).collect { chunk ->
                        val part = chunk.text.orEmpty()
                        accumulated.append(part)
                        _genState.value = AiGenState.Streaming(accumulated.toString())
                    }
                }
                val final = accumulated.toString()
                if (final.isBlank()) {
                    _genState.value = AiGenState.Error("AI returned an empty response.")
                } else {
                    _genState.value = AiGenState.SuccessAdvice(final)
                    onLog("STORE", "INFO", "AI_CHAT_RESPONSE", "${clean.take(60)} -> ${final.length} chars")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI streaming timed out", e)
                _genState.value = AiGenState.Error("Streaming timed out after 60 seconds.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI streaming failed", e)
                _genState.value = AiGenState.Error(e.localizedMessage ?: "AI request failed.")
            }
        }
    }
}
