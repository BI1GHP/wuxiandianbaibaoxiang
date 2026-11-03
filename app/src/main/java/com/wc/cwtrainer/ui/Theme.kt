package com.wc.cwtrainer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// —— 短波机金属面板配色 ——
// 参考：ICOM / YAESU / KENWOOD 面板前面板
val Bg = Color(0xFF12161C)              // 整体背景：金属面板深灰
val Surface1 = Color(0xFF1E242D)        // 凸起面板条
val Surface2 = Color(0xFF0A0D11)        // 凹槽 / 屏幕底
val Line = Color(0xFF2A3240)            // 面板分割线 / 边框
val Amber = Color(0xFFFFC857)           // 琥珀色 LED 数码管
val Green = Color(0xFF3DDC97)           // 绿色 LED
val Cyan = Color(0xFF00D4FF)             // S Meter 蓝绿
val TextMain = Color(0xFFE6EDF7)        // 高亮文字
val TextDim = Color(0xFF7A8599)         // 熄灭态 / 次要文字
val Red = Color(0xFFFF4444)              // 红色 LED / 警告

// 附加
val LedDim = Color(0xFF3A4250)          // LED 熄灭态
val MetalLight = Color(0xFF2A3240)      // 金属高光
val MetalDark = Color(0xFF0A0D11)       // 金属阴影

private val colors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF1A1200),
    secondary = Green,
    onSecondary = Color(0xFF00160C),
    background = Bg,
    onBackground = TextMain,
    surface = Surface1,
    onSurface = TextMain,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextDim,
    error = Red
)

@Composable
fun WcTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
