package com.aethersms.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.aethersms.service.NotificationService
import kotlinx.coroutines.*

/**
 * Récepteur MMS entrants.
 *
 * Pattern recommandé Android pour les BroadcastReceiver asynchrones :
 *  - goAsync() → obtient un PendingResult qui empêche Android de recycler le receiver
 *  - CoroutineScope supervisé avec SupervisorJob → si le job échoue, pendingResult.finish()
 *    est toujours appelé (try/finally)
 *  - withTimeoutOrNull(10s) → évite la fuite si le ContentProvider répond très lentement
 */
class MmsReceiver : BroadcastReceiver() {

    // Scope avec SupervisorJob : une exception ne tue pas les autres coroutines du receiver
    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return

        val pendingResult = goAsync()

        receiverScope.launch {
            try {
                withTimeoutOrNull(10_000L) {
                    val (sender, snippet) = readLatestMmsSender(context)
                    NotificationService.showMessageNotification(context, sender, snippet)
                }
            } finally {
                // Toujours libérer le pendingResult, même en cas d'erreur ou de timeout
                pendingResult.finish()
            }
        }
    }

    private suspend fun readLatestMmsSender(context: Context): Pair<String, String> {
        var sender  = "MMS"
        var snippet = "Nouveau message MMS reçu"
        runCatching {
            context.contentResolver.query(
                Telephony.Mms.CONTENT_URI, null,
                "${Telephony.Mms.MESSAGE_BOX}=${Telephony.Mms.MESSAGE_BOX_INBOX}",
                null, "${Telephony.Mms.DATE} DESC",
            )?.use { c ->
                if (!c.moveToFirst()) return@use
                val mmsId = c.getLong(c.getColumnIndexOrThrow(Telephony.Mms._ID))

                // Expéditeur (type 137 = FROM dans la spec OMA MMS)
                context.contentResolver.query(
                    android.net.Uri.parse("content://mms/$mmsId/addr"),
                    arrayOf("address", "type"), "msg_id=?",
                    arrayOf(mmsId.toString()), null,
                )?.use { a ->
                    while (a.moveToNext()) {
                        if (a.getInt(a.getColumnIndexOrThrow("type")) == 137) {
                            val addr = a.getString(a.getColumnIndexOrThrow("address")) ?: ""
                            if (addr.isNotBlank() && addr != "insert-address-token") sender = addr
                        }
                    }
                }

                // Texte (première partie text/plain)
                context.contentResolver.query(
                    android.net.Uri.parse("content://mms/$mmsId/part"),
                    null, "ct=?", arrayOf("text/plain"), null,
                )?.use { p ->
                    if (p.moveToFirst()) {
                        val partId = p.getLong(p.getColumnIndexOrThrow("_id"))
                        context.contentResolver
                            .openInputStream(android.net.Uri.parse("content://mms/part/$partId"))
                            ?.use { stream ->
                                snippet = stream.bufferedReader().readText().trim().take(120)
                            }
                    }
                }
            }
        }
        return sender to snippet
    }
}
