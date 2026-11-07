package io.legado.app.feature.max

import io.legado.app.help.config.AppConfigStore

/**
 * 阅读Max 自有的轻量开关。
 *
 * 直接读写应用统一的配置快照 [AppConfigStore]（DataStore 持久化），键名带 max_ 前缀，
 * 不进上游的 settings gateway，合并上游时不会和上游的设置结构冲突。
 */
object MaxPrefs {
    private const val KEY_SPLASH_QUOTE = "max_splash_quote_enabled"
    private const val KEY_AUTO_COVER = "max_auto_cover_enabled"
    private const val KEY_BEDTIME_MINUTES = "max_bedtime_minutes"

    /** 开屏名句，默认开启 */
    var splashQuoteEnabled: Boolean
        get() = runCatching { AppConfigStore.getBoolean(KEY_SPLASH_QUOTE) }.getOrNull() ?: true
        set(value) = AppConfigStore.putBoolean(KEY_SPLASH_QUOTE, value)

    /** 本地书无封面时自动生成封面，默认开启 */
    var autoCoverEnabled: Boolean
        get() = runCatching { AppConfigStore.getBoolean(KEY_AUTO_COVER) }.getOrNull() ?: true
        set(value) = AppConfigStore.putBoolean(KEY_AUTO_COVER, value)

    /** 睡前模式上次选择的时长（分钟），只用于记住选项；睡前模式本身每次手动开启，默认关闭 */
    var bedtimeMinutes: Int
        get() = runCatching { AppConfigStore.getInt(KEY_BEDTIME_MINUTES) }.getOrNull() ?: 30
        set(value) = AppConfigStore.putInt(KEY_BEDTIME_MINUTES, value)
}
