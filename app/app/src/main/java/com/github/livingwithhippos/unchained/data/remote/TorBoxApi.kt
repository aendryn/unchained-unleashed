package com.github.livingwithhippos.unchained.data.remote

import com.github.livingwithhippos.unchained.data.model.torbox.TorBoxCreateTorrentResponse
import com.github.livingwithhippos.unchained.data.model.torbox.TorBoxDownloadLinkResponse
import com.github.livingwithhippos.unchained.data.model.torbox.TorBoxTorrentListResponse
import com.github.livingwithhippos.unchained.data.model.torbox.TorBoxTorrentResponse
import com.github.livingwithhippos.unchained.data.model.torbox.TorBoxUserResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for the TorBox REST API (https://api.torbox.app). All endpoints are versioned
 * with a leading {api_version} path segment (currently "v1"). Authenticated endpoints expect the
 * user's permanent API key as a Bearer token.
 */
interface TorBoxApi {

    /** Validate the API key / fetch the account. Used during authentication. */
    @GET("{api_version}/api/user/me")
    suspend fun getUser(
        @Path("api_version") apiVersion: String,
        @Header("Authorization") token: String,
    ): Response<TorBoxUserResponse>

    /** List the user's torrents (list form). */
    @GET("{api_version}/api/torrents/mylist")
    suspend fun getTorrentsList(
        @Path("api_version") apiVersion: String,
        @Header("Authorization") token: String,
        @Query("bypass_cache") bypassCache: Boolean? = null,
        @Query("offset") offset: Int? = null,
        @Query("limit") limit: Int? = null,
    ): Response<TorBoxTorrentListResponse>

    /** Fetch a single torrent by id (object form). */
    @GET("{api_version}/api/torrents/mylist")
    suspend fun getTorrentInfo(
        @Path("api_version") apiVersion: String,
        @Header("Authorization") token: String,
        @Query("id") id: Long,
        @Query("bypass_cache") bypassCache: Boolean? = true,
    ): Response<TorBoxTorrentResponse>

    /** Create a torrent from a magnet link. */
    @Multipart
    @POST("{api_version}/api/torrents/createtorrent")
    suspend fun createTorrentFromMagnet(
        @Path("api_version") apiVersion: String,
        @Header("Authorization") token: String,
        @Part("magnet") magnet: RequestBody,
        @Part("seed") seed: RequestBody? = null,
        @Part("allow_zip") allowZip: RequestBody? = null,
        @Part("name") name: RequestBody? = null,
        @Part("as_queued") asQueued: RequestBody? = null,
    ): Response<TorBoxCreateTorrentResponse>

    /** Create a torrent by uploading a .torrent file. */
    @Multipart
    @POST("{api_version}/api/torrents/createtorrent")
    suspend fun createTorrentFromFile(
        @Path("api_version") apiVersion: String,
        @Header("Authorization") token: String,
        @Part file: MultipartBody.Part,
        @Part("seed") seed: RequestBody? = null,
        @Part("allow_zip") allowZip: RequestBody? = null,
        @Part("name") name: RequestBody? = null,
        @Part("as_queued") asQueued: RequestBody? = null,
    ): Response<TorBoxCreateTorrentResponse>

    /**
     * Control an existing torrent. [operation] is one of "reannounce", "delete", "resume", "pause".
     * Sent as a form body with the numeric torrent id.
     */
    @FormUrlEncoded
    @POST("{api_version}/api/torrents/controltorrent")
    suspend fun controlTorrent(
        @Path("api_version") apiVersion: String,
        @Header("Authorization") token: String,
        @Field("torrent_id") torrentId: Long,
        @Field("operation") operation: String,
    ): Response<Unit>

    /**
     * Request a temporary CDN download link for a file (or a zip of the whole torrent). Note the
     * API key is passed as the [token] query parameter here, not as a header.
     */
    @GET("{api_version}/api/torrents/requestdl")
    suspend fun requestDownloadLink(
        @Path("api_version") apiVersion: String,
        @Query("token") token: String,
        @Query("torrent_id") torrentId: Long,
        @Query("file_id") fileId: Long? = null,
        @Query("zip_link") zipLink: Boolean? = null,
    ): Response<TorBoxDownloadLinkResponse>
}
