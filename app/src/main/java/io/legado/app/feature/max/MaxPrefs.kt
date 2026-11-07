package io.legado.app.feature.max

import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import splitties.init.appCtx

/**
 * 阅读Max 自有的轻量开关。
 *
 * 这些开关只在本 fork 里存在，直接落 SharedPreferences，不进上游的 settings gateway，
 * 合并上游时不会和上游的设置结构冲突。
 */
object MaxPrefs {
    private const val KEY_SPLASH_QUOTE = "max_splash_quote_enabled"
    private const val KEY_AUTO_COVER = "max_auto_cover_enabled"
    private const val KEY_BEDTIME_MINUTES = "max_bedtime_minutes"

    /** 开屏名句，默认开启 */
    var splashQuoteEnabled: Boolean
        get() = appCtx.getPrefBoolean(KEY_SPLASH_QUOTE, true)
        set(value) = appCtx.putPrefBoolean(KEY_SPLASH_QUOTE, value)

    /** 本地书无封面时自动生成封面，默认开启 */
    var autoCoverEnabled: Boolean
        get() = appCtx.getPrefBoolean(KEY_AUTO_COVER, true)
        set(value) = appCtx.putPrefBoolean(KEY_AUTO_COVER, value)

    /** 睡前模式上次选择的时长（分钟），只用于记住选项；睡前模式本身每次手动开启，默认关闭 */
    var bedtimeMinutes: Int
        get() = appCtx.getPrefInt(KEY_BEDTIME_MINUTES, 30)
        set(value) = appCtx.putPrefInt(KEY_BEDTIME_MINUTES, value)
}
