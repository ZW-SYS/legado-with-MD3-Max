package io.legado.app.feature.excerpt

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.base.BaseComposeActivity
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.ImageSaveUtils
import io.legado.app.utils.share
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * 摘录卡片（阅读Max）：阅读器长按选中文字 →「摘录卡片」→ 选样式 → 保存到相册 / 分享。
 */
class ExcerptCardActivity : BaseComposeActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val content = remember {
            ExcerptContent(
                text = intent.getStringExtra(EXTRA_TEXT).orEmpty(),
                bookName = intent.getStringExtra(EXTRA_BOOK).orEmpty(),
                author = intent.getStringExtra(EXTRA_AUTHOR).orEmpty(),
                chapter = intent.getStringExtra(EXTRA_CHAPTER).orEmpty(),
                appName = getString(R.string.app_name),
            )
        }
        var style by remember { mutableStateOf(ExcerptStyle.PAPER) }
        val bitmap by produceState<Bitmap?>(initialValue = null, style) {
            value = withContext(Dispatchers.Default) { ExcerptCardRenderer.render(content, style) }
        }
        val scope = rememberCoroutineScope()
        val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()

        AppScaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                GlassMediumFlexibleTopAppBar(
                    title = "摘录卡片",
                    scrollBehavior = scrollBehavior,
                    navigationIcon = { TopBarNavigationButton(onClick = { finish() }) }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExcerptStyle.entries.forEach { s ->
                        FilterChip(
                            selected = style == s,
                            onClick = { style = s },
                            label = { Text(s.label) },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(6.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    val bmp = bitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "摘录卡片预览",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        AppText("正在生成…", modifier = Modifier.padding(40.dp))
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SmallPlainButton(
                        onClick = {
                            val bmp = bitmap ?: return@SmallPlainButton
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    val bytes = ByteArrayOutputStream().use {
                                        bmp.compress(Bitmap.CompressFormat.JPEG, 95, it)
                                        it.toByteArray()
                                    }
                                    ImageSaveUtils.saveImageToGallery(
                                        this@ExcerptCardActivity, bytes, "Excerpt_", "阅读Max"
                                    )
                                }
                                toastOnUi(if (ok) "已保存到相册（Pictures/阅读Max）" else "保存失败，请检查存储权限")
                            }
                        },
                        icon = Icons.Default.Download,
                        text = "保存到相册",
                    )
                    Spacer(Modifier.width(16.dp))
                    SmallPlainButton(
                        onClick = {
                            val bmp = bitmap ?: return@SmallPlainButton
                            scope.launch {
                                val file = withContext(Dispatchers.IO) {
                                    val dir = File(cacheDir, "excerpt").apply { mkdirs() }
                                    File(dir, "excerpt.jpg").also { f ->
                                        FileOutputStream(f).use {
                                            bmp.compress(Bitmap.CompressFormat.JPEG, 95, it)
                                        }
                                    }
                                }
                                share(file, "image/jpeg", "分享摘录卡片")
                            }
                        },
                        icon = Icons.Default.Share,
                        text = "分享",
                    )
                }
                Spacer(Modifier.height(12.dp))
                AppText(
                    text = "选中文字过长时，卡片只保留前 600 字。",
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    style = LegadoTheme.typography.labelSmall,
                )
            }
        }
    }

    companion object {
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_BOOK = "book"
        private const val EXTRA_AUTHOR = "author"
        private const val EXTRA_CHAPTER = "chapter"

        fun start(context: Context, text: String, book: String, author: String, chapter: String) {
            context.startActivity(
                Intent(context, ExcerptCardActivity::class.java)
                    .putExtra(EXTRA_TEXT, text)
                    .putExtra(EXTRA_BOOK, book)
                    .putExtra(EXTRA_AUTHOR, author)
                    .putExtra(EXTRA_CHAPTER, chapter)
            )
        }
    }
}
