package com.example.core.insta

import java.util.Locale

/**
 * Reading Instagram's "Baixar suas informações" export: which files are the photos of the posts,
 * and what the captions teach (the hashtags the page always uses and who signs the photos).
 *
 * The export comes as HTML or JSON, with the photos in `media/posts/` (sometimes in month folders)
 * as JPG, WEBP or HEIC. Stories, reels and the profile photo are left out: the feed is what counts.
 */
object IgExport {

    private val IMAGE = setOf("jpg", "jpeg", "webp", "heic", "heif", "png")

    /** True for a photo of a post inside the export. */
    fun isPostImage(path: String): Boolean {
        val p = path.replace('\\', '/').lowercase(Locale.ROOT)
        val ext = p.substringAfterLast('.', "")
        return ext in IMAGE && ("/media/posts/" in "/$p") && "__macosx" !in p
    }

    /** True for the file that lists the posts with their captions (HTML or JSON). */
    fun isPostList(path: String): Boolean {
        val name = path.replace('\\', '/').lowercase(Locale.ROOT)
        val file = name.substringAfterLast('/')
        return (file.startsWith("posts_") || file == "posts.html" || file == "posts.json") &&
            (file.endsWith(".html") || file.endsWith(".json")) && ("/media/" in "/$name" || "/content/" in "/$name")
    }

    /** What the captions show about how the page writes. */
    data class Voice(val hashtags: List<String>, val credit: String?) {
        fun encode() = "hashtags=${hashtags.joinToString(" ")}\ncredit=${credit.orEmpty()}\n"

        companion object {
            fun decode(text: String?): Voice? {
                val map = text?.lineSequence()?.filter { '=' in it }?.associate { it.substringBefore('=') to it.substringAfter('=') } ?: return null
                return Voice(map["hashtags"].orEmpty().split(' ').filter { it.startsWith("#") }, map["credit"]?.takeIf { it.isNotBlank() })
            }
        }
    }

    private val HASHTAG = Regex("#\\p{L}[\\p{L}\\p{N}_]{1,40}")
    private val CREDIT = Regex("(?i)fotos?\\s*(?:por)?\\s*:\\s*(@[A-Za-z0-9._]{2,30})")
    private val TAG = Regex("<[^>]+>")
    private val BLOCKS = Regex("(?is)<(style|script)[^>]*>.*?</\\1>")

    /**
     * The hashtags used in at least two posts (most used first, at most [max]) and the most frequent
     * "Fotos: @alguém". [texts] are the post list files, HTML or JSON, as they are.
     */
    fun voice(texts: List<String>, max: Int = 6): Voice {
        // The style sheet of the HTML export is full of "#fff" colours: it goes first.
        val plain = texts.map { decodeEscapes(TAG.replace(BLOCKS.replace(it, " "), "\n")) }
        val used = plain.flatMap { HASHTAG.findAll(it).map { m -> m.value } }
        // "#AssembléiadeDeus" and "#assembleiadedeus" are the same tag typed twice.
        val tags = used.groupBy { fold(it) }
            .filter { it.value.size >= 2 }.entries.sortedByDescending { it.value.size }.take(max)
            .map { e -> e.value.groupingBy { it }.eachCount().maxBy { it.value }.key } // the spelling used most
        val credit = plain.flatMap { CREDIT.findAll(it).map { m -> m.groupValues[1] } }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        return Voice(tags, credit)
    }

    private fun fold(tag: String) =
        java.text.Normalizer.normalize(tag, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

    /**
     * HTML entities and the unicode escapes of the JSON export back to letters. The JSON writes each
     * UTF-8 byte as its own escape ("Ã©" for "é"), so those bytes are put back together.
     */
    private fun decodeEscapes(s: String): String {
        val text = s.replace(Regex("&#(\\d{2,6});")) { it.groupValues[1].toInt().toChar().toString() }
            .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
            .replace(Regex("\\\\u([0-9a-fA-F]{4})")) { it.groupValues[1].toInt(16).toChar().toString() }
        val mojibake = ("Ã" in text || "â€" in text) && text.all { it.code <= 0xFF }
        return if (mojibake) String(text.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8) else text
    }
}
