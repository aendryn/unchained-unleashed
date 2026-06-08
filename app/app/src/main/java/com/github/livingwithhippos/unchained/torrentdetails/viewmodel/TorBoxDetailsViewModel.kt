package com.github.livingwithhippos.unchained.torrentdetails.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.livingwithhippos.unchained.data.model.torbox.TorBoxFile
import com.github.livingwithhippos.unchained.data.model.torbox.TorBoxTorrent
import com.github.livingwithhippos.unchained.data.repository.TorBoxDownloadsRepository
import com.github.livingwithhippos.unchained.data.repository.TorBoxTorrentsRepository
import com.github.livingwithhippos.unchained.utilities.EitherResult
import com.github.livingwithhippos.unchained.utilities.Event
import com.github.livingwithhippos.unchained.utilities.postEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Drives the TorBox torrent details screen: file list, per-file link resolution and torrent control
 * (delete / pause / resume).
 */
@HiltViewModel
class TorBoxDetailsViewModel
@Inject
constructor(
    private val torBoxTorrentsRepository: TorBoxTorrentsRepository,
    private val torBoxDownloadsRepository: TorBoxDownloadsRepository,
) : ViewModel() {

    val torrentLiveData = MutableLiveData<Event<TorBoxTorrent?>>()
    val actionLiveData = MutableLiveData<Event<TorBoxActionResult>>()

    fun loadTorrent(id: Long) {
        viewModelScope.launch {
            torrentLiveData.postEvent(torBoxTorrentsRepository.getRawTorrentInfo(id))
        }
    }

    /**
     * Resolve a single file's download link and persist it so it shows up in the Downloads tab,
     * matching the Real-Debrid behaviour where tapping a file sends it straight to Downloads.
     */
    fun resolveFileLink(torrentId: Long, file: TorBoxFile) {
        viewModelScope.launch {
            val name = file.shortName ?: file.name ?: "file"
            when (val result = torBoxTorrentsRepository.getDownloadLink(torrentId, file.id)) {
                is EitherResult.Success ->
                    saveDownload(
                        torrentId = torrentId,
                        fileId = file.id,
                        fileName = name,
                        size = file.size ?: 0L,
                        mimeType = file.mimetype,
                        url = result.success,
                    )
                is EitherResult.Failure -> actionLiveData.postEvent(TorBoxActionResult.Error)
            }
        }
    }

    fun resolveZipLink(torrentId: Long, torrentName: String) {
        viewModelScope.launch {
            when (
                val result =
                    torBoxTorrentsRepository.getDownloadLink(torrentId, null, zipLink = true)
            ) {
                is EitherResult.Success ->
                    saveDownload(
                        torrentId = torrentId,
                        fileId = null,
                        fileName = "$torrentName.zip",
                        size = 0L,
                        mimeType = "application/zip",
                        url = result.success,
                    )
                is EitherResult.Failure -> actionLiveData.postEvent(TorBoxActionResult.Error)
            }
        }
    }

    /** Persist a resolved link so it appears in the Downloads tab. */
    private suspend fun saveDownload(
        torrentId: Long,
        fileId: Long?,
        fileName: String,
        size: Long,
        mimeType: String?,
        url: String,
    ) {
        torBoxDownloadsRepository.save(
            torrentId = torrentId,
            fileId = fileId,
            fileName = fileName,
            size = size,
            mimeType = mimeType,
            downloadUrl = url,
        )
        actionLiveData.postEvent(TorBoxActionResult.DownloadSaved)
    }

    fun deleteTorrent(id: Long) {
        viewModelScope.launch {
            actionLiveData.postEvent(
                when (torBoxTorrentsRepository.deleteTorrent(id)) {
                    is EitherResult.Success -> TorBoxActionResult.Deleted
                    is EitherResult.Failure -> TorBoxActionResult.Error
                }
            )
        }
    }

    fun pauseTorrent(id: Long) {
        viewModelScope.launch {
            actionLiveData.postEvent(
                when (torBoxTorrentsRepository.pauseTorrent(id)) {
                    is EitherResult.Success -> TorBoxActionResult.Paused
                    is EitherResult.Failure -> TorBoxActionResult.Error
                }
            )
        }
    }

    fun resumeTorrent(id: Long) {
        viewModelScope.launch {
            actionLiveData.postEvent(
                when (torBoxTorrentsRepository.resumeTorrent(id)) {
                    is EitherResult.Success -> TorBoxActionResult.Resumed
                    is EitherResult.Failure -> TorBoxActionResult.Error
                }
            )
        }
    }
}

sealed class TorBoxActionResult {
    data object Deleted : TorBoxActionResult()

    data object Paused : TorBoxActionResult()

    data object Resumed : TorBoxActionResult()

    data object DownloadSaved : TorBoxActionResult()

    data object Error : TorBoxActionResult()
}
