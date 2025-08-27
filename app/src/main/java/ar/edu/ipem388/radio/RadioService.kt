package ar.edu.ipem388.radio

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.session.DefaultMediaNotificationProvider

@androidx.media3.common.util.UnstableApi
class RadioService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        // Crear y configurar el reproductor
        player = ExoPlayer.Builder(this).build().apply {
            setMediaItem(MediaItem.fromUri("https://ipem388.radio12345.com/"))
            prepare()
            playWhenReady = true
        }

        // Crear la sesión de medios
        mediaSession = MediaSession.Builder(this, player!!)
            .setId("radio_session")
            .build()

        setMediaSession(mediaSession!!)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.release()
        player?.release()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }
}
