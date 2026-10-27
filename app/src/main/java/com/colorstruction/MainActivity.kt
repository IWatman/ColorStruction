package com.colorstruction

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.colorstruction.data.ThemeMode
import com.colorstruction.ui.ColorStructionRoot
import com.colorstruction.ui.theme.ColorStructionTheme

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsRepository = (application as ColorStructionApp).container.settingsRepository
        setContent {
            // Render nothing until settings are read, so a stored theme doesn't flash the other one.
            val loaded by settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = null)
            val settings = loaded ?: return@setContent
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            ColorStructionTheme(darkTheme = darkTheme) {
                ColorStructionRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Unsticks the updater after the install dialog, a dead session or a granted permission.
        (application as ColorStructionApp).container.updateManager.onAppResumed()
    }
}
