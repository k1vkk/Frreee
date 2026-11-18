package com.example

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.navigation.NexoraRoute
import com.example.ui.navigation.nexoraDestinations
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ServersScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SpeedTestScreen
import com.example.ui.screens.SubscriptionsScreen
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NexoraTheme
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.AccountViewModel
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.ServersViewModel
import com.example.ui.viewmodel.SpeedTestViewModel
import java.util.Locale

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            NexoraRootApp(widthSizeClass = windowSizeClass.widthSizeClass)
        }
    }
}

@Composable
private fun NexoraRootApp(widthSizeClass: WindowWidthSizeClass) {
    val mainViewModel: MainViewModel = viewModel()
    val serversViewModel: ServersViewModel = viewModel()
    val speedTestViewModel: SpeedTestViewModel = viewModel()
    val accountViewModel: AccountViewModel = viewModel()

    val preferences by mainViewModel.preferences.collectAsStateWithLifecycle()
    val vpnState by mainViewModel.vpnState.collectAsStateWithLifecycle()
    val servers by mainViewModel.servers.collectAsStateWithLifecycle()
    val billingState by mainViewModel.billingState.collectAsStateWithLifecycle()
    val uiMessage by mainViewModel.uiMessage.collectAsStateWithLifecycle()

    val serversUiState by serversViewModel.uiState.collectAsStateWithLifecycle()
    val speedProgress by speedTestViewModel.progress.collectAsStateWithLifecycle()
    val speedHistory by speedTestViewModel.filteredHistory.collectAsStateWithLifecycle()
    val speedHistoryFilter by speedTestViewModel.historyFilter.collectAsStateWithLifecycle()
    val speedComparison by speedTestViewModel.comparisonState.collectAsStateWithLifecycle()
    val speedEcoMode by speedTestViewModel.ecoMode.collectAsStateWithLifecycle()
    val speedIncludeUpload by speedTestViewModel.includeUpload.collectAsStateWithLifecycle()
    val sessions by accountViewModel.sessions.collectAsStateWithLifecycle()
    val authLoading by accountViewModel.authLoading.collectAsStateWithLifecycle()
    val authError by accountViewModel.authError.collectAsStateWithLifecycle()
    val authNotice by accountViewModel.authNotice.collectAsStateWithLifecycle()
    val apiHealth by accountViewModel.apiHealth.collectAsStateWithLifecycle()

    val baseContext = LocalContext.current
    val baseConfig = LocalConfiguration.current

    val effectiveLocale = remember(preferences.languageMode) {
        when (preferences.languageMode) {
            "ar" -> Locale.forLanguageTag("ar")
            "en" -> Locale.forLanguageTag("en")
            else -> Locale.getDefault()
        }
    }
    val isArabic = effectiveLocale.language == "ar"
    val layoutDirection = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr

    val localizedContext = remember(baseContext, effectiveLocale) {
        val cfg = Configuration(baseContext.resources.configuration)
        cfg.setLocale(effectiveLocale)
        cfg.setLayoutDirection(effectiveLocale)
        baseContext.createConfigurationContext(cfg)
    }
    val localizedConfig = remember(baseConfig, effectiveLocale) {
        Configuration(baseConfig).apply {
            setLocale(effectiveLocale)
            setLayoutDirection(effectiveLocale)
        }
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        mainViewModel.onVpnPermissionResult(result.resultCode == Activity.RESULT_OK)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val confPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            mainViewModel.importConfFromUri(uri)
        }
    }

    LaunchedEffect(Unit) {
        mainViewModel.vpnPermissionRequest.collect { intent ->
            vpnPermissionLauncher.launch(intent)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiMessage) {
        if (uiMessage != null) {
            snackbarHostState.showSnackbar(uiMessage!!)
            mainViewModel.clearMessage()
        }
    }

    val requestNotificationIfNeeded = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                baseContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfig,
        LocalLayoutDirection provides layoutDirection
    ) {
        NexoraTheme(isArabic = isArabic) {
            val navController = rememberNavController()
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            val useRail = widthSizeClass != WindowWidthSizeClass.Compact

            val navigateTopLevel: (NexoraRoute) -> Unit = { route ->
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = ObsidianBlack,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(
                            containerColor = ObsidianSurface,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            nexoraDestinations.forEach { dest ->
                                val selected = currentDestination?.hierarchy?.any {
                                    it.hasRoute(dest.route::class)
                                } == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(dest.labelRes),
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                            fontSize = 10.sp
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = NeonCyan,
                                        selectedTextColor = NeonCyan,
                                        indicatorColor = ElectricBlue.copy(alpha = 0.22f),
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    ),
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail) {
                        NavigationRail(
                            containerColor = ObsidianSurface,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Spacer(modifier = Modifier.weight(0.1f))
                            nexoraDestinations.forEach { dest ->
                                val selected = currentDestination?.hierarchy?.any {
                                    it.hasRoute(dest.route::class)
                                } == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(dest.labelRes),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = NeonCyan,
                                        selectedTextColor = NeonCyan,
                                        indicatorColor = ElectricBlue.copy(alpha = 0.22f),
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    ),
                                    modifier = Modifier
                                        .padding(vertical = 4.dp)
                                        .testTag(dest.testTag)
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(ObsidianBlack)
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = NexoraRoute.Home,
                            enterTransition = {
                                fadeIn(animationSpec = tween(220)) + slideIntoContainer(
                                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                    animationSpec = tween(220)
                                )
                            },
                            exitTransition = {
                                fadeOut(animationSpec = tween(180))
                            },
                            popEnterTransition = {
                                fadeIn(animationSpec = tween(220))
                            },
                            popExitTransition = {
                                fadeOut(animationSpec = tween(180))
                            }
                        ) {
                            composable<NexoraRoute.Home> {
                                HomeScreen(
                                    vpnState = vpnState,
                                    preferences = preferences,
                                    hasServers = servers.isNotEmpty(),
                                    isArabic = isArabic,
                                    onToggleConnect = {
                                        requestNotificationIfNeeded()
                                        mainViewModel.onConnectClicked()
                                    },
                                    onConnectBestServer = {
                                        requestNotificationIfNeeded()
                                        mainViewModel.connectBestServerByPing()
                                    },
                                    onRefreshIp = { mainViewModel.refreshIps() },
                                    onToggleLanguage = {
                                        accountViewModel.setLanguageMode(if (isArabic) "en" else "ar")
                                    },
                                    onNavigateServers = { navigateTopLevel(NexoraRoute.Servers) },
                                    onNavigateSettings = { navigateTopLevel(NexoraRoute.Settings) },
                                    onImportConf = { confPickerLauncher.launch(arrayOf("*/*")) } ,
                                    onDismissError = { mainViewModel.clearMessage() }
                                )
                            }

                            composable<NexoraRoute.Servers> {
                                ServersScreen(
                                    uiState = serversUiState,
                                    activeServerId = vpnState.activeServer?.id,
                                    vpnStatus = vpnState.status,
                                    isFindingBestServer = vpnState.isFindingBestServer,
                                    onSearchChange = serversViewModel::setSearchQuery,
                                    onFilterChange = serversViewModel::setFilter,
                                    onSyncBackend = serversViewModel::syncServersFromBackend,
                                    onMeasurePings = serversViewModel::measureAllPings,
                                    onConnectBestServer = {
                                        requestNotificationIfNeeded()
                                        mainViewModel.connectBestServerByPing()
                                        navigateTopLevel(NexoraRoute.Home)
                                    },
                                    onImportConf = { confPickerLauncher.launch(arrayOf("*/*")) },
                                    onAddManual = serversViewModel::addManualServer,
                                    onToggleFavorite = serversViewModel::toggleFavorite,
                                    onConnectServer = { server ->
                                        requestNotificationIfNeeded()
                                        mainViewModel.connectToServer(server)
                                        navigateTopLevel(NexoraRoute.Home)
                                    },
                                    onDeleteServer = serversViewModel::deleteServer,
                                    onDismissMessage = serversViewModel::clearMessage
                                )
                            }

                            composable<NexoraRoute.SpeedTest> {
                                SpeedTestScreen(
                                    progress = speedProgress,
                                    history = speedHistory,
                                    historyFilter = speedHistoryFilter,
                                    comparisonState = speedComparison,
                                    vpnState = vpnState,
                                    ecoMode = speedEcoMode,
                                    includeUpload = speedIncludeUpload,
                                    onAttachWebView = speedTestViewModel::attachWebView,
                                    onDetachWebView = speedTestViewModel::detachWebView,
                                    onStart = speedTestViewModel::startSpeedTest,
                                    onStop = speedTestViewModel::stopSpeedTest,
                                    onSetEcoMode = speedTestViewModel::setEcoMode,
                                    onSetIncludeUpload = speedTestViewModel::setIncludeUpload,
                                    onSetHistoryFilter = speedTestViewModel::setHistoryFilter,
                                    onSelectAsBaseline = speedTestViewModel::selectAsBaseline,
                                    onSelectAsTarget = speedTestViewModel::selectAsTarget,
                                    onResetComparison = speedTestViewModel::resetComparisonToDefault,
                                    onDeleteEntry = speedTestViewModel::deleteHistoryEntry,
                                    onClearHistory = speedTestViewModel::clearHistory
                                )
                            }

                            composable<NexoraRoute.Subscriptions> {
                                val app = baseContext.applicationContext as NexoraApp
                                SubscriptionsScreen(
                                    activePlanTier = preferences.planTier,
                                    billingState = billingState,
                                    onSubscribe = { act, productId ->
                                        app.billingManager.launchPurchase(act, productId)
                                    },
                                    onRestorePurchases = {
                                        app.billingManager.restorePurchases()
                                        accountViewModel.syncSubscription()
                                    }
                                )
                            }

                            composable<NexoraRoute.Settings> {
                                SettingsScreen(
                                    preferences = preferences,
                                    apiHealth = apiHealth,
                                    onSaveAndTestApi = accountViewModel::saveAndTestBackendUrl,
                                    onLanguageSelected = accountViewModel::setLanguageMode,
                                    onSaveWireGuardSettings = accountViewModel::saveWireGuardSettings,
                                    onToggleExcludedPackage = accountViewModel::toggleExcludedPackage
                                )
                            }

                            composable<NexoraRoute.Profile> {
                                ProfileScreen(
                                    preferences = preferences,
                                    sessions = sessions,
                                    authLoading = authLoading,
                                    authError = authError,
                                    authNotice = authNotice,
                                    onSignIn = accountViewModel::signIn,
                                    onRegister = accountViewModel::register,
                                    onForgotPassword = accountViewModel::requestPasswordReset,
                                    onResetPassword = accountViewModel::confirmPasswordReset,
                                    onSignOut = accountViewModel::signOut,
                                    onRotateKey = accountViewModel::rotateWireGuardKey,
                                    onRevokeSession = accountViewModel::revokeSession,
                                    onClearError = accountViewModel::clearAuthError
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
