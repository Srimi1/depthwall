package dev.srimi.depthwall

import dev.srimi.depthwall.data.RemoteImageAccess
import dev.srimi.depthwall.data.WebImageImporter
import org.junit.Assert.*
import org.junit.Test
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class WebImageImporterTest {
    @Test fun rejectsPrivateNetworksAndNonHttpsLinks() {
        for (url in listOf(
            "http://example.org/wall.jpg", "file:///etc/hosts", "https://localhost/image.png",
            "https://photos.local/image.png", "https://10.0.0.1/image.jpg", "https://127.0.0.1/image.jpg",
            "https://172.31.9.1/image.jpg", "https://192.168.1.1/image.jpg", "https://169.254.169.254/image.jpg",
            "https://100.64.0.1/image.jpg", "https://[::1]/image.jpg", "https://[fc00::1]/image.jpg",
            "https://[fe80::1]/image.jpg", "https://[::ffff:127.0.0.1]/image.jpg",
            "https://user:password@example.org/image.jpg", "https://example.org:8443/image.jpg"
        )) assertTrue("Accepted $url", runCatching { RemoteImageAccess.validateUrl(url) }.isFailure)
        assertEquals("images-assets.nasa.gov", RemoteImageAccess.validateUrl("https://images-assets.nasa.gov/image.jpg").host)
    }

    @Test fun dnsPolicyRejectsSpecialUseAddressesIncludingIpv6() {
        for (ip in listOf("0.0.0.0", "127.1.2.3", "10.20.30.40", "172.16.0.1", "192.168.3.4", "169.254.0.1",
            "100.127.0.1", "198.19.0.1", "192.0.2.8", "198.51.100.2", "203.0.113.1", "224.0.0.1", "255.255.255.255",
            "::", "::1", "fd12::1", "fe80::1", "ff02::1", "2001:db8::1", "64:ff9b::127.0.0.1", "2002:7f00:0001::"))
            assertFalse("Accepted $ip", RemoteImageAccess.isPublicAddress(InetAddress.getByName(ip)))
        for (ip in listOf("8.8.8.8", "1.1.1.1", "142.250.0.1", "2606:4700:4700::1111"))
            assertTrue("Rejected $ip", RemoteImageAccess.isPublicAddress(InetAddress.getByName(ip)))
    }

    @Test fun prefersDownloadLinksAndKeepsPageCreditsWithoutInventingLicense() {
        val items = WebImageImporter.parsePage("""
            <html><head><title>A nebula</title><meta name='author' content='ESA / NASA'>
            <meta property='og:image' content='https://cdn.example.org/screen/nebula.jpg'></head><body>
            <a href='https://cdn.example.org/original/nebula.tif'>Original TIFF</a>
            <a href='https://cdn.example.org/large/nebula.jpg'>Large JPEG</a>
            <img src='/logo.png' width='900' height='900'>
            <img src='/tiny.jpg' width='90' height='90'>
            <a href='http://example.org/insecure.jpg'>Insecure</a>
            <a href='https://127.0.0.1/private.jpg'>Private</a>
            </body></html>
        """.trimIndent(), "https://example.org/images/nebula/")
        assertEquals(2, items.size)
        assertEquals("https://cdn.example.org/large/nebula.jpg", items.first().remoteUrl)
        assertEquals("https://example.org/images/nebula/", items.first().sourceUrl)
        assertEquals("ESA / NASA", items.first().author)
        assertTrue(items.first().license.contains("Check the source"))
        assertFalse(items.first().license.contains("public domain"))
    }

    @Test fun extractsJsonLdFullSizeImageAndMetadataWithStableIds() {
        val html = """
            <a href='https://cdn.example.org/mountain.jpg'>Download original</a>
            <script type='application/ld+json'>
            {"@context":"https://schema.org","@graph":[{"@type":"ImageObject","name":"Mountain night",
            "contentUrl":"https://cdn.example.org/mountain.jpg","width":4096,"height":2160,
            "author":{"@type":"Person","name":"Photographer"}}]}
            </script>
        """.trimIndent()
        val image = WebImageImporter.parsePage(html, "https://example.org/photo").single()
        assertEquals(4096, image.width)
        assertEquals(2160, image.height)
        assertEquals("Mountain night", image.title)
        assertEquals("Photographer", image.author)
        assertEquals(image.id, WebImageImporter.parsePage(html, "https://example.org/another-page").single().id)
    }

    @Test fun resolvesRelativeImagesAndDeduplicatesMetadata() {
        val items = WebImageImporter.parsePage("""
            <meta property='og:image' content='/image.jpg'>
            <meta name='twitter:image' content='/image.jpg'>
            <img src='/image.jpg' srcset='/image.jpg 1200w, /image-4k.jpg 4096w'>
            <img src='/tiny.jpg' width='120' height='80' data-full='/original.jpg'>
        """.trimIndent(), "https://example.org/photo/")
        assertEquals(3, items.size)
        assertTrue(items.any { it.remoteUrl == "https://example.org/image-4k.jpg" && it.width == 4096 })
        assertTrue(items.any { it.remoteUrl == "https://example.org/original.jpg" && it.width == 0 })
        assertEquals(1, items.count { it.remoteUrl == "https://example.org/image.jpg" })
    }

    @Test fun rejectsHtmlAndSvgMasqueradingAsImageAndAcceptsRasterSignatures() {
        for (header in listOf("<!doctype html><html>", "<svg xmlns='http://www.w3.org/2000/svg'>", "Not an image"))
            assertFalse(RemoteImageAccess.isImageSignature(header.toByteArray()))
        assertTrue(RemoteImageAccess.isImageSignature(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe0.toByte())))
        assertTrue(RemoteImageAccess.isImageSignature(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)))
        assertTrue(RemoteImageAccess.isImageSignature("RIFF0000WEBP".toByteArray()))
        assertTrue(RemoteImageAccess.isImageSignature("0000ftypavif".toByteArray()))
        assertFalse(RemoteImageAccess.isImageSignature("RIFF0000WAVE".toByteArray()))
    }

    @Test fun boundsStreamingBodiesEvenWithoutContentLength() {
        var written = 0
        RemoteImageAccess.transferLimited(ByteArrayInputStream(ByteArray(120)), 120) { _, count -> written += count }
        assertEquals(120, written)
        assertTrue(runCatching {
            RemoteImageAccess.transferLimited(ByteArrayInputStream(ByteArray(121)), 120) { _, _ -> }
        }.isFailure)
        assertTrue(runCatching {
            RemoteImageAccess.transferLimited(ByteArrayInputStream(byteArrayOf()), 120) { _, _ -> }
        }.isFailure)
    }

    @Test fun safeClientRejectsPrivateDnsAnswersBeforeConnecting() {
        val http = OkHttpClient.Builder().dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = listOf(InetAddress.getByName("10.0.0.8"))
        }).build()
        assertTrue(runCatching { RemoteImageAccess(http).safeClient().dns.lookup("images.example.org") }.isFailure)
    }

    @Test fun validatesRedirectDestinationBeforeFollowingIt() {
        val access = RemoteImageAccess(OkHttpClient())
        val interceptor = access.safeClient().interceptors.last()
        for (destination in listOf("https://127.0.0.1/secret", "http://example.org/image.jpg", "https://[::1]/secret")) {
            val chain = FakeChain { request -> redirect(request, destination) }
            assertTrue(runCatching { interceptor.intercept(chain) }.isFailure)
            assertEquals("Followed blocked redirect to $destination", 1, chain.requestCount)
        }
        val valid = FakeChain { request ->
            if (request.url.encodedPath == "/page") redirect(request, "/image.jpg")
            else Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("image".toResponseBody()).build()
        }
        interceptor.intercept(valid).use { assertEquals("/image.jpg", it.request.url.encodedPath) }
        assertEquals(2, valid.requestCount)
        val loop = FakeChain { request -> redirect(request, "/page") }
        assertTrue(runCatching { interceptor.intercept(loop) }.isFailure)
        assertEquals(6, loop.requestCount)
    }

    private fun redirect(request: Request, location: String) = Response.Builder().request(request)
        .protocol(Protocol.HTTP_1_1).code(302).message("Found").header("Location", location)
        .body("".toResponseBody()).build()

    private class FakeChain(private val respond: (Request) -> Response) : Interceptor.Chain {
        var requestCount = 0
        private val initial = Request.Builder().url("https://example.org/page").build()
        override fun request(): Request = initial
        override fun proceed(request: Request): Response { requestCount++; return respond(request) }
        override fun connection(): Connection? = null
        override fun call(): Call = OkHttpClient().newCall(initial)
        override fun connectTimeoutMillis() = 1000
        override fun readTimeoutMillis() = 1000
        override fun writeTimeoutMillis() = 1000
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }
}
