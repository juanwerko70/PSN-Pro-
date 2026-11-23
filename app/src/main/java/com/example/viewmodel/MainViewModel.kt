package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.PreferencesRepository
import com.example.data.RbacRepository
import com.example.data.UiState
import com.example.data.UserPreferences
import com.example.model.ActiveStatus
import com.example.model.ActivityLogEntry
import com.example.model.AppPermission
import com.example.model.AppRelease
import com.example.model.AuditEvent
import com.example.model.ConsoleProfile
import com.example.model.StoreItem
import com.example.model.UserAccessProfile
import com.example.model.UserRole
import com.example.model.VcsConfig
import com.example.ui.theme.AppThemeMode
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val rbacRepository: RbacRepository,
    private val preferencesRepository: PreferencesRepository,
    private val currentUserId: String,
    currentUser: FirebaseUser?
) : ViewModel() {

    private val _previewRoleOverride = MutableStateFlow<UserRole?>(null)
    val previewRoleOverride: StateFlow<UserRole?> = _previewRoleOverride.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UserPreferences())

    val myProfileState: StateFlow<UiState<UserAccessProfile?>> =
        rbacRepository.observeMyProfile(currentUserId)
            .toUiStateFlow("MyProfile")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val isRealAdminFlow: Flow<Boolean> = myProfileState.map { state ->
        val p = (state as? UiState.Success)?.data
        p != null && p.parsedRole() == UserRole.ADMIN && p.parsedStatus() == ActiveStatus.ACTIVE
    }

    val allUsersState: StateFlow<UiState<List<UserAccessProfile>>> = isRealAdminFlow
        .flatMapLatest { isAdmin ->
            if (isAdmin) rbacRepository.observeAllUsers(currentUserId).toUiStateFlow("AllUsers")
            else flowOf(UiState.Success(emptyList()))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val auditEventsState: StateFlow<UiState<List<AuditEvent>>> = isRealAdminFlow
        .flatMapLatest { isAdmin ->
            if (isAdmin) rbacRepository.observeAuditEvents(currentUserId).toUiStateFlow("AuditEvents")
            else flowOf(UiState.Success(emptyList()))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val activityLogsState: StateFlow<UiState<List<ActivityLogEntry>>> =
        rbacRepository.observeActivityLogs(currentUserId)
            .toUiStateFlow("ActivityLogs")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val consoleProfilesState: StateFlow<UiState<List<ConsoleProfile>>> =
        rbacRepository.observeConsoleProfiles(currentUserId)
            .toUiStateFlow("ConsoleProfiles")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val vcsConfigsState: StateFlow<UiState<List<VcsConfig>>> =
        rbacRepository.observeVcsConfigs(currentUserId)
            .toUiStateFlow("VcsConfigs")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val releasesState: StateFlow<UiState<List<AppRelease>>> =
        rbacRepository.observePublishedReleases(currentUserId)
            .toUiStateFlow("AppReleases")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val storeItemsState: StateFlow<UiState<List<StoreItem>>> =
        rbacRepository.observePublishedStoreItems(currentUserId)
            .toUiStateFlow("StoreItems")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    init {
        if (currentUser != null) {
            viewModelScope.launch {
                rbacRepository.ensureUserProfileOnSignIn(currentUser)
                    .onFailure { err ->
                        _statusBanner.value = err.localizedMessage ?: "Profile sync failed"
                    }
            }
        }
    }

    fun effectiveHasPermission(profile: UserAccessProfile?, permission: AppPermission): Boolean {
        if (profile == null) return false
        if (profile.parsedStatus() == ActiveStatus.SUSPENDED) return false
        val preview = _previewRoleOverride.value
        return if (preview != null && profile.parsedRole() == UserRole.ADMIN) {
            preview.defaultPermissions.contains(permission)
        } else {
            profile.hasPermission(permission)
        }
    }

    fun setPreviewRole(role: UserRole?) {
        _previewRoleOverride.value = role
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun showBanner(msg: String) {
        _statusBanner.value = msg
    }

    fun updateUserAccess(
        target: UserAccessProfile,
        newRole: UserRole,
        newPermissions: List<AppPermission>,
        newStatus: ActiveStatus
    ) {
        viewModelScope.launch {
            rbacRepository.updateUserAccess(target, newRole, newPermissions, newStatus)
                .onSuccess {
                    _statusBanner.value = "Updated ${target.email} -> ${newRole.name}"
                    rbacRepository.logActivity(
                        category = "RBAC",
                        severity = "INFO",
                        action = "ROLE_UPDATED",
                        details = "${target.email}: ${newRole.name} (${newPermissions.size} perms, ${newStatus.name})"
                    )
                }
                .onFailure { e ->
                    _statusBanner.value = "RBAC update failed: ${e.localizedMessage}"
                }
        }
    }

    fun logActivity(category: String, severity: String, action: String, details: String) {
        viewModelScope.launch {
            rbacRepository.logActivity(category, severity, action, details)
        }
    }

    fun saveConsoleProfile(profile: ConsoleProfile) {
        viewModelScope.launch {
            rbacRepository.saveConsoleProfile(profile)
                .onSuccess {
                    _statusBanner.value = "Saved console profile: ${profile.name}"
                    logActivity("FTP", "INFO", "SAVED_PROFILE", "${profile.name} (${profile.host}:${profile.port})")
                }
                .onFailure { _statusBanner.value = "Save failed: ${it.localizedMessage}" }
        }
    }

    fun deleteConsoleProfile(profileId: String) {
        viewModelScope.launch {
            rbacRepository.deleteConsoleProfile(profileId)
                .onFailure { _statusBanner.value = "Delete failed: ${it.localizedMessage}" }
        }
    }

    fun saveVcsConfig(config: VcsConfig) {
        viewModelScope.launch {
            rbacRepository.saveVcsConfig(config)
                .onSuccess {
                    _statusBanner.value = "Saved repository: ${config.owner}/${config.repo}"
                    logActivity("VCS", "INFO", "SAVED_REPO", "${config.provider} ${config.owner}/${config.repo}")
                }
                .onFailure { _statusBanner.value = "Save failed: ${it.localizedMessage}" }
        }
    }

    fun publishAppRelease(
        versionCode: Long,
        versionName: String,
        channel: String,
        changelog: String,
        downloadUrl: String,
        checksumSha256: String,
        requiredUpdate: Boolean
    ) {
        viewModelScope.launch {
            rbacRepository.publishAppRelease(
                versionCode = versionCode,
                versionName = versionName,
                channel = channel,
                changelog = changelog,
                downloadUrl = downloadUrl,
                checksumSha256 = checksumSha256,
                requiredUpdate = requiredUpdate
            ).onSuccess {
                _statusBanner.value = "Published release v$versionName ($channel)"
                logActivity("UPDATE", "INFO", "PUBLISHED_RELEASE", "v$versionName ($versionCode) on $channel")
            }.onFailure {
                _statusBanner.value = "Publish failed: ${it.localizedMessage}"
            }
        }
    }

    fun markReleaseNotified(versionCode: Long) {
        viewModelScope.launch {
            preferencesRepository.setLastNotifiedVersionCode(versionCode)
        }
    }

    fun publishStoreItem(item: StoreItem) {
        viewModelScope.launch {
            rbacRepository.publishStoreItem(item)
                .onSuccess {
                    _statusBanner.value = "Published '${item.title}' to Store"
                    logActivity("STORE", "INFO", "PUBLISHED_STORE_ITEM", "${item.category}: ${item.title} (${item.titleId})")
                }
                .onFailure { err ->
                    _statusBanner.value = "Store publish failed: ${err.localizedMessage}"
                }
        }
    }

    fun deleteStoreItem(itemId: String) {
        viewModelScope.launch {
            rbacRepository.deleteStoreItem(itemId)
                .onSuccess {
                    _statusBanner.value = "Removed item from Store"
                    logActivity("STORE", "WARN", "DELETED_STORE_ITEM", itemId)
                }
                .onFailure { err ->
                    _statusBanner.value = "Delete failed: ${err.localizedMessage}"
                }
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setLanguageTag(tag: String) {
        viewModelScope.launch { preferencesRepository.setLanguageTag(tag) }
    }

    fun setGlassOpacity(opacity: Float) {
        viewModelScope.launch { preferencesRepository.setGlassOpacity(opacity) }
    }

    fun setPushUpdateAlerts(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setPushUpdateAlerts(enabled) }
    }

    fun currentAppVersionCode(): Long = BuildConfig.VERSION_CODE.toLong()
    fun currentAppVersionName(): String = BuildConfig.VERSION_NAME

    private fun <T> Flow<T>.toUiStateFlow(tag: String): Flow<UiState<T>> =
        map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w("MainVM-$tag", "Stream error", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Stream error"))
            }
}
