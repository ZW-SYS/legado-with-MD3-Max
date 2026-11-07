package io.legado.app.feature.readstats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.data.repository.ReadRecordRepository
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.privacy.PrivateRecordKey
import io.legado.app.ui.widget.components.privacy.rememberPrivateLockedRecords
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * 阅读统计（阅读Max）：今日/本周时长、连续阅读天数、近一年热力图、累计最久的书。
 * 数据直接来自已有的 readRecordDetail 表（按天按书的阅读时长），不新增表和字段。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadStatsRouteScreen(
    onBackClick: () -> Unit,
    onBookClick: (name: String, author: String) -> Unit,
) {
    val repository: ReadRecordRepository = koinInject()
    val flow = remember(repository) {
        repository.getAllRecordDetails()
            .map { ReadStats.compute(it) }
            .flowOn(Dispatchers.Default)
    }
    val stats by flow.collectAsState(initial = null)
    ReadStatsScreen(stats = stats, onBackClick = onBackClick, onBookClick = onBookClick)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadStatsScreen(
    stats: ReadStats?,
    onBackClick: () -> Unit,
    onBookClick: (name: String, author: String) -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = "阅读统计",
                scrollBehavior = scrollBehavior,
                navigationIcon = { TopBarNavigationButton(onClick = onBackClick) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = padding.calculateTopPadding(),
                bottom = 120.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (stats == null) {
                item { AppText("正在统计…", modifier = Modifier.padding(16.dp)) }
                return@LazyColumn
            }
            item { SummaryCard(stats) }
            item { StreakCard(stats) }
            item { HeatmapYearCard(stats) }
            item { TopBooksCard(stats, onBookClick) }
        }
    }
}

@Composable
private fun StatsCard(content: @Composable () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun CardTitle(text: String) {
    AppText(
        text = text,
        style = LegadoTheme.typography.titleMedium,
        color = LegadoTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun BigNumber(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        AppText(
            text = value,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Serif,
            color = LegadoTheme.colorScheme.primary,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        AppText(
            text = label,
            fontSize = 12.sp,
            color = LegadoTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SummaryCard(stats: ReadStats) {
    StatsCard {
        CardTitle("阅读时长")
        Row(Modifier.fillMaxWidth()) {
            BigNumber("今天", ReadStats.formatDuration(stats.todayMillis), Modifier.weight(1f))
            BigNumber("本周", ReadStats.formatDuration(stats.weekMillis), Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            BigNumber("今年", ReadStats.formatDuration(stats.yearMillis), Modifier.weight(1f))
            BigNumber("累计", ReadStats.formatDuration(stats.totalMillis), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StreakCard(stats: ReadStats) {
    StatsCard {
        CardTitle("连续阅读")
        Row(Modifier.fillMaxWidth()) {
            BigNumber("当前连续", "${stats.currentStreak} 天", Modifier.weight(1f))
            BigNumber("最长连续", "${stats.longestStreak} 天", Modifier.weight(1f))
            BigNumber("今年读书天数", "${stats.readingDaysThisYear} 天", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        AppText(
            text = when {
                stats.todayMillis >= ReadStats.MIN_DAY_MILLIS -> "今天已经读过了，连续记录在延续。"
                stats.currentStreak > 0 -> "今天还没读，读满 1 分钟就能续上 ${stats.currentStreak} 天的连续记录。"
                else -> "每天读满 1 分钟计入连续天数。"
            },
            fontSize = 12.sp,
            color = LegadoTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HeatmapYearCard(stats: ReadStats) {
    val colors = LegadoTheme.colorScheme
    val empty = colors.surfaceContainerHighest
    val levelColors = listOf(
        empty,
        colors.primary.copy(alpha = 0.28f),
        colors.primary.copy(alpha = 0.5f),
        colors.primary.copy(alpha = 0.75f),
        colors.primary,
    )
    val today = stats.today
    // 最后一列是本周（周一开头），向前共 53 列
    val lastWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val firstWeekStart = lastWeekStart.minusWeeks(52)
    val weeks = 53
    val cell = 12.dp
    val gap = 3.dp
    val scroll = rememberScrollState()
    LaunchedEffect(scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }

    StatsCard {
        CardTitle("近一年")
        Row {
            // 星期标签
            Column(Modifier.padding(top = 16.dp, end = 4.dp)) {
                listOf("一", "", "三", "", "五", "", "日").forEach {
                    Box(Modifier.height(cell + gap), contentAlignment = Alignment.CenterStart) {
                        AppText(it, fontSize = 9.sp, color = colors.onSurfaceVariant)
                    }
                }
            }
            Column(Modifier.horizontalScroll(scroll)) {
                // 月份标签
                Row(Modifier.height(16.dp)) {
                    var lastMonth = -1
                    for (w in 0 until weeks) {
                        val weekStart = firstWeekStart.plusWeeks(w.toLong())
                        val label = if (weekStart.monthValue != lastMonth && weekStart.dayOfMonth <= 7) {
                            lastMonth = weekStart.monthValue
                            "${weekStart.monthValue}月"
                        } else ""
                        Box(Modifier.width(cell + gap)) {
                            if (label.isNotEmpty()) {
                                AppText(label, fontSize = 9.sp, color = colors.onSurfaceVariant, softWrap = false)
                            }
                        }
                    }
                }
                val density = LocalDensity.current
                val cellPx = with(density) { cell.toPx() }
                val gapPx = with(density) { gap.toPx() }
                val radius = with(density) { 2.5.dp.toPx() }
                Canvas(
                    modifier = Modifier
                        .width((cell + gap) * weeks)
                        .height((cell + gap) * 7)
                        .semantics {
                            contentDescription =
                                "近一年阅读热力图，今年共读书 ${stats.readingDaysThisYear} 天"
                        }
                ) {
                    for (w in 0 until weeks) {
                        for (d in 0 until 7) {
                            val date = firstWeekStart.plusWeeks(w.toLong()).plusDays(d.toLong())
                            if (date.isAfter(today)) continue
                            val level = ReadStats.level(stats.byDate[date] ?: 0L)
                            drawRoundRect(
                                color = levelColors[level],
                                topLeft = Offset(w * (cellPx + gapPx), d * (cellPx + gapPx)),
                                size = Size(cellPx, cellPx),
                                cornerRadius = CornerRadius(radius, radius),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            AppText("少", fontSize = 10.sp, color = colors.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            levelColors.forEach { c ->
                Box(
                    Modifier
                        .padding(horizontal = 1.5.dp)
                        .size(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(c)
                )
            }
            Spacer(Modifier.width(4.dp))
            AppText("多", fontSize = 10.sp, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun TopBooksCard(stats: ReadStats, onBookClick: (String, String) -> Unit) {
    val colors = LegadoTheme.colorScheme
    StatsCard {
        CardTitle("读得最久的书")
        if (stats.topBooks.isEmpty()) {
            AppText("还没有阅读记录，打开一本书开始吧。", fontSize = 13.sp, color = colors.onSurfaceVariant)
            return@StatsCard
        }
        val keys = remember(stats.topBooks) {
            stats.topBooks.map { PrivateRecordKey(it.name, it.author) }
        }
        val locked = rememberPrivateLockedRecords(keys)
        val max = stats.topBooks.first().millis.coerceAtLeast(1L)
        stats.topBooks.forEachIndexed { index, book ->
            val isLocked = PrivateRecordKey(book.name, book.author) in locked
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                onClick = if (isLocked) null else ({ onBookClick(book.name, book.author) }),
                containerColor = Color.Transparent,
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppText(
                        text = "${index + 1}",
                        fontFamily = FontFamily.Serif,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (index < 3) colors.primary else colors.onSurfaceVariant,
                        modifier = Modifier.width(32.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        AppText(
                            text = if (isLocked) "私密书籍" else book.name,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = colors.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .fillMaxWidth(fraction = (book.millis.toFloat() / max).coerceIn(0.04f, 1f))
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(colors.primary.copy(alpha = 0.6f))
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    AppText(
                        text = ReadStats.formatDuration(book.millis),
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
