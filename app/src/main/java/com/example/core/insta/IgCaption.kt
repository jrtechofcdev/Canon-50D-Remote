package com.example.core.insta

import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Caption suggestion made from text models, on the phone and free. No AI service, so it never invents
 * a fact: only what the app knows (event, church, date, the moments in the carousel) and what the
 * photographer typed for this event (theme, verse, preacher, worship) goes in. A line whose value is
 * empty is left out, so a model works with as much or as little as was filled.
 *
 * The models follow how church pages write their posts: the night and the date, the event, the theme
 * and the verse, who ministered, a word of thanks and the photo credit.
 *
 * Placeholders: {evento} {igreja} {data} {dia} (sábado) {periodo} (manhã, tarde, noite) {tema}
 * {versiculo} {pregador} {louvor} {momentos} {credito} {hashtags}.
 */
object IgCaption {

    val DEFAULT_TEMPLATES = listOf(
        "{evento}\n{dia}, {data} · {igreja}\n\n" +
            "Tema: {tema}\n{versiculo} 📖\n\n" +
            "{momentos}\nA Palavra foi ministrada por {pregador}.\nNo louvor: {louvor}.\n\n" +
            "Uma {periodo} abençoada pelo poder de Deus, do início ao fim. 🙌\n\nFotos: {credito}\n\n{hashtags}",
        "{evento}: momentos especiais 🙏\n{dia}, {data} · {igreja}\n\nFotos: {credito}\n\n{hashtags}",
        "Que {periodo} na presença do Senhor! 🙌\n{evento}, {data}.\n\n{momentos}\n\n{igreja}\nFotos: {credito}\n\n{hashtags}",
    )

    /** Separator between models when they are edited as one text in Ajustes. */
    const val SEPARATOR = "\n---\n"

    fun templates(saved: String?): List<String> =
        saved?.split(SEPARATOR)?.map { it.trim() }?.filter { it.isNotEmpty() }?.ifEmpty { null } ?: DEFAULT_TEMPLATES

    /** What the photographer types for an event; all optional. */
    data class Notes(
        val theme: String = "",
        val verse: String = "",
        val preacher: String = "",
        val worship: String = "",
    ) {
        fun encode(): String = listOf(theme, verse, preacher, worship).joinToString("\n") { it.replace('\n', ' ').trim() }

        companion object {
            fun decode(text: String?): Notes {
                val p = text?.split('\n').orEmpty()
                return Notes(p.getOrElse(0) { "" }, p.getOrElse(1) { "" }, p.getOrElse(2) { "" }, p.getOrElse(3) { "" })
            }
        }
    }

    data class Facts(
        val event: String,
        /** As the page writes it: "@iead_estrela_de_luz" or "Estrela de Luz C-10". */
        val church: String?,
        /** Time of the first photo of the carousel, or the event date. */
        val at: Long,
        val moments: List<IgMoment>,
        val notes: Notes = Notes(),
        val credit: String = "",
        val hashtags: String = "",
        val zone: ZoneId = ZoneId.systemDefault(),
    )

    /** The [index]-th suggestion (it wraps), so "Outra sugestão" walks through the models. */
    fun suggest(facts: Facts, index: Int = 0, templates: List<String> = DEFAULT_TEMPLATES): String {
        val t = templates[Math.floorMod(index, templates.size)]
        val time = Instant.ofEpochMilli(facts.at).atZone(facts.zone)
        val pt = Locale.forLanguageTag("pt-BR")
        val values = mapOf(
            "evento" to facts.event.trim(),
            "igreja" to facts.church?.trim().orEmpty(),
            "data" to time.format(DateTimeFormatter.ofPattern("d 'de' MMMM", pt)),
            "dia" to time.format(DateTimeFormatter.ofPattern("EEEE", pt)),
            "periodo" to when (time.hour) { in 5..11 -> "manhã"; in 12..17 -> "tarde"; else -> "noite" },
            "tema" to facts.notes.theme.trim(),
            "versiculo" to facts.notes.verse.trim(),
            "pregador" to facts.notes.preacher.trim(),
            "louvor" to facts.notes.worship.trim(),
            "momentos" to momentsLine(facts.moments),
            "credito" to facts.credit.trim(),
            "hashtags" to facts.hashtags.trim().ifEmpty { hashtag(facts.church) },
        )
        val lines = t.split('\n').mapNotNull { line ->
            val keys = PLACEHOLDER.findAll(line).map { it.groupValues[1] }.toList()
            // A line built around a value that is missing says nothing: drop it whole.
            if (keys.any { values[it].isNullOrEmpty() } && keys.all { it !in OPTIONAL_INLINE }) return@mapNotNull null
            var out = line
            keys.forEach { k -> out = out.replace("{$k}", values[k].orEmpty()) }
            // "sábado, 22 de novembro · " when the church is not set.
            out.trimEnd().removeSuffix("·").trimEnd().replaceFirstChar { it.uppercase() }
        }
        return tidy(lines.joinToString("\n"))
    }

    private val PLACEHOLDER = Regex("\\{([a-z]+)\\}")

    /** Values that may be empty without losing the line (the rest of the sentence still reads). */
    private val OPTIONAL_INLINE = setOf("igreja")

    /** "Momentos de louvor, oração e Palavra." from what the carousel shows; empty when nothing was recognised. */
    fun momentsLine(moments: List<IgMoment>): String {
        val words = buildList {
            if (IgMoment.WORSHIP in moments) add("louvor")
            if (IgMoment.PRAYER in moments) add("oração")
            if (IgMoment.MINISTRY in moments) add("Palavra")
            if (IgMoment.BAPTISM in moments) add("batismo")
            if (IgMoment.CHILDREN in moments) add("as crianças")
            if (IgMoment.GROUP in moments || IgMoment.PEOPLE in moments || IgMoment.CONGREGATION in moments) add("comunhão")
        }
        if (words.isEmpty()) return ""
        val list = if (words.size == 1) words[0] else words.dropLast(1).joinToString(", ") + " e " + words.last()
        return "Momentos de $list."
    }

    /** "#iead_estrela_de_luz" from "@iead_estrela_de_luz", "#estreladeluz" from "Estrela de Luz"; empty without a name. */
    fun hashtag(church: String?): String {
        val name = church.orEmpty().trim()
        if (name.startsWith("@")) return "#" + name.drop(1).lowercase(Locale.ROOT).filter { it.isLetterOrDigit() || it == '_' }.ifEmpty { return "" }
        val plain = Normalizer.normalize(name, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        val tag = plain.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
        return if (tag.isEmpty()) "" else "#$tag"
    }

    /** No runs of blank lines, nothing hanging at the ends. */
    private fun tidy(text: String): String = text
        .replace(Regex(" {2,}"), " ")
        .replace(Regex("\n{3,}"), "\n\n")
        .replace(" ,", ",")
        .trim()
}
