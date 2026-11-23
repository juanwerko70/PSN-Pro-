package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.auth.GoogleAuthHelper
import com.example.auth.configureAppCheck
import com.example.data.PreferencesRepository
import com.example.data.RbacRepository
import com.example.data.UiState
import com.example.data.UserPreferences
import com.example.engine.UpdateNotifier
import com.example.model.ActiveStatus
import com.example.model.AppPermission
import com.example.model.UserRole
import com.example.ui.components.GlassStatusPill
import com.example.ui.components.LiquidGlassBackground
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.ProvideLocalizedAppContext
import com.example.ui.components.lStr
import com.example.ui.screens.AdminRbacScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ConsoleDebugScreen
import com.example.ui.screens.DevToolsScreen
import com.example.ui.screens.PkgStudioScreen
import com.example.ui.screens.StoreScreen
import com.example.ui.screens.SystemScreen
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IridescentIndigo
import com.example.ui.theme.LocalGlassConfig
import com.example.ui.theme.PkgForgeTheme
import com.example.viewmodel.AdvancedHackerViewModel
import com.example.viewmodel.AiModViewModel
import com.example.viewmodel.ConsoleDebugViewModel
import com.example.viewmodel.DevToolsViewModel
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.PkgStudioViewModel
import com.example.viewmodel.StoreViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth

const val ROUTE_PKG = "pkg_studio"
const val ROUTE_STORE = "store"
const val ROUTE_CONSOLE = "ps4_remote"
const val ROUTE_DEVOPS = "devops"
const val ROUTE_ADMIN = "access_admin"
const val ROUTE_SYSTEM = "system_settings"

private data class NavDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
)

private val bottomDestinations = listOf(
    NavDestination(ROUTE_PKG, R.string.nav_pkg_studio, Icons.Rounded.Inventory2),
    NavDestination(ROUTE_STORE, R.string.nav_store, Icons.Rounded.Storefront),
    NavDestination(ROUTE_CONSOLE, R.string.nav_ps4_remote, Icons.Rounded.Router),
    NavDestination(ROUTE_DEVOPS, R.string.nav_devops, Icons.Rounded.Code),
    NavDestination(ROUTE_SYSTEM, R.string.nav_system, Icons.Rounded.Settings)
)

private val railDestinations = listOf(
    NavDestination(ROUTE_PKG, R.string.nav_pkg_studio, Icons.Rounded.Inventory2),
    NavDestination(ROUTE_STORE, R.string.nav_store, Icons.Rounded.Storefront),
    NavDestination(ROUTE_CONSOLE, R.string.nav_ps4_remote, Icons.Rounded.Router),
    NavDestination(ROUTE_DEVOPS, R.string.nav_devops, Icons.Rounded.Code),
    NavDestination(ROUTE_ADMIN, R.string.nav_access, Icons.Rounded.AdminPanelSettings),
    NavDestination(ROUTE_SYSTEM, R.string.nav_system, Icons.Rounded.Settings)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        UpdateNotifier.ensureChannel(this)
        setContent {
            PkgForgeRoot()
        }
    }
}

@Composable
private fun PkgForgeRoot() {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val preferencesRepository = remember(appContext) { PreferencesRepository(appContext) }
    val preferences by preferencesRepository.preferencesFlow.collectAsStateWithLifecycle(
        initialValue = UserPreferences()
    )

    val credentialManager = remember(context) { CredentialManager.create(context) }
    val scope = rememberCoroutineScope()
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    LaunchedEffect(Unit) {
        GoogleAuthHelper.attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = { currentUser = Firebase.auth.currentUser },
            onUnauthenticated = { currentUser = Firebase.auth.currentUser },
            scope = scope
        )
    }

    PkgForgeTheme(
        themeMode = preferences.themeMode,
        glassOpacity = preferences.glassOpacity
    ) {
        ProvideLocalizedAppContext(languageTag = preferences.languageTag) {
            val activeUser = currentUser
            if (activeUser == null) {
                AuthScreen(
                    onAuthSuccess = {
                        currentUser = Firebase.auth.currentUser
                    }
                )
            } else {
                key(activeUser.uid) {
                    AuthenticatedWorkspace(
                        currentUser = activeUser,
                        preferencesRepository = preferencesRepository,
                        preferences = preferences,
                        onSignOut = {
                            GoogleAuthHelper.signOut(
                                credentialManager = credentialManager,
                                scope = scope
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthenticatedWorkspace(
    currentUser: FirebaseUser,
    preferencesRepository: PreferencesRepository,
    preferences: UserPreferences,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val rbacRepository = remember(appContext) { RbacRepository(appContext) }

    val mainViewModel: MainViewModel = viewModel(
        key = "main_${currentUser.uid}",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(
                    rbacRepository = rbacRepository,
                    preferencesRepository = preferencesRepository,
                    currentUserId = currentUser.uid,
                    currentUser = currentUser
                ) as T
            }
        }
    )

    val pkgViewModel: PkgStudioViewModel = viewModel(key = "pkg_${currentUser.uid}")
    val storeViewModel: StoreViewModel = viewModel(key = "store_${currentUser.uid}")
    val aiModViewModel: AiModViewModel = viewModel(key = "aimod_${currentUser.uid}")
    val consoleViewModel: ConsoleDebugViewModel = viewModel(key = "console_${currentUser.uid}")
    val hackerViewModel: AdvancedHackerViewModel = viewModel(key = "hacker_${currentUser.uid}")
    val devToolsViewModel: DevToolsViewModel = viewModel(key = "devops_${currentUser.uid}")

    val myProfileState by mainViewModel.myProfileState.collectAsStateWithLifecycle()
    val previewRoleOverride by mainViewModel.previewRoleOverride.collectAsStateWithLifecycle()
    val allUsersState by mainViewModel.allUsersState.collectAsStateWithLifecycle()
    val auditEventsState by mainViewModel.auditEventsState.collectAsStateWithLifecycle()
    val activityLogsState by mainViewModel.activityLogsState.collectAsStateWithLifecycle()
    val consoleProfilesState by mainViewModel.consoleProfilesState.collectAsStateWithLifecycle()
    val vcsConfigsState by mainViewModel.vcsConfigsState.collectAsStateWithLifecycle()
    val releasesState by mainViewModel.releasesState.collectAsStateWithLifecycle()
    val storeItemsState by mainViewModel.storeItemsState.collectAsStateWithLifecycle()
    val statusBanner by mainViewModel.statusBanner.collectAsStateWithLifecycle()
    val pkgState by pkgViewModel.state.collectAsStateWithLifecycle()
    val consoleState by consoleViewModel.state.collectAsStateWithLifecycle()

    val myProfile = (myProfileState as? UiState.Success)?.data
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusBanner) {
        val message = statusBanner
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            mainViewModel.clearBanner()
        }
    }

    val latestNewRelease = remember(releasesState, mainViewModel.currentAppVersionCode()) {
        val list = (releasesState as? UiState.Success)?.data.orEmpty()
        list.firstOrNull { it.versionCode > mainViewModel.currentAppVersionCode() }
    }

    LaunchedEffect(latestNewRelease?.versionCode, preferences.pushUpdateAlertsEnabled, preferences.lastNotifiedVersionCode) {
        val release = latestNewRelease
        if (
            release != null &&
            preferences.pushUpdateAlertsEnabled &&
            release.versionCode > preferences.lastNotifiedVersionCode
        ) {
            UpdateNotifier.showReleaseNotification(appContext, release)
            mainViewModel.markReleaseNotified(release.versionCode)
        }
    }

    if (myProfile != null && myProfile.parsedStatus() == ActiveStatus.SUSPENDED) {
        SuspendedAccountScreen(
            email = myProfile.email,
            onSignOut = onSignOut
        )
        return
    }

    val effectiveRoleLabel = if (previewRoleOverride != null && myProfile?.parsedRole() == UserRole.ADMIN) {
        "${previewRoleOverride?.name} (Preview)"
    } else {
        myProfile?.role ?: UserRole.VIEWER.name
    }

    val canInspectPkg = mainViewModel.effectiveHasPermission(myProfile, AppPermission.PKG_INSPECT)
    val canModifyPkg = mainViewModel.effectiveHasPermission(myProfile, AppPermission.PKG_MODIFY)
    val canAccessConsole = mainViewModel.effectiveHasPermission(myProfile, AppPermission.FTP_CONSOLE)
    val canUseDevTools = mainViewModel.effectiveHasPermission(myProfile, AppPermission.DEV_VCS_CICD)
    val canViewDiagnostics = mainViewModel.effectiveHasPermission(myProfile, AppPermission.DIAGNOSTICS_LOGS)
    val canCheckUpdates = mainViewModel.effectiveHasPermission(myProfile, AppPermission.APP_UPDATES)

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_PKG

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val activeTitleId = pkgState.sfoDoc?.entries
        ?.firstOrNull { it.key == "TITLE_ID" }
        ?.stringValue
        .orEmpty()

    LiquidGlassBackground {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!useRail) {
                        GlassBottomNavBar(
                            currentRoute = currentRoute,
                            onSelect = ::navigateTo
                        )
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail) {
                        GlassNavigationRail(
                            currentRoute = currentRoute,
                            onSelect = ::navigateTo
                        )
                    }

                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        WorkspaceTopBar(
                            email = myProfile?.email ?: currentUser.email.orEmpty(),
                            roleLabel = effectiveRoleLabel,
                            isPreviewActive = previewRoleOverride != null,
                            updateVersionName = latestNewRelease?.versionName,
                            onResetPreview = { mainViewModel.setPreviewRole(null) },
                            onOpenRbac = { navigateTo(ROUTE_ADMIN) },
                            onUpdateClick = {
                                val url = latestNewRelease?.downloadUrl.orEmpty()
                                if (url.startsWith("https://")) {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }
                                } else {
                                    navigateTo(ROUTE_SYSTEM)
                                }
                            }
                        )

                        AnimatedVisibility(
                            visible = latestNewRelease != null && canCheckUpdates,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            latestNewRelease?.let { rel ->
                                LiquidGlassCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    accentColor = AmberWarning,
                                    onClick = { navigateTo(ROUTE_SYSTEM) }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.SystemUpdate,
                                                contentDescription = null,
                                                tint = AmberWarning,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = lStr(R.string.update_available_banner, rel.versionName),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        GlassStatusPill(text = rel.channel, color = AmberWarning)
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            NavHost(
                                navController = navController,
                                startDestination = ROUTE_PKG,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                composable(ROUTE_PKG) {
                                    PkgStudioScreen(
                                        viewModel = pkgViewModel,
                                        roleName = effectiveRoleLabel,
                                        canInspect = canInspectPkg,
                                        canModify = canModifyPkg,
                                        onLog = mainViewModel::logActivity
                                    )
                                }
                                composable(ROUTE_STORE) {
                                    val isRealAdmin = myProfile?.parsedRole() == UserRole.ADMIN &&
                                        myProfile.parsedStatus() == ActiveStatus.ACTIVE
                                    val canPublishStore = isRealAdmin ||
                                        myProfile?.parsedRole() == UserRole.DEVELOPER
                                    StoreScreen(
                                        storeViewModel = storeViewModel,
                                        aiModViewModel = aiModViewModel,
                                        pkgStudioViewModel = pkgViewModel,
                                        consoleState = consoleState,
                                        cloudItemsState = storeItemsState,
                                        currentUserUid = currentUser.uid,
                                        isAdmin = isRealAdmin,
                                        canPublish = canPublishStore,
                                        canDeployToConsole = canAccessConsole,
                                        roleName = effectiveRoleLabel,
                                        onPublishItem = mainViewModel::publishStoreItem,
                                        onDeleteItem = mainViewModel::deleteStoreItem,
                                        onNavigateToPkgStudio = { navigateTo(ROUTE_PKG) },
                                        onLog = mainViewModel::logActivity
                                    )
                                }
                                composable(ROUTE_CONSOLE) {
                                    ConsoleDebugScreen(
                                        viewModel = consoleViewModel,
                                        hackerViewModel = hackerViewModel,
                                        profilesState = consoleProfilesState,
                                        roleName = effectiveRoleLabel,
                                        canAccessConsole = canAccessConsole,
                                        onSaveProfile = mainViewModel::saveConsoleProfile,
                                        onDeleteProfile = mainViewModel::deleteConsoleProfile,
                                        onLog = mainViewModel::logActivity
                                    )
                                }
                                composable(ROUTE_DEVOPS) {
                                    DevToolsScreen(
                                        viewModel = devToolsViewModel,
                                        vcsConfigsState = vcsConfigsState,
                                        activeTitleId = activeTitleId,
                                        roleName = effectiveRoleLabel,
                                        canUseDevTools = canUseDevTools,
                                        onSaveVcsConfig = mainViewModel::saveVcsConfig,
                                        onLog = mainViewModel::logActivity
                                    )
                                }
                                composable(ROUTE_ADMIN) {
                                    AdminRbacScreen(
                                        myProfile = myProfile,
                                        previewRoleOverride = previewRoleOverride,
                                        allUsersState = allUsersState,
                                        auditEventsState = auditEventsState,
                                        currentVersionCode = mainViewModel.currentAppVersionCode(),
                                        onSetPreviewRole = mainViewModel::setPreviewRole,
                                        onUpdateUserAccess = mainViewModel::updateUserAccess,
                                        onPublishRelease = mainViewModel::publishAppRelease
                                    )
                                }
                                composable(ROUTE_SYSTEM) {
                                    SystemScreen(
                                        preferences = preferences,
                                        activityLogsState = activityLogsState,
                                        releasesState = releasesState,
                                        currentVersionCode = mainViewModel.currentAppVersionCode(),
                                        currentVersionName = mainViewModel.currentAppVersionName(),
                                        roleName = effectiveRoleLabel,
                                        canViewDiagnostics = canViewDiagnostics,
                                        canCheckUpdates = canCheckUpdates,
                                        onSetTheme = mainViewModel::setThemeMode,
                                        onSetLanguage = mainViewModel::setLanguageTag,
                                        onSetGlassOpacity = mainViewModel::setGlassOpacity,
                                        onSetPushAlerts = mainViewModel::setPushUpdateAlerts,
                                        onSignOut = onSignOut
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
private fun WorkspaceTopBar(
    email: String,
    roleLabel: String,
    isPreviewActive: Boolean,
    updateVersionName: String?,
    onResetPreview: () -> Unit,
    onOpenRbac: () -> Unit,
    onUpdateClick: () -> Unit
) {
    val glass = LocalGlassConfig.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = glass.opacity.coerceIn(0.35f, 0.90f)),
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = if (glass.isDark) 0.25f else 0.65f),
                    ElectricCyan.copy(alpha = 0.28f)
                )
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = lStr(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (updateVersionName != null) {
                    GlassStatusPill(
                        text = "v$updateVersionName",
                        color = AmberWarning,
                        icon = Icons.Rounded.SystemUpdate,
                        modifier = Modifier
                            .clickable(onClick = onUpdateClick)
                            .testTag("topbar_update_pill")
                    )
                }

                if (isPreviewActive) {
                    GlassStatusPill(
                        text = "RBAC",
                        color = ElectricCyan,
                        icon = Icons.Rounded.AdminPanelSettings,
                        modifier = Modifier
                            .clickable(onClick = onOpenRbac)
                            .testTag("topbar_admin_pill")
                    )
                }

                GlassStatusPill(
                    text = roleLabel,
                    color = if (isPreviewActive) AmberWarning else EmeraldSuccess,
                    icon = if (isPreviewActive) Icons.Rounded.Visibility else Icons.Rounded.AdminPanelSettings,
                    modifier = Modifier
                        .clickable {
                            if (isPreviewActive) onResetPreview() else onOpenRbac()
                        }
                        .testTag("topbar_role_pill")
                )
            }
        }
    }
}

@Composable
private fun GlassBottomNavBar(
    currentRoute: String,
    onSelect: (String) -> Unit
) {
    val glass = LocalGlassConfig.current
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = (glass.opacity + 0.20f).coerceIn(0.55f, 0.94f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (glass.isDark) 0.14f else 0.45f))
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp
        ) {
            bottomDestinations.forEach { dest ->
                val selected = currentRoute == dest.route
                val label = lStr(dest.labelRes)
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(dest.route) },
                    icon = {
                        Icon(
                            imageVector = dest.icon,
                            contentDescription = label
                        )
                    },
                    label = {
                        Text(
                            text = label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ElectricCyan,
                        selectedTextColor = ElectricCyan,
                        indicatorColor = ElectricCyan.copy(alpha = 0.16f),
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("nav_item_${dest.route}")
                )
            }
        }
    }
}

@Composable
private fun GlassNavigationRail(
    currentRoute: String,
    onSelect: (String) -> Unit
) {
    val glass = LocalGlassConfig.current
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = (glass.opacity + 0.15f).coerceIn(0.45f, 0.92f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (glass.isDark) 0.14f else 0.45f))
    ) {
        NavigationRail(
            containerColor = Color.Transparent,
            header = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(ElectricCyan.copy(alpha = 0.25f), IridescentIndigo.copy(alpha = 0.25f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Inventory2,
                        contentDescription = lStr(R.string.app_name),
                        tint = ElectricCyan
                    )
                }
            }
        ) {
            railDestinations.forEach { dest ->
                val selected = currentRoute == dest.route
                val label = lStr(dest.labelRes)
                NavigationRailItem(
                    selected = selected,
                    onClick = { onSelect(dest.route) },
                    icon = {
                        Icon(
                            imageVector = dest.icon,
                            contentDescription = label
                        )
                    },
                    label = {
                        Text(
                            text = label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = ElectricCyan,
                        selectedTextColor = ElectricCyan,
                        indicatorColor = ElectricCyan.copy(alpha = 0.18f),
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("nav_item_${dest.route}")
                )
            }
        }
    }
}

@Composable
private fun SuspendedAccountScreen(
    email: String,
    onSignOut: () -> Unit
) {
    LiquidGlassBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            LiquidGlassCard(
                modifier = Modifier.widthIn(max = 460.dp),
                accentColor = CoralError,
                contentPadding = PaddingValues(24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(CoralError.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Block,
                            contentDescription = lStr(R.string.account_suspended_title),
                            tint = CoralError,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Text(
                        text = lStr(R.string.account_suspended_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = lStr(R.string.account_suspended_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (email.isNotBlank()) {
                        GlassStatusPill(text = email, color = CoralError)
                    }
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = onSignOut,
                        colors = ButtonDefaults.buttonColors(containerColor = CoralError),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("suspended_sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(lStr(R.string.sign_out))
                    }
                }
            }
        }
    }
}
