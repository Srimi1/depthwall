package dev.srimi.depthwall

import android.os.Bundle
import android.app.KeyguardManager
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.srimi.depthwall.data.StandbyEntryGate
import dev.srimi.depthwall.standby.StandbyActivity
import dev.srimi.depthwall.standby.StandbyDeviceMonitor
import dev.srimi.depthwall.standby.StandbySettingsActivity
import dev.srimi.depthwall.ui.*
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

open class MainActivity:ComponentActivity() {
    private val standbyEntry: StandbyEntryState by viewModels()
    private val standbyLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        standbyEntry.inFlight = false
        standbyEntry.gate.dismiss()
    }
    private val standbySettingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        standbyEntry.gate.dismiss()
    }

    fun openStandbySettings() {
        standbyEntry.gate.dismiss()
        standbySettingsLauncher.launch(android.content.Intent(this, StandbySettingsActivity::class.java))
    }

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.BLACK))
        if (this !is WallpaperSettingsActivity) lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                (application as DepthWallApp).repository.standbySettings.flow.collectLatest { settings ->
                    if (!settings.enabled) {
                        standbyEntry.gate.update(false, false, null)
                        return@collectLatest
                    }
                    val monitor = StandbyDeviceMonitor(this@MainActivity, { state ->
                        val enter = standbyEntry.gate.update(true, state.powered, state.landscape)
                        if (enter && !standbyEntry.inFlight && lifecycle.currentState == Lifecycle.State.RESUMED &&
                            getSystemService(PowerManager::class.java).isInteractive &&
                            !getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
                            standbyEntry.inFlight = true
                            standbyEntry.gate.dismiss()
                            standbyLauncher.launch(StandbyActivity.intent(this@MainActivity))
                        }
                    })
                    try { monitor.start(); awaitCancellation() }
                    finally { monitor.stop() }
                }
            }
        }
        setContent {
            val vm: MainViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            val ready by vm.settingsReady.collectAsStateWithLifecycle()
            DepthTheme(settings.appTheme) {
                if(ready) DepthApp(vm, initialSettingsOpen=this is WallpaperSettingsActivity)
                else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }
}

/** Android's live wallpaper settings button opens the same depth controls as the app. */
class WallpaperSettingsActivity : MainActivity()

class StandbyEntryState : ViewModel() {
    val gate = StandbyEntryGate()
    var inFlight = false
}
