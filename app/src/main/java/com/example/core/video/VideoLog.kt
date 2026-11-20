package com.example.core.video

import com.example.core.log.AppLog

/**
 * The video module's side of the app log. Every line goes to the same daily file as the photos,
 * tagged VIDEO-<step> and written even with the full log off: the module is experimental and its
 * log is how a problem on the 50D gets diagnosed.
 */
object VideoLog {
    fun i(step: String, message: String) = AppLog.video("I", step, message)

    fun w(step: String, message: String, error: Throwable? = null) = AppLog.video("W", step, message, error)

    fun e(step: String, message: String, error: Throwable? = null) = AppLog.video("E", step, message, error)

    fun banner(text: String) = AppLog.videoBanner(text)

    /** Memory and free space, added to the lines that start or end something heavy. */
    fun resources(freeBytes: Long?): String =
        AppLog.memory() + (freeBytes?.let { " · livre no celular %.1f GB".format(it / 1_073_741_824.0) } ?: "")
}
