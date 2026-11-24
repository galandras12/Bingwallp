package com.galandras.bingwallpaper

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import java.io.File
import java.io.IOException

/** A csúsztatható (élő háttér) üzemmód közös segédjei: a széles kép tárolása és értesítés. */
object LiveWallpaper {
    const val ACTION_RELOAD = "com.galandras.bingwallpaper.RELOAD_LIVE"

    fun imageFile(context: Context) = File(context.filesDir, "live_wide.jpg")

    /** Az app élő háttere van-e jelenleg a kezdőképernyőn beállítva. */
    fun isActive(context: Context): Boolean {
        val wm = WallpaperManager.getInstance(context)
        val info = if (Build.VERSION.SDK_INT >= 34) wm.getWallpaperInfo(WallpaperManager.FLAG_SYSTEM) else wm.wallpaperInfo
        return info?.packageName == context.packageName
    }

    /** Atomikusan elmenti a széles képet, és szól a futó háttérszolgáltatásnak. */
    fun write(context: Context, bitmap: Bitmap) {
        val dest = imageFile(context)
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 94, it) }
        if (!tmp.renameTo(dest)) throw IOException("Nem sikerült menteni a széles képet")
        context.sendBroadcast(Intent(ACTION_RELOAD).setPackage(context.packageName))
    }
}
