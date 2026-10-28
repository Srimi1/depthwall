package dev.srimi.depthwall.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.srimi.depthwall.DepthWallApp
import dev.srimi.depthwall.data.*
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
    fun draft(item:WallpaperItem)=drafts.getOrPut(item.id) { mutableStateOf(repo.config(item)) }
    private val operations=Mutex()
    val wallpapers=repo.dao.observe().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val settings=repo.settings.flow.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),AppSettings())
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
    fun saveSettings(s:AppSettings)=run { repo.settings.save(s);RotationWorker.schedule(getApplication(),s) }
    fun refreshPacks()=viewModelScope.launch { refreshPacksNow() }
    private suspend fun refreshPacksNow() { _installed.value=packs.installed() }
}
