package com.example.data

import android.content.Context
import com.example.R
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
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class RbacRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun handleStreamError(error: Throwable, op: OperationType, path: String, uid: String): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
        throw error
    }

    suspend fun ensureUserProfileOnSignIn(user: FirebaseUser): Result<Unit> {
        val docPath = "users/${user.uid}"
        return try {
            val userRef = db.collection("users").document(user.uid)
            val snap = userRef.get().await()
            val normalizedEmail = user.email?.trim()?.lowercase().orEmpty()
            val isBootstrappedAdmin = normalizedEmail in BOOTSTRAPPED_ADMIN_EMAILS && user.isEmailVerified
            if (!snap.exists()) {
                val role = if (isBootstrappedAdmin) UserRole.ADMIN else UserRole.VIEWER
                val permissions = if (isBootstrappedAdmin) {
                    AppPermission.entries.map { it.name }
                } else {
                    listOf(AppPermission.PKG_INSPECT.name)
                }
                val batch = db.batch()
                val payload = mapOf(
                    "userId" to user.uid,
                    "email" to (user.email ?: ""),
                    "displayName" to (user.displayName?.takeIf { it.isNotBlank() }?.take(80)
                        ?: user.email?.substringBefore("@")?.take(80)
                        ?: "Operator"),
                    "role" to role.name,
                    "permissions" to permissions,
                    "activeStatus" to ActiveStatus.ACTIVE.name,
                    "updatedBy" to user.uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(userRef, payload)
                if (isBootstrappedAdmin) {
                    val adminRef = db.collection("admins").document(user.uid)
                    batch.set(
                        adminRef,
                        mapOf(
                            "userId" to user.uid,
                            "grantedBy" to user.uid,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            } else if (isBootstrappedAdmin) {
                val adminRef = db.collection("admins").document(user.uid)
                val adminExists = adminRef.get().await().exists()
                val allPerms = AppPermission.entries.map { it.name }
                val currentRole = snap.getString("role")
                val currentStatus = snap.getString("activeStatus")
                val currentPerms = (snap.get("permissions") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
                val needsProfileUpgrade = currentRole != UserRole.ADMIN.name ||
                    currentStatus != ActiveStatus.ACTIVE.name ||
                    !allPerms.all { it in currentPerms }

                if (!adminExists || needsProfileUpgrade) {
                    val batch = db.batch()
                    if (!adminExists) {
                        batch.set(
                            adminRef,
                            mapOf(
                                "userId" to user.uid,
                                "grantedBy" to user.uid,
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                    if (needsProfileUpgrade) {
                        batch.update(
                            userRef,
                            mapOf(
                                "role" to UserRole.ADMIN.name,
                                "permissions" to allPerms,
                                "activeStatus" to ActiveStatus.ACTIVE.name,
                                "updatedBy" to user.uid,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                    batch.commit().await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    fun observeMyProfile(userId: String): Flow<UserAccessProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snap ->
                    if (!snap.exists()) null
                    else snap.toObject(UserAccessProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error -> handleStreamError(error, OperationType.GET, path, userId) }
        )
    }

    fun observeAllUsers(currentUserId: String): Flow<List<UserAccessProfile>> = flow {
        val path = "users"
        emitAll(
            db.collection("users")
                .snapshots()
                .map { snap ->
                    snap.toObjects(UserAccessProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.email.lowercase() }
                }
                .catch { error -> handleStreamError(error, OperationType.LIST, path, currentUserId) }
        )
    }

    suspend fun updateUserAccess(
        target: UserAccessProfile,
        newRole: UserRole,
        newPermissions: List<AppPermission>,
        newStatus: ActiveStatus
    ): Result<Unit> {
        val docPath = "users/${target.userId}"
        return try {
            val actor = auth.currentUser
                ?: throw IllegalStateException("User must be signed in with Google before writing.")
            val actorUid = actor.uid
            val targetUid = target.userId
            require(targetUid.isNotBlank()) { "Target userId cannot be blank" }

            val sanitizedPerms = if (newRole == UserRole.ADMIN) {
                AppPermission.entries.map { it.name }
            } else {
                newPermissions.distinct().take(10).map { it.name }
            }

            val adminRef = db.collection("admins").document(targetUid)
            val adminExists = adminRef.get().await().exists()
            val shouldBeAdmin = newRole == UserRole.ADMIN && newStatus == ActiveStatus.ACTIVE

            val batch = db.batch()
            val userRef = db.collection("users").document(targetUid)
            batch.update(
                userRef,
                mapOf(
                    "role" to newRole.name,
                    "permissions" to sanitizedPerms,
                    "activeStatus" to newStatus.name,
                    "updatedBy" to actorUid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (shouldBeAdmin && !adminExists) {
                batch.set(
                    adminRef,
                    mapOf(
                        "userId" to targetUid,
                        "grantedBy" to actorUid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            } else if (!shouldBeAdmin && adminExists) {
                batch.delete(adminRef)
            }

            val auditId = "evt_${UUID.randomUUID().toString().replace("-", "").take(20)}"
            val auditRef = db.collection("audit_events").document(auditId)
            batch.set(
                auditRef,
                mapOf(
                    "actorUid" to actorUid,
                    "actorEmail" to (actor.email ?: ""),
                    "targetUid" to targetUid,
                    "targetEmail" to target.email,
                    "eventType" to "PERMISSIONS_UPDATED",
                    "summary" to "Role=${newRole.name}, Status=${newStatus.name}, Perms=${sanitizedPerms.size}".take(300),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    fun observeAuditEvents(currentUserId: String): Flow<List<AuditEvent>> = flow {
        val path = "audit_events"
        emitAll(
            db.collection("audit_events")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .snapshots()
                .map { snap ->
                    snap.toObjects(AuditEvent::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error -> handleStreamError(error, OperationType.LIST, path, currentUserId) }
        )
    }

    fun observeActivityLogs(userId: String): Flow<List<ActivityLogEntry>> = flow {
        val path = "users/$userId/activity_logs"
        emitAll(
            db.collection("users").document(userId).collection("activity_logs")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ActivityLogEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error -> handleStreamError(error, OperationType.LIST, path, userId) }
        )
    }

    suspend fun logActivity(
        category: String,
        severity: String,
        action: String,
        details: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val path = "users/$uid/activity_logs/$logId"
        return try {
            val payload = mapOf(
                "userId" to uid,
                "category" to category.take(20),
                "severity" to severity.take(10),
                "action" to action.ifBlank { "EVENT" }.take(100),
                "details" to details.take(500),
                "actorEmail" to (auth.currentUser?.email ?: "").take(200),
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(uid).collection("activity_logs").document(logId)
                .set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    fun observeConsoleProfiles(userId: String): Flow<List<ConsoleProfile>> = flow {
        val path = "users/$userId/console_profiles"
        emitAll(
            db.collection("users").document(userId).collection("console_profiles")
                .snapshots()
                .map { snap ->
                    snap.toObjects(ConsoleProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error -> handleStreamError(error, OperationType.LIST, path, userId) }
        )
    }

    suspend fun saveConsoleProfile(profile: ConsoleProfile): Result<Unit> {
        val uid = requireUserId()
        val id = profile.id.ifBlank { "ps4_${UUID.randomUUID().toString().replace("-", "").take(16)}" }
        val path = "users/$uid/console_profiles/$id"
        return try {
            val docRef = db.collection("users").document(uid).collection("console_profiles").document(id)
            if (profile.id.isBlank()) {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "name" to profile.name.ifBlank { "PS4 Console" }.take(60),
                        "host" to profile.host.trim().take(100),
                        "port" to profile.port.coerceIn(1L, 65535L),
                        "binLoaderPort" to profile.binLoaderPort.coerceIn(1L, 65535L),
                        "klogPort" to profile.klogPort.coerceIn(1L, 65535L),
                        "username" to profile.username.take(64),
                        "firmwareVersion" to profile.firmwareVersion.take(20),
                        "notes" to profile.notes.take(240),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "name" to profile.name.ifBlank { "PS4 Console" }.take(60),
                        "host" to profile.host.trim().take(100),
                        "port" to profile.port.coerceIn(1L, 65535L),
                        "binLoaderPort" to profile.binLoaderPort.coerceIn(1L, 65535L),
                        "klogPort" to profile.klogPort.coerceIn(1L, 65535L),
                        "username" to profile.username.take(64),
                        "firmwareVersion" to profile.firmwareVersion.take(20),
                        "notes" to profile.notes.take(240),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteConsoleProfile(profileId: String): Result<Unit> {
        val uid = requireUserId()
        val path = "users/$uid/console_profiles/$profileId"
        return try {
            db.collection("users").document(uid).collection("console_profiles").document(profileId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun observeVcsConfigs(userId: String): Flow<List<VcsConfig>> = flow {
        val path = "users/$userId/vcs_configs"
        emitAll(
            db.collection("users").document(userId).collection("vcs_configs")
                .snapshots()
                .map { snap ->
                    snap.toObjects(VcsConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error -> handleStreamError(error, OperationType.LIST, path, userId) }
        )
    }

    suspend fun saveVcsConfig(config: VcsConfig): Result<Unit> {
        val uid = requireUserId()
        val id = config.id.ifBlank { "vcs_${UUID.randomUUID().toString().replace("-", "").take(16)}" }
        val path = "users/$uid/vcs_configs/$id"
        return try {
            val docRef = db.collection("users").document(uid).collection("vcs_configs").document(id)
            if (config.id.isBlank()) {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "provider" to config.provider.take(20),
                        "owner" to config.owner.trim().take(80),
                        "repo" to config.repo.trim().take(100),
                        "branch" to config.branch.ifBlank { "main" }.take(80),
                        "workflowFile" to config.workflowFile.take(120),
                        "deployEnvironment" to config.deployEnvironment.take(60),
                        "lastStatus" to config.lastStatus.take(40),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "provider" to config.provider.take(20),
                        "owner" to config.owner.trim().take(80),
                        "repo" to config.repo.trim().take(100),
                        "branch" to config.branch.ifBlank { "main" }.take(80),
                        "workflowFile" to config.workflowFile.take(120),
                        "deployEnvironment" to config.deployEnvironment.take(60),
                        "lastStatus" to config.lastStatus.take(40),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observePublishedReleases(currentUserId: String): Flow<List<AppRelease>> = flow {
        val path = "app_releases"
        emitAll(
            db.collection("app_releases")
                .whereEqualTo("publishedStatus", "PUBLISHED")
                .snapshots()
                .map { snap ->
                    snap.toObjects(AppRelease::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.versionCode }
                }
                .catch { error -> handleStreamError(error, OperationType.LIST, path, currentUserId) }
        )
    }

    suspend fun publishAppRelease(
        versionCode: Long,
        versionName: String,
        channel: String,
        changelog: String,
        downloadUrl: String,
        checksumSha256: String,
        requiredUpdate: Boolean
    ): Result<Unit> {
        val uid = requireUserId()
        val releaseId = "rel_${versionCode}_${UUID.randomUUID().toString().replace("-", "").take(8)}"
        val path = "app_releases/$releaseId"
        return try {
            val batch = db.batch()
            val relRef = db.collection("app_releases").document(releaseId)
            batch.set(
                relRef,
                mapOf(
                    "versionCode" to versionCode.coerceIn(1L, 999999L),
                    "versionName" to versionName.trim().take(30),
                    "channel" to channel,
                    "publishedStatus" to "PUBLISHED",
                    "changelog" to changelog.trim().take(1000),
                    "downloadUrl" to downloadUrl.trim().take(500),
                    "checksumSha256" to checksumSha256.trim().take(128),
                    "requiredUpdate" to requiredUpdate,
                    "publishedBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val auditId = "evt_${UUID.randomUUID().toString().replace("-", "").take(20)}"
            batch.set(
                db.collection("audit_events").document(auditId),
                mapOf(
                    "actorUid" to uid,
                    "actorEmail" to (auth.currentUser?.email ?: ""),
                    "targetUid" to releaseId,
                    "targetEmail" to versionName.take(200),
                    "eventType" to "RELEASE_PUBLISHED",
                    "summary" to "Published v$versionName ($versionCode) on $channel".take(300),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    fun observePublishedStoreItems(currentUserId: String): Flow<List<StoreItem>> = flow {
        val path = "store_items"
        emitAll(
            db.collection("store_items")
                .whereEqualTo("publishedStatus", "PUBLISHED")
                .snapshots()
                .map { snap ->
                    snap.toObjects(StoreItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error -> handleStreamError(error, OperationType.LIST, path, currentUserId) }
        )
    }

    suspend fun publishStoreItem(item: StoreItem): Result<Unit> {
        val uid = requireUserId()
        val id = item.id.ifBlank { "itm_${UUID.randomUUID().toString().replace("-", "").take(16)}" }
        val path = "store_items/$id"
        return try {
            val cleanUrl = item.downloadUrl.trim().take(500)
            val cleanInline = item.inlineContent.take(16000)
            require(cleanUrl.startsWith("https://") || cleanInline.isNotEmpty()) {
                "Provide a valid https:// download URL or non-empty script/content"
            }
            val cleanConsolePath = item.targetConsolePath.trim().let {
                if (it.startsWith("/")) it else "/$it"
            }.replace("..", "").take(180)
            val payload = mapOf(
                "title" to item.title.trim().ifBlank { item.fileName }.take(100),
                "category" to item.category,
                "gameTitle" to item.gameTitle.trim().ifBlank { "PS4 Universal" }.take(80),
                "titleId" to item.titleId.trim().uppercase().take(16),
                "version" to item.version.trim().ifBlank { "1.0.0" }.take(30),
                "fileName" to item.fileName.trim().ifBlank { "package.bin" }.take(120),
                "targetConsolePath" to cleanConsolePath,
                "downloadUrl" to cleanUrl,
                "inlineContent" to cleanInline,
                "sha256" to item.sha256.trim().take(128),
                "sizeBytes" to item.sizeBytes.coerceAtLeast(0L),
                "description" to item.description.trim().take(600),
                "publishedStatus" to "PUBLISHED",
                "authorUid" to uid,
                "authorEmail" to (auth.currentUser?.email ?: "").take(200),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("store_items").document(id).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteStoreItem(itemId: String): Result<Unit> {
        requireUserId()
        val path = "store_items/$itemId"
        return try {
            db.collection("store_items").document(itemId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    companion object {
        val BOOTSTRAPPED_ADMIN_EMAILS = setOf(
            "papis1730jj@gmail.com",
            "tabordaedith09@gmail.com"
        )
    }
}
