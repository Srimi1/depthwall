package dev.srimi.depthwall.ui

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dev.srimi.depthwall.R
import dev.srimi.depthwall.data.*
import dev.srimi.depthwall.wallpaper.DepthWallpaperService

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DepthApp(vm:MainViewModel) {
    val context=LocalContext.current
    val items by vm.wallpapers.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val installed by vm.installed.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selected by remember { vm.selected }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var report by remember { mutableStateOf<ApkInspection?>(null) }
    val snackbar=remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    val owner=LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_RESUME)vm.refreshPacks() }
        owner.lifecycle.addObserver(observer);onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val importImage=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.run("Image added to your library") { selected=vm.repo.importImage(it);tab=3 } }
    }
    val importApk=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.run { report=vm.packs.inspect(it) } }
    }
    val applyLive=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val accepted = result.resultCode == Activity.RESULT_OK
        val flags = vm.repo.scenes.finishPreview(accepted,
            if (accepted) WallpaperApplyState.capture(context) else null,
            ComponentName(context,DepthWallpaperService::class.java).flattenToString())
        if (flags != 0) vm.message("Live wallpaper applied")
        else if (accepted) vm.message("Android did not confirm the wallpaper destination. Scene settings were preserved; try applying again.")
    }
    fun launch(intent:Intent) { runCatching { context.startActivity(intent) }.onFailure { vm.message("This action is unavailable: ${it.message}") } }
    fun openLink(url:String)=launch(Intent(Intent.ACTION_VIEW,Uri.parse(url)))
    Scaffold(containerColor=Ink,snackbarHost={SnackbarHost(snackbar)},bottomBar={
        NavigationBar(containerColor=Color(0xFF0C0C0C),tonalElevation=0.dp) {
            val icons=listOf(Icons.Outlined.GridView,Icons.Outlined.Public,Icons.Outlined.Layers,Icons.Outlined.CollectionsBookmark)
            listOf("Explore","Super","Depth Studio","Library").forEachIndexed { i,label ->
                NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(icons[i],label)},label={Text(label,fontSize=11.sp)},colors=NavigationBarItemDefaults.colors(selectedIconColor=Orange,selectedTextColor=Ivory,indicatorColor=Color(0xFF2A1C0D)),modifier=Modifier.testTag("tab_$i"))
            }
        }
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.padding(horizontal=20.dp,vertical=10.dp).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Image(painterResource(R.drawable.depthwall_logo),"DepthWall logo",Modifier.size(38.dp))
                Column(Modifier.padding(start=9.dp).weight(1f)) {
                    Text("DepthWall",fontWeight=FontWeight.Bold,fontSize=22.sp,letterSpacing=(-.8).sp)
                    Text("MADE FOR AMOLED",fontSize=8.sp,letterSpacing=2.sp,color=Muted)
                }
                IconButton(onClick={importImage.launch(arrayOf("image/*"))}) { Icon(Icons.Outlined.AddPhotoAlternate,"Import image",tint=Ivory) }
                if(tab==3)IconButton(onClick={settingsOpen=true},modifier=Modifier.testTag("settings")) { Icon(Icons.Outlined.Tune,"Settings") }
            }
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth(),color=Orange)
            Box(Modifier.weight(1f)) {
                when(tab) {
                    0 -> ExploreScreen(vm,items,{selected=it},{selected=items.firstOrNull { it.id=="scene_earth" }})
                    1 -> SuperScreen(items,installed,{selected=it},::openLink,::launch,{importApk.launch(arrayOf("application/vnd.android.package-archive","application/octet-stream"))},vm)
                    2 -> CollectionScreen("Make it dimensional.","Layered landscapes. Subtle movement. Your own sense of depth.",items.filter { it.kind=="LAYERED_DEPTH" },{selected=it},{vm.favorite(it)},"depth_grid")
                    else -> LibraryScreen(items,{selected=it},{vm.favorite(it)},{settingsOpen=true},{importImage.launch(arrayOf("image/*"))})
                }
            }
        }
    }
    selected?.let { item ->
        WallpaperPreview(items.firstOrNull { it.id==item.id } ?: item,vm,onClose={selected=null},onLive={ config ->
            val before = WallpaperApplyState.capture(context)
            if (before == null) vm.message("Cannot read the current wallpaper destinations. Please try again.")
            else try {
                vm.repo.scenes.stage(config, before)
                applyLive.launch(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,ComponentName(context,DepthWallpaperService::class.java)))
            }
            catch(e:Exception) { vm.repo.scenes.clearPending();vm.message("Wallpaper picker unavailable: ${e.message}") }
        })
    }
    if(settingsOpen)SettingsDialog(settings,items,vm,{settingsOpen=false})
    report?.let { info ->
        AlertDialog(onDismissRequest={report=null},title={Text("Review wallpaper pack")},text={
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(info.packageName,style=MaterialTheme.typography.titleSmall)
                Text("Version ${info.version}\nAndroid API ${info.minSdk}+ · target ${info.targetSdk}\n${info.abis.joinToString().ifBlank { "No native libraries" }}")
                Text("${info.services.size} wallpaper services · ${info.activities.size} exported activities")
                Text(info.problem ?: "Archive checks passed. Source authenticity and actual rendering still need verification.",color=if(info.problem==null)Ivory else Orange)
                Text("Certificate SHA-256\n${info.signatures.joinToString("\n")}\n\nFile SHA-256\n${info.sha256}",fontSize=10.sp,color=Muted)
                Text("Android will ask you to allow installation from DepthWall if needed. Return here and tap Install again.",fontSize=12.sp)
            }
        },confirmButton={TextButton(enabled=info.problem==null,onClick={launch(vm.packs.installationIntent(info))}){Text("Install")}},dismissButton={TextButton(onClick={report=null}){Text("Close")}})
    }
}

@Composable private fun ExploreScreen(vm:MainViewModel,items:List<WallpaperItem>,onSelect:(WallpaperItem)->Unit,onHero:()->Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("All") }
    var color by rememberSaveable { mutableStateOf("All colours") }
    var provider by rememberSaveable { mutableStateOf("Offline") }
    var online by remember { mutableStateOf<List<WallpaperItem>>(emptyList()) }
    var searched by rememberSaveable { mutableStateOf(false) }
    fun searchOnline() {
        val requestedProvider=provider;val requestedQuery=query
        vm.run {
            val results=vm.repo.online(requestedProvider,requestedQuery)
            if(provider==requestedProvider && query==requestedQuery) { online=results;searched=true }
        }
    }
    val source=if(provider=="Offline")items.filter { it.kind=="STATIC" && it.isLocal } else online.map { remote -> items.firstOrNull { it.id==remote.id } ?: remote }
    val filtered=remember(source,query,category,color,provider) { source.filter { (provider!="Offline" || (it.title.contains(query,true)||it.category.contains(query,true))) && (category=="All" || it.category==category || provider!="Offline") && (color=="All colours"||it.color==color||provider!="Offline") } }
    LazyVerticalGrid(columns=GridCells.Fixed(2),contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=24.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(18.dp),modifier=Modifier.testTag("explore_grid")) {
        item(span={GridItemSpan(2)}) {
            Box(Modifier.fillMaxWidth().height(255.dp).clip(RoundedCornerShape(26.dp)).background(Surface).clickable(onClick=onHero)) {
                AsyncImage("file:///android_asset/wallpapers/scene_earth.webp",null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop,alignment=Alignment.CenterEnd)
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(.55f),Color.Transparent))))
                Column(Modifier.padding(22.dp).align(Alignment.BottomStart)) {
                    Text("YOUR SCREEN, REIMAGINED",fontSize=9.sp,letterSpacing=1.7.sp,color=Orange)
                    Spacer(Modifier.height(9.dp));Text("A little space.\nA lot of depth.",style=MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(12.dp));Row(verticalAlignment=Alignment.CenterVertically) { Text("Explore living worlds",fontSize=12.sp);Icon(Icons.Outlined.ArrowOutward,null,Modifier.padding(start=6.dp).size(16.dp),tint=Orange) }
                }
                BadgeLabel("7 LIVING WORLDS",Modifier.align(Alignment.TopStart).padding(18.dp))
            }
        }
        item(span={GridItemSpan(2)}) {
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Text("Find your next favourite",style=MaterialTheme.typography.titleLarge)
                    Text(if(provider=="Offline")"${source.size} wallpapers" else provider,color=Muted,fontSize=11.sp)
                }
                OutlinedTextField(query,{query=it},placeholder={Text(if(provider=="Offline")"Search your collection" else "Search $provider")},leadingIcon={Icon(Icons.Outlined.Search,null)},singleLine=true,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().testTag("search"),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={if(provider!="Offline")searchOnline()}))
                ChoiceRow(listOf("Offline","NASA","Wikimedia"),provider) { provider=it;searched=false;online=emptyList();category="All";color="All colours" }
                if(provider=="Offline") {
                    ChoiceRow(listOf("All","Space","Nature","Abstract","Minimal","Geometry","Orange / Black"),category){category=it}
                    ChoiceRow(listOf("All colours","Orange","Blue","Green","Purple","Gold","Red","White"),color){color=it}
                } else {
                    Text("Download favourites for offline use. Credits stay attached to every image.",color=Muted,fontSize=12.sp)
                    Button(onClick={searchOnline()},modifier=Modifier.fillMaxWidth()) { Text("Find wallpapers") }
                }
            }
        }
        items(filtered,key={it.id}) { item -> WallpaperCard(item,{onSelect(item)},{vm.favorite(item)}) }
        if(filtered.isEmpty())item(span={GridItemSpan(2)}) { EmptyState(if(provider!="Offline" && !searched)"A wider world awaits" else "Nothing here yet",if(provider!="Offline" && !searched)"Search for Earth, Moon, mountains or a colour you love." else "Try another search or filter.") }
    }
}

@Composable fun ChoiceRow(options:List<String>,selected:String,onSelect:(String)->Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        options.forEach { option -> FilterChip(selected=selected==option,onClick={onSelect(option)},label={Text(option,fontSize=12.sp)},shape=RoundedCornerShape(30.dp)) }
    }
}
@Composable fun BadgeLabel(label:String,modifier:Modifier=Modifier) {
    Text(label,modifier.clip(RoundedCornerShape(30.dp)).background(Color(0xCC1C1914)).padding(horizontal=11.dp,vertical=7.dp),color=Orange,fontSize=8.sp,letterSpacing=1.sp,fontWeight=FontWeight.Bold)
}
@Composable fun WallpaperCard(item:WallpaperItem,onSelect:()->Unit,onFavorite:()->Unit) {
    val context = LocalContext.current
    val thumbnail = remember(item.thumbnailModel) {
        coil.request.ImageRequest.Builder(context).data(item.thumbnailModel).size(480, 720).build()
    }
    Column(Modifier.clickable(onClick=onSelect).testTag("wall_${item.id}")) {
        Box(Modifier.fillMaxWidth().aspectRatio(.68f).clip(RoundedCornerShape(19.dp)).background(Surface)) {
            AsyncImage(thumbnail,item.title,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            IconButton(onClick=onFavorite,modifier=Modifier.align(Alignment.TopEnd)) { Icon(if(item.favorite)Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,"Favorite ${item.title}",tint=if(item.favorite)Orange else Ivory,modifier=Modifier.size(19.dp)) }
            if(item.kind!="STATIC")BadgeLabel(if(item.kind=="NATIVE_SCENE")"LIVE" else "DEPTH",Modifier.align(Alignment.BottomStart).padding(9.dp))
            if(item.localPath.isNotEmpty())Icon(Icons.Outlined.OfflinePin,"Available offline",Modifier.align(Alignment.BottomEnd).padding(10.dp).size(16.dp),tint=Ivory)
        }
        Text(item.title,Modifier.padding(top=9.dp),fontWeight=FontWeight.Medium,fontSize=14.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
        Text(item.category,Modifier.padding(top=2.dp),color=Muted,fontSize=11.sp)
    }
}
@Composable fun EmptyState(title:String,body:String) {
    Column(Modifier.fillMaxWidth().padding(vertical=38.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.AutoAwesome,null,Modifier.size(32.dp),tint=Orange)
        Text(title,Modifier.padding(top=16.dp),style=MaterialTheme.typography.titleMedium)
        Text(body,Modifier.padding(top=8.dp),color=Muted,fontSize=13.sp)
    }
}
@Composable fun CollectionScreen(title:String,subtitle:String,items:List<WallpaperItem>,onSelect:(WallpaperItem)->Unit,onFavorite:(WallpaperItem)->Unit,tag:String) {
    LazyVerticalGrid(GridCells.Fixed(2),contentPadding=PaddingValues(20.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(18.dp),modifier=Modifier.testTag(tag)) {
        if(title.isNotBlank() || subtitle.isNotBlank())item(span={GridItemSpan(2)}) {
            Column {
                if(title.isNotBlank())Text(title,style=MaterialTheme.typography.headlineMedium)
                if(subtitle.isNotBlank())Text(subtitle,Modifier.padding(top=9.dp,bottom=8.dp),color=Muted,fontSize=13.sp)
            }
        }
        items(items,key={it.id}) { w -> WallpaperCard(w,{onSelect(w)},{onFavorite(w)}) }
        if(items.isEmpty()) item(span={GridItemSpan(2)}) { EmptyState("Your collection starts here","Save a favourite or import a photo.") }
    }
}
@Composable private fun SuperScreen(items:List<WallpaperItem>,packs:List<InstalledPack>,onSelect:(WallpaperItem)->Unit,open:(String)->Unit,launch:(Intent)->Unit,onImport:()->Unit,vm:MainViewModel) {
    var original by rememberSaveable { mutableStateOf(false) }
    Column {
        Column(Modifier.padding(horizontal=20.dp)) {
            Text("Beyond the still.",style=MaterialTheme.typography.headlineMedium)
            Text("Seven worlds. One extraordinary screen.",color=Muted,modifier=Modifier.padding(top=7.dp,bottom=10.dp))
            ChoiceRow(listOf("DepthWall originals","Xiaomi / HyperOS"),if(original)"Xiaomi / HyperOS" else "DepthWall originals"){original=it=="Xiaomi / HyperOS"}
        }
        if(!original) CollectionScreen("","Built for DepthWall · adjustable performance",items.filter { it.kind=="NATIVE_SCENE" },onSelect,{vm.favorite(it)},"scene_grid")
        else LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            item {
                Text("Original ports install separately. Choose an exposed scene below; public settings appear when available. Rendering on your exact crDroid build remains unverified.",color=Muted,fontSize=13.sp)
                OutlinedButton(onClick=onImport,modifier=Modifier.fillMaxWidth().padding(top=12.dp)) { Icon(Icons.Outlined.InstallMobile,null);Spacer(Modifier.width(8.dp));Text("Inspect / install downloaded APK") }
            }
            items(packs,key={it.definition.packageName}) { p ->
                Card(colors=CardDefaults.cardColors(containerColor=Surface),shape=RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) { Text(p.definition.title,style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f));Icon(Icons.Outlined.Public,null,tint=Orange) }
                        Text(p.version?.let { "Installed · $it" } ?: "Not installed",color=Muted,fontSize=12.sp)
                        vm.packs.verifiedRelease(p.definition.packageName)?.let { evidence ->
                            Text("Original port · %.1f MB · %d scenes".format(evidence.getLong("bytes")/1048576.0,evidence.getJSONArray("scenes").length()),color=Orange,fontSize=12.sp)
                            Text("Archive inspected; installation checked on an Android 17 emulator. Phone rendering still needs testing.",color=Muted,fontSize=11.sp)
                            if(p.version==evidence.getString("version") && p.signatures==listOf(evidence.getString("certificate"))) {
                                val unstable=p.definition.packageName.endsWith("earth") || p.definition.packageName.endsWith("mars")
                                Text(if(unstable) "This original release showed intermittent black previews and crashes on the emulator. Try the DepthWall original below if it fails."
                                    else "Scene imagery verified with an emulator graphics workaround. Phone and AOD support remain unverified.",
                                    color=if(unstable)Orange else Muted,fontSize=11.sp)
                            }
                        }
                        val nativeScene = if(p.definition.packageName.endsWith("snowmountain")) "snow" else p.definition.packageName.substringAfterLast('.')
                        items.firstOrNull { it.kind=="NATIVE_SCENE" && it.scene==nativeScene }?.let { native ->
                            TextButton(onClick={onSelect(native)}) { Text("Use DepthWall original") }
                        }
                        p.error?.let { Text(it,color=Orange,fontSize=12.sp) }
                        p.services.forEach { info ->
                            Text(info.loadLabel(LocalContext.current.packageManager).toString(),fontSize=13.sp)
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Button(onClick={launch(vm.packs.preview(info))}){Text("Preview & apply")}
                                vm.packs.configuration(info)?.let { intent -> OutlinedButton(onClick={launch(intent)}){Text("Locations")}}
                            }
                        }
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick={open(p.definition.url)}){Text("Get original pack ↗")}
                            if(p.definition.legacyUrl.isNotEmpty())TextButton(onClick={open(p.definition.legacyUrl)}){Text("linuxct ↗")}
                        }
                    }
                }
            }
            item { Text("The documented collection contains Earth, Mars, Saturn, Moon, Snow Mountain and Geometry. Sun is a DepthWall original. No root or AOD module is installed by this app.",fontSize=12.sp,color=Muted) }
        }
    }
}
@Composable private fun LibraryScreen(items:List<WallpaperItem>,onSelect:(WallpaperItem)->Unit,onFavorite:(WallpaperItem)->Unit,onSettings:()->Unit,onImport:()->Unit) {
    var filter by rememberSaveable { mutableStateOf("Favourites") }
    val chosen=items.filter { when(filter){"Favourites"->it.favorite;"Downloads"->it.localPath.isNotBlank()&&!it.id.startsWith("import");"Imports"->it.category=="Imports"||it.category=="My depth";else->true} }
    Column {
        Column(Modifier.padding(horizontal=20.dp)) {
            Text("Yours, always.",style=MaterialTheme.typography.headlineMedium)
            Text("${items.count { it.favorite }} favourites · ${items.count { it.localPath.isNotBlank() }} saved locally",Modifier.padding(top=8.dp,bottom=14.dp),color=Muted,fontSize=13.sp)
            ChoiceRow(listOf("Favourites","Downloads","Imports","All"),filter){filter=it}
            Row { TextButton(onClick=onImport){Icon(Icons.Outlined.Add,null);Text("Import photo")};TextButton(onClick=onSettings){Icon(Icons.Outlined.Tune,null);Text("Preferences")} }
        }
        CollectionScreen("","",chosen,onSelect,onFavorite,"library_grid")
    }
}
