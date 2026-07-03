package io.github.zyphoriate.hypercharge.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.zyphoriate.hypercharge.utils.ChargeProtectionUtils
import io.github.zyphoriate.hypercharge.utils.RemoteEventHelper
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val existing = remember {
                ChargeProtectionUtils.getSmartChargePercentValue(this@SettingsActivity)?.toFloat()
                    ?: MIN_SLIDER_VALUE
            }
            var sliderValue by mutableFloatStateOf(existing)

            val displayText = run {
                val iv = sliderValue.roundToInt()
                if (iv < MIN_CHARGE_PERCENT_VALUE) "Close" else "$iv%"
            }

            val controller = remember { ThemeController(ColorSchemeMode.System) }
            MiuixTheme(controller = controller) {
                Scaffold(
                    topBar = {
                        SmallTopAppBar(title = "HyperSmartCharge")
                    }
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(padding)
                            .padding(horizontal = 16.dp)
                    ) {
                        SliderPreference(
                            value = sliderValue,
                            onValueChange = { sliderValue = it },
                            title = "Charge limit",
                            summary = displayText,
                            valueText = displayText,
                            valueRange = MIN_SLIDER_VALUE..MAX_SLIDER_VALUE,
                            steps = (MAX_SLIDER_VALUE - MIN_SLIDER_VALUE).toInt() - 1
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        BasicText(
                            text = "Device stops charging when battery reaches the set value.",
                            style = MiuixTheme.textStyles.body2.copy(
                                color = MiuixTheme.colorScheme.disabledOnSecondaryVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Spacer(modifier = Modifier.weight(1f))
                            TextButton(text = "Cancel", onClick = { finish() })
                            TextButton(
                                text = "Save",
                                onClick = {
                                    val ctx = this@SettingsActivity
                                    val intValue = sliderValue.roundToInt()
                                    val percentValue = if (intValue < MIN_CHARGE_PERCENT_VALUE) null
                                    else intValue

                                    val success = if (percentValue != null) {
                                        ChargeProtectionUtils.openCommonProtectMode(percentValue)
                                    } else {
                                        ChargeProtectionUtils.closeSmartCharge()
                                    }

                                    ChargeProtectionUtils.putSmartChargePercentValue(ctx, percentValue)
                                    RemoteEventHelper.sendEvent(
                                        ctx,
                                        RemoteEventHelper.Event.UpdateNotification(
                                            if (success) percentValue?.toString() else null
                                        )
                                    )

                                    val msg = if (success) {
                                        if (percentValue != null) "$percentValue%" else "Closed"
                                    } else "Failed"
                                    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                                    finish()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val MIN_SLIDER_VALUE = 19f
        const val MAX_SLIDER_VALUE = 100f
        private const val MIN_CHARGE_PERCENT_VALUE = ChargeProtectionUtils.MIN_CHARGE_PERCENT_VALUE
    }
}
