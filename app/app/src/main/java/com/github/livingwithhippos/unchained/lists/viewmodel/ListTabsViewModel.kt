package com.github.livingwithhippos.unchained.lists.viewmodel

import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.liveData
import com.github.livingwithhippos.unchained.data.model.DebridService
import com.github.livingwithhippos.unchained.data.model.DownloadItem
import com.github.livingwithhippos.unchained.data.model.NetworkError
import com.github.livingwithhippos.unchained.data.model.UnchainedNetworkException
import com.github.livingwithhippos.unchained.data.model.UnifiedTorrent
import com.github.livingwithhippos.unchained.data.model.UnifiedTorrentStatus
import com.github.livingwithhippos.unchained.data.repository.DebridManager
import com.github.livingwithhippos.unchained.data.repository.DownloadRepository
import com.github.livingwithhippos.unchained.data.repository.TorBoxDownloadsRepository
import com.github.livingwithhippos.unchained.data.repository.TorBoxTorrentsRepository
import com.github.livingwithhippos.unchained.data.repository.TorrentsRepository
import com.github.livingwithhippos.unchained.data.repository.UnrestrictRepository
import com.github.livingwithhippos.unchained.data.repository.isTorBoxDownload
import com.github.livingwithhippos.unchained.lists.model.DownloadPagingSource
import com.github.livingwithhippos.unchained.lists.model.UnifiedTorrentPagingSource
import com.github.livingwithhippos.unchained.utilities.DOWNLOADS_TAB
import com.github.livingwithhippos.unchained.utilities.EitherResult
import com.github.livingwithhippos.unchained.utilities.Event
import com.github.livingwithhippos.unchained.utilities.postEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.launch

/**
 * A [ViewModel] subclass. It offers LiveData to be observed to populate lists with paging support
 */
@HiltViewModel
class ListTabsViewModel
@Inject
constructor(
    private val savedStateHandle: SavedStateHandle,
    private val preferences: SharedPreferences,
    private val downloadRepository: DownloadRepository,
    private val torrentsRepository: TorrentsRepository,
    private val torBoxTorrentsRepository: TorBoxTorrentsRepository,
    private val torBoxDownloadsRepository: TorBoxDownloadsRepository,
    private val debridManager: DebridManager,
    private val unrestrictRepository: UnrestrictRepository,
) : ViewModel() {

    private val MAX_PAGE_SIZE = 2500
    private val INITIAL_LOAD = 100

    // stores the last query value
    private val queryLiveData = MutableLiveData<String>()

    // items are filtered returning only if their names contain the query
    val downloadsLiveData: LiveData<PagingData<DownloadItem>> =
        queryLiveData.switchMap { query: String ->
            val size = getPagingSize()
            val initialSize = max(size, INITIAL_LOAD)
            Pager(PagingConfig(pageSize = size, initialLoadSize = initialSize)) {
                    DownloadPagingSource(downloadRepository, torBoxDownloadsRepository, query)
                }
                .liveData
                .cachedIn(viewModelScope)
        }

    val torrentsLiveData: LiveData<PagingData<UnifiedTorrent>> =
        queryLiveData.switchMap { query: String ->
            val size = getPagingSize()
            val initialSize = max(size, INITIAL_LOAD)
            Pager(PagingConfig(pageSize = size, initialLoadSize = initialSize)) {
                    UnifiedTorrentPagingSource(
                        torrentsRepository,
                        torBoxTorrentsRepository,
                        debridManager,
                        query,
                    )
                }
                .liveData
                .cachedIn(viewModelScope)
        }

    val errorsLiveData = MutableLiveData<Event<List<UnchainedNetworkException>>>()

    val downloadItemLiveData = MutableLiveData<Event<List<DownloadItem>>>()

    val deletedTorrentLiveData = MutableLiveData<Event<Int>>()
    val deletedDownloadLiveData = MutableLiveData<Event<Int>>()

    /** Downloads with links resolved and ready to enqueue (see [prepareDownloads]). */
    val preparedDownloadLiveData = MutableLiveData<Event<List<DownloadItem>>>()

    val eventLiveData = MutableLiveData<Event<ListEvent>>()

    /**
     * Un-restrict a Real-Debrid torrent and move its links to the Downloads tab. The TorBox
     * equivalent is [downloadTorBoxTorrent]; both are dispatched from [downloadItems].
     *
     * @param torrent
     */
    fun unrestrictTorrent(torrent: UnifiedTorrent) {
        val rdItem = torrent.realDebridItem ?: return
        viewModelScope.launch {
            val items = unrestrictRepository.getUnrestrictedLinkList(rdItem.links)
            val values =
                items.filterIsInstance<EitherResult.Success<DownloadItem>>().map { it.success }
            val errors =
                items.filterIsInstance<EitherResult.Failure<UnchainedNetworkException>>().map {
                    it.failure
                }

            downloadItemLiveData.postEvent(values)
            if (errors.isNotEmpty()) errorsLiveData.postEvent(errors)
        }
    }

    private fun getPagingSize(): Int {
        return min(preferences.getInt("paging_size", 50), MAX_PAGE_SIZE)
    }

    fun setSelectedTab(tabID: Int) {
        savedStateHandle[KEY_SELECTED_TAB] = tabID
    }

    fun getSelectedTab(): Int {
        return savedStateHandle[KEY_SELECTED_TAB] ?: DOWNLOADS_TAB
    }

    /**
     * Force the next torrents load to bypass TorBox's server-side cache. In-app mutations already
     * mark the list stale inside [TorBoxTorrentsRepository]; this covers explicit user refreshes
     * (pull-to-refresh / refresh button) where the user is asking for guaranteed-fresh data.
     */
    fun markTorrentsStale() {
        torBoxTorrentsRepository.markListStale()
    }

    /**
     * Whether the torrents list has a pending change (e.g. a torrent deleted from the TorBox
     * details screen) and should be refreshed when the tab next becomes visible. Non-consuming: the
     * refresh's paging load clears the flag and decides whether to bypass TorBox's cache.
     */
    fun torrentsListStale(): Boolean = torBoxTorrentsRepository.peekListStale()

    fun setListFilter(query: String?) {
        // Avoid updating the lists if the query hasn't changed. We don't check for cases but we
        // could
        if (queryLiveData.value != query) queryLiveData.postValue(query?.trim() ?: "")
    }

    fun deleteAllDownloads() {
        viewModelScope.launch {
            deletedDownloadLiveData.postEvent(0)
            var page = 1
            val completeDownloadList = mutableListOf<DownloadItem>()
            do {
                val downloads = downloadRepository.getDownloads(0, page++, 50)
                completeDownloadList.addAll(downloads)
            } while (downloads.size >= 50)

            // post a message every 10% of the deletion progress if there are more than 10 items
            val progressIndicator: Int =
                if (completeDownloadList.size / 10 < 15) 15 else completeDownloadList.size / 10

            completeDownloadList.forEachIndexed { index, item ->
                downloadRepository.deleteDownload(item.id)
                if ((index + 1) % progressIndicator == 0)
                    deletedDownloadLiveData.postEvent(index + 1)
            }

            // The RD list above doesn't include locally stored TorBox downloads; clear those too.
            torBoxDownloadsRepository.deleteAll()

            deletedDownloadLiveData.postEvent(DOWNLOADS_DELETED_ALL)
        }
    }

    fun deleteAllTorrents() {
        viewModelScope.launch {
            val services = debridManager.authenticatedServices()
            if (services.contains(DebridService.REAL_DEBRID)) {
                do {
                    val torrents = torrentsRepository.getTorrentsList(0, 1, 50)
                    torrents.forEach { torrentsRepository.deleteTorrent(it.id) }
                } while (torrents.size >= 50)
            }
            if (services.contains(DebridService.TORBOX)) {
                torBoxTorrentsRepository.getTorrentsList(limit = 1000).forEach {
                    torBoxTorrentsRepository.deleteTorrent(it.rawId.toLong())
                }
            }

            deletedTorrentLiveData.postEvent(TORRENTS_DELETED_ALL)
        }
    }

    fun deleteTorrents(torrents: List<UnifiedTorrent>) {
        viewModelScope.launch {
            val allDeleted = torrents.map { deleteUnified(it) }.all { it }
            when {
                // At least one delete failed: report it instead of a false "removed" toast. The
                // list still refreshes, so anything that did get deleted disappears.
                !allDeleted -> deletedTorrentLiveData.postEvent(TORRENT_NOT_DELETED)
                torrents.size > 1 -> deletedTorrentLiveData.postEvent(TORRENTS_DELETED)
                else -> deletedTorrentLiveData.postEvent(TORRENT_DELETED)
            }
        }
    }

    private suspend fun deleteUnified(torrent: UnifiedTorrent): Boolean =
        when (torrent.service) {
            DebridService.REAL_DEBRID ->
                torrentsRepository.deleteTorrent(torrent.rawId) is EitherResult.Success
            DebridService.TORBOX ->
                torrent.rawId.toLongOrNull()?.let {
                    torBoxTorrentsRepository.deleteTorrent(it) is EitherResult.Success
                } ?: false
        }

    fun downloadItems(torrents: List<UnifiedTorrent>) {
        torrents
            .filter { it.status == UnifiedTorrentStatus.READY }
            .forEach { torrent ->
                when (torrent.service) {
                    DebridService.REAL_DEBRID -> unrestrictTorrent(torrent)
                    DebridService.TORBOX -> downloadTorBoxTorrent(torrent)
                }
            }
    }

    /**
     * TorBox equivalent of [unrestrictTorrent]: resolve every file's CDN link, store them locally
     * and surface them in the Downloads tab. TorBox has no server-side downloads list, so the links
     * are persisted via [TorBoxDownloadsRepository].
     */
    private fun downloadTorBoxTorrent(torrent: UnifiedTorrent) {
        val torrentId = torrent.rawId.toLongOrNull() ?: return
        viewModelScope.launch {
            val raw = torBoxTorrentsRepository.getRawTorrentInfo(torrentId)
            val files = raw?.files.orEmpty()
            if (files.isEmpty()) {
                errorsLiveData.postEvent(
                    listOf(NetworkError(-1, "No downloadable files found for this torrent"))
                )
                return@launch
            }
            val errors = mutableListOf<UnchainedNetworkException>()
            files.forEach { file ->
                when (val link = torBoxTorrentsRepository.getDownloadLink(torrentId, file.id)) {
                    is EitherResult.Success ->
                        torBoxDownloadsRepository.save(
                            torrentId = torrentId,
                            fileId = file.id,
                            fileName = file.shortName ?: file.name ?: torrent.name,
                            size = file.size ?: 0L,
                            mimeType = file.mimetype,
                            downloadUrl = link.success,
                        )
                    is EitherResult.Failure -> errors.add(link.failure)
                }
            }
            // Trigger the Downloads-tab switch + refresh (the observer only checks for non-empty).
            downloadItemLiveData.postEvent(torBoxDownloadsRepository.getDownloads())
            if (errors.isNotEmpty()) errorsLiveData.postEvent(errors)
        }
    }

    /**
     * Prepare a set of downloads for enqueueing. Real-Debrid items pass through unchanged; TorBox
     * items get a freshly resolved link (their stored CDN link may have expired). The result is
     * posted on [preparedDownloadLiveData] for the fragment to enqueue.
     */
    fun prepareDownloads(downloads: List<DownloadItem>) {
        viewModelScope.launch {
            val prepared = downloads.map { item ->
                if (item.isTorBoxDownload())
                    item.copy(download = torBoxDownloadsRepository.refreshLink(item))
                else item
            }
            preparedDownloadLiveData.postEvent(prepared)
        }
    }

    fun deleteDownloads(downloads: List<DownloadItem>) {
        viewModelScope.launch {
            downloads.forEach {
                // TorBox downloads live in the local store; Real-Debrid ones on the RD account.
                if (it.isTorBoxDownload()) torBoxDownloadsRepository.delete(it.id)
                else downloadRepository.deleteDownload(it.id)
            }
            if (downloads.size > 1) deletedDownloadLiveData.postEvent(DOWNLOADS_DELETED)
            else deletedDownloadLiveData.postEvent(DOWNLOAD_DELETED)
        }
    }

    fun postEventNotice(event: ListEvent) {
        eventLiveData.postEvent(event)
    }

    companion object {
        const val KEY_SELECTED_TAB = "selected_tab_key"
        const val TORRENT_DELETED = -1
        const val TORRENTS_DELETED = -2
        const val TORRENTS_DELETED_ALL = -3
        const val TORRENT_NOT_DELETED = -4
        const val DOWNLOAD_DELETED = -1
        const val DOWNLOADS_DELETED = -2
        const val DOWNLOADS_DELETED_ALL = -3
        const val DOWNLOAD_NOT_DELETED = -4
    }
}

sealed class ListEvent {
    data class DownloadItemClick(val item: DownloadItem) : ListEvent()

    data class TorrentItemClick(val item: UnifiedTorrent) : ListEvent()

    data class OpenTorrent(val item: UnifiedTorrent) : ListEvent()

    data class SetTab(val tab: Int) : ListEvent()

    data object NewDownload : ListEvent()
}
