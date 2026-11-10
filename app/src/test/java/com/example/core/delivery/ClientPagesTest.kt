package com.example.core.delivery

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The client pages (assets/portal and assets/express) only load files the phone actually serves: a
 * file missing from [LocalServer.ASSETS] answers 404 and the page loses its look or its script.
 */
class ClientPagesTest {
    // Unit tests run from the module folder.
    private val assets = File("src/main/assets")
    /** Files the app supplies from elsewhere (fonts and logos from res/ and watermarks/). */
    private val fromApp = setOf("exo2.ttf", "exo2-400.ttf", "exo2-500.ttf", "exo2-600.ttf", "logo.png", "icon.png")

    private fun referenced(file: File): List<String> =
        Regex("""(?:href|src)="/([^"?/]+)"|url\("/([^"?/]+)"\)""").findAll(file.readText())
            .map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.toList()

    @Test
    fun everyFileThePagesAskForIsServed() {
        for (mode in listOf("portal", "express")) {
            val pages = listOf(File(assets, "$mode/index.html"), File(assets, "$mode/style.css"), File(assets, "shared/brand.css"))
            val names = pages.flatMap(::referenced).toSet()
            assertTrue("$mode: nothing referenced", names.contains("brand.css") && names.contains("app.js"))
            for (name in names) {
                assertTrue("$mode asks for /$name, which the server does not serve", name in LocalServer.ASSETS)
                val onDisk = name in fromApp || File(assets, "$mode/$name").exists() || File(assets, "shared/$name").exists()
                assertTrue("$mode asks for /$name, which is not in the APK", onDisk)
            }
        }
    }

    @Test
    fun noEmDashInClientText() {
        // House style (antislop R-02): the client never reads an em dash.
        assets.walkTopDown().filter { it.isFile && it.extension in setOf("html", "css", "js") }.forEach {
            assertTrue("${it.path} has an em dash", !it.readText().contains('—'))
        }
    }
}
