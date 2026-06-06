package com.github.livingwithhippos.unchained.data.model

import android.os.Parcelable
import com.github.livingwithhippos.unchained.data.model.torbox.TorBoxTorrent
import kotlinx.parcelize.Parcelize

/**
 * Identifies which debrid backend an item belongs to. Used across the unified torrents list so the
 * UI can show an icon/label and so repositories can route actions to the correct API.
 */
enum class DebridService(val tag: String) {
    REAL_DEBRID("real_debrid"),
    TORBOX("torbox");

    companion object {
        fun fromTag(tag: String?): DebridService? = entries.firstOrNull { it.tag == tag }
    }
}

/**
 * A backend-agnostic torrent status. Both RealDebrid's statuses and TorBox's qBittorrent-style
 * download states are mapped onto this small set so the list/details UI can reason about state
 * without caring which service produced the item.
 */
enum class UnifiedTorrentStatus {
    QUEUED,
    DOWNLOADING_METADATA,
    DOWNLOADING,
    UPLOADING, // seeding
    STALLED, // trying to download, no seeds
    PROCESSING, // compressing / uploading to debrid cloud (RD)
    READY, // fully available on the debrid cloud, downloadable by the user
    ERROR,
    UNKNOWN,
}

/**
 * Unified, UI-facing torrent. This is the single type the torrents list, details screen and
 * selection logic should consume. [service] + [serviceId] together uniquely identify an item, and
 * [rawId] is the id string to pass back to the originating service's API.
 *
 * Keeping a single model (instead of a sealed class per service) means the existing paging adapter,
 * DiffUtil and selection tracker only need to know about one type; the service is just a field.
 */
@Parcelize
data class UnifiedTorrent(
    val service: DebridService,
    // id as the originating API expects it (RD: hash-like string, TorBox: numeric as string)
    val rawId: String,
    val name: String,
    val hash: String?,
    // size of the torrent in bytes
    val bytes: Long,
    // normalized 0..100
    val progress: Float,
    val status: UnifiedTorrentStatus,
    // raw status string from the backend, kept for display/debugging
    val rawStatus: String,
    val added: String?,
    val speed: Long?,
    val seeders: Int?,
    // direct host/CDN links when already resolved (RD provides these on info; TorBox resolves
    // per-file via requestdl, so this may be empty for TorBox until requested)
    val links: List<String>,
    // MIGRATION BRIDGE: the original RealDebrid item, present only for RD rows. Lets the existing
    // RD-typed details/processing navigation keep working unchanged while the details screen is
    // migrated to UnifiedTorrent. Remove once details accept UnifiedTorrent (roadmap §5/§6).
    val realDebridItem: TorrentItem? = null,
) : Parcelable {
    /** Stable composite key for DiffUtil / selection across both services. */
    val unifiedId: String
        get() = "${service.tag}:$rawId"
}

/* --------------------------------------------------------------------------------------------- */
/*  Mappers                                                                                       */
/* --------------------------------------------------------------------------------------------- */

fun TorrentItem.toUnified(): UnifiedTorrent =
    UnifiedTorrent(
        service = DebridService.REAL_DEBRID,
        rawId = id,
        name = filename,
        hash = hash,
        bytes = bytes,
        progress = progress,
        status = mapRealDebridStatus(status),
        rawStatus = status,
        added = added,
        speed = speed?.toLong(),
        seeders = seeders,
        links = links,
        realDebridItem = this,
    )

fun TorBoxTorrent.toUnified(): UnifiedTorrent =
    UnifiedTorrent(
        service = DebridService.TORBOX,
        rawId = id.toString(),
        name = name ?: hash ?: id.toString(),
        hash = hash,
        bytes = size ?: 0L,
        // TorBox progress is 0.0..1.0
        progress = ((progress ?: 0f) * 100f),
        status = mapTorBoxStatus(this),
        rawStatus = downloadState ?: "",
        added = createdAt,
        speed = downloadSpeed,
        seeders = seeds,
        links = emptyList(),
    )

private fun mapRealDebridStatus(status: String): UnifiedTorrentStatus =
    when (status) {
        "magnet_conversion" -> UnifiedTorrentStatus.DOWNLOADING_METADATA
        "waiting_files_selection" -> UnifiedTorrentStatus.QUEUED
        "queued" -> UnifiedTorrentStatus.QUEUED
        "downloading" -> UnifiedTorrentStatus.DOWNLOADING
        "downloaded" -> UnifiedTorrentStatus.READY
        "compressing",
        "uploading" -> UnifiedTorrentStatus.PROCESSING
        "magnet_error",
        "error",
        "virus",
        "dead" -> UnifiedTorrentStatus.ERROR
        else -> UnifiedTorrentStatus.UNKNOWN
    }

private fun mapTorBoxStatus(torrent: TorBoxTorrent): UnifiedTorrentStatus {
    // TorBox marks an item available either when fully downloaded to its cloud or when cached.
    if (torrent.downloadFinished == true || torrent.downloadPresent == true) {
        return UnifiedTorrentStatus.READY
    }
    return when (torrent.downloadState?.lowercase()) {
        "completed",
        "cached" -> UnifiedTorrentStatus.READY
        "downloading" -> UnifiedTorrentStatus.DOWNLOADING
        "uploading" -> UnifiedTorrentStatus.UPLOADING
        "paused" -> UnifiedTorrentStatus.QUEUED
        "metadl" -> UnifiedTorrentStatus.DOWNLOADING_METADATA
        "checkingresumedata" -> UnifiedTorrentStatus.PROCESSING
        null -> UnifiedTorrentStatus.UNKNOWN
        else -> {
            // qBittorrent emits e.g. "stalled (no seeds)"
            if (torrent.downloadState.contains("stalled")) UnifiedTorrentStatus.STALLED
            else UnifiedTorrentStatus.UNKNOWN
        }
    }
}
