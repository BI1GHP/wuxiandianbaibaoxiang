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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wc.cwtrainer.AppState
import com.wc.cwtrainer.BuildConfig
import com.wc.cwtrainer.core.InputMode
import com.wc.cwtrainer.core.KeyMode
import com.wc.cwtrainer.core.UpdateManager
import com.wc.cwtrainer.ui.Amber
import com.wc.cwtrainer.ui.LabeledSlider
import com.wc.cwtrainer.ui.Line
import com.wc.cwtrainer.ui.Pill
import com.wc.cwtrainer.ui.Red
import com.wc.cwtrainer.ui.ScreenHeader
import com.wc.cwtrainer.ui.SectionCard
import com.wc.cwtrainer.ui.SectionTitle
import com.wc.cwtrainer.ui.Surface2
import com.wc.cwtrainer.ui.TextDim
import com.wc.cwtrainer.ui.TextMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
                        "2. 外部需提供 [键控音源]：电台 CW 侧音、外置键控器/练习器的音频输出，或电脑播放的电报音。\n\n" +
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
                    text = "CW百宝箱  ${BuildConfig.VERSION_NAME}",
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
                Spacer(Modifier.height(10.dp))
                CheckForUpdateButton()
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CheckForUpdateButton() {
    val ctx = LocalContext.current
    var status by remember { mutableStateOf("idle") }
    var message by remember { mutableStateOf("🔄 点击检查更新") }
    var newVersion by remember { mutableStateOf<String?>(null) }
    var apkUrl by remember { mutableStateOf<String?>(null) }
    var updateSource by remember { mutableStateOf<String?>(null) }
    val versionCode = remember { BuildConfig.VERSION_NAME.filter { it.isDigit() }.toIntOrNull() ?: 0 }

    // 下载并安装
    LaunchedEffect(status) {
        if (status == "download" && apkUrl != null) {
            status = "installing"
            message = "⏳ 下载中…"
            try {
                val file = withContext(Dispatchers.IO) {
                    UpdateManager.downloadApk(ctx, apkUrl!!)
                }
                if (UpdateManager.canInstallUnknownApp(ctx)) {
                    UpdateManager.installApk(ctx, file)
                    message = "📦 正在安装…"
                } else {
                    UpdateManager.requestInstallPermission(ctx)
                    status = "need_perm"
                    message = "⚠ 请允许安装未知应用，然后重新点击"
                }
            } catch (e: Exception) {
                status = "error"
                message = "❌ 下载失败：${e.message}"
            }
        }
    }

    val onClickLabel = if (status == "new" && apkUrl != null) {
        "⬇ 下载并安装 $newVersion"
    } else when (status) {
        "checking" -> "⏳ 检查中…"
        "up_to_date" -> "✅ 当前已是最新版本"
        "error" -> "❌ 失败，点击重试"
        "need_perm" -> "⚠ 点击重新检查"
        "installing", "download" -> message
        else -> "🔄 点击检查更新"
    }

    val isClickable = status != "checking" && status != "download" && status != "installing"

    Box(
        modifier = Modifier.fillMaxWidth().height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when (status) {
                    "new" -> Amber.copy(alpha = 0.2f)
                    "error" -> Red.copy(alpha = 0.15f)
                    "up_to_date" -> Surface2
                    else -> Surface2
                }
            )
            .border(
                1.dp,
                when (status) {
                    "new" -> Amber
                    "error" -> Red
                    else -> Line
                },
                RoundedCornerShape(10.dp)
            )
            .clickable(enabled = isClickable) {
                when (status) {
                    "idle", "up_to_date", "error", "need_perm" -> {
                        status = "checking"
                        message = "⏳ 检查中…"
                        kotlinx.coroutines.MainScope().launch {
                            val result = UpdateManager.checkUpdate(versionCode)
                            if (result.hasUpdate && result.info != null) {
                                newVersion = result.info.versionName
                                apkUrl = result.info.apkUrl
                                updateSource = result.info.source
                                status = "new"
                                val sz = "%.1f".format(result.info.apkSizeBytes / 1024.0 / 1024.0)
                                message = "⬇ 发现 ${result.info.versionName}（${sz} MB · ${result.info.source}），点击下载"
                            } else if (result.error != null) {
                                status = "error"
                                message = "❌ ${result.error}"
                            } else {
                                status = "up_to_date"
                                message = "✅ 已是最新版本（源: ${result.info?.source ?: "-"}）"
                            }
                        }
                    }
                    "new" -> {
                        status = "download"
                    }
                }
            }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = if (status == "download" || status == "installing" || status == "need_perm") message else onClickLabel,
            color = when (status) {
                "new", "download", "installing" -> Amber
                "error" -> Red
                "up_to_date" -> TextDim
                else -> TextMain
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
