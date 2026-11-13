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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wc.cwtrainer.AppState
import com.wc.cwtrainer.ui.Amber
import com.wc.cwtrainer.ui.Bg
import com.wc.cwtrainer.ui.Line
import com.wc.cwtrainer.ui.Pill
import com.wc.cwtrainer.ui.ScreenHeader
import com.wc.cwtrainer.ui.Surface1
import com.wc.cwtrainer.ui.TextDim
import com.wc.cwtrainer.ui.TextMain
import com.wc.cwtrainer.ui.shareCardImage

// —— 呼号牌配色：纸质卡片，与 QSL 成绩单同一套纸感 ——
private val CardPaper = Color(0xFFFAF4E6)
private val CardInk = Color(0xFF2B2620)
private val CardLine = Color(0xFFD8CBB4)
private val CardAmber = Color(0xFFB07C1F)
private val CardDim = Color(0xFF8A7E6B)

/**
 * 呼号牌个人页：本地填写呼号 / 操作证等级 / 格言，
 * 卡片汇总训练数据，可裁切为图片分享。全部数据只存本机。
 */
@Composable
fun CallsignScreen(app: AppState, onBack: () -> Unit) {
    val s = app.prefs.data
    val context = LocalContext.current
    val view = LocalView.current
    var badgeBounds by remember { mutableStateOf<Rect?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        ScreenHeader("呼号牌", onBack, subtitle = "CALLSIGN · LOCAL")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            ProfileField(
                label = "呼号 CALLSIGN",
                value = s.callsign,
                placeholder = "如 BI1GHP"
            ) { raw ->
                val cleaned = raw.uppercase().filter { it.isLetterOrDigit() }.take(8)
                app.prefs.update { it.copy(callsign = cleaned) }
            }

            Spacer(Modifier.height(12.dp))
            Text("操作证等级 LICENSE", color = TextDim, fontSize = 10.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("A", "B", "C", "NONE").forEach { level ->
                    Pill(
                        text = if (level == "NONE") "未考证" else "$level 类",
                        selected = s.opClass == level
                    ) {
                        app.prefs.update { it.copy(opClass = level) }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            ProfileField(
                label = "格言 MOTTO",
                value = s.motto,
                placeholder = "如 73 / 守听中"
            ) { raw ->
                app.prefs.update { it.copy(motto = raw.take(14)) }
            }

            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { badgeBounds = it.boundsInWindow() }
            ) {
                CallsignBadge(
                    callsign = s.callsign.ifBlank { "BI1GHP" },
                    opClass = s.opClass,
                    motto = s.motto,
                    learnTotal = app.stats.learnTotal,
                    copyBest = app.stats.copyBest,
                    contestBest = app.stats.contestBest
                )
            }

            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Amber)
                    .clickable {
                        shareCardImage(context, view, badgeBounds, "分享呼号牌")
                            .onFailure { message = "分享失败：${it.message}" }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "分享呼号牌",
                    color = Color(0xFF1A1200),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            message?.let {
                Text(
                    text = it,
                    color = TextDim,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Text(
                text = "呼号与等级仅保存在本机，不上传服务器；分享图为本地生成的 PNG。",
                color = TextDim,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 20.dp)
            )
        }
    }
}

@Composable
private fun ProfileField(
    label: String,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit
) {
    Column {
        Text(label, color = TextDim, fontSize = 10.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Surface1)
                .border(1.dp, Line, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 11.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = TextMain,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                cursorBrush = SolidColor(Amber),
                modifier = Modifier.fillMaxWidth()
            )
            if (value.isEmpty()) {
                Text(placeholder, color = TextDim.copy(alpha = 0.6f), fontSize = 14.sp)
            }
        }
    }
}

/** 纸质呼号牌：呼号 + 摩斯 + 等级徽章 + 训练数据 */
@Composable
private fun CallsignBadge(
    callsign: String,
    opClass: String,
    motto: String,
    learnTotal: Int,
    copyBest: Float,
    contestBest: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CardPaper)
            .border(1.6.dp, CardInk, RoundedCornerShape(8.dp))
            .padding(5.dp)
            .border(0.8.dp, CardLine, RoundedCornerShape(4.dp))
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "呼号牌",
                    color = CardAmber,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 3.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "LOCAL · NO ACCOUNT",
                    color = CardDim,
                    fontSize = 7.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.2.sp
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = callsign,
                color = CardInk,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 3.sp
            )
            Text(
                text = morseOf(callsign),
                color = CardAmber,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(CardInk.copy(alpha = 0.5f)))
            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .border(1.4.dp, CardInk, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (opClass == "NONE") "未考证" else "OP · $opClass",
                        color = CardInk,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = motto.ifBlank { "73" },
                    color = CardInk,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BadgeStat("背码", "$learnTotal")
                BadgeStat("听抄最佳", "${(copyBest * 100).toInt()}%")
                BadgeStat("赛事最佳", "$contestBest")
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "·−·−·  ·−··  ···−  ·",
                    color = CardAmber.copy(alpha = 0.7f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "73 DE ${callsign.ifBlank { "BI1GHP" }}",
                    color = CardDim,
                    fontSize = 7.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun BadgeStat(label: String, value: String) {
    Column {
        Text(
            text = value,
            color = CardInk,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = label,
            color = CardDim,
            fontSize = 8.sp,
            modifier = Modifier.padding(top = 1.dp)
        )
    }
}

/** 呼号 → 摩斯（字母 / 数字），未知字符忽略 */
private val MorseCodes = mapOf(
    'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".",
    'F' to "..-.", 'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---",
    'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---",
    'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-",
    'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--",
    'Z' to "--..",
    '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--",
    '4' to "....-", '5' to ".....", '6' to "-....", '7' to "--...",
    '8' to "---..", '9' to "----."
)

private fun morseOf(callsign: String): String =
    callsign.uppercase().mapNotNull { MorseCodes[it] }.joinToString("  ")


