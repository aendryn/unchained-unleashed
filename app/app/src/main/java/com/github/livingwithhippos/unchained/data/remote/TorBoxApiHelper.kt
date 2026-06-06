package com.github.livingwithhippos.unchained.data.remote

import com.github.livingwithhippos.unchained.data.model.torbox.TorBoxCreateTorrentResponse
import com.github.livingwithhippos.unchained.data.model.torbox.TorBoxDownloadLinkResponse
import com.github.livingwithhippos.unchained.data.model.torbox.TorBoxTorrentListResponse
import com.github.livingwithhippos.unchained.data.model.torbox.TorBoxTorrentResponse
import com.github.livingwithhippos.unchained.data.model.torbox.TorBoxUserResponse
import okhttp3.MultipartBody
import retrofit2.Response

interface TorBoxApiHelper {

    suspend fun getUser(token: String): Response<TorBoxUserResponse>

    suspend fun getTorrentsList(
        token: String,
        offset: Int?,
        limit: Int?,
        bypassCache: Boolean?,
    ): Response<TorBoxTorrentListResponse>

    suspend fun getTorrentInfo(token: String, id: Long): Response<TorBoxTorrentResponse>

    suspend fun createTorrentFromMagnet(
        token: String,
        magnet: String,
        seed: Int?,
        allowZip: Boolean?,
        name: String?,
        asQueued: Boolean?,
    ): Response<TorBoxCreateTorrentResponse>

    suspend fun createTorrentFromFile(
        token: String,
        file: MultipartBody.Part,
        seed: Int?,
        allowZip: Boolean?,
        name: String?,
        asQueued: Boolean?,
    ): Response<TorBoxCreateTorrentResponse>

    suspend fun controlTorrent(token: String, torrentId: Long, operation: String): Response<Unit>

    suspend fun requestDownloadLink(
        apiKey: String,
        torrentId: Long,
        fileId: Long?,
        zipLink: Boolean?,
    ): Response<TorBoxDownloadLinkResponse>
}
