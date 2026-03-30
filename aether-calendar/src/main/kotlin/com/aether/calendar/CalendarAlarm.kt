package com.aether.calendar

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Gestion des rappels d'événements via AlarmManager.
 *
 * Schéma de fonctionnement :
 *  1. Quand l'utilisateur sauvegarde un événement avec reminderMin > 0 :
 *     → CalendarAlarmScheduler.schedule() est appelé
 *     → Un AlarmManager exact est planifié à (startMs - reminderMin * 60_000)
 *
 *  2. Au moment du rappel, AlarmReceiver.onReceive() est déclenché
 *     → Affiche une notification Aether cohérente
 *
 *  3. Quand l'événement est supprimé :
 *     → CalendarAlarmScheduler.cancel() annule l'alarme
 *
 *  Permissions nécessaires (manifest) :
 *    - SCHEDULE_EXACT_ALARM (API 31+)
 *    - RECEIVE_BOOT_COMPLETED (pour replanifier après redémarrage)
 *    - POST_NOTIFICATIONS (API 33+)
 */
object CalendarAlarmScheduler {

    private const val CHANNEL_ID = "aether_calendar_reminders"

    fun schedule(context: Context, event: CalEvent) {
        if (event.reminderMin <= 0) return
        val triggerMs = event.startMs - event.reminderMin * 60_000L
        if (triggerMs <= System.currentTimeMillis()) return   // Événement passé → ne pas planifier

        val intent = buildReceiverIntent(context, event)
        val am     = context.getSystemService(AlarmManager::class.java)

        // API 31+ : vérifier la permission SCHEDULE_EXACT_ALARM
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            // Fallback : alarme inexacte (Android peut décaler de quelques minutes)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, intent)
        } else {
            // Alarme exacte (sonnera précisément au bon moment)
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, intent)
        }
    }

    fun cancel(context: Context, eventId: Long) {
        val intent = PendingIntent.getBroadcast(
            context,
            eventId.toInt(),
            Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(intent)
        intent.cancel()
    }

    private fun buildReceiverIntent(context: Context, event: CalEvent): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("event_id",    event.id)
            putExtra("event_title", event.title)
            putExtra("event_start", event.startLabel)
            putExtra("event_loc",   event.location)
        }
        return PendingIntent.getBroadcast(
            context,
            event.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Rappels d'agenda",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Rappels des événements AetherCalendar"
                enableVibration(true)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }
}

// ── Récepteur de rappel ──────────────────────────────────────────────────────

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventId    = intent.getLongExtra("event_id", -1)
        val title      = intent.getStringExtra("event_title") ?: "Événement"
        val startLabel = intent.getStringExtra("event_start") ?: ""
        val location   = intent.getStringExtra("event_loc") ?: ""

        CalendarAlarmScheduler.createNotificationChannel(context)

        // Intent pour ouvrir l'app au tap sur la notification
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, eventId.toInt(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val body = buildString {
            if (startLabel.isNotBlank()) append(startLabel)
            if (location.isNotBlank()) append("  ·  📍 $location")
        }

        val notification = androidx.core.app.NotificationCompat.Builder(context, "aether_calendar_reminders")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("📅  $title")
            .setContentText(body.ifBlank { "Rappel d'événement" })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(eventId.toInt(), notification)
    }
}

// ── Récepteur BOOT_COMPLETED (replanification après redémarrage) ─────────────

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // Replanifier tous les événements futurs depuis la base Room
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val dao = CalendarDatabase.get(context).eventDao()
            dao.observeAll()
                .kotlinx.coroutines.flow.first()
                .filter { it.reminderMin > 0 && it.startMs > System.currentTimeMillis() }
                .forEach { entity -> CalendarAlarmScheduler.schedule(context, entity.toEvent()) }
        }
    }
}
