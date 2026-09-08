package com.doffi4.doffisecure.security

import android.content.Context
import android.graphics.BitmapFactory
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Request payload for resolving a website's favicon.
 */
data class FaviconRequest(
    val host: String,
    val apexDomain: String? = null,
    val forceRefresh: Boolean = false
)

/**
 * Coil [Fetcher] implementing a resilient waterfall resolution pipeline:
 *  1. Local persistent disk cache.
 *  2. Google Favicon API (with stub/globe detection).
 *  3. DuckDuckGo Favicon API (with stub detection).
 *  4. Direct https://{domain}/favicon.ico.
 *  5. Apex domain fallback (repeating steps 2..4 for root domain if host is a subdomain).
 */
class FaviconFetcher(
    private val context: Context,
    private val request: FaviconRequest,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val host = request.host.trim().lowercase()
        if (host.isBlank()) return@withContext null

        val cacheDir = File(context.cacheDir, CACHE_SUBDIR).apply { if (!exists()) mkdirs() }
        val cacheFile = File(cacheDir, "${sanitizeHost(host)}.bin")

        // 1. Check local disk cache (unless forceRefresh requested)
        if (!request.forceRefresh && cacheFile.exists() && cacheFile.length() > 0) {
            val bytes = cacheFile.readBytes()
            if (isValidImage(bytes)) {
                return@withContext SourceResult(
                    source = ImageSource(source = Buffer().write(bytes), context = context),
                    mimeType = null,
                    dataSource = DataSource.DISK
                )
            } else {
                cacheFile.delete()
            }
        }

        // 2. Waterfall resolution
        var iconBytes = resolveWaterfall(host)

        // 3. Fallback to Apex domain if subdomain failed
        if (iconBytes == null && !request.apexDomain.isNullOrBlank() && request.apexDomain != host) {
            iconBytes = resolveWaterfall(request.apexDomain)
        }

        if (iconBytes != null && isValidImage(iconBytes)) {
            // Save to persistent cache
            try {
                cacheFile.writeBytes(iconBytes)
            } catch (_: Exception) {}

            SourceResult(
                source = ImageSource(source = Buffer().write(iconBytes), context = context),
                mimeType = null,
                dataSource = DataSource.NETWORK
            )
        } else {
            null
        }
    }

    private fun resolveWaterfall(domain: String): ByteArray? {
        // Step A: Google Favicon API
        fetchGoogle(domain)?.let { return it }

        // Step B: DuckDuckGo API
        fetchDuckDuckGo(domain)?.let { return it }

        // Step C: Direct favicon.ico
        fetchDirect(domain)?.let { return it }

        return null
    }

    private fun fetchGoogle(domain: String): ByteArray? {
        val url = "https://www.google.com/s2/favicons?domain=$domain&sz=128"
        val bytes = httpGet(url) ?: return null

        // Check if Google returned its default 16x16 gray globe stub
        if (isGoogleDefaultGlobe(bytes)) {
            return null
        }
        return bytes
    }

    private fun fetchDuckDuckGo(domain: String): ByteArray? {
        val url = "https://icons.duckduckgo.com/ip3/$domain.ico"
        val bytes = httpGet(url) ?: return null

        // Check if DuckDuckGo returned its default placeholder
        if (isDdgDefaultStub(bytes)) {
            return null
        }
        return bytes
    }

    private fun fetchDirect(domain: String): ByteArray? {
        val url = "https://$domain/favicon.ico"
        val bytes = httpGet(url) ?: return null

        if (isHtmlResponse(bytes) || !isValidImage(bytes)) {
            return null
        }
        return bytes
    }

    private fun httpGet(url: String): ByteArray? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Decryptum)")
                .build()
            httpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                val bytes = body.bytes()
                if (bytes.isEmpty()) null else bytes
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val CACHE_SUBDIR = "favicons_v2"

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        private const val GOOGLE_GLOBE_SHA256 = "59bfe9bc385ad69f50793ce4a53397316d7a875a7148a63c16df9b674c6cda64"
        private const val DDG_STUB_SHA256 = "a66570d7dd3135b57642de042fdb4ad47ef80567d99bf89690dcd73ae47f94a0"

        fun sanitizeHost(host: String): String =
            host.replace(Regex("[^a-zA-Z0-9.-]"), "_")

        /**
         * Clears all cached favicon files on disk.
         */
        fun clearDiskCache(context: Context) {
            try {
                val dir = File(context.cacheDir, CACHE_SUBDIR)
                if (dir.exists()) {
                    dir.deleteRecursively()
                }
            } catch (_: Exception) {}
        }

        /**
         * Removes a specific host's icon from disk cache.
         */
        fun invalidateHost(context: Context, host: String) {
            try {
                val dir = File(context.cacheDir, CACHE_SUBDIR)
                val file = File(dir, "${sanitizeHost(host.lowercase())}.bin")
                if (file.exists()) {
                    file.delete()
                }
            } catch (_: Exception) {}
        }

        private fun isGoogleDefaultGlobe(bytes: ByteArray): Boolean {
            if (bytes.size == 726) return true
            if (sha256(bytes).equals(GOOGLE_GLOBE_SHA256, ignoreCase = true)) return true

            // Google returns 16x16 PNG for fallback even when sz=128 was requested
            val bounds = decodeBounds(bytes)
            if (bounds != null && bounds.first <= 16 && bounds.second <= 16) {
                return true
            }
            return false
        }

        private fun isDdgDefaultStub(bytes: ByteArray): Boolean {
            if (bytes.size == 4074) return true
            if (sha256(bytes).equals(DDG_STUB_SHA256, ignoreCase = true)) return true
            return false
        }

        private fun isHtmlResponse(bytes: ByteArray): Boolean {
            val preview = String(bytes.take(64).toByteArray(), Charsets.UTF_8).trim().lowercase()
            return preview.startsWith("<!doctype") || preview.startsWith("<html") || preview.startsWith("<?xml")
        }

        private fun isValidImage(bytes: ByteArray): Boolean {
            if (bytes.size < 8) return false
            // Check magic bytes:
            // PNG: 89 50 4E 47
            if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) return true
            // ICO: 00 00 01 00
            if (bytes[0] == 0x00.toByte() && bytes[1] == 0x00.toByte() && bytes[2] == 0x01.toByte() && bytes[3] == 0x00.toByte()) return true
            // JPEG: FF D8 FF
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) return true
            // GIF: 47 49 46
            if (bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte()) return true
            // BMP: 42 4D
            if (bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte()) return true
            // WEBP: RIFF....WEBP
            if (bytes.size >= 12 && bytes[0] == 'R'.code.toByte() && bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte()) return true
            return false
        }

        private fun decodeBounds(bytes: ByteArray): Pair<Int, Int>? {
            return try {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                if (opts.outWidth > 0 && opts.outHeight > 0) {
                    Pair(opts.outWidth, opts.outHeight)
                } else null
            } catch (_: Exception) {
                null
            }
        }

        private fun sha256(bytes: ByteArray): String {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bytes)
            return digest.joinToString("") { "%02x".format(it) }
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<FaviconRequest> {
        override fun create(data: FaviconRequest, options: Options, imageLoader: ImageLoader): Fetcher {
            return FaviconFetcher(context, data, options)
        }
    }
}
