package ar.edu.ipem388.radio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.audio.AudioAttributes
import kotlinx.coroutines.*

class RadioService : Service() {
    companion object {
        const val ACTION_PLAY = "ar.edu.ipem388.radio.action.PLAY"
        const val ACTION_PAUSE = "ar.edu.ipem388.radio.action.PAUSE"
        const val ACTION_STOP = "ar.edu.ipem388.radio.action.STOP"
        const val ACTION_SELECT = "ar.edu.ipem388.radio.action.SELECT"
        const val ACTION_QUERY = "ar.edu.ipem388.radio.action.QUERY_STATE"
        const val ACTION_STATE = "ar.edu.ipem388.radio.action.STATE"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_PHASE = "phase"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_ACTIVE = "active"
        private const val CHANNEL_ID = "radio_playback"
        private const val NOTIF_ID = 1
    }

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notifications: NotificationManager
    private var source = AudioSource.RECORDING
    private var phase = PlaybackPhase.STOPPED
    private var message = ""
    private var wantsPlayback = false
    private var changingMedia = false
    private var foreground = false
    private var generation = 0
    private var workJob: Job? = null
    private val retry = ReconnectPolicy()
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main.immediate + serviceJob)

    private val commands = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_QUERY -> broadcastState()
                ACTION_PAUSE -> pausePlaying()
                ACTION_STOP -> stopPlaying()
                ACTION_SELECT -> selectSource(AudioSource.fromKey(intent.getStringExtra(EXTRA_SOURCE)))
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        source = AudioSource.fromKey(getSharedPreferences(AudioSource.PREFS, MODE_PRIVATE)
            .getString(AudioSource.SELECTED, null))
        notifications = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            notifications.createNotificationChannel(NotificationChannel(CHANNEL_ID,
                "Reproducción de Radio Escolar 388", NotificationManager.IMPORTANCE_LOW))
        }
        mediaSession = MediaSessionCompat(this, "RadioIPEM388").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { startFromControl() }
                override fun onPause() { pausePlaying() }
                override fun onStop() { stopPlaying() }
                override fun onSeekTo(pos: Long) {
                    if (source == AudioSource.RECORDING) player.seekTo(pos.coerceAtLeast(0))
                }
            })
            isActive = true
        }
        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            setHandleAudioBecomingNoisy(true)
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    if (changingMedia) return
                    if (playing) {
                        wantsPlayback = true
                        retry.reset()
                        setStatus(PlaybackPhase.PLAYING)
                    }
                }
                override fun onPlaybackStateChanged(state: Int) {
                    if (changingMedia) return
                    when (state) {
                        Player.STATE_BUFFERING -> if (wantsPlayback) setStatus(PlaybackPhase.CONNECTING)
                        Player.STATE_ENDED -> {
                            wantsPlayback = false
                            leaveForeground()
                            setStatus(PlaybackPhase.FINISHED)
                        }
                        Player.STATE_READY -> if (!wantsPlayback) setStatus(PlaybackPhase.PAUSED)
                    }
                }
                override fun onPlayWhenReadyChanged(ready: Boolean, reason: Int) {
                    if (!changingMedia && !ready && wantsPlayback) pausePlaying()
                }
                override fun onPlayerError(error: PlaybackException) {
                    if (!changingMedia) handleFailure()
                }
            })
        }
        ContextCompat.registerReceiver(this, commands, IntentFilter().apply {
            addAction(ACTION_QUERY); addAction(ACTION_PAUSE); addAction(ACTION_STOP); addAction(ACTION_SELECT)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val requested = intent.getStringExtra(EXTRA_SOURCE)?.let(AudioSource::fromKey) ?: source
                if (requested != source) selectSource(requested)
                // Promote immediately, before DNS, the portal request or audio buffering.
                foreground = true
                startForeground(NOTIF_ID, notification())
                startPlaying()
            }
            ACTION_PAUSE -> pausePlaying()
            ACTION_STOP -> stopPlaying()
        }
        return START_NOT_STICKY
    }

    private fun startFromControl() {
        ContextCompat.startForegroundService(this, Intent(this, RadioService::class.java)
            .setAction(ACTION_PLAY).putExtra(EXTRA_SOURCE, source.key))
    }

    private fun selectSource(next: AudioSource) {
        wantsPlayback = false
        cancelWork()
        retry.reset()
        changingMedia = true
        player.pause(); player.stop(); player.clearMediaItems()
        changingMedia = false
        source = next
        getSharedPreferences(AudioSource.PREFS, MODE_PRIVATE).edit()
            .putString(AudioSource.SELECTED, next.key).apply()
        leaveForeground()
        setStatus(PlaybackPhase.STOPPED)
    }

    private fun startPlaying() {
        if (wantsPlayback) { broadcastState(); return }
        wantsPlayback = true
        retry.reset()
        if (player.currentMediaItem != null && player.playerError == null) {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            setStatus(PlaybackPhase.CONNECTING)
            player.play()
        } else loadSource()
    }

    private fun loadSource() {
        cancelWork()
        val token = generation
        val requested = source
        setStatus(PlaybackPhase.CONNECTING)
        workJob = scope.launch {
            val uri = if (requested == AudioSource.RECORDING) {
                "android.resource://$packageName/${R.raw.el_molle_de_beber}"
            } else withTimeoutOrNull(18_000) { StreamResolver.resolveFromPortal() }
            ensureActive()
            if (token != generation || !wantsPlayback || source != requested) return@launch
            if (uri == null) { handleFailure(); return@launch }
            changingMedia = true
            try {
                player.setMediaItem(MediaItem.fromUri(uri))
                player.prepare()
            } finally { changingMedia = false }
            player.play()
        }
    }

    private fun handleFailure() {
        if (!wantsPlayback) return
        cancelWork()
        val wait = if (source == AudioSource.LIVE) retry.nextDelayMs() else null
        if (wait == null) {
            wantsPlayback = false
            player.pause()
            leaveForeground()
            setStatus(PlaybackPhase.ERROR, if (source == AudioSource.LIVE)
                "La emisión en vivo no está disponible. Podés reintentar o elegir el programa grabado."
                else "No se pudo abrir el programa grabado. Volvé a intentarlo.")
            return
        }
        val token = generation
        setStatus(PlaybackPhase.CONNECTING, "La emisión no responde. Reintentamos en ${wait / 1000} segundos…")
        workJob = scope.launch {
            delay(wait)
            if (wantsPlayback && token == generation) loadSource()
        }
    }

    private fun pausePlaying() {
        wantsPlayback = false
        cancelWork()
        player.pause()
        leaveForeground()
        setStatus(PlaybackPhase.PAUSED)
    }

    private fun cancelWork() { generation++; workJob?.cancel(); workJob = null }

    private fun stopPlaying() {
        wantsPlayback = false
        cancelWork()
        changingMedia = true
        player.stop(); player.clearMediaItems()
        changingMedia = false
        phase = PlaybackPhase.STOPPED
        message = ""
        broadcastState()
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
        else { @Suppress("DEPRECATION") stopForeground(true) }
        foreground = false
        notifications.cancel(NOTIF_ID)
        stopSelf()
    }

    private fun leaveForeground() {
        if (!foreground) return
        @Suppress("DEPRECATION")
        stopForeground(false)
        foreground = false
    }

    private fun setStatus(next: PlaybackPhase, detail: String = "") {
        phase = next; message = detail
        updateSession()
        notifications.notify(NOTIF_ID, notification())
        broadcastState()
    }

    private fun updateSession() {
        val state = when (phase) {
            PlaybackPhase.PLAYING -> PlaybackStateCompat.STATE_PLAYING
            PlaybackPhase.CONNECTING -> PlaybackStateCompat.STATE_BUFFERING
            PlaybackPhase.PAUSED -> PlaybackStateCompat.STATE_PAUSED
            PlaybackPhase.ERROR -> PlaybackStateCompat.STATE_ERROR
            else -> PlaybackStateCompat.STATE_STOPPED
        }
        mediaSession.setPlaybackState(PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_STOP)
            .setState(state, player.currentPosition, if (player.isPlaying) 1f else 0f).build())
        mediaSession.setMetadata(MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, source.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST,
                if (source == AudioSource.RECORDING) "Radio Escolar 388 · Programa grabado" else "Radio Escolar 388 · En vivo")
            .build())
    }

    private fun actionIntent(action: String): PendingIntent {
        val intent = Intent(this, RadioService::class.java).setAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (action == ACTION_PLAY && Build.VERSION.SDK_INT >= 26)
            PendingIntent.getForegroundService(this, action.hashCode(), intent, flags)
        else PendingIntent.getService(this, action.hashCode(), intent, flags)
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val primary = if (wantsPlayback) ACTION_PAUSE else ACTION_PLAY
        return NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(R.drawable.ic_stat_radio)
            .setContentTitle(source.title)
            .setContentText(if (source == AudioSource.RECORDING) "Programa grabado · ${phase.label}" else "En vivo · ${phase.label}")
            .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(wantsPlayback)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(if (wantsPlayback) R.drawable.ic_pause_24 else R.drawable.ic_play_24,
                if (wantsPlayback) "Pausar" else "Reproducir", actionIntent(primary))
            .addAction(R.drawable.ic_power_24, "Detener", actionIntent(ACTION_STOP))
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.sessionToken).setShowActionsInCompactView(0, 1))
            .build()
    }

    private fun broadcastState() {
        sendBroadcast(Intent(ACTION_STATE).setPackage(packageName)
            .putExtra(EXTRA_SOURCE, source.key).putExtra(EXTRA_PHASE, phase.name)
            .putExtra(EXTRA_MESSAGE, message).putExtra(EXTRA_ACTIVE, wantsPlayback))
    }

    override fun onTaskRemoved(rootIntent: Intent?) { stopPlaying(); super.onTaskRemoved(rootIntent) }
    override fun onDestroy() {
        wantsPlayback = false
        cancelWork()
        unregisterReceiver(commands)
        changingMedia = true
        player.release()
        mediaSession.release()
        notifications.cancel(NOTIF_ID)
        serviceJob.cancel()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
