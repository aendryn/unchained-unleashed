package com.github.livingwithhippos.unchained.data.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.edit
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.github.livingwithhippos.unchained.R
import com.github.livingwithhippos.unchained.base.MainActivity
import com.github.livingwithhippos.unchained.data.model.DebridService
import com.github.livingwithhippos.unchained.data.model.UnifiedTorrent
import com.github.livingwithhippos.unchained.data.model.UnifiedTorrentStatus
import com.github.livingwithhippos.unchained.data.model.toUnified
import com.github.livingwithhippos.unchained.data.repository.DebridManager
import com.github.livingwithhippos.unchained.data.repository.TorBoxTorrentsRepository
import com.github.livingwithhippos.unchained.data.repository.TorrentsRepository
import com.github.livingwithhippos.unchained.di.TorrentNotification
import com.github.livingwithhippos.unchained.di.TorrentSummaryNotification
import com.github.livingwithhippos.unchained.settings.view.SettingsFragment
import com.github.livingwithhippos.unchained.utilities.PreferenceKeys
import com.github.livingwithhippos.unchained.utilities.extension.getStatusTranslation
import com.github.livingwithhippos.unchained.utilities.extension.vibrate
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

const val MAX_SERVICE_DURATION = 5 * 60 * 60 * 1000
const val MIN_SERVICE_DURATION = 20 * 60 * 1000

@AndroidEntryPoint
@SuppressLint("MissingPermission")
class ForegroundTorrentService : LifecycleService() {

    @Inject lateinit var torrentRepository: TorrentsRepository

    @Inject lateinit var torBoxTorrentsRepository: TorBoxTorrentsRepository

    @Inject lateinit var debridManager: DebridManager

    private val torrentBinder = TorrentBinder()

    private val torrentsLiveData = MutableLiveData<List<UnifiedTorrent>>()

    @Inject @TorrentSummaryNotification lateinit var summaryBuilder: NotificationCompat.Builder

    @Inject @TorrentNotification lateinit var torrentBuilder: NotificationCompat.Builder

    @Inject lateinit var notificationManager: NotificationManagerCompat

    @Inject lateinit var preferences: SharedPreferences

    private var updateTiming = UPDATE_TIMING_SHORT

    private var serviceStart = System.currentTimeMillis()

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return torrentBinder
    }

    /**
     * Binder for the client. It can be used to retrieve this service and call its public methods.
     */
    inner class TorrentBinder : Binder() {
        internal val service: ForegroundTorrentService
            get() = this@ForegroundTorrentService
    }

    override fun onCreate() {
        super.onCreate()
        // here or in onStartCommand()
        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startForegroundService() {
        torrentsLiveData.observe(this) { list ->
            // todo: manage removed torrents (right now they just stop updating)
            // the torrents we were observing (keyed by unifiedId so both services coexist)
            val oldTorrentsIDs: Set<String> =
                preferences.getStringSet(KEY_OBSERVED_TORRENTS, emptySet()) as Set<String>
            // their updated status
            val newLoadingTorrents = list.filter { torrent -> torrent.isLoading() }
            // the torrent whose status is not a loading one anymore.
            val finishedTorrents =
                list
                    // They are in our old list
                    .filter { oldTorrentsIDs.contains(it.unifiedId) }
                    // They aren't in our new loading list
                    .filter {
                        !newLoadingTorrents.map { newT -> newT.unifiedId }.contains(it.unifiedId)
                    }
            /*
            // the new torrents to add to the notification system
            val unwatchedTorrents = newLoadingTorrents.filter { !oldTorrentsIDs.contains(it.unifiedId) }
            // the torrents not in our updated list anymore. These needs to be retrieved and analyzed singularly.
            // Shouldn't happen often since there is a limit on how many active torrents you can have in real-debrid,
            // and we retrieve the last 30 torrents every time
            val missingTorrents = oldTorrentsIDs.filter { id ->
                !list.map { it.unifiedId }.contains(id)
            }
             */

            val shouldVibrate =
                preferences.getBoolean(PreferenceKeys.DownloadManager.VIBRATE_ON_FINISH, false)

            // update the torrents id to observe
            val newIDs = mutableSetOf<String>()
            newIDs.addAll(newLoadingTorrents.map { it.unifiedId })
            preferences.edit { putStringSet(KEY_OBSERVED_TORRENTS, newIDs) }
            updateTiming = if (newIDs.isEmpty()) UPDATE_TIMING_LONG else UPDATE_TIMING_SHORT

            // let's first operate as if all the needed torrents were always in the list

            // update the notifications for torrents in one of the loading statuses
            updateNotification(newLoadingTorrents)
            // update the notifications for torrents in one of the finished statuses
            finishedTorrents.forEach { torrent -> completeNotification(torrent) }
            if (shouldVibrate && finishedTorrents.isNotEmpty()) applicationContext.vibrate()
        }

        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                startForeground(SUMMARY_ID, summaryBuilder.build())
            } else {
                startForeground(
                    SUMMARY_ID,
                    summaryBuilder.build(),
                    FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            }
        } catch (ex: Exception) {
            Timber.e("Error starting foreground service: ${ex.message}")
        }

        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == SettingsFragment.KEY_TORRENT_NOTIFICATIONS) {
                val enableTorrentNotifications = sharedPreferences?.getBoolean(key, false) ?: false
                if (!enableTorrentNotifications) stopTorrentService()
            }
        }

    private fun startMonitoring() {
        lifecycleScope.launch {
            while (true) {
                // right now on api >= 35 after 6 hours the service will crash
                // because of system imposed limits
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM &&
                        System.currentTimeMillis() - serviceStart > MAX_SERVICE_DURATION
                ) {
                    Timber.w("Service has been running for too long, stopping it.")
                    break
                }
                try {
                    val torrentList = getTorrentList()
                    torrentsLiveData.postValue(torrentList)

                    if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM &&
                            System.currentTimeMillis() - serviceStart > MIN_SERVICE_DURATION
                    ) {
                        // if there are no active torrents and the services has been started
                        // for at least some minutes, stop the service
                        val unfinishedTorrents = torrentList.count { it.isLoading() }
                        if (unfinishedTorrents == 0) {
                            Timber.i(
                                "Service has been running and no torrents are active, stopping it."
                            )
                            break
                        }
                    }
                } catch (ex: IllegalArgumentException) {
                    // no valid token ready, retry later
                }
                // update notifications every 5 seconds
                delay(updateTiming)
            }
            stopTorrentService()
        }
    }

    /**
     * Fetches the in-progress torrents from every authenticated service and merges them into a
     * single backend-agnostic list. Each service is guarded by its own auth check so a user signed
     * in to only one of them never triggers a call to the other.
     */
    private suspend fun getTorrentList(max: Int = 30): List<UnifiedTorrent> {
        val merged = mutableListOf<UnifiedTorrent>()
        if (debridManager.isRealDebridAuthenticated()) {
            try {
                merged += torrentRepository.getTorrentsList(limit = max).map { it.toUnified() }
            } catch (ex: IllegalArgumentException) {
                // no valid RD token ready yet, skip this cycle
            }
        }
        if (debridManager.isTorBoxAuthenticated()) {
            // the TorBox repository already returns UnifiedTorrent and swallows its own errors
            merged += torBoxTorrentsRepository.getTorrentsList(limit = max)
        }
        return merged
    }

    private fun updateNotification(items: List<UnifiedTorrent>) {

        val notifications: MutableMap<String, Notification> = mutableMapOf()

        items.forEach { torrent ->
            torrentBuilder.setStyle(NotificationCompat.BigTextStyle().bigText(torrent.name))

            if (torrent.status == UnifiedTorrentStatus.DOWNLOADING) {
                val speedMBs = (torrent.speed ?: 0L).toFloat().div(1000000)
                torrentBuilder
                    .setProgress(100, torrent.progress.toInt(), false)
                    .setContentTitle(
                        getString(R.string.torrent_in_progress_format, torrent.progress, speedMBs)
                    )
                    .setOngoing(true)
            } else {
                torrentBuilder
                    .setContentTitle(applicationContext.getStatusTranslation(torrent.rawStatus))
                    // note: this could be indeterminate = true since it's technically in a loading
                    // status
                    // which should change
                    .setProgress(0, 0, false)
                    .setOngoing(false)
            }

            torrentBuilder.setContentIntent(notificationIntent(torrent))

            notifications[torrent.unifiedId] = torrentBuilder.build()
        }
        // will open the app on the torrent details page
        summaryBuilder.setContentText(getString(R.string.downloading_torrent_format, items.size))

        notificationManager.apply {
            // todo: manage permission
            notifications.forEach { (id, notification) -> notify(id.hashCode(), notification) }
            notify(SUMMARY_ID, summaryBuilder.build())
        }
    }

    private fun completeNotification(item: UnifiedTorrent) {

        notificationManager.apply {
            torrentBuilder
                .setContentTitle(applicationContext.getStatusTranslation(item.rawStatus))
                // if the file is already downloaded the second row will not be set elsewhere
                .setStyle(NotificationCompat.BigTextStyle().bigText(item.name))
                // remove the progressbar if present
                .setProgress(0, 0, false)
                // set click intent
                .setContentIntent(notificationIntent(item))
                // remove notification on tap
                .setAutoCancel(true)
                .setOngoing(false)
            notify(item.unifiedId.hashCode(), torrentBuilder.build())
        }
    }

    /**
     * Builds the tap intent for a torrent notification. RealDebrid items deep-link straight to
     * their details screen via [KEY_TORRENT_ID]; TorBox doesn't have that deep link wired in
     * [MainActivity] yet, so its notifications just reopen the app on the list.
     */
    private fun notificationIntent(torrent: UnifiedTorrent): PendingIntent? {
        val resultIntent =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                if (torrent.service == DebridService.REAL_DEBRID) {
                    putExtra(KEY_TORRENT_ID, torrent.rawId)
                }
            }

        return TaskStackBuilder.create(this).run {
            // Add the intent, which inflates the back stack
            addNextIntentWithParentStack(resultIntent)
            // Get the PendingIntent containing the entire back stack
            getPendingIntent(
                torrent.unifiedId.hashCode(),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }

    private fun stopTorrentService() {
        lifecycleScope.launch {
            // delay used to let the notification finish
            delay(1000)
            notificationManager.cancel(SUMMARY_ID)
            // this will avoid removing the notifications, so the user can see what happened in the
            // meanwhile
            stopForeground(STOP_FOREGROUND_DETACH)
        }
    }

    companion object {
        const val GROUP_KEY_TORRENTS: String = "group_key_torrent"
        const val KEY_OBSERVED_TORRENTS: String = "observed_torrents_key"
        const val UPDATE_TIMING_SHORT: Long = 5000
        const val UPDATE_TIMING_LONG: Long = 30000
        const val SUMMARY_ID: Int = 21
        const val KEY_TORRENT_ID = "torrent_id_key"

        /** Statuses the torrent is still expected to advance from, across both services. */
        private val LOADING_STATUSES =
            setOf(
                UnifiedTorrentStatus.QUEUED,
                UnifiedTorrentStatus.DOWNLOADING_METADATA,
                UnifiedTorrentStatus.DOWNLOADING,
                UnifiedTorrentStatus.UPLOADING,
                UnifiedTorrentStatus.STALLED,
                UnifiedTorrentStatus.PROCESSING,
            )

        private fun UnifiedTorrent.isLoading(): Boolean = LOADING_STATUSES.contains(status)
    }
}
