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

// —— 短波面板专用色板（与 Theme 保持一致，局部 override）——
private val PanelBg = Color(0xFF12161C)
private val PanelRaised = Color(0xFF1E242D)
private val PanelRecessed = Color(0xFF0A0D11)
private val PanelLine = Color(0xFF2A3240)
private val LedAmber = Color(0xFFFFC857)
private val LedGreen = Color(0xFF3DDC97)
private val LedRed = Color(0xFFFF4444)
private val LedCyan = Color(0xFF00D4FF)
private val LedDim = Color(0xFF3A4250)

@Composable
fun ContestScreen(app: AppState, onBack: () -> Unit) {
    val s = app.prefs.data

    var station by remember { mutableStateOf(ContestGen.station()) }
    var playing by remember { mutableStateOf(false) }
    var showScript by remember { mutableStateOf(true) }   // 报文显示开关，默认开

    var callIn by remember { mutableStateOf(TextFieldValue("")) }
    var rstIn by remember { mutableStateOf(TextFieldValue("")) }
    var serialIn by remember { mutableStateOf(TextFieldValue("")) }
    var check by remember { mutableStateOf<ContestCheck?>(null) }

    // 强制 CW 模式
    val mode = ContestMode.CW
    val script = remember(station) { ContestGen.script(station, mode) }

    val pulse by rememberInfiniteTransition(label = "ct-led").animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val smeter by rememberInfiniteTransition(label = "smeter").animateFloat(
        initialValue = 0f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse),
        label = "sm"
    )

    fun newStation() {
        station = ContestGen.station()
        callIn = TextFieldValue(""); rstIn = TextFieldValue(""); serialIn = TextFieldValue("")
        check = null; playing = false
    }
    fun stop() { app.audio.stop(); playing = false }
    fun play() {
        check = null; stop(); playing = true
        app.audio.play(
            voices = listOf(VoiceSpec(text = script, fist = station.cwFist, toneHz = s.toneHz.toDouble())),
            volume = s.volume, noiseLevel = s.noiseLevel
        ) { playing = false }
    }
    fun checkIt() {
        check = ContestGen.check(station, callIn.text, rstIn.text, serialIn.text)
        check?.let { app.stats.reportContest(it.score) }
    }

    Column(Modifier.fillMaxSize().background(PanelBg)) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .background(Brush.verticalGradient(listOf(PanelBg, Color(0xFF0E1116))))
        ) {
            Spacer(Modifier.height(10.dp))
            MetalHeader(onBack = { onBack() }, title = "CW 赛事听抄", subtitle = "短波台面板风格")
            Spacer(Modifier.height(10.dp))

            FrequencyPanel(station, pulse)
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(Modifier.weight(0.38f)) {
                    FixedModeLight(pulse = pulse)
                    Spacer(Modifier.height(8.dp))
                    StatusLeds(playing = playing, pulse = pulse)
                }
                Column(Modifier.weight(1f)) {
                    StationInfoBlock(station)
                }
            }

            Spacer(Modifier.height(10.dp))
            SMeterPanel(level = if (playing) smeter else 0f)

            // —— 报文显示面板（默认打开）——
            Spacer(Modifier.height(10.dp))
            ScriptPanel(script = script, show = showScript, onToggle = { showScript = !showScript })

            Spacer(Modifier.height(10.dp))
            CopyPanel(
                callIn = callIn, rstIn = rstIn, serialIn = serialIn,
                onCall = { callIn = it }, onRst = { rstIn = it }, onSerial = { serialIn = it }
            )

            if (check != null) {
                Spacer(Modifier.height(10.dp))
                ResultPanel(check!!, station)
            }
            Spacer(Modifier.height(16.dp))
        }

        BottomPanelBar(
            playing = playing, hasResult = check != null,
            onNewStation = { newStation() }, onPlay = { play() },
            onStop = { stop() }, onCheck = { checkIt() },
            modifier = Modifier.padding(12.dp)
        )
    }
}

// ============ 子组件 ============

@Composable
private fun MetalHeader(onBack: () -> Unit, title: String, subtitle: String) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(PanelRaised, PanelBg, PanelRaised)))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹ 返回", color = LedDim.copy(alpha = 0.7f), fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onBack() }
                    .padding(horizontal = 6.dp, vertical = 4.dp))
            Spacer(Modifier.width(10.dp))
            Text(title, color = LedAmber, fontSize = 18.sp,
                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(8.dp))
            Text(subtitle, color = LedDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.weight(1f))
            Screw(); Spacer(Modifier.width(10.dp)); Screw()
        }
    }
}

@Composable
private fun Screw() {
    Box(
        Modifier.size(14.dp).clip(CircleShape)
            .background(Brush.radialGradient(colors = listOf(Color(0xFF4A5464), Color(0xFF1C222C))))
            .border(1.dp, Color(0xFF0A0D11), CircleShape),
        contentAlignment = Alignment.Center
    ) { Box(Modifier.size(9.dp).background(Color(0xFF0A0D11)).height(1.dp)) }
}

@Composable
private fun FrequencyPanel(station: com.wc.cwtrainer.core.ContestStation, pulse: Float) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(10.dp)).background(PanelRecessed)
            .border(1.dp, PanelLine, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("7.040.00",
                color = LedAmber.copy(alpha = 0.92f * pulse + 0.08f),
                fontSize = 36.sp, fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("CW", color = LedCyan.copy(alpha = 0.9f),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text("VFO  A", color = LedDim, fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 1.dp))
            }
            Spacer(Modifier.weight(1f))
            Text("●  -0 dBm", color = LedGreen.copy(alpha = 0.8f),
                fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(PanelLine))
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(station.country, color = LedAmber, fontSize = 12.sp,
                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text("  ·  ", color = LedDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text(station.call, color = LedGreen, fontSize = 14.sp,
                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.weight(1f))
            Text("CW · ${"%.1f".format(station.cwFist.wpm)} WPM",
                color = LedDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun FixedModeLight(pulse: Float) {
    Column(
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(PanelRecessed)
            .border(1.dp, PanelLine, RoundedCornerShape(10.dp)).padding(10.dp)
    ) {
        Text("模式 MODE", color = LedDim, fontSize = 9.sp,
            fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(14.dp).clip(CircleShape).background(LedGreen)
                        .border(1.dp, PanelLine, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.8f))) }
                Text("CW", color = LedGreen, fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(14.dp).clip(CircleShape).background(LedDim)
                        .border(1.dp, PanelLine, CircleShape)
                )
                Text("SSB", color = LedDim, fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun StatusLeds(playing: Boolean, pulse: Float) {
    Column(
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(PanelRecessed)
            .border(1.dp, PanelLine, RoundedCornerShape(10.dp)).padding(10.dp)
    ) {
        Text("状态 STATUS", color = LedDim, fontSize = 9.sp,
            fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusDot("RX", LedGreen, playing)
            StatusDot("TX", LedAmber, false)
            StatusDot("B", LedRed, playing)
        }
    }
}

@Composable
private fun StatusDot(label: String, color: Color, on: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(if (on) color else LedDim)
            .border(1.dp, PanelLine, CircleShape))
        Text(label, color = if (on) color else LedDim, fontSize = 8.sp,
            fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun StationInfoBlock(station: com.wc.cwtrainer.core.ContestStation) {
    Column(
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(PanelRaised)
            .border(1.dp, PanelLine, RoundedCornerShape(10.dp)).padding(12.dp)
    ) {
        Text("对方台站 STATION", color = LedDim, fontSize = 9.sp,
            fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(station.call, color = LedGreen, fontSize = 24.sp,
                fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(6.dp))
            Text("/", color = LedDim, fontSize = 18.sp, fontFamily = FontFamily.Monospace)
            Text(station.country, color = LedAmber, fontSize = 13.sp,
                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 2.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Text("RST", color = LedDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Text(station.report, color = LedAmber, fontSize = 14.sp,
                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 4.dp))
            Text("  ·  ", color = LedDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Text("SN", color = LedDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Text(station.serial, color = LedAmber, fontSize = 14.sp,
                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 4.dp))
        }

        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(PanelLine))
        Spacer(Modifier.height(6.dp))
        Text(station.accent, color = LedGreen.copy(alpha = 0.85f), fontSize = 11.sp,
            fontFamily = FontFamily.Monospace)
        Text(station.habit, color = LedDim, fontSize = 10.sp,
            fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 2.dp))

        Spacer(Modifier.height(6.dp))
        Row {
            Text("WPM ${"%.0f".format(station.cwFist.wpm)}", color = LedCyan,
                fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.clip(RoundedCornerShape(4.dp))
                    .background(LedCyan.copy(alpha = 0.12f))
                    .border(1.dp, LedCyan.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp))
            Spacer(Modifier.width(4.dp))
            Text("RATIO ${"%.1f".format(station.cwFist.dashRatio)}", color = LedDim,
                fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(PanelBg)
                    .border(1.dp, PanelLine, RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp))
        }
    }
}

@Composable
private fun SMeterPanel(level: Float) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(10.dp)).background(PanelRecessed)
            .border(1.dp, PanelLine, RoundedCornerShape(10.dp)).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("S METER", color = LedDim, fontSize = 9.sp,
                fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            Text("RF  SIGNAL", color = LedDim, fontSize = 9.sp,
                fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
        }
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth().height(20.dp)
                .clip(RoundedCornerShape(4.dp)).background(PanelBg)
                .border(1.dp, PanelLine, RoundedCornerShape(4.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until 12) {
                val segLevel = (i + 1) / 12f
                val isOn = level >= segLevel
                val color = when {
                    isOn && i < 9 -> LedGreen
                    isOn && i < 11 -> LedAmber
                    isOn -> LedRed
                    else -> LedDim
                }
                Box(Modifier.weight(1f).fillMaxSize().padding(1.dp)
                    .background(if (isOn) color.copy(alpha = 0.85f) else LedDim.copy(alpha = 0.3f)))
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            for (label in listOf("S1", "S3", "S5", "S7", "S9", "+20", "+40", "+60")) {
                Text(label, color = LedDim, fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ScriptPanel(script: String, show: Boolean, onToggle: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(10.dp)).background(PanelRecessed)
            .border(1.dp, PanelLine, RoundedCornerShape(10.dp)).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("报文内容 SCRIPT", color = LedDim, fontSize = 9.sp,
                fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            // 开/关切换
            Row(
                modifier = Modifier.clip(RoundedCornerShape(4.dp))
                    .background(if (show) LedAmber.copy(alpha = 0.15f) else PanelBg)
                    .border(1.dp, if (show) LedAmber.copy(alpha = 0.5f) else PanelLine, RoundedCornerShape(4.dp))
                    .clickable { onToggle() }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape)
                    .background(if (show) LedAmber else LedDim))
                Spacer(Modifier.width(4.dp))
                Text(if (show) "开" else "关",
                    color = if (show) LedAmber else LedDim,
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }
        Spacer(Modifier.height(8.dp))

        if (show) {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(PanelBg)
                    .border(1.dp, PanelLine, RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(script, color = LedAmber, fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp, lineHeight = 18.sp)
            }
        } else {
            Text("（已隐藏报文内容，专心听抄）",
                color = LedDim.copy(alpha = 0.6f),
                fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun CopyPanel(
    callIn: TextFieldValue, rstIn: TextFieldValue, serialIn: TextFieldValue,
    onCall: (TextFieldValue) -> Unit, onRst: (TextFieldValue) -> Unit,
    onSerial: (TextFieldValue) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(10.dp)).background(PanelRaised)
            .border(1.dp, PanelLine, RoundedCornerShape(10.dp)).padding(14.dp)
    ) {
        Text("抄收填写 COPY LOG", color = LedDim, fontSize = 9.sp,
            fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
        Spacer(Modifier.height(10.dp))

        Column {
            Text("呼号  CALLSIGN", color = LedAmber.copy(alpha = 0.7f),
                fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(4.dp))
            MetalField(value = callIn, onValueChange = onCall,
                placeholder = "对方呼号…", fontSize = 24)
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("RST", color = LedAmber.copy(alpha = 0.7f),
                    fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp))
                MetalField(value = rstIn, onValueChange = onRst,
                    placeholder = "599", fontSize = 18)
            }
            Column(Modifier.weight(1f)) {
                Text("序号  SERIAL", color = LedAmber.copy(alpha = 0.7f),
                    fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp))
                MetalField(value = serialIn, onValueChange = onSerial,
                    placeholder = "001", fontSize = 18)
            }
        }
    }
}

@Composable
private fun MetalField(
    value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit,
    placeholder: String, fontSize: Int
) {
    BasicTextField(
        value = value, onValueChange = onValueChange,
        textStyle = androidx.compose.ui.text.TextStyle(
            fontFamily = FontFamily.Monospace, fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold, color = LedGreen
        ),
        singleLine = true, cursorBrush = SolidColor(LedAmber),
        decorationBox = { inner ->
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(PanelRecessed)
                    .border(1.dp, PanelLine, RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (value.text.isEmpty()) Text(
                    placeholder, color = LedDim.copy(alpha = 0.45f),
                    fontSize = fontSize.sp, fontFamily = FontFamily.Monospace)
                inner()
            }
        }
    )
}

@Composable
private fun ResultPanel(check: ContestCheck, station: com.wc.cwtrainer.core.ContestStation) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(10.dp)).background(PanelRecessed)
            .border(1.dp, PanelLine, RoundedCornerShape(10.dp)).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("判分 RESULT", color = LedDim, fontSize = 9.sp,
                fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            Text(
                text = "${check.score} / 3",
                color = when { check.score == 3 -> LedGreen; check.score == 2 -> LedAmber; else -> LedRed },
                fontSize = 32.sp, fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(PanelLine))
        Spacer(Modifier.height(10.dp))
        CheckLine("呼号 CALL", check.callOk, station.call)
        Spacer(Modifier.height(6.dp))
        CheckLine("RST", check.reportOk, station.report)
        Spacer(Modifier.height(6.dp))
        CheckLine("序号 SERIAL", check.serialOk, station.serial)
    }
}

@Composable
private fun CheckLine(label: String, ok: Boolean, correct: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = LedDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier.size(18.dp).clip(CircleShape)
                .background(if (ok) LedGreen.copy(alpha = 0.2f) else LedRed.copy(alpha = 0.2f))
                .border(1.dp, if (ok) LedGreen else LedRed, CircleShape),
            contentAlignment = Alignment.Center
        ) { Text(if (ok) "✓" else "✗",
            color = if (ok) LedGreen else LedRed, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(8.dp))
        Text(correct, color = if (ok) LedGreen else LedRed,
            fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun BottomPanelBar(
    playing: Boolean, hasResult: Boolean,
    onNewStation: () -> Unit, onPlay: () -> Unit,
    onStop: () -> Unit, onCheck: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.horizontalGradient(listOf(PanelRaised, PanelBg, PanelRaised)))
            .border(1.dp, PanelLine, RoundedCornerShape(12.dp))
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SmallAction("↻", "换台", LedAmber) { onNewStation() }
        Spacer(Modifier.width(2.dp))

        if (playing) {
            Box(
                modifier = Modifier.weight(1f).height(48.dp)
                    .clip(RoundedCornerShape(8.dp)).background(LedRed)
                    .clickable { onStop() },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
                    Spacer(Modifier.width(8.dp))
                    Text("STOP 停止播放", color = Color.White,
                        fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace)
                }
            }
        } else {
            Box(
                modifier = Modifier.weight(1f).height(48.dp)
                    .clip(RoundedCornerShape(8.dp)).background(LedAmber)
                    .clickable { onPlay() },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("●", color = Color(0xFF1A1200), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text("CALL 播放呼叫", color = Color(0xFF1A1200),
                        fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace)
                }
            }
        }

        Spacer(Modifier.width(2.dp))
        if (!hasResult) {
            SmallAction("✓", "判分", if (playing) LedDim else LedGreen, enabled = !playing) { onCheck() }
        } else {
            SmallAction("✓", "已判", LedGreen.copy(alpha = 0.4f), enabled = false) {}
        }
    }
}

@Composable
private fun SmallAction(
    icon: String, label: String, color: Color,
    enabled: Boolean = true, onClick: () -> Unit
) {
    Column(
        modifier = Modifier.height(48.dp).clip(RoundedCornerShape(8.dp))
            .background(PanelRecessed)
            .border(1.dp, PanelLine, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(icon, color = if (enabled) color else color.copy(alpha = 0.5f),
            fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text(label, color = if (enabled) color else color.copy(alpha = 0.5f),
            fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 1.dp))
    }
}
