package com.github.livingwithhippos.unchained.data.repository

import com.github.livingwithhippos.unchained.data.model.NetworkError
import com.github.livingwithhippos.unchained.data.model.UnchainedNetworkException
import com.github.livingwithhippos.unchained.data.model.UnifiedTorrent
import com.github.livingwithhippos.unchained.data.model.toUnified
import com.github.livingwithhippos.unchained.data.model.torbox.TorBoxResponse
import com.github.livingwithhippos.unchained.data.model.torbox.TorBoxTorrent
import com.github.livingwithhippos.unchained.data.remote.TorBoxApiHelper
import com.github.livingwithhippos.unchained.utilities.EitherResult
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
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
class TorBoxTorrentsRepository
@Inject
constructor(
    private val torBoxApiHelper: TorBoxApiHelper,
    private val keyRepository: TorBoxKeyRepository,
) {

    private fun bearer(): String {
        val key =
            keyRepository.getApiKey() ?: throw IllegalArgumentException("Missing TorBox API key")
        return "Bearer $key"
    }

    suspend fun getTorrentsList(
        offset: Int? = null,
        limit: Int? = 1000,
        bypassCache: Boolean? = null,
    ): List<UnifiedTorrent> =
        withContext(Dispatchers.IO) {
            try {
                val response = torBoxApiHelper.getTorrentsList(bearer(), offset, limit, bypassCache)
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    body.data.orEmpty().map { it.toUnified() }
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
                val response = torBoxApiHelper.getTorrentInfo(bearer(), id)
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
                val response = torBoxApiHelper.getTorrentInfo(bearer(), id)
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
                    torBoxApiHelper.createTorrentFromMagnet(
                        token = bearer(),
                        magnet = magnet,
                        seed = seed,
                        allowZip = allowZip,
                        name = name,
                        asQueued = asQueued,
                    )
                val body = response.body()
                val newId = body?.data?.torrentId ?: body?.data?.queuedId
                if (response.isSuccessful && body?.success == true && newId != null) {
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
                    torBoxApiHelper.createTorrentFromFile(
                        token = bearer(),
                        file = part,
                        seed = seed,
                        allowZip = allowZip,
                        name = name,
                        asQueued = asQueued,
                    )
                val body = response.body()
                val newId = body?.data?.torrentId ?: body?.data?.queuedId
                if (response.isSuccessful && body?.success == true && newId != null) {
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
                    torBoxApiHelper.createTorrentFromFile(
                        token = bearer(),
                        file = part,
                        seed = null,
                        allowZip = null,
                        name = name,
                        asQueued = null,
                    )
                val resp = response.body()
                val newId = resp?.data?.torrentId ?: resp?.data?.queuedId
                if (response.isSuccessful && resp?.success == true && newId != null) {
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
                val response = torBoxApiHelper.controlTorrent(bearer(), id, operation)
                val body = response.body()
                // TorBox returns HTTP 200 even when it rejects the operation, signalling the real
                // outcome only via the `success` envelope field. Checking the status alone made a
                // failed delete look successful (so the torrent stayed in the list).
                if (response.isSuccessful && body?.success == true) {
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
                val response = torBoxApiHelper.requestDownloadLink(key, torrentId, fileId, zipLink)
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
}
