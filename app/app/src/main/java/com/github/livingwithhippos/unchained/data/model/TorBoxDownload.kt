package com.github.livingwithhippos.unchained.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A TorBox download the user resolved and sent to the Downloads tab.
 *
 * Real-Debrid downloads live on the RD account and are fetched from its API, but TorBox has no
 * server-side "my downloads" list: it resolves a temporary CDN link per file on demand. To give the
 * Downloads tab parity, we persist the resolved links locally and merge them with the RD list.
 *
 * [id] is a stable synthetic id (`torbox:<torrentId>:<fileId>`, with `zip` for whole-torrent zips)
 * so the same file isn't added twice and deletes can target it. [downloadUrl] is the resolved link;
 * note it is temporary on TorBox's side and may eventually expire.
 */
@Entity(tableName = "torbox_download")
data class TorBoxDownload(
    @PrimaryKey val id: String,
    val torrentId: Long,
    val fileId: Long?,
    val fileName: String,
    val size: Long,
    val mimeType: String?,
    val downloadUrl: String,
    val createdAt: Long,
)
