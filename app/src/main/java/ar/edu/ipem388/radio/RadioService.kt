package ar.edu.ipem388.radio

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.google.android.exoplayer2.audio.AudioAttributes

class RadioService : Service() {

    companion object {
        const val ACTION_START = "ar.edu.ipem388.radio.action.START"
        const val ACTION_PLAY  = "ar.edu.ipem388.radio.action.PLAY"
        const val ACTION_PAUSE = "ar.edu.ipem388.radio.action.PAUSE"
        const val ACTION_STOP  = "ar.edu.ipem388.radio.action.STOP"

        const val ACTION_STATE = "ar.edu.ipem388.radio.action.STATE"
        const val EXTRA_PLAYING = "extra_playing"

        const val CHANNEL_ID = "radio_playback"
        const val NOTIF_ID   = 1

        // URL del stream (https recomendado)
        const val STREAM_URL =
            "https://uk26freenew.listen2myradio.com/live.mp3?typeportmount=s1_8393_stream_479557847"
    }

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notifManager: PlayerNotificationManager

    private val prefs by lazy { getSharedPreferences("radio", MODE_PRIVATE) }

    private fun publishState(isPlaying: Boolean) {
        prefs.edit().putBoolean("playing", isPlaying).apply()
        sendBroadcast(Intent(ACTION_STATE).putExtra(EXTRA_PLAYING, isPlaying))
    }

    override fun onCreate() {
        super.onCreate()

        mediaSession = MediaSessionCompat(this, "RadioService").apply { isActive = true }

        val attrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(attrs, /* handleAudioFocus = */ true)
            setHandleAudioBecomingNoisy(true)
            setMediaItem(MediaItem.fromUri(STREAM_URL))
            prepare()
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    // Reintento básico si el stream se corta
                    playWhenReady = true
                    prepare()
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    publishState(isPlaying)
                }
            })
        }
        publishState(false) // estado inicial

        notifManager = PlayerNotificationManager.Builder(this, NOTIF_ID, CHANNEL_ID)
            .setMediaDescriptionAdapter(object : PlayerNotificationManager.MediaDescriptionAdapter {
                override fun getCurrentContentTitle(player: Player): CharSequence =
                    getString(R.string.app_name)

                override fun createCurrentContentIntent(player: Player): PendingIntent? {
                    val intent = Intent(this@RadioService, MainActivity::class.java)
                    return PendingIntent.getActivity(
                        this@RadioService, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                }

                override fun getCurrentContentText(player: Player): CharSequence? =
                    "Radio en vivo"

                override fun getCurrentLargeIcon(
                    player: Player,
                    callback: PlayerNotificationManager.BitmapCallback
                ) = null
            })
            .setNotificationListener(object : PlayerNotificationManager.NotificationListener {
                override fun onNotificationPosted(
                    notificationId: Int,
                    notification: Notification,
                    ongoing: Boolean
                ) {
                    if (ongoing) startForeground(notificationId, notification)
                    else stopForeground(false)
                }

                override fun onNotificationCancelled(
                    notificationId: Int,
                    dismissedByUser: Boolean
                ) {
                    publishState(false)
                    stopSelf()
                }
            })
            .setChannelNameResourceId(R.string.notif_channel_name)
            .setChannelDescriptionResourceId(R.string.notif_channel_desc)
            .build().apply {
                setSmallIcon(R.drawable.ic_stat_radio)
                setMediaSessionToken(mediaSession.sessionToken)
                setUseNextAction(false)
                setUsePreviousAction(false)
                setUseFastForwardAction(false)
                setUseRewindAction(false)
                setPlayer(player)
            }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, ACTION_PLAY -> {
                player.playWhenReady = true
                player.play()
                // onIsPlayingChanged(true) disparará publishState(true)
            }
            ACTION_PAUSE -> {
                player.pause()
                publishState(false)
            }
            ACTION_STOP -> {
                publishState(false)
                notifManager.setPlayer(null)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        publishState(false)
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }

    override fun onDestroy() {
        publishState(false)
        notifManager.setPlayer(null)
        player.release()
        mediaSession.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
