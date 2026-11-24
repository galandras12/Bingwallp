package com.galandras.bingwallpaper

import android.app.WallpaperManager
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class Target(val flags: Int) {
    HOME(WallpaperManager.FLAG_SYSTEM),
    LOCK(WallpaperManager.FLAG_LOCK),
    BOTH(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK),
}

/** Az aktuális beállítások és állapot pillanatképe. */
data class SettingsSnapshot(
    val autoEnabled: Boolean,
    val scroll: Boolean,
    val target: Target,
    val intervalHours: Int,
    val lastImageId: String,
    val lastSetDate: String,
    val lastTarget: String,
    val lastSuccessMillis: Long,
    val status: String,
    val title: String,
    val copyright: String,
    val previewStamp: Long,
    val previewId: String,
)

/** SharedPreferences-alapú tároló (kis, függőségmentes). */
class Settings(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("bing_hatter", Context.MODE_PRIVATE)

    fun snapshot() = SettingsSnapshot(
        autoEnabled = prefs.getBoolean(K_AUTO, true),
        scroll = prefs.getBoolean(K_SCROLL, false),
        target = runCatching { Target.valueOf(prefs.getString(K_TARGET, null) ?: "BOTH") }.getOrDefault(Target.BOTH),
        intervalHours = prefs.getInt(K_INTERVAL, 24).let { if (it == 12) 12 else 24 },
        lastImageId = prefs.getString(K_IMAGE_ID, "") ?: "",
        lastSetDate = prefs.getString(K_SET_DATE, "") ?: "",
        lastTarget = prefs.getString(K_LAST_TARGET, "") ?: "",
        lastSuccessMillis = prefs.getLong(K_SUCCESS, 0L),
        status = prefs.getString(K_STATUS, "") ?: "",
        title = prefs.getString(K_TITLE, "") ?: "",
        copyright = prefs.getString(K_COPYRIGHT, "") ?: "",
        previewStamp = prefs.getLong(K_PREVIEW, 0L),
        previewId = prefs.getString(K_PREVIEW_ID, "") ?: "",
    )

    fun setAuto(v: Boolean) = prefs.edit().putBoolean(K_AUTO, v).apply()
    fun setScroll(v: Boolean) = prefs.edit().putBoolean(K_SCROLL, v).apply()
    fun setTarget(v: Target) = prefs.edit().putString(K_TARGET, v.name).apply()
    fun setInterval(h: Int) = prefs.edit().putInt(K_INTERVAL, if (h == 12) 12 else 24).apply()
    fun setStatus(s: String) = prefs.edit().putString(K_STATUS, s).apply()

    fun setMeta(image: BingImage, previewStamp: Long) = prefs.edit()
        .putString(K_TITLE, image.title)
        .putString(K_COPYRIGHT, image.copyright)
        .putLong(K_PREVIEW, previewStamp)
        .putString(K_PREVIEW_ID, image.id)
        .apply()

    fun markApplied(image: BingImage, variant: String, date: String, now: Long) = prefs.edit()
        .putString(K_IMAGE_ID, image.id)
        .putString(K_SET_DATE, date)
        .putString(K_LAST_TARGET, variant)
        .putLong(K_SUCCESS, now)
        .apply()

    val changes: Flow<Unit> = callbackFlow {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
        prefs.registerOnSharedPreferenceChangeListener(l)
        trySend(Unit)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(l) }
    }

    private companion object {
        const val K_AUTO = "auto"
        const val K_SCROLL = "scroll"
        const val K_TARGET = "target"
        const val K_INTERVAL = "interval"
        const val K_IMAGE_ID = "image_id"
        const val K_SET_DATE = "set_date"
        const val K_LAST_TARGET = "last_target"
        const val K_SUCCESS = "success"
        const val K_STATUS = "status"
        const val K_TITLE = "title"
        const val K_COPYRIGHT = "copyright"
        const val K_PREVIEW = "preview"
        const val K_PREVIEW_ID = "preview_id"
    }
}

/** A beállítás „változata": cél + csúsztatható mód (csak ha van kezdőképernyős cél). */
fun variantKey(target: Target, scroll: Boolean): String =
    if (scroll && target != Target.LOCK) "${target.name}+SCROLL" else target.name

/** Kihagyható-e a letöltés: a mai kép már be van állítva ugyanarra a célra és módban. */
fun isAlreadyApplied(s: SettingsSnapshot, imageId: String, today: String, target: Target, scroll: Boolean): Boolean =
    s.lastImageId == imageId && s.lastSetDate == today && s.lastTarget == variantKey(target, scroll)
