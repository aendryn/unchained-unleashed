package com.github.livingwithhippos.unchained.data.model.torbox

import android.os.Parcelable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.parcelize.Parcelize

/*
 * Every TorBox API response is wrapped in the same envelope:
 * {
 *   "success": true,
 *   "error": null,        // string code or null
 *   "detail": "human readable message",
 *   "data": <T>           // the actual payload, shape depends on the endpoint
 * }
 *
 * Because Moshi cannot directly model a generic data class with reified type info through
 * Retrofit, we declare one concrete envelope per endpoint. They all share the same outer
 * fields so the repository layer can treat them uniformly via [TorBoxResponse].
 */
interface TorBoxResponse {
    val success: Boolean
    val error: String?
    val detail: String?
}

/** Response of GET /v1/api/torrents/mylist (list form, no id parameter). */
@JsonClass(generateAdapter = true)
data class TorBoxTorrentListResponse(
    @param:Json(name = "success") override val success: Boolean,
    @param:Json(name = "error") override val error: String?,
    @param:Json(name = "detail") override val detail: String?,
    @param:Json(name = "data") val data: List<TorBoxTorrent>?,
) : TorBoxResponse

/** Response of GET /v1/api/torrents/mylist?id=... (object form, single torrent). */
@JsonClass(generateAdapter = true)
data class TorBoxTorrentResponse(
    @param:Json(name = "success") override val success: Boolean,
    @param:Json(name = "error") override val error: String?,
    @param:Json(name = "detail") override val detail: String?,
    @param:Json(name = "data") val data: TorBoxTorrent?,
) : TorBoxResponse

/**
 * A single torrent as returned by /torrents/mylist.
 *
 * Note the differences from RealDebrid:
 * - [id] is a numeric id, not a hash-like string
 * - [downloadState] uses qBittorrent-style strings ("downloading", "uploading", "stalled (no
 *   seeds)", "paused", "completed", "cached", "metaDL", ...)
 * - progress is a 0.0..1.0 float, not 0..100
 * - the magnet is returned directly in [magnet]
 */
@JsonClass(generateAdapter = true)
@Parcelize
data class TorBoxTorrent(
    @param:Json(name = "id") val id: Long,
    @param:Json(name = "hash") val hash: String?,
    @param:Json(name = "name") val name: String?,
    @param:Json(name = "magnet") val magnet: String?,
    @param:Json(name = "size") val size: Long?,
    // 0.0 .. 1.0
    @param:Json(name = "progress") val progress: Float?,
    @param:Json(name = "download_state") val downloadState: String?,
    @param:Json(name = "download_speed") val downloadSpeed: Long?,
    @param:Json(name = "upload_speed") val uploadSpeed: Long?,
    @param:Json(name = "seeds") val seeds: Int?,
    @param:Json(name = "peers") val peers: Int?,
    @param:Json(name = "eta") val eta: Long?,
    @param:Json(name = "ratio") val ratio: Float?,
    @param:Json(name = "active") val active: Boolean?,
    @param:Json(name = "download_finished") val downloadFinished: Boolean?,
    @param:Json(name = "download_present") val downloadPresent: Boolean?,
    @param:Json(name = "cached") val cached: Boolean?,
    @param:Json(name = "created_at") val createdAt: String?,
    @param:Json(name = "updated_at") val updatedAt: String?,
    @param:Json(name = "expires_at") val expiresAt: String?,
    @param:Json(name = "server") val server: Int?,
    @param:Json(name = "files") val files: List<TorBoxFile>?,
) : Parcelable

@JsonClass(generateAdapter = true)
@Parcelize
data class TorBoxFile(
    @param:Json(name = "id") val id: Long,
    @param:Json(name = "name") val name: String?,
    @param:Json(name = "short_name") val shortName: String?,
    @param:Json(name = "size") val size: Long?,
    @param:Json(name = "mimetype") val mimetype: String?,
    @param:Json(name = "s3_path") val s3Path: String?,
    @param:Json(name = "md5") val md5: String?,
) : Parcelable

/** Response of POST /v1/api/torrents/createtorrent. */
@JsonClass(generateAdapter = true)
data class TorBoxCreateTorrentResponse(
    @param:Json(name = "success") override val success: Boolean,
    @param:Json(name = "error") override val error: String?,
    @param:Json(name = "detail") override val detail: String?,
    @param:Json(name = "data") val data: TorBoxCreateTorrentData?,
) : TorBoxResponse

@JsonClass(generateAdapter = true)
data class TorBoxCreateTorrentData(
    @param:Json(name = "torrent_id") val torrentId: Long?,
    @param:Json(name = "queued_id") val queuedId: Long?,
    @param:Json(name = "hash") val hash: String?,
    @param:Json(name = "auth_id") val authId: String?,
    @param:Json(name = "active_limit") val activeLimit: Int?,
    @param:Json(name = "current_active_downloads") val currentActiveDownloads: Int?,
)

/** Response of GET /v1/api/torrents/requestdl. [data] is the temporary CDN url (or null). */
@JsonClass(generateAdapter = true)
data class TorBoxDownloadLinkResponse(
    @param:Json(name = "success") override val success: Boolean,
    @param:Json(name = "error") override val error: String?,
    @param:Json(name = "detail") override val detail: String?,
    @param:Json(name = "data") val data: String?,
) : TorBoxResponse

/** Response of GET /v1/api/user/me. Used to validate the API key during authentication. */
@JsonClass(generateAdapter = true)
data class TorBoxUserResponse(
    @param:Json(name = "success") override val success: Boolean,
    @param:Json(name = "error") override val error: String?,
    @param:Json(name = "detail") override val detail: String?,
    @param:Json(name = "data") val data: TorBoxUser?,
) : TorBoxResponse

@JsonClass(generateAdapter = true)
data class TorBoxUser(
    @param:Json(name = "id") val id: Long?,
    @param:Json(name = "email") val email: String?,
    @param:Json(name = "plan") val plan: Int?,
    @param:Json(name = "premium_expires_at") val premiumExpiresAt: String?,
    @param:Json(name = "is_subscribed") val isSubscribed: Boolean?,
)
