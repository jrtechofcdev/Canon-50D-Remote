package com.example.core.preset

import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.zip.ZipInputStream

class PresetFormatException(message: String) : Exception(message)

/**
 * Reads the `crs:` develop settings from an XMP packet, the format of .xmp presets and of the XMP
 * block embedded in DNG presets. Handles both attribute (`crs:Exposure2012="+0.50"`) and element
 * forms, plus the tone curves stored as `rdf:Seq` lists of "x, y".
 */
object XmpPresetParser {
    private val attribute = Regex("""crs:(\w+)\s*=\s*"([^"]*)"""")
    private val simpleElement = Regex("""<crs:(\w+)>([^<]*)</crs:\1>""")
    private val seqElement = Regex("""<crs:(\w+)>\s*<rdf:Seq>(.*?)</rdf:Seq>\s*</crs:\1>""", RegexOption.DOT_MATCHES_ALL)
    private val altElement = Regex("""<crs:(\w+)>\s*<rdf:Alt>(.*?)</rdf:Alt>\s*</crs:\1>""", RegexOption.DOT_MATCHES_ALL)
    private val listItem = Regex("""<rdf:li[^>]*>(.*?)</rdf:li>""", RegexOption.DOT_MATCHES_ALL)

    fun parse(xmp: String, fallbackName: String): Preset {
        if (!xmp.contains("crs:")) throw PresetFormatException("O arquivo não tem ajustes do Lightroom (crs)")
        val values = linkedMapOf<String, String>()
        val curves = linkedMapOf<String, List<CurvePoint>>()
        var name: String? = null
        var group: String? = null

        attribute.findAll(xmp).forEach { values[it.groupValues[1]] = unescape(it.groupValues[2]) }
        simpleElement.findAll(xmp).forEach { values[it.groupValues[1]] = unescape(it.groupValues[2].trim()) }
        seqElement.findAll(xmp).forEach { m ->
            val points = listItem.findAll(m.groupValues[2]).mapNotNull { point(it.groupValues[1]) }.toList()
            if (points.isNotEmpty()) curves[m.groupValues[1]] = points
        }
        altElement.findAll(xmp).forEach { m ->
            val text = listItem.find(m.groupValues[2])?.groupValues?.get(1)?.let(::unescape)?.trim()
            when (m.groupValues[1]) {
                "Name" -> name = text
                "Group" -> group = text
            }
        }
        // A curve given as a plain list attribute is not valid Adobe XMP; nothing else to collect.
        values.remove("Name"); values.remove("Group"); values.remove("Description")
        val settings = PresetSettings(values, curves)
        if (settings.adjustmentKeys.isEmpty() && curves.isEmpty()) {
            throw PresetFormatException("O preset não tem nenhum ajuste")
        }
        return Preset(
            name = name?.takeIf { it.isNotBlank() } ?: fallbackName,
            group = group?.takeIf { it.isNotBlank() } ?: "Importados",
            settings = settings,
            source = "xmp",
        )
    }

    private fun point(text: String): CurvePoint? {
        val parts = text.split(',').map { it.trim().toIntOrNull() }
        return if (parts.size >= 2 && parts[0] != null && parts[1] != null) CurvePoint(parts[0]!!, parts[1]!!) else null
    }

    private fun unescape(s: String) = s
        .replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&#xA;", "\n").replace("&amp;", "&")
}

/**
 * DNG presets are small photos whose develop settings sit in an XMP packet (TIFF tag 700, XMLPacket).
 * Walks every IFD, including SubIFDs, until it finds the packet.
 */
object DngPresetParser {
    private const val TAG_XMP = 700
    private const val TAG_SUB_IFDS = 330

    fun extractXmp(bytes: ByteArray): String {
        if (bytes.size < 8) throw PresetFormatException("Arquivo DNG vazio")
        val little = when {
            bytes[0] == 'I'.code.toByte() && bytes[1] == 'I'.code.toByte() -> true
            bytes[0] == 'M'.code.toByte() && bytes[1] == 'M'.code.toByte() -> false
            else -> throw PresetFormatException("Não é um arquivo DNG/TIFF")
        }
        val r = Reader(bytes, little)
        val pending = ArrayDeque<Long>().apply { add(r.u32(4)) }
        val seen = HashSet<Long>()
        while (pending.isNotEmpty()) {
            val ifd = pending.removeFirst()
            if (ifd <= 0 || ifd + 2 > bytes.size || !seen.add(ifd)) continue
            val count = r.u16(ifd.toInt())
            for (i in 0 until count) {
                val entry = ifd.toInt() + 2 + i * 12
                if (entry + 12 > bytes.size) break
                val tag = r.u16(entry)
                val type = r.u16(entry + 2)
                val n = r.u32(entry + 4)
                when (tag) {
                    TAG_XMP -> {
                        val offset = if (n <= 4) (entry + 8).toLong() else r.u32(entry + 8)
                        if (offset + n <= bytes.size) {
                            val packet = String(bytes, offset.toInt(), n.toInt(), Charsets.UTF_8)
                            if (packet.contains("crs:")) return packet
                        }
                    }
                    TAG_SUB_IFDS -> {
                        if (n == 1L) pending.add(r.u32(entry + 8))
                        else {
                            val base = r.u32(entry + 8).toInt()
                            for (k in 0 until n.toInt()) if (base + k * 4 + 4 <= bytes.size) pending.add(r.u32(base + k * 4))
                        }
                    }
                }
                if (type == 0) break
            }
            val next = ifd.toInt() + 2 + count * 12
            if (next + 4 <= bytes.size) pending.add(r.u32(next))
        }
        throw PresetFormatException("Este DNG não tem ajustes de preset")
    }

    private class Reader(val b: ByteArray, val little: Boolean) {
        fun u16(p: Int): Int = if (little) (b[p].toInt() and 0xFF) or ((b[p + 1].toInt() and 0xFF) shl 8)
        else ((b[p].toInt() and 0xFF) shl 8) or (b[p + 1].toInt() and 0xFF)

        fun u32(p: Int): Long = if (little) (u16(p).toLong() or (u16(p + 2).toLong() shl 16))
        else ((u16(p).toLong() shl 16) or u16(p + 2).toLong())
    }
}

/**
 * Lightroom Classic's older .lrtemplate: a Lua table. Only the `settings = { ... }` block and the
 * title matter; values are numbers, strings, booleans, or flat number lists for the tone curves.
 */
object LrTemplateParser {
    fun parse(text: String, fallbackName: String): Preset {
        val start = Regex("""settings\s*=\s*\{""").find(text)
            ?: throw PresetFormatException("O LRTemplate não tem bloco de ajustes (settings)")
        val body = balanced(text, start.range.last)
        val values = linkedMapOf<String, String>()
        val curves = linkedMapOf<String, List<CurvePoint>>()

        var i = 0
        val entry = Regex("""\G\s*(\w+)\s*=\s*""")
        while (i < body.length) {
            val m = entry.find(body, i)
            if (m == null) {
                // Skip anything we do not understand up to the next comma at this nesting level.
                i = skipValue(body, i) + 1
                continue
            }
            val key = m.groupValues[1]
            var p = m.range.last + 1
            if (p >= body.length) break
            when {
                body[p] == '{' -> {
                    val inner = balanced(body, p)
                    val numbers = Regex("""-?\d+(?:\.\d+)?""").findAll(inner).map { it.value.toDouble().toInt() }.toList()
                    if (key.startsWith("ToneCurve") && numbers.size >= 4) {
                        curves[key] = numbers.chunked(2).filter { it.size == 2 }.map { CurvePoint(it[0], it[1]) }
                    }
                    p += inner.length + 2
                }
                body[p] == '"' -> {
                    val end = body.indexOf('"', p + 1).let { if (it < 0) body.length else it }
                    values[key] = body.substring(p + 1, end)
                    p = end + 1
                }
                else -> {
                    val end = skipValue(body, p)
                    val raw = body.substring(p, end).trim()
                    val value = when (raw) {
                        "true" -> "True"
                        "false" -> "False"
                        else -> raw.removePrefix("ZSTR").trim().trim('"')
                    }
                    if (value.isNotEmpty()) values[key] = value
                    p = end
                }
            }
            i = body.indexOf(',', p).let { if (it < 0) body.length else it + 1 }
        }

        // title = "Name"  or  title = ZSTR "$$$/CRaw/Presets/Name=Name"
        val title = Regex("""title\s*=\s*(?:ZSTR\s*)?"([^"]*)"""").find(text)?.groupValues?.get(1)
            ?.let { if ('=' in it) it.substringAfterLast('=') else it }?.ifBlank { null }
            ?: Regex("""internalName\s*=\s*"([^"]*)"""").find(text)?.groupValues?.get(1)
        val settings = PresetSettings(values, curves)
        if (settings.adjustmentKeys.isEmpty() && curves.isEmpty()) throw PresetFormatException("O preset não tem nenhum ajuste")
        return Preset(name = title?.ifBlank { null } ?: fallbackName, group = "Importados", settings = settings, source = "lrtemplate")
    }

    /** Text between the brace at [open] and its matching close (exclusive). */
    private fun balanced(s: String, open: Int): String {
        var depth = 0
        var inString = false
        for (k in open until s.length) {
            val c = s[k]
            if (c == '"' && (k == 0 || s[k - 1] != '\\')) inString = !inString
            if (inString) continue
            if (c == '{') depth++
            if (c == '}') { depth--; if (depth == 0) return s.substring(open + 1, k) }
        }
        return s.substring(open + 1)
    }

    private fun skipValue(s: String, from: Int): Int {
        var depth = 0
        var inString = false
        for (k in from until s.length) {
            val c = s[k]
            if (c == '"') inString = !inString
            if (inString) continue
            if (c == '{') depth++
            if (c == '}') depth--
            if (c == ',' && depth <= 0) return k
        }
        return s.length
    }
}

/** Figures out what a picked file is and turns it into presets. A ZIP may hold many. */
object PresetImporter {
    fun import(fileName: String, bytes: ByteArray): List<Preset> {
        val base = fileName.substringAfterLast('/').substringBeforeLast('.')
        return when {
            isZip(bytes) -> readZip(bytes)
            isTiff(bytes) -> listOf(XmpPresetParser.parse(DngPresetParser.extractXmp(bytes), base).copy(source = "dng"))
            else -> {
                val text = String(bytes, Charsets.UTF_8)
                when {
                    text.contains("crs:") -> listOf(XmpPresetParser.parse(text, base))
                    Regex("""^\s*s\s*=\s*\{""").containsMatchIn(text) || text.contains("settings = {") ->
                        listOf(LrTemplateParser.parse(text, base))
                    else -> throw PresetFormatException("Formato não reconhecido. Use DNG, XMP ou LRTemplate.")
                }
            }
        }
    }

    private fun readZip(bytes: ByteArray): List<Preset> {
        val out = mutableListOf<Preset>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                if (entry.isDirectory || name.startsWith("__MACOSX") || name.substringAfterLast('/').startsWith(".")) continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext !in setOf("xmp", "dng", "lrtemplate")) continue
                runCatching { out += import(name, zip.readBytes()) }
            }
        }
        if (out.isEmpty()) throw PresetFormatException("Nenhum preset encontrado no ZIP")
        return out
    }

    private fun isZip(b: ByteArray) = b.size > 4 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte()
    private fun isTiff(b: ByteArray) = b.size > 4 &&
        ((b[0] == 'I'.code.toByte() && b[1] == 'I'.code.toByte()) || (b[0] == 'M'.code.toByte() && b[1] == 'M'.code.toByte()))
}

/** Writes an Adobe-compatible .xmp preset (Lightroom Classic, Lightroom mobile and desktop, Camera Raw). */
object XmpPresetWriter {
    fun write(preset: Preset): String {
        val s = preset.settings
        val meta = linkedMapOf(
            "PresetType" to "Normal",
            "Cluster" to "",
            "UUID" to (s.values["UUID"] ?: UUID.nameUUIDFromBytes(preset.name.toByteArray()).toString().replace("-", "").uppercase()),
            "SupportsAmount" to "False",
            "SupportsColor" to "True",
            "SupportsMonochrome" to "True",
            "SupportsHighDynamicRange" to "True",
            "SupportsNormalDynamicRange" to "True",
            "SupportsSceneReferred" to "True",
            "SupportsOutputReferred" to "True",
            "CameraModelRestriction" to "",
            "Copyright" to "",
            "ContactInfo" to "",
            "Version" to "15.0",
            "ProcessVersion" to "11.0",
        )
        val attrs = LinkedHashMap<String, String>()
        meta.forEach { (k, v) -> attrs[k] = s.values[k] ?: v }
        s.values.forEach { (k, v) -> if (k !in attrs) attrs[k] = v }
        attrs["HasSettings"] = "True"

        return buildString {
            append("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\" x:xmptk=\"Jr_Link\">\n")
            append(" <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n")
            append("  <rdf:Description rdf:about=\"\"\n    xmlns:crs=\"http://ns.adobe.com/camera-raw-settings/1.0/\"")
            attrs.forEach { (k, v) -> append("\n    crs:").append(k).append("=\"").append(escape(v)).append('"') }
            append(">\n")
            append("   <crs:Name>\n    <rdf:Alt>\n     <rdf:li xml:lang=\"x-default\">").append(escape(preset.name)).append("</rdf:li>\n    </rdf:Alt>\n   </crs:Name>\n")
            append("   <crs:Group>\n    <rdf:Alt>\n     <rdf:li xml:lang=\"x-default\">").append(escape(preset.group)).append("</rdf:li>\n    </rdf:Alt>\n   </crs:Group>\n")
            s.curves.forEach { (k, pts) ->
                append("   <crs:").append(k).append(">\n    <rdf:Seq>\n")
                pts.forEach { append("     <rdf:li>").append(it.x).append(", ").append(it.y).append("</rdf:li>\n") }
                append("    </rdf:Seq>\n   </crs:").append(k).append(">\n")
            }
            append("  </rdf:Description>\n </rdf:RDF>\n</x:xmpmeta>\n")
        }
    }

    fun fileName(preset: Preset): String =
        preset.name.replace(Regex("[\\\\/:*?\"<>|]"), "-").trim().ifBlank { "Preset" } + ".xmp"

    private fun escape(s: String) = s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
}
