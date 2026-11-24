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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
        private var loadedPath = ""
        private var xOffset = 0.5f
        private var surfaceW = 0
        private var surfaceH = 0
        private var autoTried = false
        private var offsetEvents = 0
        private var tiltOn = false
        private var sensorsOn = false
        private var filteredAx = 0f
        private var tiltReach = 0.35f
        private var tiltSmooth = 0.5f
        private val sensorManager by lazy { getSystemService(SensorManager::class.java) }

        private val tiltListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // 1) Aluláteresztő szűrő: a rázkódás/gyors mozgás ne rángassa a képet.
                filteredAx += ImageMath.filterAlpha(tiltSmooth) * (event.values[0] - filteredAx)
                val target = ImageMath.tiltOffset(filteredAx, reach = tiltReach)
                // 2) Holtsáv: a kézremegésnyi apró változást figyelmen kívül hagyja.
                // 3) Ha ennél nagyobb a változás, fokozatosan (nem ugrásszerűen) követi a célt.
                if (Math.abs(target - xOffset) >= ImageMath.deadBand(tiltSmooth)) {
                    xOffset += (target - xOffset) * 0.35f
                    draw()
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        private var lastDiagMillis = 0L
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
            fetchOnce()
        }

        override fun onDestroy() {
            if (sensorsOn) sensorManager?.unregisterListener(tiltListener)
            sensorsOn = false
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
            if (tiltOn) return // dőléses módban a döntés vezérli a képet, nem a launcher
            // Az eltolást a launcher lépésétől függetlenül használjuk (a Samsung Home gyakran nem ad lépést).
            // Ha a launcher nem lapoz, az alapértelmezett eltolás 0.5, vagyis a kép középre kerül.
            this.xOffset = xOffset.coerceIn(0f, 1f)
            offsetEvents++
            recordDiag(xOffset, xOffsetStep)
            draw()
        }

        private fun updateSensors(visible: Boolean) {
            val snap = Settings(applicationContext).snapshot()
            tiltOn = snap.tilt
            tiltReach = snap.tiltReach
            tiltSmooth = snap.tiltSmooth
            val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val want = visible && tiltOn && accel != null
            if (want && !sensorsOn) {
                sensorManager?.registerListener(tiltListener, accel, SensorManager.SENSOR_DELAY_GAME)
                sensorsOn = true
            } else if (!want && sensorsOn) {
                sensorManager?.unregisterListener(tiltListener)
                sensorsOn = false
                if (!tiltOn) xOffset = 0.5f // kikapcsolt dőlés: vissza középre
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            updateSensors(visible)
            if (!visible) return
            reloadIfChanged()
            fetchOnce()
            draw()
        }

        /** Diagnosztika az appnak: hány lapozási jelet kapott a háttér (legfeljebb ~3 mentés/mp). */
        private fun recordDiag(x: Float, step: Float) {
            val now = System.currentTimeMillis()
            if (now - lastDiagMillis < 300) return
            lastDiagMillis = now
            Settings(applicationContext).setLiveDiag(
                applicationContext.getString(R.string.live_diag, offsetEvents, "%.3f".format(x), "%.3f".format(step)),
            )
        }

        private fun reloadIfChanged() {
            var f = LiveWallpaper.imageFile(applicationContext)
            if (!f.exists()) {
                // Amíg nincs széles kép, az előnézeti (16:9) kép látszik, ha van.
                f = WallpaperUpdater(applicationContext).previewFile()
                if (!f.exists() || bitmap != null) return
            }
            val stamp = f.lastModified()
            if (f.path == loadedPath && stamp == loadedStamp && bitmap != null) return
            val fresh = runCatching { BitmapFactory.decodeFile(f.path) }.getOrNull() ?: return
            val old = bitmap
            bitmap = fresh
            loadedStamp = stamp
            loadedPath = f.path
            old?.recycle()
        }

        /** Ha még nincs széles kép, a háttérben elkészíti (mellékhatás nélkül, az előnézetben is). */
        private fun fetchOnce() {
            if (autoTried || LiveWallpaper.imageFile(applicationContext).exists()) return
            autoTried = true
            thread(name = "bing-live-initial") {
                WallpaperUpdater(applicationContext).prepareLiveImage()
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
