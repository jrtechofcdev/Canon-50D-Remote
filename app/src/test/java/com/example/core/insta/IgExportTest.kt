package com.example.core.insta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IgExportTest {

    @Test
    fun onlyThePhotosOfThePostsAreRead() {
        assertTrue(IgExport.isPostImage("instagram-igreja-2026/media/posts/17857904286536195.heic"))
        assertTrue(IgExport.isPostImage("media/posts/202401/17955013403713398.jpg"))
        assertTrue(IgExport.isPostImage("x/media/posts/1.WEBP"))
        assertFalse(IgExport.isPostImage("x/media/stories/202401/1.jpg"))
        assertFalse(IgExport.isPostImage("x/media/posts/1.mp4"))
        assertFalse(IgExport.isPostImage("x/files/Instagram-Logo.png"))
        assertFalse(IgExport.isPostImage("__MACOSX/media/posts/._1.jpg"))
        assertTrue(IgExport.isPostList("x/your_instagram_activity/media/posts_1.html"))
        assertTrue(IgExport.isPostList("x/content/posts_1.json"))
        assertFalse(IgExport.isPostList("x/your_instagram_activity/media/stories.html"))
    }

    @Test
    fun theCaptionsTeachTheHashtagsAndTheCredit() {
        // As the HTML export writes it: a style sheet full of colours, "@" as an entity.
        val html = """<html><head><style type="text/css">body{color:#fff}a{color:#385898}#facebook .x{}</style></head><body>
            <div>Culto de ensino<br>Fotos:&#064;fotografo_x<br>#AssembléiadeDeus<br>#cultodeensino</div>
            <div>Festa dos jovens #AssembléiadeDeus #jovens Fotos:&#064;fotografo_x</div>
            <div>Santa Ceia #assembleiadedeus #jovens #umavez</div></body></html>"""
        val v = IgExport.voice(listOf(html))
        assertEquals(listOf("#AssembléiadeDeus", "#jovens"), v.hashtags)
        assertEquals("@fotografo_x", v.credit)
        assertEquals(v, IgExport.Voice.decode(v.encode()))
    }

    @Test
    fun theJsonExportGetsItsAccentsBack() {
        // The JSON export writes each UTF-8 byte of "ã" as its own escape.
        val json = """[{"title": "#jovenscristÃ£os Fotos: @foto_y"}, {"title": "#jovenscristÃ£os"}]"""
        val v = IgExport.voice(listOf(json))
        assertEquals(listOf("#jovenscristãos"), v.hashtags)
        assertEquals("@foto_y", v.credit)
        assertNull(IgExport.Voice.decode(null))
    }
}
