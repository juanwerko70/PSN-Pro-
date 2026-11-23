package com.example.engine

import com.example.model.StoreCategory
import com.example.model.StoreItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object StoreEngine {

    const val SOURCE_ALL_INTERNET = "internet:all"
    const val SOURCE_GTA_MODS = "internet:gtav"
    const val SOURCE_COD_MODS = "internet:cod"
    const val SOURCE_PKG_BUNDLES = "internet:pkgs"
    const val SOURCE_POLYHAVEN_TEXTURES = "polyhaven:textures"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    data class QuickFeedPreset(
        val label: String,
        val sourceKey: String,
        val defaultCategory: StoreCategory?
    )

    val quickPresets = listOf(
        QuickFeedPreset("Todo Internet", SOURCE_ALL_INTERNET, null),
        QuickFeedPreset("GTA V Mods", SOURCE_GTA_MODS, StoreCategory.MOD),
        QuickFeedPreset("Call of Duty", SOURCE_COD_MODS, StoreCategory.SCRIPT),
        QuickFeedPreset("Patches XML", "illusionyy/PS-Game-Patch", StoreCategory.MOD),
        QuickFeedPreset("Cheats CUSA", "GoldHEN/GoldHEN_Cheat_Repository", StoreCategory.SCRIPT),
        QuickFeedPreset("Plugins (.prx)", "GoldHEN/GoldHEN_Plugins_Repository", StoreCategory.MOD),
        QuickFeedPreset("PKGs Homebrew", SOURCE_PKG_BUNDLES, StoreCategory.PKG),
        QuickFeedPreset("Payloads (.bin)", "TheWizWikii/All-PS4-Payloads", StoreCategory.PAYLOAD),
        QuickFeedPreset("Texturas Juegos", SOURCE_POLYHAVEN_TEXTURES, StoreCategory.TEXTURE),
        QuickFeedPreset("Modelos 3D", "alecjacobson/common-3d-test-models", StoreCategory.MODEL_3D)
    )

    private val allowedAssetExtensions = setOf(
        "pkg", "prx", "sprx", "bin", "elf", "xml",
        "dds", "png", "jpg", "jpeg",
        "obj", "fbx", "stl",
        "lua", "sh", "py", "json", "shn", "mc4"
    )

    private val knownTitleIdGames = mapOf(
        "CUSA00419" to "Grand Theft Auto V",
        "CUSA00411" to "Grand Theft Auto V",
        "CUSA26616" to "GTA Vice City Definitive",
        "CUSA00803" to "Call of Duty: Advanced Warfare",
        "CUSA02624" to "Call of Duty: Black Ops III",
        "CUSA03005" to "Call of Duty: Black Ops III",
        "CUSA12433" to "Call of Duty: Black Ops 4",
        "CUSA12446" to "Call of Duty: Black Ops 4",
        "CUSA05282" to "Call of Duty: Infinite Warfare",
        "CUSA05369" to "Call of Duty: Infinite Warfare",
        "CUSA07820" to "Call of Duty: WWII",
        "CUSA08602" to "Call of Duty: WWII",
        "CUSA15240" to "Call of Duty: Modern Warfare",
        "CUSA20446" to "Call of Duty: Black Ops Cold War",
        "CUSA03041" to "Red Dead Redemption 2",
        "CUSA08519" to "Red Dead Redemption 2",
        "CUSA00900" to "Bloodborne",
        "CUSA00207" to "Bloodborne",
        "CUSA07408" to "God of War",
        "CUSA02299" to "Marvel's Spider-Man",
        "CUSA00265" to "Minecraft PS4",
        "CUSA07820" to "Call of Duty: WWII"
    )

    private val priorityTitleIds = listOf(
        "CUSA00419", "CUSA00411", "CUSA02624", "CUSA12433",
        "CUSA00803", "CUSA05282", "CUSA15240", "CUSA20446",
        "CUSA03041", "CUSA00900", "CUSA07408", "CUSA00265"
    )

    suspend fun fetchCuratedInternetCatalog(bearerToken: String = ""): Result<List<StoreItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val results = coroutineScope {
                    val gtaLotus = async {
                        fetchGitHubRepoResources("illusionyy/PS4-GTAV-Lotus-SPRX-Release", bearerToken, includeRepoTree = true, maxItems = 6)
                            .getOrElse { emptyList() }
                    }
                    val gtaBase = async {
                        fetchGitHubRepoResources("2much4u/PS4-GTA-V-Menu-Base", bearerToken, includeRepoTree = true, maxItems = 4)
                            .getOrElse { emptyList() }
                    }
                    val rdrBase = async {
                        fetchGitHubRepoResources("Ethylamine/PS4-RDR-2-Menu-Base", bearerToken, includeRepoTree = true, maxItems = 4)
                            .getOrElse { emptyList() }
                    }
                    val gamePatches = async {
                        fetchGitHubRepoResources("illusionyy/PS-Game-Patch", bearerToken, includeRepoTree = true, maxItems = 14)
                            .getOrElse { emptyList() }
                    }
                    val pkgItemz = async {
                        fetchGitHubRepoResources("LightningMods/Itemzflow", bearerToken, includeRepoTree = false, maxItems = 4)
                            .getOrElse { emptyList() }
                    }
                    val pkgApollo = async {
                        fetchGitHubRepoResources("bucanero/apollo-ps4", bearerToken, includeRepoTree = false, maxItems = 4)
                            .getOrElse { emptyList() }
                    }
                    val plugins = async {
                        fetchGitHubRepoResources("GoldHEN/GoldHEN_Plugins_Repository", bearerToken, includeRepoTree = true, maxItems = 10)
                            .getOrElse { emptyList() }
                    }
                    val cheats = async {
                        fetchGitHubRepoResources("GoldHEN/GoldHEN_Cheat_Repository", bearerToken, includeRepoTree = true, maxItems = 16)
                            .getOrElse { emptyList() }
                    }
                    val payloads = async {
                        fetchGitHubRepoResources("TheWizWikii/All-PS4-Payloads", bearerToken, includeRepoTree = true, maxItems = 8)
                            .getOrElse { emptyList() }
                    }
                    val textures = async {
                        fetchPolyHavenTextures(limit = 12).getOrElse { emptyList() }
                    }
                    val models = async {
                        fetchGitHubRepoResources("alecjacobson/common-3d-test-models", bearerToken, includeRepoTree = true, maxItems = 10)
                            .getOrElse { emptyList() }
                    }
                    awaitAll(
                        gtaLotus, gtaBase, rdrBase, gamePatches,
                        pkgItemz, pkgApollo, plugins, cheats, payloads,
                        textures, models
                    ).flatten()
                }
                if (results.isEmpty()) {
                    error("No internet resources could be fetched. Check network connectivity.")
                }
                results.distinctBy { it.downloadUrl }
            }
        }

    suspend fun fetchGtaVModsBundle(bearerToken: String = ""): Result<List<StoreItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val all = coroutineScope {
                    listOf(
                        async { fetchGitHubRepoResources("illusionyy/PS4-GTAV-Lotus-SPRX-Release", bearerToken, includeRepoTree = true, maxItems = 12).getOrElse { emptyList() } },
                        async { fetchGitHubRepoResources("2much4u/PS4-GTA-V-Menu-Base", bearerToken, includeRepoTree = true, maxItems = 12).getOrElse { emptyList() } },
                        async {
                            fetchGitHubRepoResources(
                                ownerRepo = "GoldHEN/GoldHEN_Cheat_Repository",
                                bearerToken = bearerToken,
                                includeRepoTree = true,
                                maxItems = 20,
                                pathFilterKeywords = listOf("CUSA00419", "CUSA00411", "CUSA26616", "GTA")
                            ).getOrElse { emptyList() }
                        }
                    ).awaitAll().flatten()
                }
                if (all.isEmpty()) error("No GTA V assets retrieved from GitHub")
                all.distinctBy { it.downloadUrl }
            }
        }

    suspend fun fetchCallOfDutyBundle(bearerToken: String = ""): Result<List<StoreItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val codKeys = listOf(
                    "CUSA00803", "CUSA02624", "CUSA03005", "CUSA12433", "CUSA12446",
                    "CUSA05282", "CUSA05369", "CUSA07820", "CUSA08602", "CUSA15240", "CUSA20446",
                    "BlackOps", "ModernWarfare", "CallofDuty", "COD"
                )
                val all = coroutineScope {
                    listOf(
                        async {
                            fetchGitHubRepoResources(
                                ownerRepo = "GoldHEN/GoldHEN_Cheat_Repository",
                                bearerToken = bearerToken,
                                includeRepoTree = true,
                                maxItems = 24,
                                pathFilterKeywords = codKeys
                            ).getOrElse { emptyList() }
                        },
                        async {
                            fetchGitHubRepoResources(
                                ownerRepo = "illusionyy/PS-Game-Patch",
                                bearerToken = bearerToken,
                                includeRepoTree = true,
                                maxItems = 20,
                                pathFilterKeywords = codKeys
                            ).getOrElse { emptyList() }
                        },
                        async {
                            fetchGitHubRepoResources(
                                ownerRepo = "GoldHEN/GoldHEN_Plugins_Repository",
                                bearerToken = bearerToken,
                                includeRepoTree = true,
                                maxItems = 10
                            ).getOrElse { emptyList() }
                        }
                    ).awaitAll().flatten()
                }
                if (all.isEmpty()) error("No Call of Duty assets retrieved from GitHub")
                all.distinctBy { it.downloadUrl }
            }
        }

    suspend fun fetchHomebrewPkgBundle(bearerToken: String = ""): Result<List<StoreItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val items = coroutineScope {
                    listOf(
                        async { fetchGitHubRepoResources("LightningMods/Itemzflow", bearerToken, includeRepoTree = false, maxItems = 10).getOrElse { emptyList() } },
                        async { fetchGitHubRepoResources("bucanero/apollo-ps4", bearerToken, includeRepoTree = false, maxItems = 10).getOrElse { emptyList() } },
                        async { fetchGitHubRepoResources("OpenOrbis/OpenOrbis-PS4-Toolchain", bearerToken, includeRepoTree = false, maxItems = 6).getOrElse { emptyList() } }
                    ).awaitAll().flatten()
                }
                if (items.isEmpty()) error("No PKG releases retrieved from GitHub")
                items.distinctBy { it.downloadUrl }
            }
        }

    suspend fun fetchPolyHavenTextures(limit: Int = 16): Result<List<StoreItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val listReq = Request.Builder()
                    .url("https://api.polyhaven.com/assets?type=textures")
                    .header("Accept", "application/json")
                    .header("User-Agent", "PKGForge-Android/1.0")
                    .build()
                val rawList = httpClient.newCall(listReq).execute().use { resp ->
                    if (!resp.isSuccessful) error("Poly Haven HTTP ${resp.code}")
                    resp.body?.string().orEmpty()
                }
                val root = JSONObject(rawList)
                val slugs = mutableListOf<String>()
                val keys = root.keys()
                while (keys.hasNext() && slugs.size < limit * 2) {
                    slugs.add(keys.next())
                }
                val selectedSlugs = slugs.take(limit)

                coroutineScope {
                    selectedSlugs.map { slug ->
                        async {
                            runCatching {
                                val meta = root.optJSONObject(slug)
                                val displayName = meta?.optString("name", slug)?.ifBlank { slug } ?: slug
                                val thumbUrl = meta?.optString("thumbnail_url", "")?.takeIf { it.startsWith("https://") }
                                    ?: "https://cdn.polyhaven.com/asset_img/thumbs/$slug.png?width=256&height=256"
                                val categoriesArr = meta?.optJSONArray("categories")
                                val catTag = if (categoriesArr != null && categoriesArr.length() > 0) {
                                    categoriesArr.optString(0, "Material")
                                } else {
                                    "PBR Texture"
                                }

                                val filesReq = Request.Builder()
                                    .url("https://api.polyhaven.com/files/$slug")
                                    .header("Accept", "application/json")
                                    .header("User-Agent", "PKGForge-Android/1.0")
                                    .build()
                                val filesRaw = httpClient.newCall(filesReq).execute().use { resp ->
                                    if (!resp.isSuccessful) return@runCatching null
                                    resp.body?.string().orEmpty()
                                }
                                val filesJson = JSONObject(filesRaw)
                                val res1k = filesJson.optJSONObject("1k") ?: filesJson.optJSONObject("2k")
                                val diffuseMap = res1k?.optJSONObject("Diffuse")
                                    ?: res1k?.optJSONObject("color")
                                    ?: res1k?.optJSONObject("albedo")
                                    ?: res1k?.keys()?.asSequence()?.firstOrNull()?.let { res1k.optJSONObject(it) }
                                    ?: return@runCatching null

                                val chosenObj = if (diffuseMap.has("url")) {
                                    diffuseMap
                                } else {
                                    diffuseMap.optJSONObject("png")
                                        ?: diffuseMap.optJSONObject("jpg")
                                        ?: diffuseMap.keys().asSequence().firstOrNull()?.let { diffuseMap.optJSONObject(it) }
                                } ?: return@runCatching null

                                val downloadUrl = chosenObj.optString("url", "")
                                if (!downloadUrl.startsWith("https://")) return@runCatching null
                                val size = chosenObj.optLong("size", 0L)
                                val fileName = downloadUrl.substringAfterLast('/').substringBefore('?').ifBlank { "${slug}_1k.jpg" }

                                StoreItem(
                                    id = "ph_$slug",
                                    title = "$displayName (1K Game Texture)",
                                    category = StoreCategory.TEXTURE.name,
                                    gameTitle = "Game PBR • $catTag",
                                    titleId = "CUSA00000",
                                    version = "1K CC0",
                                    fileName = fileName,
                                    targetConsolePath = StoreCategory.TEXTURE.defaultConsoleDir,
                                    downloadUrl = downloadUrl,
                                    sizeBytes = size,
                                    description = "Public domain CC0 $catTag texture from Poly Haven. Edit colors/scale in Studio or upload to PS4.",
                                    publishedStatus = "PUBLISHED",
                                    authorEmail = "PolyHaven CC0",
                                    previewImageUrl = thumbUrl
                                )
                            }.getOrNull()
                        }
                    }.awaitAll().filterNotNull()
                }
            }
        }

    suspend fun fetchGitHubRepoResources(
        ownerRepo: String,
        bearerToken: String = "",
        includeRepoTree: Boolean = true,
        maxItems: Int = 40,
        pathFilterKeywords: List<String> = emptyList()
    ): Result<List<StoreItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val parts = ownerRepo.trim().split('/').filter { it.isNotBlank() }
            require(parts.size == 2) { "Use owner/repo format (e.g. GoldHEN/GoldHEN_Plugins_Repository)" }
            val owner = parts[0]
            val repo = parts[1]
            val ownerAvatar = "https://github.com/$owner.png?size=200"

            val items = mutableListOf<StoreItem>()

            // 1. Releases API
            if (pathFilterKeywords.isEmpty()) {
                runCatching {
                    val url = "https://api.github.com/repos/$owner/$repo/releases?per_page=10"
                    val bodyStr = executeGitHubGet(url, bearerToken)
                    val releases = JSONArray(bodyStr)
                    for (i in 0 until releases.length()) {
                        if (items.size >= maxItems) break
                        val rel = releases.getJSONObject(i)
                        val tagName = rel.optString("tag_name", "1.0.0")
                        val relName = rel.optString("name", tagName).ifBlank { tagName }
                        val desc = rel.optString("body", "").take(280)
                        val assets = rel.optJSONArray("assets") ?: continue
                        for (j in 0 until assets.length()) {
                            if (items.size >= maxItems) break
                            val asset = assets.getJSONObject(j)
                            val assetName = asset.optString("name", "").ifBlank { continue }
                            val downloadUrl = asset.optString("browser_download_url", "")
                            if (!downloadUrl.startsWith("https://")) continue
                            val size = asset.optLong("size", 0L)
                            val cat = inferCategoryFromFileName(assetName, repo)
                            val titleIdMatch = Regex("(CUSA\\d{5}|PPSA\\d{5})", RegexOption.IGNORE_CASE)
                                .find("$assetName $relName $desc $repo")?.value?.uppercase()
                                ?: inferDefaultTitleIdForRepo(repo)
                            val thumb = if (assetName.endsWith(".png", true) || assetName.endsWith(".jpg", true)) {
                                downloadUrl
                            } else {
                                ownerAvatar
                            }
                            items.add(
                                StoreItem(
                                    id = "gh_${owner}_${repo}_${asset.optLong("id", (i * 100 + j).toLong())}",
                                    title = "$relName • $assetName".take(96),
                                    category = cat.name,
                                    gameTitle = inferGameLabel(owner, repo, assetName, titleIdMatch),
                                    titleId = titleIdMatch,
                                    version = tagName.take(28),
                                    fileName = assetName.take(120),
                                    targetConsolePath = targetDirFor(cat, assetName),
                                    downloadUrl = downloadUrl,
                                    sizeBytes = size,
                                    description = desc.ifBlank { "GitHub release asset from $owner/$repo" },
                                    publishedStatus = "PUBLISHED",
                                    authorEmail = owner,
                                    previewImageUrl = thumb
                                )
                            )
                        }
                    }
                }
            }

            // 2. Repo Tree API
            if (includeRepoTree && items.size < maxItems) {
                runCatching {
                    val treeUrl = "https://api.github.com/repos/$owner/$repo/git/trees/HEAD?recursive=1"
                    val treeRaw = executeGitHubGet(treeUrl, bearerToken)
                    val treeArray = JSONObject(treeRaw).optJSONArray("tree") ?: JSONArray()

                    val allCandidates = mutableListOf<Pair<String, Long>>()
                    for (i in 0 until treeArray.length()) {
                        val node = treeArray.getJSONObject(i)
                        if (node.optString("type") != "blob") continue
                        val path = node.optString("path", "")
                        if (path.startsWith(".github/") || path.startsWith(".vscode/")) continue
                        val fileName = path.substringAfterLast('/')
                        if (fileName.equals("package.json", true) ||
                            fileName.equals("tsconfig.json", true) ||
                            fileName.equals("settings.json", true) ||
                            fileName.equals("manifest.json", true)
                        ) continue
                        val ext = fileName.substringAfterLast('.', "").lowercase()
                        if (ext !in allowedAssetExtensions) continue
                        if (pathFilterKeywords.isNotEmpty() &&
                            pathFilterKeywords.none { kw -> path.contains(kw, ignoreCase = true) }
                        ) continue
                        allCandidates.add(path to node.optLong("size", 0L))
                    }

                    // Prioritize well-known games (GTA V, CoD, RDR2, Bloodborne, GoW) first, then sample remaining
                    val prioritized = allCandidates.filter { (path, _) ->
                        priorityTitleIds.any { id -> path.contains(id, ignoreCase = true) } ||
                            path.contains("GTA", true) || path.contains("Call", true)
                    }
                    val others = allCandidates.filterNot { it in prioritized }
                    val remaining = (maxItems - items.size).coerceAtLeast(0)
                    val combined = mutableListOf<Pair<String, Long>>()
                    combined.addAll(prioritized.take(remaining))
                    if (combined.size < remaining && others.isNotEmpty()) {
                        val needed = remaining - combined.size
                        if (others.size <= needed) {
                            combined.addAll(others)
                        } else {
                            val step = (others.size.toFloat() / needed).coerceAtLeast(1f)
                            (0 until needed).forEach { idx ->
                                combined.add(others[(idx * step).toInt().coerceIn(0, others.lastIndex)])
                            }
                        }
                    }

                    combined.forEachIndexed { idx, (path, size) ->
                        val fileName = path.substringAfterLast('/')
                        val encodedPath = path.split('/').joinToString("/") { seg ->
                            URLEncoder.encode(seg, "UTF-8").replace("+", "%20")
                        }
                        val rawUrl = "https://raw.githubusercontent.com/$owner/$repo/HEAD/$encodedPath"
                        val cat = inferCategoryFromFileName(fileName, "$repo/$path")
                        val titleIdMatch = Regex("(CUSA\\d{5}|PPSA\\d{5})", RegexOption.IGNORE_CASE)
                            .find("$path $repo")?.value?.uppercase()
                            ?: inferDefaultTitleIdForRepo(repo)
                        val gameLabel = inferGameLabel(owner, repo, path, titleIdMatch)
                        val cleanDisplay = fileName.substringBeforeLast('.')
                            .replace('_', ' ')
                            .replace('-', ' ')
                            .trim()
                            .ifBlank { fileName }
                        val titleText = if (titleIdMatch != "CUSA00000" && !cleanDisplay.contains(gameLabel, true)) {
                            "$gameLabel • $cleanDisplay"
                        } else {
                            cleanDisplay
                        }
                        val thumb = if (cat == StoreCategory.TEXTURE) rawUrl else ownerAvatar

                        items.add(
                            StoreItem(
                                id = "tree_${owner}_${repo}_${idx}_${path.hashCode()}",
                                title = titleText.take(96),
                                category = cat.name,
                                gameTitle = gameLabel,
                                titleId = titleIdMatch,
                                version = "HEAD",
                                fileName = fileName.take(120),
                                targetConsolePath = targetDirFor(cat, fileName),
                                downloadUrl = rawUrl,
                                sizeBytes = size,
                                description = "Live file from github.com/$owner/$repo ($path)",
                                publishedStatus = "PUBLISHED",
                                authorEmail = owner,
                                previewImageUrl = thumb
                            )
                        )
                    }
                }
            }

            if (items.isEmpty()) {
                error("No downloadable assets found in $owner/$repo")
            }
            items.distinctBy { it.downloadUrl }
        }
    }

    private fun executeGitHubGet(url: String, bearerToken: String): String {
        val reqBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "PKGForge-Android/1.0")
        if (bearerToken.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer ${bearerToken.trim()}")
        }
        return httpClient.newCall(reqBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful) error("GitHub HTTP ${resp.code}: ${resp.message}")
            resp.body?.string().orEmpty()
        }
    }

    suspend fun fetchJsonManifestFeed(manifestUrl: String): Result<List<StoreItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = manifestUrl.trim()
            require(clean.startsWith("https://")) { "Manifest URL must start with https://" }
            val req = Request.Builder()
                .url(clean)
                .header("Accept", "application/json")
                .header("User-Agent", "PKGForge-Android/1.0")
                .build()
            val raw = httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Feed HTTP ${resp.code}")
                resp.body?.string().orEmpty()
            }
            val array = if (raw.trimStart().startsWith("[")) {
                JSONArray(raw)
            } else {
                val obj = JSONObject(raw)
                obj.optJSONArray("items")
                    ?: obj.optJSONArray("packages")
                    ?: obj.optJSONArray("entries")
                    ?: error("JSON feed missing items/packages array")
            }
            val out = mutableListOf<StoreItem>()
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val rawUrl = o.optString("downloadUrl", o.optString("url", o.optString("package", "")))
                val url = if (rawUrl.startsWith("http://")) "https://" + rawUrl.removePrefix("http://") else rawUrl
                val inline = o.optString("inlineContent", o.optString("script", ""))
                if (!url.startsWith("https://") && inline.isBlank()) continue
                val title = o.optString("title", o.optString("name", "Resource #$i")).take(100)
                val fileName = o.optString(
                    "fileName",
                    url.substringAfterLast('/').substringBefore('?').ifBlank {
                        title.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".pkg"
                    }
                )
                val rawThumb = o.optString("previewImageUrl", o.optString("image", o.optString("main_icon_path", "")))
                val thumb = if (rawThumb.startsWith("http://")) "https://" + rawThumb.removePrefix("http://") else rawThumb
                val explicitCat = StoreCategory.entries.firstOrNull {
                    it.name.equals(o.optString("category"), ignoreCase = true)
                }
                val cat = explicitCat ?: inferCategoryFromFileName(fileName, o.optString("apptype", ""))
                val titleId = o.optString(
                    "titleId",
                    o.optString("title_id", o.optString("id", "CUSA00000"))
                ).uppercase().take(16)
                out.add(
                    StoreItem(
                        id = "feed_${i}_${fileName.hashCode()}",
                        title = title,
                        category = cat.name,
                        gameTitle = o.optString("gameTitle", o.optString("game", o.optString("apptype", "PS4 Store"))).take(80),
                        titleId = titleId,
                        version = o.optString("version", "1.0.0").take(30),
                        fileName = fileName.take(120),
                        targetConsolePath = o.optString("targetConsolePath", targetDirFor(cat, fileName)),
                        downloadUrl = url,
                        inlineContent = inline,
                        sha256 = o.optString("sha256", ""),
                        sizeBytes = o.optLong("sizeBytes", o.optLong("size", 0L)),
                        description = o.optString("description", o.optString("desc", "")).take(600),
                        publishedStatus = "PUBLISHED",
                        authorEmail = o.optString("author", o.optString("Author", "External Feed")),
                        previewImageUrl = thumb.takeIf { it.startsWith("https://") }.orEmpty()
                    )
                )
            }
            out
        }
    }

    suspend fun downloadItemBytes(
        item: StoreItem,
        maxBytes: Int = 24 * 1024 * 1024,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            if (item.inlineContent.isNotEmpty() && !item.downloadUrl.startsWith("https://")) {
                val bytes = item.inlineContent.toByteArray(Charsets.UTF_8)
                verifySha256IfPresent(bytes, item.sha256)
                onProgress(bytes.size.toLong(), bytes.size.toLong())
                return@runCatching bytes
            }
            val baos = ByteArrayOutputStream()
            streamUrlToOutput(item.downloadUrl, baos, maxBytes.toLong(), onProgress).getOrThrow()
            val bytes = baos.toByteArray()
            verifySha256IfPresent(bytes, item.sha256)
            bytes
        }
    }

    suspend fun downloadItemToStream(
        item: StoreItem,
        output: OutputStream,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            if (item.inlineContent.isNotEmpty() && !item.downloadUrl.startsWith("https://")) {
                val bytes = item.inlineContent.toByteArray(Charsets.UTF_8)
                verifySha256IfPresent(bytes, item.sha256)
                output.write(bytes)
                output.flush()
                onProgress(bytes.size.toLong(), bytes.size.toLong())
                return@runCatching bytes.size.toLong()
            }
            streamUrlToOutput(item.downloadUrl, output, Long.MAX_VALUE, onProgress).getOrThrow()
        }
    }

    private fun streamUrlToOutput(
        url: String,
        output: OutputStream,
        maxBytes: Long,
        onProgress: (Long, Long) -> Unit
    ): Result<Long> = runCatching {
        require(url.startsWith("https://")) { "Only HTTPS download URLs are supported" }
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "PKGForge-Android/1.0")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Download failed HTTP ${resp.code}")
            val body = resp.body ?: error("Empty download body")
            val total = body.contentLength()
            var copied = 0L
            val buf = ByteArray(32768)
            body.byteStream().use { ins ->
                var n: Int
                while (ins.read(buf).also { n = it } != -1) {
                    if (copied + n > maxBytes) {
                        error("File exceeds in-memory studio cap ($maxBytes bytes). Use Download (SAF) or Direct FTP Stream.")
                    }
                    output.write(buf, 0, n)
                    copied += n
                    onProgress(copied, total)
                }
            }
            output.flush()
            copied
        }
    }

    suspend fun deployItemToConsole(
        item: StoreItem,
        host: String,
        ftpPort: Int,
        binLoaderPort: Int,
        username: String,
        password: String,
        sendAsBinPayload: Boolean,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = downloadItemBytes(item, maxBytes = 48 * 1024 * 1024, onProgress = onProgress).getOrThrow()
            if (sendAsBinPayload || (item.category == StoreCategory.PAYLOAD.name && item.fileName.endsWith(".bin", true))) {
                val sent = Ps4DebugBridge.sendBinPayload(host, binLoaderPort, bytes).getOrThrow()
                return@runCatching "Payload '${item.fileName}' ($sent B) injected to $host:$binLoaderPort"
            }
            val cleanDir = item.targetConsolePath.trim().ifBlank { "/data/pkg" }.let {
                if (it.startsWith("/")) it else "/$it"
            }.trimEnd('/').ifEmpty { "/" }
            val safeFile = item.fileName.substringAfterLast('/').ifBlank { "asset.bin" }
            val remotePath = if (cleanDir == "/") "/$safeFile" else "$cleanDir/$safeFile"

            val ftp = Ps4FtpClient()
            try {
                ftp.connect(host, ftpPort, username, password).getOrThrow()
                if (cleanDir != "/") {
                    var acc = ""
                    cleanDir.split('/').filter { it.isNotBlank() }.forEach { seg ->
                        acc += "/$seg"
                        ftp.makeDirectory(acc)
                    }
                }
                val uploaded = ftp.uploadFile(remotePath, ByteArrayInputStream(bytes)) { b ->
                    onProgress(b, bytes.size.toLong())
                }.getOrThrow()
                "Deployed '$safeFile' ($uploaded B) -> $host:$remotePath"
            } finally {
                ftp.disconnect()
            }
        }
    }

    fun inferCategoryFromFileName(name: String, contextHint: String = ""): StoreCategory {
        val lower = name.lowercase()
        val hint = contextHint.lowercase()
        return when {
            lower.endsWith(".pkg") -> StoreCategory.PKG
            lower.endsWith(".dds") || lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> StoreCategory.TEXTURE
            lower.endsWith(".obj") || lower.endsWith(".fbx") || lower.endsWith(".stl") -> StoreCategory.MODEL_3D
            lower.endsWith(".lua") || lower.endsWith(".sh") || lower.endsWith(".py") ||
                lower.endsWith(".json") || lower.endsWith(".shn") || lower.endsWith(".mc4") ||
                lower.endsWith(".xml") -> StoreCategory.SCRIPT
            lower.endsWith(".bin") || lower.endsWith(".elf") -> StoreCategory.PAYLOAD
            lower.endsWith(".prx") || lower.endsWith(".sprx") || hint.contains("plugin") -> StoreCategory.MOD
            hint.contains("cheat") -> StoreCategory.SCRIPT
            hint.contains("payload") -> StoreCategory.PAYLOAD
            else -> StoreCategory.MOD
        }
    }

    private fun targetDirFor(cat: StoreCategory, fileName: String): String {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".json") -> "/data/GoldHEN/cheats/json"
            lower.endsWith(".shn") -> "/data/GoldHEN/cheats/shn"
            lower.endsWith(".mc4") -> "/data/GoldHEN/cheats/mc4"
            lower.endsWith(".xml") -> "/data/GoldHEN/patches/xml"
            lower.endsWith(".prx") || lower.endsWith(".sprx") -> "/data/GoldHEN/plugins"
            else -> cat.defaultConsoleDir
        }
    }

    private fun inferDefaultTitleIdForRepo(repo: String): String {
        val r = repo.lowercase()
        return when {
            r.contains("gta") -> "CUSA00419"
            r.contains("rdr") -> "CUSA03041"
            else -> "CUSA00000"
        }
    }

    private fun inferGameLabel(owner: String, repo: String, path: String, titleId: String): String {
        knownTitleIdGames[titleId]?.let { return it }
        val r = repo.lowercase()
        return when {
            r.contains("gtav") || r.contains("gta-v") -> "Grand Theft Auto V"
            r.contains("rdr") -> "Red Dead Redemption 2"
            r.contains("itemzflow") -> "Itemzflow Homebrew"
            r.contains("apollo") -> "Apollo Save Tool"
            titleId != "CUSA00000" -> "$repo ($titleId)"
            else -> {
                val folder = path.substringBeforeLast('/', "").substringAfterLast('/').ifBlank { repo }
                "$owner/$folder"
            }
        }
    }

    private fun verifySha256IfPresent(bytes: ByteArray, expectedHex: String) {
        val cleanExpected = expectedHex.trim().lowercase()
        if (cleanExpected.length < 32) return
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val actual = digest.joinToString("") { "%02x".format(it) }
        if (actual != cleanExpected) {
            error("SHA-256 mismatch! Expected $cleanExpected, got $actual")
        }
    }
}
