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
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Miuix Compose dialog content for setting the smart charge cutoff value.
 *
 * Slider range: 19..100
 *   - Position 0 → value 19 → "Close" (disable smart charge)
 *   - Position >=1 → real percent values 20..100
 *
 * @author qingyu
 */
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

    val displayText: String
        get() {
            val intValue = sliderValue.roundToInt()
            return if (intValue < ChargeProtectionUtils.MIN_CHARGE_PERCENT_VALUE) {
                "Close"
            } else {
                "$intValue%"
            }
        }

    HyperSmartChargeTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            BasicText(
                text = displayText,
                style = MiuixTheme.textStyles.title3.copy(
                    color = MiuixTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                valueRange = MIN_SLIDER_VALUE..MAX_SLIDER_VALUE,
                steps = (MAX_SLIDER_VALUE - MIN_SLIDER_VALUE).toInt() - 1,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            BasicText(
                text = "The device will stop charging when battery reaches the custom value.",
                style = MiuixTheme.textStyles.body2.copy(
                    color = MiuixTheme.colorScheme.disabledOnSecondaryVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    text = "Cancel",
                    onClick = onCancel
                )
                Spacer(modifier = Modifier.padding(8.dp))
                Button(
                    text = "OK",
                    onClick = {
                        val intValue = sliderValue.roundToInt()
                        val result = if (intValue < ChargeProtectionUtils.MIN_CHARGE_PERCENT_VALUE) {
                            null
                        } else {
                            intValue
                        }
                        onConfirm(result)
                    }
                )
            }
        }
    }
}

private const val MIN_SLIDER_VALUE = 19f
private const val MAX_SLIDER_VALUE = 100f
