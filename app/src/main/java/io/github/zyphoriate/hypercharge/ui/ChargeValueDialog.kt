package io.github.zyphoriate.hypercharge.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.zyphoriate.hypercharge.ui.theme.HyperSmartChargeTheme
import io.github.zyphoriate.hypercharge.utils.ChargeProtectionUtils
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val MIN_SLIDER_VALUE = 19f
private const val MAX_SLIDER_VALUE = 100f

@Composable
fun ChargeValueDialogContent(
    context: Context,
    onConfirm: (value: Int?) -> Unit,
    onCancel: () -> Unit,
) {
    val existingValue = remember {
        ChargeProtectionUtils.getSmartChargePercentValue(context)?.toFloat() ?: MIN_SLIDER_VALUE
    }
    var sliderValue by mutableFloatStateOf(existingValue)

    // Reactively derive display text from slider value
    val displayText by remember { derivedStateOf { formatSliderValue(sliderValue) } }

    HyperSmartChargeTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Value display — updates reactively as slider moves
            BasicText(
                text = displayText,
                style = MiuixTheme.textStyles.title3.copy(
                    color = MiuixTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Slider: 19 (Close) to 100
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                valueRange = MIN_SLIDER_VALUE..MAX_SLIDER_VALUE,
                steps = (MAX_SLIDER_VALUE - MIN_SLIDER_VALUE).toInt() - 1,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Note
            BasicText(
                text = "Device stops charging when battery reaches the set value.",
                style = MiuixTheme.textStyles.body2.copy(
                    color = MiuixTheme.colorScheme.disabledOnSecondaryVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(text = "Cancel", onClick = onCancel)
                Spacer(modifier = Modifier.padding(8.dp))
                TextButton(
                    text = "OK",
                    onClick = {
                        val intValue = sliderValue.roundToInt()
                        val result = if (intValue < ChargeProtectionUtils.MIN_CHARGE_PERCENT_VALUE) null
                        else intValue
                        onConfirm(result)
                    }
                )
            }
        }
    }
}

private fun formatSliderValue(sliderValue: Float): String {
    val intValue = sliderValue.roundToInt()
    return if (intValue < ChargeProtectionUtils.MIN_CHARGE_PERCENT_VALUE) "Close" else "$intValue%"
}
