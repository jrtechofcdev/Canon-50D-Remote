package com.example.core.data

/**
 * A saved exposure combination, e.g. "Igreja interna". Each value is a raw EOS property code;
 * null means "leave as is" when applying.
 */
data class ExposurePreset(
    val name: String,
    val iso: Int? = null,
    val aperture: Int? = null,
    val shutter: Int? = null,
    val exposureCompensation: Int? = null,
    val whiteBalance: Int? = null,
) {
    companion object {
        // One preset per line: name|iso|av|tv|ev|wb, empty field = not set.
        fun encode(list: List<ExposurePreset>): String = list.joinToString("\n") { p ->
            listOf(
                p.name.replace("|", "/").replace("\n", " "),
                p.iso, p.aperture, p.shutter, p.exposureCompensation, p.whiteBalance,
            ).joinToString("|") { it?.toString() ?: "" }
        }

        fun decode(text: String): List<ExposurePreset> = text.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val f = line.split("|")
                if (f.size < 6 || f[0].isBlank()) return@mapNotNull null
                ExposurePreset(
                    name = f[0],
                    iso = f[1].toIntOrNull(),
                    aperture = f[2].toIntOrNull(),
                    shutter = f[3].toIntOrNull(),
                    exposureCompensation = f[4].toIntOrNull(),
                    whiteBalance = f[5].toIntOrNull(),
                )
            }
            .toList()
    }
}
