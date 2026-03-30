package com.aethersms.mms.pdu

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Encodeur PDU MMS complet, autonome — sans dépendance aux APIs AOSP privées.
 *
 * Implémente la spécification OMA MMS Encapsulation v1.2 (OMA-MMS-ENC-V1_2-20050301-A).
 * Dérivé d'AOSP frameworks/opt/telephony/PduComposer.java — Apache 2.0.
 *
 * Structure d'un PDU m-send-req :
 *  ┌─ En-têtes (WAP binary key-value) ───────────────────────┐
 *  │  Message-Type | Transaction-ID | MMS-Version | Date     │
 *  │  From | To... | Subject? | Content-Type (multipart)     │
 *  └──────────────────────────────────────────────────────────┘
 *  ┌─ Corps multipart ───────────────────────────────────────┐
 *  │  UintVar(nb_parts)                                       │
 *  │  Pour chaque partie :                                    │
 *  │    UintVar(header_len) UintVar(data_len) header data     │
 *  └──────────────────────────────────────────────────────────┘
 */
class PduComposer(
    private val context: Context,
    private val sendReq: SendReq,
) {

    private val output = ByteArrayOutputStream(4096)

    // ── API publique ────────────────────────────────────────────────────────

    /**
     * Encode le PDU complet et retourne les bytes résultants.
     * Retourne null en cas d'erreur (corpo manquant, données illisibles…).
     */
    fun make(): ByteArray? {
        return runCatching {
            writeHeaders()
            writeBody()
            output.toByteArray()
        }.getOrNull()
    }

    // ── En-têtes ────────────────────────────────────────────────────────────

    private fun writeHeaders() {
        // X-Mms-Message-Type: m-send-req
        appendOctet(0x8C)
        appendOctet(PduHeaders.MESSAGE_TYPE_SEND_REQ)

        // X-Mms-Transaction-Id
        appendOctet(0x98)
        appendTextString(UUID.randomUUID().toString().replace("-", "").take(16))

        // X-Mms-MMS-Version: 1.2
        appendOctet(0x8D)
        appendShortInteger(PduHeaders.MMS_VERSION_1_2)

        // Date
        appendOctet(0x85)
        appendLongInteger(sendReq.date)

        // From: insert-address-token
        appendOctet(0x89)
        appendOctet(1)                          // value-length = 1 octet
        appendOctet(PduHeaders.INSERT_ADDRESS_TOKEN)

        // To (un champ par destinataire)
        sendReq.to.forEach { addr ->
            appendOctet(0x97)
            appendEncodedString(addr)
        }

        // Subject (optionnel)
        sendReq.subject?.let { subj ->
            appendOctet(0x96)
            appendEncodedString(subj)
        }

        // X-Mms-Delivery-Report: No
        appendOctet(0x86)
        appendOctet(PduHeaders.VALUE_NO)
    }

    // ── Corps ───────────────────────────────────────────────────────────────

    private fun writeBody() {
        val body = sendReq.body ?: return

        // Résoudre les données de chaque partie
        val resolvedParts = mutableListOf<Pair<PduPart, ByteArray>>()
        for (i in 0 until body.partsNum) {
            val part = body.getPart(i)
            val data = resolvePart(part) ?: continue
            resolvedParts.add(part to data)
        }

        // Construire le SMIL automatiquement si non présent
        val hasSmil = resolvedParts.any { (p, _) ->
            p.getContentTypeString().equals("application/smil", ignoreCase = true)
        }
        val finalParts = if (!hasSmil) {
            val smilPart = buildSmilPart(resolvedParts)
            val smilData = resolvePart(smilPart)!!
            listOf(smilPart to smilData) + resolvedParts
        } else {
            resolvedParts
        }

        // Content-Type: application/vnd.wap.multipart.related
        appendOctet(0x84)
        appendMultipartRelatedContentType(
            startContentId = finalParts.firstOrNull { (p, _) ->
                p.getContentTypeString().equals("application/smil", ignoreCase = true)
            }?.first?.getContentIdString() ?: "<smil>"
        )

        // Nombre de parties
        appendUintVar(finalParts.size.toLong())

        // Écrire chaque partie
        finalParts.forEach { (part, data) ->
            writePart(part, data)
        }
    }

    private fun writePart(part: PduPart, data: ByteArray) {
        val hOut = ByteArrayOutputStream()
        val ct = part.getContentTypeString()

        // ── Content-Type : field token 0x84 ─────────────────────────────────
        // Si le type est une partie texte avec charset, on encapsule dans un
        // paramètre via value-length. Sinon on écrit directement le token.
        if (ct.startsWith("text/")) {
            hOut.write(0x84)                    // Content-Type field
            // value = UTF-8 charset (0xEA = 0x6A|0x80) + content-type token ou texte
            val ctToken = wellKnownContentType(ct)
            val ctInner = ByteArrayOutputStream()
            ctInner.write(0xEA)                 // UTF-8 short-integer (0x6A | 0x80)
            if (ctToken != null) {
                ctInner.write(ctToken)
            } else {
                ctInner.write(ct.toByteArray(Charsets.UTF_8))
                ctInner.write(0x00)
            }
            val ctBytes = ctInner.toByteArray()
            appendValueLength(ctBytes.size.toLong(), hOut)
            hOut.write(ctBytes)
        } else {
            hOut.write(0x84)                    // Content-Type field
            val wellKnown = wellKnownContentType(ct)
            if (wellKnown != null) {
                // Short-integer déjà encodé (bit 7 = 1)
                hOut.write(wellKnown)
            } else {
                // Type non standard (ex. application/pdf, application/smil) :
                // text-string null-terminé. Si premier byte >= 0x80 → préfixe quote 0x7F
                val ctBytes = ct.toByteArray(Charsets.UTF_8)
                if (ctBytes.isNotEmpty() && (ctBytes[0].toInt() and 0xFF) >= 0x80) hOut.write(0x7F)
                hOut.write(ctBytes)
                hOut.write(0x00)
            }
        }

        // ── Content-ID ───────────────────────────────────────────────────────
        val contentId = part.getContentIdString() ?: "<part${System.nanoTime()}>"
        hOut.write(0xC0.toByte().toInt())       // Content-ID header token
        writeNullTerminated(hOut, contentId)

        // ── Content-Location (nom de fichier) ────────────────────────────────
        part.getNameString()?.let { name ->
            hOut.write(0xCE.toByte().toInt())   // Content-Location header token
            writeNullTerminated(hOut, name)
        }

        val headerBytes = hOut.toByteArray()
        appendUintVar(headerBytes.size.toLong())
        appendUintVar(data.size.toLong())
        output.write(headerBytes)
        output.write(data)
    }

    /** Variante de appendValueLength qui écrit dans un ByteArrayOutputStream arbitraire. */
    private fun appendValueLength(length: Long, out: ByteArrayOutputStream) {
        if (length < 0x1F) {
            out.write(length.toInt())
        } else {
            out.write(0x1F)
            val bytes = mutableListOf<Byte>()
            var v = length
            bytes.add((v and 0x7F).toByte()); v = v ushr 7
            while (v > 0) { bytes.add(0, ((v and 0x7F) or 0x80).toByte()); v = v ushr 7 }
            bytes.forEach { out.write(it.toInt() and 0xFF) }
        }
    }

    // ── SMIL auto-généré ─────────────────────────────────────────────────────

    private fun buildSmilPart(parts: List<Pair<PduPart, ByteArray>>): PduPart {
        val sb = StringBuilder()
        sb.append("<smil><head><layout>")
        sb.append("<root-layout width=\"100%\" height=\"100%\"/>")
        sb.append("<region id=\"Image\" top=\"0\" left=\"0\" height=\"70%\" width=\"100%\" fit=\"meet\"/>")
        sb.append("<region id=\"Text\"  top=\"70%\" left=\"0\" height=\"30%\" width=\"100%\"/>")
        sb.append("</layout></head><body><par dur=\"5000ms\">")
        parts.forEach { (p, _) ->
            val ct = p.getContentTypeString()
            val src = p.getNameString() ?: p.getContentIdString() ?: "file"
            when {
                ct.startsWith("image/")      -> sb.append("<img src=\"$src\" region=\"Image\"/>")
                ct.startsWith("text/plain")  -> sb.append("<text src=\"$src\" region=\"Text\"/>")
                ct.startsWith("video/")      -> sb.append("<video src=\"$src\" region=\"Image\"/>")
                ct.startsWith("audio/")      -> sb.append("<audio src=\"$src\"/>")
            }
        }
        sb.append("</par></body></smil>")

        return PduPart().apply {
            setContentType("application/smil")
            setContentId("<smil>")
            setName("smil.xml")
            setData(sb.toString().toByteArray(Charsets.UTF_8))
        }
    }

    // ── Résolution des données ───────────────────────────────────────────────

    private fun resolvePart(part: PduPart): ByteArray? {
        part.data?.let { return it }
        part.dataUri?.let { uriString ->
            return runCatching {
                context.contentResolver
                    .openInputStream(Uri.parse(uriString))
                    ?.use { it.readBytes() }
            }.getOrNull()
        }
        return null
    }

    // ── Encodage WAP binary ──────────────────────────────────────────────────

    /**
     * Content-Type: application/vnd.wap.multipart.related (0xB3)
     * avec paramètre Start (0x8A) et Type (0x89).
     */
    private fun appendMultipartRelatedContentType(startContentId: String) {
        val ctOut = ByteArrayOutputStream()

        // multipart/related well-known token
        ctOut.write(0xB3)

        // Start parameter (0x8A)
        ctOut.write(0x8A)
        writeNullTerminated(ctOut, startContentId)

        // Type parameter (0x89) — application/smil
        ctOut.write(0x89)
        writeNullTerminated(ctOut, "application/smil")

        val ctBytes = ctOut.toByteArray()
        // Value-length + content
        appendValueLength(ctBytes.size.toLong())
        output.write(ctBytes)
    }

    /** Écrit un octet brut */
    private fun appendOctet(value: Int) { output.write(value and 0xFF) }

    /**
     * Short-integer : valeur sur 1 octet avec bit 7 mis à 1.
     * Utilisé pour les valeurs 0–127.
     */
    private fun appendShortInteger(value: Int) {
        output.write((value and 0x7F) or 0x80)
    }

    /**
     * Long-integer : 1 octet = longueur, puis les octets de valeur.
     */
    private fun appendLongInteger(value: Long) {
        val bytes = mutableListOf<Byte>()
        var v = value
        while (v > 0) {
            bytes.add(0, (v and 0xFF).toByte())
            v = v ushr 8
        }
        if (bytes.isEmpty()) bytes.add(0)
        output.write(bytes.size)
        bytes.forEach { output.write(it.toInt() and 0xFF) }
    }

    /**
     * Text-string : null-terminé, avec 0x80 devant si la chaîne
     * commence par un caractère >= 0x80 (quote character).
     */
    private fun appendTextString(s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        if (bytes.isNotEmpty() && (bytes[0].toInt() and 0xFF) >= 0x80) output.write(0x7F)
        output.write(bytes)
        output.write(0x00)
    }

    /**
     * Encoded-string-value : charset (short-int ou long-int) + texte null-terminé.
     */
    private fun appendEncodedString(esv: EncodedStringValue) {
        val strBytes = esv.textString
        // Charset UTF-8 encodé en short-integer : 0x6A | 0x80 = 0xEA
        // Mais on utilise une value-length pour être propre
        val inner = ByteArrayOutputStream()
        inner.write(0xEA)               // UTF-8 short-integer (0x6A | 0x80)
        inner.write(strBytes)
        inner.write(0x00)               // null terminator
        val innerBytes = inner.toByteArray()
        appendValueLength(innerBytes.size.toLong())
        output.write(innerBytes)
    }

    /**
     * Value-length : valeur < 31 → 1 octet direct ; >= 31 → 0x1F + UintVar.
     */
    private fun appendValueLength(length: Long) {
        if (length < 0x1F) {
            output.write(length.toInt())
        } else {
            output.write(0x1F)
            appendUintVar(length)
        }
    }

    /**
     * UintVar : entier non signé à longueur variable (7 bits utiles par octet,
     * bit 7 = 1 signifie "un octet de plus suit").
     */
    private fun appendUintVar(value: Long) {
        val bytes = mutableListOf<Byte>()
        var v = value
        bytes.add((v and 0x7F).toByte())
        v = v ushr 7
        while (v > 0) {
            bytes.add(0, ((v and 0x7F) or 0x80).toByte())
            v = v ushr 7
        }
        bytes.forEach { output.write(it.toInt() and 0xFF) }
    }

    private fun writeNullTerminated(out: ByteArrayOutputStream, s: String) {
        out.write(s.toByteArray(Charsets.UTF_8))
        out.write(0x00)
    }

    /**
     * Table de correspondance type MIME → short-integer WSP (token | 0x80).
     * Source : WAP-TYPE-20020209-a Table 40 + OMA MMS ENC 1.2 §7.3.
     *
     * IMPORTANT : les valeurs retournées sont déjà encodées en short-integer
     * (bit 7 mis à 1). Ex. : image/jpeg = 0x1E (brut) → 0x9E (short-int).
     * application/smil N'EST PAS dans la table WAP standard ; il sera encodé
     * en texte null-terminé par la branche `else` de writePart().
     */
    private fun wellKnownContentType(ct: String): Int? = when (ct.lowercase().trim()) {
        "text/html"                              -> 0x82  // 0x02 | 0x80
        "text/plain"                             -> 0x83  // 0x03 | 0x80
        "text/vnd.wap.wml"                       -> 0x88  // 0x08 | 0x80
        "image/gif"                              -> 0x9D  // 0x1D | 0x80
        "image/jpeg", "image/jpg"                -> 0x9E  // 0x1E | 0x80
        "image/tiff"                             -> 0x9F  // 0x1F | 0x80
        "image/png"                              -> 0xA0  // 0x20 | 0x80
        "image/vnd.wap.wbmp"                     -> 0xA1  // 0x21 | 0x80
        "application/vnd.wap.multipart.mixed"    -> 0xA3  // 0x23 | 0x80
        "application/vnd.wap.multipart.related"  -> 0xB3  // 0x33 | 0x80
        // application/smil → absent de la table WAP, encodé en texte (else branch)
        else                                     -> null
    }
}
