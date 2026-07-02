package cn.buffcow.hypersc.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * Theme wrapper for HyperSmartCharge.
 *
 * Uses MiuixTheme with system-following color scheme to match
 * the MIUI/HyperOS look and feel.
 *
 * @author qingyu
 */
@Composable
fun HyperSmartChargeTheme(
    content: @Composable () -> Unit
) {
    val controller = remember { ThemeController(ColorSchemeMode.System) }
    MiuixTheme(
        controller = controller,
        content = content
    )
}
