package com.aethersms.mms.pdu

/**
 * Constantes des champs PDU MMS selon la spécification OMA MMS 1.2.
 * Source : OMA-MMS-ENC-V1_2 + AOSP frameworks/opt/telephony
 * Licence : Apache 2.0
 */
object PduHeaders {
    // ── Codes des champs d'en-tête (0x80 = bit de codage court) ────────────
    const val BCC                    = 0x01
    const val CC                     = 0x02
    const val CONTENT_LOCATION       = 0x03
    const val CONTENT_TYPE           = 0x04
    const val DATE                   = 0x05
    const val DELIVERY_REPORT        = 0x06
    const val DELIVERY_TIME          = 0x07
    const val EXPIRY                 = 0x08
    const val FROM                   = 0x09
    const val MESSAGE_CLASS          = 0x0A
    const val MESSAGE_ID             = 0x0B
    const val MESSAGE_TYPE           = 0x0C
    const val MMS_VERSION            = 0x0D
    const val MESSAGE_SIZE           = 0x0E
    const val PRIORITY               = 0x0F
    const val READ_REPLY             = 0x10
    const val REPORT_ALLOWED         = 0x11
    const val RESPONSE_STATUS        = 0x12
    const val RESPONSE_TEXT          = 0x13
    const val SENDER_VISIBILITY      = 0x14
    const val STATUS                 = 0x15
    const val SUBJECT                = 0x16
    const val TO                     = 0x17
    const val TRANSACTION_ID         = 0x18
    const val RETRIEVE_STATUS        = 0x19
    const val RETRIEVE_TEXT          = 0x1A
    const val READ_STATUS            = 0x1B
    const val REPLY_CHARGING         = 0x1C
    const val REPLY_CHARGING_DEADLINE= 0x1D
    const val REPLY_CHARGING_ID      = 0x1E
    const val REPLY_CHARGING_SIZE    = 0x1F
    const val PREVIOUSLY_SENT_BY     = 0x20
    const val PREVIOUSLY_SENT_DATE   = 0x21
    const val STORE                  = 0x22
    const val MM_STATE               = 0x23
    const val MM_FLAGS               = 0x24
    const val STORE_STATUS           = 0x25
    const val STORE_STATUS_TEXT      = 0x26
    const val STORED                 = 0x27
    const val ATTRIBUTES             = 0x28
    const val TOTALS                 = 0x29
    const val MBOX_TOTALS            = 0x2A
    const val QUOTAS                 = 0x2B
    const val MBOX_QUOTAS            = 0x2C
    const val MESSAGE_COUNT          = 0x2D
    const val CONTENT                = 0x2E
    const val START                  = 0x2F
    const val ADDITIONAL_HEADERS     = 0x30
    const val DISTRIBUTION_INDICATOR = 0x31
    const val ELEMENT_DESCRIPTOR     = 0x32
    const val LIMIT                  = 0x33
    const val RECOMMENDED_RETRIEVAL_MODE        = 0x34
    const val RECOMMENDED_RETRIEVAL_MODE_TEXT   = 0x35
    const val STATUS_TEXT            = 0x36
    const val APPLIC_ID              = 0x37
    const val REPLY_APPLIC_ID        = 0x38
    const val AUX_APPLIC_ID          = 0x39
    const val CONTENT_CLASS          = 0x3A
    const val DRM_CONTENT            = 0x3B
    const val ADAPTATION_ALLOWED     = 0x3C
    const val REPLACE_ID             = 0x3D
    const val CANCEL_ID              = 0x3E
    const val CANCEL_STATUS          = 0x3F

    // ── Types de messages ───────────────────────────────────────────────────
    const val MESSAGE_TYPE_SEND_REQ          = 0x80
    const val MESSAGE_TYPE_SEND_CONF         = 0x81
    const val MESSAGE_TYPE_NOTIFICATION_IND  = 0x82
    const val MESSAGE_TYPE_NOTIFYRESP_IND    = 0x83
    const val MESSAGE_TYPE_RETRIEVE_CONF     = 0x84
    const val MESSAGE_TYPE_ACKNOWLEDGE_IND   = 0x85
    const val MESSAGE_TYPE_DELIVERY_IND      = 0x86
    const val MESSAGE_TYPE_READ_REC_IND      = 0x87
    const val MESSAGE_TYPE_READ_ORIG_IND     = 0x88
    const val MESSAGE_TYPE_FORWARD_REQ       = 0x89
    const val MESSAGE_TYPE_FORWARD_CONF      = 0x8A
    const val MESSAGE_TYPE_MBOX_STORE_REQ    = 0x8B
    const val MESSAGE_TYPE_MBOX_STORE_CONF   = 0x8C
    const val MESSAGE_TYPE_MBOX_VIEW_REQ     = 0x8D
    const val MESSAGE_TYPE_MBOX_VIEW_CONF    = 0x8E
    const val MESSAGE_TYPE_MBOX_UPLOAD_REQ   = 0x8F
    const val MESSAGE_TYPE_MBOX_UPLOAD_CONF  = 0x90
    const val MESSAGE_TYPE_MBOX_DELETE_REQ   = 0x91
    const val MESSAGE_TYPE_MBOX_DELETE_CONF  = 0x92
    const val MESSAGE_TYPE_MBOX_DESCR        = 0x93
    const val MESSAGE_TYPE_DELETE_REQ        = 0x94
    const val MESSAGE_TYPE_DELETE_CONF       = 0x95
    const val MESSAGE_TYPE_CANCEL_REQ        = 0x96
    const val MESSAGE_TYPE_CANCEL_CONF       = 0x97

    // ── Versions MMS ────────────────────────────────────────────────────────
    const val MMS_VERSION_1_0 = 0x90
    const val MMS_VERSION_1_1 = 0x91
    const val MMS_VERSION_1_2 = 0x92
    const val MMS_VERSION_1_3 = 0x93

    // ── Token "From: insert-address" ────────────────────────────────────────
    const val INSERT_ADDRESS_TOKEN = 0x81
    const val ADDRESS_PRESENT_TOKEN = 0x80

    // ── Priorités ────────────────────────────────────────────────────────────
    const val PRIORITY_LOW    = 0x81
    const val PRIORITY_NORMAL = 0x82
    const val PRIORITY_HIGH   = 0x83

    // ── Delivery-Report / Read-Reply ─────────────────────────────────────────
    const val VALUE_YES = 0x81
    const val VALUE_NO  = 0x82
}
