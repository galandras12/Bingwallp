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
        val today = LocalDate.now().toString()

        val image = try {
            BingClient.fetchTodayImage()
        } catch (e: BingException) {
            return fail(R.string.status_err_network, R.string.status_err_format, e)
        }

        if (!force && isAlreadyApplied(snap, image.id, today, target) && snap.lastSuccessMillis > 0) {
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
            val bitmap = ImageProcessor.decodeCropped(tmp, w, h)
            try {
                WallpaperManager.getInstance(context).setBitmap(bitmap, null, true, target.flags)
            } finally {
                bitmap.recycle()
            }
            settings.markApplied(image, target, today, System.currentTimeMillis())
            settings.setStatus(context.getString(R.string.status_ok))
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

    private fun fail(netRes: Int, fmtRes: Int, e: BingException, store: Boolean = true): UpdateResult {
        val msg = context.getString(if (e.transient) netRes else fmtRes, e.message ?: "")
        return if (store) store(msg, e.transient) else UpdateResult.Failed(msg, e.transient)
    }

    private fun store(msg: String, retryable: Boolean): UpdateResult {
        settings.setStatus(msg)
        return UpdateResult.Failed(msg, retryable)
    }
}
