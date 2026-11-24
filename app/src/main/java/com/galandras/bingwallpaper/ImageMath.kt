package com.galandras.bingwallpaper

/** Vágási téglalap a forráskép pixelkoordinátáiban (right/bottom kizáró). */
data class CropRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}

object ImageMath {
    /** Középre igazított vágás, hogy a kép aránya megegyezzen a cél (képernyő) arányával. */
    fun centerCrop(srcW: Int, srcH: Int, dstW: Int, dstH: Int): CropRect {
        require(srcW > 0 && srcH > 0 && dstW > 0 && dstH > 0) { "A méretek pozitívak kell legyenek" }
        // srcW/srcH > dstW/dstH  <=>  srcW*dstH > dstW*srcH  (Long a túlcsordulás ellen)
        val srcWider = srcW.toLong() * dstH > dstW.toLong() * srcH
        val cropW: Int
        val cropH: Int
        if (srcWider) {
            cropH = srcH
            cropW = Math.round(srcH.toDouble() * dstW / dstH).toInt().coerceIn(1, srcW)
        } else {
            cropW = srcW
            cropH = Math.round(srcW.toDouble() * dstH / dstW).toInt().coerceIn(1, srcH)
        }
        val left = (srcW - cropW) / 2
        val top = (srcH - cropH) / 2
        return CropRect(left, top, left + cropW, top + cropH)
    }

    /**
     * Legnagyobb 2-hatvány inSampleSize, amellyel a [cropW]x[cropH] terület dekódolva még
     * legalább [reqW]x[reqH] pixel marad (nem kell felskálázni a mintavételezés miatt).
     */
    fun sampleSize(cropW: Int, cropH: Int, reqW: Int, reqH: Int): Int {
        require(cropW > 0 && cropH > 0 && reqW > 0 && reqH > 0)
        var sample = 1
        while (cropW / (sample * 2) >= reqW && cropH / (sample * 2) >= reqH) sample *= 2
        return sample
    }

    /**
     * A csúsztatható (a kezdőképernyő lapozásakor elmozduló) háttér célmérete: a magasság a
     * képernyőé, a szélesség a forrás arányát követi, de legalább a képernyő szélessége és
     * legfeljebb [maxFactor]-szorosa (memória- és használhatósági korlát).
     */
    fun scrollTarget(screenW: Int, screenH: Int, srcW: Int, srcH: Int, maxFactor: Double = 3.0): Pair<Int, Int> {
        require(screenW > 0 && screenH > 0 && srcW > 0 && srcH > 0 && maxFactor >= 1.0)
        val width = Math.round(screenH.toDouble() * srcW / srcH).toInt()
            .coerceIn(screenW, Math.round(screenW * maxFactor).toInt())
        return width to screenH
    }

    /** Ha a kép nagyobb a célnál, a cél méretére kicsinyítendő méret; különben változatlan. */
    fun fitDown(w: Int, h: Int, maxW: Int, maxH: Int): Pair<Int, Int> {
        if (w <= maxW && h <= maxH) return w to h
        val scale = minOf(maxW.toDouble() / w, maxH.toDouble() / h)
        return Math.round(w * scale).toInt().coerceAtLeast(1) to Math.round(h * scale).toInt().coerceAtLeast(1)
    }
}
