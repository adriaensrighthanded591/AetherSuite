package com.aethersms.data.repository

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import com.aethersms.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class SmsRepository(private val context: Context) {

    // ─── CONVERSATIONS ───────────────────────────────────────────────────────

    fun getConversationsFlow(): Flow<List<Conversation>> = flow {
        emit(loadConversations())
    }.flowOn(Dispatchers.IO)

    suspend fun loadConversations(): List<Conversation> = withContext(Dispatchers.IO) {
        val conversations = mutableListOf<Conversation>()
        val uri = Uri.parse("content://mms-sms/conversations?simple=true")
        val projection = arrayOf(
            Telephony.Threads._ID,
            Telephony.Threads.SNIPPET,
            Telephony.Threads.DATE,
            Telephony.Threads.MESSAGE_COUNT,
            Telephony.Threads.READ,
            Telephony.Threads.RECIPIENT_IDS,
            Telephony.Threads.TYPE,
        )
        context.contentResolver.query(uri, projection, null, null, "date DESC")?.use { cursor ->
            while (cursor.moveToNext()) {
                val threadId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Threads._ID))
                val snippet   = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Threads.SNIPPET)) ?: ""
                val date      = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Threads.DATE))
                val msgCount  = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Threads.MESSAGE_COUNT))
                val recipientIds = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Threads.RECIPIENT_IDS)) ?: ""

                val addresses = resolveRecipientAddresses(recipientIds.trim().split(" ").filter { it.isNotBlank() })
                val (names, photoUri) = lookupContacts(addresses)
                val unread = getUnreadCount(threadId)

                conversations.add(
                    Conversation(
                        threadId = threadId,
                        recipientAddresses = addresses,
                        contactNames = names,
                        snippet = snippet,
                        date = date,
                        messageCount = msgCount,
                        unreadCount = unread,
                        isGroup = addresses.size > 1,
                        photoUri = photoUri,
                    )
                )
            }
        }
        conversations
    }

    private fun getUnreadCount(threadId: Long): Int {
        var count = 0
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf("count(*) as cnt"),
            "${Telephony.Sms.THREAD_ID}=? AND ${Telephony.Sms.READ}=0",
            arrayOf(threadId.toString()),
            null
        )?.use { c ->
            if (c.moveToFirst()) count = c.getInt(0)
        }
        return count
    }

    // ─── MESSAGES ────────────────────────────────────────────────────────────

    fun getMessagesFlow(threadId: Long): Flow<List<Message>> = flow {
        emit(loadMessages(threadId))
    }.flowOn(Dispatchers.IO)

    suspend fun loadMessages(threadId: Long): List<Message> = withContext(Dispatchers.IO) {
        val messages = mutableListOf<Message>()
        // SMS
        val smsCursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            null,
            "${Telephony.Sms.THREAD_ID}=?",
            arrayOf(threadId.toString()),
            "${Telephony.Sms.DATE} ASC"
        )
        smsCursor?.use { c ->
            while (c.moveToNext()) {
                messages.add(
                    Message(
                        id       = c.getLong(c.getColumnIndexOrThrow(Telephony.Sms._ID)),
                        threadId = threadId,
                        address  = c.getString(c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: "",
                        body     = c.getString(c.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: "",
                        date     = c.getLong(c.getColumnIndexOrThrow(Telephony.Sms.DATE)),
                        dateSent = c.getLong(c.getColumnIndexOrThrow(Telephony.Sms.DATE_SENT)),
                        type     = when (c.getInt(c.getColumnIndexOrThrow(Telephony.Sms.TYPE))) {
                            Telephony.Sms.MESSAGE_TYPE_INBOX  -> MessageType.INBOX
                            Telephony.Sms.MESSAGE_TYPE_SENT   -> MessageType.SENT
                            Telephony.Sms.MESSAGE_TYPE_DRAFT  -> MessageType.DRAFT
                            Telephony.Sms.MESSAGE_TYPE_OUTBOX -> MessageType.OUTBOX
                            Telephony.Sms.MESSAGE_TYPE_FAILED -> MessageType.FAILED
                            else -> MessageType.INBOX
                        },
                        read     = c.getInt(c.getColumnIndexOrThrow(Telephony.Sms.READ)) == 1,
                        seen     = c.getInt(c.getColumnIndexOrThrow(Telephony.Sms.SEEN)) == 1,
                        isMms    = false,
                    )
                )
            }
        }
        // MMS
        val mmsCursor = context.contentResolver.query(
            Telephony.Mms.CONTENT_URI,
            null,
            "${Telephony.Mms.THREAD_ID}=?",
            arrayOf(threadId.toString()),
            "${Telephony.Mms.DATE} ASC"
        )
        mmsCursor?.use { c ->
            while (c.moveToNext()) {
                val mmsId = c.getLong(c.getColumnIndexOrThrow(Telephony.Mms._ID))
                val parts = loadMmsParts(mmsId)
                val body  = parts.firstOrNull { it.contentType == "text/plain" }?.let {
                    context.contentResolver.openInputStream(Uri.parse(it.uriString))
                        ?.use { s -> s.bufferedReader().readText() } ?: ""
                } ?: ""
                val attachments = parts.filter { it.contentType != "text/plain" && it.contentType != "application/smil" }
                val address = getMmsAddress(mmsId)
                val msgType = c.getInt(c.getColumnIndexOrThrow(Telephony.Mms.MESSAGE_BOX))
                messages.add(
                    Message(
                        id          = mmsId,
                        threadId    = threadId,
                        address     = address,
                        body        = body,
                        date        = c.getLong(c.getColumnIndexOrThrow(Telephony.Mms.DATE)) * 1000L,
                        type        = if (msgType == Telephony.Mms.MESSAGE_BOX_INBOX) MessageType.INBOX else MessageType.SENT,
                        read        = c.getInt(c.getColumnIndexOrThrow(Telephony.Mms.READ)) == 1,
                        isMms       = true,
                        attachments = attachments,
                    )
                )
            }
        }
        messages.sortBy { it.date }
        markThreadAsRead(threadId)
        messages
    }

    private fun loadMmsParts(mmsId: Long): List<Attachment> {
        val parts = mutableListOf<Attachment>()
        val uri = Uri.parse("content://mms/$mmsId/part")
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val partId      = c.getLong(c.getColumnIndexOrThrow("_id"))
                val contentType = c.getString(c.getColumnIndexOrThrow("ct")) ?: ""
                val name        = c.getString(c.getColumnIndexOrThrow("name")) ?: ""
                parts.add(
                    Attachment(
                        partId      = partId,
                        contentType = contentType,
                        name        = name,
                        uriString   = "content://mms/part/$partId",
                    )
                )
            }
        }
        return parts
    }

    private fun getMmsAddress(mmsId: Long): String {
        var address = ""
        context.contentResolver.query(
            Uri.parse("content://mms/$mmsId/addr"),
            arrayOf("address", "type"),
            "msg_id=?",
            arrayOf(mmsId.toString()),
            null
        )?.use { c ->
            while (c.moveToNext()) {
                val type = c.getInt(c.getColumnIndexOrThrow("type"))
                if (type == 137) { // PduHeaders.FROM
                    address = c.getString(c.getColumnIndexOrThrow("address")) ?: ""
                    return@use
                }
            }
        }
        return address
    }

    private fun markThreadAsRead(threadId: Long) {
        context.contentResolver.update(
            Telephony.Sms.CONTENT_URI,
            android.content.ContentValues().apply {
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
            },
            "${Telephony.Sms.THREAD_ID}=?",
            arrayOf(threadId.toString())
        )
    }

    // ─── ENVOI SMS ───────────────────────────────────────────────────────────

    @Suppress("DEPRECATION")
    fun getSmsManager(subId: Int = -1): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (subId == -1) context.getSystemService(SmsManager::class.java)
            else context.getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
        } else {
            if (subId == -1) SmsManager.getDefault()
            else SmsManager.getSmsManagerForSubscriptionId(subId)
        }
    }

    suspend fun sendSms(address: String, body: String, subId: Int = -1) = withContext(Dispatchers.IO) {
        val smsManager = getSmsManager(subId)
        val parts = smsManager.divideMessage(body)
        if (parts.size == 1) {
            smsManager.sendTextMessage(address, null, body, null, null)
        } else {
            smsManager.sendMultipartTextMessage(address, null, parts, null, null)
        }
    }

    // ─── CONTACTS ────────────────────────────────────────────────────────────

    private fun resolveRecipientAddresses(recipientIds: List<String>): List<String> {
        return recipientIds.mapNotNull { id ->
            if (id.isBlank()) return@mapNotNull null
            var address: String? = null
            context.contentResolver.query(
                Uri.parse("content://mms-sms/canonical-addresses"),
                null,
                "_id=?",
                arrayOf(id),
                null
            )?.use { c ->
                if (c.moveToFirst()) address = c.getString(c.getColumnIndexOrThrow("address"))
            }
            address
        }
    }

    private fun lookupContacts(addresses: List<String>): Pair<List<String>, String?> {
        val names = mutableListOf<String>()
        var firstPhotoUri: String? = null
        addresses.forEach { address ->
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(address)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val name = c.getString(c.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
                    if (!name.isNullOrBlank()) names.add(name)
                    if (firstPhotoUri == null) {
                        firstPhotoUri = c.getString(c.getColumnIndexOrThrow(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI))
                    }
                }
            }
        }
        return Pair(names, firstPhotoUri)
    }

    suspend fun searchContacts(query: String): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Pair<String, String>>()
        if (query.isBlank()) return@withContext results
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$query%"),
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )
        cursor?.use { c ->
            while (c.moveToNext()) {
                val name   = c.getString(0) ?: continue
                val number = c.getString(1) ?: continue
                results.add(Pair(name, number))
            }
        }
        results.distinctBy { it.second }
    }

    // ─── SUPPRESSION ─────────────────────────────────────────────────────────

    suspend fun deleteConversation(threadId: Long) = withContext(Dispatchers.IO) {
        context.contentResolver.delete(
            Uri.parse("content://mms-sms/conversations/$threadId"),
            null, null
        )
    }

    suspend fun deleteMessage(message: Message) = withContext(Dispatchers.IO) {
        val uri = if (message.isMms) Telephony.Mms.CONTENT_URI else Telephony.Sms.CONTENT_URI
        context.contentResolver.delete(uri, "_id=?", arrayOf(message.id.toString()))
    }
}

    // ─── BROUILLONS ──────────────────────────────────────────────────────────

    suspend fun saveDraft(threadId: Long, address: String, body: String) = withContext(Dispatchers.IO) {
        if (body.isBlank()) { deleteDraft(threadId); return@withContext }
        // Mettre à jour un brouillon existant ou en créer un nouveau
        val existingId = getDraftId(threadId)
        val values = android.content.ContentValues().apply {
            put(Telephony.Sms.BODY,      body)
            put(Telephony.Sms.TYPE,      Telephony.Sms.MESSAGE_TYPE_DRAFT)
            put(Telephony.Sms.THREAD_ID, threadId)
            put(Telephony.Sms.ADDRESS,   address)
            put(Telephony.Sms.DATE,      System.currentTimeMillis())
        }
        if (existingId > 0) {
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI, values, "_id=?", arrayOf(existingId.toString())
            )
        } else {
            context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
        }
    }

    suspend fun deleteDraft(threadId: Long) = withContext(Dispatchers.IO) {
        context.contentResolver.delete(
            Telephony.Sms.CONTENT_URI,
            "${Telephony.Sms.THREAD_ID}=? AND ${Telephony.Sms.TYPE}=${Telephony.Sms.MESSAGE_TYPE_DRAFT}",
            arrayOf(threadId.toString())
        )
    }

    private fun getDraftId(threadId: Long): Long {
        var id = -1L
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID),
            "${Telephony.Sms.THREAD_ID}=? AND ${Telephony.Sms.TYPE}=${Telephony.Sms.MESSAGE_TYPE_DRAFT}",
            arrayOf(threadId.toString()),
            null
        )?.use { c -> if (c.moveToFirst()) id = c.getLong(0) }
        return id
    }

    suspend fun getDraft(threadId: Long): String = withContext(Dispatchers.IO) {
        var body = ""
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.BODY),
            "${Telephony.Sms.THREAD_ID}=? AND ${Telephony.Sms.TYPE}=${Telephony.Sms.MESSAGE_TYPE_DRAFT}",
            arrayOf(threadId.toString()),
            "${Telephony.Sms.DATE} DESC LIMIT 1"
        )?.use { c -> if (c.moveToFirst()) body = c.getString(0) ?: "" }
        body
    }
