package com.colorstruction.ui.paints

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.colorstruction.R
import com.colorstruction.color.Rgb
import com.colorstruction.data.Paint

/** Small rounded color swatch. */
@Composable
internal fun ColorDot(color: Rgb, size: Dp = 24.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(color.toArgbInt()))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
    )
}

/** Read-only dropdown choosing the paint mixed with the calibrated one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PartnerSelector(
    partners: List<Paint>,
    selected: Paint?,
    onSelect: (Paint) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.name.orEmpty(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.calibration_partner)) },
            leadingIcon = if (selected != null) {
                { ColorDot(selected.color) }
            } else {
                null
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            partners.forEach { paint ->
                DropdownMenuItem(
                    text = { Text(paint.name) },
                    leadingIcon = { ColorDot(paint.color) },
                    onClick = {
                        expanded = false
                        onSelect(paint)
                    },
                )
            }
        }
    }
}

/** "1 : 4" style ratio with a stepper on each side. */
@Composable
internal fun PartsRow(
    paintParts: Int,
    partnerParts: Int,
    onPaintPartsChange: (Int) -> Unit,
    onPartnerPartsChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        PartsStepper(paintParts, onPaintPartsChange)
        Text(text = ":", style = MaterialTheme.typography.titleMedium)
        PartsStepper(partnerParts, onPartnerPartsChange)
    }
}

@Composable
private fun PartsStepper(parts: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { onChange(parts - 1) },
            enabled = parts > MIN_CALIBRATION_PARTS,
        ) {
            Icon(imageVector = Icons.Filled.Remove, contentDescription = null)
        }
        Text(text = parts.toString(), style = MaterialTheme.typography.titleMedium)
        IconButton(
            onClick = { onChange(parts + 1) },
            enabled = parts < MAX_CALIBRATION_PARTS,
        ) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = null)
        }
    }
}
