package dev.srimi.depthwall.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.InetAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.security.MessageDigest

/** HTTPS access for user-supplied sites. DNS and every redirect are checked before connecting. */
class RemoteImageAccess(http: OkHttpClient) {
    private val client = http.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .proxy(Proxy.NO_PROXY)
        .connectionPool(ConnectionPool())
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val addresses = http.dns.lookup(hostname)
                if (addresses.isEmpty() || addresses.any { !isPublicAddress(it) })
                    throw UnknownHostException("Only public image websites are supported.")
                return addresses
            }
        })
        .addInterceptor { chain ->
            var request = chain.request().newBuilder()
                .url(validateUrl(chain.request().url.toString()))
                .header("User-Agent", "DepthWall/1.1 (+https://github.com/Srimi1/depthwall)")
                .build()
            repeat(6) {
                val response = chain.proceed(request)
                if (response.code !in listOf(301, 302, 303, 307, 308)) return@addInterceptor response
                val next = response.use {
                    val location = it.header("Location") ?: error("The website returned an incomplete redirect.")
                    validateUrl(request.url.resolve(location)?.toString() ?: error("Invalid website redirect."))
                }
                request = request.newBuilder().url(next).apply {
                    if (next.host != request.url.host) {
                        removeHeader("Authorization"); removeHeader("Cookie"); removeHeader("Proxy-Authorization")
                    }
                }.build()
            }
            error("This website redirected too many times. Try a direct image link.")
        }.build()

    /** Use for Coil too, so previews obey the same public-host and redirect policy as downloads. */
    fun safeClient(): OkHttpClient = client

    /** Caller must close the returned response. Redirect bodies are always closed here. */
    fun open(url: String): Response {
        val response = client.newCall(Request.Builder().url(validateUrl(url))
            .header("Accept", "image/avif,image/webp,image/*,text/html;q=0.8,application/xhtml+xml;q=0.8")
            .build()).execute()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            error(when (code) {
                401, 403 -> "This website requires browser access. Download there, then use Import photo."
                429 -> "This website is temporarily limiting requests. Try again later."
                else -> "The website is unavailable (HTTP $code). Try its image page or direct image link."
            })
        }
        return response
    }

    fun readLimited(response: Response, limit: Long): ByteArray {
        val body = response.body ?: error("The website returned an empty response.")
        require(body.contentLength() <= limit) { "The website page is too large to import." }
        return body.byteStream().use { input ->
            val output = ByteArrayOutputStream()
            transferLimited(input, limit) { buffer, count -> output.write(buffer, 0, count) }
            output.toByteArray()
        }
    }

    /** Checks real image signatures as well as MIME type, then streams with a hard byte bound. */
    fun copyImage(response: Response, destination: File, limit: Long = MAX_IMAGE_BYTES) {
        val body = response.body ?: error("The website returned an empty image.")
        require(body.contentLength() <= limit) { "This image is larger than ${limit / 1024 / 1024} MB. Choose a smaller download." }
        val mime = body.contentType()?.let { "${it.type}/${it.subtype}" }.orEmpty()
        require(mime.startsWith("image/") || mime.isBlank() || mime == "application/octet-stream") {
            "This link returned a web page instead of an image. Try the original image link."
        }
        require(isImageSignature(response.peekBody(32).bytes())) {
            "This file is not a supported photo. Choose a JPEG, PNG, WebP, AVIF, HEIF, GIF or BMP image."
        }
        body.byteStream().use { input ->
            destination.outputStream().use { output -> transferLimited(input, limit) { buffer, count -> output.write(buffer, 0, count) } }
        }
    }

    companion object {
        const val MAX_IMAGE_BYTES = 64L * 1024 * 1024
        const val MAX_PAGE_BYTES = 2L * 1024 * 1024

        fun validateUrl(value: String): HttpUrl {
            val url = value.trim().toHttpUrlOrNull()
                ?: throw IllegalArgumentException("Enter a complete HTTPS website or image link.")
            require(url.isHttps && url.port == 443 && url.username.isEmpty() && url.password.isEmpty()) {
                "Use a public HTTPS link without login details or a custom port."
            }
            val host = url.host.lowercase().trimEnd('.')
            require(host != "localhost" && !host.endsWith(".localhost") && !host.endsWith(".local") &&
                !host.endsWith(".internal") && !host.endsWith(".home") && ('.' in host || ':' in host)) {
                "Only public image websites are supported."
            }
            if (':' in host || host.all { it.isDigit() || it == '.' }) {
                require(isPublicAddress(InetAddress.getByName(host))) { "Only public image websites are supported." }
            }
            return url
        }

        fun isPublicAddress(address: InetAddress): Boolean {
            if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
                address.isSiteLocalAddress || address.isMulticastAddress) return false
            val bytes = address.address.map { it.toInt() and 255 }
            if (bytes.size == 4) {
                val a = bytes[0]; val b = bytes[1]; val c = bytes[2]
                return a != 0 && a != 10 && a != 127 && a < 224 &&
                    !(a == 100 && b in 64..127) && !(a == 169 && b == 254) &&
                    !(a == 172 && b in 16..31) && !(a == 192 && b == 168) &&
                    !(a == 192 && b == 0 && c in listOf(0, 2)) && !(a == 192 && b == 88 && c == 99) &&
                    !(a == 198 && b in 18..19) && !(a == 198 && b == 51 && c == 100) &&
                    !(a == 203 && b == 0 && c == 113)
            }
            // Accept global unicast only; excludes ULA, NAT64, mapped and transition addresses.
            return bytes.size == 16 && bytes[0] and 0xE0 == 0x20 &&
                !(bytes[0] == 0x20 && bytes[1] == 0x01 && bytes[2] == 0x0D && bytes[3] == 0xB8) &&
                !(bytes[0] == 0x20 && bytes[1] == 0x01 && bytes[2] == 0 && bytes[3] == 0) &&
                !(bytes[0] == 0x20 && bytes[1] == 0x02)
        }

        fun isImageSignature(bytes: ByteArray): Boolean {
            fun match(vararg signature: Int) = bytes.size >= signature.size &&
                signature.indices.all { (bytes[it].toInt() and 255) == signature[it] }
            fun text(start: Int, end: Int) = if (bytes.size >= end) String(bytes, start, end - start, Charsets.US_ASCII) else ""
            return match(0xff, 0xd8, 0xff) || match(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a) ||
                text(0, 6) in listOf("GIF87a", "GIF89a") || text(0, 2) == "BM" ||
                (text(0, 4) == "RIFF" && text(8, 12) == "WEBP") ||
                (text(4, 8) == "ftyp" && text(8, 12) in listOf("avif", "avis", "heic", "heix", "hevc", "hevx", "mif1", "msf1"))
        }

        internal fun transferLimited(input: InputStream, limit: Long, write: (ByteArray, Int) -> Unit) {
            val buffer = ByteArray(16384)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= limit) { "The file exceeds the allowed size. Choose a smaller image." }
                write(buffer, count)
            }
            require(total > 0) { "The website returned an empty file." }
        }
    }
}

class WebImageImporter(http: OkHttpClient) {
    private val access = RemoteImageAccess(http)

    suspend fun extract(url: String): List<WallpaperItem> = withContext(Dispatchers.IO) {
        access.open(url).use { response ->
            val finalUrl = response.request.url.toString()
            val body = response.body ?: error("The website returned an empty page.")
            val mime = body.contentType()?.let { "${it.type}/${it.subtype}" }.orEmpty()
            if (RemoteImageAccess.isImageSignature(response.peekBody(32).bytes())) {
                require(body.contentLength() <= RemoteImageAccess.MAX_IMAGE_BYTES) { "This image is larger than 64 MB. Choose a smaller version." }
                val title = response.request.url.pathSegments.lastOrNull().orEmpty().substringBeforeLast('.').ifBlank { "Website wallpaper" }
                listOf(item(finalUrl, finalUrl, title, response.request.url.host, 0, 0, body.contentLength().coerceAtLeast(0)))
            } else {
                require(mime.isBlank() || mime == "text/html" || mime == "application/xhtml+xml") {
                    "This link is not a supported image or wallpaper page. Try its JPEG, PNG or WebP download link."
                }
                val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                val html = access.readLimited(response, RemoteImageAccess.MAX_PAGE_BYTES).toString(charset)
                val candidates = parsePage(html, finalUrl)
                require(candidates.isNotEmpty()) {
                    "No downloadable images were found. Open the wallpaper's detail page, copy its image link, or download in your browser and use Import photo."
                }
                candidates
            }
        }
    }

    companion object {
        private val imageExtension = Regex("\\.(?:jpe?g|png|webp|avif|heic|heif|gif|bmp)$", RegexOption.IGNORE_CASE)
        private val decoration = Regex("(?:^|[/_.-])(?:logo|favicon|icon|avatar|badge|sprite)(?:[/_.-]|$)", RegexOption.IGNORE_CASE)

        internal fun parsePage(html: String, pageUrl: String): List<WallpaperItem> {
            val page = RemoteImageAccess.validateUrl(pageUrl)
            val document = Jsoup.parse(html, pageUrl)
            fun meta(name: String) = document.selectFirst("meta[property='$name'], meta[name='$name']")?.attr("content").orEmpty()
            val title = meta("og:title").ifBlank { document.title() }.ifBlank { "Website wallpaper" }.take(180)
            val author = meta("author").ifBlank { meta("article:author") }.ifBlank { page.host }.take(300)
            data class Candidate(val url: String, val width: Int, val height: Int, val score: Int, val title: String = "", val author: String = "")
            val found = linkedMapOf<String, Candidate>()
            fun add(value: String, width: Int = 0, height: Int = 0, score: Int = 0, name: String = "", credit: String = "") {
                if (value.isBlank() || value.length > 4096) return
                val resolved = page.resolve(value.trim()) ?: return
                val checked = runCatching { RemoteImageAccess.validateUrl(resolved.toString()) }.getOrNull() ?: return
                if (decoration.containsMatchIn(checked.encodedPath) || (width in 1..299) || (height in 1..299)) return
                val candidate = Candidate(checked.toString(), width.coerceAtLeast(0), height.coerceAtLeast(0), score, name.take(180), credit.take(300))
                val previous = found[candidate.url]
                if (previous == null) found[candidate.url] = candidate
                else {
                    val best = if (candidate.score > previous.score) candidate else previous
                    val fallback = if (best === candidate) previous else candidate
                    found[candidate.url] = best.copy(width = best.width.takeIf { it > 0 } ?: fallback.width,
                        height = best.height.takeIf { it > 0 } ?: fallback.height,
                        title = best.title.ifBlank { fallback.title }, author = best.author.ifBlank { fallback.author })
                }
            }
            // Explicit download links rank above social previews. Only supported raster formats are offered.
            document.select("a[href], link[rel=image_src]").forEach { element ->
                val value = element.attr("href")
                val linked = page.resolve(value)
                if (linked != null && imageExtension.containsMatchIn(linked.encodedPath)) {
                    val preferred = Regex("original|large|full|download|4k", RegexOption.IGNORE_CASE)
                        .containsMatchIn(value + " " + element.text() + " " + element.attr("class"))
                    add(value, score = if (preferred) 100 else 65)
                }
            }
            document.select("script[type=application/ld+json]").take(20).forEach { script ->
                fun walk(value: Any?, depth: Int = 0, imageContext: Boolean = false) {
                    if (depth > 12) return
                    when (value) {
                        is JSONArray -> for (i in 0 until minOf(value.length(), 100)) walk(value.opt(i), depth + 1, imageContext)
                        is JSONObject -> {
                            val isImage = imageContext || value.optString("@type").contains("ImageObject", true)
                            val width = value.optInt("width", 0); val height = value.optInt("height", 0)
                            val creator = value.optJSONObject("author")?.optString("name").orEmpty()
                                .ifBlank { value.optJSONObject("creator")?.optString("name").orEmpty() }
                            value.optString("contentUrl").takeIf { it.isNotBlank() }?.let { add(it, width, height, 95, value.optString("name"), creator) }
                            if (isImage) value.optString("url").takeIf { it.isNotBlank() }?.let { add(it, width, height, 90, value.optString("name"), creator) }
                            value.keys().forEach { key ->
                                if (key in listOf("image", "primaryImageOfPage")) {
                                    val image = value.opt(key)
                                    if (image is String) add(image, score = 85) else walk(image, depth + 1, true)
                                } else if (key in listOf("@graph", "mainEntity", "mainEntityOfPage", "itemListElement", "item", "associatedMedia")) walk(value.opt(key), depth + 1)
                            }
                        }
                        is String -> if (imageContext) add(value, score = 85)
                    }
                }
                runCatching { walk(JSONTokener(script.data()).nextValue()) }
            }
            val socialWidth = meta("og:image:width").toIntOrNull() ?: 0
            val socialHeight = meta("og:image:height").toIntOrNull() ?: 0
            listOf("og:image:secure_url", "og:image", "twitter:image", "twitter:image:src").forEach {
                add(meta(it), socialWidth, socialHeight, 60)
            }
            document.select("img").take(300).forEach { img ->
                val width = img.attr("width").toIntOrNull() ?: 0
                val height = img.attr("height").toIntOrNull() ?: 0
                listOf("data-full", "data-original", "data-download", "data-src", "src").forEach { attr ->
                    val value = img.attr(attr)
                    val knownImage = page.resolve(value)?.let { imageExtension.containsMatchIn(it.encodedPath) } == true
                    val fullSize = attr in listOf("data-full", "data-original", "data-download")
                    // Element dimensions describe the displayed thumbnail, not a linked original.
                    if (knownImage) add(value, if (fullSize) 0 else width, if (fullSize) 0 else height,
                        if (fullSize) 80 else 30, img.attr("alt"))
                }
                img.attr("srcset").split(',').forEach { entry ->
                    val parts = entry.trim().split(Regex("\\s+"))
                    val pixels = parts.getOrNull(1)?.removeSuffix("w")?.toIntOrNull() ?: 0
                    if (pixels >= 1000) add(parts.first(), pixels, 0, 45, img.attr("alt"))
                }
            }
            return found.values.sortedWith(compareByDescending<Candidate> { it.score }
                .thenByDescending { it.width.toLong() * it.height }).take(24).map {
                item(it.url, pageUrl, it.title.ifBlank { title }, it.author.ifBlank { author }, it.width, it.height)
            }
        }

        private fun item(url: String, source: String, title: String, author: String, width: Int, height: Int, bytes: Long = 0): WallpaperItem {
            val hash = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
            return WallpaperItem(id = "web_$hash", title = title, category = "Web imports", author = author,
                license = "Check the source's image credits and usage terms before use; website availability does not grant a license.",
                sourceUrl = source, remoteUrl = url, thumbnail = url, width = width, height = height, bytes = bytes)
        }
    }
}
