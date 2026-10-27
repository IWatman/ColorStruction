package com.colorstruction.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.colorstruction.R

/** Settings tab: language, in-app updater and the about block. */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val language by viewModel.language.collectAsStateWithLifecycle()
    val theme by viewModel.themeMode.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        LanguageSection(
            selected = language,
            onSelect = viewModel::setLanguage,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        ThemeSection(
            selected = theme,
            onSelect = viewModel::setThemeMode,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        UpdateSection(
            state = updateState,
            onCheck = viewModel::check,
            onDownloadAndInstall = viewModel::downloadAndInstall,
            onCancel = viewModel::cancel,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        AboutSection()
    }
}
