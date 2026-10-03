package com.example.core.shop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShopCatalogTest {

    // Same shape as lightroompresets.kasunpremarathna.com/api/presets.php.
    private val sample = """
        [{"id":1085,"filename":"presets_id_692.dng","category":"Winter","type":"Free","file_path":"\/home\/x\/presets_id_692.dng","created_at":"2026-06-29 21:35:31","downloadUrl":"https:\/\/lightroompresets.kasunpremarathna.com\/uploads\/Winter\/presets_id_692.dng"},
         {"id":1088,"filename":"Amber A3.dng","category":"Autumn","type":"Free","downloadUrl":"https:\/\/lightroompresets.kasunpremarathna.com\/uploads\/Autumn\/Amber%20A3.dng"},
         {"id":700,"filename":"presets_id_333.jpg","category":"Daily","downloadUrl":"https:\/\/x\/presets_id_333.jpg"},
         {"id":701,"filename":"Rainbow Relax 7 (327).dng","category":"Sky Colour","downloadUrl":"https:\/\/x\/uploads\/Sky Colour\/Rainbow Relax 7 (327).dng"}]
    """.trimIndent()

    @Test
    fun readsTheApiListAndSkipsWhatIsNotAPreset() {
        val items = ShopCatalog.parse(sample)
        assertEquals(listOf(1085L, 1088L, 701L), items.map { it.id })
        val winter = items[0]
        assertEquals("Inverno 692", winter.name)
        assertEquals("Inverno", winter.categoryLabel)
        assertEquals("presets_id_692", winter.baseName)
        assertEquals("loja:1085", winter.source)
        assertTrue(winter.isDng)
        assertEquals("Amber A3", items[1].name)
        assertEquals("Outono", items[1].categoryLabel)
        // Spaces in a link are encoded so the request doesn't fail.
        assertEquals("https://x/uploads/Sky%20Colour/Rainbow%20Relax%207%20(327).dng", items[2].url)
        assertEquals("Céu", items[2].categoryLabel)
    }

    @Test
    fun acceptsAListInsideAnObjectAndOtherFieldNames() {
        val json = """{"presets":[{"id":5,"name":"Soft.xmp","category":"Moody","url":"https://x/Soft.xmp"}]}"""
        val items = ShopCatalog.parse(json)
        assertEquals(1, items.size)
        assertEquals("Soft", items[0].name)
        assertEquals("Moody", items[0].categoryLabel)
        assertTrue(!items[0].isDng)
    }

    @Test
    fun unknownCategoryKeepsItsName() {
        assertEquals("Neon Nights", ShopCatalog.categoryLabel("Neon Nights"))
        assertEquals("Preto e branco", ShopCatalog.categoryLabel("Black & White"))
    }
}
