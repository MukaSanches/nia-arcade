package com.mukasanches.zapptv.data

import android.content.Context
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

enum class HttpDownloadResult {
    UPDATED,
    NOT_MODIFIED,
    FAILED
}

class HttpDownloadEngine(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("sanchestv-http-meta", Context.MODE_PRIVATE)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cache(Cache(File(appContext.cacheDir, "sanchestv-http"), HTTP_CACHE_BYTES))
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun download(
        url: String,
        target: File,
        maxBytes: Long,
        validator: ((File) -> Boolean)? = null
    ): HttpDownloadResult {
        if (!url.startsWith("https://", ignoreCase = true)) {
            return HttpDownloadResult.FAILED
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")

        prefs.getString(metaKey(url, "etag"), null)
            ?.takeIf(String::isNotBlank)
            ?.let { requestBuilder.header("If-None-Match", it) }

        prefs.getString(metaKey(url, "last_modified"), null)
            ?.takeIf(String::isNotBlank)
            ?.let { requestBuilder.header("If-Modified-Since", it) }

        val temp = File(target.parentFile, "${target.name}.tmp")

        return runCatching {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.code == 304 && target.isFile && target.length() > 0L) {
                    target.setLastModified(System.currentTimeMillis())
                    return@use HttpDownloadResult.NOT_MODIFIED
                }

                if (!response.isSuccessful) {
                    return@use HttpDownloadResult.FAILED
                }

                val body = response.body
                val raw = BufferedInputStream(body.byteStream())
                raw.mark(4)
                val first = raw.read()
                val second = raw.read()
                raw.reset()

                val input = if (first == GZIP_MAGIC_1 && second == GZIP_MAGIC_2) {
                    GZIPInputStream(raw)
                } else {
                    raw
                }

                FileOutputStream(temp).use { output ->
                    input.use { source ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val read = source.read(buffer)
                            if (read <= 0) break
                            total += read
                            require(total <= maxBytes) {
                                "Resposta excedeu o limite seguro"
                            }
                            output.write(buffer, 0, read)
                        }
                        output.fd.sync()
                    }
                }

                require(temp.length() > 0L) { "Resposta vazia" }
                require(validator?.invoke(temp) != false) { "Conteúdo inválido" }

                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
                target.setLastModified(System.currentTimeMillis())

                prefs.edit().apply {
                    response.header("ETag")?.let {
                        putString(metaKey(url, "etag"), it)
                    }
                    response.header("Last-Modified")?.let {
                        putString(metaKey(url, "last_modified"), it)
                    }
                }.apply()

                HttpDownloadResult.UPDATED
            }
        }.getOrElse {
            temp.delete()
            HttpDownloadResult.FAILED
        }
    }

    fun versionToken(url: String): String? =
        prefs.getString(metaKey(url, "etag"), null)
            ?: prefs.getString(metaKey(url, "last_modified"), null)

    private fun metaKey(url: String, suffix: String): String =
        url.hashCode().toUInt().toString(16) + "_" + suffix

    companion object {
        private const val USER_AGENT = "SANCHESTV/2.9.1 (Android TV)"
        private const val CONNECT_TIMEOUT_SECONDS = 12L
        private const val READ_TIMEOUT_SECONDS = 45L
        private const val CALL_TIMEOUT_SECONDS = 75L
        private const val HTTP_CACHE_BYTES = 48L * 1024L * 1024L
        private const val GZIP_MAGIC_1 = 0x1f
        private const val GZIP_MAGIC_2 = 0x8b
    }
}
