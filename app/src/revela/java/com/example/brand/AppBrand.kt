package com.example.brand

/**
 * Who this app is: Revela, tethered capture for any photographer and any PTP camera. The studio's own
 * name, logo and colour come from "Sua marca" (Settings); these are only the app's defaults.
 */
object AppBrand {
    const val IS_REVELA = true
    /** Name on screen, in the log, in backups, EXIF and XMP. */
    const val APP_NAME = "Revela"
    /** Folder created at the root of the phone. */
    const val ROOT_FOLDER = "Revela"
    /** Studio name until the photographer sets theirs in "Sua marca". */
    const val DEFAULT_STUDIO = "Fotografia"
    /** The studio signs its own messages; the app adds none. */
    const val SIGNATURE = ""
    const val TAG_LINE = ""
    /** Line under the name in Settings → About. */
    const val ABOUT = "Captura vinculada para fotógrafos"
}
