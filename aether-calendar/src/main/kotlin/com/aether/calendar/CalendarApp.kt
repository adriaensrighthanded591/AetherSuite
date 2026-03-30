package com.aether.calendar

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.room.*
import com.aether.core.AetherIntents
import com.aether.core.ui.components.*
import com.aether.core.ui.theme.AetherColors
import com.aether.core.ui.theme.AetherTheme
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ════════════════════════════════════════════════════════════════════════════
// MODEL + ROOM
// ════════════════════════════════════════════════════════════════════════════

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id:       Long    = 0L,
    @ColumnInfo(name = "title")      val title:    String  = "",
    @ColumnInfo(name = "description")val desc:     String  = "",
    @ColumnInfo(name = "start_ms")   val startMs:  Long    = 0L,
    @ColumnInfo(name = "end_ms")     val endMs:    Long    = 0L,
    @ColumnInfo(name = "all_day")    val allDay:   Boolean = false,
    @ColumnInfo(name = "color_hex")  val colorHex: String  = "#6B4EFF",
    @ColumnInfo(name = "location")   val location: String  = "",
    @ColumnInfo(name = "reminder_min") val reminderMin: Int = 15,  // 0 = pas de rappel
)

data class CalEvent(
    val id:          Long,
    val title:       String,
    val desc:        String,
    val startMs:     Long,
    val endMs:       Long,
    val allDay:      Boolean,
    val colorHex:    String,
    val location:    String,
    val reminderMin: Int,
) {
    val color: Color get() = runCatching { Color(android.graphics.Color.parseColor(colorHex)) }
        .getOrDefault(Color(0xFF6B4EFF))
    val startLabel: String get() = if (allDay) "Toute la journée"
        else SimpleDateFormat("HH:mm", Locale.FRENCH).format(Date(startMs))
    val dateLabel: String get() =
        SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRENCH).format(Date(startMs))
            .replaceFirstChar { it.uppercase() }
    val shareSummary: String get() = buildString {
        append("📅 $title\n")
        append("🗓 $dateLabel\n")
        if (!allDay) append("⏰ $startLabel\n")
        if (location.isNotBlank()) append("📍 $location\n")
        if (desc.isNotBlank()) append("ℹ️ $desc")
    }
}

val EVENT_COLORS = listOf("#6B4EFF","#F57C00","#E91E63","#00BCD4","#4CAF50","#3949AB","#FFB300","#E53935")

fun EventEntity.toEvent() = CalEvent(id, title, desc, startMs, endMs, allDay, colorHex, location, reminderMin)
fun CalEvent.toEntity()   = EventEntity(id, title, desc, startMs, endMs, allDay, colorHex, location, reminderMin)

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY start_ms ASC")
    fun observeAll(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE start_ms >= :from AND start_ms < :to ORDER BY start_ms ASC")
    fun observeRange(from: Long, to: Long): Flow<List<EventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: EventEntity): Long

    @Delete
    suspend fun delete(e: EventEntity)
}

@Database(entities = [EventEntity::class], version = 1, exportSchema = false)
abstract class CalendarDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    companion object {
        @Volatile private var INST: CalendarDatabase? = null
        fun get(ctx: Context) = INST ?: synchronized(this) {
            INST ?: Room.databaseBuilder(ctx.applicationContext, CalendarDatabase::class.java, "aether_cal.db").build().also { INST = it }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// VIEWMODEL
// ════════════════════════════════════════════════════════════════════════════

class CalendarViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = CalendarDatabase.get(app).eventDao()

    private val _selectedDay = MutableStateFlow(todayStart())
    private val _editing     = MutableStateFlow<CalEvent?>(null)
    private val _view        = MutableStateFlow(CalView.MONTH)

    val selectedDay: StateFlow<Long>       = _selectedDay.asStateFlow()
    val editing:     StateFlow<CalEvent?>  = _editing.asStateFlow()
    val view:        StateFlow<CalView>    = _view.asStateFlow()

    // Tous les événements
    val allEvents: StateFlow<List<CalEvent>> = dao.observeAll().map { list -> list.map { it.toEvent() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Événements du jour sélectionné
    val dayEvents: StateFlow<List<CalEvent>> = combine(allEvents, _selectedDay) { events, day ->
        val end = day + 24 * 60 * 60 * 1000L
        events.filter { it.startMs in day until end }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Événements du mois courant
    val monthEvents: StateFlow<List<CalEvent>> = combine(allEvents, _selectedDay) { events, day ->
        val cal = Calendar.getInstance().apply { timeInMillis = day }
        cal.set(Calendar.DAY_OF_MONTH, 1); val from = cal.timeInMillis
        cal.add(Calendar.MONTH, 1); val to = cal.timeInMillis
        events.filter { it.startMs in from until to }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectDay(ms: Long)          { _selectedDay.value = ms }
    fun setView(v: CalView)          { _view.value = v }
    fun startEdit(e: CalEvent? = null) {
        _editing.value = e ?: CalEvent(0L, "", "", todayStart() + 9 * 3600_000L,
            todayStart() + 10 * 3600_000L, false, "#6B4EFF", "", 15)
    }
    fun cancelEdit()                 { _editing.value = null }

    fun save(e: CalEvent) = viewModelScope.launch {
        dao.upsert(e.toEntity())
        _editing.value = null
        // Planifier (ou replanifier) le rappel
        CalendarAlarmScheduler.schedule(getApplication(), e)
    }
    fun delete(e: CalEvent) = viewModelScope.launch {
        dao.delete(e.toEntity())
        CalendarAlarmScheduler.cancel(getApplication(), e.id)
        _editing.value = null
    }
}

enum class CalView { MONTH, AGENDA }

private fun todayStart(): Long {
    val c = Calendar.getInstance(); c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0); return c.timeInMillis
}

// ════════════════════════════════════════════════════════════════════════════
// UI
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarApp(vm: CalendarViewModel) {
    val allEvents   by vm.allEvents.collectAsStateWithLifecycle()
    val dayEvents   by vm.dayEvents.collectAsStateWithLifecycle()
    val monthEvents by vm.monthEvents.collectAsStateWithLifecycle()
    val selectedDay by vm.selectedDay.collectAsStateWithLifecycle()
    val editing     by vm.editing.collectAsStateWithLifecycle()
    val view        by vm.view.collectAsStateWithLifecycle()

    if (editing != null) {
        EventEditorScreen(
            event    = editing!!,
            onSave   = { vm.save(it) },
            onDelete = { vm.delete(it) },
            onCancel = { vm.cancelEdit() },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(monthName(selectedDay)) },
                actions = {
                    IconButton(onClick = { vm.setView(if (view == CalView.MONTH) CalView.AGENDA else CalView.MONTH) }) {
                        Icon(if (view == CalView.MONTH) Icons.Rounded.ViewAgenda else Icons.Rounded.CalendarMonth, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        floatingActionButton = {
            AetherFab(onClick = { vm.startEdit() }, label = "Événement") {
                Icon(Icons.Rounded.Add, null)
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (view) {
                CalView.MONTH -> {
                    MonthGrid(
                        selectedDay   = selectedDay,
                        eventsThisMonth = monthEvents,
                        onDayClick    = { vm.selectDay(it) },
                    )
                    HorizontalDivider()
                }
                CalView.AGENDA -> {}
            }

            // Liste des événements du jour / agenda
            val displayEvents = if (view == CalView.MONTH) dayEvents else allEvents
            if (displayEvents.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.EventBusy, null, modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(.25f))
                        Spacer(Modifier.height(8.dp))
                        Text(if (view == CalView.MONTH) "Aucun événement ce jour"
                             else "Aucun événement", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(.5f))
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(displayEvents, key = { it.id }) { event ->
                        EventRow(event = event,
                            onClick = { vm.startEdit(event) },
                            onShare = { shareEvent(LocalContext.current, event) })
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(selectedDay: Long, eventsThisMonth: List<CalEvent>, onDayClick: (Long) -> Unit) {
    val cal    = Calendar.getInstance().apply { timeInMillis = selectedDay; set(Calendar.DAY_OF_MONTH, 1) }
    val first  = cal.get(Calendar.DAY_OF_WEEK) - 1  // 0=dim
    val days   = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val today  = todayStart()
    val eventDays = eventsThisMonth.map {
        Calendar.getInstance().apply { timeInMillis = it.startMs }.get(Calendar.DAY_OF_MONTH)
    }.toSet()

    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        // En-têtes jours
        Row(Modifier.fillMaxWidth()) {
            listOf("D","L","M","M","J","V","S").forEach { d ->
                Box(Modifier.weight(1f), Alignment.Center) {
                    Text(d, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        // Grille
        var day = 1
        repeat(6) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val slot = week * 7 + col
                    if (slot < first || day > days) {
                        Box(Modifier.weight(1f).height(44.dp))
                    } else {
                        val dayMs = cal.timeInMillis + (day - 1) * 86_400_000L
                        val isSelected = dayMs == selectedDay
                        val isToday    = dayMs == today
                        val hasEvent   = day in eventDays
                        Box(
                            Modifier.weight(1f).height(44.dp).clickable { onDayClick(dayMs) },
                            Alignment.Center,
                        ) {
                            Box(
                                Modifier.size(36.dp)
                                    .background(when { isSelected -> MaterialTheme.colorScheme.primary; isToday -> MaterialTheme.colorScheme.primaryContainer; else -> Color.Transparent }, CircleShape),
                                Alignment.Center,
                            ) {
                                Text(day.toString(), style = MaterialTheme.typography.bodyMedium,
                                    color = when { isSelected -> MaterialTheme.colorScheme.onPrimary; isToday -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.onSurface })
                            }
                            if (hasEvent && !isSelected) {
                                Box(Modifier.size(5.dp).align(Alignment.BottomCenter).offset(y = (-3).dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape))
                            }
                        }
                        day++
                    }
                }
            }
            if (day > days) return@repeat
        }
    }
}

@Composable
private fun EventRow(event: CalEvent, onClick: () -> Unit, onShare: () -> Unit) {
    val ctx = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Indicateur couleur
        Box(Modifier.width(4.dp).height(40.dp).background(event.color, RoundedCornerShape(2.dp)))
        Column(Modifier.weight(1f)) {
            Text(event.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(event.startLabel, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (event.location.isNotBlank()) {
                    Text("· ${event.location}", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.Share, "Partager", modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(Modifier.padding(start = 32.dp), thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.surfaceVariant)
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditorScreen(event: CalEvent, onSave: (CalEvent) -> Unit, onDelete: (CalEvent) -> Unit, onCancel: () -> Unit) {
    var title    by remember { mutableStateOf(event.title) }
    var desc     by remember { mutableStateOf(event.desc) }
    var location by remember { mutableStateOf(event.location) }
    var allDay   by remember { mutableStateOf(event.allDay) }
    var colorHex by remember { mutableStateOf(event.colorHex) }
    var showDel  by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, null) } },
                title = { Text(if (event.id == 0L) "Nouvel événement" else "Modifier") },
                actions = {
                    if (event.id > 0) {
                        IconButton(onClick = { showDel = true }) {
                            Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = {
                        if (title.isNotBlank()) onSave(event.copy(title = title, desc = desc,
                            location = location, allDay = allDay, colorHex = colorHex))
                    }, enabled = title.isNotBlank()) { Text("Enregistrer") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedTextField(value = title, onValueChange = { title = it },
                    label = { Text("Titre *") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    leadingIcon = { Icon(Icons.Rounded.Title, null, tint = MaterialTheme.colorScheme.primary) })
            }
            item {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Toute la journée", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = allDay, onCheckedChange = { allDay = it })
                }
            }
            item {
                OutlinedTextField(value = location, onValueChange = { location = it },
                    label = { Text("Lieu") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Rounded.LocationOn, null, tint = MaterialTheme.colorScheme.primary) })
            }
            item {
                OutlinedTextField(value = desc, onValueChange = { desc = it },
                    label = { Text("Description") }, maxLines = 4, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    leadingIcon = { Icon(Icons.Rounded.Notes, null, tint = MaterialTheme.colorScheme.primary) })
            }
            item {
                // Sélecteur couleur
                Text("Couleur", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    EVENT_COLORS.forEach { hex ->
                        Box(Modifier.size(32.dp).background(runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray), CircleShape)
                            .then(if (hex == colorHex) Modifier.border(androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), CircleShape) else Modifier)
                            .clickable { colorHex = hex })
                    }
                }
            }
        }
    }

    if (showDel) {
        AlertDialog(onDismissRequest = { showDel = false },
            icon  = { Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Supprimer cet événement ?") },
            confirmButton = { TextButton(onClick = { onDelete(event); showDel = false }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDel = false }) { Text("Annuler") } })
    }
}



private fun shareEvent(context: Context, event: CalEvent) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, event.shareSummary)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    context.startActivity(Intent.createChooser(intent, "Partager l'événement").apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
}

private fun monthName(ms: Long): String =
    SimpleDateFormat("MMMM yyyy", Locale.FRENCH).format(Date(ms)).replaceFirstChar { it.uppercase() }

// ════════════════════════════════════════════════════════════════════════════
// ACTIVITY + APP
// ════════════════════════════════════════════════════════════════════════════

class CalApp : Application()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val vm = ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(application))[CalendarViewModel::class.java]
        setContent { AetherTheme { Surface(Modifier.fillMaxSize()) { CalendarApp(vm) } } }
    }
}
