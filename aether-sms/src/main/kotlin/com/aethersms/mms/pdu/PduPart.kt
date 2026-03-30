package com.aethersms.mms.pdu

/**
 * Une partie (MIME part) du corps d'un message MMS.
 * Dérivé d'AOSP — Apache 2.0.
 */
class PduPart {

    // ── Champs d'en-tête de la partie ───────────────────────────────────────

    /** Content-Type (ex: "image/jpeg", "text/plain", "application/pdf") */
    var contentType: ByteArray = byteArrayOf()

    /** Content-Id (ex: "<image1>") */
    var contentId: ByteArray? = null

    /** Content-Location / nom de fichier (ex: "photo.jpg") */
    var name: ByteArray? = null

    /** Charset pour les parties texte (UTF-8 = 0x6A = 106) */
    var charset: Int = 0x6A

    /** Données brutes de la partie (null si stocké dans dataUri) */
    var data: ByteArray? = null

    /** URI content:// si les données sont dans le ContentResolver */
    var dataUri: String? = null

    // ── Helpers ─────────────────────────────────────────────────────────────

    fun setContentType(ct: String) { contentType = ct.toByteArray(Charsets.US_ASCII) }
    fun getContentTypeString(): String = contentType.toString(Charsets.US_ASCII)

    fun setContentId(id: String) { contentId = id.toByteArray(Charsets.US_ASCII) }
    fun getContentIdString(): String? = contentId?.toString(Charsets.US_ASCII)

    fun setName(n: String) { name = n.toByteArray(Charsets.UTF_8) }
    fun getNameString(): String? = name?.toString(Charsets.UTF_8)

    fun setData(d: ByteArray) { data = d; dataUri = null }
    fun setDataUri(uri: String) { dataUri = uri; data = null }
}
