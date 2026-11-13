package com.wc.cwtrainer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wc.cwtrainer.AppState
import com.wc.cwtrainer.core.SkinInfo
import com.wc.cwtrainer.core.Skins
import com.wc.cwtrainer.core.trainingPoints
import com.wc.cwtrainer.ui.Amber
import com.wc.cwtrainer.ui.Bg
import com.wc.cwtrainer.ui.Green
import com.wc.cwtrainer.ui.Line
import com.wc.cwtrainer.ui.ScreenHeader
import com.wc.cwtrainer.ui.Surface1
import com.wc.cwtrainer.ui.Surface2
import com.wc.cwtrainer.ui.TextDim
import com.wc.cwtrainer.ui.TextMain
import com.wc.cwtrainer.ui.paletteOf

/**
 * 皮肤商店。
 *
 * 当前版本皮肤全部用「训练积分」兑换：积分由背码题数、听抄最佳正确率与赛事成绩
 * 本地换算（`trainingPoints`），不涉及账号与支付；后续接入内购时可在此替换解锁逻辑。
 */
@Composable
fun SkinStoreScreen(app: AppState, onBack: () -> Unit) {
    val s = app.prefs.data
    val earned = trainingPoints(app.stats)
    val balance = (earned - s.spentPoints).coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        ScreenHeader("皮肤商店", onBack, subtitle = "SKINS · WC-2025")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // 积分余额
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Surface1)
                    .border(1.dp, Line, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "训练积分",
                        color = TextDim,
                        fontSize = 10.sp,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "$balance",
                        color = Amber,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "累计 $earned",
                        color = TextDim,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "已使用 ${s.spentPoints}",
                        color = TextDim,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Text(
                text = "背码 1 题 = 1 分；听抄最佳正确率 ×2；赛事最佳分直接计入。完成训练即自动累积。",
                color = TextDim,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp)
            )

            Spacer(Modifier.height(14.dp))
            Skins.forEach { skin ->
                SkinRow(
                    skin = skin,
                    active = s.theme == skin.mode,
                    unlocked = skin.mode.name in s.unlockedSkins,
                    balance = balance,
                    onClick = {
                        val cost = skin.cost
                        if (skin.mode.name in s.unlockedSkins) {
                            app.prefs.update { it.copy(theme = skin.mode) }
                        } else if (balance >= cost) {
                            app.prefs.update {
                                it.copy(
                                    theme = skin.mode,
                                    unlockedSkins = it.unlockedSkins + skin.mode.name,
                                    spentPoints = it.spentPoints + cost
                                )
                            }
                        }
                    }
                )
                Spacer(Modifier.height(10.dp))
            }

            Text(
                text = "联名皮肤与内购礼包正在筹备；现有皮肤均可用训练积分兑换，不设付费墙。",
                color = TextDim,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 20.dp, start = 2.dp)
            )
        }
    }
}

@Composable
private fun SkinRow(
    skin: SkinInfo,
    active: Boolean,
    unlocked: Boolean,
    balance: Int,
    onClick: () -> Unit
) {
    val p = paletteOf(skin.mode)
    val canAfford = balance >= skin.cost
    val actionable = !active && (unlocked || canAfford)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface1)
            .border(
                1.dp,
                if (active) p.accent else Line,
                RoundedCornerShape(14.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 色卡：背景 / 面板 / 强调 / 文字
        Column(
            modifier = Modifier
                .size(width = 44.dp, height = 44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(p.bg)
                .border(1.dp, p.line, RoundedCornerShape(10.dp)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .width(24.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(p.accent)
            )
            Spacer(Modifier.height(3.dp))
            Box(
                Modifier
                    .width(24.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(p.surface1)
            )
            Spacer(Modifier.height(3.dp))
            Box(
                Modifier
                    .width(24.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(p.textMain)
            )
        }

        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = skin.name,
                    color = TextMain,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                if (active) {
                    Text(
                        text = "使用中",
                        color = Green,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
            Text(
                text = skin.tagline,
                color = TextDim,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        val label = when {
            active -> "使用中"
            unlocked -> "使用"
            else -> "${skin.cost} 积分"
        }
        Box(
            modifier = Modifier
                .heightIn(min = 36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (active) Surface2
                    else if (actionable) p.accent.copy(alpha = 0.16f)
                    else Surface2
                )
                .border(
                    1.dp,
                    if (actionable) p.accent else Line,
                    RoundedCornerShape(10.dp)
                )
                .clickable(enabled = actionable) { onClick() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = when {
                    active -> TextDim
                    actionable -> p.accent
                    else -> TextDim.copy(alpha = 0.7f)
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
