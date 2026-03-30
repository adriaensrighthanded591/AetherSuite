package com.aether.suite.tests

import com.aether.contacts.AetherContact
import com.aether.contacts.ContactPhone
import com.aether.contacts.ContactEmail
import com.aether.notes.Note
import com.aether.notes.NOTE_COLORS
import com.aether.files.formatSize
import com.aether.files.getMime
import com.aether.phone.CallLogEntry
import com.aether.phone.CallType
import org.junit.Assert.*
import org.junit.Test
import java.io.File

// ════════════════════════════════════════════════════════════════════════════
// AETHER CONTACTS
// ════════════════════════════════════════════════════════════════════════════

class ContactModelTest {

    @Test
    fun `initials générées depuis prénom et nom`() {
        val c = AetherContact(displayName = "Jean Dupont")
        assertEquals("JD", c.initials)
    }

    @Test
    fun `initials depuis un seul mot`() {
        val c = AetherContact(displayName = "Alice")
        assertEquals("A", c.initials)
    }

    @Test
    fun `initials fallback point d interrogation`() {
        val c = AetherContact(displayName = "")
        assertEquals("?", c.initials)
    }

    @Test
    fun `firstPhone retourne le premier numéro`() {
        val c = AetherContact(phones = listOf(
            ContactPhone("+33612345678", "Mobile"),
            ContactPhone("+33987654321", "Domicile"),
        ))
        assertEquals("+33612345678", c.firstPhone)
    }

    @Test
    fun `firstPhone retourne vide si aucun téléphone`() {
        val c = AetherContact(phones = emptyList())
        assertEquals("", c.firstPhone)
    }

    @Test
    fun `firstEmail retourne le premier email`() {
        val c = AetherContact(emails = listOf(ContactEmail("alice@example.com")))
        assertEquals("alice@example.com", c.firstEmail)
    }

    @Test
    fun `isFavorite false par défaut`() {
        val c = AetherContact()
        assertFalse(c.isFavorite)
    }

    @Test
    fun `contact avec organisation`() {
        val c = AetherContact(displayName = "Bob Martin", organization = "Aether Corp")
        assertEquals("Aether Corp", c.organization)
    }
}

// ════════════════════════════════════════════════════════════════════════════
// AETHER NOTES
// ════════════════════════════════════════════════════════════════════════════

class NoteModelTest {

    private fun makeNote(title: String = "Test", body: String = "Corps du texte") = Note(
        id        = 1L,
        title     = title,
        body      = body,
        colorHex  = NOTE_COLORS.first(),
        pinned    = false,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        tags      = emptyList(),
    )

    @Test
    fun `snippet retourne la première ligne non vide`() {
        val note = makeNote(body = "\n  \nPremière ligne\nDeuxième ligne")
        assertEquals("Première ligne", note.snippet)
    }

    @Test
    fun `snippet tronqué à 80 caractères`() {
        val long = "A".repeat(100)
        val note = makeNote(body = long)
        assertEquals(80, note.snippet.length)
    }

    @Test
    fun `snippet vide si body vide`() {
        val note = makeNote(body = "")
        assertEquals("", note.snippet)
    }

    @Test
    fun `color parsée depuis hex valide`() {
        val note = makeNote().copy(colorHex = "#1F1D2B")
        assertNotNull(note.color)
    }

    @Test
    fun `color par défaut si hex invalide`() {
        val note = makeNote().copy(colorHex = "invalide_hex")
        assertNotNull(note.color)  // Ne doit pas crasher
    }

    @Test
    fun `NOTE_COLORS contient 8 couleurs`() {
        assertEquals(8, NOTE_COLORS.size)
    }

    @Test
    fun `note non épinglée par défaut`() {
        assertFalse(makeNote().pinned)
    }

    @Test
    fun `note copiée avec épinglage`() {
        val pinned = makeNote().copy(pinned = true)
        assertTrue(pinned.pinned)
    }
}

// ════════════════════════════════════════════════════════════════════════════
// AETHER FILES
// ════════════════════════════════════════════════════════════════════════════

class FilesUtilsTest {

    @Test
    fun `formatSize octets`() {
        assertEquals("512 o", formatSize(512L))
    }

    @Test
    fun `formatSize kilo-octets`() {
        assertEquals("2 Ko", formatSize(2048L))
    }

    @Test
    fun `formatSize mega-octets`() {
        val result = formatSize(1_572_864L)  // 1.5 Mo
        assertTrue(result.contains("Mo"))
    }

    @Test
    fun `formatSize giga-octets`() {
        val result = formatSize(2_147_483_648L)  // 2 Go
        assertTrue(result.contains("Go"))
    }

    @Test
    fun `getMime image jpeg`() {
        val file = File("photo.jpg")
        assertEquals("image/jpeg", getMime(file))
    }

    @Test
    fun `getMime application pdf`() {
        val file = File("document.pdf")
        assertEquals("application/pdf", getMime(file))
    }

    @Test
    fun `getMime dossier`() {
        // Un File directory retourne inode/directory
        val dir = File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        assertEquals("inode/directory", getMime(dir))
    }

    @Test
    fun `getMime inconnu retourne octet-stream`() {
        val file = File("data.xyz123unknown")
        assertEquals("application/octet-stream", getMime(file))
    }

    @Test
    fun `getMime audio mp3`() {
        val file = File("musique.mp3")
        assertTrue(getMime(file).startsWith("audio/"))
    }

    @Test
    fun `getMime video mp4`() {
        val file = File("video.mp4")
        assertTrue(getMime(file).startsWith("video/"))
    }
}

// ════════════════════════════════════════════════════════════════════════════
// AETHER PHONE
// ════════════════════════════════════════════════════════════════════════════

class CallLogModelTest {

    private fun makeCall(type: CallType = CallType.INCOMING, name: String? = "Alice") =
        CallLogEntry(id = 1L, number = "+33612345678", contactName = name,
            type = type, date = System.currentTimeMillis(), duration = 120L)

    @Test
    fun `displayName retourne le nom du contact`() {
        assertEquals("Alice", makeCall().displayName)
    }

    @Test
    fun `displayName retourne le numéro si aucun contact`() {
        assertEquals("+33612345678", makeCall(name = null).displayName)
    }

    @Test
    fun `displayName retourne le numéro si nom vide`() {
        assertEquals("+33612345678", makeCall(name = "").displayName)
    }

    @Test
    fun `isAnswered vrai pour appel entrant`() {
        assertTrue(makeCall(CallType.INCOMING).isAnswered)
    }

    @Test
    fun `isAnswered vrai pour appel sortant`() {
        assertTrue(makeCall(CallType.OUTGOING).isAnswered)
    }

    @Test
    fun `isAnswered faux pour appel manqué`() {
        assertFalse(makeCall(CallType.MISSED).isAnswered)
    }

    @Test
    fun `isAnswered faux pour appel rejeté`() {
        assertFalse(makeCall(CallType.REJECTED).isAnswered)
    }

    @Test
    fun `duration correctement stockée`() {
        assertEquals(120L, makeCall().duration)
    }
}
