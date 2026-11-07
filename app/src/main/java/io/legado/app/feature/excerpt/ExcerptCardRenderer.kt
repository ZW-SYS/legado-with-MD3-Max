package io.legado.app.feature.excerpt

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils

/** 摘录卡片的样式 */
enum class ExcerptStyle(val label: String) {
    PAPER("宣纸"),
    NIGHT("夜读"),
    MINIMAL("素白"),
}

data class ExcerptContent(
    val text: String,
    val bookName: String,
    val author: String,
    val chapter: String,
    val appName: String,
)

/**
 * 摘录卡片渲染（阅读Max）：纯 Canvas 绘制，宽 1080，高度随文字自适应。
 */
object ExcerptCardRenderer {

    private const val WIDTH = 1080
    private const val PADDING = 110
    private const val MAX_CHARS = 600

    private data class Colors(
        val bgTop: Int,
        val bgBottom: Int,
        val text: Int,
        val sub: Int,
        val accent: Int,
    )

    private fun colors(style: ExcerptStyle) = when (style) {
        ExcerptStyle.PAPER -> Colors(
            0xFFF6F0E2.toInt(), 0xFFEADFC6.toInt(),
            0xFF3A2F25.toInt(), 0xFF7A6A55.toInt(), 0xFFB5523B.toInt()
        )
        ExcerptStyle.NIGHT -> Colors(
            0xFF141A26.toInt(), 0xFF26324A.toInt(),
            0xFFE9E4D6.toInt(), 0xFF9AA4B8.toInt(), 0xFFE0B860.toInt()
        )
        ExcerptStyle.MINIMAL -> Colors(
            0xFFFFFFFF.toInt(), 0xFFF7F7F7.toInt(),
            0xFF1F1F1F.toInt(), 0xFF8A8A8A.toInt(), 0xFF1F1F1F.toInt()
        )
    }

    fun render(content: ExcerptContent, style: ExcerptStyle): Bitmap {
        val c = colors(style)
        val textWidth = WIDTH - PADDING * 2
        val body = content.text.trim().let {
            if (it.length > MAX_CHARS) it.take(MAX_CHARS) + "……" else it
        }

        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = c.text
            textSize = if (body.length > 200) 44f else 52f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            letterSpacing = 0.04f
        }
        val bodyLayout = StaticLayout.Builder.obtain(body, 0, body.length, bodyPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.55f)
            .build()

        val bookPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = c.text
            textSize = 38f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = c.sub
            textSize = 32f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }
        val appPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = c.sub
            textSize = 26f
            letterSpacing = 0.2f
        }
        val quoteMarkPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (c.accent and 0x00FFFFFF) or 0x99000000.toInt()
            textSize = 200f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }

        val top = 230
        val bodyBottom = top + bodyLayout.height
        val metaTop = bodyBottom + 110
        val height = metaTop + 40 + 50 + 50 + 160

        val bmp = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bg = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(), c.bgTop, c.bgBottom, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), height.toFloat(), bg)

        when (style) {
            ExcerptStyle.PAPER -> {
                // 细边框 + 角落印章感的小方块
                val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE
                    strokeWidth = 3f
                    color = (c.sub and 0x00FFFFFF) or 0x55000000
                }
                canvas.drawRect(RectF(50f, 50f, WIDTH - 50f, height - 50f), frame)
                val seal = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c.accent }
                canvas.drawRoundRect(
                    RectF(WIDTH - 50f - 70f, height - 50f - 70f - 40f, WIDTH - 50f - 30f, height - 50f - 30f - 40f),
                    6f, 6f, seal
                )
            }
            ExcerptStyle.NIGHT -> {
                // 星点
                val star = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
                var seed = content.text.hashCode()
                repeat(40) {
                    seed = seed * 1103515245 + 12345
                    val x = ((seed ushr 8) and 0x7FFF) % WIDTH
                    seed = seed * 1103515245 + 12345
                    val y = ((seed ushr 8) and 0x7FFF) % 200
                    canvas.drawCircle(x.toFloat(), y.toFloat() + 20f, 2.5f, star)
                }
            }
            ExcerptStyle.MINIMAL -> {
                val bar = Paint().apply { color = c.accent }
                canvas.drawRect(0f, 0f, 16f, height.toFloat(), bar)
            }
        }

        canvas.drawText("“", PADDING - 20f, top - 10f, quoteMarkPaint)

        canvas.save()
        canvas.translate(PADDING.toFloat(), top.toFloat())
        bodyLayout.draw(canvas)
        canvas.restore()

        // 分隔线
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = c.accent
            strokeWidth = 4f
        }
        canvas.drawLine(PADDING.toFloat(), metaTop.toFloat() - 40f, PADDING + 90f, metaTop.toFloat() - 40f, linePaint)

        val bookLine = "《${content.bookName.ifBlank { "未知书名" }}》"
        canvas.drawText(
            TextUtils.ellipsize(bookLine, bookPaint, textWidth.toFloat(), TextUtils.TruncateAt.END).toString(),
            PADDING.toFloat(), metaTop + 40f, bookPaint
        )
        val sub = listOf(content.author, content.chapter).filter { it.isNotBlank() }.joinToString(" · ")
        if (sub.isNotEmpty()) {
            canvas.drawText(
                TextUtils.ellipsize(sub, subPaint, textWidth.toFloat(), TextUtils.TruncateAt.END).toString(),
                PADDING.toFloat(), metaTop + 100f, subPaint
            )
        }
        val appText = "摘自 ${content.appName}"
        val appW = appPaint.measureText(appText)
        canvas.drawText(appText, WIDTH - PADDING - appW, height - 90f, appPaint)
        return bmp
    }
}
