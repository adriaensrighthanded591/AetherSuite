package com.aethersms.mms.pdu

/**
 * Corps d'un message MMS = liste de PduPart.
 * Dérivé d'AOSP — Apache 2.0.
 */
class PduBody {

    private val parts = mutableListOf<PduPart>()

    fun addPart(part: PduPart) { parts.add(part) }
    fun addPart(index: Int, part: PduPart) { parts.add(index, part) }

    fun getPart(index: Int): PduPart = parts[index]

    val partsNum: Int get() = parts.size

    fun getParts(): List<PduPart> = parts.toList()

    fun getPartByContentId(contentId: String): PduPart? =
        parts.firstOrNull { it.getContentIdString() == contentId }

    fun getPartByName(name: String): PduPart? =
        parts.firstOrNull { it.getNameString() == name }
}
