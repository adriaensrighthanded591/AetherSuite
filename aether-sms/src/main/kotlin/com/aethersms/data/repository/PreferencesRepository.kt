package com.aethersms.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aether_prefs")

class PreferencesRepository(private val context: Context) {

    companion object {
        val KEY_DARK_THEME         = booleanPreferencesKey("dark_theme")
        val KEY_USE_SYSTEM_THEME   = booleanPreferencesKey("use_system_theme")    // true par défaut
        val KEY_VOICE_LANGUAGE     = stringPreferencesKey("voice_language")       // "fr-FR" | "en-US"
        val KEY_FONT_SIZE          = intPreferencesKey("font_size")               // 14..20
        val KEY_DEFAULT_SIM        = intPreferencesKey("default_sim_sub_id")      // -1 = auto
        val KEY_SMS_GATEWAY        = stringPreferencesKey("sms_gateway")          // passerelle WiFi fallback
        val KEY_SMS_GATEWAY_EMAIL  = stringPreferencesKey("sms_gateway_email")
        val KEY_LOCK_APP           = booleanPreferencesKey("lock_app")            // biométrie
        val KEY_BLOCKED_NUMBERS    = stringPreferencesKey("blocked_numbers")      // CSV
        val KEY_NOTIFICATION_SOUND = booleanPreferencesKey("notification_sound")
        val KEY_NOTIFICATION_VIB   = booleanPreferencesKey("notification_vibrate")
        val KEY_AUTO_DELETE_DAYS   = intPreferencesKey("auto_delete_days")        // 0 = jamais
    }

    // ── Lecture ───────────────────────────────────────────────────────────

    val appSettings: Flow<AppSettings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            AppSettings(
                useDarkTheme        = prefs[KEY_DARK_THEME]         ?: false,
                useSystemTheme      = prefs[KEY_USE_SYSTEM_THEME]   ?: true,
                voiceLanguage       = prefs[KEY_VOICE_LANGUAGE]     ?: "fr-FR",
                fontSize            = prefs[KEY_FONT_SIZE]          ?: 14,
                defaultSimSubId     = prefs[KEY_DEFAULT_SIM]        ?: -1,
                smsGateway          = prefs[KEY_SMS_GATEWAY]        ?: "",
                smsGatewayEmail     = prefs[KEY_SMS_GATEWAY_EMAIL]  ?: "",
                lockApp             = prefs[KEY_LOCK_APP]           ?: false,
                blockedNumbers      = prefs[KEY_BLOCKED_NUMBERS]
                    ?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                notificationSound   = prefs[KEY_NOTIFICATION_SOUND]?: true,
                notificationVibrate = prefs[KEY_NOTIFICATION_VIB]  ?: true,
                autoDeleteDays      = prefs[KEY_AUTO_DELETE_DAYS]   ?: 0,
            )
        }

    // ── Écriture ──────────────────────────────────────────────────────────

    suspend fun setDarkTheme(dark: Boolean) = context.dataStore.edit {
        it[KEY_DARK_THEME] = dark
        it[KEY_USE_SYSTEM_THEME] = false
    }
    suspend fun setUseSystemTheme(use: Boolean) = context.dataStore.edit {
        it[KEY_USE_SYSTEM_THEME] = use
    }
    suspend fun setVoiceLanguage(lang: String)   = context.dataStore.edit { it[KEY_VOICE_LANGUAGE] = lang }
    suspend fun setFontSize(size: Int)            = context.dataStore.edit { it[KEY_FONT_SIZE] = size.coerceIn(12, 22) }
    suspend fun setDefaultSim(subId: Int)         = context.dataStore.edit { it[KEY_DEFAULT_SIM] = subId }
    suspend fun setSmsGateway(gw: String)         = context.dataStore.edit { it[KEY_SMS_GATEWAY] = gw }
    suspend fun setSmsGatewayEmail(email: String) = context.dataStore.edit { it[KEY_SMS_GATEWAY_EMAIL] = email }
    suspend fun setLockApp(lock: Boolean)         = context.dataStore.edit { it[KEY_LOCK_APP] = lock }
    suspend fun setNotifSound(v: Boolean)         = context.dataStore.edit { it[KEY_NOTIFICATION_SOUND] = v }
    suspend fun setNotifVibrate(v: Boolean)       = context.dataStore.edit { it[KEY_NOTIFICATION_VIB] = v }
    suspend fun setAutoDeleteDays(days: Int)      = context.dataStore.edit { it[KEY_AUTO_DELETE_DAYS] = days }

    suspend fun blockNumber(number: String) = context.dataStore.edit { prefs ->
        val current = prefs[KEY_BLOCKED_NUMBERS]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        prefs[KEY_BLOCKED_NUMBERS] = (current + number).distinct().joinToString(",")
    }
    suspend fun unblockNumber(number: String) = context.dataStore.edit { prefs ->
        val current = prefs[KEY_BLOCKED_NUMBERS]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        prefs[KEY_BLOCKED_NUMBERS] = current.filter { it != number }.joinToString(",")
    }
}

data class AppSettings(
    val useDarkTheme:        Boolean     = false,
    val useSystemTheme:      Boolean     = true,
    val voiceLanguage:       String      = "fr-FR",
    val fontSize:            Int         = 14,
    val defaultSimSubId:     Int         = -1,
    val smsGateway:          String      = "",
    val smsGatewayEmail:     String      = "",
    val lockApp:             Boolean     = false,
    val blockedNumbers:      List<String>= emptyList(),
    val notificationSound:   Boolean     = true,
    val notificationVibrate: Boolean     = true,
    val autoDeleteDays:      Int         = 0,
)
