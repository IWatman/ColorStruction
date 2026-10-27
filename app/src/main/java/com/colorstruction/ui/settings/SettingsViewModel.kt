package com.colorstruction.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.colorstruction.ColorStructionApp
import com.colorstruction.data.SettingsStore
import com.colorstruction.data.ThemeMode
import com.colorstruction.update.UpdateManager
import com.colorstruction.update.UpdateState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State holder of the settings screen: the updater state, UI language and theme mode.
 *
 * @param updateManager app-scoped updater; its state is exposed unchanged.
 * @param settingsStore settings storage that persists the theme mode.
 */
class SettingsViewModel(
    private val updateManager: UpdateManager,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    /** Updater state for the "Updates" section. */
    val updateState: StateFlow<UpdateState> = updateManager.state

    private val _language = MutableStateFlow(
        appLanguageFromTags(AppCompatDelegate.getApplicationLocales().toLanguageTags()),
    )

    /** Currently selected UI language. */
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)

    /** Currently selected theme mode, mirrored from settings storage. */
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { _themeMode.value = it.themeMode }
        }
    }

    /** Asks the updater to look for a newer release. */
    fun check() = updateManager.check()

    /** Downloads the available release and starts installing it. */
    fun downloadAndInstall() = updateManager.downloadAndInstall()

    /** Aborts the download or the install in progress. */
    fun cancel() = updateManager.cancel()

    /** Stores [mode] as the theme mode. */
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsStore.setThemeMode(mode) }
    }

    /** Applies [lang] to the whole app and mirrors it in [language]. */
    fun setLanguage(lang: AppLanguage) {
        val locales = if (lang == AppLanguage.SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(lang.tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
        _language.value = lang
    }

    companion object {
        /** Creates the view model from the application container. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ColorStructionApp
                SettingsViewModel(
                    updateManager = app.container.updateManager,
                    settingsStore = app.container.settingsRepository,
                )
            }
        }
    }
}
