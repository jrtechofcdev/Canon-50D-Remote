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
}
