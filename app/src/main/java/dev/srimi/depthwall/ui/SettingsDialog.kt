package dev.srimi.depthwall.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.srimi.depthwall.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Wait for persisted preferences before making an editable snapshot, including cold system-settings entry. */
@Composable fun SettingsDialog(items:List<WallpaperItem>,vm:MainViewModel,onClose:()->Unit) {
    var attempt by remember { mutableIntStateOf(0) }
    var failure by remember { mutableStateOf<String?>(null) }
    val snapshot by produceState<AppSettings?>(null,vm,attempt) {
        failure=null
        try { value=vm.repo.settings.flow.first() }
        catch(cancelled:CancellationException) { throw cancelled }
        catch(error:Exception) { failure=error.message ?: "Preferences could not be loaded." }
    }
    val loaded=snapshot
    if(loaded!=null)LoadedSettingsDialog(loaded,items,vm,onClose)
    else AlertDialog(onDismissRequest=onClose,
        title={Text(if(failure==null)"Loading preferences" else "Preferences unavailable")},
        text={
            if(failure==null)LinearProgressIndicator(Modifier.fillMaxWidth())
            else Text(failure.orEmpty(),fontSize=13.sp)
        },
        confirmButton={if(failure!=null)TextButton(onClick={attempt++}){Text("Try again")}},
        dismissButton={TextButton(onClick=onClose){Text("Close")}})
}

@OptIn(coil.annotation.ExperimentalCoilApi::class)
@Composable private fun LoadedSettingsDialog(settings:AppSettings,items:List<WallpaperItem>,vm:MainViewModel,onClose:()->Unit) {
    // A preference emission from another screen must not replace an in-progress draft.
    var s by remember { mutableStateOf(settings) }
    val context=LocalContext.current
    val busy by vm.busy.collectAsStateWithLifecycle()
    AlertDialog(onDismissRequest=onClose,title={Text("Make it yours")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("AMOLED themes",style=MaterialTheme.typography.titleSmall)
            ChoiceRow(listOf("Papaya","Space blue","AMOLED monochrome"),s.appTheme){s=s.copy(appTheme=it)}
            Text("Papaya orange and deep black, inspired by your McLaren Edition. These palettes change DepthWall’s interface; system icons and colours stay in Android’s theme settings.",fontSize=12.sp,color=Muted)
            HorizontalDivider()
            Text("Your phone & image quality",style=MaterialTheme.typography.titleSmall)
            ChoiceRow(listOf("OnePlus 7T Pro","Current screen"),s.deviceProfile){s=s.copy(deviceProfile=it)}
            Text(if(s.deviceProfile=="OnePlus 7T Pro")"OnePlus 7T Pro McLaren · 1440 × 3120 · 19.5:9"
                else "Current screen · ${context.resources.displayMetrics.widthPixels} × ${context.resources.displayMetrics.heightPixels}",fontSize=12.sp,color=Muted)
            Text("Export size",fontSize=12.sp,color=Muted)
            ChoiceRow(listOf("Original","Phone optimized","4K portrait"),s.downloadSize){s=s.copy(downloadSize=it)}
            Text("Downloads and Original exports keep the source image. Phone optimized and 4K exports use your preview crop. 4K portrait keeps the phone’s aspect ratio, up to 2160 × 4680; smaller sources are never enlarged.",fontSize=12.sp,color=Muted)
            HorizontalDivider()
            Text("Depth effect",style=MaterialTheme.typography.titleSmall)
            Toggle("Open images in Depth mode",s.depthEnabled){s=s.copy(depthEnabled=it)}
            LabelSlider("Parallax strength",s.depthParallax,0f..1f){s=s.copy(depthParallax=it)}
            Text("Wallpaper clock",fontSize=12.sp,color=Muted)
            ChoiceRow(listOf("Off","Digital","Minimal","Analogue"),s.depthClock){s=s.copy(depthClock=it)}
            if(s.depthClock!="Off")LabelSlider("Clock position",s.depthClockY,.18f.. .72f){s=s.copy(depthClockY=it)}
            Text("Defaults apply when you open a wallpaper preview. For a subject in front of the clock, use a layered wallpaper or create a foreground with Edit mask / Import layer. The Android lock-screen clock is separate.",fontSize=12.sp,color=Muted)
            OutlinedButton(enabled=!busy,onClick={vm.applyDepthDefaults(s);onClose()},modifier=Modifier.fillMaxWidth().testTag("update_active_depth")) {
                Text("Save & update active depth")
            }
            Text("Updates motion and clock only on an active DepthWall depth wallpaper. Choose and apply a wallpaper in Explore or Depth Studio first.",fontSize=12.sp,color=Muted)
            HorizontalDivider()
            Text("Native scene quality",style=MaterialTheme.typography.titleSmall)
            ChoiceRow(listOf("Battery","Balanced","High Quality"),s.quality){s=s.copy(quality=it)}
            Text("Battery stops idle animation. Balanced pauses settled landscapes and uses gentle motion elsewhere. These controls apply to DepthWall originals.",fontSize=12.sp,color=Muted)
            HorizontalDivider()
            Text("Automatic rotation",style=MaterialTheme.typography.titleSmall)
            ChoiceRow(listOf("Off","1 hour","6 hours","Daily"),when(s.rotationHours){1->"1 hour";6->"6 hours";24->"Daily";else->"Off"}){s=s.copy(rotationHours=when(it){"1 hour"->1;"6 hours"->6;"Daily"->24;else->0})}
            if(s.rotationHours>0) {
                ChoiceRow(listOf("Home","Lock","Both"),when(s.rotationTarget){2->"Lock";3->"Both";else->"Home"}){s=s.copy(rotationTarget=when(it){"Lock"->2;"Both"->3;else->1})}
                Toggle("Only favourites",s.favoritesOnly){s=s.copy(favoritesOnly=it)}
                Text("${items.count { it.kind=="STATIC" && it.isLocal && (!s.favoritesOnly || it.favorite) }} eligible images. Runs approximately on schedule; pauses while a live wallpaper is active.",fontSize=12.sp,color=Muted)
            }
            HorizontalDivider()
            Toggle("Download on Wi-Fi only",s.wifiOnly){s=s.copy(wifiOnly=it)}
            Text("No app download quota, subscription or charge. Source availability and rate limits still apply.",fontSize=12.sp,color=Muted)
            Text("Saved images: %.1f MB".format(items.filter { it.localPath.isNotBlank() }.sumOf { it.bytes }/1048576.0),fontSize=12.sp,color=Muted)
            TextButton(onClick={vm.run("Thumbnail cache cleared. Saved wallpapers are kept.") { withContext(Dispatchers.IO) { coil.Coil.imageLoader(context).diskCache?.clear();coil.Coil.imageLoader(context).memoryCache?.clear() } }}){Text("Clear thumbnail cache")}
            HorizontalDivider()
            Text("Always-on display",style=MaterialTheme.typography.titleSmall)
            Text("Wallpaper AOD is controlled by crDroid and is unavailable to this installed app. Use your ROM’s display settings for its clock and AOD options.",fontSize=12.sp,color=Muted)
            TextButton(onClick={
                runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS)) }
                    .onFailure { runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS)) } }
            }) { Text("Open display settings") }
            HorizontalDivider()
            Text("DepthWall ${dev.srimi.depthwall.BuildConfig.VERSION_NAME}",style=MaterialTheme.typography.titleSmall)
            Text("Personal edition · Android 12+\nDesigned for the OnePlus 7T Pro\nNo ads. No accounts. No analytics.",fontSize=12.sp,color=Muted)
            Text("Phone-specific frame rate, battery and original-pack compatibility require testing on your crDroid build. AOD and native lock-screen clock layering are not enabled by this no-root app.",fontSize=12.sp,color=Muted)
        }
    },confirmButton={TextButton(enabled=!busy,onClick={vm.saveSettings(s);onClose()}){Text("Save preferences")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
}
@Composable private fun Toggle(label:String,value:Boolean,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text(label,Modifier.weight(1f),fontSize=13.sp);Switch(value,onChange) }
}
