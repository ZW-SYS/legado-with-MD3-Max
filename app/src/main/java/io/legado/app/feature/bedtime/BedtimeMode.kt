package io.legado.app.feature.bedtime

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 睡前模式（阅读Max）。
 *
 * 开启后在设定时长内：屏幕亮度逐渐降低、叠加一层逐渐加深的暖琥珀色，时间到了温和地提醒休息。
 * 只在当前进程内有效，默认关闭，不会被自动开启。
 */
object BedtimeMode {

    data class State(
        val active: Boolean = false,
        val startAt: Long = 0L,
        val durationMillis: Long = 0L,
        val finished: Boolean = false,
    )

    val presetMinutes = listOf(15, 30, 45, 60)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun start(minutes: Int) {
        _state.value = State(
            active = true,
            startAt = System.currentTimeMillis(),
            durationMillis = minutes * 60_000L,
        )
    }

    fun stop() {
        _state.value = State()
    }

    internal fun markFinished() {
        val s = _state.value
        if (s.active) _state.value = s.copy(finished = true)
    }

    /** 剩余分钟（向上取整），未开启时返回 null */
    fun remainingMinutes(now: Long = System.currentTimeMillis()): Int? {
        val s = _state.value
        if (!s.active || s.finished) return null
        val left = s.startAt + s.durationMillis - now
        return ((left + 59_999) / 60_000).toInt().coerceAtLeast(0)
    }
}

/**
 * 盖在阅读界面最上层。不拦截触摸（提醒卡片除外），翻页、菜单照常可用。
 */
@Composable
fun BedtimeOverlay(modifier: Modifier = Modifier) {
    val state by BedtimeMode.state.collectAsState()
    if (!state.active) return
    val context = LocalContext.current
    val activity = context as? Activity
    var progress by remember(state.startAt) { mutableFloatStateOf(0f) }

    // 记住开启前的窗口亮度，结束/关闭时恢复
    DisposableEffect(state.startAt) {
        val window = activity?.window
        val original = window?.attributes?.screenBrightness
            ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        onDispose {
            window?.let {
                val lp = it.attributes
                lp.screenBrightness = original
                it.attributes = lp
            }
        }
    }

    LaunchedEffect(state.startAt) {
        val window = activity?.window
        val original = window?.attributes?.screenBrightness ?: -1f
        // 跟随系统亮度时取 0.5 作为起点
        val from = if (original < 0f) 0.5f else original
        val to = (from * 0.25f).coerceAtLeast(0.02f)
        while (true) {
            val elapsed = System.currentTimeMillis() - state.startAt
            progress = (elapsed.toFloat() / state.durationMillis).coerceIn(0f, 1f)
            window?.let {
                val lp = it.attributes
                lp.screenBrightness = from + (to - from) * progress
                it.attributes = lp
            }
            if (progress >= 1f) {
                BedtimeMode.markFinished()
                break
            }
            delay(15_000L)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 暖色：琥珀色逐渐加深；再叠一点黑色压暗
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFFFFA000).copy(alpha = 0.04f + 0.18f * progress))
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.30f * progress))
        )
        AnimatedVisibility(
            visible = state.finished,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .widthIn(max = 360.dp)
                    .background(Color(0xFF2B2118).copy(alpha = 0.94f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "夜深了",
                    color = Color(0xFFFFD9A0),
                    fontFamily = FontFamily.Serif,
                    fontSize = 22.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "今天就读到这里吧。书签已经替你记着这一页，明天接着读。",
                    color = Color(0xFFF3E6D3),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = { BedtimeMode.start(15) }) {
                        Text("再读 15 分钟", color = Color(0xFFFFD9A0))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        BedtimeMode.stop()
                        // 阅读页是主界面里的一个路由，不能 finish；退到后台即可
                        activity?.moveTaskToBack(true)
                    }) {
                        Text("去休息", color = Color(0xFFFFD9A0))
                    }
                }
                TextButton(onClick = { BedtimeMode.stop() }) {
                    Text("关闭睡前模式", color = Color(0xFFBFAF9A), fontSize = 13.sp)
                }
            }
        }
    }
}
