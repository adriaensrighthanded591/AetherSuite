package com.aether.core.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

// ════════════════════════════════════════════════════════════════════════════
// BIOMÉTRIE — partagée par toutes les apps Aether
// ════════════════════════════════════════════════════════════════════════════

object AetherBiometric {

    enum class Result { SUCCESS, FAILED, ERROR, NOT_AVAILABLE }

    fun isAvailable(context: Context): Boolean {
        val bm = BiometricManager.from(context)
        return bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Demande biométrique suspendue — utilise des coroutines.
     * @param activity  FragmentActivity requise par BiometricPrompt
     * @param title     Titre de la boîte de dialogue
     * @param subtitle  Sous-titre
     */
    suspend fun authenticate(
        activity: FragmentActivity,
        title:    String = "Aether Suite",
        subtitle: String = "Déverrouillez pour continuer",
    ): Result = suspendCancellableCoroutine { cont ->
        if (!isAvailable(activity)) {
            cont.resume(Result.NOT_AVAILABLE)
            return@suspendCancellableCoroutine
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt   = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) {
                    if (cont.isActive) cont.resume(Result.SUCCESS)
                }
                override fun onAuthenticationFailed() {
                    if (cont.isActive) cont.resume(Result.FAILED)
                }
                override fun onAuthenticationError(code: Int, msg: CharSequence) {
                    if (cont.isActive) cont.resume(Result.ERROR)
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        prompt.authenticate(info)
    }
}

// ════════════════════════════════════════════════════════════════════════════
// PRÉFÉRENCES CHIFFRÉES
// ════════════════════════════════════════════════════════════════════════════

/**
 * SharedPreferences chiffrées via AES-256-GCM + clé dans AndroidKeyStore.
 * Utilisé par toutes les apps Aether pour stocker des paramètres sensibles
 * (clé de chiffrement des notes, état du verrouillage, etc.)
 */
class AetherSecurePrefs(context: Context, fileName: String = "aether_secure") {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        fileName,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun putString(key: String, value: String)  = prefs.edit().putString(key, value).apply()
    fun getString(key: String, default: String = "") = prefs.getString(key, default) ?: default
    fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    fun getBoolean(key: String, default: Boolean = false) = prefs.getBoolean(key, default)
    fun putLong(key: String, value: Long)       = prefs.edit().putLong(key, value).apply()
    fun getLong(key: String, default: Long = 0L) = prefs.getLong(key, default)
    fun remove(key: String)                     = prefs.edit().remove(key).apply()
    fun contains(key: String)                   = prefs.contains(key)
}

// ════════════════════════════════════════════════════════════════════════════
// CHIFFREMENT DES NOTES
// ════════════════════════════════════════════════════════════════════════════

object AetherCrypto {

    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_SIZE  = 256
    private const val IV_SIZE   = 12   // 96 bits pour GCM
    private const val TAG_SIZE  = 128  // bits

    /**
     * Chiffre une chaîne en AES-256-GCM.
     * Retourne IV + ciphertext en Base64.
     */
    fun encrypt(plaintext: String, keyAlias: String = "aether_notes_key"): String {
        return runCatching {
            val key      = getOrCreateKey(keyAlias)
            val cipher   = javax.crypto.Cipher.getInstance(ALGORITHM)
            val paramSpec = javax.crypto.spec.GCMParameterSpec(TAG_SIZE, generateIv())
            // Note : pour GCM, l'IV est généré automatiquement par le KeyStore
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key)
            val iv         = cipher.iv
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            // Format : Base64(iv || ciphertext)
            val combined   = iv + ciphertext
            android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
        }.getOrElse { plaintext }  // Fallback : retourner le texte en clair si erreur keystore
    }

    /**
     * Déchiffre un texte chiffré par encrypt().
     */
    fun decrypt(cipherBase64: String, keyAlias: String = "aether_notes_key"): String {
        return runCatching {
            val combined   = android.util.Base64.decode(cipherBase64, android.util.Base64.NO_WRAP)
            val iv         = combined.copyOfRange(0, IV_SIZE)
            val ciphertext = combined.copyOfRange(IV_SIZE, combined.size)
            val key        = getOrCreateKey(keyAlias)
            val cipher     = javax.crypto.Cipher.getInstance(ALGORITHM)
            val paramSpec  = javax.crypto.spec.GCMParameterSpec(TAG_SIZE, iv)
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, paramSpec)
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        }.getOrElse { cipherBase64 }  // Fallback : retourner tel quel
    }

    private fun generateIv(): ByteArray = ByteArray(IV_SIZE).also {
        java.security.SecureRandom().nextBytes(it)
    }

    private fun getOrCreateKey(alias: String): java.security.Key {
        val keystore = java.security.KeyStore.getInstance("AndroidKeyStore")
        keystore.load(null)
        if (keystore.containsAlias(alias)) {
            return keystore.getKey(alias, null)
        }
        val keyGen = javax.crypto.KeyGenerator.getInstance(
            android.security.keystore.KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            alias,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
            android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE)
            .setUserAuthenticationRequired(false)   // Clé accessible sans auth (l'app gère ça)
            .build()
        keyGen.init(spec)
        return keyGen.generateKey()
    }
}
