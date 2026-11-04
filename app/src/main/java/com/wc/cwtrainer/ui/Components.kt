package com.wc.cwtrainer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "‹ 返回",
            color = TextDim,
            fontSize = 14.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onBack() }
                .padding(horizontal = 6.dp, vertical = 4.dp)
        )
        Text(
            text = title,
            color = TextMain,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1)
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .padding(14.dp),
        content = content
    )
}

@Composable
fun SectionTitle(text: String, trailing: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = TextDim,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        if (trailing != null) {
            Text(
                text = trailing,
                color = Amber,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun Pill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Amber.copy(alpha = 0.16f) else Surface2)
            .border(1.dp, if (selected) Amber else Line, RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        Text(
            text = text,
            color = if (selected) Amber else TextDim,
            fontSize = 13.sp
        )
    }
}

@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    steps: Int = 0,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, color = TextDim, fontSize = 13.sp)
            Text(
                text = valueText,
                color = Amber,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 紧凑码速调节条：- 按钮 + 数值 + + 按钮，5~40 WPM */
@Composable
fun WpmAdjuster(
    wpm: Int,
    onWpm: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "码速"
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Surface2)
            .border(1.dp, Line, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Surface1)
                .border(1.dp, Line, RoundedCornerShape(6.dp))
                .clickable { if (wpm > 5) onWpm(wpm - 1) },
            contentAlignment = Alignment.Center
        ) { Text("−", color = Amber, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        Text(
            text = "$wpm",
            color = Amber, fontSize = 14.sp,
            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Surface1)
                .border(1.dp, Line, RoundedCornerShape(6.dp))
                .clickable { if (wpm < 40) onWpm(wpm + 1) },
            contentAlignment = Alignment.Center
        ) { Text("+", color = Amber, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        Text("WPM", color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 4.dp))
    }
}

/** 点/划按键，支持多指独立按下抬起（每个 Keypad 只跟踪按下本按钮的 pointerId） */
@Composable
fun Keypad(
    label: String,
    sub: String,
    accent: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keypadKey: Int = 0,
    onDown: () -> Unit,
    onUp: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (pressed) accent.copy(alpha = 0.32f) else accent.copy(alpha = 0.10f))
            .border(
                1.dp,
                accent.copy(alpha = if (pressed) 0.95f else 0.42f),
                RoundedCornerShape(18.dp)
            )
            // 每个 Keypad 用独立 key → Compose 创建独立 gesture detector，互不干扰
            .pointerInput(enabled, keypadKey) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    // 按下：requireUnconsumed=false 让多个 Keypad 都能收到同一次多指点按
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val myPointerId = down.id  // 记住按下本按钮的那个手指 ID
                    pressed = true
                    onDown()
                    // 等待 **我们的那个手指** 释放，忽略其他手指的事件
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Main)
                        val myChange = ev.changes.firstOrNull { it.id == myPointerId }
                        if (myChange != null && !myChange.pressed) break
                    }
                    pressed = false
                    onUp()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                color = if (enabled) accent else TextDim,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            if (sub.isNotEmpty()) {
                Text(
                    text = sub,
                    color = TextDim,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, valueColor: Color = TextMain) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextDim, fontSize = 12.sp)
        Text(text = value, color = valueColor, fontSize = 12.sp)
    }
}

@Composable
fun DotDashText(code: String, color: Color = Amber, size: Int = 13) {
    Text(
        text = code,
        color = color,
        fontSize = size.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium
    )
}

@Composable
fun BigButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) Amber else Surface2)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) Color(0xFF1A1200) else TextDim,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Surface2)
            .border(1.dp, Line, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) TextMain else TextDim,
            fontSize = 13.sp
        )
    }
}

@Composable
fun Tag(text: String, color: Color = Amber) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text = text, color = color, fontSize = 11.sp)
    }
}

@Composable
fun MonoLabel(text: String, size: Int = 12, color: Color = TextDim) {
    Text(
        text = text,
        color = color,
        fontSize = size.sp,
        fontFamily = FontFamily.Monospace
    )
}

@Composable
fun DividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(Line.copy(alpha = 0.5f))
            .padding(top = 1.dp)
    )
}
