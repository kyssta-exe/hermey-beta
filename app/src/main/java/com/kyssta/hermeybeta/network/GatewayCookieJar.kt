package com.kyssta.hermeybeta.network

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * Gateway session cookies persist across app restarts via EncryptedSharedPreferences.
 * Cookies survive until explicitly cleared or naturally expired.
 */
object GatewayCookieJar : CookieJar {
    private var context: Context? = null
    private val gson = Gson()
    private val cookiesTypeToken = object : TypeToken<Map<String, List<Cookie>>>() {}.type
    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()
    private const val KEY_COOKIES = "cookies"

    fun init(ctx: Context) {
        if (context != null) return
        context = ctx
        loadFromFile()
    }

    private fun prefs(): SharedPreferences {
        val ctx = context ?: throw IllegalStateException("GatewayCookieJar not initialized")
        return EncryptedSharedPreferences.create(
            ctx,
            "gateway_cookies_prefs",
            MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun loadFromFile() {
        val json = try { prefs().getString(KEY_COOKIES, null) } catch (_: Exception) { null } ?: return
        try {
            val loaded = gson.fromJson<Map<String, List<Cookie>>>(json, cookiesTypeToken) ?: return
            val now = System.currentTimeMillis()
            loaded.forEach { (host, cookies) ->
                val valid = cookies.filter { it.expiresAt > now + 10_000L }
                if (valid.isNotEmpty()) store[host] = valid.toMutableList()
            }
        } catch (_: Exception) {}
    }

    private fun saveToFile() {
        val json = gson.toJson(store.toMap())
        try { prefs().edit { putString(KEY_COOKIES, json) } } catch (_: Exception) {}
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val list = store.getOrPut(url.host) { mutableListOf() }
        synchronized(list) {
            list.removeAll { existing -> cookies.any { it.name == existing.name } }
            list.addAll(cookies)
            saveToFile()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val list = store[url.host] ?: return emptyList()
        synchronized(list) {
            val now = System.currentTimeMillis()
            list.removeAll { it.expiresAt <= now }
            if (list.isEmpty()) {
                store.remove(url.host)
                saveToFile()
                return emptyList()
            }
            return list.toList()
        }
    }

    fun clear() {
        store.clear()
        try { prefs().edit { remove(KEY_COOKIES) } } catch (_: Exception) {}
    }
}
