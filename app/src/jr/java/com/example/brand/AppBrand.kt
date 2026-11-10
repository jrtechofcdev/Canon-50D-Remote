package com.example.brand

/**
 * Who this app is: JR C50D Sync, the Jr_Tech_OFC app for the Canon 50D. The Revela variant has its own
 * AppBrand with the same fields (src/revela); shared code reads the name and texts only from here.
 */
object AppBrand {
    const val IS_REVELA = false
    /** Name on screen, in the log, in backups, EXIF and XMP. */
    const val APP_NAME = "JR C50D Sync"
    /** Folder created at the root of the phone. */
    const val ROOT_FOLDER = "JR C50D Sync"
    /** Studio name until the photographer changes it (pages, previews, messages). */
    const val DEFAULT_STUDIO = "Jr_Tech_OFC"
    /** Closing line of the ready-made WhatsApp messages. */
    const val SIGNATURE = "\n\n*Jr_Tech_OFC*"
    /** The "tag us" line of the delivery message. */
    const val TAG_LINE = "\n\nSe puder, marque *@jr_tech_ofc* quando postar."
    /** Line under the name in Ajustes → Sobre. */
    const val ABOUT = "por Jr_Tech_OFC · Nossa Arte, Seu Sucesso"
}
