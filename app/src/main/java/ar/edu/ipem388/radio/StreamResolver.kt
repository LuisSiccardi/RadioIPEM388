package ar.edu.ipem388.radio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

object StreamResolver {
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS).build()

    suspend fun resolveFromPortal(url: String = "https://ipem388.radio12345.com/"): String? =
        withContext(Dispatchers.IO) {
            try {
                val html = client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    response.body?.string() ?: return@withContext null
                }
                extractCandidates(html).take(3).firstOrNull { candidate ->
                    try {
                        client.newCall(Request.Builder().url(candidate)
                            .header("Range", "bytes=0-511").build()).execute().use { response ->
                            if (!response.isSuccessful) return@use false
                            val type = response.header("Content-Type", "").orEmpty().lowercase()
                            if (type.contains("text/html")) return@use false
                            val body = response.body ?: return@use false
                            val bytes = ByteArray(512)
                            val count = body.byteStream().read(bytes)
                            isAudioPrefix(bytes, count, type)
                        }
                    } catch (_: java.io.IOException) { false }
                }
            } catch (_: java.io.IOException) { null }
        }

    internal fun extractCandidates(html: String): List<String> {
        val decoded = html.replace("\\/", "/").replace("&amp;", "&").replace("\\u0026", "&")
        return Regex("""https?://[^\s"'<>]+?\.(?:mp3|m3u8)[^\s"'<>]*""", RegexOption.IGNORE_CASE)
            .findAll(decoded).map { it.value }.filter { url ->
                val host = try { URI(url).host?.lowercase() } catch (_: Exception) { null }
                host != null && (host == "listen2myradio.com" || host.endsWith(".listen2myradio.com") ||
                    host == "radio12345.com" || host.endsWith(".radio12345.com"))
            }.distinct().toList()
    }

    internal fun isAudioPrefix(bytes: ByteArray, count: Int, type: String): Boolean {
        if (count <= 0 || type.contains("text/html", ignoreCase = true)) return false
        val prefix = String(bytes, 0, minOf(count, 64), Charsets.ISO_8859_1).trimStart()
        if (prefix.startsWith("<") || prefix.startsWith("{")) return false
        return prefix.startsWith("ID3") || prefix.startsWith("OggS") || prefix.startsWith("#EXTM3U") ||
            (count >= 2 && (bytes[0].toInt() and 0xff) == 0xff && (bytes[1].toInt() and 0xe0) == 0xe0) ||
            (type.startsWith("audio/") && count >= 16)
    }
}
