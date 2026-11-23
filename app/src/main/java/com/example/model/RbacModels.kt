package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class UserRole(val defaultPermissions: List<AppPermission>) {
    ADMIN(AppPermission.entries.toList()),
    DEVELOPER(
        listOf(
            AppPermission.PKG_INSPECT,
            AppPermission.PKG_MODIFY,
            AppPermission.FTP_CONSOLE,
            AppPermission.DEV_VCS_CICD,
            AppPermission.DIAGNOSTICS_LOGS,
            AppPermission.APP_UPDATES
        )
    ),
    OPERATOR(
        listOf(
            AppPermission.PKG_INSPECT,
            AppPermission.FTP_CONSOLE,
            AppPermission.DIAGNOSTICS_LOGS,
            AppPermission.APP_UPDATES
        )
    ),
    VIEWER(listOf(AppPermission.PKG_INSPECT));

    companion object {
        fun safeValueOf(raw: String): UserRole =
            entries.firstOrNull { it.name == raw } ?: VIEWER
    }
}

enum class AppPermission(val label: String, val description: String) {
    PKG_INSPECT("PKG Inspect", "Import and inspect .PKG headers, SFO & entries"),
    PKG_MODIFY("PKG & Resource Edit", "Modify PARAM.SFO, binary patches, DDS/PNG & OBJ/FBX"),
    FTP_CONSOLE("PS4 Remote & FTP", "Connect FTP, transfer files, Klog & process/RAM debug"),
    DEV_VCS_CICD("VCS & Cloud CI/CD", "GitHub/GitLab sync, workflow dispatch & manifests"),
    DIAGNOSTICS_LOGS("Diagnostics & Logs", "Hardware/network probes & activity log export"),
    APP_UPDATES("App Updates", "Receive & check release channels and update alerts"),
    MANAGE_ROLES("Admin RBAC", "Grant/revoke roles, permissions & publish app releases");

    companion object {
        fun safeValueOf(raw: String): AppPermission? =
            entries.firstOrNull { it.name == raw }
    }
}

enum class ActiveStatus {
    ACTIVE,
    SUSPENDED;

    companion object {
        fun safeValueOf(raw: String): ActiveStatus =
            entries.firstOrNull { it.name == raw } ?: ACTIVE
    }
}

@Keep
@IgnoreExtraProperties
data class UserAccessProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: String = UserRole.VIEWER.name,
    val permissions: List<String> = listOf(AppPermission.PKG_INSPECT.name),
    val activeStatus: String = ActiveStatus.ACTIVE.name,
    val updatedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun parsedRole(): UserRole = UserRole.safeValueOf(role)
    fun parsedStatus(): ActiveStatus = ActiveStatus.safeValueOf(activeStatus)
    fun hasPermission(permission: AppPermission): Boolean {
        if (parsedStatus() == ActiveStatus.SUSPENDED) return false
        return parsedRole() == UserRole.ADMIN || permissions.contains(permission.name)
    }
}

@Keep
@IgnoreExtraProperties
data class ActivityLogEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val category: String = "SYSTEM",
    val severity: String = "INFO",
    val action: String = "",
    val details: String = "",
    val actorEmail: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ConsoleProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val host: String = "",
    val port: Long = 2121L,
    val binLoaderPort: Long = 9090L,
    val klogPort: Long = 3232L,
    val username: String = "anonymous",
    val firmwareVersion: String = "9.00",
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class VcsConfig(
    @DocumentId val id: String = "",
    val userId: String = "",
    val provider: String = "GITHUB",
    val owner: String = "",
    val repo: String = "",
    val branch: String = "main",
    val workflowFile: String = "build-pkg.yml",
    val deployEnvironment: String = "production",
    val lastStatus: String = "READY",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AppRelease(
    @DocumentId val id: String = "",
    val versionCode: Long = 1L,
    val versionName: String = "1.0.0",
    val channel: String = "STABLE",
    val publishedStatus: String = "PUBLISHED",
    val changelog: String = "",
    val downloadUrl: String = "",
    val checksumSha256: String = "",
    val requiredUpdate: Boolean = false,
    val publishedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AuditEvent(
    @DocumentId val id: String = "",
    val actorUid: String = "",
    val actorEmail: String = "",
    val targetUid: String = "",
    val targetEmail: String = "",
    val eventType: String = "PERMISSIONS_UPDATED",
    val summary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

enum class StoreCategory(val label: String, val defaultConsoleDir: String) {
    TEXTURE("Textures (DDS/PNG)", "/user/app"),
    MOD("Mods & Plugins", "/data/GoldHEN/plugins"),
    SCRIPT("Scripts (Lua/SH)", "/data/GoldHEN/scripts"),
    PKG("Homebrew PKG", "/data/pkg"),
    MODEL_3D("3D Models (OBJ/FBX)", "/data/pkgforge/models"),
    PAYLOAD("Payloads (.bin)", "/data/GoldHEN/payloads");

    companion object {
        fun safeValueOf(raw: String): StoreCategory =
            entries.firstOrNull { it.name == raw } ?: MOD
    }
}

@Keep
@IgnoreExtraProperties
data class StoreItem(
    @DocumentId val id: String = "",
    val title: String = "",
    val category: String = StoreCategory.MOD.name,
    val gameTitle: String = "PS4 Universal",
    val titleId: String = "CUSA00000",
    val version: String = "1.0.0",
    val fileName: String = "",
    val targetConsolePath: String = "/data/pkg",
    val downloadUrl: String = "",
    val inlineContent: String = "",
    val sha256: String = "",
    val sizeBytes: Long = 0L,
    val description: String = "",
    val publishedStatus: String = "PUBLISHED",
    val authorUid: String = "",
    val authorEmail: String = "",
    @get:com.google.firebase.firestore.Exclude val previewImageUrl: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

