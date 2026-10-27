package com.colorstruction.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavType
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.colorstruction.R
import com.colorstruction.color.Rgb
import com.colorstruction.ui.mix.MixScreen
import com.colorstruction.ui.mix.PICKED_COLOR_KEY
import com.colorstruction.ui.paints.PaintsScreen
import com.colorstruction.ui.picker.ColorPickerScreen
import com.colorstruction.ui.settings.SettingsScreen

private enum class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    Mix("mix", R.string.nav_mix, Icons.Filled.Science),
    Paints("paints", R.string.nav_paints, Icons.Filled.Palette),
    Settings("settings", R.string.nav_settings, Icons.Filled.Settings),
}

private const val PICKER_ROUTE = "picker"
private const val PICKER_COLOR_ARG = "color"
private const val PICKER_PATTERN = "$PICKER_ROUTE?$PICKER_COLOR_ARG={$PICKER_COLOR_ARG}"
private const val DEFAULT_TARGET_ARGB: Int = 0xFF3D933E.toInt()

/** Root navigation graph: three bottom tabs plus the full screen color picker. */
@Composable
fun ColorStructionRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = currentDestination?.route != PICKER_PATTERN

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentDestination
                        ?.hierarchy
                        ?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Mix.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopLevelDestination.Mix.route) { entry ->
                MixScreen(
                    onPickColor = { rgb ->
                        navController.navigate("$PICKER_ROUTE?$PICKER_COLOR_ARG=${rgb.toArgbInt()}")
                    },
                    savedStateHandle = entry.savedStateHandle,
                )
            }
            composable(TopLevelDestination.Paints.route) { PaintsScreen() }
            composable(TopLevelDestination.Settings.route) { SettingsScreen() }
            composable(
                route = PICKER_PATTERN,
                arguments = listOf(
                    navArgument(PICKER_COLOR_ARG) {
                        type = NavType.IntType
                        defaultValue = DEFAULT_TARGET_ARGB
                    },
                ),
            ) { entry ->
                val initialArgb = entry.arguments?.getInt(PICKER_COLOR_ARG) ?: DEFAULT_TARGET_ARGB
                ColorPickerScreen(
                    initialColor = Rgb.fromArgbInt(initialArgb),
                    onConfirm = { rgb ->
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(PICKED_COLOR_KEY, rgb.toArgbInt())
                        navController.popBackStack()
                    },
                    onCancel = { navController.popBackStack() },
                )
            }
        }
    }
}
