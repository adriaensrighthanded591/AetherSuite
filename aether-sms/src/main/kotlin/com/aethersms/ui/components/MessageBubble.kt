package com.aethersms.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aethersms.data.model.Attachment
import com.aethersms.data.model.Message
import com.aethersms.data.model.MessageType
import com.aethersms.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Bulle de message avec :
 *  - Long-press → menu contextuel (copier, supprimer, transférer, infos)
 *  - Mise en surbrillance du texte recherché
 *  - Pièces jointes : image HD, PDF, fichiers génériques
 *  - Statut de lecture (coches)
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message:     Message,
    modifier:    Modifier = Modifier,
    searchQuery: String   = "",
    onDelete:    ((Message) -> Unit)? = null,
) {
    val context = LocalContext.current
    val isSent  = message.type == MessageType.SENT || message.type == MessageType.OUTBOX

    val bubbleBg  = if (isSent) bubbleSentColor()  else bubbleRecvColor()
    val textColor = if (isSent) bubbleSentText()   else bubbleRecvText()
    val alignment = if (isSent) Alignment.End      else Alignment.Start
    val shape = if (isSent) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp,   bottomStart = 18.dp, bottomEnd = 18.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp,  topEnd = 18.dp,  bottomStart = 18.dp, bottomEnd = 18.dp)
    }

    // Menu contextuel long-press
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier            = modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(bubbleBg)
                .combinedClickable(
                    onClick     = {},
                    onLongClick = { showMenu = true },
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // Pièces jointes
            message.attachments.forEach { att ->
                AttachmentView(attachment = att)
                Spacer(Modifier.height(4.dp))
            }

            // Corps texte avec surbrillance de recherche
            if (message.body.isNotBlank()) {
                if (searchQuery.isNotBlank() &&
                    message.body.contains(searchQuery, ignoreCase = true)) {
                    HighlightedText(
                        text        = message.body,
                        query       = searchQuery,
                        textColor   = textColor,
                        highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    )
                } else {
                    Text(
                        text  = message.body,
                        color = textColor,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // Timestamp + statut
            Row(
                modifier              = Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text      = formatTime(message.date),
                    style     = MaterialTheme.typography.labelSmall,
                    color     = textColor.copy(alpha = 0.65f),
                    textAlign = TextAlign.End,
                )
                if (isSent) {
                    Icon(
                        imageVector = when {
                            message.status.name == "FAILED" -> Icons.Rounded.Error
                            message.read  -> Icons.Rounded.DoneAll
                            else          -> Icons.Rounded.Done
                        },
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint     = if (message.read) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                   else textColor.copy(alpha = 0.6f),
                    )
                }
            }
        }

        // Menu contextuel
        DropdownMenu(
            expanded         = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            // Copier le texte
            if (message.body.isNotBlank()) {
                DropdownMenuItem(
                    text         = { Text("Copier le texte") },
                    leadingIcon  = { Icon(Icons.Rounded.ContentCopy, null) },
                    onClick      = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(
                            ClipData.newPlainText("message", message.body)
                        )
                        showMenu = false
                    },
                )
            }
            // Transférer
            DropdownMenuItem(
                text        = { Text("Transférer") },
                leadingIcon = { Icon(Icons.Rounded.Forward, null) },
                onClick     = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("smsto:")
                        putExtra("sms_body", message.body)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    runCatching { context.startActivity(intent) }
                    showMenu = false
                },
            )
            // Supprimer
            if (onDelete != null) {
                HorizontalDivider()
                DropdownMenuItem(
                    text        = { Text("Supprimer", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error)
                    },
                    onClick     = {
                        onDelete(message)
                        showMenu = false
                    },
                )
            }
        }
    }
}

// ── Texte avec surbrillance ──────────────────────────────────────────────────

@Composable
private fun HighlightedText(
    text:           String,
    query:          String,
    textColor:      androidx.compose.ui.graphics.Color,
    highlightColor: androidx.compose.ui.graphics.Color,
) {
    val annotated = buildAnnotatedString {
        val lowerText  = text.lowercase()
        val lowerQuery = query.lowercase()
        var cursor = 0
        while (cursor < text.length) {
            val idx = lowerText.indexOf(lowerQuery, cursor)
            if (idx == -1) {
                withStyle(SpanStyle(color = textColor)) { append(text.substring(cursor)) }
                break
            }
            if (idx > cursor) {
                withStyle(SpanStyle(color = textColor)) {
                    append(text.substring(cursor, idx))
                }
            }
            withStyle(SpanStyle(
                color      = textColor,
                fontWeight = FontWeight.Bold,
                background = highlightColor,
            )) {
                append(text.substring(idx, idx + query.length))
            }
            cursor = idx + query.length
        }
    }
    Text(
        text  = annotated,
        style = MaterialTheme.typography.bodySmall,
    )
}

// ── Pièces jointes ───────────────────────────────────────────────────────────

@Composable
private fun AttachmentView(attachment: Attachment) {
    val context = LocalContext.current
    when {
        attachment.isImage -> AsyncImage(
            model = ImageRequest.Builder(context)
                .data(Uri.parse(attachment.uriString))
                .crossfade(true)
                .build(),
            contentDescription = attachment.name.ifBlank { "Image" },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp)
                .clip(RoundedCornerShape(10.dp))
                .combinedClickable(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse(attachment.uriString), "image/*")
                            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        }
                        runCatching { context.startActivity(intent) }
                    },
                    onLongClick = {},
                ),
            contentScale = ContentScale.Fit,
        )
        attachment.isPdf -> PdfAttachmentView(attachment)
        else             -> GenericFileView(attachment)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PdfAttachmentView(attachment: Attachment) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .combinedClickable(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(attachment.uriString), "application/pdf")
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    runCatching { context.startActivity(intent) }
                },
                onLongClick = {},
            )
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.PictureAsPdf, null, tint = AetherError, modifier = Modifier.size(32.dp))
        Column(Modifier.weight(1f)) {
            Text(attachment.name.ifBlank { "document.pdf" },
                style = MaterialTheme.typography.bodySmall, maxLines = 1)
            if (attachment.size > 0) {
                Text(formatSize(attachment.size), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.Rounded.OpenInNew, null, modifier = Modifier.size(16.dp))
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun GenericFileView(attachment: Attachment) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f))
            .combinedClickable(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(attachment.uriString), attachment.contentType)
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    runCatching { context.startActivity(intent) }
                },
                onLongClick = {},
            )
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.InsertDriveFile, null, modifier = Modifier.size(32.dp))
        Text(attachment.name.ifBlank { "fichier" },
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.OpenInNew, null, modifier = Modifier.size(16.dp))
    }
}

private fun formatTime(ts: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f Mo".format(bytes / 1_000_000f)
    bytes >= 1_000     -> "${bytes / 1000} Ko"
    else               -> "$bytes o"
}
