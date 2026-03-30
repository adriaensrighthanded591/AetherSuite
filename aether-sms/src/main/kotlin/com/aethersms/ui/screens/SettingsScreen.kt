package com.aethersms.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aethersms.data.repository.NetworkStatus
import com.aethersms.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack:    () -> Unit,
    vm: SettingsViewModel = viewModel(),
) {
    val settings      by vm.settings.collectAsStateWithLifecycle()
    val sims          by vm.sims.collectAsStateWithLifecycle()
    val networkStatus by vm.networkStatus.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBackIosNew, null)
                    }
                },
                title = { Text("Paramètres") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {

            // ── APPARENCE ────────────────────────────────────────────────
            SectionTitle(icon = Icons.Rounded.Palette, title = "Apparence")
            SettingsCard {
                SwitchRow(
                    icon    = Icons.Rounded.SettingsBrightness,
                    label   = "Suivre le thème système",
                    checked = settings.useSystemTheme,
                    onToggle = { vm.setUseSystemTheme(it) },
                )
                if (!settings.useSystemTheme) {
                    SwitchRow(
                        icon    = Icons.Rounded.DarkMode,
                        label   = "Mode sombre",
                        checked = settings.useDarkTheme,
                        onToggle = { vm.setDarkTheme(it) },
                    )
                }
            }

            // ── VOCAL & CLAVIER ─────────────────────────────────────────
            SectionTitle(icon = Icons.Rounded.Mic, title = "Vocal & clavier")
            SettingsCard {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Language, null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Langue de reconnaissance vocale", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (settings.voiceLanguage == "fr-FR") "Français" else "Anglais",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(Modifier.selectableGroup()) {
                        FilterChip(
                            selected = settings.voiceLanguage == "fr-FR",
                            onClick  = { vm.setVoiceLanguage("fr-FR") },
                            label    = { Text("FR") },
                            modifier = Modifier.padding(end = 4.dp),
                        )
                        FilterChip(
                            selected = settings.voiceLanguage == "en-US",
                            onClick  = { vm.setVoiceLanguage("en-US") },
                            label    = { Text("EN") },
                        )
                    }
                }
            }

            // ── RÉSEAU & WIFI CALLING ───────────────────────────────────
            SectionTitle(icon = Icons.Rounded.Wifi, title = "Réseau & WiFi SMS")
            SettingsCard {
                // Statut réseau actuel
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val (icon, color) = when (networkStatus) {
                        NetworkStatus.WIFI_CALLING_ACTIVE, NetworkStatus.WIFI_CALLING_ONLY ->
                            Icons.Rounded.Wifi to MaterialTheme.colorScheme.primary
                        NetworkStatus.NO_NETWORK, NetworkStatus.WIFI_ONLY ->
                            Icons.Rounded.WifiOff to MaterialTheme.colorScheme.error
                        else -> Icons.Rounded.SignalCellularAlt to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
                    Column {
                        Text("Statut réseau", style = MaterialTheme.typography.bodyLarge)
                        Text(networkStatus.label, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                // SIMs disponibles
                if (sims.isNotEmpty()) {
                    sims.forEach { sim ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.SimCard, null,
                                    tint = if (sim.isDefault) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                                Column {
                                    Text("${sim.displayName} — ${sim.carrierName}",
                                        style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        if (sim.wifiCallingEnabled) "✓ WiFi Calling activé"
                                        else "WiFi Calling non détecté",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (sim.wifiCallingEnabled) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (sim.isDefault) Badge { Text("Par défaut") }
                        }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }

                // Info WiFi Calling
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    shape    = RoundedCornerShape(10.dp),
                    color    = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Comment activer le WiFi Calling ?",
                            style = MaterialTheme.typography.titleMedium)
                        Text(
                            "• Free Mobile : Paramètres téléphone → Réseau → Appels WiFi\n" +
                            "• SFR / Bouygues : Paramètres → Connexions → Appels WiFi\n" +
                            "• Orange : Paramètres → Réseau mobile → Appels WiFi\n\n" +
                            "Une fois activé sur l'opérateur ET le téléphone, vos SMS/MMS passeront automatiquement par WiFi quand le réseau cell est faible.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Passerelle e-mail → SMS (fallback)
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                var showGateway by remember { mutableStateOf(false) }
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Passerelle SMS par e-mail (fallback)", style = MaterialTheme.typography.bodyLarge)
                        Text("Pour envoyer via WiFi sans WiFi Calling opérateur",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = showGateway, onCheckedChange = { showGateway = it })
                }
                AnimatedVisibility(visible = showGateway) {
                    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = settings.smsGatewayEmail,
                            onValueChange = { vm.setSmsGatewayEmail(it) },
                            label = { Text("Votre e-mail d'envoi") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                        )
                        OutlinedTextField(
                            value = settings.smsGateway,
                            onValueChange = { vm.setSmsGateway(it) },
                            label = { Text("Passerelle (ex: free.smsfree.fr)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                        )
                        Text(
                            "Exemples : free.smsfree.fr · sfr.fr · mms.bouyguestelecom.fr",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── NOTIFICATIONS ────────────────────────────────────────────
            SectionTitle(icon = Icons.Rounded.Notifications, title = "Notifications")
            SettingsCard {
                SwitchRow(
                    icon    = Icons.Rounded.VolumeUp,
                    label   = "Son",
                    checked = settings.notificationSound,
                    onToggle = { vm.setNotifSound(it) },
                )
                SwitchRow(
                    icon    = Icons.Rounded.Vibration,
                    label   = "Vibration",
                    checked = settings.notificationVibrate,
                    onToggle = { vm.setNotifVibrate(it) },
                )
            }

            // ── CONFIDENTIALITÉ ─────────────────────────────────────────
            SectionTitle(icon = Icons.Rounded.Security, title = "Confidentialité & sécurité")
            SettingsCard {
                SwitchRow(
                    icon    = Icons.Rounded.Fingerprint,
                    label   = "Verrouillage biométrique",
                    checked = settings.lockApp,
                    onToggle = { vm.setLockApp(it) },
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                // Suppression auto
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Supprimer les anciens messages", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (settings.autoDeleteDays == 0) "Désactivé"
                                else "Après ${settings.autoDeleteDays} jours",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(Modifier.selectableGroup()) {
                        listOf(0, 30, 90, 365).forEach { days ->
                            FilterChip(
                                selected = settings.autoDeleteDays == days,
                                onClick  = { vm.setAutoDeleteDays(days) },
                                label    = { Text(if (days == 0) "Non" else "${days}j") },
                                modifier = Modifier.padding(horizontal = 2.dp),
                            )
                        }
                    }
                }
            }

            // ── INFOS ────────────────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            Text(
                "AetherSMS v1.0.0 — open source, sans pub, sans télémétrie",
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Composants utilitaires ────────────────────────────────────────────────────

@Composable
private fun SectionTitle(icon: ImageVector, title: String) {
    Row(
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        color    = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        content  = { Column(content = content) }
    )
}

@Composable
private fun SwitchRow(
    icon:     ImageVector,
    label:    String,
    checked:  Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}
