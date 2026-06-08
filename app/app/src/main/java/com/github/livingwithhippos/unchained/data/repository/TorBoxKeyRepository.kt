package com.github.livingwithhippos.unchained.data.repository

import android.content.SharedPreferences
import androidx.core.content.edit
import com.github.livingwithhippos.unchained.di.SecurePreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the TorBox API key. TorBox authenticates with a single permanent API key (no OAuth), so
 * unlike RealDebrid we don't need the protobuf credential store.
 *
 * The key lives in a dedicated [SecurePreferences] file that is excluded from cloud backup and
 * device transfer (see res/xml/data_extraction_rules.xml and unchained_backup.xml), so the permanent
 * key never leaves the device in a backup. Earlier versions kept it in the default (backed-up)
 * settings file; [migrateLegacyKey] moves any such key into the secure file on first use.
 */
@Singleton
class TorBoxKeyRepository
@Inject
constructor(
    @SecurePreferences private val preferences: SharedPreferences,
    private val legacyPreferences: SharedPreferences,
) {

    init {
        migrateLegacyKey()
    }

    fun getApiKey(): String? = preferences.getString(KEY_TORBOX_API_KEY, null)

    fun isAuthenticated(): Boolean = !getApiKey().isNullOrBlank()

    fun setApiKey(apiKey: String) {
        preferences.edit { putString(KEY_TORBOX_API_KEY, apiKey.trim()) }
    }

    fun clear() {
        preferences.edit { remove(KEY_TORBOX_API_KEY) }
    }

    /**
     * Move a key written by an older build (which stored it in the default, backed-up preferences)
     * into the secure file, then delete the legacy copy so it stops being backed up. Idempotent.
     */
    private fun migrateLegacyKey() {
        if (!legacyPreferences.contains(KEY_TORBOX_API_KEY)) return
        if (!preferences.contains(KEY_TORBOX_API_KEY)) {
            val legacyKey = legacyPreferences.getString(KEY_TORBOX_API_KEY, null)
            if (!legacyKey.isNullOrBlank()) {
                preferences.edit { putString(KEY_TORBOX_API_KEY, legacyKey) }
            }
        }
        legacyPreferences.edit { remove(KEY_TORBOX_API_KEY) }
    }

    companion object {
        const val KEY_TORBOX_API_KEY = "torbox_api_key"
    }
}
