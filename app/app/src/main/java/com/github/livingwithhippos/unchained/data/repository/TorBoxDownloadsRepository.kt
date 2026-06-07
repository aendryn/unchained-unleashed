package com.github.livingwithhippos.unchained.data.repository

import com.github.livingwithhippos.unchained.data.local.TorBoxDownloadDao
import com.github.livingwithhippos.unchained.data.model.DownloadItem
import com.github.livingwithhippos.unchained.data.model.TorBoxDownload
import com.github.livingwithhippos.unchained.utilities.EitherResult
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Local store for TorBox downloads shown in the Downloads tab. TorBox has no server-side downloads
 * list (unlike Real-Debrid), so resolved links are persisted here and surfaced as [DownloadItem]s
 * so the existing Downloads UI can treat them uniformly. See [TorBoxDownload].
 */
class TorBoxDownloadsRepository
@Inject
constructor(
    private val torBoxDownloadDao: TorBoxDownloadDao,
    private val torBoxTorrentsRepository: TorBoxTorrentsRepository,
) {

    suspend fun save(
        torrentId: Long,
        fileId: Long?,
        fileName: String,
        size: Long,
        mimeType: String?,
        downloadUrl: String,
    ) =
        withContext(Dispatchers.IO) {
            torBoxDownloadDao.insert(
                TorBoxDownload(
                    id = buildId(torrentId, fileId),
                    torrentId = torrentId,
                    fileId = fileId,
                    fileName = fileName,
                    size = size,
                    mimeType = mimeType,
                    downloadUrl = downloadUrl,
                    createdAt = System.currentTimeMillis(),
                )
            )
        }

    /** All stored TorBox downloads as synthesized [DownloadItem]s, newest first. */
    suspend fun getDownloads(): List<DownloadItem> =
        withContext(Dispatchers.IO) { torBoxDownloadDao.getAll().map { it.toDownloadItem() } }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) { torBoxDownloadDao.delete(id) }

    suspend fun deleteAll() = withContext(Dispatchers.IO) { torBoxDownloadDao.deleteAll() }

    /**
     * Re-resolve a fresh CDN link for a stored TorBox download just before it's consumed. TorBox's
     * `requestdl` links are temporary, so the link saved when the item was added may have expired;
     * this asks TorBox for a new one and updates the store. Falls back to the stored link if the id
     * can't be parsed or the request fails.
     */
    suspend fun refreshLink(item: DownloadItem): String =
        withContext(Dispatchers.IO) {
            val (torrentId, fileId) = parseId(item.id) ?: return@withContext item.download
            val result =
                torBoxTorrentsRepository.getDownloadLink(
                    torrentId = torrentId,
                    fileId = fileId,
                    zipLink = fileId == null,
                )
            when (result) {
                is EitherResult.Success -> {
                    torBoxDownloadDao.updateUrl(item.id, result.success)
                    result.success
                }
                is EitherResult.Failure -> item.download
            }
        }

    companion object {
        /** Prefix marking a [DownloadItem] as a locally stored TorBox download. */
        const val TORBOX_DOWNLOAD_ID_PREFIX = "torbox:"

        fun buildId(torrentId: Long, fileId: Long?): String =
            "$TORBOX_DOWNLOAD_ID_PREFIX$torrentId:${fileId ?: "zip"}"

        /**
         * Parse a synthetic id back into (torrentId, fileId); fileId is null for whole-zip items.
         */
        fun parseId(id: String): Pair<Long, Long?>? {
            if (!id.startsWith(TORBOX_DOWNLOAD_ID_PREFIX)) return null
            val parts = id.removePrefix(TORBOX_DOWNLOAD_ID_PREFIX).split(":")
            if (parts.size != 2) return null
            val torrentId = parts[0].toLongOrNull() ?: return null
            val fileId = if (parts[1] == "zip") null else parts[1].toLongOrNull() ?: return null
            return torrentId to fileId
        }
    }
}

/** True if this download is a locally stored TorBox download rather than a Real-Debrid one. */
fun DownloadItem.isTorBoxDownload(): Boolean =
    id.startsWith(TorBoxDownloadsRepository.TORBOX_DOWNLOAD_ID_PREFIX)

private fun TorBoxDownload.toDownloadItem(): DownloadItem =
    DownloadItem(
        id = id,
        filename = fileName,
        mimeType = mimeType,
        fileSize = size,
        link = downloadUrl,
        host = "torbox.app",
        hostIcon = null,
        chunks = 1,
        crc = null,
        download = downloadUrl,
        // Mark media files streamable so the player / cast buttons show. TorBox has no transcoding
        // endpoint, so only direct playback (send-to-player, Kodi/VLC) is offered, not RD
        // streaming.
        streamable =
            if (mimeType != null && (mimeType.startsWith("video") || mimeType.startsWith("audio")))
                1
            else 0,
        generated = null,
        type = null,
        alternative = null,
    )
