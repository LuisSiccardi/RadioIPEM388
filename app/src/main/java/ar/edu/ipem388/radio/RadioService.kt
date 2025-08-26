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

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioAttributes as AndroidAudioAttributes
import com.google.android.exoplayer2.audio.AudioAttributes as ExoAudioAttributes

class RadioService : Service() {

    companion object {
        const val ACTION_START = "ar.edu.ipem388.radio.action.START"
        const val ACTION_PLAY  = "ar.edu.ipem388.radio.action.PLAY"
        const val ACTION_PAUSE = "ar.edu.ipem388.radio.action.PAUSE"
        const val ACTION_STOP  = "ar.edu.ipem388.radio.action.STOP"

        const val CHANNEL_ID = "radio_playback"
        const val NOTIF_ID   = 1

        // ⬇️ URL del stream
        const val STREAM_URL =
            "https://uk26freenew.listen2myradio.com/live.mp3?typeportmount=s1_8393_stream_479557847"
    }

    // --- Player / media ---
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notifManager: PlayerNotificationManager

    // --- AudioFocus manual (para no reanudar automáticamente) ---
    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Audio temporal (WhatsApp, etc.) → Pausamos y NO reanudamos solos
                pauseInternal(focusLoss = true)
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Pérdida total → Pausamos y soltamos el focus
                pauseInternal(focusLoss = true)
                abandonFocus()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // No reanudamos automáticamente: queda a decisión del usuario
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        mediaSession = MediaSessionCompat(this, "RadioService").apply { isActive = true }

        // ExoPlayer: SIN manejo automático de audio focus (false)
        val exoAttrs = ExoAudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(exoAttrs, /* handleAudioFocus= */ false)
            setHandleAudioBecomingNoisy(true) // pausa al desenchufar auriculares
            setMediaItem(MediaItem.fromUri(STREAM_URL))
            prepare()
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    // Reintento simple si el stream se corta
                    playWhenReady = true
                    prepare()
                }
            })
        }

        // Notificación
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
            ACTION_START, ACTION_PLAY -> startPlaying()
            ACTION_PAUSE -> {
                pauseInternal(focusLoss = false)
                abandonFocus()
            }
            ACTION_STOP -> {
                stopPlaying()
            }
        }
        // No queremos que Android lo relance solo si lo mata.
        return START_NOT_STICKY
    }

    // --- Helpers de reproducción / focus ---

    private fun startPlaying() {
        if (requestFocus()) {
            player.playWhenReady = true
            player.play()
        }
    }

    private fun pauseInternal(focusLoss: Boolean) {
        player.pause()
        // Si quisieras reanudar automáticamente tras LOSS_TRANSIENT,
        // podrías guardar un flag aquí y usarlo en AUDIOFOCUS_GAIN.
    }

    private fun stopPlaying() {
        notifManager.setPlayer(null)
        player.pause()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        abandonFocus()
        stopSelf()
    }

    private fun requestFocus(): Boolean {
        val res = if (Build.VERSION.SDK_INT >= 26) {
            val attrs = AndroidAudioAttributes.Builder()
                .setUsage(AndroidAudioAttributes.USAGE_MEDIA)
                .setContentType(AndroidAudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .setWillPauseWhenDucked(true)
                .build()
            focusRequest = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        return res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= 26) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListener)
        }
    }

    // Si el usuario cierra la app "deslizando", apagamos todo.
    override fun onTaskRemoved(rootIntent: Intent?) {
        stopPlaying()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        notifManager.setPlayer(null)
        player.release()
        mediaSession.release()
        abandonFocus()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
