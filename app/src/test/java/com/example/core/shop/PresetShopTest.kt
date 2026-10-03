package com.example.core.shop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.PhotoDatabase
import com.example.core.data.SettingKeys
import com.example.core.data.SettingsRepository
import com.example.core.preset.PresetRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PresetShopTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: PhotoDatabase

    private fun dng(gap: Int): ByteArray {
        val bmp = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200, 120, 60)) }
        return TestDng.build(TestDng.jpeg(bmp), 320, 240, gap = gap)
    }

    private val api = "https://example.test/api/presets.php"
    private val near = "https://example.test/uploads/Moody/presets_id_1.dng"
    private val far = "https://example.test/uploads/Winter/Frost%20A1.dng"
    private val catalog = """[
        {"id":1,"filename":"presets_id_1.dng","category":"Moody","downloadUrl":"$near"},
        {"id":2,"filename":"Frost A1.dng","category":"Winter","downloadUrl":"$far"}]"""

    private lateinit var server: TestDng.Server
    private lateinit var presets: PresetRepository
    private lateinit var settings: SettingsRepository

    @Before
    fun setUp() = runBlocking {
        context.filesDir.resolve("shop").deleteRecursively()
        context.cacheDir.resolve("shop").deleteRecursively()
        db = PhotoDatabase.create(context, "shop_test.db")
        settings = SettingsRepository(context)
        settings.set(SettingKeys.shopUrl, api)
        presets = PresetRepository(context, db.presetDao(), settings)
        server = TestDng.Server(mapOf(api to catalog.toByteArray(), near to dng(gap = 1_000), far to dng(gap = 300_000)))
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase("shop_test.db")
    }

    @Test
    fun previewAndSettingsComeFromPartsOfTheDng() = runBlocking {
        val shop = PresetShop(context, settings, presets, server)
        shop.refresh()
        val items = shop.items.value
        assertEquals(listOf("Moody 1", "Frost A1"), items.map { it.name })

        val bmp = shop.preview(items[1], 480)
        assertNotNull(bmp)
        assertEquals(320, bmp!!.width)
        // Only the first 64 KB and the preview travelled, not the 300 KB of "raw" in between.
        assertTrue("sent ${server.sent}", server.sent < catalog.length + PresetShop.HEAD_BYTES + 20_000)

        val preset = shop.preset(items[1])
        assertEquals("Frost A1", preset.name)
        assertEquals("Inverno", preset.group)
        assertEquals(0.5, preset.settings.num("Exposure2012"), 1e-6)
    }

    @Test
    fun savesOnceAndKeepsWorkingOffline() = runBlocking {
        val shop = PresetShop(context, settings, presets, server)
        shop.refresh()
        val item = shop.items.value.first()
        val id = shop.save(item)
        assertEquals(id, shop.save(item))
        val stored = presets.get(id)!!
        assertEquals("loja:1", stored.source)
        assertEquals("Moody", stored.group)
        assertEquals(12.0, stored.settings.num("Contrast2012"), 1e-6)

        // Without internet: the list saved on the phone stays, and the saved preset's settings still load.
        server.online = false
        val offline = PresetShop(context, settings, presets, server)
        offline.refresh()
        assertEquals(2, offline.items.value.size)
        assertTrue(offline.status.value is ShopStatus.Offline)
        assertTrue(offline.hasOffline(item))
        assertEquals(0.5, offline.preset(item).settings.num("Exposure2012"), 1e-6)
    }

    @Test
    fun originalComesBackWithoutThePreset() = runBlocking {
        // The "before": mid grey on the left, a warm colour on the right.
        val before = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(128, 128, 128))
            for (x in 32 until 64) for (y in 0 until 48) setPixel(x, y, Color.rgb(200, 120, 60))
        }
        val file = TestDng.build(
            TestDng.jpeg(Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }), 64, 48,
            original = Triple(TestDng.jpeg(before), 64, 48),
        )
        val url = "https://example.test/uploads/Moody/orig.dng"
        val json = """[{"id":7,"filename":"orig.dng","category":"Moody","downloadUrl":"$url"}]"""
        val shop = PresetShop(context, settings, presets, TestDng.Server(mapOf(api to json.toByteArray(), url to file)))
        shop.refresh()
        val layout = DngLayout.read(file)
        assertEquals(1, layout.originals.size)
        assertEquals(256, layout.originals[0].linearization?.size)

        val bmp = shop.original(shop.items.value.first(), full = true)!!
        assertEquals(64, bmp.width)
        // Linearized and put back on the sRGB curve: the original colours, within JPEG rounding.
        val grey = bmp.getPixel(8, 24)
        val warm = bmp.getPixel(56, 24)
        assertTrue(kotlin.math.abs(Color.red(grey) - 128) <= 4 && kotlin.math.abs(Color.blue(grey) - 128) <= 4)
        assertTrue(kotlin.math.abs(Color.red(warm) - 200) <= 6 && kotlin.math.abs(Color.blue(warm) - 60) <= 6)
    }

    @Test
    fun layoutFindsThePreviewAndTheXmp() {
        val bytes = dng(gap = 1_000)
        val layout = DngLayout.read(bytes)
        assertEquals(1, layout.previews.size)
        assertEquals(320, layout.previews[0].width)
        val seg = layout.previews[0]
        assertTrue(DngLayout.isViewableJpeg(bytes.copyOfRange(seg.offset.toInt(), seg.end.toInt())))
        val x = layout.xmp!!
        assertTrue(String(bytes, x.offset.toInt(), x.length).contains("crs:Exposure2012"))
        // Lossless JPEG (SOF3), as in the raw data, is not a preview.
        assertTrue(!DngLayout.isViewableJpeg(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xC3.toByte(), 0, 4, 0, 0)))
    }
}
