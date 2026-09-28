package com.example.core.preset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PresetFormatsTest {

    // Shaped like a preset exported by Lightroom Classic 13.
    private val xmp = """
        <x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 7.0-c000 1.000000, 0000/00/00-00:00:00        ">
         <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
          <rdf:Description rdf:about=""
            xmlns:crs="http://ns.adobe.com/camera-raw-settings/1.0/"
           crs:PresetType="Normal"
           crs:UUID="6E0D4F5C1B9A4E6B8C2D3F4A5B6C7D8E"
           crs:Version="16.0"
           crs:ProcessVersion="11.0"
           crs:Exposure2012="+0.35"
           crs:Contrast2012="+12"
           crs:Highlights2012="-45"
           crs:Shadows2012="+30"
           crs:IncrementalTemperature="+8"
           crs:SaturationAdjustmentGreen="-20"
           crs:CameraProfile="Adobe Color"
           crs:HasSettings="True">
           <crs:Name>
            <rdf:Alt>
             <rdf:li xml:lang="x-default">Golden Hour &amp; Pele</rdf:li>
            </rdf:Alt>
           </crs:Name>
           <crs:Group>
            <rdf:Alt>
             <rdf:li xml:lang="x-default">Externo</rdf:li>
            </rdf:Alt>
           </crs:Group>
           <crs:ToneCurvePV2012>
            <rdf:Seq>
             <rdf:li>0, 12</rdf:li>
             <rdf:li>128, 132</rdf:li>
             <rdf:li>255, 250</rdf:li>
            </rdf:Seq>
           </crs:ToneCurvePV2012>
          </rdf:Description>
         </rdf:RDF>
        </x:xmpmeta>
    """.trimIndent()

    @Test
    fun readsAttributesNameGroupAndCurves() {
        val p = XmpPresetParser.parse(xmp, "arquivo")
        assertEquals("Golden Hour & Pele", p.name)
        assertEquals("Externo", p.group)
        assertEquals(0.35, p.settings.num("Exposure2012"), 1e-9)
        assertEquals(-45.0, p.settings.num("Highlights2012"), 1e-9)
        assertEquals("Adobe Color", p.settings.values["CameraProfile"])
        assertEquals(listOf(CurvePoint(0, 12), CurvePoint(128, 132), CurvePoint(255, 250)), p.settings.curve("ToneCurvePV2012"))
    }

    @Test
    fun readsElementFormValues() {
        val p = XmpPresetParser.parse(
            """<x:xmpmeta><rdf:RDF><rdf:Description xmlns:crs="http://ns.adobe.com/camera-raw-settings/1.0/">
               <crs:Vibrance>+25</crs:Vibrance><crs:Clarity2012>+10</crs:Clarity2012>
               </rdf:Description></rdf:RDF></x:xmpmeta>""",
            "Meu preset",
        )
        assertEquals("Meu preset", p.name)
        assertEquals(25.0, p.settings.num("Vibrance"), 1e-9)
        assertEquals(10.0, p.settings.num("Clarity2012"), 1e-9)
    }

    @Test
    fun roundTripKeepsEverything() {
        val original = XmpPresetParser.parse(xmp, "x")
        val again = XmpPresetParser.parse(XmpPresetWriter.write(original), "y")
        assertEquals(original.name, again.name)
        assertEquals(original.group, again.group)
        assertEquals(original.settings.curves, again.settings.curves)
        original.settings.values.forEach { (k, v) -> assertEquals("key $k", v, again.settings.values[k]) }
    }

    /** Minimal little-endian TIFF with the XMP in tag 700 of IFD0, the way DNG presets carry it. */
    private fun dng(xmpPacket: String, bigEndian: Boolean = false): ByteArray {
        val payload = xmpPacket.toByteArray()
        val order = if (bigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
        val b = ByteBuffer.allocate(8 + 2 + 12 * 2 + 4 + payload.size).order(order)
        b.put(if (bigEndian) "MM".toByteArray() else "II".toByteArray()).putShort(42).putInt(8)
        b.putShort(2)
        b.putShort(256).putShort(3).putInt(1).putShort(1).putShort(0) // ImageWidth = 1
        b.putShort(700).putShort(1).putInt(payload.size).putInt(8 + 2 + 24 + 4)
        b.putInt(0)
        b.put(payload)
        return b.array()
    }

    @Test
    fun extractsPresetFromDng() {
        for (big in listOf(false, true)) {
            val presets = PresetImporter.import("Golden.dng", dng(xmp, big))
            assertEquals(1, presets.size)
            assertEquals("dng", presets[0].source)
            assertEquals("Golden Hour & Pele", presets[0].name)
            assertEquals(30.0, presets[0].settings.num("Shadows2012"), 1e-9)
        }
    }

    @Test
    fun dngWithoutPresetIsRejectedWithAClearMessage() {
        try {
            // A regular photo's XMP (no develop settings) is not a preset.
            DngPresetParser.extractXmp(dng("<x:xmpmeta><dc:title>foto</dc:title></x:xmpmeta>"))
            fail("expected error")
        } catch (e: PresetFormatException) {
            assertTrue(e.message!!.isNotBlank())
        }
    }

    private val lrtemplate = """
        s = {
            id = "1C0B0B3B-1111-2222-3333-444455556666",
            internalName = "Hard Sun Fix",
            title = ZSTR "${'$'}${'$'}${'$'}/CRaw/Presets/Hard_Sun=Hard Sun Fix",
            type = "Develop",
            value = {
                settings = {
                    Blacks2012 = -8,
                    ConvertToGrayscale = false,
                    Exposure2012 = -0.2,
                    Highlights2012 = -70,
                    ProcessVersion = "11.0",
                    ToneCurveName2012 = "Custom",
                    ToneCurvePV2012 = {
                        0,
                        0,
                        64,
                        58,
                        255,
                        255,
                    },
                    Look = {
                        Name = "Nada",
                        Parameters = { Version = 1, },
                    },
                    WhiteBalance = "As Shot",
                },
                uuid = "AAAA",
            },
            version = 0,
        }
    """.trimIndent()

    @Test
    fun readsLrTemplate() {
        val p = PresetImporter.import("hard.lrtemplate", lrtemplate.toByteArray()).single()
        assertEquals("Hard Sun Fix", p.name)
        assertEquals("lrtemplate", p.source)
        assertEquals(-0.2, p.settings.num("Exposure2012"), 1e-9)
        assertEquals(-70.0, p.settings.num("Highlights2012"), 1e-9)
        assertEquals("False", p.settings.values["ConvertToGrayscale"])
        assertEquals("As Shot", p.settings.values["WhiteBalance"])
        assertEquals(listOf(CurvePoint(0, 0), CurvePoint(64, 58), CurvePoint(255, 255)), p.settings.curve("ToneCurvePV2012"))
    }

    @Test
    fun readsZipPacks() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("Pack/Golden.xmp")); z.write(xmp.toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("Pack/Sun.lrtemplate")); z.write(lrtemplate.toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("__MACOSX/Pack/._Golden.xmp")); z.write(byteArrayOf(1, 2)); z.closeEntry()
            z.putNextEntry(ZipEntry("Pack/leia-me.txt")); z.write("oi".toByteArray()); z.closeEntry()
        }
        val presets = PresetImporter.import("pack.zip", out.toByteArray())
        assertEquals(listOf("Golden Hour & Pele", "Hard Sun Fix"), presets.map { it.name })
    }

    @Test
    fun unknownFileIsRejected() {
        try {
            PresetImporter.import("foto.jpg", byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3))
            fail("expected error")
        } catch (e: PresetFormatException) {
            assertTrue(e.message!!.contains("DNG"))
        }
    }

    @Test
    fun databaseEncodingRoundTrips() {
        val s = XmpPresetParser.parse(xmp, "x").settings.with("Exposure2012", -1.5).with("Vibrance", 20.0)
        val back = PresetSettings.decode(s.encode())
        assertEquals(s, back)
        assertEquals("-1.50", back.values["Exposure2012"])
        assertEquals("+20", back.values["Vibrance"])
    }
}
