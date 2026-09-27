package com.example.core.ptp

/**
 * PTP and Canon EOS codes used by the app.
 * Values follow the PTP (ISO 15740) spec and the Canon EOS extension as documented by libgphoto2 (ptp.h).
 */
object PtpOp {
    const val GET_DEVICE_INFO = 0x1001
    const val OPEN_SESSION = 0x1002
    const val CLOSE_SESSION = 0x1003
    const val GET_OBJECT_INFO = 0x1008
    const val GET_OBJECT = 0x1009

    const val EOS_SET_DEVICE_PROP_VALUE_EX = 0x9110
    const val EOS_REMOTE_RELEASE = 0x910F
    const val EOS_SET_REMOTE_MODE = 0x9114
    const val EOS_SET_EVENT_MODE = 0x9115
    const val EOS_GET_EVENT = 0x9116
    const val EOS_TRANSFER_COMPLETE = 0x9117
    const val EOS_KEEP_DEVICE_ON = 0x911D
    const val EOS_REMOTE_RELEASE_ON = 0x9128
    const val EOS_REMOTE_RELEASE_OFF = 0x9129
}

object PtpResponse {
    const val OK = 0x2001
    const val GENERAL_ERROR = 0x2002
    const val SESSION_NOT_OPEN = 0x2003
    const val OPERATION_NOT_SUPPORTED = 0x2005
    const val DEVICE_BUSY = 0x2019
    const val SESSION_ALREADY_OPEN = 0x201E

    fun name(code: Int): String = when (code) {
        OK -> "OK"
        GENERAL_ERROR -> "Erro geral"
        SESSION_NOT_OPEN -> "Sessão não aberta"
        OPERATION_NOT_SUPPORTED -> "Operação não suportada"
        DEVICE_BUSY -> "Câmera ocupada"
        SESSION_ALREADY_OPEN -> "Sessão já aberta"
        0xA102 -> "Câmera não conseguiu disparar (foco?)"
        else -> "0x%04X".format(code)
    }
}

object EosEvent {
    const val OBJECT_ADDED_EX = 0xC181
    const val PROP_VALUE_CHANGED = 0xC189
    const val AVAIL_LIST_CHANGED = 0xC18A
}

object EosProp {
    const val APERTURE = 0xD101
    const val SHUTTER_SPEED = 0xD102
    const val ISO = 0xD103
    const val EXPOSURE_COMPENSATION = 0xD104
    const val SHOOTING_MODE = 0xD105
    const val WHITE_BALANCE = 0xD109
    const val AVAILABLE_SHOTS = 0xD11B
    const val CAPTURE_DESTINATION = 0xD11C
    const val BATTERY_POWER = 0xD111

    /** Capture destination values: the card keeps a backup, the phone gets a copy via ObjectAddedEx. */
    const val DESTINATION_CARD = 2
}

object PtpFormat {
    const val ASSOCIATION = 0x3001 // folder
    const val EXIF_JPEG = 0x3801
    const val CANON_CRW = 0xB101
    const val CANON_CR2 = 0xB103
}
