package io.legado.app.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.theme.LegadoTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 开屏名句覆盖层（阅读Max）。
 *
 * - 底色取当前主题配色做的全屏渐变，日间/夜间都跟随主题；
 * - 名句逐字淡入，衬线大字；出处在正文显示完后淡入；
 * - 全部显示后再停留 [holdMillis]，随后整体淡出并回调 [onDismiss]；
 * - 点任意位置立即淡出跳过。
 */
@Composable
fun EncouragementOverlay(
    onDismiss: () -> Unit,
    holdMillis: Long = 3000L,
    perCharMillis: Long = 70L,
    fadeMillis: Int = 450,
) {
    val quote = remember { SPLASH_QUOTES.random() }
    val colors = LegadoTheme.colorScheme
    val scope = rememberCoroutineScope()

    // 已“点亮”的字数（浮点，用于让最新一个字做淡入过渡）
    val reveal = remember { Animatable(0f) }
    var leaving by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }
    val overlayAlpha by animateFloatAsState(
        targetValue = if (leaving) 0f else 1f,
        animationSpec = tween(fadeMillis, easing = FastOutSlowInEasing),
        label = "splashOverlayAlpha",
        finishedListener = {
            if (leaving && !dismissed) {
                dismissed = true
                onDismiss()
            }
        }
    )
    val textLength = quote.text.length
    val attributionAlpha by animateFloatAsState(
        targetValue = if (reveal.value >= textLength) 1f else 0f,
        animationSpec = tween(600, easing = LinearEasing),
        label = "splashAttributionAlpha",
    )

    LaunchedEffect(Unit) {
        // 逐字显示：每个字 perCharMillis，最后一个字的淡入另算 300ms 收尾
        reveal.animateTo(
            targetValue = textLength.toFloat() + 1f,
            animationSpec = tween(
                durationMillis = (textLength * perCharMillis + 300L).toInt(),
                easing = LinearEasing,
            ),
        )
        delay(holdMillis)
        leaving = true
    }

    val skip: () -> Unit = {
        if (!leaving) {
            scope.launch { reveal.snapTo(textLength.toFloat() + 1f) }
            leaving = true
        }
    }

    val gradient = Brush.linearGradient(
        colors = listOf(
            colors.primaryContainer,
            colors.surface,
            colors.tertiaryContainer,
        ),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
    )
    val textColor = colors.onSurface
    val accent = colors.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha }
            .background(gradient)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = skip,
            )
            .semantics { contentDescription = "${quote.text}${quote.attribution}，轻触跳过" },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center,
        ) {
            // 装饰性的大引号
            Text(
                text = "“",
                color = accent.copy(alpha = 0.55f),
                fontFamily = FontFamily.Serif,
                fontSize = 72.sp,
                lineHeight = 72.sp,
                modifier = Modifier.padding(bottom = 0.dp),
            )
            Text(
                text = revealedText(quote.text, reveal.value, textColor),
                fontFamily = FontFamily.Serif,
                fontSize = 26.sp,
                lineHeight = 42.sp,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(28.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(attributionAlpha),
                horizontalAlignment = Alignment.End,
            ) {
                Box(
                    Modifier
                        .width(48.dp)
                        .height(2.dp)
                        .background(accent.copy(alpha = 0.6f), RoundedCornerShape(1.dp))
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = quote.attribution,
                    color = textColor.copy(alpha = 0.72f),
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 15.sp,
                    textAlign = TextAlign.End,
                )
            }
        }
        Text(
            text = "轻触任意处跳过",
            color = textColor.copy(alpha = 0.38f),
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
        )
    }
}

/** 已显示的字正常着色，正在出现的那个字按小数部分做透明度，未出现的字完全透明（保持排版不跳动）。 */
private fun revealedText(text: String, progress: Float, color: Color): AnnotatedString =
    buildAnnotatedString {
        text.forEachIndexed { index, c ->
            val a = (progress - index).coerceIn(0f, 1f)
            pushStyle(SpanStyle(color = color.copy(alpha = color.alpha * a)))
            append(c)
            pop()
        }
    }
