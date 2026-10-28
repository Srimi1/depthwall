package dev.srimi.depthwall.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.srimi.depthwall.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(coil.annotation.ExperimentalCoilApi::class)
@Composable fun SettingsDialog(settings:AppSettings,items:List<WallpaperItem>,vm:MainViewModel,onClose:()->Unit) {
    var s by remember { mutableStateOf(settings) }
    val context=LocalContext.current
    AlertDialog(onDismissRequest=onClose,title={Text("Make it yours")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Native scene quality",style=MaterialTheme.typography.titleSmall)
            ChoiceRow(listOf("Battery","Balanced","High Quality"),s.quality){s=s.copy(quality=it)}
            Text("Balanced uses smooth transitions and restrained idle motion. Original Xiaomi packs control their own performance.",fontSize=12.sp,color=Muted)
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
            Text("Saved images: %.1f MB".format(items.filter { it.localPath.isNotBlank() }.sumOf { it.bytes }/1048576.0),fontSize=12.sp,color=Muted)
            TextButton(onClick={vm.run("Thumbnail cache cleared. Saved wallpapers are kept.") { withContext(Dispatchers.IO) { coil.Coil.imageLoader(context).diskCache?.clear();coil.Coil.imageLoader(context).memoryCache?.clear() } }}){Text("Clear thumbnail cache")}
            HorizontalDivider()
            Text("DepthWall 1.0.0",style=MaterialTheme.typography.titleSmall)
            Text("Personal edition · Android 12+\nDesigned for the OnePlus 7T Pro\nNo ads. No accounts. No analytics.",fontSize=12.sp,color=Muted)
            Text("Phone-specific frame rate, battery and original-pack compatibility require testing on your crDroid build. AOD and native lock-screen clock layering are not enabled by this no-root app.",fontSize=12.sp,color=Muted)
        }
    },confirmButton={TextButton(onClick={vm.saveSettings(s);onClose()}){Text("Save preferences")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
}
@Composable private fun Toggle(label:String,value:Boolean,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text(label,Modifier.weight(1f),fontSize=13.sp);Switch(value,onChange) }
}
