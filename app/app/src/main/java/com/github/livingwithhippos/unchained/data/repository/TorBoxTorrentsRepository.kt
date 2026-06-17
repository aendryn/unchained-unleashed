package com.github.livingwithhippos.unchained.data.repository

import com.github.livingwithhippos.unchained.data.model.NetworkError
import com.github.livingwithhippos.unchained.data.model.UnchainedNetworkException
import com.github.livingwithhippos.unchained.data.model.UnifiedTorrent
import com.github.livingwithhippos.unchained.data.model.toUnified
import com.github.livingwithhippos.unchained.data.model.torbox.TorBoxControlTorrentRequest
import com.github.livingwithhippos.unchained.data.model.torbox.TorBoxResponse
import com.github.livingwithhippos.unchained.data.model.torbox.TorBoxTorrent
import com.github.livingwithhippos.unchained.data.remote.TorBoxApi
import com.github.livingwithhippos.unchained.utilities.EitherResult
import com.github.livingwithhippos.unchained.utilities.TORBOX_API_VERSION
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import timber.log.Timber

/**
 * Repository for TorBox. Mirrors [TorrentsRepository] but:
 * - reads the API key from [TorBoxKeyRepository] instead of the protobuf credential store
 * - unwraps the TorBox `{success, error, detail, data}` envelope
 * - returns [UnifiedTorrent] so callers don't depend on TorBox-specific types
 */
@Singleton
class TorBoxTorrentsRepository
@Inject
constructor(private val torBoxApi: TorBoxApi, private val keyRepository: TorBoxKeyRepository) {

    /**
     * Set whenever the torrent list changes (an add/delete/pause/resume through this app) or when
     * the user explicitly asks for fresh data. The torrents paging source reads-and-clears this via
     * [consumeListStale] to decide whether it must bypass TorBox's server-side `/mylist` cache for
     * the next load. Routine loads (first open, tab switches, scrolling) leave it false and so are
     * served from the cache, which is much faster; anything that could have changed the list flips
     * it true so the next load shows the true status. Singleton-scoped so every mutation call site
     * shares the same flag.
     */
    private val listStale = AtomicBoolean(false)

    /** Mark the cached torrent list as stale so the next load fetches fresh data. */
    fun markListStale() {
        listStale.set(true)
    }

    /** Returns whether the list is stale and clears the flag in one atomic step. */
    fun consumeListStale(): Boolean = listStale.getAndSet(false)

    /** Returns whether the list is stale without clearing the flag. */
    fun peekListStale(): Boolean = listStale.get()

    /**
     * Torrents deleted through the app, mapped to the time they were deleted. TorBox processes
     * deletes asynchronously: `controltorrent` returns `success: true` immediately, but `/mylist`
     * (even with `bypass_cache=true`) keeps listing the just-deleted torrent for a short window
     * afterwards. Without this, the post-delete refresh re-fetches that stale list and the torrent
     * reappears, so the delete looks like a no-op. We suppress these ids from list results until
     * the server stops returning them; the [DELETED_TOMBSTONE_MS] cap then lets them expire so a
     * tombstone can never hide a torrent forever. Re-adding a torrent clears its id (see the add*
     * methods).
     */
    private val deletedIds = ConcurrentHashMap<Long, Long>()

    /**
     * Drop tombstones older than [DELETED_TOMBSTONE_MS] so they can't suppress a torrent forever.
     */
    private fun pruneTombstones() {
        if (deletedIds.isEmpty()) return
        val cutoff = System.currentTimeMillis() - DELETED_TOMBSTONE_MS
        deletedIds.entries.removeAll { it.value < cutoff }
    }

    private fun bearer(): String {
        val key =
            keyRepository.getApiKey() ?: throw IllegalArgumentException("Missing TorBox API key")
        return "Bearer $key"
    }

    // TorBox's createtorrent endpoint is multipart/form-data, so scalar fields go as text parts.
    private val plainText = "text/plain".toMediaTypeOrNull()

    private fun String.asPart(): RequestBody = toRequestBody(plainText)

    suspend fun getTorrentsList(
        offset: Int? = null,
        limit: Int? = 1000,
        bypassCache: Boolean? = null,
    ): List<UnifiedTorrent> =
        withContext(Dispatchers.IO) {
            try {
                val response =
                    torBoxApi.getTorrentsList(
                        TORBOX_API_VERSION,
                        bearer(),
                        bypassCache,
                        offset,
                        limit,
                    )
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    pruneTombstones()
                    // Hide torrents we just deleted but TorBox's list still returns (see
                    // [deletedIds]).
                    body.data
                        .orEmpty()
                        .filterNot { deletedIds.containsKey(it.id) }
                        .map { it.toUnified() }
                } else {
                    Timber.d("TorBox getTorrentsList failed: ${describe(response, body)}")
                    emptyList()
                }
            } catch (e: Exception) {
                Timber.e(e, "TorBox getTorrentsList error")
                emptyList()
            }
        }

    suspend fun getTorrentInfo(id: Long): UnifiedTorrent? =
        withContext(Dispatchers.IO) {
            try {
                val response = torBoxApi.getTorrentInfo(TORBOX_API_VERSION, bearer(), id)
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    body.data?.toUnified()
                } else null
            } catch (e: Exception) {
                Timber.e(e, "TorBox getTorrentInfo error")
                null
            }
        }

    /** Like [getTorrentInfo] but returns the raw TorBox torrent, including its file list. */
    suspend fun getRawTorrentInfo(id: Long): TorBoxTorrent? =
        withContext(Dispatchers.IO) {
            try {
                val response = torBoxApi.getTorrentInfo(TORBOX_API_VERSION, bearer(), id)
                val body = response.body()
                if (response.isSuccessful && body?.success == true) body.data else null
            } catch (e: Exception) {
                Timber.e(e, "TorBox getRawTorrentInfo error")
                null
            }
        }

    suspend fun addMagnet(
        magnet: String,
        seed: Int? = null,
        allowZip: Boolean? = null,
        name: String? = null,
        asQueued: Boolean? = null,
    ): EitherResult<UnchainedNetworkException, Long> =
        withContext(Dispatchers.IO) {
            try {
                val response =
                    torBoxApi.createTorrentFromMagnet(
                        apiVersion = TORBOX_API_VERSION,
                        token = bearer(),
                        magnet = magnet.asPart(),
                        seed = seed?.toString()?.asPart(),
                        allowZip = allowZip?.toString()?.asPart(),
                        name = name?.asPart(),
                        asQueued = asQueued?.toString()?.asPart(),
                    )
                val body = response.body()
                val newId = body?.data?.torrentId ?: body?.data?.queuedId
                if (response.isSuccessful && body?.success == true && newId != null) {
                    // Re-adding clears any stale tombstone so the torrent can show again.
                    deletedIds.remove(newId)
                    markListStale()
                    EitherResult.Success(newId)
                } else {
                    EitherResult.Failure(NetworkError(response.code(), describe(response, body)))
                }
            } catch (e: Exception) {
                Timber.e(e, "TorBox addMagnet error")
                EitherResult.Failure(NetworkError(-1, e.message ?: "Error adding magnet"))
            }
        }

    suspend fun addTorrentFile(
        torrentFile: File,
        name: String? = null,
        seed: Int? = null,
        allowZip: Boolean? = null,
        asQueued: Boolean? = null,
    ): EitherResult<UnchainedNetworkException, Long> =
        withContext(Dispatchers.IO) {
            try {
                val requestFile =
                    torrentFile.asRequestBody("application/x-bittorrent".toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("file", torrentFile.name, requestFile)
                val response =
                    torBoxApi.createTorrentFromFile(
                        apiVersion = TORBOX_API_VERSION,
                        token = bearer(),
                        file = part,
                        seed = seed?.toString()?.asPart(),
                        allowZip = allowZip?.toString()?.asPart(),
                        name = name?.asPart(),
                        asQueued = asQueued?.toString()?.asPart(),
                    )
                val body = response.body()
                val newId = body?.data?.torrentId ?: body?.data?.queuedId
                if (response.isSuccessful && body?.success == true && newId != null) {
                    // Re-adding clears any stale tombstone so the torrent can show again.
                    deletedIds.remove(newId)
                    markListStale()
                    EitherResult.Success(newId)
                } else {
                    EitherResult.Failure(NetworkError(response.code(), describe(response, body)))
                }
            } catch (e: Exception) {
                Timber.e(e, "TorBox addTorrentFile error")
                EitherResult.Failure(NetworkError(-1, e.message ?: "Error uploading torrent"))
            }
        }

    /** Overload accepting raw bytes, matching [TorrentsRepository.addTorrent]'s call site. */
    suspend fun addTorrent(
        binaryTorrent: ByteArray,
        name: String? = null,
    ): EitherResult<UnchainedNetworkException, Long> =
        withContext(Dispatchers.IO) {
            try {
                val body =
                    binaryTorrent.toRequestBody(
                        "application/x-bittorrent".toMediaTypeOrNull(),
                        0,
                        binaryTorrent.size,
                    )
                val part = MultipartBody.Part.createFormData("file", "upload.torrent", body)
                val response =
                    torBoxApi.createTorrentFromFile(
                        apiVersion = TORBOX_API_VERSION,
                        token = bearer(),
                        file = part,
                        name = name?.asPart(),
                    )
                val resp = response.body()
                val newId = resp?.data?.torrentId ?: resp?.data?.queuedId
                if (response.isSuccessful && resp?.success == true && newId != null) {
                    // Re-adding clears any stale tombstone so the torrent can show again.
                    deletedIds.remove(newId)
                    markListStale()
                    EitherResult.Success(newId)
                } else {
                    EitherResult.Failure(NetworkError(response.code(), describe(response, resp)))
                }
            } catch (e: Exception) {
                Timber.e(e, "TorBox addTorrent error")
                EitherResult.Failure(NetworkError(-1, e.message ?: "Error uploading torrent"))
            }
        }

    suspend fun deleteTorrent(id: Long): EitherResult<UnchainedNetworkException, Unit> =
        controlTorrent(id, "delete")

    suspend fun pauseTorrent(id: Long): EitherResult<UnchainedNetworkException, Unit> =
        controlTorrent(id, "pause")

    suspend fun resumeTorrent(id: Long): EitherResult<UnchainedNetworkException, Unit> =
        controlTorrent(id, "resume")

    private suspend fun controlTorrent(
        id: Long,
        operation: String,
    ): EitherResult<UnchainedNetworkException, Unit> =
        withContext(Dispatchers.IO) {
            try {
                val response =
                    torBoxApi.controlTorrent(
                        TORBOX_API_VERSION,
                        bearer(),
                        TorBoxControlTorrentRequest(id, operation),
                    )
                val body = response.body()
                // TorBox returns HTTP 200 even when it rejects the operation, signalling the real
                // outcome only via the `success` envelope field. Checking the status alone made a
                // failed delete look successful (so the torrent stayed in the list).
                if (response.isSuccessful && body?.success == true) {
                    // TorBox keeps listing a deleted torrent for a moment; tombstone it so the
                    // post-delete refresh doesn't bring it back (see [deletedIds]).
                    if (operation == "delete") deletedIds[id] = System.currentTimeMillis()
                    markListStale()
                    EitherResult.Success(Unit)
                } else {
                    Timber.e("TorBox $operation failed: ${describe(response, body)}")
                    EitherResult.Failure(NetworkError(response.code(), describe(response, body)))
                }
            } catch (e: Exception) {
                Timber.e(e, "TorBox controlTorrent ($operation) error")
                EitherResult.Failure(NetworkError(-1, e.message ?: "Error: $operation"))
            }
        }

    /**
     * Resolve a temporary CDN link for a file inside a torrent. Pass null [fileId] with [zipLink] =
     * true to download the whole torrent as a zip.
     */
    suspend fun getDownloadLink(
        torrentId: Long,
        fileId: Long?,
        zipLink: Boolean? = null,
    ): EitherResult<UnchainedNetworkException, String> =
        withContext(Dispatchers.IO) {
            val key =
                keyRepository.getApiKey()
                    ?: return@withContext EitherResult.Failure(
                        NetworkError(-1, "Missing TorBox API key")
                    )
            try {
                val response =
                    torBoxApi.requestDownloadLink(
                        TORBOX_API_VERSION,
                        key,
                        torrentId,
                        fileId,
                        zipLink,
                    )
                val body = response.body()
                val link = body?.data
                if (response.isSuccessful && body?.success == true && !link.isNullOrBlank()) {
                    EitherResult.Success(link)
                } else {
                    EitherResult.Failure(NetworkError(response.code(), describe(response, body)))
                }
            } catch (e: Exception) {
                Timber.e(e, "TorBox getDownloadLink error")
                EitherResult.Failure(NetworkError(-1, e.message ?: "Error resolving link"))
            }
        }

    private fun describe(response: Response<*>, body: TorBoxResponse?): String =
        body?.detail ?: body?.error ?: "HTTP ${response.code()}"

    private companion object {
        // How long a deleted torrent stays hidden before we trust TorBox's list again. Comfortably
        // longer than the observed window in which `/mylist` keeps returning a just-deleted
        // torrent,
        // while still bounding the suppression so a tombstone can never hide a torrent
        // indefinitely.
        const val DELETED_TOMBSTONE_MS = 5 * 60 * 1000L
    }
}
