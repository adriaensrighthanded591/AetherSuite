package com.aether.core.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.aether.core.ui.theme.AetherColors

// ── AetherTopBar ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AetherTopBar(
    title:          String,
    onBack:         (() -> Unit)? = null,
    actions:        @Composable RowScope.() -> Unit = {},
    searchable:     Boolean = false,
    searchQuery:    String = "",
    onSearchChange: (String) -> Unit = {},
    onSearchToggle: (() -> Unit)? = null,
) {
    var searchActive by remember { mutableStateOf(false) }

    TopAppBar(
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = "Retour")
                }
            }
        },
        title = {
            AnimatedContent(targetState = searchActive, label = "search_anim") { searching ->
                if (searching && searchable) {
                    OutlinedTextField(
                        value         = searchQuery,
                        onValueChange = onSearchChange,
                        placeholder   = { Text("Rechercher…") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth().padding(end = 8.dp),
                        shape         = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    )
                } else {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                }
            }
        },
        actions = {
            if (searchable) {
                IconButton(onClick = {
                    searchActive = !searchActive
                    if (!searchActive) onSearchChange("")
                    onSearchToggle?.invoke()
                }) {
                    Icon(
                        if (searchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                        contentDescription = "Rechercher",
                    )
                }
            }
            actions()
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

// ── AetherFab ────────────────────────────────────────────────────────────────

@Composable
fun AetherFab(
    onClick: () -> Unit,
    icon:    @Composable () -> Unit,
    label:   String? = null,
) {
    if (label != null) {
        ExtendedFloatingActionButton(
            onClick        = onClick,
            icon           = icon,
            text           = { Text(label) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor   = MaterialTheme.colorScheme.onPrimary,
            shape          = RoundedCornerShape(16.dp),
        )
    } else {
        FloatingActionButton(
            onClick        = onClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor   = MaterialTheme.colorScheme.onPrimary,
            shape          = RoundedCornerShape(16.dp),
        ) { icon() }
    }
}

// ── AetherAvatar ─────────────────────────────────────────────────────────────

@Composable
fun AetherAvatar(
    name:        String,
    photoUri:    String?   = null,
    size:        androidx.compose.ui.unit.Dp = 48.dp,
    accentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primaryContainer,
) {
    Box(
        modifier         = Modifier.size(size).clip(CircleShape).background(accentColor),
        contentAlignment = Alignment.Center,
    ) {
        if (!photoUri.isNullOrBlank()) {
            coil.compose.AsyncImage(
                model              = photoUri,
                contentDescription = name,
                modifier           = Modifier.fillMaxSize(),
                contentScale       = androidx.compose.ui.layout.ContentScale.Crop,
            )
        } else {
            val initials = name.split(" ", ",", "-").filter { it.isNotBlank() }.take(2)
                .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
            Text(
                text       = initials,
                style      = MaterialTheme.typography.bodyLarge,
                color      = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            )
        }
    }
}

// ── AetherEmptyState ─────────────────────────────────────────────────────────

@Composable
fun AetherEmptyState(
    icon:    androidx.compose.ui.graphics.vector.ImageVector,
    title:   String,
    message: String,
    action:  Pair<String, () -> Unit>? = null,
) {
    Column(
        modifier            = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon, null,
            modifier = Modifier.size(80.dp),
            tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
        )
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = action.second) { Text(action.first) }
        }
    }
}

// ── AetherAppChip (navigation inter-apps) ────────────────────────────────────

@Composable
fun AetherAppChip(
    label:   String,
    icon:    androidx.compose.ui.graphics.vector.ImageVector,
    color:   androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label   = { Text(label, style = MaterialTheme.typography.labelSmall) },
        leadingIcon = {
            Icon(icon, null, modifier = Modifier.size(14.dp), tint = color)
        },
        shape = RoundedCornerShape(8.dp),
        border = AssistChipDefaults.assistChipBorder(
            enabled = true,
            borderColor = color.copy(alpha = 0.3f),
        ),
    )
}
