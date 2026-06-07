package com.github.livingwithhippos.unchained.utilities.extension

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import com.github.livingwithhippos.unchained.R

/**
 * Open [url] in the user's configured external media player, honoring the "default_media_player"
 * preference. Shared by the Real-Debrid download details and the TorBox torrent details screens so
 * both behave identically.
 */
fun Fragment.openInExternalPlayer(
    url: String,
    defaultPlayer: String?,
    customPlayerPackage: String = "",
) {
    fun mediaIntent(appPackage: String): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setPackage(appPackage)
            setDataAndTypeAndNormalize(url.toUri(), "video/*")
        }

    fun tryStart(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            context?.showToast(R.string.app_not_installed)
        }
    }

    when (defaultPlayer) {
        "vlc" -> tryStart(mediaIntent("org.videolan.vlc"))
        "mpv" -> tryStart(mediaIntent("is.xyz.mpv"))
        "mx_player" -> {
            val mxIntent = mediaIntent("com.mxtech.videoplayer.pro")
            try {
                startActivity(mxIntent)
            } catch (e: ActivityNotFoundException) {
                mxIntent.setPackage("com.mxtech.videoplayer.ad")
                tryStart(mxIntent)
            }
        }
        "web_video_cast" -> tryStart(mediaIntent("com.instantbits.cast.webvideo"))
        "play_it" -> tryStart(mediaIntent("com.playit.videoplayer"))
        "player_just_video" -> tryStart(mediaIntent("com.brouken.player"))
        "custom_player" ->
            if (customPlayerPackage.isBlank()) context?.showToast(R.string.invalid_package)
            else tryStart(mediaIntent(customPlayerPackage))
        else -> context?.showToast(R.string.missing_default_player)
    }
}
