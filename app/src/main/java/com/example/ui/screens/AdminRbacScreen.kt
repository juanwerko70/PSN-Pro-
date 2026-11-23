package com.example.ui.screens

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
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.UiState
import com.example.model.ActiveStatus
import com.example.model.AppPermission
import com.example.model.AuditEvent
import com.example.model.UserAccessProfile
import com.example.model.UserRole
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

@Composable
fun AdminRbacScreen(
    myProfile: UserAccessProfile?,
    previewRoleOverride: UserRole?,
    allUsersState: UiState<List<UserAccessProfile>>,
    auditEventsState: UiState<List<AuditEvent>>,
    currentVersionCode: Long,
    onSetPreviewRole: (UserRole?) -> Unit,
    onUpdateUserAccess: (UserAccessProfile, UserRole, List<AppPermission>, ActiveStatus) -> Unit,
    onPublishRelease: (Long, String, String, String, String, String, Boolean) -> Unit
) {
    var activeTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedRoleFilter by remember { mutableStateOf<UserRole?>(null) }
    val isRealAdmin = myProfile?.parsedRole() == UserRole.ADMIN && myProfile.parsedStatus() == ActiveStatus.ACTIVE
    val canManageRoles = isRealAdmin || (myProfile?.hasPermission(AppPermission.MANAGE_ROLES) == true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Current User Live RBAC Profile
        LiquidGlassCard(accentColor = ElectricCyan) {
            SectionHeaderRow(
                title = lStr(R.string.admin_my_profile),
                subtitle = myProfile?.email ?: "Loading profile...",
                icon = Icons.Rounded.Person,
                trailing = {
                    val status = myProfile?.parsedStatus() ?: ActiveStatus.ACTIVE
                    GlassStatusPill(
                        text = myProfile?.role ?: "VIEWER",
                        color = if (status == ActiveStatus.SUSPENDED) CoralError else EmeraldSuccess
                    )
                }
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AppPermission.entries.forEach { perm ->
                    val granted = myProfile?.hasPermission(perm) == true
                    GlassStatusPill(
                        text = perm.label,
                        color = if (granted) EmeraldSuccess else CoralError.copy(alpha = 0.7f),
                        icon = if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.Block
                    )
                }
            }

            if (isRealAdmin) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Admin UI Role Preview (local preview only — does not alter permissions):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = previewRoleOverride == null,
                        onClick = { onSetPreviewRole(null) },
                        label = { Text("Actual (ADMIN)") }
                    )
                    listOf(UserRole.DEVELOPER, UserRole.OPERATOR, UserRole.VIEWER).forEach { r ->
                        FilterChip(
                            selected = previewRoleOverride == r,
                            onClick = { onSetPreviewRole(r) },
                            label = { Text("Preview ${r.name}") }
                        )
                    }
                }
            }
        }

        if (!canManageRoles) {
            PermissionLockedPanel(
                roleName = myProfile?.role ?: UserRole.VIEWER.name,
                permissionName = "MANAGE_ROLES"
            )
            Spacer(Modifier.height(24.dp))
            return
        }

        // Admin Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                lStr(R.string.admin_users_tab),
                lStr(R.string.admin_audit_tab),
                lStr(R.string.admin_releases_tab)
            ).forEachIndexed { idx, label ->
                FilterChip(
                    selected = activeTab == idx,
                    onClick = { activeTab = idx },
                    label = { Text(label) },
                    modifier = Modifier.testTag("admin_subtab_$idx")
                )
            }
        }

        when (activeTab) {
            0 -> {
                LiquidGlassCard(accentColor = IridescentIndigo) {
                    SectionHeaderRow(
                        title = "Real-Time RBAC Directory",
                        subtitle = "Changes propagate instantly to active devices via Firestore",
                        icon = Icons.Rounded.AdminPanelSettings
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search user by name or email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedRoleFilter == null,
                            onClick = { selectedRoleFilter = null },
                            label = { Text("All") }
                        )
                        UserRole.entries.forEach { role ->
                            FilterChip(
                                selected = selectedRoleFilter == role,
                                onClick = { selectedRoleFilter = role },
                                label = { Text(role.name) }
                            )
                        }
                    }
                }

                when (allUsersState) {
                    is UiState.Loading -> Text("Syncing users...", style = MaterialTheme.typography.bodyMedium)
                    is UiState.Error -> Text(allUsersState.message, color = CoralError)
                    is UiState.Success -> {
                        val filtered = allUsersState.data.filter { user ->
                            (selectedRoleFilter == null || user.parsedRole() == selectedRoleFilter) &&
                                (searchQuery.isBlank() ||
                                    user.email.contains(searchQuery, true) ||
                                    user.displayName.contains(searchQuery, true))
                        }
                        filtered.forEach { user ->
                            UserRbacEditorCard(
                                user = user,
                                isSelf = user.userId == myProfile?.userId,
                                onApply = onUpdateUserAccess
                            )
                        }
                    }
                }
            }

            1 -> {
                LiquidGlassCard(accentColor = ElectricCyan) {
                    SectionHeaderRow(
                        title = "RBAC & Security Audit Trail",
                        icon = Icons.Rounded.History
                    )
                    Spacer(Modifier.height(8.dp))
                    when (auditEventsState) {
                        is UiState.Loading -> Text("Loading audit trail...")
                        is UiState.Error -> Text(auditEventsState.message, color = CoralError)
                        is UiState.Success -> {
                            if (auditEventsState.data.isEmpty()) {
                                Text("No RBAC audit events recorded yet.", style = MaterialTheme.typography.bodySmall)
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    auditEventsState.data.forEach { evt ->
                                        LiquidGlassCard(shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(evt.eventType, style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
                                                Text(evt.targetEmail, style = MaterialTheme.typography.labelSmall)
                                            }
                                            Text(evt.summary, style = MaterialTheme.typography.bodySmall)
                                            Text(
                                                text = "By: ${evt.actorEmail}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                ReleasePublisherCard(
                    currentVersionCode = currentVersionCode,
                    onPublishRelease = onPublishRelease
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun UserRbacEditorCard(
    user: UserAccessProfile,
    isSelf: Boolean,
    onApply: (UserAccessProfile, UserRole, List<AppPermission>, ActiveStatus) -> Unit
) {
    var draftRole by remember(user.userId, user.role) { mutableStateOf(user.parsedRole()) }
    var draftStatus by remember(user.userId, user.activeStatus) { mutableStateOf(user.parsedStatus()) }
    var draftPerms by remember(user.userId, user.permissions) {
        mutableStateOf(
            user.permissions.mapNotNull { AppPermission.safeValueOf(it) }.toSet()
        )
    }

    LiquidGlassCard(
        accentColor = if (draftStatus == ActiveStatus.SUSPENDED) CoralError else IridescentIndigo
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.displayName + if (isSelf) " (You)" else "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = user.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "UID: ${user.userId.take(14)}...",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan
                )
            }
            GlassStatusPill(
                text = draftStatus.name,
                color = if (draftStatus == ActiveStatus.ACTIVE) EmeraldSuccess else CoralError
            )
        }

        Spacer(Modifier.height(10.dp))
        Text("Assign Role Preset:", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            UserRole.entries.forEach { role ->
                FilterChip(
                    selected = draftRole == role,
                    onClick = {
                        draftRole = role
                        draftPerms = role.defaultPermissions.toSet()
                    },
                    label = { Text(role.name) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Granular Feature Permissions:", style = MaterialTheme.typography.labelMedium)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AppPermission.entries.forEach { perm ->
                val checked = draftRole == UserRole.ADMIN || draftPerms.contains(perm)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(perm.label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            perm.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = checked,
                        enabled = draftRole != UserRole.ADMIN,
                        onCheckedChange = { enabled ->
                            draftPerms = if (enabled) draftPerms + perm else draftPerms - perm
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    draftStatus = if (draftStatus == ActiveStatus.ACTIVE) ActiveStatus.SUSPENDED else ActiveStatus.ACTIVE
                },
                enabled = !isSelf,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (draftStatus == ActiveStatus.ACTIVE) "Suspend User" else "Activate User")
            }
            Button(
                onClick = {
                    onApply(user, draftRole, draftPerms.toList(), draftStatus)
                },
                modifier = Modifier.weight(1f).testTag("apply_rbac_${user.userId}"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.Security, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Apply Live")
            }
        }
    }
}

@Composable
private fun ReleasePublisherCard(
    currentVersionCode: Long,
    onPublishRelease: (Long, String, String, String, String, String, Boolean) -> Unit
) {
    var versionCode by remember { mutableStateOf((currentVersionCode + 1L).toString()) }
    var versionName by remember { mutableStateOf("1.${currentVersionCode}.0") }
    var channel by remember { mutableStateOf("STABLE") }
    var changelog by remember { mutableStateOf("New PKG Studio patches, DDS/FBX enhancements, and real-time RBAC improvements.") }
    var downloadUrl by remember { mutableStateOf("https://github.com/pkgforge/releases") }
    var checksum by remember { mutableStateOf("") }
    var requiredUpdate by remember { mutableStateOf(false) }

    LiquidGlassCard(accentColor = EmeraldSuccess) {
        SectionHeaderRow(
            title = lStr(R.string.admin_publish_release),
            subtitle = "Pushes real-time update notification to all active clients",
            icon = Icons.Rounded.NotificationsActive
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = versionCode,
                onValueChange = { versionCode = it.filter { ch -> ch.isDigit() } },
                label = { Text("Version Code") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = versionName,
                onValueChange = { versionName = it },
                label = { Text("Version Name") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("STABLE", "BETA", "NIGHTLY").forEach { ch ->
                FilterChip(
                    selected = channel == ch,
                    onClick = { channel = ch },
                    label = { Text(ch) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = changelog,
            onValueChange = { changelog = it },
            label = { Text("Release Changelog") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = downloadUrl,
            onValueChange = { downloadUrl = it },
            label = { Text("Release / APK Download URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = checksum,
            onValueChange = { checksum = it },
            label = { Text("SHA-256 Checksum (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Mark as Mandatory Update", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = requiredUpdate, onCheckedChange = { requiredUpdate = it })
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                val code = versionCode.toLongOrNull() ?: (currentVersionCode + 1L)
                onPublishRelease(code, versionName, channel, changelog, downloadUrl, checksum, requiredUpdate)
            },
            modifier = Modifier.fillMaxWidth().testTag("publish_release_btn"),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Rounded.NotificationsActive, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(lStr(R.string.admin_publish_release))
        }
    }
}
