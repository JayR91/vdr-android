package com.jayr91.vdr.engine

import okhttp3.OkHttpClient
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Shared HTTP client with a browser-like User-Agent and Referer.
 * Some CDNs (e.g. W3Schools) return HTML 403 pages to bare OkHttp clients.
 */
object VdrHttp {
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36 VDR/1.5.6"

    fun refererFor(url: String): String? {
        return try {
            val u = URI(url.trim())
            val scheme = u.scheme ?: return null
            val authority = u.authority ?: return null
            val path = u.path.orEmpty()
            val parent = when {
                path.isEmpty() || path == "/" -> "/"
                path.endsWith("/") -> path
                else -> path.substringBeforeLast('/', missingDelimiterValue = "") + "/"
            }.ifEmpty { "/" }
            URI(scheme, authority, parent, null, null).toString()
        } catch (_: Exception) {
            null
        }
    }

    fun newClient(
        connectTimeoutSec: Long = 15,
        readTimeoutSec: Long = 30,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(connectTimeoutSec, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor { chain ->
            val req = chain.request()
            val b = req.newBuilder()
            if (req.header("User-Agent").isNullOrBlank()) {
                b.header("User-Agent", USER_AGENT)
            }
            if (req.header("Referer").isNullOrBlank()) {
                refererFor(req.url.toString())?.let { b.header("Referer", it) }
            }
            if (req.header("Accept").isNullOrBlank()) {
                b.header("Accept", "*/*")
            }
            chain.proceed(b.build())
        }
        .build()
}
