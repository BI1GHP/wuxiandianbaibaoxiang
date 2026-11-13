package com.wc.cwtrainer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wc.cwtrainer.AppState
import com.wc.cwtrainer.core.Skins
import com.wc.cwtrainer.core.ThemeMode
import com.wc.cwtrainer.ui.WcColors
import com.wc.cwtrainer.ui.paletteOf

/**
 * 全屏主题预览（独立页面）。
 *
 * 页面内容全部用**待应用主题**的色板绘制，因此可以在不修改全局主题的前提下，
 * 现场对比两套设计语言；点「应用此主题」才写入 AppPrefs，返回则保持原主题。
 *
 * 入口：设置 → 外观主题 → 全屏预览；由 MainActivity 的 AppScreen.THEME_PREVIEW 承载。
 */
@Composable
fun ThemePreviewScreen(app: AppState, onClose: () -> Unit) {
    // 预览中的临时选择，不落盘
    var mode by remember { mutableStateOf(app.prefs.data.theme) }
    val p = paletteOf(mode)

    BackHandler { onClose() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(p.bg)
            .systemBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "‹ 返回",
                color = p.textDim,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onClose() }
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "主题预览",
                color = p.textMain,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = mode.name,
                color = p.accent,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Skins.forEach { skin ->
                PreviewTab(skin.name, mode == skin.mode, p) { mode = skin.mode }
            }
        }

        Spacer(Modifier.height(14.dp))
        MockPreviewScreen(p, Modifier.weight(1f))
        Spacer(Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(p.accent)
                .clickable {
                    app.prefs.update { it.copy(theme = mode) }
                    onClose()
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "应用此主题",
                color = p.onAccent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "应用后全 App 立即生效，可随时在设置中切换",
            color = p.textDim,
            fontSize = 10.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(12.dp))
    }
}

/** 预览里的示例界面：首页卡片 + 底部操作条，覆盖主要组件形态 */
@Composable
private fun MockPreviewScreen(p: WcColors, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(p.surface2)
            .border(1.dp, p.line, RoundedCornerShape(18.dp))
            .padding(12.dp)
    ) {
        // 顶栏：铭牌 + 码速
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(p.accent)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "CW TRAINER",
                color = p.accent,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "20 WPM",
                color = p.textDim,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(10.dp))

        // 继续上次训练
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(p.surface1)
                .border(1.dp, p.accent.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                .padding(horizontal = 11.dp, vertical = 9.dp)
        ) {
            Text(
                text = "继续上次训练",
                color = p.accent,
                fontSize = 9.sp,
                letterSpacing = 2.sp
            )
            Text(
                text = "听抄练习",
                color = p.textMain,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MockModule("听抄", "逐字即时反馈", p, Modifier.weight(1f))
            MockModule("赛事", "模拟真实通联", p, Modifier.weight(1f))
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(10.dp))

        // 底部操作条：主按钮独占一行 + 次级操作三等分
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(p.surface1)
                .border(1.dp, p.line, RoundedCornerShape(12.dp))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MockBarButton("换一组", p.surface2, p.textDim, Modifier.weight(0.9f))
            MockBarButton("播放报文", p.accent, p.onAccent, Modifier.weight(1.3f))
            MockBarButton("判分", p.surface2, p.accentAlt, Modifier.weight(0.9f))
        }
    }
}

@Composable
private fun MockModule(
    title: String,
    desc: String,
    p: WcColors,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(p.surface1)
            .border(1.dp, p.line, RoundedCornerShape(11.dp))
            .padding(9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(p.accent)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = title,
                color = p.textMain,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = desc,
            color = p.textDim,
            fontSize = 9.sp,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

@Composable
private fun MockBarButton(
    text: String,
    bg: Color,
    fg: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .heightIn(min = 34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PreviewTab(
    label: String,
    selected: Boolean,
    p: WcColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) p.accent.copy(alpha = 0.16f) else p.surface1)
            .border(
                1.dp,
                if (selected) p.accent else p.line,
                RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) p.accent else p.textDim,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
