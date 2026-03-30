package com.aethersms

import android.Manifest
import android.app.Activity
import com.aether.core.AetherIntents
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aethersms.data.repository.PreferencesRepository
import com.aethersms.ui.navigation.AetherNavigation
import com.aethersms.ui.theme.AetherSMSTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    // Permissions requises à la volée
    private val REQUIRED_PERMISSIONS = buildList {
        add(Manifest.permission.READ_SMS)
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.READ_PHONE_STATE)   // SIM info + WiFi Calling detection
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
            add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions accordées — l'app continue naturellement */ }

    private val defaultSmsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* résultat de la demande d'app SMS par défaut */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // 1. Demander les permissions
        permissionsLauncher.launch(REQUIRED_PERMISSIONS)

        // 2. Proposer de devenir l'app SMS par défaut
        requestDefaultSmsRole()

        // 3. Lire les préférences thème de façon synchrone (splash)
        val prefs = PreferencesRepository(this)
        val settings = runBlocking { prefs.appSettings.first() }

        setContent {
            // Thème dynamique basé sur les préférences
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when {
                settings.useSystemTheme -> systemDark
                else                    -> settings.useDarkTheme
            }

            AetherSMSTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AetherNavigation()
                }
            }
        }
    }

    private fun requestDefaultSmsRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)
                && !roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                defaultSmsLauncher.launch(intent)
            }
        } else {
            val current = Telephony.Sms.getDefaultSmsPackage(this)
            if (current != packageName) {
                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                    putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                }
                startActivity(intent)
            }
        }
    }

    /**
     * Déverrouillage biométrique — appelé depuis l'UI si `lockApp = true`.
     */
    fun showBiometricPrompt(onSuccess: () -> Unit, onFail: () -> Unit) {
        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            onSuccess() // Pas de biométrie disponible → laisser passer
            return
        }
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onSuccess() }
            override fun onAuthenticationFailed() { onFail() }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { onFail() }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("AetherSMS")
            .setSubtitle("Déverrouiller pour accéder à vos messages")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(info)
    }
}
