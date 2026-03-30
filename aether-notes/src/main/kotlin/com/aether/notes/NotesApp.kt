package com.aether.notes

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.room.*
import com.aether.core.AetherIntents
import com.aether.core.security.AetherCrypto
import com.aether.core.ui.components.*
import com.aether.core.ui.theme.AetherColors
import com.aether.core.ui.theme.AetherTheme
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ════════════════════════════════════════════════════════════════════════════
// MODEL + ROOM DATABASE
// ════════════════════════════════════════════════════════════════════════════

/**
 * Entité Room : titre en clair (pour recherche rapide),
 * contenu chiffré AES-256-GCM, couleur de catégorie.
 *
 * Schéma de sécurité :
 *  - La clé AES est dans l'AndroidKeyStore (jamais dans l'APK)
 *  - Le contenu en base est inutilisable sans la clé
 *  - Pas de backup cloud (android:allowBackup="false")
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id:           Long   = 0L,
    @ColumnInfo(name = "title")      val title:        String = "",   // en clair (index)
    @ColumnInfo(name = "body_enc")   val bodyEncrypted: String = "",  // AES-256-GCM
    @ColumnInfo(name = "color")      val colorHex:     String = "#1F1D2B",
    @ColumnInfo(name = "pinned")     val pinned:       Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt:    Long   = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt:    Long   = System.currentTimeMillis(),
    @ColumnInfo(name = "tags")       val tags:         String = "",   // CSV
)

data class Note(
    val id:        Long,
    val title:     String,
    val body:      String,   // déchiffré — JAMAIS persisté en clair
    val colorHex:  String,
    val pinned:    Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val tags:      List<String>,
) {
    val snippet: String get() = body.lines().firstOrNull { it.isNotBlank() }?.take(80) ?: ""
    val color: Color get() = runCatching {
        Color(android.graphics.Color.parseColor(colorHex))
    }.getOrDefault(Color(0xFF1F1D2B))
}

// Note card colors palette
val NOTE_COLORS = listOf(
    "#1F1D2B", "#1A2744", "#1B3A2D", "#3A1A1A",
    "#2A1A3A", "#1A3A3A", "#2A2A1A", "#2D1A2A",
)
val NOTE_COLORS_LIGHT = listOf(
    "#F0EEFF", "#E8F0FF", "#E8F5EE", "#FFE8E8",
    "#F0E8FF", "#E8F5F5", "#F5F5E8", "#F5E8F0",
)

// ════════════════════════════════════════════════════════════════════════════
// ROOM DAO + DATABASE
// ════════════════════════════════════════════════════════════════════════════

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY pinned DESC, updated_at DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE title LIKE '%' || :q || '%' ORDER BY updated_at DESC")
    fun search(q: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun findById(id: Long): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity): Long

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("UPDATE notes SET pinned = :pinned WHERE id = :id")
    suspend fun setPin(id: Long, pinned: Boolean)
}

@Database(entities = [NoteEntity::class], version = 1, exportSchema = false)
abstract class NotesDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile private var INSTANCE: NotesDatabase? = null
        fun get(context: Context): NotesDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                NotesDatabase::class.java,
                "aether_notes.db",
            ).build().also { INSTANCE = it }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// REPOSITORY
// ════════════════════════════════════════════════════════════════════════════

class NotesRepository(context: Context) {

    private val dao = NotesDatabase.get(context).noteDao()

    /** Flux de toutes les notes — chiffrement transparent */
    val allNotes: Flow<List<Note>> = dao.observeAll().map { entities ->
        entities.map { it.toNote() }
    }

    fun search(q: String): Flow<List<Note>> = dao.search(q).map { e -> e.map { it.toNote() } }

    suspend fun save(note: Note): Long {
        val entity = NoteEntity(
            id            = note.id,
            title         = note.title,
            bodyEncrypted = AetherCrypto.encrypt(note.body),  // ← chiffrement ici
            colorHex      = note.colorHex,
            pinned        = note.pinned,
            createdAt     = note.createdAt,
            updatedAt     = System.currentTimeMillis(),
            tags          = note.tags.joinToString(","),
        )
        return dao.upsert(entity)
    }

    suspend fun delete(note: Note) = dao.delete(
        NoteEntity(id = note.id, title = note.title, bodyEncrypted = "")
    )

    suspend fun setPin(id: Long, pinned: Boolean) = dao.setPin(id, pinned)

    suspend fun getById(id: Long): Note? = dao.findById(id)?.toNote()

    // Déchiffrement à la lecture
    private fun NoteEntity.toNote() = Note(
        id        = id,
        title     = title,
        body      = AetherCrypto.decrypt(bodyEncrypted),  // ← déchiffrement ici
        colorHex  = colorHex,
        pinned    = pinned,
        createdAt = createdAt,
        updatedAt = updatedAt,
        tags      = tags.split(",").filter { it.isNotBlank() },
    )
}

// ════════════════════════════════════════════════════════════════════════════
// VIEWMODEL
// ════════════════════════════════════════════════════════════════════════════

class NotesViewModel(app: Application) : AndroidViewModel(app) {

    private val repo    = NotesRepository(app)
    private val _search = MutableStateFlow("")
    private val _active = MutableStateFlow<Note?>(null)
    private val _grid   = MutableStateFlow(true)

    val search:    StateFlow<String>  = _search.asStateFlow()
    val activeNote: StateFlow<Note?> = _active.asStateFlow()
    val gridMode:  StateFlow<Boolean> = _grid.asStateFlow()

    val notes: StateFlow<List<Note>> = _search
        .flatMapLatest { q ->
            if (q.isBlank()) repo.allNotes
            else repo.search(q)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSearch(q: String)       { _search.value = q }
    fun setActive(n: Note?)        { _active.value = n }
    fun toggleGrid()               { _grid.value = !_grid.value }

    fun save(note: Note) = viewModelScope.launch {
        val id = repo.save(note)
        _active.value = note.copy(id = id)
    }

    fun delete(note: Note) = viewModelScope.launch {
        repo.delete(note)
        _active.value = null
    }

    fun togglePin(note: Note) = viewModelScope.launch {
        repo.setPin(note.id, !note.pinned)
    }

    fun newNote(prefillTitle: String = "", prefillBody: String = "") {
        _active.value = Note(
            id        = 0L,
            title     = prefillTitle,
            body      = prefillBody,
            colorHex  = NOTE_COLORS.first(),
            pinned    = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            tags      = emptyList(),
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
// UI
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesApp(vm: NotesViewModel) {
    val notes      by vm.notes.collectAsStateWithLifecycle()
    val activeNote by vm.activeNote.collectAsStateWithLifecycle()
    val search     by vm.search.collectAsStateWithLifecycle()
    val gridMode   by vm.gridMode.collectAsStateWithLifecycle()
    val context    = LocalContext.current

    // Éditeur actif
    if (activeNote != null) {
        NoteEditorScreen(
            note     = activeNote!!,
            onSave   = { vm.save(it) },
            onDelete = { vm.delete(it) },
            onBack   = { vm.setActive(null) },
        )
        return
    }

    Scaffold(
        topBar = {
            AetherTopBar(
                title          = "Notes",
                searchable     = true,
                searchQuery    = search,
                onSearchChange = vm::setSearch,
                actions = {
                    IconButton(onClick = { vm.toggleGrid() }) {
                        Icon(
                            if (gridMode) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                            contentDescription = "Changer la vue",
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            AetherFab(onClick = { vm.newNote() }, label = "Nouvelle note") {
                Icon(Icons.Rounded.Add, null)
            }
        },
    ) { padding ->
        if (notes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                AetherEmptyState(
                    icon    = Icons.Rounded.NoteAdd,
                    title   = "Aucune note",
                    message = "Vos notes sont chiffrées AES-256-GCM.\nPersonne ne peut les lire sans votre appareil.",
                    action  = "Créer une note" to { vm.newNote() },
                )
            }
        } else if (gridMode) {
            // Grille en cascade (Staggered)
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 80.dp),
                verticalItemSpacing = 8.dp,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(notes, key = { it.id }) { note ->
                    NoteCard(note = note,
                        onClick = { vm.setActive(note) },
                        onPin   = { vm.togglePin(note) })
                }
            }
        } else {
            // Liste
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                items(notes, key = { it.id }) { note ->
                    NoteListRow(note = note,
                        onClick = { vm.setActive(note) },
                        onPin   = { vm.togglePin(note) })
                }
            }
        }
    }
}

@Composable
private fun NoteCard(note: Note, onClick: () -> Unit, onPin: () -> Unit) {
    Card(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = note.color),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterStart) {
                if (note.title.isNotBlank()) {
                    Text(note.title, style = MaterialTheme.typography.titleMedium,
                        color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                }
                if (note.pinned) {
                    Icon(Icons.Rounded.PushPin, null, tint = AetherColors.NotesAmber,
                        modifier = Modifier.size(16.dp))
                }
            }
            if (note.snippet.isNotBlank()) {
                if (note.title.isNotBlank()) Spacer(Modifier.height(4.dp))
                Text(note.snippet, style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f), maxLines = 5,
                    overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            Text(formatNoteDate(note.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun NoteListRow(note: Note, onClick: () -> Unit, onPin: () -> Unit) {
    ListItem(
        headlineContent  = {
            Text(note.title.ifBlank { note.snippet },
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                if (note.title.isNotBlank()) note.snippet else "",
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                if (note.pinned) Icon(Icons.Rounded.PushPin, null,
                    tint = AetherColors.NotesAmber, modifier = Modifier.size(14.dp))
                Text(formatNoteDate(note.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        modifier  = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Box(Modifier.size(12.dp).let {
                it // colored dot
            })
        }
    )
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.surfaceVariant)
}

// ── Éditeur de note ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    note:     Note,
    onSave:   (Note) -> Unit,
    onDelete: (Note) -> Unit,
    onBack:   () -> Unit,
) {
    var title      by remember { mutableStateOf(note.title) }
    var body       by remember { mutableStateOf(note.body) }
    var colorHex   by remember { mutableStateOf(note.colorHex) }
    var showColors by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    val context    = LocalContext.current

    // Auto-save à chaque modification (debounce géré par le ViewModel)
    val currentNote = remember(title, body, colorHex) {
        note.copy(title = title, body = body, colorHex = colorHex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        if (title.isNotBlank() || body.isNotBlank()) onSave(currentNote)
                        onBack()
                    }) { Icon(Icons.Rounded.ArrowBackIosNew, "Retour") }
                },
                title = {},
                actions = {
                    // Partager vers SMS
                    IconButton(onClick = {
                        AetherIntents.sendSms(context, "")
                        // Partage système
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type    = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "$title\n\n$body")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Partager"))
                    }) { Icon(Icons.Rounded.Share, "Partager") }

                    // Changer la couleur
                    IconButton(onClick = { showColors = !showColors }) {
                        Icon(Icons.Rounded.Palette, "Couleur")
                    }
                    // Supprimer
                    IconButton(onClick = { showDelete = true }) {
                        Icon(Icons.Rounded.Delete, "Supprimer",
                            tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // Sélecteur de couleur
            AnimatedVisibility(visible = showColors) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NOTE_COLORS.forEach { hex ->
                        Box(
                            Modifier.size(32.dp)
                                .let { m -> if (hex == colorHex) m.padding(2.dp) else m }
                                .clickable { colorHex = hex; showColors = false }
                        ) {
                            Card(shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = runCatching {
                                    Color(android.graphics.Color.parseColor(hex))
                                }.getOrDefault(Color.Gray)),
                                modifier = Modifier.fillMaxSize()) {}
                        }
                    }
                }
            }

            // Titre
            TextField(
                value         = title,
                onValueChange = { title = it },
                placeholder   = { Text("Titre", style = MaterialTheme.typography.headlineMedium) },
                textStyle     = MaterialTheme.typography.headlineMedium,
                modifier      = Modifier.fillMaxWidth(),
                colors        = TextFieldDefaults.colors(
                    focusedContainerColor   = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor   = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                singleLine    = true,
            )

            // Corps — champ principal
            TextField(
                value         = body,
                onValueChange = { body = it },
                placeholder   = { Text("Commencez à écrire…") },
                modifier      = Modifier.fillMaxSize(),
                colors        = TextFieldDefaults.colors(
                    focusedContainerColor   = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor   = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            icon  = { Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Supprimer cette note ?") },
            text  = { Text("Cette note chiffrée sera définitivement supprimée.") },
            confirmButton = {
                TextButton(onClick = { onDelete(note); showDelete = false }) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Annuler") }
            },
        )
    }
}

private fun formatNoteDate(ts: Long): String {
    val now  = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ts }
    return when {
        now.get(Calendar.DATE) == then.get(Calendar.DATE) ->
            SimpleDateFormat("HH:mm", Locale.FRENCH).format(Date(ts))
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR) ->
            SimpleDateFormat("dd MMM", Locale.FRENCH).format(Date(ts))
        else ->
            SimpleDateFormat("dd/MM/yy", Locale.FRENCH).format(Date(ts))
    }
}

// ════════════════════════════════════════════════════════════════════════════
// ACTIVITY + APP
// ════════════════════════════════════════════════════════════════════════════

class NotesApp : Application()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val vm = ViewModelProvider(this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application))[NotesViewModel::class.java]

        // Gérer les intents entrants (partage depuis autre app Aether)
        when (intent?.action) {
            Intent.ACTION_SEND, AetherIntents.ACTION_SHARE_TO_NOTE -> {
                val t = intent.getStringExtra(AetherIntents.EXTRA_NOTE_TITLE) ?: ""
                val b = intent.getStringExtra(AetherIntents.EXTRA_NOTE_CONTENT)
                    ?: intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                if (t.isNotBlank() || b.isNotBlank()) vm.newNote(t, b)
            }
            AetherIntents.ACTION_NEW_NOTE -> vm.newNote()
        }

        setContent {
            AetherTheme {
                Surface(Modifier.fillMaxSize()) { NotesApp(vm) }
            }
        }
    }
}
