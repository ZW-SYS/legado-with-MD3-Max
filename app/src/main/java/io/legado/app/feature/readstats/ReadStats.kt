package io.legado.app.feature.readstats

import io.legado.app.data.entities.readRecord.ReadRecordDetail
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** 一本书的累计阅读时长 */
data class BookReadTime(val name: String, val author: String, val millis: Long)

/** 阅读统计面板需要的全部数据，纯计算结果，不依赖 Android。 */
data class ReadStats(
    val todayMillis: Long = 0L,
    val weekMillis: Long = 0L,
    val yearMillis: Long = 0L,
    val totalMillis: Long = 0L,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val readingDaysThisYear: Int = 0,
    /** 近一年按天的阅读时长（毫秒），只含有记录的日子 */
    val byDate: Map<LocalDate, Long> = emptyMap(),
    val topBooks: List<BookReadTime> = emptyList(),
    val today: LocalDate = LocalDate.now(),
) {
    companion object {
        /** 一天读满这么久才算“读过”，避免点开就关的记录把连续天数刷上去 */
        const val MIN_DAY_MILLIS = 60_000L

        fun compute(
            details: List<ReadRecordDetail>,
            today: LocalDate = LocalDate.now(),
            topCount: Int = 10,
        ): ReadStats {
            val byDate = HashMap<LocalDate, Long>()
            val byBook = HashMap<Pair<String, String>, Long>()
            for (d in details) {
                if (d.readTime <= 0) continue
                val date = runCatching { LocalDate.parse(d.date) }.getOrNull() ?: continue
                byDate[date] = (byDate[date] ?: 0L) + d.readTime
                val key = d.bookName to d.bookAuthor
                byBook[key] = (byBook[key] ?: 0L) + d.readTime
            }
            val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val yearStart = today.withDayOfYear(1)
            var week = 0L
            var year = 0L
            var yearDays = 0
            byDate.forEach { (date, ms) ->
                if (!date.isBefore(weekStart) && !date.isAfter(today)) week += ms
                if (!date.isBefore(yearStart) && !date.isAfter(today)) {
                    year += ms
                    if (ms >= MIN_DAY_MILLIS) yearDays++
                }
            }
            val readDays = byDate.filterValues { it >= MIN_DAY_MILLIS }.keys
            // 当前连续：今天读过就从今天往前数；今天还没读，就从昨天往前数（今天还有机会续上）
            var cursor = if (today in readDays) today else today.minusDays(1)
            var current = 0
            while (cursor in readDays) {
                current++
                cursor = cursor.minusDays(1)
            }
            var longest = 0
            var run = 0
            var prev: LocalDate? = null
            for (date in readDays.sorted()) {
                run = if (prev != null && prev.plusDays(1) == date) run + 1 else 1
                if (run > longest) longest = run
                prev = date
            }
            val heatStart = today.minusDays(371)
            return ReadStats(
                todayMillis = byDate[today] ?: 0L,
                weekMillis = week,
                yearMillis = year,
                totalMillis = byDate.values.sum(),
                currentStreak = current,
                longestStreak = longest,
                readingDaysThisYear = yearDays,
                byDate = byDate.filterKeys { it.isAfter(heatStart) },
                topBooks = byBook.entries
                    .sortedByDescending { it.value }
                    .take(topCount)
                    .map { BookReadTime(it.key.first, it.key.second, it.value) },
                today = today,
            )
        }

        /** GitHub 风格的 0~4 档：0=没读，1=<15分钟，2=<30分钟，3=<1小时，4=≥1小时 */
        fun level(millis: Long): Int = when {
            millis < MIN_DAY_MILLIS -> 0
            millis < 15 * 60_000L -> 1
            millis < 30 * 60_000L -> 2
            millis < 60 * 60_000L -> 3
            else -> 4
        }

        fun formatDuration(millis: Long): String {
            val totalMinutes = millis / 60_000L
            val h = totalMinutes / 60
            val m = totalMinutes % 60
            return when {
                h > 0 && m > 0 -> "${h}小时${m}分钟"
                h > 0 -> "${h}小时"
                else -> "${m}分钟"
            }
        }
    }
}
