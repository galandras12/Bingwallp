package com.galandras.bingwallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import android.view.WindowManager
import java.io.File
import java.io.IOException

class ImageException(message: String, cause: Throwable? = null) : Exception(message, cause)

object ImageProcessor {
    /** Álló tájolású képernyőméret pixelben (WindowMetrics, régi API-n DisplayMetrics). */
    fun screenSize(context: Context): Pair<Int, Int> {
        val (w, h) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = context.getSystemService(WindowManager::class.java)
            val b = wm.maximumWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val dm = context.resources.displayMetrics
            dm.widthPixels to dm.heightPixels
        }
        return minOf(w, h).coerceAtLeast(1) to maxOf(w, h).coerceAtLeast(1)
    }

    /**
     * A fájlból center-crop-pal a [dstW]x[dstH] arányú képet készít, memóriatakarékosan:
     * csak a vágási területet dekódolja (BitmapRegionDecoder), mintavételezéssel.
     */
    fun decodeCropped(file: File, dstW: Int, dstH: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val srcW = bounds.outWidth
        val srcH = bounds.outHeight
        if (srcW <= 0 || srcH <= 0) throw ImageException("A letöltött fájl nem érvényes kép")

        val crop = ImageMath.centerCrop(srcW, srcH, dstW, dstH)
        val sample = ImageMath.sampleSize(crop.width, crop.height, dstW, dstH)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded: Bitmap = try {
            decodeRegion(file, Rect(crop.left, crop.top, crop.right, crop.bottom), opts)
        } catch (e: OutOfMemoryError) {
            throw ImageException("Kevés a memória a kép feldolgozásához", e)
        } ?: decodeFullAndCrop(file, srcW, srcH, dstW, dstH, sample)

        val (tw, th) = ImageMath.fitDown(decoded.width, decoded.height, dstW, dstH)
        if (tw == decoded.width && th == decoded.height) return decoded
        val scaled = Bitmap.createScaledBitmap(decoded, tw, th, true)
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private fun decodeRegion(file: File, rect: Rect, opts: BitmapFactory.Options): Bitmap? = try {
        val decoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BitmapRegionDecoder.newInstance(file.path)
        } else {
            @Suppress("DEPRECATION") BitmapRegionDecoder.newInstance(file.path, false)
        }
        try {
            decoder.decodeRegion(rect, opts)
        } finally {
            decoder.recycle()
        }
    } catch (e: IOException) {
        null // visszalépünk a teljes dekódolásra
    }

    private fun decodeFullAndCrop(file: File, srcW: Int, srcH: Int, dstW: Int, dstH: Int, sample: Int): Bitmap {
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val full = try {
            BitmapFactory.decodeFile(file.path, opts)
        } catch (e: OutOfMemoryError) {
            throw ImageException("Kevés a memória a kép feldolgozásához", e)
        } ?: throw ImageException("A kép nem dekódolható")
        val c = ImageMath.centerCrop(full.width, full.height, dstW, dstH)
        val cropped = Bitmap.createBitmap(full, c.left, c.top, c.width, c.height)
        if (cropped !== full) full.recycle()
        return cropped
    }

    /** Kicsinyített előnézet (JPEG) a fájlból, max. [maxWidth] széles. */
    fun writePreview(src: File, dest: File, maxWidth: Int = 1280) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw ImageException("Érvénytelen kép")
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
        val bmp = BitmapFactory.decodeFile(src.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw ImageException("Az előnézet nem dekódolható")
        try {
            val tmp = File(dest.parentFile, dest.name + ".tmp")
            tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            if (!tmp.renameTo(dest)) throw IOException("Nem sikerült menteni az előnézetet")
        } finally {
            bmp.recycle()
        }
    }
}
