package io.legado.app.feature.autocover

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.feature.max.MaxPrefs
import io.legado.app.help.book.isImage
import io.legado.app.help.book.isLocal
import io.legado.app.model.localBook.LocalBook
import io.legado.app.utils.FileUtils
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * 本地书自动封面（阅读Max）。
 *
 * 纯本地 Canvas 绘制，不联网、不用 AI：按书名哈希确定性地挑一套配色，渐变底 + 淡淡的纹样，
 * 衬线大字书名自动换行，作者小字放在底部。生成的 JPG 写到这本书的封面路径
 * （[LocalBook.getCoverPath]），书架、详情页和导出都直接用这个文件。
 *
 * 优先级：用户自定义封面（customCoverUrl）> 书里自带的封面 > 自动封面。
 */
object AutoCover {

    private const val WIDTH = 600
    private const val HEIGHT = 840

    /** 每套配色：渐变起点、终点、文字色、纹样色 */
    private data class Palette(val start: Int, val end: Int, val text: Int, val pattern: Int)

    private val palettes = listOf(
        Palette(0xFF1F3A5F.toInt(), 0xFF4A6FA5.toInt(), 0xFFF5F1E6.toInt(), 0x22FFFFFF), // 夜蓝
        Palette(0xFF6B2D2D.toInt(), 0xFFB5523B.toInt(), 0xFFFBEFE3.toInt(), 0x1FFFFFFF), // 朱砂
        Palette(0xFF2F4F3E.toInt(), 0xFF6E8B5E.toInt(), 0xFFF2F0E1.toInt(), 0x1FFFFFFF), // 松绿
        Palette(0xFF3E2C4F.toInt(), 0xFF8A5A8C.toInt(), 0xFFF6EDF5.toInt(), 0x1FFFFFFF), // 暮紫
        Palette(0xFFE9DFC8.toInt(), 0xFFCDB993.toInt(), 0xFF3B2F22.toInt(), 0x24000000), // 宣纸
        Palette(0xFF243B3A.toInt(), 0xFF3F7F7A.toInt(), 0xFFEAF4F1.toInt(), 0x1FFFFFFF), // 青瓷
        Palette(0xFF4A3B2A.toInt(), 0xFF9C7A4C.toInt(), 0xFFF7EEDC.toInt(), 0x1FFFFFFF), // 檀木
        Palette(0xFFDCE3EA.toInt(), 0xFFA9B8C9.toInt(), 0xFF1E2A38.toInt(), 0x22000000), // 霜白
        Palette(0xFF5A1F3D.toInt(), 0xFFA34B6E.toInt(), 0xFFFCEFF4.toInt(), 0x1FFFFFFF), // 胭脂
        Palette(0xFF1C1C1E.toInt(), 0xFF4A4A4F.toInt(), 0xFFE9D8A6.toInt(), 0x1AFFFFFF), // 墨金
    )

    /** 稳定哈希：不同进程、不同设备上同一个书名得到同一结果 */
    private fun stableHash(s: String): Int {
        var h = 0x811C9DC5.toInt()
        for (c in s) {
            h = h xor c.code
            h *= 0x01000193
        }
        return h
    }

    /** 生成封面位图（不落盘），供预览/测试 */
    fun render(title: String, author: String): Bitmap {
        val name = title.trim().ifEmpty { "未命名" }
        val hash = stableHash(name)
        val p = palettes[abs(hash % palettes.size)]
        val bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // 渐变底：方向也由哈希决定（左上→右下 / 上→下）
        val diagonal = (hash ushr 8) and 1 == 0
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f,
                if (diagonal) WIDTH.toFloat() else 0f, HEIGHT.toFloat(),
                p.start, p.end, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), bgPaint)

        drawPattern(canvas, (hash ushr 12) and 3, p.pattern)

        // 内框
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = (p.text and 0x00FFFFFF) or 0x66000000
        }
        canvas.drawRect(RectF(36f, 36f, WIDTH - 36f, HEIGHT - 36f), framePaint)

        // 书名：衬线粗体，自动换行，字号按长度收放
        val titleSize = when {
            name.length <= 4 -> 92f
            name.length <= 8 -> 76f
            name.length <= 14 -> 62f
            name.length <= 24 -> 50f
            else -> 40f
        }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.text
            textSize = titleSize
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            letterSpacing = 0.06f
        }
        val textWidth = WIDTH - 2 * 80
        val titleLayout = StaticLayout.Builder
            .obtain(name, 0, name.length, titlePaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.18f)
            .setMaxLines(6)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(80f, 150f)
        titleLayout.draw(canvas)
        canvas.restore()

        // 书名下方短线
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.text
            strokeWidth = 4f
        }
        val lineY = 150f + titleLayout.height + 36f
        canvas.drawLine(80f, lineY, 150f, lineY, linePaint)

        // 作者：底部小字
        val authorText = author.trim()
        if (authorText.isNotEmpty()) {
            val authorPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = (p.text and 0x00FFFFFF) or 0xD9000000.toInt()
                textSize = 32f
                typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
                letterSpacing = 0.12f
            }
            val shown = android.text.TextUtils.ellipsize(
                authorText, authorPaint, textWidth.toFloat(), android.text.TextUtils.TruncateAt.END
            ).toString()
            canvas.drawText(shown, 80f, HEIGHT - 96f, authorPaint)
        }
        return bmp
    }

    /** 0=斜线 1=圆点 2=同心圆 3=横纹 */
    private fun drawPattern(canvas: Canvas, kind: Int, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        when (kind) {
            0 -> {
                var x = -HEIGHT.toFloat()
                while (x < WIDTH) {
                    canvas.drawLine(x, HEIGHT.toFloat(), x + HEIGHT, 0f, paint)
                    x += 28f
                }
            }
            1 -> {
                paint.style = Paint.Style.FILL
                var y = 20f
                var row = 0
                while (y < HEIGHT) {
                    var x = if (row % 2 == 0) 20f else 40f
                    while (x < WIDTH) {
                        canvas.drawCircle(x, y, 3f, paint)
                        x += 40f
                    }
                    y += 34f
                    row++
                }
            }
            2 -> {
                var r = 40f
                while (r < 900f) {
                    canvas.drawCircle(WIDTH.toFloat(), HEIGHT.toFloat(), r, paint)
                    r += 36f
                }
            }
            else -> {
                var y = 0f
                while (y < HEIGHT) {
                    canvas.drawLine(0f, y, WIDTH.toFloat(), y, paint)
                    y += 22f
                }
            }
        }
    }

    /** 这本书现在是否需要自动封面 */
    fun needsCover(book: Book): Boolean {
        if (!book.isLocal || book.isImage) return false
        if (!book.customCoverUrl.isNullOrEmpty()) return false
        val cover = book.coverUrl
        if (cover.isNullOrEmpty()) return true
        // 网络地址视为已有封面；本地路径但文件不存在（比如 EPUB 里没有封面图）才补
        if (cover.startsWith("http", ignoreCase = true)) return false
        return !File(cover).exists()
    }

    /**
     * 按需生成并写入封面文件，修改 [book] 的 coverUrl（不负责写库）。
     * @return 是否生成了新封面
     */
    fun ensure(book: Book): Boolean {
        if (!MaxPrefs.autoCoverEnabled) return false
        if (!needsCover(book)) return false
        return try {
            val path = book.coverUrl?.takeIf { it.isNotEmpty() && !it.startsWith("http") }
                ?: LocalBook.getCoverPath(book)
            val bmp = render(book.name, book.author)
            FileOutputStream(FileUtils.createFileIfNotExist(path)).use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            bmp.recycle()
            book.coverUrl = path
            true
        } catch (e: Exception) {
            AppLog.put("自动封面生成失败：${book.name}\n${e.localizedMessage}", e)
            false
        }
    }

    /** 给书架上已有的、没有封面的本地书补封面（后台调用） */
    fun backfill() {
        if (!MaxPrefs.autoCoverEnabled) return
        val books = runCatching { appDb.bookDao.all }.getOrNull() ?: return
        books.forEach { book ->
            if (needsCover(book) && ensure(book)) {
                runCatching { appDb.bookDao.update(book) }
            }
        }
    }
}
