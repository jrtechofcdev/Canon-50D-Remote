package com.example.core.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    @Test
    fun version2PhotosSurviveTheUpgrade() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = context.getDatabasePath("tether50d.db").apply { parentFile?.mkdirs(); delete() }

        // Exactly what Room 2.7 created for schema version 2.
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `photos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `session` TEXT NOT NULL, `baseName` TEXT NOT NULL, `savedBase` TEXT NOT NULL, `capturedAt` INTEGER NOT NULL, `jpgUri` TEXT, `rawUri` TEXT, `jpgSize` INTEGER NOT NULL, `rawSize` INTEGER NOT NULL, `previewPath` TEXT, `aperture` TEXT, `shutter` TEXT, `iso` TEXT, `favorite` INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_session_baseName` ON `photos` (`session`, `baseName`)")
            db.execSQL("INSERT INTO photos (session, baseName, savedBase, capturedAt, jpgSize, rawSize, favorite) VALUES ('Casamento', 'IMG_0001', 'IMG_0001', 1, 10, 20, 1)")
            db.version = 2
        }

        val room = PhotoDatabase.create(context)
        val photos = runBlocking { room.photoDao().inSession("Casamento") }
        room.close()

        assertEquals(1, photos.size)
        assertEquals("IMG_0001", photos[0].baseName)
        assertEquals(true, photos[0].favorite)
        assertEquals(0, photos[0].rating)
    }

    @Test
    fun version3PhotosSurviveAndPresetsWork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = context.getDatabasePath("tether50d.db").apply { parentFile?.mkdirs(); delete() }

        // Version 3 (Jr_Link 2.1 to 2.3): version 2 plus stars and camera file names.
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `photos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `session` TEXT NOT NULL, `baseName` TEXT NOT NULL, `savedBase` TEXT NOT NULL, `capturedAt` INTEGER NOT NULL, `jpgUri` TEXT, `rawUri` TEXT, `jpgSize` INTEGER NOT NULL, `rawSize` INTEGER NOT NULL, `previewPath` TEXT, `aperture` TEXT, `shutter` TEXT, `iso` TEXT, `favorite` INTEGER NOT NULL, `rating` INTEGER NOT NULL DEFAULT 0, `rawCameraName` TEXT, `jpgCameraName` TEXT)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_session_baseName` ON `photos` (`session`, `baseName`)")
            db.execSQL("INSERT INTO photos (session, baseName, savedBase, capturedAt, jpgUri, jpgSize, rawSize, favorite, rating, jpgCameraName) VALUES ('Igreja', 'IMG_0042', 'IMG_0042', 1, 'file:///x.jpg', 10, 0, 0, 4, 'IMG_0042.JPG')")
            db.version = 3
        }

        val room = PhotoDatabase.create(context)
        runBlocking {
            val photo = room.photoDao().inSession("Igreja").single()
            assertEquals(4, photo.rating)
            assertEquals(null, photo.presetId)
            assertEquals("file:///x.jpg", photo.lookUri)

            val presetId = room.presetDao().insert(PresetEntity(name = "Teste", group = "Meus presets", settings = "", source = "criado", createdAt = 1, updatedAt = 1))
            room.photoDao().setLook(photo.id, presetId, null)
            assertEquals(presetId, room.photoDao().get(photo.id)?.presetId)
            room.presetDao().detachFromPhotos(presetId)
            assertEquals(null, room.photoDao().get(photo.id)?.presetId)
        }
        room.close()
    }
}
