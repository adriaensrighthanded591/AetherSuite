package com.aether.music

import android.Manifest
import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aether.core.AetherIntents
import com.aether.core.ui.components.*
import com.aether.core.ui.theme.AetherColors
import com.aether.core.ui.theme.AetherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

// ════════════════════════════════════════════════════════════════════════════
// MODEL
// ════════════════════════════════════════════════════════════════════════════

data class Track(
    val id:       Long,
    val uri:      Uri,
    val title:    String,
    val artist:   String,
    val album:    String,
    val duration: Long,   // ms
    val albumId:  Long,
) {
    val albumArtUri: Uri get() = ContentUris.withAppendedId(
        Uri.parse("content://media/external/audio/albumart"), albumId)
    val formattedDuration: String get() {
        val s = duration / 1000
        return "${s / 60}:${"%02d".format(s % 60)}"
    }
}

data class AetherAlbum(val id: Long, val name: String, val artist: String, val trackCount: Int, val artUri: Uri)
data class AetherArtist(val name: String, val trackCount: Int)

enum class MusicTab { TRACKS, ALBUMS, ARTISTS, PLAYLISTS }
enum class RepeatMode { NONE, ONE, ALL }

// ════════════════════════════════════════════════════════════════════════════
// REPOSITORY
// ════════════════════════════════════════════════════════════════════════════

class MusicRepository(private val context: Context) {

    private val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    suspend fun loadTracks(): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        val proj = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ALBUM_ID,
        )
        context.contentResolver.query(uri, proj,
            "${MediaStore.Audio.Media.IS_MUSIC}=1 AND ${MediaStore.Audio.Media.DURATION} > 30000",
            null, "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                tracks.add(Track(
                    id = id, uri = ContentUris.withAppendedId(uri, id),
                    title = c.getString(1) ?: "Titre inconnu",
                    artist = c.getString(2) ?: "Artiste inconnu",
                    album = c.getString(3) ?: "Album inconnu",
                    duration = c.getLong(4),
                    albumId = c.getLong(5),
                ))
            }
        }
        tracks
    }

    suspend fun loadAlbums(): List<AetherAlbum> = withContext(Dispatchers.IO) {
        val albums = mutableListOf<AetherAlbum>()
        val uri = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI
        val proj = arrayOf(MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM,
            MediaStore.Audio.Albums.ARTIST, MediaStore.Audio.Albums.NUMBER_OF_SONGS)
        context.contentResolver.query(uri, proj, null, null, "${MediaStore.Audio.Albums.ALBUM} ASC")?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                albums.add(AetherAlbum(id, c.getString(1) ?: "", c.getString(2) ?: "",
                    c.getInt(3), ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), id)))
            }
        }
        albums
    }
}

// ════════════════════════════════════════════════════════════════════════════
// PLAYER (MediaPlayer wrapper)
// ════════════════════════════════════════════════════════════════════════════

class AetherPlayer(private val context: Context) {
    private var mp: MediaPlayer? = null
    var onCompletion: (() -> Unit)? = null

    fun play(uri: Uri) {
        stop()
        mp = MediaPlayer.create(context, uri)?.apply {
            setOnCompletionListener { onCompletion?.invoke() }
            start()
        }
    }
    fun pause()   { mp?.pause() }
    fun resume()  { mp?.start() }
    fun stop()    { mp?.stop(); mp?.release(); mp = null }
    fun seekTo(ms: Int) { mp?.seekTo(ms) }
    fun isPlaying(): Boolean = mp?.isPlaying == true
    fun position(): Int = mp?.currentPosition ?: 0
    fun duration(): Int = mp?.duration ?: 0
}

// ════════════════════════════════════════════════════════════════════════════
// VIEWMODEL
// ════════════════════════════════════════════════════════════════════════════

class MusicViewModel(app: Application) : AndroidViewModel(app) {
    private val repo   = MusicRepository(app)
    val player         = AetherPlayer(app)

    private val _tracks  = MutableStateFlow<List<Track>>(emptyList())
    private val _albums  = MutableStateFlow<List<AetherAlbum>>(emptyList())
    private val _loading = MutableStateFlow(false)
    private val _current = MutableStateFlow<Track?>(null)
    private val _playing = MutableStateFlow(false)
    private val _tab     = MutableStateFlow(MusicTab.TRACKS)
    private val _search  = MutableStateFlow("")
    private val _repeat  = MutableStateFlow(RepeatMode.NONE)
    private val _shuffle = MutableStateFlow(false)
    private val _queue   = MutableStateFlow<List<Track>>(emptyList())

    val tracks:  StateFlow<List<Track>>        = combine(_tracks, _search) { list, q ->
        if (q.isBlank()) list else list.filter { it.title.contains(q, true) || it.artist.contains(q, true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val albums:  StateFlow<List<AetherAlbum>>  = _albums.asStateFlow()
    val loading: StateFlow<Boolean>            = _loading.asStateFlow()
    val current: StateFlow<Track?>             = _current.asStateFlow()
    val playing: StateFlow<Boolean>            = _playing.asStateFlow()
    val tab:     StateFlow<MusicTab>           = _tab.asStateFlow()
    val search:  StateFlow<String>             = _search.asStateFlow()
    val repeat:  StateFlow<RepeatMode>         = _repeat.asStateFlow()
    val shuffle: StateFlow<Boolean>            = _shuffle.asStateFlow()

    init {
        load()
        // La completion est gérée par MusicService via ACTION_NEXT
    }

    fun load() = viewModelScope.launch {
        _loading.value = true
        _tracks.value  = repo.loadTracks()
        _albums.value  = repo.loadAlbums()
        _queue.value   = _tracks.value
        _loading.value = false
    }

    fun play(track: Track) {
        _current.value = track
        _playing.value = true
        // Délégation au ForegroundService (musique continue en arrière-plan)
        MusicService.play(getApplication(), track.uri, track.title, track.artist)
    }

    fun togglePlay() {
        _playing.value = !_playing.value
        val action = if (_playing.value) MusicService.ACTION_PLAY else MusicService.ACTION_PAUSE
        val intent = android.content.Intent(getApplication(), MusicService::class.java).apply { this.action = action }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
            getApplication<Application>().startForegroundService(intent)
        else getApplication<Application>().startService(intent)
    }

    fun next() {
        val queue = if (_shuffle.value) _queue.value.shuffled() else _queue.value
        val idx = queue.indexOfFirst { it.id == _current.value?.id }
        val next = when (_repeat.value) {
            RepeatMode.ONE  -> _current.value
            RepeatMode.ALL  -> queue[(idx + 1) % queue.size]
            RepeatMode.NONE -> if (idx + 1 < queue.size) queue[idx + 1] else null
        }
        next?.let { play(it) }
    }

    fun previous() {
        val queue = _queue.value
        val idx = queue.indexOfFirst { it.id == _current.value?.id }
        if (idx > 0) play(queue[idx - 1])
    }

    fun setTab(t: MusicTab)    { _tab.value = t }
    fun setSearch(q: String)   { _search.value = q }
    fun toggleShuffle()        { _shuffle.value = !_shuffle.value }
    fun cycleRepeat()          { _repeat.value = RepeatMode.values()[(RepeatMode.values().indexOf(_repeat.value) + 1) % 3] }

    override fun onCleared()   { player.stop(); super.onCleared() }
}

// ════════════════════════════════════════════════════════════════════════════
// UI
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicApp(vm: MusicViewModel) {
    val tracks  by vm.tracks.collectAsStateWithLifecycle()
    val albums  by vm.albums.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val current by vm.current.collectAsStateWithLifecycle()
    val playing by vm.playing.collectAsStateWithLifecycle()
    val tab     by vm.tab.collectAsStateWithLifecycle()
    val search  by vm.search.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            AetherTopBar(title = "Musique", searchable = true,
                searchQuery = search, onSearchChange = vm::setSearch)
        },
        bottomBar = {
            // Mini lecteur persistant
            current?.let { track ->
                MiniPlayer(track = track, playing = playing,
                    onToggle = { vm.togglePlay() },
                    onNext   = { vm.next() })
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Onglets
            TabRow(selectedTabIndex = MusicTab.values().indexOf(tab)) {
                listOf("Titres", "Albums", "Artistes").forEachIndexed { i, label ->
                    Tab(selected = tab.ordinal == i, onClick = { vm.setTab(MusicTab.values()[i]) },
                        text = { Text(label) })
                }
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else when (tab) {
                MusicTab.TRACKS -> {
                    if (tracks.isEmpty()) Box(Modifier.fillMaxSize()) {
                        AetherEmptyState(Icons.Rounded.MusicNote, "Aucune musique", "Ajoutez des fichiers MP3 sur votre téléphone")
                    } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                        items(tracks, key = { it.id }) { track ->
                            TrackRow(track = track, isPlaying = current?.id == track.id && playing,
                                onClick = { vm.play(track) },
                                onNote  = { AetherIntents.shareToNotes(context, track.title, "🎵 ${track.artist} — ${track.title}\n💿 ${track.album}\n⏱ ${track.formattedDuration}") })
                        }
                    }
                }
                MusicTab.ALBUMS -> LazyColumn(Modifier.fillMaxSize()) {
                    items(albums, key = { it.id }) { album ->
                        AlbumRow(album = album, onClick = { /* filtrer titres */ })
                    }
                }
                else -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("Bientôt disponible", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TrackRow(track: Track, isPlaying: Boolean, onClick: () -> Unit, onNote: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    ListItem(
        headlineContent  = {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        },
        supportingContent = {
            Text("${track.artist}  ·  ${track.album}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        leadingContent = {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)) {
                AsyncImage(model = ImageRequest.Builder(LocalContext.current)
                    .data(track.albumArtUri).crossfade(true).build(),
                    contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize())
                if (isPlaying) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(.5f)),
                        Alignment.Center) { Icon(Icons.Rounded.MusicNote, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp)) }
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(track.formattedDuration, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.MoreVert, null, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Mémoriser dans Notes") },
                            leadingIcon = { Icon(Icons.Rounded.Note, null, tint = AetherColors.NotesAmber) },
                            onClick = { onNote(); showMenu = false })
                    }
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider(Modifier.padding(start = 60.dp), thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.surfaceVariant)
}

@Composable
private fun AlbumRow(album: AetherAlbum, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text("${album.artist}  ·  ${album.trackCount} titre${if (album.trackCount > 1) "s" else ""}",
            color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingContent = {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)) {
                AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(album.artUri).crossfade(true).build(),
                    contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider(Modifier.padding(start = 64.dp), thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.surfaceVariant)
}

@Composable
private fun MiniPlayer(track: Track, playing: Boolean, onToggle: () -> Unit, onNext: () -> Unit) {
    Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(track.albumArtUri).crossfade(true).build(),
                    contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onToggle) {
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Rounded.SkipNext, null, modifier = Modifier.size(24.dp))
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// ACTIVITY + APP
// ════════════════════════════════════════════════════════════════════════════

class MusicApp : Application()

class MainActivity : ComponentActivity() {
    private lateinit var vm: MusicViewModel
    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { vm.load() }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        vm = ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(application))[MusicViewModel::class.java]

        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        permLauncher.launch(perms)

        setContent { AetherTheme { Surface(Modifier.fillMaxSize()) { MusicApp(vm) } } }
    }
}
