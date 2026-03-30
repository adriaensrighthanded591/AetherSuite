package com.aethersms.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gestionnaire WiFi Calling / WiFi SMS-MMS
 *
 * Android expose la préférence réseau via ImsManager (API interne) ou
 * via TelephonyManager depuis API 29+. On utilise les APIs publiques
 * et on tombe sur les SmsManager standards qui routent automatiquement
 * sur WiFi si l'opérateur et le téléphone ont activé le WiFi Calling.
 *
 * Cas supportés :
 *  1. WiFi Calling activé sur l'opérateur + téléphone  → SMS/MMS passent
 *     automatiquement via WiFi (aucun code supplémentaire nécessaire,
 *     c'est le modem qui arbitre).
 *  2. Pas de signal cell mais WiFi disponible           → idem, le
 *     SmsManager délègue à IMS over WiFi si dispo.
 *  3. L'app signale à l'utilisateur si WiFi Calling est détecté actif.
 *
 * Pour les opérateurs qui NE supportent PAS le WiFi Calling natif,
 * on propose en fallback l'envoi via passerelle e-mail-to-SMS
 * (ex: [numéro]@txt.att.net) si l'utilisateur configure sa passerelle.
 */
class WifiMessagingManager(private val context: Context) {

    data class SimInfo(
        val subId: Int,
        val displayName: String,
        val carrierName: String,
        val isDefault: Boolean,
        val wifiCallingEnabled: Boolean,
        val simSlot: Int,
    )

    /**
     * Retourne la liste des SIMs disponibles avec leur statut WiFi Calling.
     */
    fun getAvailableSims(): List<SimInfo> {
        val subs = mutableListOf<SimInfo>()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return subs

        // READ_PHONE_STATE requis pour lire les SIMs — vérification avant appel
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return subs

        val subManager = context.getSystemService(SubscriptionManager::class.java) ?: return subs
        val defaultSubId = SubscriptionManager.getDefaultSmsSubscriptionId()

        runCatching {
            val activeList: List<SubscriptionInfo> = subManager.activeSubscriptionInfoList ?: return subs
            activeList.forEach { info ->
                val wifiCalling = isWifiCallingEnabled(info.subscriptionId)
                subs.add(
                    SimInfo(
                        subId              = info.subscriptionId,
                        displayName        = info.displayName?.toString() ?: "SIM ${info.simSlotIndex + 1}",
                        carrierName        = info.carrierName?.toString() ?: "",
                        isDefault          = info.subscriptionId == defaultSubId,
                        wifiCallingEnabled = wifiCalling,
                        simSlot            = info.simSlotIndex,
                    )
                )
            }
        }
        return subs
    }

    /**
     * Vérifie si le WiFi Calling est actif pour une SIM donnée.
     * API 34+ : méthode publique isWifiCallingAvailable().
     * API 26–33 : réflexion sur ImsManager (interne, best-effort).
     * Avant API 26 : toujours false.
     */
    fun isWifiCallingEnabled(subId: Int = SubscriptionManager.getDefaultSmsSubscriptionId()): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                isWifiCallingApi34(subId)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                isWifiCallingReflection(subId)
            }
            else -> false
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun isWifiCallingApi34(subId: Int): Boolean {
        return runCatching {
            val tm = context.getSystemService(TelephonyManager::class.java)
            val sub = tm.createForSubscriptionId(subId)
            sub.isWifiCallingAvailable
        }.getOrDefault(false)
    }

    private fun isWifiCallingReflection(subId: Int): Boolean {
        return runCatching {
            // Utilise ImsManager via réflexion (API interne stable sur AOSP)
            val imsManagerClass = Class.forName("android.telephony.ims.ImsMmTelManager")
            val createMethod    = imsManagerClass.getMethod("createForSubscriptionId", Int::class.java)
            val imsManager      = createMethod.invoke(null, subId)
            val isEnabledMethod = imsManagerClass.getMethod("isVoWiFiSettingEnabled")
            isEnabledMethod.invoke(imsManager) as? Boolean ?: false
        }.getOrDefault(false)
    }

    /**
     * Retourne l'état réseau actuel pour l'affichage dans l'UI.
     */
    fun getNetworkStatus(): NetworkStatus {
        val connectivityManager = context.getSystemService(android.net.ConnectivityManager::class.java)
        val activeNetwork = connectivityManager?.activeNetwork
        val capabilities  = connectivityManager?.getNetworkCapabilities(activeNetwork)

        val hasWifi = capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
        val hasCell = capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true

        val wifiCallingActive = getAvailableSims().any { it.wifiCallingEnabled }

        return when {
            hasCell && hasWifi && wifiCallingActive -> NetworkStatus.WIFI_CALLING_ACTIVE
            hasCell && hasWifi                      -> NetworkStatus.WIFI_AND_CELL
            hasWifi && wifiCallingActive            -> NetworkStatus.WIFI_CALLING_ONLY
            hasWifi                                 -> NetworkStatus.WIFI_ONLY
            hasCell                                 -> NetworkStatus.CELL_ONLY
            else                                    -> NetworkStatus.NO_NETWORK
        }
    }

    /**
     * Passerelle e-mail → SMS (fallback sans WiFi Calling opérateur).
     * L'utilisateur configure sa passerelle dans les Paramètres.
     * Exemples : Free → [num]@free.smsfree.fr, SFR → [num]@sfr.fr
     */
    suspend fun sendViaSmsGateway(
        toNumber: String,
        body:     String,
        gateway:  String,           // ex: "free.smsfree.fr"
        fromEmail: String,          // adresse Gmail/autre de l'utilisateur
    ): Result<Unit> = withContext(Dispatchers.IO) {
        // Construction de l'adresse destinataire passerelle
        val normalizedNumber = toNumber.replace(Regex("[^0-9+]"), "")
        val gatewayAddress   = "$normalizedNumber@$gateway"

        // Envoi via Intent EMAIL (ouvre le client mail ou passe par SMTP configuré)
        runCatching {
            val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                data    = android.net.Uri.parse("mailto:$gatewayAddress")
                putExtra(android.content.Intent.EXTRA_TEXT, body)
                flags   = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }
}

enum class NetworkStatus {
    CELL_ONLY,           // Réseau cellulaire uniquement
    WIFI_ONLY,           // WiFi uniquement (sans WiFi Calling → pas de SMS)
    WIFI_AND_CELL,       // WiFi + cellulaire (SMS via cellulaire)
    WIFI_CALLING_ACTIVE, // WiFi Calling actif → SMS/MMS passent via WiFi
    WIFI_CALLING_ONLY,   // Seul le WiFi est dispo, WiFi Calling activé
    NO_NETWORK,          // Aucun réseau
    ;

    val label: String get() = when (this) {
        CELL_ONLY           -> "Réseau cellulaire"
        WIFI_ONLY           -> "WiFi (sans appels WiFi)"
        WIFI_AND_CELL       -> "WiFi + 4G/5G"
        WIFI_CALLING_ACTIVE -> "Appels & SMS via WiFi ✓"
        WIFI_CALLING_ONLY   -> "SMS via WiFi uniquement"
        NO_NETWORK          -> "Hors ligne"
    }
    val isUsable: Boolean get() = this != WIFI_ONLY && this != NO_NETWORK
    val isWifi:   Boolean get() = this == WIFI_CALLING_ACTIVE || this == WIFI_CALLING_ONLY
}
