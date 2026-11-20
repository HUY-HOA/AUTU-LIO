package com.autulio.app.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenDisplayGeometryTest {
    @Test
    fun combinesSystemAndCutoutInsetsWithoutAddingOverlappingEdges() {
        val geometry = ScreenDisplayGeometry(
            widthPx = 1080,
            heightPx = 2400,
            systemInsets = ScreenInsets(left = 0, top = 72, right = 0, bottom = 96),
            cutoutSafeInsets = ScreenInsets(left = 0, top = 110, right = 0, bottom = 0),
            cutoutDetectionKnown = true
        )

        assertEquals(ScreenInsets(top = 110, bottom = 96), geometry.safeInsets)
        assertEquals(1080, geometry.safeWidthPx)
        assertEquals(2194, geometry.safeHeightPx)
    }

    @Test
    fun reportsCutoutAndFlattensItsBoundsInStableOrder() {
        val geometry = ScreenDisplayGeometry(
            widthPx = 1080,
            heightPx = 2400,
            cutoutDetectionKnown = true,
            cutoutBounds = listOf(
                ScreenCutoutBounds(440, 0, 640, 90),
                ScreenCutoutBounds(0, 1100, 24, 1300)
            )
        )

        assertEquals(true, geometry.hasCutout)
        assertEquals(2, geometry.cutoutCount)
        assertEquals(listOf(440, 0, 640, 90, 0, 1100, 24, 1300), geometry.flattenedCutoutBounds())
    }

    @Test
    fun reportsNoCutoutOnlyWhenDetectionIsKnown() {
        val knownWithoutCutout = ScreenDisplayGeometry(
            widthPx = 1080,
            heightPx = 2400,
            cutoutDetectionKnown = true
        )
        val unknown = ScreenDisplayGeometry.unknown(1080, 2400)

        assertFalse(knownWithoutCutout.hasCutout!!)
        assertEquals(0, knownWithoutCutout.cutoutCount)
        assertNull(unknown.hasCutout)
        assertNull(unknown.cutoutCount)
    }

    @Test
    fun returnsCachedGeometryOnlyForThePublishedDisplayDimensions() {
        ScreenDisplayGeometryStore.clear()
        try {
            val published = ScreenDisplayGeometry(
                widthPx = 1080,
                heightPx = 2400,
                systemInsets = ScreenInsets(top = 90, bottom = 80),
                cutoutSafeInsets = ScreenInsets(top = 120),
                cutoutDetectionKnown = true,
                cutoutBounds = listOf(ScreenCutoutBounds(440, 0, 640, 95))
            )
            ScreenDisplayGeometryStore.publish(published)

            assertEquals(published, ScreenDisplayGeometryStore.current(1080, 2400))
            assertNull(ScreenDisplayGeometryStore.current(2400, 1080))
            assertNull(ScreenDisplayGeometryStore.current(720, 1600))
        } finally {
            ScreenDisplayGeometryStore.clear()
        }
    }

    @Test
    fun clearRemovesCachedDisplayGeometry() {
        ScreenDisplayGeometryStore.clear()
        try {
            ScreenDisplayGeometryStore.publish(
                ScreenDisplayGeometry(
                    widthPx = 720,
                    heightPx = 1600,
                    cutoutDetectionKnown = false
                )
            )
            ScreenDisplayGeometryStore.clear()

            assertNull(ScreenDisplayGeometryStore.current(720, 1600))
        } finally {
            ScreenDisplayGeometryStore.clear()
        }
    }

    @Test
    fun limitsSafeDimensionsToZeroWhenInsetsExceedDisplay() {
        val geometry = ScreenDisplayGeometry(
            widthPx = 100,
            heightPx = 100,
            systemInsets = ScreenInsets(left = 80, right = 80, top = 60, bottom = 60),
            cutoutDetectionKnown = false
        )

        assertEquals(0, geometry.safeWidthPx)
        assertEquals(0, geometry.safeHeightPx)
    }
}
