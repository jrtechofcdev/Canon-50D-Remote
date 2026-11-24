package com.example.core.video

import com.example.core.log.AppLog

/** Video lines in the app log, tagged VIDEO-<step> (card search, download, gallery, player). */
object VideoLog {
    fun i(step: String, message: String) = AppLog.i("VIDEO-$step", message)

    fun w(step: String, message: String, error: Throwable? = null) = AppLog.w("VIDEO-$step", message, error)

    fun e(step: String, message: String, error: Throwable? = null) = AppLog.e("VIDEO-$step", message, error)

    /** Memory and free space, added to the lines of a big download. */
    fun resources(freeBytes: Long?): String =
        AppLog.memory() + (freeBytes?.let { " · livre no celular %.1f GB".format(it / 1_073_741_824.0) } ?: "")
}
