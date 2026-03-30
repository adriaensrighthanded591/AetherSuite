package com.aethersms.mms.pdu

/**
 * PDU de demande d'envoi MMS (m-send-req).
 * Dérivé d'AOSP — Apache 2.0.
 */
class SendReq {

    /** Destinataires */
    val to: MutableList<EncodedStringValue> = mutableListOf()

    /** Expéditeur (optionnel — laissé null = INSERT_ADDRESS_TOKEN) */
    var from: EncodedStringValue? = null

    /** Sujet (optionnel) */
    var subject: EncodedStringValue? = null

    /** Corps multipart */
    var body: PduBody? = null

    /** Date d'envoi en secondes depuis epoch */
    var date: Long = System.currentTimeMillis() / 1000L

    /** Priorité (PRIORITY_NORMAL par défaut) */
    var priority: Int = PduHeaders.PRIORITY_NORMAL

    /** Demander un accusé de lecture ? */
    var readReport: Boolean = false

    // ── Helpers ─────────────────────────────────────────────────────────────

    fun addTo(address: EncodedStringValue) { to.add(address) }
    fun addTo(address: String) { to.add(EncodedStringValue(address)) }
}
