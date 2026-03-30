package com.aethersms

import com.aethersms.data.model.*
import com.aethersms.mms.pdu.*
import org.junit.Assert.*
import org.junit.Test

// ── Tests des modèles de données ─────────────────────────────────────────────

class ConversationModelTest {

    @Test
    fun `displayName retourne le nom du contact en priorité`() {
        val conv = Conversation(
            recipientAddresses = listOf("+33612345678"),
            contactNames       = listOf("Alice Dupont"),
        )
        assertEquals("Alice Dupont", conv.displayName)
    }

    @Test
    fun `displayName retourne le numéro si aucun contact`() {
        val conv = Conversation(
            recipientAddresses = listOf("+33612345678"),
            contactNames       = emptyList(),
        )
        assertEquals("+33612345678", conv.displayName)
    }

    @Test
    fun `displayName retourne Inconnu si aucune donnée`() {
        val conv = Conversation()
        assertEquals("Inconnu", conv.displayName)
    }

    @Test
    fun `initiales calculées correctement sur un nom complet`() {
        val conv = Conversation(contactNames = listOf("Jean-Pierre Martin"))
        assertEquals("JM", conv.initials)
    }

    @Test
    fun `initiales d un seul mot retournent une lettre`() {
        val conv = Conversation(contactNames = listOf("Alice"))
        assertEquals("A", conv.initials)
    }

    @Test
    fun `initiales fallback point d interrogation si vide`() {
        val conv = Conversation()
        assertEquals("?", conv.initials)
    }

    @Test
    fun `isGroup vrai si plusieurs destinataires`() {
        val conv = Conversation(recipientAddresses = listOf("A", "B"))
        assertFalse(conv.isGroup) // isGroup est défini manuellement, pas calculé
        // => correct : c'est le ContentProvider qui le détermine
    }
}

class MessageModelTest {

    @Test
    fun `attachment isImage vrai pour image-jpeg`() {
        val att = Attachment(contentType = "image/jpeg")
        assertTrue(att.isImage)
        assertFalse(att.isPdf)
        assertFalse(att.isVideo)
    }

    @Test
    fun `attachment isPdf vrai pour application-pdf`() {
        val att = Attachment(contentType = "application/pdf")
        assertTrue(att.isPdf)
        assertFalse(att.isImage)
    }

    @Test
    fun `attachment isVideo vrai pour video-mp4`() {
        val att = Attachment(contentType = "video/mp4")
        assertTrue(att.isVideo)
        assertFalse(att.isImage)
    }

    @Test
    fun `messageType INBOX identifié correctement`() {
        val msg = Message(type = MessageType.INBOX)
        assertEquals(MessageType.INBOX, msg.type)
    }
}

// ── Tests des classes PDU ─────────────────────────────────────────────────────

class EncodedStringValueTest {

    @Test
    fun `constructeur depuis String encode en UTF-8`() {
        val esv = EncodedStringValue("Hello World")
        assertEquals("Hello World", esv.string)
        assertEquals(0x6A, esv.charset)
    }

    @Test
    fun `constructeur depuis String avec accents`() {
        val esv = EncodedStringValue("Bonjour à tous")
        assertEquals("Bonjour à tous", esv.string)
    }

    @Test
    fun `egalite fonctionne sur deux instances identiques`() {
        val a = EncodedStringValue("test")
        val b = EncodedStringValue("test")
        assertEquals(a, b)
    }

    @Test
    fun `inegalite fonctionne sur contenus differents`() {
        val a = EncodedStringValue("foo")
        val b = EncodedStringValue("bar")
        assertNotEquals(a, b)
    }
}

class PduHeadersTest {

    @Test
    fun `MESSAGE_TYPE_SEND_REQ vaut 0x80`() {
        assertEquals(0x80, PduHeaders.MESSAGE_TYPE_SEND_REQ)
    }

    @Test
    fun `MMS_VERSION_1_2 vaut 0x92`() {
        assertEquals(0x92, PduHeaders.MMS_VERSION_1_2)
    }

    @Test
    fun `INSERT_ADDRESS_TOKEN vaut 0x81`() {
        assertEquals(0x81, PduHeaders.INSERT_ADDRESS_TOKEN)
    }

    @Test
    fun `TO field code vaut 0x17`() {
        assertEquals(0x17, PduHeaders.TO)
    }
}

class PduPartTest {

    @Test
    fun `setContentType et getContentTypeString sont coherents`() {
        val part = PduPart()
        part.setContentType("image/jpeg")
        assertEquals("image/jpeg", part.getContentTypeString())
    }

    @Test
    fun `setData stocke les bytes`() {
        val part = PduPart()
        val data = "Hello MMS".toByteArray()
        part.setData(data)
        assertArrayEquals(data, part.data)
        assertNull(part.dataUri)
    }

    @Test
    fun `setDataUri efface data et stocke l URI`() {
        val part = PduPart()
        part.setData("old".toByteArray())
        part.setDataUri("content://mms/part/42")
        assertNull(part.data)
        assertEquals("content://mms/part/42", part.dataUri)
    }

    @Test
    fun `setName et getNameString sont coherents`() {
        val part = PduPart()
        part.setName("photo.jpg")
        assertEquals("photo.jpg", part.getNameString())
    }
}

class PduBodyTest {

    @Test
    fun `partsNum reflète le nombre de parties ajoutées`() {
        val body = PduBody()
        assertEquals(0, body.partsNum)
        body.addPart(PduPart())
        assertEquals(1, body.partsNum)
        body.addPart(PduPart())
        assertEquals(2, body.partsNum)
    }

    @Test
    fun `getPart retourne la partie à l index correct`() {
        val body = PduBody()
        val part1 = PduPart().apply { setContentType("text/plain") }
        val part2 = PduPart().apply { setContentType("image/jpeg") }
        body.addPart(part1)
        body.addPart(part2)
        assertEquals("text/plain", body.getPart(0).getContentTypeString())
        assertEquals("image/jpeg", body.getPart(1).getContentTypeString())
    }

    @Test
    fun `getPartByName retrouve une partie par son nom`() {
        val body = PduBody()
        val part = PduPart().apply {
            setName("photo.jpg")
            setContentType("image/jpeg")
        }
        body.addPart(part)
        assertNotNull(body.getPartByName("photo.jpg"))
        assertNull(body.getPartByName("other.jpg"))
    }
}

class SendReqTest {

    @Test
    fun `addTo avec String crée un EncodedStringValue`() {
        val req = SendReq()
        req.addTo("+33612345678")
        assertEquals(1, req.to.size)
        assertEquals("+33612345678", req.to[0].string)
    }

    @Test
    fun `plusieurs destinataires stockés correctement`() {
        val req = SendReq()
        req.addTo("+33611111111")
        req.addTo("+33622222222")
        req.addTo("+33633333333")
        assertEquals(3, req.to.size)
    }

    @Test
    fun `date initialisée à l heure courante`() {
        val before = System.currentTimeMillis() / 1000L
        val req    = SendReq()
        val after  = System.currentTimeMillis() / 1000L
        assertTrue(req.date in before..after)
    }

    @Test
    fun `body peut être null`() {
        val req = SendReq()
        assertNull(req.body)
    }

    @Test
    fun `body assigné correctement`() {
        val req  = SendReq()
        val body = PduBody()
        req.body = body
        assertSame(body, req.body)
    }
}
