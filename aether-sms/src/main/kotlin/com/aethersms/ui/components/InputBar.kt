package com.aethersms.ui.components

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Barre de saisie complète :
 *  - TextField avec correcteur orthographique (FR/EN géré par le clavier système)
 *  - Bouton microphone → reconnaissance vocale Android (aucune permission supplémentaire)
 *  - Bouton presse-papiers → colle le dernier contenu copié
 *  - Bouton pièce jointe → sélection fichier (image, PDF, vidéo, audio…)
 *  - Bouton envoi (actif si texte non vide ou pièce jointe sélectionnée)
 */
@Composable
fun InputBar(
    onSend:        (text: String, attachments: List<Uri>) -> Unit,
    modifier:      Modifier  = Modifier,
    initialText:   String    = "",               // brouillon pré-rempli
    onTextChanged: ((String) -> Unit)? = null,   // callback auto-save brouillon
    voiceLanguage: String    = "fr-FR",          // "fr-FR" ou "en-US"
) {
    val context = LocalContext.current
    var text        by remember(initialText) { mutableStateOf(initialText) }
    var attachments by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showAttachMenu by remember { mutableStateOf(false) }

    val canSend = text.isNotBlank() || attachments.isNotEmpty()

    // ── Reconnaissance vocale ──────────────────────────────────────────────
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val words = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull() ?: return@rememberLauncherForActivityResult
            text = if (text.isBlank()) words
                   else "${text.trimEnd()} $words"
        }
    }
    fun launchSpeech() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, voiceLanguage)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT,
                if (voiceLanguage.startsWith("fr")) "Parlez maintenant…"
                else "Speak now…"
            )
        }
        runCatching { speechLauncher.launch(intent) }
    }

    // ── Sélecteur de fichiers ──────────────────────────────────────────────
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) attachments = (attachments + uris).distinctBy { it.toString() }
    }

    Column(modifier.background(MaterialTheme.colorScheme.surface)) {

        // Prévisualisation des pièces jointes sélectionnées
        if (attachments.isNotEmpty()) {
            AttachmentPreviewRow(
                uris     = attachments,
                onRemove = { uri -> attachments = attachments.filter { it != uri } },
            )
        }

        // Menu pièce jointe flottant
        if (showAttachMenu) {
            AttachMenu(
                onPickImage = { filePicker.launch("image/*"); showAttachMenu = false },
                onPickPdf   = { filePicker.launch("application/pdf"); showAttachMenu = false },
                onPickAny   = { filePicker.launch("*/*"); showAttachMenu = false },
                onDismiss   = { showAttachMenu = false },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {

            // Bouton pièce jointe
            IconButton(onClick = { showAttachMenu = !showAttachMenu }) {
                Icon(
                    Icons.Rounded.AttachFile,
                    contentDescription = "Joindre un fichier",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Zone de saisie
            TextField(
                value = text,
                onValueChange = { v ->
                            text = v
                            onTextChanged?.invoke(v)
                        },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message…") },
                maxLines = 6,
                shape = RoundedCornerShape(22.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor   = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    autoCorrect    = true,   // ← correcteur orthographique actif (FR/EN clavier)
                    keyboardType   = KeyboardType.Text,
                    imeAction      = ImeAction.Default,
                ),
                trailingIcon = {
                    // Bouton presse-papiers intégré au champ
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                            if (!clip.isNullOrBlank()) {
                                text = if (text.isBlank()) clip else "${text.trimEnd()} $clip"
                            }
                        }
                    ) {
                        Icon(
                            Icons.Rounded.ContentPaste,
                            contentDescription = "Coller",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            )

            // Bouton micro OU envoi
            AnimatedContent(
                targetState = canSend,
                label       = "mic_or_send",      // requis Compose 1.7+
                transitionSpec = {
                    (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut())
                },
            ) { ready ->
                if (ready) {
                    // Bouton envoi
                    IconButton(
                        onClick = {
                            onSend(text.trim(), attachments)
                            text        = ""
                            attachments = emptyList()
                            onTextChanged?.invoke("")
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    ) {
                        Icon(
                            Icons.Rounded.Send,
                            contentDescription = "Envoyer",
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                } else {
                    // Bouton micro
                    IconButton(
                        onClick = { launchSpeech() },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Icon(
                            Icons.Rounded.Mic,
                            contentDescription = "Vocal",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ─── Menu pièce jointe ───────────────────────────────────────────────────────

@Composable
private fun AttachMenu(
    onPickImage: () -> Unit,
    onPickPdf:   () -> Unit,
    onPickAny:   () -> Unit,
    onDismiss:   () -> Unit,
) {
    Surface(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        shape    = RoundedCornerShape(16.dp),
        shadowElevation = 4.dp,
        color    = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AttachOption(icon = Icons.Rounded.Image,       label = "Photo",    onClick = onPickImage)
            AttachOption(icon = Icons.Rounded.PictureAsPdf, label = "PDF",    onClick = onPickPdf)
            AttachOption(icon = Icons.Rounded.Folder,      label = "Fichier",  onClick = onPickAny)
        }
    }
}

@Composable
private fun AttachOption(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

// ─── Prévisualisation pièces jointes ─────────────────────────────────────────

@Composable
fun AttachmentPreviewRow(uris: List<Uri>, onRemove: (Uri) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        uris.forEach { uri ->
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                val mime = LocalContext.current.contentResolver.getType(uri) ?: ""
                if (mime.startsWith("image/")) {
                    coil.compose.AsyncImage(
                        model  = uri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                } else {
                    Icon(
                        if (mime == "application/pdf") Icons.Rounded.PictureAsPdf else Icons.Rounded.InsertDriveFile,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.Center).size(32.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                // Bouton supprimer
                IconButton(
                    onClick  = { onRemove(uri) },
                    modifier = Modifier.align(Alignment.TopEnd).size(22.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), CircleShape),
                ) {
                    Icon(Icons.Rounded.Close, null, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}
