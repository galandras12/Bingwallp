package com.galandras.bingwallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageMathTest {
    @Test fun landscapeToPortraitPhone() {
        // 3840x2160 -> 1080x2340: magasság marad, szélesség = 2160*1080/2340 = 997
        val r = ImageMath.centerCrop(3840, 2160, 1080, 2340)
        assertEquals(2160, r.height)
        assertEquals(997, r.width)
        assertEquals((3840 - 997) / 2, r.left)
        assertEquals(0, r.top)
    }

    @Test fun sameAspectIsNoCrop() {
        val r = ImageMath.centerCrop(1920, 1080, 1280, 720)
        assertEquals(CropRect(0, 0, 1920, 1080), r)
    }

    @Test fun tallerSourceCropsVertically() {
        val r = ImageMath.centerCrop(1000, 2000, 1000, 1000)
        assertEquals(CropRect(0, 500, 1000, 1500), r)
    }

    @Test fun resultAspectMatchesTarget() {
        val r = ImageMath.centerCrop(3840, 2160, 1440, 3120)
        val diff = Math.abs(r.width.toDouble() / r.height - 1440.0 / 3120)
        assertTrue(diff < 0.001)
        assertTrue(r.left >= 0 && r.top >= 0 && r.right <= 3840 && r.bottom <= 2160)
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsZeroSize() {
        ImageMath.centerCrop(0, 10, 10, 10)
    }

    @Test fun sampleSizeNeverUndershootsRequest() {
        assertEquals(1, ImageMath.sampleSize(997, 2160, 1080, 2340))
        assertEquals(2, ImageMath.sampleSize(2000, 4000, 1000, 2000))
        assertEquals(4, ImageMath.sampleSize(4000, 4000, 1000, 1000))
        assertEquals(1, ImageMath.sampleSize(100, 100, 1000, 1000))
    }

    @Test fun fitDown() {
        assertEquals(1000 to 500, ImageMath.fitDown(1000, 500, 2000, 2000))
        assertEquals(1000 to 500, ImageMath.fitDown(2000, 1000, 1000, 1000))
    }
}
