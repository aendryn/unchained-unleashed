package com.github.livingwithhippos.unchained.lists.model

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.github.livingwithhippos.unchained.data.model.DownloadItem
import com.github.livingwithhippos.unchained.data.repository.DownloadRepository
import com.github.livingwithhippos.unchained.data.repository.TorBoxDownloadsRepository
import java.io.IOException
import retrofit2.HttpException

private const val DOWNLOAD_STARTING_PAGE_INDEX = 1

class DownloadPagingSource(
    private val downloadRepository: DownloadRepository,
    private val torBoxDownloadsRepository: TorBoxDownloadsRepository,
    private val query: String,
) : PagingSource<Int, DownloadItem>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, DownloadItem> {
        val page = params.key ?: DOWNLOAD_STARTING_PAGE_INDEX

        return try {
            val rdDownloads = downloadRepository.getDownloads(null, page, params.loadSize)

            // Locally stored TorBox downloads aren't paged; surface them once, at the top of the
            // first page, so they sit alongside the Real-Debrid list.
            val torBoxDownloads =
                if (page == DOWNLOAD_STARTING_PAGE_INDEX) torBoxDownloadsRepository.getDownloads()
                else emptyList()

            val response =
                (torBoxDownloads + rdDownloads).let { combined ->
                    if (query.isBlank()) combined
                    else combined.filter { it.filename.contains(query, ignoreCase = true) }
                }

            LoadResult.Page(
                data = response,
                prevKey = if (page == DOWNLOAD_STARTING_PAGE_INDEX) null else page - 1,
                // Only the Real-Debrid list is paged; stop when it's exhausted.
                nextKey = if (rdDownloads.isEmpty()) null else page + 1,
            )
        } catch (exception: IOException) {
            return LoadResult.Error(exception)
        } catch (exception: HttpException) {
            return LoadResult.Error(exception)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, DownloadItem>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            // This loads starting from previous page, but since PagingConfig.initialLoadSize spans
            // multiple pages, the initial load will still load items centered around
            // anchorPosition. This also prevents needing to immediately launch prepend due to
            // prefetchDistance.
            state.closestPageToPosition(anchorPosition)?.prevKey
        }
    }
}
