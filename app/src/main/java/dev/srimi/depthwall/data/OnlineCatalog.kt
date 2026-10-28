package dev.srimi.depthwall.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class WallpaperSource(val id: String, val label: String, val description: String, val website: String)

object WallpaperSources {
    val sources = listOf(
        WallpaperSource("NASA", "NASA", "NASA Image and Video Library. Original images with source credits.", "https://images.nasa.gov/"),
        WallpaperSource("ESA", "ESA · Commons", "ESA-related, freely licensed images on Wikimedia Commons. This is a Commons search, not an official ESA feed.", "https://www.esa.int/ESA_Multimedia/Images"),
        WallpaperSource("ISRO", "ISRO · Commons", "ISRO-related, freely licensed images on Wikimedia Commons. Availability depends on contributors and image licenses.", "https://www.isro.gov.in/"),
        WallpaperSource("JAXA", "JAXA · Commons", "JAXA-related, freely licensed images on Wikimedia Commons, with credits retained.", "https://global.jaxa.jp/"),
        WallpaperSource("CSA", "CSA · Commons", "Canadian Space Agency-related, freely licensed images on Wikimedia Commons, with credits retained.", "https://www.asc-csa.gc.ca/eng/"),
        WallpaperSource("Wikimedia", "Wikimedia", "Freely licensed photography, landscapes and space imagery. Original files and attribution stay attached.", "https://commons.wikimedia.org/"),
        WallpaperSource("Wallhaven", "Wallhaven", "Search the public safe-for-work wallpaper collection for space, cars, mountains and themes. Creator rights still apply.", "https://wallhaven.cc/")
    )

    fun find(id: String): WallpaperSource? = sources.firstOrNull { it.id == id }
}

data class OnlinePage(val items: List<WallpaperItem>, val nextCursor: String? = null)

/** Public catalogs have no app-imposed download quota. Provider rate limits still apply. */
class OnlineCatalog(private val http: OkHttpClient) {
    suspend fun search(provider: String, query: String, cursor: String? = null, only4k: Boolean = false): OnlinePage = withContext(Dispatchers.IO) {
        require(WallpaperSources.find(provider) != null) { "Choose a supported wallpaper source." }
        when (provider) {
            "NASA" -> {
                val page = pageNumber(cursor)
                val url = "https://images-api.nasa.gov/search".toHttpUrl().newBuilder()
                    .addQueryParameter("media_type", "image")
                    .addQueryParameter("page_size", PAGE_SIZE.toString())
                    .addQueryParameter("page", page.toString())
                    .addQueryParameter("q", query.trim().ifBlank { "nebula" }).build()
                parseNasa(json(url), page, only4k)
            }
            "Wallhaven" -> {
                val page = pageNumber(cursor)
                val url = "https://wallhaven.cc/api/v1/search".toHttpUrl().newBuilder()
                    .addQueryParameter("q", query.trim().ifBlank { "space" })
                    .addQueryParameter("purity", "100")
                    .addQueryParameter("categories", "111")
                    .addQueryParameter("sorting", "relevance")
                    .addQueryParameter("order", "desc")
                    .addQueryParameter("page", page.toString()).build()
                // Filter locally so portrait 2160 × 3840 originals are not excluded by a landscape-only API minimum.
                parseWallhaven(json(url), query, page, only4k)
            }
            else -> {
                val url = "https://commons.wikimedia.org/w/api.php".toHttpUrl().newBuilder()
                    .addQueryParameter("action", "query")
                    .addQueryParameter("format", "json")
                    .addQueryParameter("generator", "search")
                    .addQueryParameter("gsrsearch", commonsQuery(provider, query))
                    .addQueryParameter("gsrnamespace", "6")
                    .addQueryParameter("gsrlimit", PAGE_SIZE.toString())
                    .addQueryParameter("prop", "imageinfo")
                    .addQueryParameter("iiprop", "url|extmetadata|size|mime")
                    .addQueryParameter("iiextmetadatafilter", "Artist|LicenseShortName|LicenseUrl|Credit")
                    .addQueryParameter("iiurlwidth", "600")
                if (cursor != null) {
                    val token = runCatching { JSONObject(cursor) }.getOrElse { throw IllegalArgumentException("Start a new search to continue this collection.") }
                    require(token.has("gsroffset") && token.optInt("gsroffset", -1) >= 0) { "Invalid collection page. Start a new search." }
                    url.addQueryParameter("gsroffset", token.getInt("gsroffset").toString())
                    token.optString("continue").takeIf { it.isNotBlank() }?.let { url.addQueryParameter("continue", it) }
                }
                parseCommons(json(url.build()), provider, only4k)
            }
        }
    }

    /** Older saved NASA results only contain a thumbnail. Resolve them through NASA's asset API. */
    suspend fun resolveDownload(item: WallpaperItem): WallpaperItem = withContext(Dispatchers.IO) {
        if (!item.id.startsWith("nasa_") || (item.width > 0 && item.height > 0)) return@withContext item
        val id = item.id.removePrefix("nasa_")
        val url = "https://images-api.nasa.gov/asset/".toHttpUrl().newBuilder().addPathSegment(id).build()
        val images = json(url).getJSONObject("collection").optJSONArray("items") ?: JSONArray()
        val best = (0 until images.length()).mapNotNull { index ->
            nasaImageUrl(images.optJSONObject(index)?.optString("href").orEmpty())
        }.minByOrNull { url ->
            when {
                "~orig." in url -> 0
                "~large." in url -> 1
                "~medium." in url -> 2
                "~small." in url -> 3
                "~thumb." in url -> 5
                else -> 4
            }
        } ?: throw IOException("NASA has no supported image download for this item.")
        item.copy(remoteUrl = best, bytes = 0)
    }

    private fun json(url: HttpUrl): JSONObject {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Accept", "application/json").build()
        return http.newCall(request).execute().use { response ->
            if (response.code == 429) {
                val retry = response.header("Retry-After")?.take(80)?.let { " Retry after $it." }.orEmpty()
                throw IOException("This source is temporarily rate limited.$retry Your downloads have no DepthWall quota; try again later.")
            }
            if (!response.isSuccessful) throw IOException("This collection is unavailable (${response.code}). Try again later or choose another source.")
            val body = response.body ?: throw IOException("This source returned an empty response.")
            val source = body.source()
            source.request(MAX_JSON_BYTES + 1)
            require(source.buffer.size <= MAX_JSON_BYTES) { "The source returned an oversized response." }
            JSONObject(source.readUtf8()).also { data ->
                if (data.has("error")) throw IOException("This source could not complete the search. Try another query or retry later.")
            }
        }
    }

    companion object {
        const val USER_AGENT = "DepthWall/1.1 (personal wallpaper app; https://github.com/Srimi1/depthwall)"
        private const val PAGE_SIZE = 30
        private const val MAX_JSON_BYTES = 4L * 1024 * 1024
        private const val MAX_IMAGE_BYTES = 64L * 1024 * 1024
        private const val MAX_PIXELS = 100_000_000L

        fun is4k(width: Int, height: Int): Boolean = maxOf(width, height) >= 3840 && minOf(width, height) >= 2160

        private fun pageNumber(cursor: String?): Int = if (cursor == null) 1 else
            cursor.toIntOrNull()?.takeIf { it > 0 } ?: throw IllegalArgumentException("Invalid collection page. Start a new search.")

        internal fun commonsQuery(provider: String, query: String): String {
            val agency = when (provider) {
                "ESA" -> "(ESA OR \"European Space Agency\")"
                "ISRO" -> "(ISRO OR \"Indian Space Research Organisation\")"
                "JAXA" -> "(JAXA OR \"Japan Aerospace Exploration Agency\")"
                "CSA" -> "\"Canadian Space Agency\""
                else -> ""
            }
            val term = query.trim().ifBlank {
                when (provider) { "ESA" -> "nebula"; "ISRO" -> "moon"; "JAXA" -> "earth"; "CSA" -> "space"; else -> "landscape" }
            }
            return listOf(agency, term, "filetype:bitmap").filter { it.isNotBlank() }.joinToString(" ")
        }

        internal fun parseNasa(data: JSONObject, page: Int, only4k: Boolean): OnlinePage {
            val collection = data.getJSONObject("collection")
            val rows = collection.optJSONArray("items") ?: JSONArray()
            val items = (0 until rows.length()).mapNotNull { index -> runCatching {
                val row = rows.getJSONObject(index)
                val info = row.getJSONArray("data").getJSONObject(0)
                val id = info.getString("nasa_id")
                val links = row.optJSONArray("links") ?: return@runCatching null
                val images = (0 until links.length()).mapNotNull { links.optJSONObject(it) }.filter {
                    it.optString("render") == "image" && nasaImageUrl(it.optString("href")) != null &&
                        fitsImageLimits(it.optInt("width"), it.optInt("height"), it.optLong("size"))
                }
                // NASA sometimes omits dimensions on the canonical original while reporting them
                // on smaller previews. Missing metadata must never make a thumbnail the download.
                val best = images.maxWithOrNull(compareBy<JSONObject> {
                    if (it.optString("rel") == "canonical" || "~orig." in it.optString("href")) 1 else 0
                }.thenBy { it.optInt("width").toLong() * it.optInt("height") }) ?: return@runCatching null
                val width = best.optInt("width")
                val height = best.optInt("height")
                if (only4k && !is4k(width, height)) return@runCatching null
                val thumb = images.minWithOrNull(compareBy<JSONObject> {
                    when {
                        it.optString("rel") == "preview" || "~thumb." in it.optString("href") -> 0
                        it.optInt("width") > 0 -> 1
                        else -> 2
                    }
                }.thenBy { kotlin.math.abs(it.optInt("width") - 600) }) ?: best
                WallpaperItem(
                    id = "nasa_$id", title = info.getString("title"), category = "NASA",
                    author = info.optString("photographer").ifBlank { info.optString("secondary_creator").ifBlank { info.optString("center", "NASA") } },
                    license = "NASA media usage guidelines; retain source credits and check third-party rights.",
                    sourceUrl = "https://images.nasa.gov/details/".toHttpUrl().newBuilder().addPathSegment(id).build().toString(),
                    remoteUrl = nasaImageUrl(best.getString("href"))!!, thumbnail = nasaImageUrl(thumb.getString("href"))!!,
                    bytes = best.optLong("size"), width = width, height = height
                )
            }.getOrNull() }.distinctBy { it.id }
            val links = collection.optJSONArray("links") ?: JSONArray()
            val hasNext = (0 until links.length()).any { links.optJSONObject(it)?.optString("rel") == "next" }
            return OnlinePage(items, if (hasNext) (page + 1).toString() else null)
        }

        internal fun parseCommons(data: JSONObject, provider: String, only4k: Boolean): OnlinePage {
            val pages = data.optJSONObject("query")?.optJSONObject("pages") ?: JSONObject()
            val items = pages.keys().asSequence().mapNotNull { key -> pages.optJSONObject(key) }
                .sortedBy { it.optInt("index", Int.MAX_VALUE) }.mapNotNull { page -> runCatching {
                    val info = page.getJSONArray("imageinfo").getJSONObject(0)
                    val width = info.optInt("width"); val height = info.optInt("height"); val bytes = info.optLong("size")
                    if (!fitsImageLimits(width, height, bytes) || (only4k && !is4k(width, height))) return@runCatching null
                    if (info.optString("mime") !in listOf("image/jpeg", "image/png", "image/webp")) return@runCatching null
                    val metadata = info.optJSONObject("extmetadata") ?: JSONObject()
                    fun meta(name: String) = plainText(metadata.optJSONObject(name)?.optString("value").orEmpty())
                    val license = meta("LicenseShortName")
                    if (!license.startsWith("CC ", true) && !license.startsWith("CC0", true) && !license.contains("public domain", true)) return@runCatching null
                    val original = commonsImageUrl(info.getString("url")) ?: return@runCatching null
                    val thumbnail = commonsImageUrl(info.optString("thumburl")) ?: original
                    val source = info.optString("descriptionurl").toHttpUrlOrNull()?.takeIf { it.isHttps && it.host == "commons.wikimedia.org" } ?: return@runCatching null
                    WallpaperItem(
                        id = "commons_${page.getLong("pageid")}", title = plainText(page.getString("title").removePrefix("File:").substringBeforeLast('.')),
                        category = if (provider == "Wikimedia") "Wikimedia" else "$provider · Commons",
                        author = meta("Artist").ifBlank { meta("Credit").ifBlank { "See source credits" } },
                        license = listOf(license, meta("LicenseUrl")).filter { it.isNotBlank() }.joinToString(" · "),
                        sourceUrl = source.toString(), remoteUrl = original, thumbnail = thumbnail,
                        bytes = bytes, width = width, height = height
                    )
                }.getOrNull() }.toList().distinctBy { it.id }
            val continuation = data.optJSONObject("continue")?.takeIf { it.has("gsroffset") }
            return OnlinePage(items, continuation?.toString())
        }

        internal fun parseWallhaven(data: JSONObject, query: String, page: Int, only4k: Boolean): OnlinePage {
            val rows = data.optJSONArray("data") ?: JSONArray()
            val items = (0 until rows.length()).mapNotNull { index -> runCatching {
                val row = rows.getJSONObject(index)
                if (row.optString("purity") != "sfw") return@runCatching null
                val width = row.optInt("dimension_x"); val height = row.optInt("dimension_y"); val bytes = row.optLong("file_size")
                if (!fitsImageLimits(width, height, bytes) || (only4k && !is4k(width, height))) return@runCatching null
                if (row.optString("file_type") !in listOf("image/jpeg", "image/png", "image/webp")) return@runCatching null
                val original = row.getString("path").toHttpUrlOrNull()?.takeIf { it.isHttps && it.host == "w.wallhaven.cc" } ?: return@runCatching null
                val thumb = row.optJSONObject("thumbs")?.optString("large")?.toHttpUrlOrNull()?.takeIf { it.isHttps && it.host == "th.wallhaven.cc" }
                val source = row.getString("url").toHttpUrlOrNull()?.takeIf { it.isHttps && it.host == "wallhaven.cc" } ?: return@runCatching null
                val id = row.getString("id")
                WallpaperItem(
                    id = "wallhaven_$id", title = "${query.trim().ifBlank { "Space" }} · $id", category = "Wallhaven",
                    author = row.optString("source").ifBlank { "Creator credits on the source page" },
                    license = "Creator retains rights. Review the wallpaper source and creator terms before reuse.",
                    sourceUrl = source.toString(), remoteUrl = original.toString(), thumbnail = thumb?.toString() ?: original.toString(),
                    bytes = bytes, width = width, height = height
                )
            }.getOrNull() }.distinctBy { it.id }
            val lastPage = data.optJSONObject("meta")?.optInt("last_page", page) ?: page
            return OnlinePage(items, if (page < lastPage) (page + 1).toString() else null)
        }

        private fun fitsImageLimits(width: Int, height: Int, bytes: Long): Boolean =
            width >= 0 && height >= 0 && bytes in 0..MAX_IMAGE_BYTES && width.toLong() * height <= MAX_PIXELS

        private fun nasaImageUrl(value: String): String? {
            val url = value.toHttpUrlOrNull() ?: return null
            if (url.host != "nasa.gov" && !url.host.endsWith(".nasa.gov")) return null
            if (url.encodedPath.substringAfterLast('.').lowercase() !in listOf("jpg", "jpeg", "png", "webp")) return null
            return url.newBuilder().scheme("https").build().toString()
        }

        private fun commonsImageUrl(value: String): String? = value.toHttpUrlOrNull()
            ?.takeIf { it.isHttps && it.host in listOf("upload.wikimedia.org", "thumb.wikimedia.org") }?.toString()

        private fun plainText(value: String): String = value.replace(Regex("<[^>]*>"), "")
            .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").trim().take(4000)
    }
}
