package com.aether.core

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Toutes les actions et constantes pour la communication inter-applications Aether.
 *
 * Architecture :
 *  - Chaque app Aether déclare les <intent-filter> correspondants dans son Manifest
 *  - Les autres apps utilisent ces constantes pour lancer des actions
 *  - Aucune dépendance directe entre apps — tout passe par des Intents Android
 *
 * Sécurité :
 *  - Les Intents ne contiennent jamais de données sensibles en clair
 *  - Seules les apps du package com.aether.* peuvent répondre aux actions privées
 */
object AetherIntents {

    // ── Packages ──────────────────────────────────────────────────────────
    const val PKG_SMS      = "com.aethersms"
    const val PKG_CONTACTS = "com.aether.contacts"
    const val PKG_PHONE    = "com.aether.phone"
    const val PKG_NOTES    = "com.aether.notes"
    const val PKG_FILES    = "com.aether.files"

    // ── Actions Contacts ─────────────────────────────────────────────────
    const val ACTION_VIEW_CONTACT    = "com.aether.action.VIEW_CONTACT"
    const val ACTION_EDIT_CONTACT    = "com.aether.action.EDIT_CONTACT"
    const val ACTION_CREATE_CONTACT  = "com.aether.action.CREATE_CONTACT"
    const val ACTION_PICK_CONTACT    = "com.aether.action.PICK_CONTACT"

    // ── Actions Phone ─────────────────────────────────────────────────────
    const val ACTION_DIAL            = "com.aether.action.DIAL"
    const val ACTION_CALL            = "com.aether.action.CALL"
    const val ACTION_VIEW_CALL_LOG   = "com.aether.action.VIEW_CALL_LOG"

    // ── Actions Notes ─────────────────────────────────────────────────────
    const val ACTION_NEW_NOTE        = "com.aether.action.NEW_NOTE"
    const val ACTION_VIEW_NOTE       = "com.aether.action.VIEW_NOTE"
    const val ACTION_SHARE_TO_NOTE   = "com.aether.action.SHARE_TO_NOTE"

    // ── Actions Files ─────────────────────────────────────────────────────
    const val ACTION_OPEN_FILE       = "com.aether.action.OPEN_FILE"
    const val ACTION_PICK_FILE       = "com.aether.action.PICK_FILE"
    const val ACTION_BROWSE_DIR      = "com.aether.action.BROWSE_DIR"

    // ── Extras ────────────────────────────────────────────────────────────
    const val EXTRA_CONTACT_ID       = "contact_id"
    const val EXTRA_PHONE_NUMBER     = "phone_number"
    const val EXTRA_NOTE_ID          = "note_id"
    const val EXTRA_NOTE_TITLE       = "note_title"
    const val EXTRA_NOTE_CONTENT     = "note_content"
    const val EXTRA_FILE_PATH        = "file_path"
    const val EXTRA_DIRECTORY        = "directory"

    // ════════════════════════════════════════════════════════════════════════
    // HELPERS DE NAVIGATION
    // ════════════════════════════════════════════════════════════════════════

    /** Ouvrir AetherContacts sur un contact spécifique */
    fun viewContact(context: Context, contactId: Long) {
        val intent = Intent(ACTION_VIEW_CONTACT).apply {
            putExtra(EXTRA_CONTACT_ID, contactId)
            setPackage(PKG_CONTACTS)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { context.startActivity(intent) }
    }

    /** Créer un nouveau contact dans AetherContacts avec un numéro pré-rempli */
    fun createContactFromNumber(context: Context, phoneNumber: String) {
        val intent = Intent(ACTION_CREATE_CONTACT).apply {
            putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
            setPackage(PKG_CONTACTS)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { context.startActivity(intent) }
    }

    /** Composer un numéro dans AetherPhone */
    fun dialNumber(context: Context, phoneNumber: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
            setPackage(PKG_PHONE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        // Fallback sur le dialer système si AetherPhone non installé
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
    }

    /** Appel direct (nécessite permission CALL_PHONE) */
    fun callNumber(context: Context, phoneNumber: String) {
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply {
            setPackage(PKG_PHONE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { context.startActivity(intent) }
    }

    /** Partager du texte vers AetherNotes */
    fun shareToNotes(context: Context, title: String = "", content: String) {
        val intent = Intent(ACTION_SHARE_TO_NOTE).apply {
            putExtra(EXTRA_NOTE_TITLE,   title)
            putExtra(EXTRA_NOTE_CONTENT, content)
            setPackage(PKG_NOTES)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { context.startActivity(intent) }
    }

    /** Envoyer un SMS depuis n'importe quelle app Aether */
    fun sendSms(context: Context, phoneNumber: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phoneNumber")).apply {
            setPackage(PKG_SMS)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { context.startActivity(intent) }
    }

    /** Ouvrir un fichier dans AetherFiles */
    fun openInFiles(context: Context, path: String) {
        val intent = Intent(ACTION_BROWSE_DIR).apply {
            putExtra(EXTRA_DIRECTORY, path)
            setPackage(PKG_FILES)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { context.startActivity(intent) }
    }

    /** Vérifier si une app Aether est installée */
    fun isAetherAppInstalled(context: Context, packageName: String): Boolean =
        runCatching {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
}
