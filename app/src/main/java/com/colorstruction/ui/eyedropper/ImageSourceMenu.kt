package com.colorstruction.ui.eyedropper

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.colorstruction.R

/**
 * "From image" button with a menu offering the gallery and the camera.
 *
 * @param onPickFromImage invoked with the chosen image to open the eyedropper on
 */
@Composable
fun ImageSourceMenu(
    onPickFromImage: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    val launcher = rememberImageSourceLauncher(onImage = onPickFromImage)
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedButton(onClick = { expanded = true }) {
            Icon(imageVector = Icons.Outlined.AddPhotoAlternate, contentDescription = null)
            Text(stringResource(R.string.from_image), Modifier.padding(start = 8.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.from_gallery)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Outlined.PhotoLibrary, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    launcher.pickFromGallery()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.take_photo)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Outlined.PhotoCamera, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    launcher.takePhoto()
                },
            )
        }
    }
}
