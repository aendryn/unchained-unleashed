package com.github.livingwithhippos.unchained.torrentdetails.viewmodel

import android.content.SharedPreferences
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
    private val preferences: SharedPreferences,
) : ViewModel() {

    fun getDefaultPlayer(): String? = preferences.getString("default_media_player", "vlc")

    fun getCustomPlayerPreference(): String = preferences.getString("custom_media_player", "") ?: ""

    val torrentLiveData = MutableLiveData<Event<TorBoxTorrent?>>()
    val linkLiveData = MutableLiveData<Event<TorBoxLinkResult>>()
    val actionLiveData = MutableLiveData<Event<TorBoxActionResult>>()

    fun loadTorrent(id: Long) {
        viewModelScope.launch {
            torrentLiveData.postEvent(torBoxTorrentsRepository.getRawTorrentInfo(id))
        }
    }

    fun resolveFileLink(torrentId: Long, file: TorBoxFile) {
        viewModelScope.launch {
            val name = file.shortName ?: file.name ?: "file"
            when (val result = torBoxTorrentsRepository.getDownloadLink(torrentId, file.id)) {
                is EitherResult.Success ->
                    linkLiveData.postEvent(
                        TorBoxLinkResult.Resolved(
                            name = name,
                            url = result.success,
                            torrentId = torrentId,
                            fileId = file.id,
                            size = file.size ?: 0L,
                            mimeType = file.mimetype,
                        )
                    )
                is EitherResult.Failure -> linkLiveData.postEvent(TorBoxLinkResult.Error)
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
                    linkLiveData.postEvent(
                        TorBoxLinkResult.Resolved(
                            name = "$torrentName.zip",
                            url = result.success,
                            torrentId = torrentId,
                            fileId = null,
                            size = 0L,
                            mimeType = "application/zip",
                        )
                    )
                is EitherResult.Failure -> linkLiveData.postEvent(TorBoxLinkResult.Error)
            }
        }
    }

    /** Persist a resolved link so it appears in the Downloads tab. */
    fun saveDownload(resolved: TorBoxLinkResult.Resolved) {
        viewModelScope.launch {
            torBoxDownloadsRepository.save(
                torrentId = resolved.torrentId,
                fileId = resolved.fileId,
                fileName = resolved.name,
                size = resolved.size,
                mimeType = resolved.mimeType,
                downloadUrl = resolved.url,
            )
            actionLiveData.postEvent(TorBoxActionResult.DownloadSaved)
        }
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

sealed class TorBoxLinkResult {
    data class Resolved(
        val name: String,
        val url: String,
        val torrentId: Long,
        val fileId: Long?,
        val size: Long,
        val mimeType: String?,
    ) : TorBoxLinkResult()

    data object Error : TorBoxLinkResult()
}

sealed class TorBoxActionResult {
    data object Deleted : TorBoxActionResult()

    data object Paused : TorBoxActionResult()

    data object Resumed : TorBoxActionResult()

    data object DownloadSaved : TorBoxActionResult()

    data object Error : TorBoxActionResult()
}
