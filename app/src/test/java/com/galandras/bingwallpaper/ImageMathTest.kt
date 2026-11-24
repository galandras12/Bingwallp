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

class ScrollWallpaperTest {
    @Test fun wideTargetFollowsSourceAspectWithinCap() {
        // 16:9 forrás, 1080x2340 kijelző: 2340*16/9 = 4160 -> felső korlát 3*1080 = 3240
        assertEquals(3240 to 2340, ImageMath.scrollTarget(1080, 2340, 3840, 2160))
    }

    @Test fun wideTargetUsesSourceAspectWhenBelowCap() {
        // 4:3 forrás, 1080x1920: 1920*4/3 = 2560 (< 3240)
        assertEquals(2560 to 1920, ImageMath.scrollTarget(1080, 1920, 4000, 3000))
    }

    @Test fun wideTargetNeverNarrowerThanScreen() {
        // álló forrás: nem lehet keskenyebb a képernyőnél
        assertEquals(1080 to 2340, ImageMath.scrollTarget(1080, 2340, 1000, 3000))
    }

    @Test fun variantKeyAndAlreadyApplied() {
        assertEquals("BOTH", variantKey(Target.BOTH, false))
        assertEquals("HOME+SCROLL", variantKey(Target.HOME, true))
        assertEquals("LOCK", variantKey(Target.LOCK, true)) // lezárt képernyőnél a mód nem számít
        assertEquals("BOTH+SCROLL+LIVE", variantKey(Target.BOTH, true, live = true))
        val s = SettingsSnapshot(
            autoEnabled = true, scroll = true, target = Target.HOME, intervalHours = 24,
            lastImageId = "id1", lastSetDate = "2026-11-24", lastTarget = "HOME+SCROLL",
            lastSuccessMillis = 1L, status = "", title = "", copyright = "", previewStamp = 0L, previewId = "",
        )
        assertTrue(isAlreadyApplied(s, "id1", "2026-11-24", Target.HOME, true))
        assertTrue(!isAlreadyApplied(s, "id1", "2026-11-24", Target.HOME, false)) // mód váltás: újra kell
        assertTrue(!isAlreadyApplied(s, "id1", "2026-11-24", Target.HOME, true, live = true)) // élő módra váltás: újra kell
        assertTrue(!isAlreadyApplied(s, "id2", "2026-11-24", Target.HOME, true))
        assertTrue(!isAlreadyApplied(s, "id1", "2026-11-25", Target.HOME, true))
    }
}

class TiltTest {
    @Test fun flatIsCentered() {
        assertEquals(0.5f, ImageMath.tiltOffset(0f), 0.0001f)
    }

    @Test fun rightSideDownMovesAbove() {
        assertTrue(ImageMath.tiltOffset(-3f) > 0.5f)
        assertTrue(ImageMath.tiltOffset(3f) < 0.5f)
    }

    @Test fun isSymmetricAndBounded() {
        val a = ImageMath.tiltOffset(-9.81f)
        val b = ImageMath.tiltOffset(9.81f)
        assertEquals(1f, a + b, 0.0001f)
        // erős dőlésnél sem lépi túl a reach által megengedett kitérést (0.5 + 0.5*0.35 = 0.675)
        assertEquals(0.675f, a, 0.0001f)
    }

    @Test fun reachesFullTravelAtMaxAngle() {
        val ax = -(9.81f * Math.sin(Math.toRadians(20.0)).toFloat())
        assertEquals(0.675f, ImageMath.tiltOffset(ax), 0.001f)
        assertEquals(1f, ImageMath.tiltOffset(ax, reach = 1f), 0.001f)
    }
}

class SmoothingTest {
    @Test fun filterGetsSlowerWithMoreSmoothing() {
        assertEquals(0.25f, ImageMath.filterAlpha(0f), 0.0001f)
        assertEquals(0.02f, ImageMath.filterAlpha(1f), 0.0001f)
        assertTrue(ImageMath.filterAlpha(0.3f) > ImageMath.filterAlpha(0.7f))
    }

    @Test fun deadBandGrowsWithSmoothingAndIsClamped() {
        assertTrue(ImageMath.deadBand(0.9f) > ImageMath.deadBand(0.1f))
        assertEquals(ImageMath.deadBand(1f), ImageMath.deadBand(5f), 0.00001f)
        assertEquals(ImageMath.deadBand(0f), ImageMath.deadBand(-1f), 0.00001f)
    }

    @Test fun smallerReachMovesLess() {
        val ax = -4f
        assertTrue(ImageMath.tiltOffset(ax, reach = 0.2f) < ImageMath.tiltOffset(ax, reach = 0.8f))
    }
}
