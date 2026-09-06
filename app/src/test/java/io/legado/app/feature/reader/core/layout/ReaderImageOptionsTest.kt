package io.legado.app.feature.reader.core.layout

import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import io.legado.app.feature.reader.core.source.ReaderChapterSourceParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderImageOptionsTest {
    private val shaper = ReaderTextShaper { text ->
        GlyphClusters(text.map(Char::toString), List(text.length) { 10f })
    }
    private val baseStyle = ReaderChapterMeasureStyle(
        ReaderTextStyle(0, 10f), ReaderTextStyle(0, 10f), 0,
        ReaderTextAlignment.START, ReaderTextAlignment.START,
        imageAvailableWidthPx = 100f,
    )
    private val config = ReaderPaginationConfig(0, "", 100, 200, 0f, 0f, 0f, 0f, 10f, 8f)

    private suspend fun measure(
        options: ReaderImageOptions,
        style: ReaderChapterMeasureStyle = baseStyle,
        dimensions: ReaderImageDimensions = ReaderImageDimensions(100f, 50f),
    ): ReaderChapterMeasureResult.Success {
        val source = ReaderChapterSourceParser.parse(
            0, "", listOf("<img src=\"image\">"), false, false,
        )
        return ReaderChapterBlockMeasurer(
            shaper, shaper, { dimensions }, imageOptionsResolver = { options },
        ).measure(source, style) as ReaderChapterMeasureResult.Success
    }

    @Test fun globalTextModeKeepsLargeImagesInline() = runBlocking {
        val result = measure(ReaderImageOptions(), baseStyle.copy(imageLayoutMode = ReaderImageLayoutMode.INLINE))
        assertTrue(result.blocks.single() is ReaderMeasuredBlock.InlineParagraph)
    }

    @Test
    fun inlineImageFitsTheVisibleTextArea() = runBlocking {
        val result = measure(
            ReaderImageOptions(ReaderImageLayoutMode.INLINE, action = "run()"),
            baseStyle.copy(
                imageLayoutMode = ReaderImageLayoutMode.INLINE,
                imageAvailableWidthPx = 100f,
                imageAvailableHeightPx = 40f,
            ),
            ReaderImageDimensions(20f, 10f),
        )
        val paragraph = result.blocks.single() as ReaderMeasuredBlock.InlineParagraph
        val image = paragraph.items.single() as ReaderMeasuredInlineItem.Image
        assertEquals(80f, image.widthPx, 0f)
        assertEquals(40f, image.heightPx, 0f)
        assertEquals("run()", image.action)
    }

    @Test
    fun requestedWidthIsPreservedForInlineImageGeometry() = runBlocking {
        val result = measure(ReaderImageOptions(requestedWidthFraction = .5f))
        val paragraph = result.blocks.single() as ReaderMeasuredBlock.InlineParagraph
        val image = paragraph.items.single() as ReaderMeasuredInlineItem.Image
        assertEquals(50f, image.widthPx, 0f)
        assertEquals(25f, image.heightPx, 0f)
    }

    @Test fun rightAlignmentAndSinglePageOverridesReachPaginator() = runBlocking {
        val right = measure(ReaderImageOptions(
            layoutMode = ReaderImageLayoutMode.STANDALONE,
            horizontalAlignment = ReaderTextAlignment.END,
        )).blocks.single() as ReaderMeasuredBlock.Image
        val rightImage = ReaderPaginator.paginateBlocks(listOf(right), config.copy(viewportWidthPx = 140))
            .single().elements.single() as ReaderElement.Image
        assertEquals(40f, rightImage.bounds.left, 0f)

        val single = measure(
            ReaderImageOptions(ReaderImageLayoutMode.SINGLE_PAGE),
            baseStyle.copy(imageLayoutMode = ReaderImageLayoutMode.INLINE),
        ).blocks.single() as ReaderMeasuredBlock.Image
        assertTrue(single.pageBreakBefore)
        assertTrue(single.pageBreakAfter)
        assertEquals(ReaderImageScaleMode.FIT_PAGE, single.scaleMode)
    }
}
