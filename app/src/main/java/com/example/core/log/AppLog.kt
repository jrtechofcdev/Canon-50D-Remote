package com.example.core.log

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Debug
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The app's diary, kept in a file on the phone so it survives restarts and crashes: one file per day
 * in the app's folder, copied every few seconds to the folder picked in Ajustes → Diagnóstico.
 *
 * - Routine lines (connection, downloads, memory per photo, screens) only when [enabled].
 * - Errors, crashes and the reason the app last closed are always written: they cost nothing until
 *   something goes wrong, and they are what is needed to fix it.
 * - A crash is written synchronously before the process dies; how the previous run ended (crash,
 *   native crash, out of memory, killed by Android) is read back on the next start.
 */
object AppLog {
    @Volatile var enabled: Boolean = true
        private set
    /** Modo desenvolvedor: every camera operation goes to the log too. */
    @Volatile var verbose: Boolean = false
    @Volatile private var folder: Uri? = null
    @Volatile private var dirty = false
    private var context: Context? = null

    private val queue = Channel<String>(capacity = 4_000, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fileLock = Any()

    private val _recent = MutableStateFlow<List<String>>(emptyList())
    /** The last lines, for the Diagnóstico screen. */
    val recent: StateFlow<List<String>> = _recent.asStateFlow()

    private val _lastExit = MutableStateFlow<String?>(null)
    /** How the previous run ended, when it was not a normal close. */
    val lastExit: StateFlow<String?> = _lastExit.asStateFlow()

    private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)
    private val day = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

    fun init(appContext: Context) {
        if (context != null) return
        context = appContext.applicationContext
        installCrashHandler()
        scope.launch { writer() }
        scope.launch { mirrorLoop() }
        scope.launch { cleanOld() }
        always("APP", "Início · JR C50D Sync ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${device()} · ${memory()}")
        scope.launch { readLastExit() }
    }

    /** From the settings: routine logging on/off and the folder the file is copied to (null = only in the app). */
    fun configure(on: Boolean, folderUri: String?) {
        if (on != enabled) always("LOG", if (on) "Log completo ligado" else "Log completo desligado (só erros e fechamentos)")
        enabled = on
        folder = folderUri?.let(Uri::parse)
        dirty = true
    }

    fun i(tag: String, message: String) {
        if (enabled) add("I", tag, message)
    }

    fun w(tag: String, message: String, error: Throwable? = null) {
        if (enabled) add("W", tag, message + (error?.let { "\n" + stack(it) } ?: ""))
    }

    /** Errors are written even with the full log off. */
    fun e(tag: String, message: String, error: Throwable? = null) = add("E", tag, message + (error?.let { "\n" + stack(it) } ?: ""))

    private fun always(tag: String, message: String) = add("I", tag, message)

    /** For coroutine scopes that must never take the app down: the failure is written and the app carries on. */
    fun handler(tag: String) = CoroutineExceptionHandler { _, t -> e(tag, "Erro não tratado (o app continuou)", t) }

    /** Heap in use / limit, and native memory, e.g. "memória 182/512 MB, nativa 96 MB". */
    fun memory(): String {
        val rt = Runtime.getRuntime()
        val used = (rt.totalMemory() - rt.freeMemory()) / MB
        return "memória ${used}/${rt.maxMemory() / MB} MB, nativa ${Debug.getNativeHeapAllocatedSize() / MB} MB"
    }

    fun dir(): File = File(requireNotNull(context).filesDir, "logs").apply { mkdirs() }

    fun todayFile(): File = File(dir(), "JR_C50D_Sync_log_${day.format(Date())}.txt")

    /** Every log file, newest first. */
    fun files(): List<File> = dir().listFiles { f -> f.name.endsWith(".txt") }.orEmpty().sortedByDescending { it.name }

    /** The last [days] files in one text file, to send in one go. */
    fun bundle(days: Int = 3): File {
        flushNow()
        val out = File(File(requireNotNull(context).cacheDir, "export").apply { mkdirs() }, "JR_C50D_Sync_log.txt")
        out.bufferedWriter().use { w ->
            w.write("JR C50D Sync ${BuildConfig.VERSION_NAME} · ${device()}\n")
            _lastExit.value?.let { w.write("Última saída anormal: $it\n") }
            files().take(days).reversed().forEach { f ->
                w.write("\n===== ${f.name} =====\n")
                f.bufferedReader().use { it.copyTo(w) }
            }
        }
        return out
    }

    fun clear() {
        synchronized(fileLock) { files().forEach { it.delete() } }
        _recent.value = emptyList()
        always("LOG", "Logs apagados")
    }

    // ---- writing ------------------------------------------------------------------------------

    private fun add(level: String, tag: String, message: String) {
        val line = "${time.format(Date())} $level/$tag: $message"
        Log.println(if (level == "E") Log.ERROR else if (level == "W") Log.WARN else Log.INFO, "JRC50D/$tag", message)
        _recent.update { (it + line).takeLast(RECENT) }
        queue.trySend(line)
    }

    private suspend fun writer() {
        for (first in queue) {
            val batch = StringBuilder(first).append('\n')
            // Everything waiting goes in one write: cheap even when lines come in bursts.
            while (true) {
                val next = queue.tryReceive().getOrNull() ?: break
                batch.append(next).append('\n')
            }
            append(batch.toString())
        }
    }

    private fun append(text: String) {
        if (context == null) return
        synchronized(fileLock) {
            runCatching {
                val file = todayFile()
                if (file.length() > MAX_FILE) file.renameTo(File(file.parentFile, file.name.replace(".txt", "_${System.currentTimeMillis()}.txt")))
                file.appendText(text)
                dirty = true
            }
        }
    }

    /** Writes whatever is still queued, right now, on this thread. */
    private fun flushNow() {
        val batch = StringBuilder()
        while (true) {
            val next = queue.tryReceive().getOrNull() ?: break
            batch.append(next).append('\n')
        }
        if (batch.isNotEmpty()) append(batch.toString())
    }

    private suspend fun mirrorLoop() {
        while (true) {
            delay(MIRROR_MS)
            if (dirty) mirror()
        }
    }

    /** Copies today's file to the picked folder (Downloads, SD card, Drive…). */
    private fun mirror() {
        val ctx = context ?: return
        val target = folder ?: return
        dirty = false
        runCatching {
            val source = todayFile()
            if (!source.exists()) return
            val tree = DocumentFile.fromTreeUri(ctx, target) ?: return
            val doc = tree.findFile(source.name) ?: tree.createFile("text/plain", source.name) ?: return
            ctx.contentResolver.openOutputStream(doc.uri, "wt")?.use { out -> synchronized(fileLock) { source.inputStream().use { it.copyTo(out) } } }
        }.onFailure { Log.w("JRC50D/LOG", "Não deu para copiar o log para a pasta escolhida", it) }
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                flushNow()
                append(
                    "${time.format(Date())} E/CRASH: O app fechou por um erro na linha \"${thread.name}\" · ${memory()}\n" +
                        stack(error) + "\n",
                )
                mirror()
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /**
     * Android keeps why the last runs ended. A crash in native code (graphics), running out of memory
     * or being killed in the background never reach the crash handler, so they are read here.
     */
    private fun readLastExit() {
        val ctx = context ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            val prefs = ctx.getSharedPreferences("app_log", Context.MODE_PRIVATE)
            val seen = prefs.getLong("last_exit", 0L)
            val am = ctx.getSystemService(ActivityManager::class.java)
            val exits = am.getHistoricalProcessExitReasons(ctx.packageName, 0, 5).filter { it.timestamp > seen }
            exits.sortedBy { it.timestamp }.forEach { info ->
                val reason = reasonName(info.reason)
                val text = "$reason em ${SimpleDateFormat("dd/MM HH:mm:ss", Locale.ROOT).format(Date(info.timestamp))}" +
                    (info.description?.let { " · $it" } ?: "") + " · memória usada ${info.pss / 1024} MB"
                val abnormal = info.reason !in NORMAL
                if (abnormal) {
                    _lastExit.value = text
                    val trace = if (info.reason == ApplicationExitInfo.REASON_CRASH_NATIVE || info.reason == ApplicationExitInfo.REASON_ANR) {
                        runCatching { info.traceInputStream?.bufferedReader()?.use { r -> r.lineSequence().take(120).joinToString("\n") } }.getOrNull()
                    } else null
                    e("SAIDA", "A execução anterior terminou: $text" + (trace?.let { "\n$it" } ?: ""))
                } else {
                    always("SAIDA", "Execução anterior: $text")
                }
            }
            exits.maxOfOrNull { it.timestamp }?.let { prefs.edit().putLong("last_exit", it).apply() }
        }
    }

    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> "Fechou por erro do app"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "Fechou por erro nativo (gráficos/memória)"
        ApplicationExitInfo.REASON_ANR -> "Travou e o Android fechou (ANR)"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "Android fechou por falta de memória"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "Android fechou por uso excessivo"
        ApplicationExitInfo.REASON_SIGNALED -> "Encerrado pelo sistema"
        ApplicationExitInfo.REASON_EXIT_SELF -> "Fechado normalmente"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "Fechado pelo usuário"
        ApplicationExitInfo.REASON_USER_STOPPED -> "Parado nas configurações"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "Permissão alterada"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "Dependência encerrada"
        else -> "Outro motivo ($reason)"
    }

    private fun cleanOld() {
        val cutoff = System.currentTimeMillis() - KEEP_DAYS * 24 * 3600 * 1000L
        files().filter { it.lastModified() < cutoff }.forEach { it.delete() }
    }

    private fun device() = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    private fun stack(t: Throwable): String = StringWriter().also { t.printStackTrace(PrintWriter(it)) }.toString().trimEnd()

    private const val MB = 1024L * 1024L
    private const val RECENT = 400
    private const val MAX_FILE = 8L * 1024 * 1024
    private const val MIRROR_MS = 15_000L
    private const val KEEP_DAYS = 10
    private val NORMAL = setOf(
        ApplicationExitInfo.REASON_EXIT_SELF,
        ApplicationExitInfo.REASON_USER_REQUESTED,
        ApplicationExitInfo.REASON_USER_STOPPED,
        ApplicationExitInfo.REASON_PERMISSION_CHANGE,
        ApplicationExitInfo.REASON_PACKAGE_UPDATED,
        ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE,
    )
}
