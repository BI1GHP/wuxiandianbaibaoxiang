package com.wc.cwtrainer.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import com.wc.cwtrainer.core.ThemeMode

/**
 * 双主题品牌色板。
 *
 * 两套主题共用同一套语义 Token（背景/面板/凹槽/分隔线/强调色/文字），
 * 页面只引用 Token，不写死颜色，切换主题时整站实时生效。
 */
data class WcColors(
    val isDark: Boolean,
    val bg: Color,
    val surface1: Color,
    val surface2: Color,
    val line: Color,
    val accent: Color,       // 主操作 / 琥珀 LED
    val accentAlt: Color,    // 正确 / 通过
    val info: Color,         // S 表、波形
    val textMain: Color,
    val textDim: Color,
    val danger: Color,
    val ledDim: Color,
    val metalLight: Color,
    val metalDark: Color,
    val onAccent: Color
)

/** 主题 A：野战电台（军规金属面板，默认） */
val FieldPalette = WcColors(
    isDark = true,
    bg = Color(0xFF12161C),
    surface1 = Color(0xFF1E242D),
    surface2 = Color(0xFF0A0D11),
    line = Color(0xFF2A3240),
    accent = Color(0xFFFFC857),
    accentAlt = Color(0xFF3DDC97),
    info = Color(0xFF00D4FF),
    textMain = Color(0xFFE6EDF7),
    textDim = Color(0xFF7A8599),
    danger = Color(0xFFFF4444),
    ledDim = Color(0xFF3A4250),
    metalLight = Color(0xFF2A3240),
    metalDark = Color(0xFF0A0D11),
    onAccent = Color(0xFF1A1200)
)

/** 主题 B：奶油手帐（纸感 / 圆角 / 珊瑚点缀） */
val CreamPalette = WcColors(
    isDark = false,
    bg = Color(0xFFFFF7F2),
    surface1 = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF6F1E4),
    line = Color(0xFFEFE3D8),
    accent = Color(0xFFD9576F),
    accentAlt = Color(0xFF2E8B74),
    info = Color(0xFF3B7EA1),
    textMain = Color(0xFF3A362E),
    textDim = Color(0xFF8E8776),
    danger = Color(0xFFC7503F),
    ledDim = Color(0xFFD8CEC3),
    metalLight = Color(0xFFFFFFFF),
    metalDark = Color(0xFFF0E7DC),
    onAccent = Color(0xFFFFFFFF)
)

/** 皮肤 C：丛林军绿（哑光军绿面板） */
val OlivePalette = WcColors(
    isDark = true,
    bg = Color(0xFF14170F),
    surface1 = Color(0xFF1F2418),
    surface2 = Color(0xFF0C0F08),
    line = Color(0xFF2F3826),
    accent = Color(0xFFC8D96F),
    accentAlt = Color(0xFF8FBF6A),
    info = Color(0xFF7FD1C0),
    textMain = Color(0xFFEDF0E2),
    textDim = Color(0xFF8A9377),
    danger = Color(0xFFD9534F),
    ledDim = Color(0xFF3C4530),
    metalLight = Color(0xFF2F3826),
    metalDark = Color(0xFF0C0F08),
    onAccent = Color(0xFF14170F)
)

/** 皮肤 D：沙漠黄沙（暖沙纸面，强光环境） */
val SandPalette = WcColors(
    isDark = false,
    bg = Color(0xFFF6EFE2),
    surface1 = Color(0xFFFFFBF2),
    surface2 = Color(0xFFEDE2CF),
    line = Color(0xFFDCCDB4),
    accent = Color(0xFFC08A3E),
    accentAlt = Color(0xFF4E7C59),
    info = Color(0xFF3F7F96),
    textMain = Color(0xFF3B3327),
    textDim = Color(0xFF8D8069),
    danger = Color(0xFFB0472F),
    ledDim = Color(0xFFD8CBB2),
    metalLight = Color(0xFFFFFDF7),
    metalDark = Color(0xFFE9DCC6),
    onAccent = Color(0xFFFFFFFF)
)

/** 皮肤 E：深海蓝调（冷蓝背光，夜间守听） */
val OceanPalette = WcColors(
    isDark = true,
    bg = Color(0xFF08131F),
    surface1 = Color(0xFF102132),
    surface2 = Color(0xFF050C14),
    line = Color(0xFF1D3348),
    accent = Color(0xFF4FC3F7),
    accentAlt = Color(0xFF57D9A3),
    info = Color(0xFF7FD4FF),
    textMain = Color(0xFFE3F1FF),
    textDim = Color(0xFF7E96AB),
    danger = Color(0xFFFF5D5D),
    ledDim = Color(0xFF2A4257),
    metalLight = Color(0xFF1D3348),
    metalDark = Color(0xFF050C14),
    onAccent = Color(0xFF04121E)
)

fun paletteOf(mode: ThemeMode): WcColors = when (mode) {
    ThemeMode.FIELD -> FieldPalette
    ThemeMode.CREAM -> CreamPalette
    ThemeMode.OLIVE -> OlivePalette
    ThemeMode.SAND -> SandPalette
    ThemeMode.OCEAN -> OceanPalette
}

/**
 * 当前品牌色（快照状态）。
 * 页面里大量使用下面这些顶层色值，切换主题时读取它们的 Composable 会自动重组。
 */
private var palette by mutableStateOf(FieldPalette)
private val cur: WcColors get() = palette

val Bg: Color get() = cur.bg
val Surface1: Color get() = cur.surface1
val Surface2: Color get() = cur.surface2
val Line: Color get() = cur.line
val Amber: Color get() = cur.accent
val Green: Color get() = cur.accentAlt
val Cyan: Color get() = cur.info
val TextMain: Color get() = cur.textMain
val TextDim: Color get() = cur.textDim
val Red: Color get() = cur.danger
val LedDim: Color get() = cur.ledDim
val MetalLight: Color get() = cur.metalLight
val MetalDark: Color get() = cur.metalDark

private fun schemeOf(p: WcColors) =
    if (p.isDark) {
        darkColorScheme(
            primary = p.accent,
            onPrimary = p.onAccent,
            secondary = p.accentAlt,
            onSecondary = p.onAccent,
            background = p.bg,
            onBackground = p.textMain,
            surface = p.surface1,
            onSurface = p.textMain,
            surfaceVariant = p.surface2,
            onSurfaceVariant = p.textDim,
            error = p.danger
        )
    } else {
        lightColorScheme(
            primary = p.accent,
            onPrimary = p.onAccent,
            secondary = p.accentAlt,
            onSecondary = p.onAccent,
            background = p.bg,
            onBackground = p.textMain,
            surface = p.surface1,
            onSurface = p.textMain,
            surfaceVariant = p.surface2,
            onSurfaceVariant = p.textDim,
            error = p.danger
        )
    }

@Composable
fun WcTheme(mode: ThemeMode, fontScale: Float = 1f, content: @Composable () -> Unit) {
    val p = paletteOf(mode)
    // 切换主题时同步全局品牌色，让仍在使用顶层色值的页面一起变色
    if (palette !== p) palette = p

    val colors = remember(p) { schemeOf(p) }
    val view = LocalView.current
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        window.statusBarColor = p.bg.toArgb()
        window.navigationBarColor = p.bg.toArgb()
        val controller = WindowCompat.getInsetsController(window, view)
        // 浅色主题下状态栏图标变深，避免白底白字
        controller.isAppearanceLightStatusBars = !p.isDark
        controller.isAppearanceLightNavigationBars = !p.isDark
    }
    // 大字号模式：只放大字体，不改变 dp 间距，避免整体布局比例失衡
    val base = LocalDensity.current
    val density = remember(base, fontScale) {
        if (fontScale == 1f) base else Density(base.density, base.fontScale * fontScale)
    }
    CompositionLocalProvider(LocalDensity provides density) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}

/** View 的 Context 可能被 ContextThemeWrapper 包过一层，向上找到 Activity */
private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
