package com.colorstruction.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.colorstruction.R
import com.colorstruction.update.UpdateError
import com.colorstruction.update.UpdateState

/** Drops the Markdown markers GitHub release notes use (headings, bold, code) for plain display. */
internal fun stripMarkdown(text: String): String =
    text.lines().joinToString("\n") { line ->
        line.replace(Regex("^#{1,6}\\s+"), "")
            .replace(Regex("^\\s*[-*]\\s+"), "• ")
            .replace("**", "")
            .replace("`", "")
    }

/** Release notes, collapsed to six lines until the user expands them. */
@Composable
internal fun ReleaseNotes(notes: String, modifier: Modifier = Modifier) {
    if (notes.isBlank()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.release_notes),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stripMarkdown(notes),
            style = MaterialTheme.typography.bodySmall,
            maxLines = if (expanded) Int.MAX_VALUE else 6,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = { expanded = !expanded }) {
            Text(
                stringResource(if (expanded) R.string.show_less else R.string.show_more),
            )
        }
    }
}

/** Failure message plus the retry button of [UpdateState.Failed]. */
@Composable
internal fun FailedContent(
    state: UpdateState.Failed,
    onCheck: () -> Unit,
    onDownloadAndInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = failureMessage(state.reason),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Button(
            onClick = { if (state.release != null) onDownloadAndInstall() else onCheck() },
        ) {
            Text(stringResource(R.string.try_again))
        }
    }
}

/** Maps an [UpdateError] to its localized message. */
@Composable
private fun failureMessage(reason: UpdateError): String = when (reason) {
    is UpdateError.Network -> stringResource(R.string.error_network)
    is UpdateError.RateLimited -> stringResource(R.string.error_rate_limited)
    UpdateError.NoApkAsset -> stringResource(R.string.error_no_apk)
    UpdateError.NoRelease -> stringResource(R.string.error_no_release)
    else -> stringResource(R.string.error_generic, reason.message ?: "")
}
