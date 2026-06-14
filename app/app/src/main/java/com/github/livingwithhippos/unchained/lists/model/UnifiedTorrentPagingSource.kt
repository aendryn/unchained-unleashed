package com.github.livingwithhippos.unchained.lists.model

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.github.livingwithhippos.unchained.data.model.DebridService
import com.github.livingwithhippos.unchained.data.model.UnifiedTorrent
import com.github.livingwithhippos.unchained.data.model.toUnified
import com.github.livingwithhippos.unchained.data.repository.DebridManager
import com.github.livingwithhippos.unchained.data.repository.TorBoxTorrentsRepository
import com.github.livingwithhippos.unchained.data.repository.TorrentsRepository
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException

/**
 * Loads the user's torrents from every authenticated debrid service and merges them, newest first,
 * into a single stream of [UnifiedTorrent]. This is where the "any one service or both" requirement
 * lives for the torrents view: it queries exactly the services [DebridManager] reports as
 * authenticated.
 *
 * Incremental rendering: rather than fetching every torrent from every service before showing
 * anything, each service is paged a chunk at a time and the chunks are k-way merged on the fly. The
 * first page paints as soon as one chunk from each service has arrived (the two are fetched
 * concurrently), and more loads only as the user scrolls. The per-service cursors live on this
 * instance; Paging creates a fresh source — resetting them — on every refresh/invalidate.
 *
 * Ordering assumption: each service is expected to return its torrents newest-first across pages
 * (RD orders `/torrents` by date descending; TorBox `/mylist` by id, which tracks recency). Each
 * fetched chunk is also sorted client-side as a safeguard, so the only thing relied on is that page
 * N+1 of a service is older than page N. For accounts whose whole list fits in the first chunk per
 * service (the common case) ordering is exact regardless.
 */
class UnifiedTorrentPagingSource(
    private val torrentsRepository: TorrentsRepository,
    private val torBoxTorrentsRepository: TorBoxTorrentsRepository,
    private val debridManager: DebridManager,
    private val query: String,
) : PagingSource<Int, UnifiedTorrent>() {

    // Loads for a given generation arrive sequentially for append paging, but guard the cursor
    // state
    // anyway so a stray concurrent call can't corrupt the buffers.
    private val mutex = Mutex()
    private var initialized = false
    private var hasRealDebrid = false
    private var hasTorBox = false

    private val rdBuffer = ArrayDeque<UnifiedTorrent>()
    private var rdPage = 1
    private var rdExhausted = false

    private val tbBuffer = ArrayDeque<UnifiedTorrent>()
    private var tbOffset = 0
    private var tbExhausted = false

    // True for the first TorBox chunk of this generation iff the list changed (or the user forced a
    // refresh). Cleared after that chunk so later pages are served from TorBox's cache.
    private var torBoxBypassPending = false

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, UnifiedTorrent> {
        return try {
            mutex.withLock {
                if (!initialized) {
                    val services = debridManager.authenticatedServices()
                    hasRealDebrid = services.contains(DebridService.REAL_DEBRID)
                    hasTorBox = services.contains(DebridService.TORBOX)
                    torBoxBypassPending = hasTorBox && torBoxTorrentsRepository.consumeListStale()
                    initialized = true
                }

                val want = params.loadSize
                val out = ArrayList<UnifiedTorrent>(want)

                // Top both buffers up concurrently so the wait for the first page is the slower
                // service, not the sum of both.
                fillConcurrently(want)

                while (out.size < want) {
                    // A sparse query can filter a whole chunk down to nothing while the service
                    // still has more, so keep pulling until a buffer fills or the service runs out.
                    if (rdBuffer.isEmpty() && hasRealDebrid && !rdExhausted) commitRd(fetchRd())
                    if (tbBuffer.isEmpty() && hasTorBox && !tbExhausted) commitTb(fetchTb(want))

                    val rdHead = rdBuffer.firstOrNull()
                    val tbHead = tbBuffer.firstOrNull()
                    when {
                        rdHead == null && tbHead == null -> break
                        tbHead == null -> out.add(rdBuffer.removeFirst())
                        rdHead == null -> out.add(tbBuffer.removeFirst())
                        // Newest first; `added` is ISO-8601 so reverse-lexicographic == by recency.
                        sortKey(rdHead) >= sortKey(tbHead) -> out.add(rdBuffer.removeFirst())
                        else -> out.add(tbBuffer.removeFirst())
                    }
                }

                val moreToLoad =
                    rdBuffer.isNotEmpty() ||
                        tbBuffer.isNotEmpty() ||
                        (hasRealDebrid && !rdExhausted) ||
                        (hasTorBox && !tbExhausted)

                LoadResult.Page(
                    data = out,
                    prevKey = null,
                    nextKey = if (moreToLoad) (params.key ?: 0) + 1 else null,
                )
            }
        } catch (exception: IOException) {
            LoadResult.Error(exception)
        } catch (exception: HttpException) {
            LoadResult.Error(exception)
        }
    }

    /**
     * Fetch the next chunk of each service that needs one, in parallel, and only commit the results
     * once both have succeeded. Awaiting both before committing means a failure in one cancels the
     * other and leaves every cursor untouched, so Paging's retry re-fetches cleanly instead of
     * skipping a page.
     */
    private suspend fun fillConcurrently(want: Int) = coroutineScope {
        val rdJob =
            if (hasRealDebrid && rdBuffer.isEmpty() && !rdExhausted) async { fetchRd() } else null
        val tbJob =
            if (hasTorBox && tbBuffer.isEmpty() && !tbExhausted) async { fetchTb(want) } else null

        val rdChunk = rdJob?.await()
        val tbChunk = tbJob?.await()
        rdChunk?.let { commitRd(it) }
        tbChunk?.let { commitTb(it) }
    }

    private suspend fun fetchRd(): Chunk {
        // RD's `/torrents` returns an empty body when given an `offset`, so page-based paging is
        // the
        // reliable form (and what the app used before). A fixed page size keeps page boundaries
        // stable no matter how many merged items a given load requests.
        val raw =
            torrentsRepository.getTorrentsList(offset = null, page = rdPage, limit = RD_MAX_CHUNK)
        return Chunk(filterAndSort(raw.map { it.toUnified() }), raw.size, raw.size < RD_MAX_CHUNK)
    }

    private fun commitRd(chunk: Chunk) {
        rdBuffer.addAll(chunk.items)
        rdPage += 1
        rdExhausted = chunk.exhausted
    }

    private suspend fun fetchTb(want: Int): Chunk {
        val size = want.coerceIn(MIN_CHUNK, TB_MAX_CHUNK)
        val raw =
            torBoxTorrentsRepository.getTorrentsList(
                offset = tbOffset,
                limit = size,
                bypassCache = torBoxBypassPending,
            )
        return Chunk(filterAndSort(raw), raw.size, raw.size < size)
    }

    private fun commitTb(chunk: Chunk) {
        tbBuffer.addAll(chunk.items)
        tbOffset += chunk.rawCount
        tbExhausted = chunk.exhausted
        torBoxBypassPending = false
    }

    private fun filterAndSort(items: List<UnifiedTorrent>): List<UnifiedTorrent> {
        val filtered =
            if (query.isBlank()) items
            else items.filter { it.name.contains(query, ignoreCase = true) }
        return filtered.sortedByDescending { sortKey(it) }
    }

    private fun sortKey(torrent: UnifiedTorrent): String = torrent.added ?: ""

    override fun getRefreshKey(state: PagingState<Int, UnifiedTorrent>): Int? = null

    /**
     * One fetched page from a single service: the (filtered, sorted) items plus cursor bookkeeping.
     */
    private class Chunk(val items: List<UnifiedTorrent>, val rawCount: Int, val exhausted: Boolean)

    private companion object {
        // Smallest chunk worth a round trip; keeps tiny configured page sizes from being chatty.
        const val MIN_CHUNK = 50
        // RD caps `/torrents` at 100 entries per request.
        const val RD_MAX_CHUNK = 100
        // TorBox `/mylist` comfortably serves large pages; cap so a huge configured page size still
        // streams in reasonable chunks.
        const val TB_MAX_CHUNK = 1000
    }
}
