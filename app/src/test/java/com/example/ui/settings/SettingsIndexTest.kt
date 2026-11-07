package com.example.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsIndexTest {
    /** Each page's composable, as it is named in the source. */
    private val pages = mapOf(
        "ProfilesSettings" to SettingsSection.Profiles, "SaveSettings" to SettingsSection.Save,
        "ReviewSettings" to SettingsSection.Review, "CameraSettings" to SettingsSection.Camera,
        "ShareSettings" to SettingsSection.Share, "DeliverySettings" to SettingsSection.Delivery,
        "PaymentSettings" to SettingsSection.Payments, "ExpressSettings" to SettingsSection.Express,
        "PresetSettingsSection" to SettingsSection.Presets, "MessageSettings" to SettingsSection.Messages,
        "AlertSettings" to SettingsSection.Alerts, "DisplaySettings" to SettingsSection.Display,
        "DiagnosticsSettings" to SettingsSection.Diagnostics, "DeveloperSettings" to SettingsSection.Developer,
    )

    @Test
    fun everyRowTitleCanBeFound() {
        // Unit tests run from the module folder.
        val dir = File("src/main/java/com/example/ui/settings")
        val source = dir.listFiles { f -> f.extension == "kt" && f.name != "SettingsIndex.kt" }!!
            .sortedBy { it.name }.joinToString("\n") { it.readText() }
        val row = Regex("""(?:ToggleRow|SliderRow|ActionRow|ChoiceRow|SettingRow|FolderRow|TextRow|SettingsGroup|NavRow|InfoRow|PickerRow|SegmentRow)\(\s*(?:Icons\.[\w.]+,\s*)?"([^"$\\]+)"""")
        val missing = mutableListOf<String>()
        source.split(Regex("""\n(?=(?:private |internal )?fun |@Composable\n(?:private |internal )?fun )""")).forEach { block ->
            val name = Regex("""fun (\w+)\(""").find(block)?.groupValues?.get(1) ?: return@forEach
            val section = pages[name] ?: return@forEach
            row.findAll(block).map { it.groupValues[1] }.forEach { title ->
                if (title !in settingsIndex[section].orEmpty()) missing += "${section.name}: $title"
            }
        }
        assertTrue("Faltam no SettingsIndex.kt:\n" + missing.joinToString("\n"), missing.isEmpty())
    }

    @Test
    fun searchIgnoresAccentsAndCase() {
        assertEquals(listOf("Foto acompanha o celular"), settingsMatches(SettingsSection.Display, "foto acompanha"))
        assertTrue(settingsMatches(SettingsSection.Share, "MARCA D'AGUA")!!.contains("Marca d'água"))
        assertTrue(settingsMatches(SettingsSection.Camera, "relogio camera")!!.contains("Acertar o relógio da câmera"))
    }

    @Test
    fun aPageWithNothingMatchingIsLeftOut() {
        assertNull(settingsMatches(SettingsSection.Diagnostics, "pix"))
        assertTrue(settingsMatches(SettingsSection.Payments, "pix")!!.contains("Pix"))
    }
}
