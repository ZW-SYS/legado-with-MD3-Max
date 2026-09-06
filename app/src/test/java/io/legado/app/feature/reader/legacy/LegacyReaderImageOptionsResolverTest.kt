package io.legado.app.feature.reader.legacy

import io.legado.app.feature.reader.core.layout.ReaderImageLayoutMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LegacyReaderImageOptionsResolverTest {
    @Test
    fun sourceTextStyleIsTheOnlyLayoutOverride() {
        val options = LegacyReaderImageOptionsResolver.resolve(
            "https://example/image, {\"style\":\"text\",\"width\":\"37.5%\",\"click\":\"open(\\\"x\\\")\"}",
        )!!
        assertEquals(ReaderImageLayoutMode.INLINE, options.layoutMode)
        assertNull(options.requestedWidthFraction)
        assertNull(options.horizontalAlignment)
        assertEquals("open(\"x\")", options.action)
    }

    @Test
    fun nonTextSourceStyleDoesNotOverrideReaderImageStyle() {
        val options = LegacyReaderImageOptionsResolver.resolve("x,{\"style\":\"full\",\"width\":\"123\"}")!!
        assertNull(options.layoutMode)
        assertNull(options.requestedWidthPx)
    }

    @Test fun malformedOrAbsentOptionsDoNotInventOverrides() {
        assertNull(LegacyReaderImageOptionsResolver.resolve("x"))
        assertNull(LegacyReaderImageOptionsResolver.resolve("x,{bad}"))
    }
}
