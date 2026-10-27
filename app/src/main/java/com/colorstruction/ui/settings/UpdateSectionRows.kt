package com.colorstruction.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.colorstruction.R
import com.colorstruction.update.UpdateState

/** "Check for updates" button. */
@Composable
internal fun CheckButton(onCheck: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onCheck, modifier = modifier) {
        Text(stringResource(R.string.check_updates))
    }
}

/** Spinner plus "Checking…" while the release list is fetched. */
@Composable
internal fun CheckingRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(
            text = stringResource(R.string.checking),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** "Latest version" note plus another check button. */
@Composable
internal fun UpToDateRow(onCheck: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.up_to_date),
            style = MaterialTheme.typography.bodyMedium,
        )
        CheckButton(onCheck = onCheck)
    }
}

/** Available release: version, release notes and the download button. */
@Composable
internal fun AvailableRow(
    state: UpdateState.Available,
    onDownloadAndInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(
                R.string.update_available,
                state.release.version?.toString() ?: state.release.tag,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        ReleaseNotes(notes = state.release.notes)
        Button(onClick = onDownloadAndInstall) {
            Text(stringResource(R.string.download_install))
        }
    }
}
