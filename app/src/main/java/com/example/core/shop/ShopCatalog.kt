package com.example.core.shop

import org.json.JSONArray
import org.json.JSONObject

/**
 * One preset offered by the catalogue API. [url] points at the DNG (or XMP); [fileName] is kept as it
 * is on the server so a download matches the files already on the photographer's computer.
 */
data class ShopItem(
    val id: Long,
    val fileName: String,
    val category: String,
    val url: String,
    val createdAt: String = "",
) {
    /** Name shown in the app and given to the preset when saved. */
    val name: String get() = ShopCatalog.displayName(fileName, category)

    /** Category in Portuguese, as the chips and the saved preset's group show it. */
    val categoryLabel: String get() = ShopCatalog.categoryLabel(category)

    val isDng: Boolean get() = fileName.endsWith(".dng", ignoreCase = true)

    /** File name without the extension, shared by the downloaded .dng and .xmp. */
    val baseName: String get() = fileName.substringBeforeLast('.').replace(Regex("[\\\\/:*?\"<>|]"), "-").trim().ifBlank { "Preset $id" }

    /** Written in [com.example.core.preset.Preset.source], so the shop knows what is already saved. */
    val source: String get() = "$SOURCE_PREFIX$id"

    companion object {
        const val SOURCE_PREFIX = "loja:"
    }
}

/**
 * Reads the catalogue JSON. The API answers a plain list:
 * `[{"id":1085,"filename":"presets_id_692.dng","category":"Winter","downloadUrl":"https://…"}]`.
 * Other shapes a future version might use are accepted too: the list under "presets"/"data"/"items",
 * and "name"/"download_url"/"url" as field names. Entries that aren't DNG or XMP (sample JPGs) are skipped.
 */
object ShopCatalog {
    private const val BOM = '\uFEFF'

    fun parse(json: String): List<ShopItem> {
        val text = json.trim().trimStart(BOM)
        val array = when {
            text.startsWith("[") -> JSONArray(text)
            text.startsWith("{") -> JSONObject(text).let { o ->
                o.optJSONArray("presets") ?: o.optJSONArray("data") ?: o.optJSONArray("items") ?: JSONArray()
            }
            else -> throw IllegalArgumentException("A API não respondeu uma lista de presets")
        }
        val out = ArrayList<ShopItem>(array.length())
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val file = o.optString("filename").ifBlank { o.optString("name") }.trim()
            val url = o.optString("downloadUrl").ifBlank { o.optString("download_url") }.ifBlank { o.optString("url") }.trim()
            if (file.isEmpty() || !url.startsWith("http")) continue
            val ext = file.substringAfterLast('.', "").lowercase()
            if (ext != "dng" && ext != "xmp") continue
            out += ShopItem(
                id = o.optLong("id", (file + url).hashCode().toLong()),
                fileName = file,
                category = o.optString("category").trim().ifBlank { "Outros" },
                url = url.replace(" ", "%20"),
                createdAt = o.optString("created_at"),
            )
        }
        return out.distinctBy { it.id }
    }

    private val numbered = Regex("""^presets?[_ ]?id[_ ]?(\d+)$""", RegexOption.IGNORE_CASE)

    /** "presets_id_692.dng" in Winter → "Inverno 692"; "Amber A1.dng" → "Amber A1". */
    fun displayName(fileName: String, category: String): String {
        val base = fileName.substringBeforeLast('.').replace('_', ' ').trim()
        numbered.find(fileName.substringBeforeLast('.'))?.let { return "${categoryLabel(category)} ${it.groupValues[1]}" }
        return base.ifBlank { fileName }
    }

    private val labels = mapOf(
        "daily" to "Dia a dia",
        "moody" to "Moody",
        "cinematic" to "Cinematográfico",
        "urban" to "Urbano",
        "autumn" to "Outono",
        "portrait" to "Retrato",
        "orange & teal" to "Orange & Teal",
        "black & white" to "Preto e branco",
        "bright & airy" to "Claro e leve",
        "wedding" to "Casamento",
        "sky colour" to "Céu",
        "sky color" to "Céu",
        "beach & tropical" to "Praia e tropical",
        "winter" to "Inverno",
        "summer" to "Verão",
        "spring" to "Primavera",
        "vintage" to "Vintage",
        "food" to "Comida",
        "travel" to "Viagem",
        "night" to "Noite",
    )

    fun categoryLabel(category: String): String = labels[category.trim().lowercase()] ?: category.trim()
}
