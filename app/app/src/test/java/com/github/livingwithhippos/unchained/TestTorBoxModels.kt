package com.github.livingwithhippos.unchained

import com.github.livingwithhippos.unchained.data.model.DebridService
import com.github.livingwithhippos.unchained.data.model.TorrentItem
import com.github.livingwithhippos.unchained.data.model.UnifiedTorrentStatus
import com.github.livingwithhippos.unchained.data.model.toUnified
import com.github.livingwithhippos.unchained.data.model.torbox.TorBoxControlTorrentRequest
import com.github.livingwithhippos.unchained.data.model.torbox.TorBoxDownloadLinkResponse
import com.github.livingwithhippos.unchained.data.model.torbox.TorBoxTorrent
import com.github.livingwithhippos.unchained.data.model.torbox.TorBoxTorrentListResponse
import com.github.livingwithhippos.unchained.data.model.torbox.TorBoxUserResponse
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsing tests for the TorBox response envelope + the controltorrent request body, and mapping
 * tests for TorBoxTorrent -> UnifiedTorrent (progress scaling and status mapping). These cover the
 * pieces the rest of the integration relies on and have no Android dependencies.
 */
class TestTorBoxModels {

    private val moshi: Moshi = Moshi.Builder().build()

    /** Builds a TorBoxTorrent with only the fields a test cares about; the rest default to null. */
    private fun torrent(
        id: Long = 1L,
        hash: String? = "abc",
        name: String? = "Some.Torrent",
        size: Long? = 1000L,
        progress: Float? = null,
        downloadState: String? = null,
        downloadFinished: Boolean? = null,
        downloadPresent: Boolean? = null,
        downloadSpeed: Long? = null,
        seeds: Int? = null,
    ) =
        TorBoxTorrent(
            id = id,
            hash = hash,
            name = name,
            magnet = null,
            size = size,
            progress = progress,
            downloadState = downloadState,
            downloadSpeed = downloadSpeed,
            uploadSpeed = null,
            seeds = seeds,
            peers = null,
            eta = null,
            ratio = null,
            active = null,
            downloadFinished = downloadFinished,
            downloadPresent = downloadPresent,
            cached = null,
            createdAt = "2024-01-01T00:00:00Z",
            updatedAt = null,
            expiresAt = null,
            server = null,
            files = null,
        )

    // ----------------------------------------------------------------------------------- parsing

    @Test
    fun parsesSuccessfulMyListEnvelope() {
        val json =
            """
            {
              "success": true,
              "error": null,
              "detail": "Got user torrents.",
              "data": [
                {
                  "id": 42,
                  "hash": "deadbeef",
                  "name": "Big.Buck.Bunny",
                  "size": 1048576,
                  "progress": 1.0,
                  "download_state": "cached",
                  "download_finished": true,
                  "download_present": true,
                  "seeds": 7
                }
              ]
            }
            """
                .trimIndent()

        val response = moshi.adapter(TorBoxTorrentListResponse::class.java).fromJson(json)!!

        assertTrue(response.success)
        assertNull(response.error)
        assertEquals(1, response.data?.size)
        val t = response.data!!.first()
        assertEquals(42L, t.id)
        assertEquals("Big.Buck.Bunny", t.name)
        assertEquals(1048576L, t.size)
        assertEquals(1.0f, t.progress!!, 0.0001f)
        assertEquals("cached", t.downloadState)
        assertEquals(true, t.downloadFinished)
    }

    @Test
    fun parsesErrorEnvelope() {
        val json =
            """
            {
              "success": false,
              "error": "DATABASE_ERROR",
              "detail": "Something went wrong.",
              "data": null
            }
            """
                .trimIndent()

        val response = moshi.adapter(TorBoxUserResponse::class.java).fromJson(json)!!

        assertEquals(false, response.success)
        assertEquals("DATABASE_ERROR", response.error)
        assertEquals("Something went wrong.", response.detail)
        assertNull(response.data)
    }

    @Test
    fun parsesRequestDownloadLinkResponse() {
        val json =
            """
            {"success":true,"error":null,"detail":"ok","data":"https://cdn.torbox.app/file.mkv?token=x"}
            """
                .trimIndent()

        val response = moshi.adapter(TorBoxDownloadLinkResponse::class.java).fromJson(json)!!

        assertTrue(response.success)
        assertEquals("https://cdn.torbox.app/file.mkv?token=x", response.data)
    }

    @Test
    fun serializesControlTorrentRequestAsJsonObject() {
        // Regression: controltorrent must be a JSON object, not form-urlencoded (TorBox 422s on a
        // form body). See the fix in TorBoxApi.controlTorrent.
        val json =
            moshi
                .adapter(TorBoxControlTorrentRequest::class.java)
                .toJson(TorBoxControlTorrentRequest(36357534L, "resume"))

        assertEquals("""{"torrent_id":36357534,"operation":"resume"}""", json)
    }

    // ----------------------------------------------------------------------------------- mapping

    @Test
    fun torBoxProgressIsScaledToPercent() {
        assertEquals(0f, torrent(progress = 0.0f).toUnified().progress, 0.001f)
        assertEquals(42.5f, torrent(progress = 0.425f).toUnified().progress, 0.001f)
        assertEquals(100f, torrent(progress = 1.0f).toUnified().progress, 0.001f)
        // a missing progress should not crash and should default to 0
        assertEquals(0f, torrent(progress = null).toUnified().progress, 0.001f)
    }

    @Test
    fun torBoxStatusMapping() {
        // completion flags win regardless of the textual state
        assertEquals(
            UnifiedTorrentStatus.READY,
            torrent(downloadState = "downloading", downloadFinished = true).toUnified().status,
        )
        assertEquals(
            UnifiedTorrentStatus.READY,
            torrent(downloadState = "cached").toUnified().status,
        )
        assertEquals(
            UnifiedTorrentStatus.DOWNLOADING,
            torrent(downloadState = "downloading").toUnified().status,
        )
        assertEquals(
            UnifiedTorrentStatus.UPLOADING,
            torrent(downloadState = "uploading").toUnified().status,
        )
        assertEquals(
            UnifiedTorrentStatus.QUEUED,
            torrent(downloadState = "paused").toUnified().status,
        )
        assertEquals(
            UnifiedTorrentStatus.DOWNLOADING_METADATA,
            torrent(downloadState = "metaDL").toUnified().status,
        )
        // qBittorrent-style compound state
        assertEquals(
            UnifiedTorrentStatus.STALLED,
            torrent(downloadState = "stalled (no seeds)").toUnified().status,
        )
        assertEquals(
            UnifiedTorrentStatus.UNKNOWN,
            torrent(downloadState = "something_new").toUnified().status,
        )
    }

    @Test
    fun torBoxNameFallsBackToHashThenId() {
        assertEquals("named", torrent(name = "named", hash = "h", id = 5).toUnified().name)
        assertEquals("h", torrent(name = null, hash = "h", id = 5).toUnified().name)
        assertEquals("5", torrent(name = null, hash = null, id = 5).toUnified().name)
    }

    @Test
    fun torBoxUnifiedIdentity() {
        val u = torrent(id = 99).toUnified()
        assertEquals(DebridService.TORBOX, u.service)
        assertEquals("99", u.rawId)
        assertEquals("torbox:99", u.unifiedId)
        assertNull("TorBox rows must not carry an RD migration item", u.realDebridItem)
    }

    /** Minimal RealDebrid torrent with the given status; other fields are placeholders. */
    private fun rdTorrent(status: String) =
        TorrentItem(
            id = "RDID1",
            filename = "rd.file",
            originalFilename = null,
            hash = "rdhash",
            bytes = 100L,
            originalBytes = null,
            host = "real-debrid.com",
            split = 0,
            progress = 50f,
            status = status,
            added = "2024-01-01T00:00:00Z",
            files = null,
            links = listOf("https://real-debrid.com/d/AAA"),
            ended = null,
            speed = null,
            seeders = null,
        )

    @Test
    fun realDebridStatusMapping() {
        assertEquals(UnifiedTorrentStatus.READY, rdTorrent("downloaded").toUnified().status)
        assertEquals(UnifiedTorrentStatus.DOWNLOADING, rdTorrent("downloading").toUnified().status)
        assertEquals(
            UnifiedTorrentStatus.DOWNLOADING_METADATA,
            rdTorrent("magnet_conversion").toUnified().status,
        )
        assertEquals(UnifiedTorrentStatus.PROCESSING, rdTorrent("compressing").toUnified().status)
        assertEquals(UnifiedTorrentStatus.ERROR, rdTorrent("virus").toUnified().status)
        assertEquals(UnifiedTorrentStatus.UNKNOWN, rdTorrent("brand_new").toUnified().status)
    }

    @Test
    fun realDebridUnifiedKeepsMigrationItemAndProgress() {
        val u = rdTorrent("downloading").toUnified()
        assertEquals(DebridService.REAL_DEBRID, u.service)
        assertEquals("real_debrid:RDID1", u.unifiedId)
        // RD progress is already 0..100 and must pass through unchanged
        assertEquals(50f, u.progress, 0.001f)
        assertEquals("RD rows must keep the migration bridge item", "RDID1", u.realDebridItem?.id)
    }
}
