package ar.edu.ipem388.radio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object StreamResolver {

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun resolveFromPortal(url: String = "https://ipem388.radio12345.com/"): String? =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Android) RadioIPEM388")
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val html = resp.body?.string() ?: return@withContext null

                    // 1) Buscar .mp3 típicos
                    val mp3 = Regex("""https?://[^\s"'<>]+?\.mp3[^\s"'<>]*""", RegexOption.IGNORE_CASE)
                        .find(html)?.value
                    if (mp3 != null) return@withContext mp3

                    // 2) Buscar .m3u8 por si cambian a HLS
                    val m3u8 = Regex("""https?://[^\s"'<>]+?\.m3u8[^\s"'<>]*""", RegexOption.IGNORE_CASE)
                        .find(html)?.value
                    if (m3u8 != null) return@withContext m3u8

                    null
                }
            } catch (_: Exception) {
                null
            }
        }
}
