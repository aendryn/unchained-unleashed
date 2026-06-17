package com.github.livingwithhippos.unchained.data.local

import android.content.Context
import com.github.livingwithhippos.unchained.data.model.DownloadItem
import com.github.livingwithhippos.unchained.data.model.UnifiedTorrent
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Tiny persistent cache of the first screenful of each list, so a cold launch can paint the
 * last-seen Downloads/Torrents instantly while the real network load runs in the background,
 * instead of showing an empty list with a spinner. Stored as JSON in [Context.getFilesDir];
 * best-effort, so any read/write failure just means we fall back to the network-only path. Capped
 * to [LIMIT] items — enough to fill the first viewport, which is all the instant-paint needs.
 */
@Singleton
class ListSnapshotCache
@Inject
constructor(@param:ApplicationContext private val context: Context) {

    private val moshi = Moshi.Builder().build()
    private val downloadsAdapter =
        moshi.adapter<List<DownloadItem>>(
            Types.newParameterizedType(List::class.java, DownloadItem::class.java)
        )
    private val torrentsAdapter =
        moshi.adapter<List<UnifiedTorrent>>(
            Types.newParameterizedType(List::class.java, UnifiedTorrent::class.java)
        )

    suspend fun saveDownloads(items: List<DownloadItem>) =
        write(DOWNLOADS_FILE, downloadsAdapter.toJson(items.take(LIMIT)))

    suspend fun loadDownloads(): List<DownloadItem> =
        read(DOWNLOADS_FILE)?.let { runCatching { downloadsAdapter.fromJson(it) }.getOrNull() }
            ?: emptyList()

    suspend fun saveTorrents(items: List<UnifiedTorrent>) =
        write(TORRENTS_FILE, torrentsAdapter.toJson(items.take(LIMIT)))

    suspend fun loadTorrents(): List<UnifiedTorrent> =
        read(TORRENTS_FILE)?.let { runCatching { torrentsAdapter.fromJson(it) }.getOrNull() }
            ?: emptyList()

    private suspend fun write(name: String, json: String) =
        withContext(Dispatchers.IO) {
            try {
                File(context.filesDir, name).writeText(json)
            } catch (e: Exception) {
                Timber.w(e, "Failed to write list snapshot $name")
            }
        }

    private suspend fun read(name: String): String? =
        withContext(Dispatchers.IO) {
            try {
                File(context.filesDir, name).takeIf { it.exists() }?.readText()
            } catch (e: Exception) {
                Timber.w(e, "Failed to read list snapshot $name")
                null
            }
        }

    private companion object {
        const val LIMIT = 50
        const val DOWNLOADS_FILE = "snapshot_downloads.json"
        const val TORRENTS_FILE = "snapshot_torrents.json"
    }
}
