package com.wc.cwtrainer.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wc.cwtrainer.AppState
import com.wc.cwtrainer.core.ContestCheck
import com.wc.cwtrainer.core.ContestGen
import com.wc.cwtrainer.core.ContestMode
import com.wc.cwtrainer.core.VoiceSpec
import com.wc.cwtrainer.ui.Amber
import com.wc.cwtrainer.ui.Bg
import com.wc.cwtrainer.ui.Green
import com.wc.cwtrainer.ui.Line
import com.wc.cwtrainer.ui.Pill
import com.wc.cwtrainer.ui.Red
import com.wc.cwtrainer.ui.Surface1
import com.wc.cwtrainer.ui.Surface2
import com.wc.cwtrainer.ui.TextDim
import com.wc.cwtrainer.ui.TextMain
import kotlinx.coroutines.launch

@Composable
fun ContestScreen(app: AppState, onBack: () -> Unit) {
    val s = app.prefs.data
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(ContestMode.SSB) }
    var station by remember { mutableStateOf(ContestGen.station()) }
    var showSubtitle by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var ttsFailed by remember { mutableStateOf(false) }

    var callIn by remember { mutableStateOf(TextFieldValue("")) }
    var rstIn by remember { mutableStateOf(TextFieldValue("")) }
    var serialIn by remember { mutableStateOf(TextFieldValue("")) }
    var check by remember { mutableStateOf<ContestCheck?>(null) }

    // SSB 回退判断：SSB 模式 + 没 TTS → 用字母解释法 CW + 解说卡
    val ttsAvailable = app.speaker.available
    val ssbPhoneticCw = mode == ContestMode.SSB && !ttsAvailable
    val script = remember(station, mode) { ContestGen.script(station, mode) }

    // 实际播出内容：SSB 无 TTS 时改用字母解释法脚本
    val actualScript = if (ssbPhoneticCw) ContestGen.ssbPhoneticCwScript(station) else script
    val actualMode = if (ssbPhoneticCw) ContestMode.CW else mode

    fun newStation() {
        station = ContestGen.station()
        callIn = TextFieldValue(""); rstIn = TextFieldValue(""); serialIn = TextFieldValue("")
        check = null; ttsFailed = false; playing = false
    }

    fun play() {
        check = null; ttsFailed = false
        app.audio.stop(); app.speaker.stop()
        if (mode == ContestMode.SSB && ttsAvailable) {
            // 真正的 SSB 语音
            playing = true
            scope.launch {
                val ok = app.speaker.speak(script, station.localeTag, station.rate * s.ttsRate, station.pitch)
                playing = false; ttsFailed = !ok
            }
        } else {
            // CW 或 SSB 回退：用 station.cwFist 播放实际脚本
            if (ssbPhoneticCw) ttsFailed = true
            playing = true
            app.audio.play(
                voices = listOf(
                    VoiceSpec(
                        text = actualScript,
                        fist = if (ssbPhoneticCw) station.cwFist.copy(wpm = (station.cwFist.wpm * 0.85))
                        else station.cwFist,
                        toneHz = s.toneHz.toDouble()
                    )
                ),
                volume = s.volume, noiseLevel = s.noiseLevel
            ) { playing = false }
        }
    }

    fun checkIt() {
        val c = ContestGen.check(station, callIn.text, rstIn.text, serialIn.text)
        check = c
        app.stats.reportContest(c.score)
    }

    val dotPulse by rememberInfiniteTransition(label = "ct-pulse").animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Column(Modifier.fillMaxSize().background(Bg)) {
        // 顶部
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("‹ 返回", color = TextDim, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onBack() }
                    .padding(horizontal = 6.dp, vertical = 6.dp))
            Text("赛事听抄", color = TextMain, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp))
            Spacer(Modifier.weight(1f))
            ModeChipC(
                if (ssbPhoneticCw) "SSB·字母解释法"
                else if (mode == ContestMode.SSB) "SSB 语音"
                else "CW 电报",
                station.country
            )
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // 状态栏
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Surface1).border(1.dp, Line, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(10.dp).clip(CircleShape).background(
                        when {
                            playing -> Amber.copy(alpha = dotPulse)
                            check != null -> if (check!!.score == 3) Green else Amber
                            else -> Line
                        }
                    )
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = when {
                            playing -> when {
                                ssbPhoneticCw -> "字母解释法 CW 播放中…"
                                mode == ContestMode.SSB -> "SSB 语音播放中…"
                                else -> "CW 电报播放中…"
                            }
                            check != null -> "判分完成  ${check!!.score}/3"
                            ssbPhoneticCw -> "SSB 语音不可用，自动降级为字母解释法 CW"
                            else -> "准备播放，听后填写呼号 / 报告 / 序号"
                        },
                        color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = when {
                            playing -> "建议戴耳机，按比赛节奏不要暂停"
                            ssbPhoneticCw -> "用 CW 发送各国字母解释法单词，模拟真人朗读节奏"
                            ttsFailed -> "⚠ 语音播放失败，TTS 语言包可能缺失"
                            mode == ContestMode.SSB && !ttsAvailable ->
                                "⚠ 当前设备无 TTS 引擎，SSB 自动降级为字母解释法 CW"
                            mode == ContestMode.CW -> "${"%.1f".format(station.cwFist.wpm)} WPM · 手法模拟 · 噪声 ${"%.0f".format(s.noiseLevel * 100)}%"
                            else -> "${station.country}  ${station.accent}  ${station.habit}"
                        },
                        color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // 台站档案卡
            StationProfile(station, mode, s, playing, ssbPhoneticCw)

            Spacer(Modifier.height(10.dp))

            // 字幕调试区
            if (showSubtitle || check != null) {
                SubtitleBlock(actualScript, showSubtitle)
                Spacer(Modifier.height(10.dp))
            }

            // 抄收输入卡
            CopyFieldsBlock(
                callIn = callIn, rstIn = rstIn, serialIn = serialIn,
                onCall = { callIn = it },
                onRst = { rstIn = it },
                onSerial = { serialIn = it }
            )

            // 结果卡
            if (check != null) {
                Spacer(Modifier.height(10.dp))
                ContestResult(check!!, station)
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = "SSB 模式使用系统 TTS 按国家切换语言/音色；CW 模式带速度、划长、抖动差异，模拟不同操员手法（fist）。",
                color = TextDim, fontSize = 11.sp, lineHeight = 16.sp
            )
            Spacer(Modifier.height(24.dp))
        }

        // 底部操作条
        BottomContestBar(
            playing = playing,
            showSubtitle = showSubtitle,
            hasResult = check != null,
            onRegenerate = { newStation() },
            onPlay = { play() },
            onCheck = { checkIt() },
            onToggleSub = { showSubtitle = !showSubtitle },
            modifier = Modifier.padding(12.dp)
        )
    }
}

// ============ 子组件 ============

@Composable
private fun ModeChipC(a: String, b: String) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Surface2)
            .border(1.dp, Line, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = a, color = Amber, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Box(modifier = Modifier.padding(horizontal = 6.dp).size(4.dp).clip(CircleShape).background(Line))
        Text(text = b, color = TextDim, fontSize = 11.sp)
    }
}

@Composable
private fun StationProfile(
    station: com.wc.cwtrainer.core.ContestStation,
    mode: ContestMode,
    s: com.wc.cwtrainer.core.SettingsData,
    playing: Boolean,
    ssbPhoneticCw: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Surface1).border(1.dp, Line, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TagA(station.country, Amber)
            Spacer(Modifier.width(6.dp))
            Text("台站档案", color = TextDim, fontSize = 11.sp, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            TagA(
                when { ssbPhoneticCw -> "SSB·解释法"; mode == ContestMode.SSB -> "SSB"; else -> "CW" },
                if (ssbPhoneticCw) Amber else if (mode == ContestMode.SSB) CyanConst else Green
            )
        }

        Spacer(Modifier.height(12.dp))

        // 对方呼号大字
        Text(
            text = station.call,
            color = Green, fontSize = 28.sp, fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(station.report, color = Amber, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace)
            Text("  ·  ", color = TextDim, fontSize = 12.sp)
            Text(station.serial, color = Amber, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace)
        }

        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(station.accent, color = TextMain, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(8.dp))
            Text("·", color = Line, fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            Text(station.habit, color = TextDim, fontSize = 11.sp)
        }

        // SSB 字母解释法：显示"念法"让用户知道听到的是什么
        if (ssbPhoneticCw) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "字母解释法念法",
                    color = TextDim, fontSize = 10.sp, letterSpacing = 1.sp
                )
            }
            Text(
                text = ContestGen.spellCall(station.call, station.style),
                color = Amber, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Amber.copy(alpha = 0.10f))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        } else if (mode == ContestMode.CW) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${"%.1f".format(station.cwFist.wpm)} WPM",
                    color = Green, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Green.copy(alpha = 0.12f))
                        .border(1.dp, Green.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "划/点 ${"%.1f".format(station.cwFist.dashRatio)}",
                    color = TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun TagA(text: String, color: Color) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) { Text(text = text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium) }
}

private val CyanConst = Color(0xFF5DD3F3)

@Composable
private fun SubtitleBlock(script: String, isDebug: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Surface1).border(1.dp, Line, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Text(
            text = if (isDebug) "播报字幕 · 调试模式" else "播报字幕 · 已揭晓",
            color = TextDim, fontSize = 11.sp, letterSpacing = 2.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = script,
            color = TextMain, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun CopyFieldsBlock(
    callIn: TextFieldValue, rstIn: TextFieldValue, serialIn: TextFieldValue,
    onCall: (TextFieldValue) -> Unit,
    onRst: (TextFieldValue) -> Unit,
    onSerial: (TextFieldValue) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(Surface1, Surface1.copy(alpha = 0.6f))))
            .border(1.dp, Line, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text("抄收填写", color = TextDim, fontSize = 11.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(10.dp))

        // 呼号（主字段，大）
        FieldItem(label = "呼号 CALL", value = callIn, onValueChange = onCall)
        Spacer(Modifier.height(12.dp))

        // RST + 序号（并排）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.weight(1f)) {
                FieldItemCompact(label = "信号报告 RST", value = rstIn, onValueChange = onRst)
            }
            Column(Modifier.weight(1f)) {
                FieldItemCompact(label = "序号 SERIAL", value = serialIn, onValueChange = onSerial)
            }
        }
    }
}

@Composable
private fun FieldItem(label: String, value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit) {
    Column {
        Text(text = label, color = TextDim, fontSize = 10.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = value, onValueChange = onValueChange,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Monospace, fontSize = 26.sp,
                fontWeight = FontWeight.Bold, color = TextMain
            ),
            singleLine = true, cursorBrush = SolidColor(Amber),
            decorationBox = { inner ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .border(1.dp, Line, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (value.text.isEmpty()) Text(
                        "对方呼号…", color = TextDim.copy(alpha = 0.45f),
                        fontSize = 22.sp, fontFamily = FontFamily.Monospace
                    )
                    inner()
                }
            }
        )
    }
}

@Composable
private fun FieldItemCompact(label: String, value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit) {
    Column {
        Text(text = label, color = TextDim, fontSize = 10.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = value, onValueChange = onValueChange,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Monospace, fontSize = 18.sp,
                fontWeight = FontWeight.Bold, color = TextMain
            ),
            singleLine = true, cursorBrush = SolidColor(Amber),
            decorationBox = { inner ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .border(1.dp, Line, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (value.text.isEmpty()) Text(
                        "…", color = TextDim.copy(alpha = 0.45f),
                        fontSize = 16.sp, fontFamily = FontFamily.Monospace
                    )
                    inner()
                }
            }
        )
    }
}

@Composable
private fun ContestResult(check: ContestCheck, station: com.wc.cwtrainer.core.ContestStation) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Surface1).border(1.dp, Line, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column {
                Text("判分结果", color = TextDim, fontSize = 11.sp, letterSpacing = 2.sp)
                Text(
                    text = "${check.score}/3",
                    color = when {
                        check.score == 3 -> Green
                        check.score == 2 -> Amber
                        else -> Red
                    },
                    fontSize = 44.sp, fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (check.score == 3) "完整抄收" else if (check.score == 2) "基本准确" else "继续加油",
                    color = when { check.score == 3 -> Green; check.score == 2 -> Amber; else -> Red },
                    fontSize = 13.sp, fontWeight = FontWeight.Medium
                )
                Text(
                    text = when {
                        check.score == 3 -> "节奏稳定，继续保持"
                        check.score == 2 -> "先抓呼号骨架，再补细节"
                        else -> "建议先在 20 WPM 用 SSB 模式练熟"
                    },
                    color = TextDim, fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
        Spacer(Modifier.height(12.dp))

        CheckRow("呼号", check.callOk, station.call)
        Spacer(Modifier.height(8.dp))
        CheckRow("信号报告", check.reportOk, station.report)
        Spacer(Modifier.height(8.dp))
        CheckRow("序号", check.serialOk, station.serial)
    }
}

@Composable
private fun CheckRow(label: String, ok: Boolean, correct: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, color = TextDim, fontSize = 12.sp)
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier.size(22.dp).clip(CircleShape)
                .background(if (ok) Green.copy(alpha = 0.20f) else Red.copy(alpha = 0.20f))
                .border(1.dp, if (ok) Green else Red, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (ok) "✓" else "✗",
                color = if (ok) Green else Red,
                fontSize = 13.sp, fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = correct,
            color = if (ok) Green else Red, fontSize = 16.sp,
            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
        )
        if (!ok) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = "（正确值）",
                color = TextDim, fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun BottomContestBar(
    playing: Boolean, showSubtitle: Boolean, hasResult: Boolean,
    onRegenerate: () -> Unit, onPlay: () -> Unit, onCheck: () -> Unit,
    onToggleSub: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Surface1).border(1.dp, Line, RoundedCornerShape(16.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionBtnC("↻", "换台", TextDim, Modifier.padding(end = 2.dp)) { onRegenerate() }

        Box(
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                .background(if (playing) Red else Amber)
                .clickable { onPlay() }.padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (playing) "■" else "▶",
                    color = if (playing) Color.White else Color(0xFF1A1200),
                    fontSize = 18.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (playing) "停止播放" else "播放呼叫",
                    color = if (playing) Color.White else Color(0xFF1A1200),
                    fontSize = 16.sp, fontWeight = FontWeight.Bold
                )
            }
        }

        ActionBtnC(
            icon = if (showSubtitle) "字" else "字",
            label = if (showSubtitle) "字幕开" else "字幕关",
            color = if (showSubtitle) CyanConst else TextDim,
            modifier = Modifier.padding(start = 2.dp)
        ) { onToggleSub() }

        if (!hasResult) {
            ActionBtnC("✓", "判分", Green, Modifier.padding(start = 2.dp)) { onCheck() }
        } else {
            ActionBtnC("✓", "已判", Green.copy(alpha = 0.4f), Modifier.padding(start = 2.dp), enabled = false) { }
        }
    }
}

@Composable
private fun ActionBtnC(
    icon: String, label: String, color: Color,
    modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit
) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(12.dp))
            .background(Surface2).border(1.dp, Line, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, color = if (enabled) color else color.copy(alpha = 0.5f),
            fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = if (enabled) color else color.copy(alpha = 0.5f), fontSize = 10.sp,
            modifier = Modifier.padding(top = 1.dp))
    }
}
