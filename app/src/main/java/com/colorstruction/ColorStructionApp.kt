package com.colorstruction

import android.app.Application
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ColorStructionApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Silent check on launch; debug builds skip it to spare the GitHub rate limit.
        if (!BuildConfig.DEBUG) {
            container.updateManager.check()
        }

        container.applicationScope.launch {
            val settingsRepository = container.settingsRepository
            val starterPaletteId = container.paintRepository.ensureStarterPalette()
            val settings = settingsRepository.settings.first()
            if (settings.activePaletteId == null) {
                settingsRepository.setActivePalette(starterPaletteId)
            }
        }
    }
}
