package ar.edu.ipem388.radio

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
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
import android.media.AudioAttributes as AndroidAudioAttributes
import com.google.android.exoplayer2.audio.AudioAttributes as ExoAudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import kotlinx.coroutines.*
import android.graphics.Bitmap

class RadioService : Service() {

    companion object {
        const val ACTION_START = "ar.edu.ipem388.radio.action.START"
        const val ACTION_PLAY  = "ar.edu.ipem388.radio.action.PLAY"
        const val ACTION_PAUSE = "ar.edu.ipem388.radio.action.PAUSE"
        const val ACTION_STOP  = "ar.edu.ipem388.radio.action.STOP"

        // Sincronización de estado con la UI
        const val ACTION_QUERY  = "ar.edu.ipem388.radio.action.QUERY_STATE"
        const val ACTION_STATE  = "ar.edu.ipem388.radio.action.STATE"
        const val EXTRA_PLAYING = "extra_playing"

        const val CHANNEL_ID = "radio_playback"
        const val NOTIF_ID   = 1

        private const val PREFS = "radio_prefs"
        private const val KEY_LAST_URL = "last_stream_url"

        // Fallback duro por si falla todo
        private const val HARD_FALLBACK =
            "https://uk26freenew.listen2myradio.com/live.mp3?typeportmount=s1_8393_stream_479557847"
    }

    // Ducking
    private val NORMAL_VOLUME = 1.0f
    private val DUCK_VOLUME   = 0.20f
    private var preDuckVolume = NORMAL_VOLUME
    private var isDucked      = false

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notifManager: PlayerNotificationManager

    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + serviceJob)

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> duck(true)
            AudioManager.AUDIOFOCUS_GAIN -> duck(false)
            AudioManager.AUDIOFOCUS_LOSS -> { pauseInternal(true); abandonFocus() }
        }
    }

    override fun onCreate() {
        super.onCreate()

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        mediaSession = MediaSessionCompat(this, "RadioService").apply { isActive = true }

        val exoAttrs = ExoAudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(exoAttrs, /* handleAudioFocus= */ false)
            setHandleAudioBecomingNoisy(true)
            volume = NORMAL_VOLUME
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    broadcastState()
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    broadcastState()
                }
                override fun onPlayerError(error: PlaybackException) {
                    // Si falla el stream, re-resolver y reintentar
                    resolveAndPlay(force = true)
                }
            })
        }

        // Notificación con controles
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
                override fun getCurrentContentText(player: Player): CharSequence? = "Radio en vivo"
                override fun getCurrentLargeIcon(
                    player: Player,
                    callback: PlayerNotificationManager.BitmapCallback
                ): Bitmap? = null
            })
            .setNotificationListener(object : PlayerNotificationManager.NotificationListener {
                override fun onNotificationPosted(id: Int, n: Notification, ongoing: Boolean) {
                    if (ongoing) startForeground(id, n) else stopForeground(false)
                }
                override fun onNotificationCancelled(id: Int, byUser: Boolean) { stopSelf() }
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

        // Resolver stream al iniciar
        resolveAndPlay(force = false)
        broadcastState() // estado inicial
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, ACTION_PLAY -> startPlaying()
            ACTION_PAUSE -> { pauseInternal(false); abandonFocus() }
            ACTION_STOP  -> stopPlaying()
            ACTION_QUERY -> broadcastState()
        }
        return START_NOT_STICKY
    }

    private fun resolveAndPlay(force: Boolean) {
        serviceScope.launch {
            val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val cached = prefs.getString(KEY_LAST_URL, null)

            val resolved = withContext(Dispatchers.IO) {
                if (!force && cached != null) return@withContext cached
                StreamResolver.resolveFromPortal() ?: cached ?: HARD_FALLBACK
            }

            if (resolved != cached) {
                prefs.edit().putString(KEY_LAST_URL, resolved).apply()
            }

            val wasPlaying = player.isPlaying
            player.setMediaItem(MediaItem.fromUri(resolved))
            player.prepare()
            if (wasPlaying) player.play()
        }
    }

    private fun startPlaying() {
        if (requestFocus()) {
            if (player.currentMediaItem == null) {
                resolveAndPlay(force = false)
            }
            player.playWhenReady = true
            player.play()
            broadcastState() // feedback inmediato
        }
    }

    private fun pauseInternal(@Suppress("UNUSED_PARAMETER") focusLoss: Boolean) {
        player.pause()
        broadcastState()
    }

    private fun stopPlaying() {
        notifManager.setPlayer(null)
        player.pause()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
        abandonFocus()
        stopSelf()
        broadcastState()
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
                .setWillPauseWhenDucked(false)
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
            @Suppress("DEPRECATION") audioManager.abandonAudioFocus(focusChangeListener)
        }
    }

    private fun duck(enable: Boolean) {
        if (!::player.isInitialized) return
        if (enable) {
            if (!isDucked) {
                preDuckVolume = player.volume
                player.volume = DUCK_VOLUME
                isDucked = true
            }
        } else {
            if (isDucked) {
                player.volume = preDuckVolume.coerceIn(0f, 1f)
                isDucked = false
            }
        }
    }

    // Broadcast de estado real
    private fun broadcastState() {
        val i = Intent(ACTION_STATE).apply {
            putExtra(EXTRA_PLAYING, ::player.isInitialized && player.isPlaying)
        }
        sendBroadcast(i)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopPlaying()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        notifManager.setPlayer(null)
        if (::player.isInitialized) player.release()
        mediaSession.release()
        abandonFocus()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
