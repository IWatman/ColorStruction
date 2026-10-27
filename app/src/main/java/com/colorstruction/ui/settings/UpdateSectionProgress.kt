package com.colorstruction.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.colorstruction.R
import com.colorstruction.update.UpdateState
import kotlin.math.roundToInt

/** Download progress with the percentage and a cancel button. */
@Composable
internal fun DownloadingRow(
    state: UpdateState.Downloading,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.downloading, (state.progress * 100f).roundToInt()),
            style = MaterialTheme.typography.bodyMedium,
        )
        CancelButton(onCancel = onCancel)
    }
}

/** Indeterminate progress while the package installer runs. */
@Composable
internal fun InstallingRow(onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(
            text = stringResource(R.string.installing),
            style = MaterialTheme.typography.bodyMedium,
        )
        CancelButton(onCancel = onCancel)
    }
}

/** Explains the missing permission and opens the system screen that grants it. */
@Composable
internal fun PermissionRow(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.install_permission_needed),
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onOpenSettings) {
            Text(stringResource(R.string.open_settings))
        }
    }
}

/** Cancel button shared by the download and install rows. */
@Composable
private fun CancelButton(onCancel: () -> Unit) {
    OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
}
