package dev.srimi.depthwall.ui

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.srimi.depthwall.DepthWallApp
import dev.srimi.depthwall.data.*
import dev.srimi.depthwall.wallpaper.DepthWallpaperService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.MutableState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MainViewModel(app:Application):AndroidViewModel(app) {
    val repo=(app as DepthWallApp).repository
    val packs=PackManager(app)
    val selected=mutableStateOf<WallpaperItem?>(null)
    private val drafts=mutableMapOf<String,MutableState<SceneConfig>>()
    fun draft(item:WallpaperItem)=drafts.getOrPut(item.id) {
        val defaults = settings.value
        mutableStateOf(repo.config(item).copy(
            parallax=defaults.depthParallax,
            clock=defaults.depthClock,
            clockY=defaults.depthClockY,
        ))
    }
    private val operations=Mutex()
    val wallpapers=repo.dao.observe().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val devicePicks=wallpapers.map { DevicePicks.resolve(repo.devicePicks,it) }
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    private val _settingsReady=MutableStateFlow(false)
    val settingsReady=_settingsReady.asStateFlow()
    val settings=repo.settings.flow.onEach { _settingsReady.value=true }
        .stateIn(viewModelScope,SharingStarted.Eagerly,AppSettings())
    private val _message=MutableSharedFlow<String>(extraBufferCapacity=5)
    val messages=_message.asSharedFlow()
    private val _busy=MutableStateFlow(false)
    val busy=_busy.asStateFlow()
    private val _installed=MutableStateFlow<List<InstalledPack>>(emptyList())
    val installed=_installed.asStateFlow()
    init { run { repo.seed(); refreshPacksNow() } }
    fun message(text:String) { _message.tryEmit(text) }
    fun run(success:String?=null, block:suspend ()->Unit) = viewModelScope.launch { operations.withLock {
        _busy.value=true
        try { block();success?.let { _message.emit(it) } }
        catch(e:CancellationException) { throw e }
        catch(e:Exception) { _message.emit(e.message ?: "Something went wrong. Please try again.") }
        finally { _busy.value=false }
    } }
    fun favorite(item:WallpaperItem)=run { repo.dao.seed(listOf(item));repo.dao.toggleFavorite(item.id) }
    fun saveSettings(s:AppSettings)=run { persistSettings(s) }
    private suspend fun persistSettings(s: AppSettings) {
        val previous = settings.value
        repo.settings.save(s)
        if (previous.depthParallax != s.depthParallax || previous.depthClock != s.depthClock ||
            previous.depthClockY != s.depthClockY || previous.depthEnabled != s.depthEnabled) drafts.clear()
        RotationWorker.schedule(getApplication(),s)
    }
    /** Only edit scenes confirmed to be bound to DepthWall; never apply a different wallpaper. */
    fun applyDepthDefaults(s: AppSettings)=run {
        persistSettings(s)
        val state = WallpaperApplyState.capture(getApplication())
            ?: error("Preferences saved. Cannot read the active wallpaper; reopen its preview to apply these controls.")
        val component = ComponentName(getApplication(), DepthWallpaperService::class.java).flattenToString()
        var updated = 0
        val home = repo.scenes.active(1)
        if (state.homeComponent == component && home.scene == "depth") {
            val flags = if (state.lockId < 0) 3 else 1
            repo.scenes.commit(home.copy(parallax=s.depthParallax, clock=s.depthClock, clockY=s.depthClockY), flags)
            updated = updated or flags
        }
        val lock = repo.scenes.active(2)
        if (state.lockId > 0 && state.lockComponent == component && lock.scene == "depth") {
            repo.scenes.commit(lock.copy(parallax=s.depthParallax, clock=s.depthClock, clockY=s.depthClockY), 2)
            updated = updated or 2
        }
        message(if (updated == 0) "Preferences saved. Apply a DepthWall depth wallpaper first to update its motion and clock."
            else "Preferences saved. Active depth wallpaper updated.")
    }
    fun refreshPacks()=viewModelScope.launch { refreshPacksNow() }
    private suspend fun refreshPacksNow() { _installed.value=packs.installed() }
}
