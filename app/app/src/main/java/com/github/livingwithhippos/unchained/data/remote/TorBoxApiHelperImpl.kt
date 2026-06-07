package com.github.livingwithhippos.unchained.data.remote

import com.github.livingwithhippos.unchained.data.model.torbox.TorBoxControlTorrentRequest
import com.github.livingwithhippos.unchained.data.model.torbox.TorBoxControlTorrentResponse
import com.github.livingwithhippos.unchained.data.model.torbox.TorBoxCreateTorrentResponse
import com.github.livingwithhippos.unchained.data.model.torbox.TorBoxDownloadLinkResponse
import com.github.livingwithhippos.unchained.data.model.torbox.TorBoxTorrentListResponse
import com.github.livingwithhippos.unchained.data.model.torbox.TorBoxTorrentResponse
import com.github.livingwithhippos.unchained.data.model.torbox.TorBoxUserResponse
import com.github.livingwithhippos.unchained.utilities.TORBOX_API_VERSION
import javax.inject.Inject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response

class TorBoxApiHelperImpl @Inject constructor(private val torBoxApi: TorBoxApi) : TorBoxApiHelper {

    private val plainText = "text/plain".toMediaTypeOrNull()

    private fun String.asPart(): RequestBody = this.toRequestBody(plainText)

    override suspend fun getUser(token: String): Response<TorBoxUserResponse> =
        torBoxApi.getUser(TORBOX_API_VERSION, token)

    override suspend fun getTorrentsList(
        token: String,
        offset: Int?,
        limit: Int?,
        bypassCache: Boolean?,
    ): Response<TorBoxTorrentListResponse> =
        torBoxApi.getTorrentsList(TORBOX_API_VERSION, token, bypassCache, offset, limit)

    override suspend fun getTorrentInfo(token: String, id: Long): Response<TorBoxTorrentResponse> =
        torBoxApi.getTorrentInfo(TORBOX_API_VERSION, token, id)

    override suspend fun createTorrentFromMagnet(
        token: String,
        magnet: String,
        seed: Int?,
        allowZip: Boolean?,
        name: String?,
        asQueued: Boolean?,
    ): Response<TorBoxCreateTorrentResponse> =
        torBoxApi.createTorrentFromMagnet(
            apiVersion = TORBOX_API_VERSION,
            token = token,
            magnet = magnet.asPart(),
            seed = seed?.toString()?.asPart(),
            allowZip = allowZip?.toString()?.asPart(),
            name = name?.asPart(),
            asQueued = asQueued?.toString()?.asPart(),
        )

    override suspend fun createTorrentFromFile(
        token: String,
        file: MultipartBody.Part,
        seed: Int?,
        allowZip: Boolean?,
        name: String?,
        asQueued: Boolean?,
    ): Response<TorBoxCreateTorrentResponse> =
        torBoxApi.createTorrentFromFile(
            apiVersion = TORBOX_API_VERSION,
            token = token,
            file = file,
            seed = seed?.toString()?.asPart(),
            allowZip = allowZip?.toString()?.asPart(),
            name = name?.asPart(),
            asQueued = asQueued?.toString()?.asPart(),
        )

    override suspend fun controlTorrent(
        token: String,
        torrentId: Long,
        operation: String,
    ): Response<TorBoxControlTorrentResponse> =
        torBoxApi.controlTorrent(
            TORBOX_API_VERSION,
            token,
            TorBoxControlTorrentRequest(torrentId, operation),
        )

    override suspend fun requestDownloadLink(
        apiKey: String,
        torrentId: Long,
        fileId: Long?,
        zipLink: Boolean?,
    ): Response<TorBoxDownloadLinkResponse> =
        torBoxApi.requestDownloadLink(TORBOX_API_VERSION, apiKey, torrentId, fileId, zipLink)
}
