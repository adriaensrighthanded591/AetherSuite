package com.aethersms.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aethersms.data.repository.NetworkStatus
import com.aethersms.data.repository.WifiMessagingManager
import com.aethersms.ui.components.InputBar
import com.aethersms.ui.components.MessageBubble
import com.aethersms.viewmodel.ConversationViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    viewModel: ConversationViewModel,
    address:   String,
    onBack:    () -> Unit,
) {
    val context   = LocalContext.current
    val messages  by viewModel.messages.collectAsStateWithLifecycle()
    val loading   by viewModel.loading.collectAsStateWithLifecycle()
    val sending   by viewModel.sending.collectAsStateWithLifecycle()
    val draft     by viewModel.draft.collectAsStateWithLifecycle()
    val searchQ   by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchRes by viewModel.searchResults.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val error             by viewModel.error.collectAsStateWithLifecycle()
    val listState         = rememberLazyListState()
    val scope             = rememberCoroutineScope()
    var showSearch        by remember { mutableStateOf(false) }

    // Réseau WiFi
    val wifiMgr = remember { WifiMessagingManager(context) }
    val networkStatus = remember { wifiMgr.getNetworkStatus() }

    // Afficher les erreurs via Snackbar
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(message = it, withDismissAction = true)
            viewModel.clearError()
        }
    }

    // Scroll vers le bas sur nouveaux messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && !showSearch) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Rounded.ArrowBackIosNew, "Retour")
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text     = address,
                                style    = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            NetworkBadge(status = networkStatus)
                        }
                    },
                    actions = {
                        // Bouton recherche dans la conversation
                        IconButton(onClick = {
                            showSearch = !showSearch
                            if (!showSearch) viewModel.clearSearch()
                        }) {
                            Icon(
                                if (showSearch) Icons.Rounded.SearchOff else Icons.Rounded.Search,
                                contentDescription = "Rechercher dans la conversation",
                            )
                        }
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$address"))
                            context.startActivity(intent)
                        }) {
                            Icon(Icons.Rounded.Call, "Appeler")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )

                // Barre de recherche (s'affiche/masque avec animation)
                AnimatedVisibility(
                    visible = showSearch,
                    enter   = expandVertically() + fadeIn(),
                    exit    = shrinkVertically() + fadeOut(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value         = searchQ,
                            onValueChange = viewModel::setSearchQuery,
                            modifier      = Modifier.weight(1f),
                            placeholder   = { Text("Rechercher dans les messages…") },
                            singleLine    = true,
                            shape         = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            trailingIcon  = if (searchQ.isNotBlank()) {{
                                IconButton(onClick = { viewModel.clearSearch() }) {
                                    Icon(Icons.Rounded.Close, null, modifier = Modifier.size(18.dp))
                                }
                            }} else null,
                        )
                        if (searchRes.isNotEmpty()) {
                            Text(
                                "${searchRes.size} résultat${if (searchRes.size > 1) "s" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column {
                AnimatedVisibility(visible = sending) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color    = MaterialTheme.colorScheme.primary,
                    )
                }
                InputBar(
                    onSend          = { text, attachments ->
                        viewModel.sendMessage(text, attachments)
                        scope.launch {
                            if (messages.isNotEmpty())
                                listState.animateScrollToItem(messages.size - 1)
                        }
                    },
                    initialText     = draft,
                    onTextChanged   = viewModel::onDraftChanged,
                    modifier        = Modifier.fillMaxWidth(),
                )
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                messages.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.ChatBubbleOutline, null,
                            modifier = Modifier.size(56.dp),
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.25f),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Démarrez la conversation",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f),
                        )
                    }
                }
                else -> {
                    val displayMessages = if (searchQ.isNotBlank()) searchRes else messages
                    LazyColumn(
                        state           = listState,
                        modifier        = Modifier.fillMaxSize(),
                        contentPadding  = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        var lastDateLabel = ""
                        items(displayMessages, key = { it.id }) { msg ->
                            val label = formatMsgDate(msg.date)
                            if (label != lastDateLabel) {
                                lastDateLabel = label
                                DateSeparator(date = label)
                            }
                            MessageBubble(
                                message     = msg,
                                modifier    = Modifier.fillMaxWidth(),
                                searchQuery = searchQ,
                                onDelete    = { viewModel.deleteMessage(it) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkBadge(status: NetworkStatus) {
    val (icon, color, label) = when (status) {
        NetworkStatus.WIFI_CALLING_ACTIVE, NetworkStatus.WIFI_CALLING_ONLY ->
            Triple(Icons.Rounded.Wifi, MaterialTheme.colorScheme.primary, status.label)
        NetworkStatus.NO_NETWORK ->
            Triple(Icons.Rounded.WifiOff, MaterialTheme.colorScheme.error, status.label)
        NetworkStatus.WIFI_ONLY ->
            Triple(Icons.Rounded.Wifi, MaterialTheme.colorScheme.error, "WiFi sans SMS")
        else ->
            Triple(Icons.Rounded.SignalCellularAlt, MaterialTheme.colorScheme.onSurfaceVariant, status.label)
    }
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(icon, null, modifier = Modifier.size(10.dp), tint = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun DateSeparator(date: String) {
    Box(
        modifier         = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        ) {
            Text(
                text     = date,
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

private fun formatMsgDate(ts: Long): String {
    val now  = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ts }
    return when {
        now.get(Calendar.DATE)      == then.get(Calendar.DATE)      -> "Aujourd'hui"
        now.get(Calendar.DATE) - 1 == then.get(Calendar.DATE)      -> "Hier"
        else -> SimpleDateFormat("dd MMMM yyyy", Locale.FRENCH).format(Date(ts))
    }
}
