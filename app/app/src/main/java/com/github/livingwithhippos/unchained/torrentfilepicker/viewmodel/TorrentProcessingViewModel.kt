package com.github.livingwithhippos.unchained.torrentfilepicker.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.livingwithhippos.unchained.data.model.EmptyBodyError
import com.github.livingwithhippos.unchained.data.model.TorrentItem
import com.github.livingwithhippos.unchained.data.model.UnchainedNetworkException
import com.github.livingwithhippos.unchained.data.model.UploadedTorrent
import com.github.livingwithhippos.unchained.data.repository.TorBoxTorrentsRepository
import com.github.livingwithhippos.unchained.data.repository.TorrentsRepository
import com.github.livingwithhippos.unchained.torrentdetails.model.TorrentFileItem
import com.github.livingwithhippos.unchained.utilities.EitherResult
import com.github.livingwithhippos.unchained.utilities.Event
import com.github.livingwithhippos.unchained.utilities.Node
import com.github.livingwithhippos.unchained.utilities.beforeSelectionStatusList
import com.github.livingwithhippos.unchained.utilities.extension.cancelIfActive
import com.github.livingwithhippos.unchained.utilities.postEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class TorrentProcessingViewModel
@Inject
constructor(
    private val savedStateHandle: SavedStateHandle,
    private val torrentsRepository: TorrentsRepository,
    private val torBoxTorrentsRepository: TorBoxTorrentsRepository,
) : ViewModel() {

    val networkExceptionLiveData = MutableLiveData<Event<UnchainedNetworkException>>()
    val torrentLiveData = MutableLiveData<Event<TorrentEvent>>()
    val structureLiveData = MutableLiveData<Event<Node<TorrentFileItem>>>()

    private var job = Job()

    fun fetchAddedMagnet(magnet: String) {
        viewModelScope.launch {
            val availableHosts = torrentsRepository.getAvailableHosts()
            if (availableHosts.isNullOrEmpty()) {
                Timber.e("Error fetching available hosts")
            } else {
                val addedMagnet = torrentsRepository.addMagnet(magnet, availableHosts.first().host)
                when (addedMagnet) {
                    is EitherResult.Failure -> {
                        Timber.e("Error adding magnet: ${addedMagnet.failure}")
                        networkExceptionLiveData.postEvent(addedMagnet.failure)
                    }
                    is EitherResult.Success -> {
                        setTorrentID(addedMagnet.success.id)
                        torrentLiveData.postEvent(TorrentEvent.Uploaded(addedMagnet.success))
                    }
                }
            }
        }
    }

    fun fetchTorrentDetails(torrentID: String) {

        setTorrentID(torrentID)

        viewModelScope.launch {
            var torrentData: TorrentItem? = torrentsRepository.getTorrentInfo(torrentID)
            // A freshly added magnet starts in "magnet_conversion" with no files yet. Poll until
            // Real-Debrid finishes converting it (files become available / it leaves conversion)
            // so the file-selection screen isn't shown empty. This matters especially for the
            // "Both" flow, where adding to TorBox first delays the Real-Debrid add.
            var attempts = 0
            while (
                torrentData != null &&
                    torrentData.status == MAGNET_CONVERSION_STATUS &&
                    torrentData.files.isNullOrEmpty() &&
                    attempts < MAX_CONVERSION_POLLS
            ) {
                delay(CONVERSION_POLL_DELAY_MS)
                torrentData = torrentsRepository.getTorrentInfo(torrentID)
                attempts++
            }
            // todo: replace using either
            if (torrentData != null) {
                setTorrentDetails(torrentData)
                torrentLiveData.postEvent(TorrentEvent.TorrentInfo(torrentData))
            } else {
                // The torrent info came back null — e.g. Real-Debrid dropped a magnet it couldn't
                // resolve and now returns 404. Surface a failure instead of hanging forever on the
                // loading screen.
                Timber.e("Retrieved torrent info were null for id $torrentID")
                torrentLiveData.postEvent(TorrentEvent.DownloadedFileFailure)
            }
        }
    }

    private fun setTorrentDetails(item: TorrentItem) {
        savedStateHandle[KEY_CURRENT_TORRENT] = item
    }

    fun getTorrentID(): String? {
        return savedStateHandle[KEY_CURRENT_TORRENT_ID]
    }

    private fun setTorrentID(id: String) {
        savedStateHandle[KEY_CURRENT_TORRENT_ID] = id
    }

    fun updateTorrentStructure() {
        torrentLiveData.postEvent(TorrentEvent.SelectionUpdated)
    }

    fun startSelectionLoop(files: String = "all") {

        val id = getTorrentID()

        if (id == null) {
            Timber.e("Torrent files selection requested but torrent id was not ready")
            return
        }

        job.cancelIfActive()
        job = Job()

        val scope = CoroutineScope(job + Dispatchers.IO)

        scope.launch {
            var selected = false
            // / maybe job.isActive?
            while (isActive) {
                if (!selected) {
                    when (val selectResponse = torrentsRepository.selectFiles(id, files)) {
                        is EitherResult.Failure -> {
                            if (selectResponse.failure is EmptyBodyError) {
                                Timber.d(
                                    "Select torrent files success returned ${selectResponse.failure.returnCode}"
                                )
                                selected = true
                            } else {
                                Timber.e(
                                    "Exception during torrent files selection call: ${selectResponse.failure}"
                                )
                            }
                        }
                        is EitherResult.Success -> {
                            Timber.d("Select torrent files success")
                            selected = true
                        }
                    }
                }

                if (selected) {
                    val torrentItem: TorrentItem? = torrentsRepository.getTorrentInfo(id)
                    if (torrentItem != null) {
                        if (!beforeSelectionStatusList.contains(torrentItem.status)) {
                            job.cancelIfActive()
                            torrentLiveData.postEvent(TorrentEvent.FilesSelected(torrentItem))
                        }
                    }
                }
                delay(1500)
            }
        }
    }

    fun triggerTorrentEvent(event: TorrentEvent) {
        torrentLiveData.postEvent(event)
    }

    /** Add a magnet to TorBox only. It starts automatically, so no file-selection step. */
    fun addMagnetTorBox(magnet: String) {
        viewModelScope.launch {
            when (val res = torBoxTorrentsRepository.addMagnet(magnet)) {
                is EitherResult.Failure -> networkExceptionLiveData.postEvent(res.failure)
                is EitherResult.Success -> torrentLiveData.postEvent(TorrentEvent.TorBoxAdded)
            }
        }
    }

    /** Add a magnet to both services: TorBox best-effort, then the Real-Debrid file-picker flow. */
    fun addMagnetBoth(magnet: String) {
        viewModelScope.launch {
            val res = torBoxTorrentsRepository.addMagnet(magnet)
            torrentLiveData.postEvent(TorrentEvent.TorBoxBothResult(res is EitherResult.Success))
            fetchAddedMagnet(magnet)
        }
    }

    /** Upload torrent bytes to TorBox only. */
    fun addTorrentBytesTorBox(bytes: ByteArray) {
        viewModelScope.launch {
            when (val res = torBoxTorrentsRepository.addTorrent(bytes)) {
                is EitherResult.Failure -> networkExceptionLiveData.postEvent(res.failure)
                is EitherResult.Success -> torrentLiveData.postEvent(TorrentEvent.TorBoxAdded)
            }
        }
    }

    /** Upload torrent bytes to both services: TorBox best-effort, then Real-Debrid. */
    fun addTorrentBytesBoth(bytes: ByteArray) {
        viewModelScope.launch {
            val res = torBoxTorrentsRepository.addTorrent(bytes)
            torrentLiveData.postEvent(TorrentEvent.TorBoxBothResult(res is EitherResult.Success))
            fetchUploadedTorrent(bytes)
        }
    }

    fun fetchUploadedTorrent(binaryTorrent: ByteArray) {
        viewModelScope.launch {
            val availableHosts = torrentsRepository.getAvailableHosts()
            if (availableHosts.isNullOrEmpty()) {
                Timber.e("Error fetching available hosts")
                torrentLiveData.postEvent(TorrentEvent.DownloadedFileFailure)
            } else {
                val uploadedTorrent =
                    torrentsRepository.addTorrent(binaryTorrent, availableHosts.first().host)
                when (uploadedTorrent) {
                    is EitherResult.Failure -> {
                        networkExceptionLiveData.postEvent(uploadedTorrent.failure)
                        torrentLiveData.postEvent(TorrentEvent.DownloadedFileFailure)
                    }
                    is EitherResult.Success -> {
                        fetchTorrentDetails(uploadedTorrent.success.id)
                    }
                }
            }
        }
    }

    companion object {
        const val KEY_CURRENT_TORRENT = "current_torrent_key"
        const val KEY_CURRENT_TORRENT_ID = "current_torrent_id_key"

        // Real-Debrid status reported while a magnet's metadata is still being resolved.
        private const val MAGNET_CONVERSION_STATUS = "magnet_conversion"
        // Poll the torrent info while it is converting, so the file picker isn't shown empty.
        private const val CONVERSION_POLL_DELAY_MS = 1500L
        private const val MAX_CONVERSION_POLLS = 20
    }
}

sealed class TorrentEvent {
    data class Uploaded(val torrent: UploadedTorrent) : TorrentEvent()

    data class TorrentInfo(val item: TorrentItem) : TorrentEvent()

    data class FilesSelected(val torrent: TorrentItem) : TorrentEvent()

    data object SelectionUpdated : TorrentEvent()

    data object DownloadAll : TorrentEvent()

    data class DownloadSelection(val filesNumber: Int) : TorrentEvent()

    data object DownloadedFileSuccess : TorrentEvent()

    data object DownloadedFileFailure : TorrentEvent()

    data class DownloadedFileProgress(val progress: Int) : TorrentEvent()

    /** A TorBox-only add succeeded; the torrent starts automatically (no file selection). */
    data object TorBoxAdded : TorrentEvent()

    /** Result of the best-effort TorBox add in the "Both" flow (true = added). */
    data class TorBoxBothResult(val success: Boolean) : TorrentEvent()
}
