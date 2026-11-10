package com.example.core.tether

import java.io.FileNotFoundException

/** Why a photo could not be saved, in words the photographer can act on. */
enum class SaveReason(val title: String, val action: String) {
    NoSpace("Celular sem espaço", "Libere espaço ou escolha uma pasta no cartão SD em Ajustes → Salvamento."),
    NoPermission("A pasta não aceita mais gravar", "Escolha a pasta de novo em Ajustes → Salvamento."),
    FolderGone("A pasta sumiu", "O cartão foi removido ou a pasta foi apagada. Escolha de novo em Ajustes → Salvamento."),
    Other("Não deu para salvar", "Veja o motivo em Ajustes → Diagnóstico. As fotos continuam no cartão da câmera."),
    ;

    companion object {
        /** Reads the cause chain: Android wraps "no space" (ENOSPC) in IOExceptions of several kinds. */
        fun of(error: Throwable): SaveReason {
            val chain = generateSequence(error) { it.cause?.takeIf { c -> c !== it } }.take(6).toList()
            val text = chain.joinToString(" ") { "${it::class.java.simpleName} ${it.message.orEmpty()}" }.lowercase()
            return when {
                "enospc" in text || "no space" in text || "espaço" in text -> NoSpace
                chain.any { it is SecurityException } || "permission" in text || "eacces" in text -> NoPermission
                chain.any { it is FileNotFoundException } || "enoent" in text || "not found" in text -> FolderGone
                else -> Other
            }
        }
    }
}

/** The save failure the Session keeps on screen until a photo saves again or it is dismissed. */
data class SaveProblem(val reason: SaveReason, val lastFile: String, val count: Int)
