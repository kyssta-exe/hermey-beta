package com.kyssta.hermeybeta.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Latest published GitHub release carrying an APK (Settings → About updater). */
data class AppRelease(val tag: String, val apkUrl: String, val apkSize: Long)

object AppUpdates {
    const val REPO = "kyssta-exe/hermey-beta"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Null when offline, rate-limited, or no release/APK is published yet. */
    suspend fun latest(): AppRelease? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val o = JSONObject(resp.body?.string() ?: return@withContext null)
            val tag = o.optString("tag_name").takeUnless { it.isBlank() } ?: return@withContext null
            val assets = o.optJSONArray("assets") ?: return@withContext null
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                if (a.optString("name").endsWith(".apk")) {
                    return@withContext AppRelease(tag, a.optString("browser_download_url"), a.optLong("size"))
                }
            }
            null
        }
    }

    /** True when [tag] (e.g. "v0.5.0") is newer than [current] (e.g. "0.4.0"). */
    fun isNewer(tag: String, current: String): Boolean {
        fun parts(s: String) = s.trim().removePrefix("v").removePrefix("V")
            .split(".", "-").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(tag)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val d = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
            if (d != 0) return d > 0
        }
        return false
    }
}
