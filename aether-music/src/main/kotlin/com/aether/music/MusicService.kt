package com.aether.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Service de lecture musicale en avant-plan (Foreground Service).
 *
 * Garantit que la musique continue quand :
 *   - L'écran est éteint
 *   - L'utilisateur bascule vers une autre app
 *   - Android veut récupérer de la mémoire
 *
 * Utilisation :
 *   1. Lier (bindService) depuis MainActivity
 *   2. Appeler MusicService.play(uri), .pause(), .resume(), .stop()
 *   3. La notification du lecteur reste affichée en permanence
 */
class MusicService : Service() {

    // ── Binder (liaison Activity ↔ Service) ──────────────────────────────
    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }
    private val binder = MusicBinder()
    override fun onBind(intent: Intent): IBinder = binder

    // ── MediaPlayer ───────────────────────────────────────────────────────
    private var mediaPlayer: MediaPlayer? = null
    private var currentUri: Uri? = null
    private var currentTitle: String = "AetherMusic"
    private var currentArtist: String = ""
    var onCompletion: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    companion object {
        const val CHANNEL_ID   = "aether_music_playback"
        const val NOTIF_ID     = 42

        const val ACTION_PLAY  = "com.aether.music.PLAY"
        const val ACTION_PAUSE = "com.aether.music.PAUSE"
        const val ACTION_NEXT  = "com.aether.music.NEXT"
        const val ACTION_STOP  = "com.aether.music.STOP"
        const val EXTRA_URI    = "track_uri"
        const val EXTRA_TITLE  = "track_title"
        const val EXTRA_ARTIST = "track_artist"
    }

    // ── Cycle de vie ──────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY  -> {
                val uri    = intent.getStringExtra(EXTRA_URI)?.let { Uri.parse(it) }
                val title  = intent.getStringExtra(EXTRA_TITLE) ?: "Titre inconnu"
                val artist = intent.getStringExtra(EXTRA_ARTIST) ?: ""
                if (uri != null) play(uri, title, artist)
            }
            ACTION_PAUSE -> if (isPlaying()) pause() else resume()
            ACTION_NEXT  -> onCompletion?.invoke()
            ACTION_STOP  -> { stopPlayback(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }

    // ── API publique ──────────────────────────────────────────────────────

    fun play(uri: Uri, title: String = "Titre", artist: String = "") {
        stopPlayback()
        currentUri    = uri
        currentTitle  = title
        currentArtist = artist

        mediaPlayer = runCatching {
            MediaPlayer.create(this, uri)
        }.getOrNull()

        mediaPlayer?.apply {
            setOnCompletionListener { onCompletion?.invoke() }
            setOnErrorListener { _, _, extra ->
                onError?.invoke("Erreur lecture (code $extra)")
                false
            }
            start()
        } ?: run {
            onError?.invoke("Impossible de lire ce fichier")
            return
        }

        startForeground(NOTIF_ID, buildNotification(title, artist, true))
    }

    fun pause() {
        mediaPlayer?.pause()
        updateNotification(currentTitle, currentArtist, false)
    }

    fun resume() {
        mediaPlayer?.start()
        updateNotification(currentTitle, currentArtist, true)
    }

    fun stopPlayback() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun seekTo(positionMs: Int) { mediaPlayer?.seekTo(positionMs) }
    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true
    fun position(): Int = mediaPlayer?.currentPosition ?: 0
    fun duration(): Int = mediaPlayer?.duration ?: 0

    // ── Notification ──────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Lecture musicale",
                NotificationManager.IMPORTANCE_LOW,  // LOW = silencieux, pas de son
            ).apply {
                description = "Contrôles de lecture AetherMusic"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(title: String, artist: String, playing: Boolean): Notification {
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this, 0, activityIntent, PendingIntent.FLAG_IMMUTABLE
        )

        // Actions : pause/play + suivant
        val toggleAction = NotificationCompat.Action(
            if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (playing) "Pause" else "Lire",
            buildServicePendingIntent(if (playing) ACTION_PAUSE else ACTION_PLAY),
        )
        val nextAction = NotificationCompat.Action(
            android.R.drawable.ic_media_next,
            "Suivant",
            buildServicePendingIntent(ACTION_NEXT),
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist.ifBlank { "AetherMusic" })
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentIntent)
            .addAction(toggleAction)
            .addAction(nextAction)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1)
            )
            .setOngoing(playing)               // Sticky pendant la lecture
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(title: String, artist: String, playing: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(title, artist, playing))
    }

    private fun buildServicePendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicService::class.java).apply { this.action = action }
        return PendingIntent.getService(this, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE)
    }

    // ── Helper statique ───────────────────────────────────────────────────

    companion object {
        /** Lance la lecture via le Service depuis n'importe quel contexte */
        fun play(context: Context, uri: Uri, title: String, artist: String) {
            val intent = Intent(context, MusicService::class.java).apply {
                action = ACTION_PLAY
                putExtra(EXTRA_URI,    uri.toString())
                putExtra(EXTRA_TITLE,  title)
                putExtra(EXTRA_ARTIST, artist)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
