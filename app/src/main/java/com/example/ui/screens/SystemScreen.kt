package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.UiState
import com.example.data.UserPreferences
import com.example.engine.UpdateNotifier
import com.example.model.ActivityLogEntry
import com.example.model.AppRelease
import com.example.ui.components.GlassMetricTile
import com.example.ui.components.GlassStatusPill
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.PermissionLockedPanel
import com.example.ui.components.SakuraCreditsCard
import com.example.ui.components.SectionHeaderRow
import com.example.ui.components.lStr
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IridescentIndigo
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.net.Inet4Address
import java.net.NetworkInterface

@Composable
fun SystemScreen(
    preferences: UserPreferences,
    activityLogsState: UiState<List<ActivityLogEntry>>,
    releasesState: UiState<List<AppRelease>>,
    currentVersionCode: Long,
    currentVersionName: String,
    roleName: String,
    canViewDiagnostics: Boolean,
    canCheckUpdates: Boolean,
    onSetTheme: (AppThemeMode) -> Unit,
    onSetLanguage: (String) -> Unit,
    onSetGlassOpacity: (Float) -> Unit,
    onSetPushAlerts: (Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    var activeTab by remember { mutableIntStateOf(0) }
    var logCategoryFilter by remember { mutableStateOf("ALL") }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val logExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val logs = (activityLogsState as? UiState.Success)?.data.orEmpty()
        if (uri != null) {
            val text = logs.joinToString("\n") { entry ->
                "[${entry.severity}] [${entry.category}] ${entry.action} — ${entry.details} (${entry.actorEmail})"
            }
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(text.toByteArray(Charsets.UTF_8))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                lStr(R.string.sys_tab_diag),
                lStr(R.string.sys_tab_logs),
                lStr(R.string.sys_tab_updates),
                lStr(R.string.sys_tab_settings)
            ).forEachIndexed { idx, label ->
                FilterChip(
                    selected = activeTab == idx,
                    onClick = { activeTab = idx },
                    label = { Text(label) },
                    modifier = Modifier.testTag("sys_subtab_$idx")
                )
            }
        }

        when (activeTab) {
            0 -> {
                if (!canViewDiagnostics) {
                    PermissionLockedPanel(roleName, "DIAGNOSTICS_LOGS")
                } else {
                    DiagnosticsPanel()
                }
            }

            1 -> {
                if (!canViewDiagnostics) {
                    PermissionLockedPanel(roleName, "DIAGNOSTICS_LOGS")
                } else {
                    ActivityLogsPanel(
                        logsState = activityLogsState,
                        categoryFilter = logCategoryFilter,
                        onSelectCategory = { logCategoryFilter = it },
                        onExport = { logExporter.launch("pkg_forge_activity_log.txt") }
                    )
                }
            }

            2 -> {
                if (!canCheckUpdates) {
                    PermissionLockedPanel(roleName, "APP_UPDATES")
                } else {
                    UpdatesPanel(
                        releasesState = releasesState,
                        currentVersionCode = currentVersionCode,
                        currentVersionName = currentVersionName,
                        onRequestNotifPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onTriggerLocalAlert = { rel ->
                            UpdateNotifier.showReleaseNotification(context, rel)
                        },
                        onOpenUrl = { url ->
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            }
                        }
                    )
                }
            }

            3 -> {
                SettingsPanel(
                    preferences = preferences,
                    onSetTheme = onSetTheme,
                    onSetLanguage = onSetLanguage,
                    onSetGlassOpacity = onSetGlassOpacity,
                    onSetPushAlerts = onSetPushAlerts,
                    onSignOut = onSignOut
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DiagnosticsPanel() {
    val runtime = Runtime.getRuntime()
    val maxMb = (runtime.maxMemory() / (1024 * 1024)).coerceAtLeast(1L)
    val usedMb = ((runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)).coerceAtLeast(1L)
    val ratio = (usedMb.toFloat() / maxMb.toFloat()).coerceIn(0f, 1f)

    val statFs = remember { StatFs(Environment.getDataDirectory().path) }
    val availGb = (statFs.availableBytes / (1024f * 1024f * 1024f))
    val localIp = remember {
        runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                ?.hostAddress ?: "No LAN IP"
        }.getOrDefault("Unavailable")
    }

    LiquidGlassCard(accentColor = ElectricCyan) {
        SectionHeaderRow(
            title = "Runtime & Hardware Diagnostics",
            subtitle = "${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE}",
            icon = Icons.Rounded.BugReport
        )
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Canvas(modifier = Modifier.size(96.dp)) {
                val stroke = 12.dp.toPx()
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val tl = Offset(stroke / 2, stroke / 2)
                drawArc(
                    color = Color.White.copy(alpha = 0.12f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = tl,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = ElectricCyan,
                    startAngle = -90f,
                    sweepAngle = 360f * ratio,
                    useCenter = false,
                    topLeft = tl,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("JVM Heap: $usedMb / $maxMb MB", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("LAN IPv4: $localIp", fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                Text("ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                Text("Free Storage: ${"%.1f".format(availGb)} GB", style = MaterialTheme.typography.bodySmall, color = EmeraldSuccess)
            }
        }
    }
}

@Composable
private fun ActivityLogsPanel(
    logsState: UiState<List<ActivityLogEntry>>,
    categoryFilter: String,
    onSelectCategory: (String) -> Unit,
    onExport: () -> Unit
) {
    LiquidGlassCard(accentColor = IridescentIndigo) {
        SectionHeaderRow(
            title = "Detailed Activity Log",
            icon = Icons.Rounded.BugReport,
            trailing = {
                OutlinedButton(onClick = onExport) {
                    Icon(Icons.Rounded.Download, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Export")
                }
            }
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("ALL", "PKG", "STORE", "FTP", "HACK", "VCS", "RBAC", "UPDATE").forEach { cat ->
                FilterChip(
                    selected = categoryFilter == cat,
                    onClick = { onSelectCategory(cat) },
                    label = { Text(cat) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        when (logsState) {
            is UiState.Loading -> Text("Syncing logs...")
            is UiState.Error -> Text(logsState.message, color = CoralError)
            is UiState.Success -> {
                val filtered = logsState.data.filter { categoryFilter == "ALL" || it.category == categoryFilter }
                if (filtered.isEmpty()) {
                    Text("No activity logs for filter '$categoryFilter'.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        filtered.forEach { log ->
                            val sevColor = when (log.severity) {
                                "ERROR" -> CoralError
                                "WARN" -> AmberWarning
                                else -> EmeraldSuccess
                            }
                            LiquidGlassCard(shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("${log.category} • ${log.action}", style = MaterialTheme.typography.titleSmall)
                                    GlassStatusPill(log.severity, sevColor)
                                }
                                Text(log.details, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdatesPanel(
    releasesState: UiState<List<AppRelease>>,
    currentVersionCode: Long,
    currentVersionName: String,
    onRequestNotifPermission: () -> Unit,
    onTriggerLocalAlert: (AppRelease) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val context = LocalContext.current
    val hasPerm = UpdateNotifier.hasNotificationPermission(context)

    LiquidGlassCard(accentColor = ElectricCyan) {
        SectionHeaderRow(
            title = lStr(R.string.sys_tab_updates),
            subtitle = "Installed: v$currentVersionName (build $currentVersionCode)",
            icon = Icons.Rounded.SystemUpdate
        )
        Spacer(Modifier.height(10.dp))
        if (!hasPerm) {
            OutlinedButton(onClick = onRequestNotifPermission, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Notifications, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Enable Push Update Notifications")
            }
            Spacer(Modifier.height(8.dp))
        }

        when (releasesState) {
            is UiState.Loading -> Text("Checking release channel...")
            is UiState.Error -> Text(releasesState.message, color = CoralError)
            is UiState.Success -> {
                val latest = releasesState.data.firstOrNull()
                if (latest == null || latest.versionCode <= currentVersionCode) {
                    GlassStatusPill(lStr(R.string.update_up_to_date, currentVersionName), EmeraldSuccess)
                } else {
                    GlassStatusPill(lStr(R.string.update_available_banner, latest.versionName), AmberWarning)
                }
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    releasesState.data.forEach { rel ->
                        LiquidGlassCard(shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("v${rel.versionName} (build ${rel.versionCode})", style = MaterialTheme.typography.titleSmall)
                                GlassStatusPill(rel.channel, if (rel.requiredUpdate) CoralError else ElectricCyan)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(rel.changelog, style = MaterialTheme.typography.bodySmall)
                            if (rel.checksumSha256.isNotBlank()) {
                                Text("SHA-256: ${rel.checksumSha256}", fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onOpenUrl(rel.downloadUrl) }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.OpenInNew, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Open Release")
                                }
                                OutlinedButton(onClick = { onTriggerLocalAlert(rel) }) {
                                    Icon(Icons.Rounded.Notifications, "Test Push Alert", Modifier.size(16.dp))
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
private fun SettingsPanel(
    preferences: UserPreferences,
    onSetTheme: (AppThemeMode) -> Unit,
    onSetLanguage: (String) -> Unit,
    onSetGlassOpacity: (Float) -> Unit,
    onSetPushAlerts: (Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    SakuraCreditsCard()

    LiquidGlassCard(accentColor = ElectricCyan) {
        SectionHeaderRow(title = lStr(R.string.settings_language), icon = Icons.Rounded.Language)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "system" to "System",
                "en" to "English",
                "es" to "Español",
                "pt" to "Português",
                "fr" to "Français"
            ).forEach { (tag, label) ->
                FilterChip(
                    selected = preferences.languageTag == tag,
                    onClick = { onSetLanguage(tag) },
                    label = { Text(label) },
                    modifier = Modifier.testTag("lang_chip_$tag")
                )
            }
        }
    }

    LiquidGlassCard(accentColor = IridescentIndigo) {
        SectionHeaderRow(title = lStr(R.string.settings_theme), icon = Icons.Rounded.Palette)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                AppThemeMode.DARK_GLASS to "Obsidian Glass",
                AppThemeMode.LIGHT_GLASS to "Frosted Ice",
                AppThemeMode.SYSTEM_DYNAMIC to "Material You"
            ).forEach { (mode, label) ->
                FilterChip(
                    selected = preferences.themeMode == mode,
                    onClick = { onSetTheme(mode) },
                    label = { Text(label) }
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "${lStr(R.string.settings_glass_opacity)}: ${(preferences.glassOpacity * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium
        )
        Slider(
            value = preferences.glassOpacity,
            onValueChange = onSetGlassOpacity,
            valueRange = 0.28f..0.90f
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Push Update Alerts", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = preferences.pushUpdateAlertsEnabled, onCheckedChange = onSetPushAlerts)
        }
    }

    LiquidGlassCard(accentColor = CoralError) {
        SectionHeaderRow(title = "Session", icon = Icons.Rounded.Settings)
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onSignOut,
            colors = ButtonDefaults.buttonColors(containerColor = CoralError, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().testTag("sign_out_button"),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.AutoMirrored.Rounded.Logout, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(lStr(R.string.sign_out))
        }
    }
}
