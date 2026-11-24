package com.galandras.bingwallpaper

import android.app.WallpaperManager
import android.content.Context
import java.io.File
import java.io.IOException
import java.time.LocalDate

sealed interface UpdateResult {
    data object Applied : UpdateResult
    data object AlreadyApplied : UpdateResult
    /** [retryable] = átmeneti hiba, a WorkManager újrapróbálhatja. */
    data class Failed(val message: String, val retryable: Boolean) : UpdateResult
}

/** A teljes folyamat: metaadat → (kihagyás ha kész) → letöltés → vágás → beállítás. */
class WallpaperUpdater(private val context: Context) {
    private val settings = Settings(context)

    fun previewFile() = File(context.filesDir, "preview.jpg")

    /** Csak a metaadatot és az előnézetet frissíti (az app megnyitásakor). */
    fun refreshPreview(): UpdateResult {
        val image = try {
            BingClient.fetchTodayImage()
        } catch (e: BingException) {
            return fail(R.string.status_err_network, R.string.status_err_format, e, store = false)
        }
        if (settings.snapshot().previewId == image.id && previewFile().exists()) {
            return UpdateResult.AlreadyApplied
        }
        val tmp = File(context.cacheDir, "download.img")
        return try {
            BingClient.downloadImage(image, tmp)
            ImageProcessor.writePreview(tmp, previewFile())
            settings.setMeta(image, System.currentTimeMillis())
            UpdateResult.Applied
        } catch (e: BingException) {
            fail(R.string.status_err_network, R.string.status_err_format, e, store = false)
        } catch (e: Exception) {
            UpdateResult.Failed(e.message ?: "ismeretlen hiba", false)
        } finally {
            tmp.delete()
        }
    }

    /** @param force true esetén a „már beállítva” ellenőrzést kihagyja. */
    fun run(force: Boolean): UpdateResult {
        val snap = settings.snapshot()
        val target = snap.target
        val scroll = snap.scroll && target != Target.LOCK
        val live = scroll && LiveWallpaper.isActive(context)
        val today = LocalDate.now().toString()

        val image = try {
            BingClient.fetchTodayImage()
        } catch (e: BingException) {
            return fail(R.string.status_err_network, R.string.status_err_format, e)
        }

        if (!force && isAlreadyApplied(snap, image.id, today, target, scroll, live) && snap.lastSuccessMillis > 0) {
            settings.setMeta(image, snap.previewStamp)
            settings.setStatus(context.getString(R.string.status_already))
            return UpdateResult.AlreadyApplied
        }

        val tmp = File(context.cacheDir, "download.img")
        try {
            BingClient.downloadImage(image, tmp)
            runCatching { ImageProcessor.writePreview(tmp, previewFile()) }
                .onSuccess { settings.setMeta(image, System.currentTimeMillis()) }
            val (w, h) = ImageProcessor.screenSize(context)
            val wm = WallpaperManager.getInstance(context)
            if (scroll) {
                // Csúsztatható: a kezdőképernyő képe szélesebb a kijelzőnél (lapozáskor elmozdul).
                val (srcW, srcH) = ImageProcessor.imageSize(tmp)
                val (ww, wh) = ImageMath.scrollTarget(w, h, srcW, srcH)
                if (live) {
                    // Az app élő háttere aktív: csak a széles képet kell frissíteni, ő rajzolja és csúsztatja.
                    val wide = ImageProcessor.decodeCropped(tmp, ww, wh)
                    try { LiveWallpaper.write(context, wide) } finally { wide.recycle() }
                } else {
                    // Tartalék: statikus széles kép (a One UI Home általában nem görgeti).
                    hint(wm, ww, wh)
                    apply(wm, ImageProcessor.decodeCropped(tmp, ww, wh), WallpaperManager.FLAG_SYSTEM)
                }
                // A zárolt képernyő nem görgethető: ott a megszokott, képernyőarányú vágás kell.
                if (target == Target.BOTH) {
                    apply(wm, ImageProcessor.decodeCropped(tmp, w, h), WallpaperManager.FLAG_LOCK)
                }
            } else {
                // Nem csúsztatható mód: a kívánt méret visszaáll a kijelzőméretre.
                hint(wm, w, h)
                apply(wm, ImageProcessor.decodeCropped(tmp, w, h), target.flags)
            }
            settings.markApplied(image, variantKey(target, scroll, live), today, System.currentTimeMillis())
            val ok = context.getString(R.string.status_ok)
            settings.setStatus(
                when {
                    live -> ok + " " + context.getString(R.string.status_live)
                    scroll -> ok + " " + context.getString(
                        R.string.status_scroll_detail, wm.desiredMinimumWidth, wm.desiredMinimumHeight,
                    )
                    else -> ok
                },
            )
            return UpdateResult.Applied
        } catch (e: BingException) {
            return fail(R.string.status_err_network, R.string.status_err_format, e)
        } catch (e: ImageException) {
            return store(context.getString(R.string.status_err_image, e.message), false)
        } catch (e: IOException) {
            return store(context.getString(R.string.status_err_set, e.message ?: "I/O hiba"), true)
        } catch (e: SecurityException) {
            return store(context.getString(R.string.status_err_set, e.message ?: "nincs jogosultság"), false)
        } catch (e: OutOfMemoryError) {
            return store(context.getString(R.string.status_err_image, "kevés a memória"), false)
        } finally {
            tmp.delete()
        }
    }

    private fun hint(wm: WallpaperManager, w: Int, h: Int) {
        try {
            wm.suggestDesiredDimensions(w, h)
        } catch (_: SecurityException) {
            // Hint nélkül is beállítható a háttér; csak a csúsztatás nem garantált.
        }
    }

    private fun apply(wm: WallpaperManager, bitmap: android.graphics.Bitmap, flags: Int) {
        try {
            wm.setBitmap(bitmap, null, true, flags)
        } finally {
            bitmap.recycle()
        }
    }

    private fun fail(netRes: Int, fmtRes: Int, e: BingException, store: Boolean = true): UpdateResult {
        val msg = context.getString(if (e.transient) netRes else fmtRes, e.message ?: "")
        return if (store) store(msg, e.transient) else UpdateResult.Failed(msg, e.transient)
    }

    private fun store(msg: String, retryable: Boolean): UpdateResult {
        settings.setStatus(msg)
        return UpdateResult.Failed(msg, retryable)
    }
}
