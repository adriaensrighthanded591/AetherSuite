package com.aethersms.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aethersms.data.model.Conversation
import com.aethersms.viewmodel.ConversationListViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    viewModel: ConversationListViewModel,
    onOpenConversation: (Long, String) -> Unit,
    onNewConversation: () -> Unit,
    onSettings: () -> Unit,
) {
    val conversations by viewModel.filtered.collectAsStateWithLifecycle()
    val loading       by viewModel.loading.collectAsStateWithLifecycle()
    val search        by viewModel.search.collectAsStateWithLifecycle()
    var showSearch    by remember { mutableStateOf(false) }
    var deleteTarget  by remember { mutableStateOf<Conversation?>(null) }

    Scaffold(
        topBar = {
            AetherTopBar(
                showSearch     = showSearch,
                searchQuery    = search,
                onSearchToggle = { showSearch = !showSearch; if (!showSearch) viewModel.setSearch("") },
                onSearchChange = viewModel::setSearch,
                onSettings     = onSettings,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick        = onNewConversation,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor   = MaterialTheme.colorScheme.onPrimary,
                shape          = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Rounded.Edit, contentDescription = "Nouveau message")
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                conversations.isEmpty() -> EmptyState()
                else -> LazyColumn(
                    modifier       = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(
                        items = conversations,
                        key   = { it.threadId },
                    ) { conv ->
                        SwipeableConversationRow(
                            conversation = conv,
                            onClick      = {
                                onOpenConversation(
                                    conv.threadId,
                                    conv.recipientAddresses.firstOrNull() ?: "",
                                )
                            },
                            onDelete = { deleteTarget = conv },
                        )
                    }
                }
            }
        }
    }

    // Dialogue de confirmation de suppression
    deleteTarget?.let { conv ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon    = { Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title   = { Text("Supprimer la conversation") },
            text    = { Text("Supprimer tous les messages avec ${conv.displayName} ? Cette action est irréversible.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteConversation(conv.threadId)
                    deleteTarget = null
                }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Annuler") }
            },
        )
    }
}

// ── Swipe-to-dismiss (Material 3) ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableConversationRow(
    conversation: Conversation,
    onClick:  () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                false   // false = ne pas supprimer visuellement avant confirmation
            } else false
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.4f },
    )

    SwipeToDismissBox(
        state             = dismissState,
        enableDismissFromStartToEnd = false,    // swipe droite→gauche uniquement
        enableDismissFromEndToStart = true,
        backgroundContent = {
            // Fond rouge qui apparaît lors du swipe
            val progress = when (dismissState.targetValue) {
                SwipeToDismissBoxValue.EndToStart -> 1f
                else -> 0f
            }
            val bgColor = Color(
                red   = 0.90f,
                green = 0.22f + (1f - progress) * 0.22f,
                blue  = 0.21f + (1f - progress) * 0.21f,
                alpha = 0.15f + progress * 0.85f,
            )
            Box(
                modifier          = Modifier.fillMaxSize().background(bgColor),
                contentAlignment  = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector        = Icons.Rounded.Delete,
                    contentDescription = "Supprimer",
                    tint               = Color.White,
                    modifier           = Modifier.padding(end = 24.dp).size(24.dp),
                )
            }
        },
    ) {
        ConversationRow(conversation = conversation, onClick = onClick)
    }
}

@Composable
private fun ConversationRow(conversation: Conversation, onClick: () -> Unit) {
    val hasUnread = conversation.unreadCount > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AvatarBadge(
            name     = conversation.displayName,
            photoUri = conversation.photoUri,
            unread   = conversation.unreadCount,
        )
        Column(Modifier.weight(1f)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text       = conversation.displayName,
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Medium,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier.weight(1f),
                )
                Text(
                    text       = formatDate(conversation.date),
                    style      = MaterialTheme.typography.labelSmall,
                    color      = if (hasUnread) MaterialTheme.colorScheme.primary
                                 else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (hasUnread) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Indicateur "Brouillon"
                if (conversation.snippet.startsWith("[Brouillon]")) {
                    Text(
                        "Brouillon",
                        style  = MaterialTheme.typography.labelSmall,
                        color  = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text     = conversation.snippet.removePrefix("[Brouillon] "),
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (hasUnread) FontWeight.Medium else FontWeight.Normal,
                )
            }
        }
    }
    HorizontalDivider(
        modifier  = Modifier.padding(start = 76.dp),
        thickness = 0.5.dp,
        color     = MaterialTheme.colorScheme.surfaceVariant,
    )
}

@Composable
fun AvatarBadge(name: String, photoUri: String?, unread: Int) {
    Box(contentAlignment = Alignment.BottomEnd) {
        if (!photoUri.isNullOrBlank()) {
            AsyncImage(
                model              = photoUri,
                contentDescription = name,
                modifier           = Modifier.size(48.dp).clip(CircleShape),
            )
        } else {
            Box(
                modifier         = Modifier.size(48.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                val initials = name.split(" ", ",").filter { it.isNotBlank() }.take(2)
                    .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
                Text(
                    text       = initials,
                    style      = MaterialTheme.typography.bodyLarge,
                    color      = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (unread > 0) {
            Box(
                modifier         = Modifier.size(18.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text  = if (unread > 9) "9+" else unread.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AetherTopBar(
    showSearch:    Boolean,
    searchQuery:   String,
    onSearchToggle: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSettings:    () -> Unit,
) {
    TopAppBar(
        title = {
            AnimatedContent(targetState = showSearch, label = "search_toggle") { searching ->
                if (searching) {
                    OutlinedTextField(
                        value         = searchQuery,
                        onValueChange = onSearchChange,
                        placeholder   = { Text("Rechercher…") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth().padding(end = 8.dp),
                        shape         = RoundedCornerShape(12.dp),
                    )
                } else {
                    Text("AetherSMS", style = MaterialTheme.typography.titleLarge)
                }
            }
        },
        actions = {
            IconButton(onClick = onSearchToggle) {
                Icon(
                    if (showSearch) Icons.Rounded.Close else Icons.Rounded.Search,
                    contentDescription = "Rechercher",
                )
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "Paramètres")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
private fun EmptyState() {
    Column(
        modifier            = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.ChatBubbleOutline,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Aucune conversation",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Appuyez sur ✏️ pour écrire votre premier message",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}

private fun formatDate(ts: Long): String {
    if (ts == 0L) return ""
    val now  = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ts }
    return when {
        now.get(Calendar.DATE) == then.get(Calendar.DATE) ->
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
        now.get(Calendar.WEEK_OF_YEAR) == then.get(Calendar.WEEK_OF_YEAR) ->
            SimpleDateFormat("EEE", Locale.FRENCH).format(Date(ts))
        else ->
            SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(ts))
    }
}
