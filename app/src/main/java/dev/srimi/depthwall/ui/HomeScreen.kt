package dev.srimi.depthwall.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.srimi.depthwall.data.WallpaperItem

/** Tab order shared by the navigation bar, the header and the "See all" links. */
object Tabs { const val HOME = 0; const val LIVE = 1; const val BROWSE = 2; const val DEPTH = 3; const val LIBRARY = 4 }

/** Badge text for a card: live scenes, layered depth and the phone collection stand out. */
fun cardBadge(item: WallpaperItem): String? = when {
    item.kind == "NATIVE_SCENE" -> "LIVE"
    item.kind == "LAYERED_DEPTH" -> "DEPTH"
    item.id.startsWith("device_") -> "NEW"
    else -> null
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable fun HomeScreen(items: List<WallpaperItem>, picks: List<WallpaperItem>, onSelect: (WallpaperItem) -> Unit, onOpenTab: (Int) -> Unit) {
    val scenes = items.filter { it.kind == "NATIVE_SCENE" }
    val papaya = items.filter { it.category == "Papaya Edition" }
    val space = items.filter { it.kind == "STATIC" && it.category == "Space" && it.isLocal }
    val depth = items.filter { it.kind == "LAYERED_DEPTH" }
    val hero = picks.filter { it.id.startsWith("device_") }.ifEmpty { picks }.take(5)
    LazyColumn(Modifier.fillMaxSize().testTag("home"), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
        if (hero.isNotEmpty()) item(key = "hero") {
            val pager = rememberPagerState { hero.size }
            Column {
                HorizontalPager(pager, contentPadding = PaddingValues(horizontal = 20.dp), pageSpacing = 12.dp, modifier = Modifier.testTag("home_hero")) { page ->
                    HeroCard(hero[page]) { onSelect(hero[page]) }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
                    repeat(hero.size) { index ->
                        Box(Modifier.padding(horizontal = 3.dp).size(if (index == pager.currentPage) 18.dp else 6.dp, 6.dp)
                            .clip(CircleShape).background(if (index == pager.currentPage) Orange else Hairline))
                    }
                }
            }
        }
        if (picks.isNotEmpty()) item(key = "picks") {
            Shelf("Best for your phone", "Tuned for the 7T Pro's 1440 × 3120 AMOLED panel", picks, "pick", onSelect, rowTag = "device_picks")
        }
        if (scenes.isNotEmpty()) item(key = "live") {
            Shelf("Living worlds", "Built-in live wallpapers that move with you", scenes, "live", onSelect, width = 168.dp) { onOpenTab(Tabs.LIVE) }
        }
        if (papaya.isNotEmpty()) item(key = "papaya") {
            Shelf("Papaya Edition", "Papaya, carbon and true black", papaya, "papaya", onSelect)
        }
        if (space.isNotEmpty()) item(key = "space") {
            Shelf("Into space", "Planets, orbits and quiet starfields", space, "space", onSelect) { onOpenTab(Tabs.BROWSE) }
        }
        if (depth.isNotEmpty()) item(key = "depth") {
            Shelf("Depth", "Layered scenes with parallax and an optional clock", depth, "depth", onSelect) { onOpenTab(Tabs.DEPTH) }
        }
        item(key = "browse") {
            Card(onClick = { onOpenTab(Tabs.BROWSE) }, colors = CardDefaults.cardColors(containerColor = Raised), shape = RoundedCornerShape(24.dp),
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().testTag("home_browse")) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Explore, null, Modifier.size(28.dp), tint = Orange)
                    Column(Modifier.padding(start = 16.dp).weight(1f)) {
                        Text("Browse everything", style = MaterialTheme.typography.titleMedium)
                        Text("${items.count { it.kind == "STATIC" && it.isLocal }} offline wallpapers, NASA, Wikimedia, Wallhaven and website import", color = Muted, fontSize = 12.sp)
                    }
                    Icon(Icons.Outlined.ChevronRight, null, tint = Muted)
                }
            }
        }
    }
}

@Composable private fun HeroCard(item: WallpaperItem, onOpen: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(440.dp).clip(RoundedCornerShape(30.dp)).background(Raised).clickable(onClick = onOpen).testTag("hero_${item.id}")) {
        AsyncImage(item.imageModel, item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, .55f to Color.Transparent, 1f to Color.Black.copy(.92f))))
        BadgeLabel("MADE FOR 7T PRO", Modifier.align(Alignment.TopStart).padding(18.dp))
        Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) {
            Text(item.title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (item.kind == "NATIVE_SCENE") "Live wallpaper" else "${item.category} · 1440 × 3120 ready", color = Ivory.copy(.75f), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            Button(onClick = onOpen, modifier = Modifier.padding(top = 14.dp), contentPadding = PaddingValues(horizontal = 20.dp)) {
                Icon(Icons.Outlined.Wallpaper, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Preview")
            }
        }
    }
}

/** A titled horizontal row of poster cards; [onSeeAll] adds a link to the full tab. */
@Composable fun Shelf(title: String, subtitle: String, items: List<WallpaperItem>, tagPrefix: String, onSelect: (WallpaperItem) -> Unit,
    width: Dp = 140.dp, rowTag: String = "${tagPrefix}_row", onSeeAll: (() -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(subtitle, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (onSeeAll != null) TextButton(onClick = onSeeAll) { Text("See all", fontSize = 13.sp) }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().testTag(rowTag)) {
            items(items, key = { it.id }) { item -> PosterCard(item, { onSelect(item) }, Modifier.width(width), "${tagPrefix}_${item.id}") }
        }
    }
}

/** Full-bleed artwork with the title on the image, used on Home and Live. */
@Composable fun PosterCard(item: WallpaperItem, onSelect: () -> Unit, modifier: Modifier = Modifier, tag: String = "poster_${item.id}") {
    val context = LocalContext.current
    val thumbnail = remember(item.thumbnailModel) {
        coil.request.ImageRequest.Builder(context).data(item.thumbnailModel).size(480, 780).build()
    }
    Box(modifier.aspectRatio(.6f).clip(RoundedCornerShape(22.dp)).background(Raised).clickable(onClick = onSelect).testTag(tag)) {
        AsyncImage(thumbnail, item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, .6f to Color.Transparent, 1f to Color.Black.copy(.88f))))
        cardBadge(item)?.let { BadgeLabel(it, Modifier.align(Alignment.TopStart).padding(10.dp)) }
        if (item.favorite) Icon(Icons.Outlined.Favorite, "Favourite", Modifier.align(Alignment.TopEnd).padding(12.dp).size(16.dp), tint = Orange)
        Text(item.title, Modifier.align(Alignment.BottomStart).padding(12.dp), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
