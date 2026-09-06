package io.legado.app.feature.reader.legacy

import io.legado.app.feature.reader.core.layout.ReaderImageLayoutMode
import io.legado.app.feature.reader.core.layout.ReaderImageOptions
import io.legado.app.feature.reader.core.layout.ReaderImageOptionsResolver
import io.legado.app.model.analyzeRule.AnalyzeUrl.Companion.paramPattern
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject

/** Keeps source-URL JSON parsing at the legacy input boundary; the Canvas core receives typed options. */
object LegacyReaderImageOptionsResolver : ReaderImageOptionsResolver {
    override fun resolve(source: String): ReaderImageOptions? {
        val separator = paramPattern.find(source) ?: return null
        val values = GSON.fromJsonObject<Map<String, String>>(
            source.substring(separator.range.last + 1),
        ).getOrNull() ?: return null
        val style = values["style"]?.uppercase()
        val action = values["click"]?.takeIf(String::isNotBlank)
        // The View reader only treats a source-level TEXT style as an override. FULL,
        // SINGLE, alignment and width metadata do not replace the reader's selected image
        // style; retaining that precedence keeps existing book-source rendering compatible.
        return ReaderImageOptions(
            layoutMode = when (style) {
                "TEXT" -> ReaderImageLayoutMode.INLINE
                else -> null
            },
            action = action,
        )
    }
}
