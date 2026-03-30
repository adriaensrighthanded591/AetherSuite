package com.aethersms.data.model

// ─── Message ────────────────────────────────────────────────────────────────

data class Message(
    val id: Long = 0L,
    val threadId: Long = 0L,
    val address: String = "",       // numéro expéditeur/destinataire
    val body: String = "",
    val date: Long = 0L,            // timestamp ms
    val dateSent: Long = 0L,
    val type: MessageType = MessageType.INBOX,
    val status: MessageStatus = MessageStatus.NONE,
    val seen: Boolean = true,
    val read: Boolean = true,
    val subId: Int = -1,            // SIM utilisée (-1 = par défaut)
    val attachments: List<Attachment> = emptyList(),
    val isMms: Boolean = false,
)

enum class MessageType { INBOX, SENT, DRAFT, OUTBOX, FAILED, QUEUED }
enum class MessageStatus { NONE, COMPLETE, PENDING, FAILED }

// ─── Attachment ─────────────────────────────────────────────────────────────

data class Attachment(
    val partId: Long = 0L,
    val contentType: String = "",   // "image/jpeg", "application/pdf", etc.
    val name: String = "",
    val size: Long = 0L,
    val uriString: String = "",     // content URI en chaîne
) {
    val isImage: Boolean get() = contentType.startsWith("image/")
    val isVideo: Boolean get() = contentType.startsWith("video/")
    val isPdf: Boolean   get() = contentType == "application/pdf"
    val isAudio: Boolean get() = contentType.startsWith("audio/")
}

// ─── Conversation ────────────────────────────────────────────────────────────

data class Conversation(
    val threadId: Long = 0L,
    val recipientAddresses: List<String> = emptyList(),
    val contactNames: List<String> = emptyList(),
    val snippet: String = "",       // dernier message (extrait)
    val date: Long = 0L,
    val messageCount: Int = 0,
    val unreadCount: Int = 0,
    val isGroup: Boolean = false,
    val isBlocked: Boolean = false,
    val isArchived: Boolean = false,
    val photoUri: String? = null,   // photo du contact (1er destinataire)
) {
    /** Nom à afficher : contact ou numéro brut */
    val displayName: String get() = when {
        contactNames.isNotEmpty() -> contactNames.joinToString(", ")
        recipientAddresses.isNotEmpty() -> recipientAddresses.joinToString(", ")
        else -> "Inconnu"
    }
    val initials: String get() = displayName
        .split(" ", ",")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }
}
