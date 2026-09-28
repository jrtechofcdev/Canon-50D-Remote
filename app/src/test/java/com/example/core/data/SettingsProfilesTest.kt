package com.example.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsProfilesTest {

    @Test
    fun valuesKeepTheirTypeThroughJson() {
        val values = mapOf<String, Any>("night_mode" to true, "night_brightness" to 15, "session_preset_id" to 7L, "download_mode" to "JPG_ONLY")
        val back = SettingsProfiles.decodeValues(SettingsProfiles.encodeValues(values))
        assertEquals(values, back)
        assertTrue(back["night_brightness"] is Int)
        assertTrue(back["session_preset_id"] is Long)
    }

    @Test
    fun profileListRoundTrip() {
        val list = listOf(SettingsProfile("Igreja", ProfileIcon.Church.id, mapOf("night_mode" to true)))
        assertEquals(list, SettingsProfiles.decodeList(SettingsProfiles.encodeList(list)))
        assertEquals(emptyList<SettingsProfile>(), SettingsProfiles.decodeList("não é json"))
    }

    @Test
    fun startersNeverTouchPersonalOptions() {
        SettingsProfiles.starters.forEach { p ->
            assertTrue(p.name, p.values.keys.none { it in SettingsProfiles.personal })
            assertTrue(p.name, p.values.isNotEmpty())
        }
    }

    @Test
    fun applyingAProfileChangesItsOptionsAndKeepsTheRest() = runBlocking {
        val repo = SettingsRepository(ApplicationProvider.getApplicationContext<Context>())
        repo.set(SettingKeys.sessionName, "Casamento Ana")
        repo.set(SettingKeys.nightMode, false)
        val church = SettingsProfiles.starters.first { it.icon == ProfileIcon.Church.id }
        repo.applyProfile(church)
        val s = repo.current()
        assertTrue(s.nightMode)
        assertTrue(s.momentSubfolder)
        assertEquals("Casamento Ana", s.sessionName)
        assertEquals(church.name, repo.activeProfile.first())
    }

    @Test
    fun savedProfileLeavesFoldersAndPinOut() = runBlocking {
        val repo = SettingsRepository(ApplicationProvider.getApplicationContext<Context>())
        repo.set(SettingKeys.clientPin, "1234")
        repo.set(SettingKeys.jpgFolder, "content://x")
        repo.set(SettingKeys.galleryThumb, 150)
        repo.saveCurrentAs("Meu", ProfileIcon.Star.id)
        val saved = repo.profiles.first().first { it.name == "Meu" }
        assertEquals(150, saved.values["gallery_thumb"])
        assertFalse(saved.values.containsKey("client_pin"))
        assertFalse(saved.values.containsKey("jpg_folder"))
    }

    @Test
    fun backupFileRestoresOptionsProfilesAndPresets() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<com.example.TetherApp>()
        app.settings.set(SettingKeys.blurThreshold, 61)
        app.settings.set(SettingKeys.jpgFolder, "content://this-phone")
        app.settings.saveCurrentAs("Formatura", ProfileIcon.Party.id)
        app.presets.save(com.example.core.preset.Preset(name = "Backup look", group = "Igreja", favorite = true))
        val file = app.appBackup.export("3.1")
        assertFalse(file.contains("content://this-phone"))

        app.settings.set(SettingKeys.blurThreshold, 40)
        app.settings.saveProfiles(emptyList())
        app.presets.all().filter { it.name == "Backup look" }.forEach { app.presets.delete(it) }

        val report = app.appBackup.restore(file)
        assertEquals(61, app.settings.current().blurThreshold)
        assertEquals("content://this-phone", app.settings.current().jpgFolder)
        assertTrue(app.settings.profiles.first().any { it.name == "Formatura" })
        val restored = app.presets.all().first { it.name == "Backup look" }
        assertEquals("Igreja", restored.group)
        assertTrue(restored.favorite)
        assertEquals(1, report.presets)
    }
}
