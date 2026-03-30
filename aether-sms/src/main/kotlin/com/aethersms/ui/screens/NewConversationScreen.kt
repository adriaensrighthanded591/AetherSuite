package com.aethersms.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aethersms.viewmodel.ContactSearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewConversationScreen(
    viewModel:  ContactSearchViewModel,
    onNavigate: (address: String) -> Unit,
    onBack:     () -> Unit,
) {
    val results by viewModel.results.collectAsStateWithLifecycle()
    var query   by remember { mutableStateOf("") }

    // Si la saisie ressemble à un numéro de téléphone → afficher le bouton direct
    val isPhoneNumber = remember(query) {
        query.replace(Regex("[\\s\\-+]"), "").all { it.isDigit() } && query.length >= 4
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = "Retour")
                    }
                },
                title = { Text("Nouveau message") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Champ de recherche
            OutlinedTextField(
                value         = query,
                onValueChange = { q ->
                    query = q
                    viewModel.search(q)
                },
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder   = { Text("Nom ou numéro de téléphone") },
                leadingIcon   = { Icon(Icons.Rounded.Search, null) },
                trailingIcon  = if (query.isNotBlank()) {{
                    IconButton(onClick = { query = ""; viewModel.search("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Effacer")
                    }
                }} else null,
                singleLine    = true,
                shape         = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction    = ImeAction.Search,
                ),
            )

            // Bouton "Envoyer directement à ce numéro"
            AnimatedVisibility(visible = isPhoneNumber) {
                Surface(
                    modifier  = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { onNavigate(query.replace(Regex("\\s"), "")) },
                    shape     = RoundedCornerShape(12.dp),
                    color     = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(
                        modifier  = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Rounded.SendToMobile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Column {
                            Text(
                                "Envoyer à $query",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                "Numéro inconnu — appuyer pour continuer",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }

            // Résultats contacts
            if (results.isNotEmpty()) {
                Text(
                    "Contacts",
                    style    = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(
                contentPadding      = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                items(results, key = { it.second }) { (name, number) ->
                    ContactRow(
                        name   = name,
                        number = number,
                        onClick = { onNavigate(number) },
                    )
                }
            }

            // État vide (pas de résultats, pas de numéro)
            if (results.isEmpty() && query.isNotBlank() && !isPhoneNumber) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.PersonSearch,
                            null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Aucun contact trouvé",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(name: String, number: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Mini avatar initiales
        Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(40.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val initial = name.firstOrNull()?.uppercase() ?: "#"
                Text(
                    initial,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(number, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
    }
    HorizontalDivider(
        modifier  = Modifier.padding(start = 70.dp),
        thickness = 0.5.dp,
        color     = MaterialTheme.colorScheme.surfaceVariant,
    )
}
