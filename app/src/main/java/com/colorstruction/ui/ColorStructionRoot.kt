package com.colorstruction.ui

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavType
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.colorstruction.ColorStructionApp
import com.colorstruction.R
import com.colorstruction.color.Rgb
import com.colorstruction.ui.eyedropper.EyedropperScreen
import com.colorstruction.ui.mix.MixScreen
import com.colorstruction.ui.mix.PICKED_COLOR_KEY
import com.colorstruction.ui.paints.PaintEditorScreen
import com.colorstruction.ui.paints.PaintsScreen
import com.colorstruction.ui.picker.ColorPickerScreen
import com.colorstruction.ui.settings.SettingsScreen
import com.colorstruction.update.UpdateState

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

private const val PAINT_EDITOR_ROUTE = "paintEditor"
private const val PALETTE_ID_ARG = "paletteId"
private const val PAINT_ID_ARG = "paintId"
private const val PAINT_EDITOR_PATTERN =
    "$PAINT_EDITOR_ROUTE?$PALETTE_ID_ARG={$PALETTE_ID_ARG}&$PAINT_ID_ARG={$PAINT_ID_ARG}"
private const val NEW_PAINT_ID = -1L

private const val EYEDROPPER_ROUTE = "eyedropper"
private const val EYEDROPPER_URI_ARG = "uri"
private const val EYEDROPPER_PATTERN = "$EYEDROPPER_ROUTE?$EYEDROPPER_URI_ARG={$EYEDROPPER_URI_ARG}"

/** Root navigation graph: three bottom tabs plus the color picker, eyedropper and paint editor. */
@Composable
fun ColorStructionRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val appContext = LocalContext.current.applicationContext as ColorStructionApp
    val updateState by appContext.container.updateManager.state.collectAsStateWithLifecycle()
    val showBottomBar = currentDestination?.route != PICKER_PATTERN &&
        currentDestination?.route != PAINT_EDITOR_PATTERN &&
        currentDestination?.route != EYEDROPPER_PATTERN

    // Both the picker and the eyedropper return their color through the previous entry.
    val openEyedropper: (Uri) -> Unit = { uri ->
        navController.navigate("$EYEDROPPER_ROUTE?$EYEDROPPER_URI_ARG=${Uri.encode(uri.toString())}")
    }

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
                            if (destination == TopLevelDestination.Settings) {
                                BadgedBox(
                                    badge = {
                                        if (updateState is UpdateState.Available) Badge()
                                    },
                                ) {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = null,
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = null,
                                )
                            }
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
                    onPickFromImage = openEyedropper,
                    savedStateHandle = entry.savedStateHandle,
                )
            }
            composable(TopLevelDestination.Paints.route) {
                PaintsScreen(
                    onAddPaint = { paletteId ->
                        navController.navigate(
                            "$PAINT_EDITOR_ROUTE?$PALETTE_ID_ARG=$paletteId" +
                                "&$PAINT_ID_ARG=$NEW_PAINT_ID",
                        )
                    },
                    onEditPaint = { paletteId, paintId ->
                        navController.navigate(
                            "$PAINT_EDITOR_ROUTE?$PALETTE_ID_ARG=$paletteId" +
                                "&$PAINT_ID_ARG=$paintId",
                        )
                    },
                )
            }
            composable(TopLevelDestination.Settings.route) { SettingsScreen() }
            composable(
                route = PAINT_EDITOR_PATTERN,
                arguments = listOf(
                    navArgument(PALETTE_ID_ARG) {
                        type = NavType.LongType
                        defaultValue = NEW_PAINT_ID
                    },
                    navArgument(PAINT_ID_ARG) {
                        type = NavType.LongType
                        defaultValue = NEW_PAINT_ID
                    },
                ),
            ) { entry ->
                val savedStateHandle = entry.savedStateHandle
                val pickedFlow = remember(entry) {
                    savedStateHandle.getStateFlow<Int?>(PICKED_COLOR_KEY, null)
                }
                PaintEditorScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                    onPickColor = { rgb ->
                        navController.navigate("$PICKER_ROUTE?$PICKER_COLOR_ARG=${rgb.toArgbInt()}")
                    },
                    onPickFromImage = openEyedropper,
                    pickedColor = pickedFlow,
                    onClearPickedColor = { savedStateHandle[PICKED_COLOR_KEY] = null },
                )
            }
            composable(
                route = EYEDROPPER_PATTERN,
                arguments = listOf(
                    navArgument(EYEDROPPER_URI_ARG) {
                        type = NavType.StringType
                        nullable = true
                    },
                ),
            ) { entry ->
                val imageUri = entry.arguments
                    ?.getString(EYEDROPPER_URI_ARG)
                    ?.let(Uri::parse)
                if (imageUri == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    EyedropperScreen(
                        imageUri = imageUri,
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
