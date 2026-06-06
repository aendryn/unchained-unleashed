package com.github.livingwithhippos.unchained.data.repository

import android.content.SharedPreferences
import androidx.core.content.edit
import javax.inject.Inject

/**
 * Stores the TorBox API key. TorBox authenticates with a single permanent API key (no OAuth), so
 * unlike RealDebrid we don't need the protobuf credential store. We reuse the app-wide
 * [SharedPreferences] that already backs the settings screen.
 *
 * If you prefer all credentials to live in one place, the alternative is to extend
 * credentials.proto into a `map<string, CurrentCredential>` keyed by [DebridService.tag]; see
 * TORBOX_INTEGRATION.md for that migration. This implementation is intentionally minimal so the
 * networking layer can be wired up and tested independently.
 */
class TorBoxKeyRepository @Inject constructor(private val preferences: SharedPreferences) {

    fun getApiKey(): String? = preferences.getString(KEY_TORBOX_API_KEY, null)

    fun isAuthenticated(): Boolean = !getApiKey().isNullOrBlank()

    fun setApiKey(apiKey: String) {
        preferences.edit { putString(KEY_TORBOX_API_KEY, apiKey.trim()) }
    }

    fun clear() {
        preferences.edit { remove(KEY_TORBOX_API_KEY) }
    }

    companion object {
        const val KEY_TORBOX_API_KEY = "torbox_api_key"
    }
}
