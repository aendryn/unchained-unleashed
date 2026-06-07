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
import retrofit2.HttpException

/**
 * Loads the user's torrents from every authenticated debrid service and merges them into a single
 * list of [UnifiedTorrent]. This is where the "any one service or both" requirement lives for the
 * torrents view: it queries exactly the services [DebridManager] reports as authenticated.
 *
 * v1 simplification: the merged list is returned as a single page. RealDebrid torrents are fetched
 * page-by-page until exhausted and TorBox returns its whole list in one call, so for normal
 * accounts this is a bounded, one-shot load. Cursor-based merge paging can be added later if
 * needed.
 */
class UnifiedTorrentPagingSource(
    private val torrentsRepository: TorrentsRepository,
    private val torBoxTorrentsRepository: TorBoxTorrentsRepository,
    private val debridManager: DebridManager,
    private val query: String,
) : PagingSource<Int, UnifiedTorrent>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, UnifiedTorrent> {
        return try {
            val services = debridManager.authenticatedServices()
            val merged = mutableListOf<UnifiedTorrent>()

            if (services.contains(DebridService.REAL_DEBRID)) {
                var page = 1
                val rdPageSize = 50
                while (true) {
                    val batch = torrentsRepository.getTorrentsList(null, page, rdPageSize)
                    merged.addAll(batch.map { it.toUnified() })
                    if (batch.size < rdPageSize) break
                    page++
                }
            }

            if (services.contains(DebridService.TORBOX)) {
                // Bypass TorBox's server-side cache of /mylist: this source is reloaded on an
                // explicit refresh (e.g. right after a delete or add), and the cached list would
                // otherwise still contain a just-deleted torrent, making the delete look like a
                // no-op. The list is fetched once per refresh, so this stays cheap.
                merged.addAll(
                    torBoxTorrentsRepository.getTorrentsList(limit = 1000, bypassCache = true)
                )
            }

            val filtered =
                if (query.isBlank()) merged
                else merged.filter { it.name.contains(query, ignoreCase = true) }

            // Newest first. `added` is an ISO-8601 string for both services, so a reverse
            // lexicographic sort orders by recency; nulls (unknown) sink to the bottom.
            val sorted = filtered.sortedByDescending { it.added ?: "" }

            LoadResult.Page(data = sorted, prevKey = null, nextKey = null)
        } catch (exception: IOException) {
            LoadResult.Error(exception)
        } catch (exception: HttpException) {
            LoadResult.Error(exception)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, UnifiedTorrent>): Int? = null
}
