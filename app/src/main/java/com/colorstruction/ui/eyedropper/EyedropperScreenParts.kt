package com.colorstruction.ui.eyedropper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.colorstruction.R

/**
 * Eyedropper app bar: back cancels, check confirms the sampled color.
 *
 * Insets are zero because the root Scaffold already pads for the system bars.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EyedropperTopBar(canConfirm: Boolean, onCancel: () -> Unit, onConfirm: () -> Unit) {
    TopAppBar(
        windowInsets = WindowInsets(0),
        title = { Text(stringResource(R.string.eyedropper_title)) },
        navigationIcon = {
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
        },
        actions = {
            IconButton(onClick = onConfirm, enabled = canConfirm) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = stringResource(R.string.confirm),
                )
            }
        },
    )
}

/** Centered progress indicator with the "loading" caption. */
@Composable
fun EyedropperLoading() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator()
        EyedropperMessage(stringResource(R.string.eyedropper_loading))
    }
}

/** Centered status text (loading or error). */
@Composable
fun EyedropperMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(24.dp),
    )
}
