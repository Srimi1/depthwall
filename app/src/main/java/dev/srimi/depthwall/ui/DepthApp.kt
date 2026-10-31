package dev.srimi.depthwall.ui

import android.app.Activity
import android.app.WallpaperInfo
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import dev.srimi.depthwall.BuildConfig
import dev.srimi.depthwall.R
import dev.srimi.depthwall.data.*
import dev.srimi.depthwall.wallpaper.DepthWallpaperService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DepthApp(vm:MainViewModel,initialSettingsOpen:Boolean=false) {
    val context=LocalContext.current
    val items by vm.wallpapers.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val installed by vm.installed.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val picks by vm.devicePicks.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selected by remember { vm.selected }
    var settingsOpen by rememberSaveable { mutableStateOf(initialSettingsOpen) }
    var report by remember { mutableStateOf<ApkInspection?>(null) }
    val snackbar=remember { SnackbarHostState() }
    var applied by remember { mutableStateOf(WallpaperApplyState.capture(context)) }
    var restart by remember { mutableStateOf<WallpaperInfo?>(null) }
    var sharedProcessWarning by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    val owner=LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_RESUME) { vm.refreshPacks();applied=WallpaperApplyState.capture(context) } }
        owner.lifecycle.addObserver(observer);onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val importImage=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.run("Image added to your library") { selected=vm.repo.importImage(it);tab=Tabs.LIBRARY } }
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
    val applyPortResult=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        applied=WallpaperApplyState.capture(context)
        if(applied?.let(WallpaperRestart::sharesPortProcess)==true) sharedProcessWarning=true
    }
    fun launch(intent:Intent) { runCatching { context.startActivity(intent) }.onFailure { vm.message("This action is unavailable: ${it.message}") } }
    fun applyPort(info:WallpaperInfo) { runCatching { applyPortResult.launch(vm.packs.preview(info)) }.onFailure { vm.message("This action is unavailable: ${it.message}") } }
    fun openLink(url:String)=launch(Intent(Intent.ACTION_VIEW,Uri.parse(url)))
    Scaffold(containerColor=Ink,snackbarHost={SnackbarHost(snackbar)},bottomBar={
        NavigationBar(containerColor=Color(0xFF0A0A0A),tonalElevation=0.dp) {
            val icons=listOf(Icons.Outlined.Home,Icons.Outlined.AutoAwesome,Icons.Outlined.Explore,Icons.Outlined.Layers,Icons.Outlined.CollectionsBookmark)
            listOf("Home","Live","Browse","Depth","Library").forEachIndexed { i,label ->
                NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(icons[i],label)},label={Text(label,fontSize=11.sp,fontWeight=if(tab==i)FontWeight.SemiBold else FontWeight.Normal)},colors=NavigationBarItemDefaults.colors(selectedIconColor=Orange,selectedTextColor=Ivory,unselectedIconColor=Muted,unselectedTextColor=Muted,indicatorColor=MaterialTheme.colorScheme.primaryContainer),modifier=Modifier.testTag("tab_$i"))
            }
        }
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.padding(start=20.dp,end=8.dp,top=12.dp,bottom=14.dp).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.depthwall_logo),"DepthWall logo",Modifier.size(22.dp))
                        Text("DEPTHWALL",Modifier.padding(start=7.dp),fontSize=11.sp,letterSpacing=2.4.sp,fontWeight=FontWeight.Bold,color=Muted)
                        Text("v${BuildConfig.VERSION_NAME}",Modifier.padding(start=8.dp).clip(RoundedCornerShape(30.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal=8.dp,vertical=3.dp).testTag("app_version"),color=Orange,fontSize=10.sp,fontWeight=FontWeight.Bold)
                    }
                    Text(when(tab){Tabs.HOME->"Made for your screen";Tabs.LIVE->"Live wallpapers";Tabs.BROWSE->"Browse";Tabs.DEPTH->"Depth";else->"Your library"},
                        Modifier.padding(top=6.dp),style=MaterialTheme.typography.displaySmall,maxLines=1)
                }
                IconButton(onClick={importImage.launch(arrayOf("image/*"))}) { Icon(Icons.Outlined.AddPhotoAlternate,"Import image",tint=Ivory) }
                IconButton(onClick={settingsOpen=true},modifier=Modifier.testTag("settings")) { Icon(Icons.Outlined.Tune,"Settings") }
            }
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth(),color=Orange)
            Box(Modifier.weight(1f)) {
                when(tab) {
                    Tabs.HOME -> HomeScreen(items,picks,{selected=it}) { tab=it }
                    Tabs.LIVE -> LiveScreen(items,installed,{selected=it},::openLink,::launch,{importApk.launch(arrayOf("application/vnd.android.package-archive","application/octet-stream"))},vm,applied,::applyPort) { restart=it }
                    Tabs.BROWSE -> ExploreScreen(vm,items,{selected=it})
                    Tabs.DEPTH -> CollectionScreen("","Layered landscapes with subtle parallax and an optional clock. Make your own in the preview with Import layer or Edit mask.",items.filter { it.kind=="LAYERED_DEPTH" },{selected=it},{vm.favorite(it)},"depth_grid")
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
    if(settingsOpen)SettingsDialog(items,vm,{settingsOpen=false})
    restart?.let { info -> RestartPortDialog(info,vm,::launch,{ restart=null;applyPort(it) }) { restart=null;applied=WallpaperApplyState.capture(context) } }
    if(sharedProcessWarning)AlertDialog(onDismissRequest={sharedProcessWarning=false},title={Text("Use one scene for Home and lock")},
        text={Text("Your home and lock screens now show two scenes from the same pack. Both run in one app process, and starting them together after a restart can crash it, leaving the wallpaper black or reset. Apply one scene to Home and lock, or use a DepthWall original on one screen.")},
        confirmButton={TextButton(onClick={sharedProcessWarning=false}){Text("OK")}})
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

@Composable private fun ExploreScreen(vm:MainViewModel,items:List<WallpaperItem>,onSelect:(WallpaperItem)->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("All") }
    var color by rememberSaveable { mutableStateOf("All colours") }
    var provider by rememberSaveable { mutableStateOf("Offline") }
    var topic by rememberSaveable { mutableStateOf("") }
    var only4k by rememberSaveable { mutableStateOf(false) }
    var online by remember { mutableStateOf<List<WallpaperItem>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }
    var nextCursor by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var failedAppend by remember { mutableStateOf(false) }
    var generation by remember { mutableIntStateOf(0) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var websiteImport by rememberSaveable { mutableStateOf(false) }
    val sources=WallpaperSources.sources
    val sourceInfo=sources.firstOrNull { it.id==provider }

    fun resetOnline() {
        generation++
        searchJob?.cancel()
        online=emptyList();nextCursor=null;searched=false;loading=false;failure=null
    }
    fun searchOnline(append:Boolean=false) {
        if(loading || provider=="Offline")return
        val cursor=if(append)nextCursor ?: return else null
        val requestedProvider=provider;val requestedQuery=query;val requested4k=only4k
        val request=++generation
        loading=true;failure=null;failedAppend=append
        searchJob=scope.launch {
            try {
                val page=vm.repo.onlinePage(requestedProvider,requestedQuery,cursor,requested4k)
                if(request==generation) {
                    online=(if(append)online+page.items else page.items).distinctBy { it.id }
                    nextCursor=page.nextCursor;searched=true
                }
            } catch(cancelled:CancellationException) { throw cancelled }
            catch(error:Exception) {
                if(request==generation)failure=error.message ?: "This source is unavailable. Please try again."
            } finally { if(request==generation)loading=false }
        }
    }
    fun openWebsite(url:String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url))) }
            .onFailure { vm.message("No browser is available to open this website.") }
    }
    val source=if(provider=="Offline")items.filter { it.kind=="STATIC" && it.isLocal }
        else online.map { remote -> items.firstOrNull { it.id==remote.id } ?: remote }
    val filtered=remember(source,query,category,color,provider) {
        source.filter { (provider!="Offline" || it.title.contains(query,true) || it.category.contains(query,true)) &&
            (provider!="Offline" || category=="All" || it.category==category) &&
            (provider!="Offline" || color=="All colours" || it.color==color) }
    }
    LazyVerticalGrid(columns=GridCells.Fixed(2),contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=24.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(18.dp),modifier=Modifier.testTag("explore_grid")) {
        item(span={GridItemSpan(2)}) {
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("Space, open roads and AMOLED colour, offline or from NASA, Wikimedia and Wallhaven.",color=Muted,fontSize=13.sp)
                ChoiceRow(listOf("Space","Cars","Themes","Alps"),topic) { selectedTopic ->
                    resetOnline();topic=selectedTopic
                    when(selectedTopic) {
                        "Space" -> { if(provider=="Offline" || provider=="Wallhaven")provider="NASA";query="nebula" }
                        "Cars" -> { provider="Wallhaven";query="McLaren" }
                        "Themes" -> { provider="Wallhaven";query="abstract" }
                        else -> { provider="Wikimedia";query="Alps landscape" }
                    }
                    category="All";color="All colours"
                    searchOnline()
                }
                OutlinedTextField(query,{resetOnline();query=it;topic=""},placeholder={Text(if(provider=="Offline")"Search your collection" else "Search ${sourceInfo?.label ?: provider}")},leadingIcon={Icon(Icons.Outlined.Search,null)},singleLine=true,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().testTag("search"),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={searchOnline()}))
                ChoiceRow(listOf("Offline")+sources.map { it.label },sourceInfo?.label ?: "Offline") { label ->
                    resetOnline();provider=sources.firstOrNull { it.label==label }?.id ?: "Offline"
                    category="All";color="All colours"
                }
                if(provider=="Offline") {
                    Text("${source.size} wallpapers available offline",color=Muted,fontSize=11.sp)
                    ChoiceRow(listOf("All","Papaya Edition","Space","Cars","Themes","Nature","Abstract","Minimal","Geometry","Orange / Black"),category){category=it}
                    ChoiceRow(listOf("All colours","Orange","Blue","Green","Purple","Gold","Red","White"),color){color=it}
                } else {
                    Card(colors=CardDefaults.cardColors(containerColor=Surface),shape=RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                            Text(sourceInfo?.label ?: provider,style=MaterialTheme.typography.titleSmall)
                            Text(sourceInfo?.description.orEmpty(),color=Muted,fontSize=12.sp)
                            Text("No daily download quota or subscription in DepthWall. Source availability and rate limits still apply. Credits stay with saved images.",color=Muted,fontSize=12.sp)
                        }
                    }
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("4K sources only",fontSize=13.sp)
                            Text("At least 3840 × 2160, either orientation",color=Muted,fontSize=11.sp)
                        }
                        Switch(only4k,{resetOnline();only4k=it},modifier=Modifier.testTag("filter_4k"))
                    }
                    Button(enabled=!loading,onClick={searchOnline()},modifier=Modifier.fillMaxWidth().testTag("find_wallpapers")) {
                        Icon(Icons.Outlined.Search,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp))
                        Text(if(loading)"Finding wallpapers…" else "Find wallpapers")
                    }
                }
                OutlinedButton(onClick={websiteImport=true},modifier=Modifier.fillMaxWidth().testTag("import_website")) {
                    Icon(Icons.Outlined.Link,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Import from a website")
                }
                Text("More wallpaper websites",style=MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "NASA" to "https://images.nasa.gov/",
                        "ESA" to "https://www.esa.int/ESA_Multimedia/Images",
                        "ESO" to "https://www.eso.org/public/images/",
                        "Hubble" to "https://esahubble.org/images/",
                        "Webb" to "https://esawebb.org/images/",
                        "Wallpaper Abyss" to "https://wall.alphacoders.com/",
                        "Wallhaven" to "https://wallhaven.cc/"
                    ).forEach { (label,url) ->
                        SuggestionChip(onClick={openWebsite(url)},label={Text(label,fontSize=11.sp)},icon={Icon(Icons.Outlined.OpenInNew,null,Modifier.size(13.dp))})
                    }
                }
                Text("Open a website, then paste an image or wallpaper-page link to import it. Some sites require a browser download first.",color=Muted,fontSize=11.sp)
            }
        }
        if(loading)item(span={GridItemSpan(2)}) { LinearProgressIndicator(Modifier.fillMaxWidth(),color=Orange) }
        if(failure!=null)item(span={GridItemSpan(2)}) {
            Card(colors=CardDefaults.cardColors(containerColor=Surface)) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("Couldn’t load ${sourceInfo?.label ?: provider}",style=MaterialTheme.typography.titleSmall)
                    Text(failure.orEmpty(),color=Muted,fontSize=12.sp)
                    TextButton(enabled=!loading,onClick={searchOnline(failedAppend)},modifier=Modifier.testTag("retry_search")){Text("Try again")}
                }
            }
        }
        items(filtered,key={it.id}) { item -> WallpaperCard(item,{onSelect(item)},{vm.favorite(item)}) }
        if(filtered.isEmpty() && !loading && failure==null)item(span={GridItemSpan(2)}) {
            EmptyState(
                if(provider!="Offline" && !searched)"A wider world awaits" else if(nextCursor!=null)"No matches on this page" else "Nothing here yet",
                if(provider!="Offline" && !searched)"Pick a collection or search for something you love."
                else if(nextCursor!=null)"Load more to keep looking, or change your search."
                else if(only4k && provider!="Offline")"Try a different search or turn off the 4K filter." else "Try another search or filter.")
        }
        if(provider!="Offline" && nextCursor!=null)item(span={GridItemSpan(2)}) {
            OutlinedButton(enabled=!loading,onClick={searchOnline(true)},modifier=Modifier.fillMaxWidth().testTag("load_more")) {
                Text(if(loading)"Loading…" else "Load more wallpapers")
            }
        }
        if(provider!="Offline" && searched && filtered.isNotEmpty() && nextCursor==null && !loading)item(span={GridItemSpan(2)}) {
            Text("${filtered.size} wallpapers · End of this search",color=Muted,fontSize=12.sp)
        }
    }
    if(websiteImport)WebsiteImportDialog(vm,{websiteImport=false}) { item -> websiteImport=false;onSelect(item) }
}

@Composable private fun WebsiteImportDialog(vm:MainViewModel,onClose:()->Unit,onSelect:(WallpaperItem)->Unit) {
    val scope=rememberCoroutineScope()
    var url by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var candidates by remember { mutableStateOf<List<WallpaperItem>>(emptyList()) }
    var job by remember { mutableStateOf<Job?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    fun findImages() {
        if(loading || url.isBlank())return
        loading=true;failure=null;candidates=emptyList()
        val request=++generation
        val requestedUrl=url.trim()
        job=scope.launch {
            try {
                val result=vm.repo.importWebImage(requestedUrl).distinctBy { it.remoteUrl }
                if(request==generation) {
                    candidates=result
                    if(candidates.isEmpty())failure="No downloadable images were found. Open the site in your browser and import its downloaded image instead."
                }
            } catch(cancelled:CancellationException) { throw cancelled }
            catch(error:Exception) { if(request==generation)failure=error.message ?: "This page could not be imported." }
            finally { if(request==generation)loading=false }
        }
    }
    AlertDialog(onDismissRequest=onClose,title={Text("Wallpaper from the web")},text={
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Paste a public HTTPS image or wallpaper-page link. Choose a photo to preview, crop and download.",color=Muted,fontSize=12.sp)
            OutlinedTextField(url,{
                generation++;job?.cancel();loading=false;failure=null;candidates=emptyList();url=it
            },label={Text("Image or page URL")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("website_url"),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={findImages()}))
            Text("Use images the site offers for download. A page may expose only a preview; the downloaded image’s actual size determines its quality.",color=Muted,fontSize=11.sp)
            if(loading)LinearProgressIndicator(Modifier.fillMaxWidth(),color=Orange)
            failure?.let { Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp) }
            if(candidates.isNotEmpty()) {
                Text("${candidates.size} images found · choose one",fontSize=12.sp)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max=280.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    items(candidates,key={it.id}) { candidate ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onSelect(candidate) }.padding(6.dp),verticalAlignment=Alignment.CenterVertically) {
                            AsyncImage(candidate.thumbnailModel,candidate.title,Modifier.size(68.dp).clip(RoundedCornerShape(9.dp)),contentScale=ContentScale.Crop)
                            Column(Modifier.weight(1f).padding(start=10.dp)) {
                                Text(candidate.title,maxLines=2,overflow=TextOverflow.Ellipsis,fontSize=13.sp)
                                Text(if(candidate.width>0 && candidate.height>0)"${candidate.width} × ${candidate.height}" else "Size checked on download",color=Muted,fontSize=11.sp)
                            }
                        }
                    }
                }
            }
        }
    },confirmButton={TextButton(enabled=!loading && url.isNotBlank(),onClick={findImages()}){Text(if(failure!=null)"Try again" else "Find images")}},dismissButton={TextButton(onClick=onClose){Text("Close")}})
}

@Composable fun ChoiceRow(options:List<String>,selected:String,onSelect:(String)->Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        options.forEach { option -> FilterChip(selected=selected==option,onClick={onSelect(option)},label={Text(option,fontSize=12.sp)},shape=RoundedCornerShape(30.dp)) }
    }
}
@Composable fun BadgeLabel(label:String,modifier:Modifier=Modifier) {
    Text(label,modifier.clip(RoundedCornerShape(30.dp)).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha=.85f)).padding(horizontal=11.dp,vertical=7.dp),color=Orange,fontSize=8.sp,letterSpacing=1.sp,fontWeight=FontWeight.Bold)
}
@Composable fun WallpaperCard(item:WallpaperItem,onSelect:()->Unit,onFavorite:()->Unit,modifier:Modifier=Modifier,tag:String="wall_${item.id}") {
    val context = LocalContext.current
    val thumbnail = remember(item.thumbnailModel) {
        coil.request.ImageRequest.Builder(context).data(item.thumbnailModel).size(480, 720).build()
    }
    Column(modifier.clickable(onClick=onSelect).testTag(tag)) {
        Box(Modifier.fillMaxWidth().aspectRatio(.66f).clip(RoundedCornerShape(22.dp)).background(Raised)) {
            AsyncImage(thumbnail,item.title,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            IconButton(onClick=onFavorite,modifier=Modifier.align(Alignment.TopEnd)) { Icon(if(item.favorite)Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,"Favorite ${item.title}",tint=if(item.favorite)Orange else Ivory,modifier=Modifier.size(19.dp)) }
            val badge=cardBadge(item) ?: if(maxOf(item.width,item.height)>=3840 && minOf(item.width,item.height)>=2160)"4K SOURCE" else null
            badge?.let { BadgeLabel(it,Modifier.align(Alignment.BottomStart).padding(9.dp)) }
            if(item.localPath.isNotEmpty())Icon(Icons.Outlined.OfflinePin,"Available offline",Modifier.align(Alignment.BottomEnd).padding(10.dp).size(16.dp),tint=Ivory)
        }
        Text(item.title,Modifier.padding(top=9.dp),fontWeight=FontWeight.Medium,fontSize=14.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
        Text(if(item.width>0 && item.height>0)"${item.category} · ${item.width} × ${item.height}" else item.category,Modifier.padding(top=2.dp),color=Muted,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
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
@Composable private fun LiveScreen(items:List<WallpaperItem>,packs:List<InstalledPack>,onSelect:(WallpaperItem)->Unit,open:(String)->Unit,launch:(Intent)->Unit,onImport:()->Unit,vm:MainViewModel,
    applied:WallpaperApplyState?,apply:(WallpaperInfo)->Unit,onRestart:(WallpaperInfo)->Unit) {
    // DepthWall's own scenes are built in. Xiaomi/HyperOS ports are separate apps, so only the
    // installed ones are listed; the rest stay in an optional, collapsed section.
    var showPacks by rememberSaveable { mutableStateOf(false) }
    val scenes=items.filter { it.kind=="NATIVE_SCENE" }
    val installedPacks=packs.filter { it.version!=null }
    val otherPacks=packs.filter { it.version==null }
    LazyVerticalGrid(GridCells.Fixed(2),contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=28.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(14.dp),modifier=Modifier.testTag("scene_grid")) {
        item(span={GridItemSpan(2)}) {
            Text("Seven living worlds built into DepthWall. Smooth at 90 Hz, gentle on battery, and no extra apps to install.",color=Muted,fontSize=13.sp)
        }
        items(scenes,key={it.id}) { scene -> PosterCard(scene,{onSelect(scene)},Modifier.fillMaxWidth(),"wall_${scene.id}") }
        if(installedPacks.isNotEmpty())item(span={GridItemSpan(2)}) {
            Text("Your HyperOS packs",Modifier.padding(top=14.dp),style=MaterialTheme.typography.titleLarge)
        }
        items(installedPacks,key={it.definition.packageName},span={GridItemSpan(2)}) { p -> PackCard(p,items,vm,applied,onSelect,open,launch,apply,onRestart) }
        item(span={GridItemSpan(2)}) {
            Card(colors=CardDefaults.cardColors(containerColor=Raised),shape=RoundedCornerShape(24.dp),modifier=Modifier.padding(top=10.dp)) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { showPacks=!showPacks }.testTag("more_packs"),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Original Xiaomi / HyperOS packs",style=MaterialTheme.typography.titleMedium)
                            Text("Optional separate apps. The scenes above don't need them.",color=Muted,fontSize=12.sp)
                        }
                        Icon(if(showPacks)Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,if(showPacks)"Hide packs" else "Show packs",tint=Muted)
                    }
                    if(showPacks) {
                        Text("Each pack is a separate download. Rendering on your exact crDroid build is unverified, and Earth and Mars can crash; the matching DepthWall scene is the reliable choice.",color=Muted,fontSize=12.sp)
                        otherPacks.forEach { p ->
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Text(p.definition.title,Modifier.weight(1f),fontSize=14.sp)
                                TextButton(onClick={open(p.definition.url)}){Text("Get ↗")}
                                if(p.definition.legacyUrl.isNotEmpty())TextButton(onClick={open(p.definition.legacyUrl)}){Text("linuxct ↗")}
                            }
                        }
                        OutlinedButton(onClick=onImport,modifier=Modifier.fillMaxWidth()) { Icon(Icons.Outlined.InstallMobile,null);Spacer(Modifier.width(8.dp));Text("Inspect / install downloaded pack") }
                    }
                }
            }
        }
    }
}
@Composable private fun PackCard(p:InstalledPack,items:List<WallpaperItem>,vm:MainViewModel,applied:WallpaperApplyState?,onSelect:(WallpaperItem)->Unit,
    open:(String)->Unit,launch:(Intent)->Unit,apply:(WallpaperInfo)->Unit,onRestart:(WallpaperInfo)->Unit) {
    Card(colors=CardDefaults.cardColors(containerColor=Raised),shape=RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) { Text(p.definition.title,style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f));Text("Installed · ${p.version}",color=Muted,fontSize=12.sp) }
            vm.packs.verifiedRelease(p.definition.packageName)?.let { evidence ->
                if(p.version==evidence.getString("version") && p.signatures==listOf(evidence.getString("certificate"))) {
                    val unstable=p.definition.packageName.endsWith("earth") || p.definition.packageName.endsWith("mars")
                    if(unstable)Text("This pack showed black previews and crashes in testing. If it fails, use the DepthWall scene instead.",color=Orange,fontSize=11.sp)
                }
            }
            val nativeScene = if(p.definition.packageName.endsWith("snowmountain")) "snow" else p.definition.packageName.substringAfterLast('.')
            items.firstOrNull { it.kind=="NATIVE_SCENE" && it.scene==nativeScene }?.let { native ->
                TextButton(onClick={onSelect(native)}) { Text("Use DepthWall ${native.title} instead") }
            }
            p.error?.let { Text(it,color=Orange,fontSize=12.sp) }
            p.services.forEach { info ->
                Text(info.loadLabel(LocalContext.current.packageManager).toString(),fontSize=13.sp)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button(onClick={apply(info)}){Text("Preview & apply")}
                    vm.packs.configuration(info)?.let { intent -> OutlinedButton(onClick={launch(intent)}){Text("Locations")}}
                }
                if(applied!=null && WallpaperRestart.destinations(applied,info.component.flattenToString())!=0)
                    TextButton(onClick={onRestart(info)}) { Icon(Icons.Outlined.Refresh,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("Black or frozen? Restart it") }
            }
            TextButton(onClick={open(p.definition.url)}){Text("Pack source ↗")}
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

/** Re-applying the active wallpaper only re-sends a command, so a stuck engine is replaced first. */
@Composable private fun RestartPortDialog(info:WallpaperInfo,vm:MainViewModel,launch:(Intent)->Unit,apply:(WallpaperInfo)->Unit,onClose:()->Unit) {
    val context=LocalContext.current
    val label=remember(info) { info.loadLabel(context.packageManager).toString() }
    val busy by vm.busy.collectAsStateWithLifecycle()
    var released by remember(info) { mutableStateOf(false) }
    AlertDialog(onDismissRequest=onClose,title={Text("Restart $label")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Applying the same wallpaper again does not restart it. Android starts a fresh copy only after another wallpaper has replaced it.",fontSize=13.sp)
            Text("1. Switch to plain black",style=MaterialTheme.typography.titleSmall)
            OutlinedButton(enabled=!busy && !released,onClick={vm.run {
                released=WallpaperRestart.release(context,info.component.flattenToString())
                if(!released) vm.message("Android has not released the wallpaper yet. Please try again.")
            } },modifier=Modifier.testTag("restart_release")) { Text(if(released)"Switched to black" else "Switch to black") }
            Text("2. Force stop (recommended)",style=MaterialTheme.typography.titleSmall)
            Text("Clears a stuck wallpaper process. Tap Force stop on the next screen, then come back here.",fontSize=12.sp,color=Muted)
            OutlinedButton(enabled=released,onClick={launch(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.fromParts("package",info.packageName,null)))}) { Text("Open app info") }
            Text("3. Apply again",style=MaterialTheme.typography.titleSmall)
            Text("Choose Home and lock. Two scenes from one pack on separate screens share a process and can crash after a restart.",fontSize=12.sp,color=Muted)
            Button(enabled=released,onClick={apply(info)}) { Text("Preview & apply") }
            Text("If you close this after step 1, the screen stays black until you choose a wallpaper.",fontSize=11.sp,color=Muted)
        }
    },confirmButton={TextButton(onClick=onClose){Text("Close")}})
}
