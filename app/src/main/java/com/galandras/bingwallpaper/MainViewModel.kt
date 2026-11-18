package com.galandras.bingwallpaper

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val settings: SettingsSnapshot,
    val preview: Bitmap? = null,
    val busy: Boolean = false,
    val loadingPreview: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = Settings(app)
    private val updater = WallpaperUpdater(app)
    private var shownStamp = -1L

    private val _state = MutableStateFlow(UiState(settings.snapshot()))
    val state: StateFlow<UiState> = _state

    init {
        viewModelScope.launch {
            settings.changes.collect { onSettingsChanged() }
        }
        // Az előnézet frissítése megnyitáskor (hálózati hiba esetén a régi marad).
        viewModelScope.launch {
            _state.update { it.copy(loadingPreview = true) }
            withContext(Dispatchers.IO) { updater.refreshPreview() }
            _state.update { it.copy(loadingPreview = false) }
        }
        // Meglévő beállítás szerint az ütemezés biztosítása (UPDATE: azonos paraméterekkel nem nullázza az időzítőt).
        applySchedule()
    }

    private suspend fun onSettingsChanged() {
        val snap = settings.snapshot()
        var bmp = _state.value.preview
        if (snap.previewStamp != shownStamp) {
            shownStamp = snap.previewStamp
            bmp = withContext(Dispatchers.IO) { loadPreview() } ?: bmp
        }
        _state.update { it.copy(settings = snap, preview = bmp) }
    }

    private fun loadPreview(): Bitmap? = runCatching {
        val f = updater.previewFile()
        if (f.exists()) BitmapFactory.decodeFile(f.path) else null
    }.getOrNull()

    private fun applySchedule() {
        val s = settings.snapshot()
        if (s.autoEnabled) WallpaperWorker.schedule(getApplication(), s.intervalHours)
        else WallpaperWorker.cancel(getApplication())
    }

    fun setAuto(enabled: Boolean) {
        settings.setAuto(enabled)
        applySchedule()
    }

    fun setTarget(target: Target) {
        settings.setTarget(target)
        // Új cél: a mai kép újra beállítandó erre a célra (az "már beállítva" ellenőrzés célt is néz).
        if (settings.snapshot().autoEnabled) applySchedule()
    }

    fun setInterval(hours: Int) {
        settings.setInterval(hours)
        applySchedule()
    }

    fun setNow() {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            withContext(Dispatchers.IO) { updater.run(force = true) }
            _state.update { it.copy(busy = false) }
        }
    }
}
