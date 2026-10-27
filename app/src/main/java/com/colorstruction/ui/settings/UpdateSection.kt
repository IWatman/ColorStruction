package com.colorstruction.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.colorstruction.BuildConfig
import com.colorstruction.R
import com.colorstruction.update.UpdateState

/**
 * Updater section: installed version plus the controls of the current [UpdateState].
 *
 * @param state updater state to render
 * @param onCheck asks the updater to look for a newer release
 * @param onDownloadAndInstall downloads and installs the known release
 * @param onCancel aborts the download or the install in progress
 */
@Composable
fun UpdateSection(
    state: UpdateState,
    onCheck: () -> Unit,
    onDownloadAndInstall: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val permissionIntent = remember(context) {
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        )
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_updates),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.current_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyMedium,
        )
        when (state) {
            UpdateState.Idle -> CheckButton(onCheck = onCheck)
            UpdateState.Checking -> CheckingRow()
            UpdateState.UpToDate -> UpToDateRow(onCheck = onCheck)
            is UpdateState.Available -> AvailableRow(
                state = state,
                onDownloadAndInstall = onDownloadAndInstall,
            )

            is UpdateState.Downloading -> DownloadingRow(state = state, onCancel = onCancel)
            is UpdateState.Installing -> InstallingRow(onCancel = onCancel)
            is UpdateState.NeedsInstallPermission -> PermissionRow(
                onOpenSettings = { context.startActivity(permissionIntent) },
            )

            is UpdateState.Failed -> FailedContent(
                state = state,
                onCheck = onCheck,
                onDownloadAndInstall = onDownloadAndInstall,
            )
        }
    }
}
