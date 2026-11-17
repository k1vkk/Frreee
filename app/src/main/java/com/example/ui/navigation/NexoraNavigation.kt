package com.example.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.R
import kotlinx.serialization.Serializable

sealed interface NexoraRoute {
    @Serializable data object Home : NexoraRoute
    @Serializable data object Servers : NexoraRoute
    @Serializable data object SpeedTest : NexoraRoute
    @Serializable data object Subscriptions : NexoraRoute
    @Serializable data object Settings : NexoraRoute
    @Serializable data object Profile : NexoraRoute
}

data class TopLevelDestination(
    val route: NexoraRoute,
    @param:StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

val nexoraDestinations = listOf(
    TopLevelDestination(
        route = NexoraRoute.Home,
        labelRes = R.string.nav_home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_home"
    ),
    TopLevelDestination(
        route = NexoraRoute.Servers,
        labelRes = R.string.nav_servers,
        selectedIcon = Icons.Filled.Dns,
        unselectedIcon = Icons.Outlined.Dns,
        testTag = "nav_servers"
    ),
    TopLevelDestination(
        route = NexoraRoute.SpeedTest,
        labelRes = R.string.nav_speed_test,
        selectedIcon = Icons.Filled.Speed,
        unselectedIcon = Icons.Outlined.Speed,
        testTag = "nav_speed_test"
    ),
    TopLevelDestination(
        route = NexoraRoute.Subscriptions,
        labelRes = R.string.nav_subscriptions,
        selectedIcon = Icons.Filled.WorkspacePremium,
        unselectedIcon = Icons.Outlined.WorkspacePremium,
        testTag = "nav_subscriptions"
    ),
    TopLevelDestination(
        route = NexoraRoute.Settings,
        labelRes = R.string.nav_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_settings"
    ),
    TopLevelDestination(
        route = NexoraRoute.Profile,
        labelRes = R.string.nav_profile,
        selectedIcon = Icons.Filled.AccountCircle,
        unselectedIcon = Icons.Outlined.AccountCircle,
        testTag = "nav_profile"
    )
)
