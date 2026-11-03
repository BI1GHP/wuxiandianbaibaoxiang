package com.wc.cwtrainer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wc.cwtrainer.AppState
import com.wc.cwtrainer.core.InputMode
import com.wc.cwtrainer.core.KeyMode
import com.wc.cwtrainer.ui.Amber
import com.wc.cwtrainer.ui.LabeledSlider
import com.wc.cwtrainer.ui.Pill
import com.wc.cwtrainer.ui.ScreenHeader
import com.wc.cwtrainer.ui.SectionCard
import com.wc.cwtrainer.ui.SectionTitle
import com.wc.cwtrainer.ui.TextDim
import com.wc.cwtrainer.ui.TextMain
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(app: AppState, onBack: () -> Unit) {
    val s = app.prefs.data
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("设置", onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            SectionCard {
                SectionTitle("电码与音频")
                LabeledSlider(
                    label = "发送速度",
                    value = s.wpm.toFloat(),
                    range = 5f..40f,
                    valueText = "${s.wpm} WPM",
                    steps = 34
                ) { v ->
                    val wpm = v.toInt()
                    app.prefs.update {
                        it.copy(wpm = wpm, farnsworth = if (it.farnsworth > wpm) 0 else it.farnsworth)
                    }
                }
                LabeledSlider(
                    label = "Farnsworth 有效速度",
                    value = s.farnsworth.toFloat(),
                    range = 0f..s.wpm.toFloat(),
                    valueText = if (s.farnsworth == 0) "关闭" else "${s.farnsworth} WPM",
                    steps = 0
                ) { v ->
                    app.prefs.update { it.copy(farnsworth = v.roundToInt()) }
                }
                LabeledSlider(
                    label = "侧音频率",
                    value = s.toneHz.toFloat(),
                    range = 400f..1000f,
                    valueText = "${s.toneHz} Hz",
                    steps = 29
                ) { v ->
                    app.prefs.update { it.copy(toneHz = v.roundToInt()) }
                }
                LabeledSlider(
                    label = "音量",
                    value = s.volume,
                    range = 0f..1f,
                    valueText = "${(s.volume * 100).roundToInt()}%",
                    steps = 9
                ) { v ->
                    app.prefs.update { it.copy(volume = v) }
                }
            }

            Spacer(Modifier.height(10.dp))

            SectionCard {
                SectionTitle("发码")
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("手键模式", s.keyMode == KeyMode.STRAIGHT) {
                        app.prefs.update { it.copy(keyMode = KeyMode.STRAIGHT) }
                    }
                    Pill("自动键模式", s.keyMode == KeyMode.PADDLE) {
                        app.prefs.update { it.copy(keyMode = KeyMode.PADDLE) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "IAMBIC 记忆（B 模式）",
                            color = TextMain,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "自动键双桨同时按下时交替发码，并记忆反向桨",
                            color = TextDim,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = s.iambicB,
                        onCheckedChange = { v -> app.prefs.update { it.copy(iambicB = v) } }
                    )
                }
                LabeledSlider(
                    label = "外部输入灵敏度",
                    value = s.micSensitivity,
                    range = 0f..1f,
                    valueText = "${(s.micSensitivity * 100).roundToInt()}%",
                    steps = 9
                ) { v ->
                    app.prefs.update { it.copy(micSensitivity = v) }
                }
                LabeledSlider(
                    label = "外部检测音调频率",
                    value = s.extToneHz.toFloat(),
                    range = 300f..900f,
                    valueText = "${s.extToneHz} Hz",
                    steps = 59
                ) { v ->
                    app.prefs.update { it.copy(extToneHz = v.roundToInt()) }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("默认屏幕按键", s.inputSource == InputMode.SCREEN) {
                        app.prefs.update { it.copy(inputSource = InputMode.SCREEN) }
                    }
                    Pill("默认音频接口", s.inputSource == InputMode.AUDIO) {
                        app.prefs.update { it.copy(inputSource = InputMode.AUDIO) }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            SectionCard {
                SectionTitle("赛事模拟")
                LabeledSlider(
                    label = "背景噪声",
                    value = s.noiseLevel,
                    range = 0f..0.3f,
                    valueText = "${(s.noiseLevel * 100).roundToInt()}%",
                    steps = 29
                ) { v ->
                    app.prefs.update { it.copy(noiseLevel = v) }
                }
                LabeledSlider(
                    label = "TTS 语速系数",
                    value = s.ttsRate,
                    range = 0.7f..1.3f,
                    valueText = "${"%.2f".format(s.ttsRate)}x",
                    steps = 5
                ) { v ->
                    app.prefs.update { it.copy(ttsRate = v) }
                }
                Text(
                    text = "SSB 口音依赖系统 TTS 语言包。若某国语音包缺失，会自动回退到英语。",
                    color = TextDim,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }

            Spacer(Modifier.height(10.dp))

            SectionCard {
                SectionTitle("外部按键接入说明")
                Text(
                    text = "1. 手机 3.5mm 耳机孔（四段 TRRS）或 USB 声卡作为音频输入。\n\n" +
                        "2. 外部需提供“键控音源”：电台 CW 侧音、外置键控器/练习器的音频输出，或电脑播放的电报音。\n\n" +
                        "3. 纯机械手键/双桨是干接点开关，手机音频口无法直接读取通断，需要外接一台有源键控器（带侧音输出），再接入手机。\n\n" +
                        "4. APP 检测设定频率上的音调能量，判断 key down / key up，再自适应解码为点、划、字符。\n\n" +
                        "5. 自动键模式：外部自动键需自带键控器，APP 只负责识别与评分；屏幕双桨则由 APP 内置 IAMBIC 键控器。\n\n" +
                        "6. 建议使用有线耳机，避免外放声音回授到麦克风造成误判。",
                    color = TextDim,
                    fontSize = 11.sp,
                    lineHeight = 18.sp
                )
            }

            Spacer(Modifier.height(10.dp))

            SectionCard {
                SectionTitle("关于")
                Text(
                    text = "无线电百宝箱  V1.0",
                    color = TextMain,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "听抄 · 背码 · 发码 · 赛事听抄\n制作者 BI1GHP",
                    color = Amber,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
