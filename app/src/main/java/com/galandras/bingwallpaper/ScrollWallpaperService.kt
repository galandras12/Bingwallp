package com.galandras.bingwallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread

/**
 * Élő háttér, amely a széles Bing-képet rajzolja, és a kezdőképernyő lapozásakor (onOffsetsChanged)
 * eltolja. Ha a launcher nem küld eltolást, a kép középre igazítva látszik (pl. zárolt képernyőn).
 */
class ScrollWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = ScrollEngine()

    private inner class ScrollEngine : Engine() {
        private var bitmap: Bitmap? = null
        private var loadedStamp = -1L
        private var xOffset = 0.5f
        private var surfaceW = 0
        private var surfaceH = 0
        private var autoTried = false
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                reloadIfChanged()
                draw()
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            setOffsetNotificationsEnabled(true)
            ContextCompat.registerReceiver(
                applicationContext, receiver, IntentFilter(LiveWallpaper.ACTION_RELOAD),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            reloadIfChanged()
        }

        override fun onDestroy() {
            runCatching { applicationContext.unregisterReceiver(receiver) }
            bitmap?.recycle()
            bitmap = null
            super.onDestroy()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            surfaceW = width
            surfaceH = height
            draw()
        }

        override fun onOffsetsChanged(
            xOffset: Float, yOffset: Float, xOffsetStep: Float, yOffsetStep: Float, xPixelOffset: Int, yPixelOffset: Int,
        ) {
            // Ha a launcher nem lapoz (step <= 0), középre igazítunk.
            this.xOffset = if (xOffsetStep > 0f) xOffset.coerceIn(0f, 1f) else 0.5f
            draw()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (!visible) return
            reloadIfChanged()
            if (bitmap == null) fetchOnce()
            draw()
        }

        private fun reloadIfChanged() {
            val f = LiveWallpaper.imageFile(applicationContext)
            if (!f.exists()) return
            val stamp = f.lastModified()
            if (stamp == loadedStamp && bitmap != null) return
            val fresh = runCatching { BitmapFactory.decodeFile(f.path) }.getOrNull() ?: return
            val old = bitmap
            bitmap = fresh
            loadedStamp = stamp
            old?.recycle()
        }

        /** Ha még nincs kép (pl. most aktiváltad az élő hátteret), a háttérben letölti a mait. */
        private fun fetchOnce() {
            if (autoTried || !Settings(applicationContext).snapshot().scroll) return
            autoTried = true
            thread(name = "bing-live-initial") {
                WallpaperUpdater(applicationContext).run(force = true)
            }
        }

        private fun draw() {
            val bmp = bitmap
            val holder = surfaceHolder
            if (surfaceW <= 0 || surfaceH <= 0) return
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas() ?: return
                canvas.drawColor(Color.BLACK)
                if (bmp != null && !bmp.isRecycled) {
                    val scale = maxOf(surfaceH.toFloat() / bmp.height, surfaceW.toFloat() / bmp.width)
                    val scaledW = bmp.width * scale
                    val dx = -(scaledW - surfaceW) * xOffset
                    val m = Matrix().apply {
                        postScale(scale, scale)
                        postTranslate(dx, 0f)
                    }
                    canvas.drawBitmap(bmp, m, paint)
                }
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas)
            }
        }
    }
}
