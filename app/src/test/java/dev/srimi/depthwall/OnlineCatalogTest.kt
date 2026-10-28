package dev.srimi.depthwall

import dev.srimi.depthwall.data.OnlineCatalog
import dev.srimi.depthwall.data.WallpaperItem
import dev.srimi.depthwall.data.WallpaperSources
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OnlineCatalogTest {
    @Test fun legacyNasaDownloadResolvesOriginalAndUpgradesHttps() = runBlocking {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals("/asset/OLD", chain.request().url.encodedPath)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"collection":{"items":[{"href":"http://images-assets.nasa.gov/image/OLD/OLD~small.jpg"},{"href":"http://images-assets.nasa.gov/image/OLD/OLD~orig.jpg"}]}}""".toResponseBody()).build()
        }.build()
        val image = OnlineCatalog(http).resolveDownload(WallpaperItem("nasa_OLD", "Old image", "NASA", remoteUrl = "https://images-assets.nasa.gov/image/OLD/OLD~small.jpg", bytes = 50000))
        assertEquals("https://images-assets.nasa.gov/image/OLD/OLD~orig.jpg", image.remoteUrl)
        assertEquals(0L, image.bytes)
    }

    @Test fun knownNasaOriginalDoesNotNeedExtraManifestRequest() = runBlocking {
        val http = OkHttpClient.Builder().addInterceptor { error("Unexpected extra request") }.build()
        val original = WallpaperItem("nasa_NEW", "New image", "NASA", remoteUrl = "https://images-assets.nasa.gov/image/NEW/NEW~orig.jpg", width = 6000, height = 4000)
        assertEquals(original, OnlineCatalog(http).resolveDownload(original))
    }

    @Test fun sourceRateLimitIsAnActionableErrorInsteadOfAnEmptyCollection() = runBlocking {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals("100", chain.request().url.queryParameter("purity"))
            assertEquals("3", chain.request().url.queryParameter("page"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(429).message("Too Many Requests")
                .header("Retry-After", "60").body("{}".toResponseBody()).build()
        }.build()
        val error = runCatching { OnlineCatalog(http).search("Wallhaven", "car", "3") }.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error!!.message!!.contains("rate limited"))
        assertTrue(error.message!!.contains("60"))
    }

    @Test fun commonsApiErrorDoesNotSilentlyLookLikeNoResults() = runBlocking {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"error":{"code":"cirrussearch-too-busy-error","info":"Search is busy"}}""".toResponseBody()).build()
        }.build()
        val error = runCatching { OnlineCatalog(http).search("ESA", "nebula") }.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error!!.message!!.contains("retry later"))
    }

    @Test fun fourKRequiresBothDimensionsAndSupportsPortrait() {
        assertTrue(OnlineCatalog.is4k(3840, 2160))
        assertTrue(OnlineCatalog.is4k(2160, 3840))
        assertFalse(OnlineCatalog.is4k(6000, 1000))
        assertFalse(OnlineCatalog.is4k(1440, 3120))
        assertFalse(OnlineCatalog.is4k(0, 0))
    }

    @Test fun nasaSelectsOriginalAndUsesSmallImageOnlyForThumbnail() {
        val page = OnlineCatalog.parseNasa(JSONObject(nasaFixture), 1, true)
        val image = page.items.single()
        assertEquals("nasa_NEBULA", image.id)
        assertEquals("https://images-assets.nasa.gov/image/NEBULA/NEBULA~orig.jpg", image.remoteUrl)
        assertTrue(image.thumbnail.endsWith("~thumb.jpg"))
        assertEquals(6000, image.width)
        assertEquals(4000, image.height)
        assertEquals("NASA / ESA & Team", image.author)
        assertEquals("2", page.nextCursor)
    }

    @Test fun filteredNasaPageStillOffersNextPage() {
        val data = JSONObject(nasaFixture.replace("6000", "1920").replace("4000", "1080"))
        val page = OnlineCatalog.parseNasa(data, 5, true)
        assertTrue(page.items.isEmpty())
        assertEquals("6", page.nextCursor)
    }

    @Test fun nasaOriginalWithMissingDimensionsWinsOverMeasuredThumbnail() {
        val data = JSONObject(nasaFixture)
        val links = data.getJSONObject("collection").getJSONArray("items").getJSONObject(0).getJSONArray("links")
        links.getJSONObject(0).put("width", 1920).put("height", 1920)
        links.getJSONObject(1).remove("width")
        links.getJSONObject(1).remove("height")
        val image = OnlineCatalog.parseNasa(data, 1, false).items.single()
        assertTrue(image.remoteUrl.endsWith("~orig.jpg"))
        assertTrue(image.thumbnail.endsWith("~thumb.jpg"))
        assertEquals(0, image.width)
        assertEquals(0, image.height)
    }

    @Test fun nasaUnknownOriginalDimensionsCannotPassFourKUsingAlternateDimensions() {
        val data = JSONObject(nasaFixture)
        val links = data.getJSONObject("collection").getJSONArray("items").getJSONObject(0).getJSONArray("links")
        links.getJSONObject(0).put("width", 4000).put("height", 4000)
        links.getJSONObject(1).remove("width")
        links.getJSONObject(1).remove("height")
        val page = OnlineCatalog.parseNasa(data, 1, true)
        assertTrue(page.items.isEmpty())
        assertEquals("2", page.nextCursor)
    }

    @Test fun nasaRejectsUnsupportedAndOversizedOriginalsWithoutLosingUsablePreview() {
        val data = JSONObject(nasaFixture.replace("NEBULA~orig.jpg", "NEBULA~orig.tif"))
        val image = OnlineCatalog.parseNasa(data, 1, false).items.single()
        assertTrue(image.remoteUrl.endsWith("~thumb.jpg"))
        assertTrue(OnlineCatalog.parseNasa(data, 1, true).items.isEmpty())
    }

    @Test fun nasaDoesNotAcceptLookalikeImageHosts() {
        val data = JSONObject(nasaFixture.replace("images-assets.nasa.gov", "nasa.gov.evil.example"))
        assertTrue(OnlineCatalog.parseNasa(data, 1, false).items.isEmpty())
    }

    @Test fun commonsDownloadsOriginalPreservesCreditsAndContinues() {
        val page = OnlineCatalog.parseCommons(JSONObject(commonsFixture), "ESA", true)
        val image = page.items.single()
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/a/ab/Space.jpg", image.remoteUrl)
        assertTrue(image.thumbnail.contains("600px"))
        assertEquals("ESA · Commons", image.category)
        assertEquals("NASA & ESA team", image.author)
        assertTrue(image.license.startsWith("CC BY-SA 4.0"))
        assertTrue(image.license.contains("creativecommons.org"))
        val cursor = JSONObject(page.nextCursor!!)
        assertEquals(30, cursor.getInt("gsroffset"))
        assertEquals("gsroffset||", cursor.getString("continue"))
    }

    @Test fun commonsFiltersUnknownLicensesAndUnusableImagesButKeepsContinuation() {
        for (fixture in listOf(
            commonsFixture.replace("CC BY-SA 4.0", "All rights reserved"),
            commonsFixture.replace("image/jpeg", "image/svg+xml"),
            commonsFixture.replace("\"width\":6000", "\"width\":60000"),
            commonsFixture.replace("\"size\":4000000", "\"size\":90000000")
        )) {
            val page = OnlineCatalog.parseCommons(JSONObject(fixture), "Wikimedia", false)
            assertTrue(page.items.isEmpty())
            assertNotNull(page.nextCursor)
        }
    }

    @Test fun agencySearchesKeepTheirAgencyAndUserQuery() {
        assertEquals("(ISRO OR \"Indian Space Research Organisation\") moon filetype:bitmap", OnlineCatalog.commonsQuery("ISRO", "moon"))
        assertTrue(OnlineCatalog.commonsQuery("CSA", "earth").startsWith("\"Canadian Space Agency\" earth"))
        assertEquals("Alps filetype:bitmap", OnlineCatalog.commonsQuery("Wikimedia", "Alps"))
        for (source in WallpaperSources.sources.filter { it.id in listOf("ESA", "ISRO", "JAXA", "CSA") }) {
            assertTrue(source.label.endsWith("Commons"))
            assertTrue(source.description.contains("Commons"))
        }
    }

    @Test fun wallhavenKeepsPortraitOriginalsAndRejectsNonSfwResults() {
        val page = OnlineCatalog.parseWallhaven(JSONObject(wallhavenFixture), "McLaren", 1, true)
        val image = page.items.single()
        assertEquals("wallhaven_abc123", image.id)
        assertEquals(2160, image.width)
        assertEquals(3840, image.height)
        assertEquals("https://w.wallhaven.cc/full/ab/wallhaven-abc123.jpg", image.remoteUrl)
        assertEquals("2", page.nextCursor)
        assertTrue(OnlineCatalog.parseWallhaven(JSONObject(wallhavenFixture.replace("sfw", "sketchy")), "car", 1, false).items.isEmpty())
    }

    @Test fun wallhavenLastPageEndsPaginationAndDuplicateIdsAreRemoved() {
        val data = JSONObject(wallhavenFixture)
        data.getJSONArray("data").put(data.getJSONArray("data").getJSONObject(0))
        val page = OnlineCatalog.parseWallhaven(data, "car", 2, true)
        assertEquals(1, page.items.size)
        assertNull(page.nextCursor)
    }

    private val nasaFixture = """
        {"collection":{"items":[{"data":[{"nasa_id":"NEBULA","title":"Nebula","secondary_creator":"NASA / ESA & Team"}],"links":[
            {"href":"https://images-assets.nasa.gov/image/NEBULA/NEBULA~thumb.jpg","render":"image","rel":"preview","width":640,"height":426,"size":100000},
            {"href":"https://images-assets.nasa.gov/image/NEBULA/NEBULA~orig.jpg","render":"image","rel":"canonical","width":6000,"height":4000,"size":5000000}
        ]}],"links":[{"rel":"next","href":"http://images-api.nasa.gov/search?page=2"}]}}
    """.trimIndent()

    private val commonsFixture = """
        {"continue":{"gsroffset":30,"continue":"gsroffset||"},"query":{"pages":{"42":{
            "pageid":42,"index":1,"title":"File:Space.jpg","imageinfo":[{
                "width":6000,"height":4000,"size":4000000,"mime":"image/jpeg",
                "url":"https://upload.wikimedia.org/wikipedia/commons/a/ab/Space.jpg",
                "thumburl":"https://thumb.wikimedia.org/wikipedia/commons/thumb/a/ab/Space.jpg/600px-Space.jpg",
                "descriptionurl":"https://commons.wikimedia.org/wiki/File:Space.jpg",
                "extmetadata":{"Artist":{"value":"<b>NASA &amp; ESA team</b>"},"LicenseShortName":{"value":"CC BY-SA 4.0"},"LicenseUrl":{"value":"https://creativecommons.org/licenses/by-sa/4.0/"}}
            }]
        }}}}
    """.trimIndent()

    private val wallhavenFixture = """
        {"data":[{"id":"abc123","url":"https://wallhaven.cc/w/abc123","purity":"sfw",
            "dimension_x":2160,"dimension_y":3840,"file_size":5000000,"file_type":"image/jpeg",
            "source":"https://example.com/creator","path":"https://w.wallhaven.cc/full/ab/wallhaven-abc123.jpg",
            "thumbs":{"large":"https://th.wallhaven.cc/lg/ab/abc123.jpg"}}],
            "meta":{"current_page":1,"last_page":2}}
    """.trimIndent()
}
