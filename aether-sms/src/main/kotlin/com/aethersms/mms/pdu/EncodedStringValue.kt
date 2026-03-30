package com.aethersms.mms.pdu

/**
 * Chaîne encodée avec son jeu de caractères, utilisée dans les adresses MMS.
 * Implémentation dérivée d'AOSP frameworks/opt/telephony — Apache 2.0.
 */
data class EncodedStringValue(
    /** Charset IANA (UTF-8 = 106 = 0x6A) */
    val charset: Int = 0x6A,
    /** Texte sous forme de bytes (selon le charset) */
    val textString: ByteArray,
) {
    constructor(text: String) : this(
        charset    = 0x6A,
        textString = text.toByteArray(Charsets.UTF_8),
    )

    /** Représentation lisible */
    val string: String get() = when (charset) {
        0x6A, 106 -> textString.toString(Charsets.UTF_8)
        0x03, 3   -> textString.toString(Charsets.US_ASCII)
        else      -> textString.toString(Charsets.UTF_8)
    }

    override fun equals(other: Any?): Boolean =
        other is EncodedStringValue && charset == other.charset && textString.contentEquals(other.textString)

    override fun hashCode(): Int = 31 * charset + textString.contentHashCode()
}
