package com.colorstruction.ui.mix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.colorstruction.R
import com.colorstruction.data.AppSettings

/** Totals offered as one-tap chips in the amount mode. */
private val QUICK_TOTALS = listOf(50, 100, 250)

/**
 * "Total, ml" field next to the quick total chips, shown in [com.colorstruction.data.RatioFormat.AMOUNT].
 *
 * The typed value is applied as soon as it is a number within
 * [AppSettings.MIN_TOTAL_AMOUNT]..[AppSettings.MAX_TOTAL_AMOUNT].
 *
 * @param totalAmount current total volume in millilitres.
 * @param onTotalAmountChange invoked with the new total volume in millilitres.
 */
@Composable
internal fun TotalAmountRow(totalAmount: Int, onTotalAmountChange: (Int) -> Unit) {
    var text by rememberSaveable(totalAmount) { mutableStateOf(totalAmount.toString()) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                val digits = input.filter { it.isDigit() }.take(4)
                text = digits
                digits.toIntOrNull()
                    ?.takeIf { it in AppSettings.MIN_TOTAL_AMOUNT..AppSettings.MAX_TOTAL_AMOUNT }
                    ?.let(onTotalAmountChange)
            },
            label = { Text(stringResource(R.string.total_amount)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.width(140.dp),
        )
        for (quick in QUICK_TOTALS) {
            FilterChip(
                selected = quick == totalAmount,
                onClick = { onTotalAmountChange(quick) },
                label = { Text(quick.toString()) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
