package com.example.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.example.core.data.Photo
import com.example.ui.photos.PhotosScreen
import com.example.ui.photos.ViewerScreen
import kotlinx.coroutines.runBlocking
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.TetherApp
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsSection
import com.example.ui.settings.SettingsSectionScreen
import com.example.ui.session.SessionScreen
import com.example.ui.theme.JrLinkTheme
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the main screens to PNG (app/build/screens) so the layout can be reviewed without a phone.
 * Not an assertion test: it fails only if a screen crashes while composing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<TetherApp>()

    private fun save(name: String, readyWhen: androidx.compose.ui.test.SemanticsMatcher? = null) {
        if (readyWhen != null) {
            runCatching { rule.waitUntil(15_000) { rule.onAllNodes(readyWhen).fetchSemanticsNodes().isNotEmpty() } }
        }
        // Room, Coil and the histogram work on background threads. waitUntil keeps the clock and loopers
        // moving while it polls, so let it run out before drawing.
        runCatching { rule.waitUntil(8_000) { false } }
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun session() {
        seedPhotos()
        val actions = PhotoActions(app, app.store, app.export)
        rule.setContent { JrLinkTheme { SessionScreen(app, actions, {}, {}, {}, {}) } }
        save("sessao", androidx.compose.ui.test.hasText("Finalizar sessão"))
    }

    @Test
    fun sessionNight() {
        seedPhotos()
        val actions = PhotoActions(app, app.store, app.export)
        rule.setContent { JrLinkTheme(night = true) { SessionScreen(app, actions, {}, {}, {}, {}) } }
        save("sessao_noite", androidx.compose.ui.test.hasText("Finalizar sessão"))
    }

    /** A few synthetic frames (gradients with a bright patch) so the gallery and viewer have content. */
    private fun seedPhotos() = runBlocking {
        val dao = app.database.photoDao()
        if (dao.inSession("Geral").isNotEmpty()) return@runBlocking
        // Warm/cool defocused-light scenes (bokeh), so the screens look like a real event gallery.
        val palettes = listOf(
            intArrayOf(0xFF1B1410.toInt(), 0xFFFFB45A.toInt()), intArrayOf(0xFF0E1622.toInt(), 0xFF7FC4FF.toInt()),
            intArrayOf(0xFF1E1016.toInt(), 0xFFFF7A8A.toInt()), intArrayOf(0xFF101A14.toInt(), 0xFFB8F29A.toInt()),
            intArrayOf(0xFF1A1420.toInt(), 0xFFE0A6FF.toInt()), intArrayOf(0xFF1C1810.toInt(), 0xFFFFE08A.toInt()),
        )
        palettes.forEachIndexed { i, (dark, light) ->
            val bmp = Bitmap.createBitmap(900, 600, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(dark)
            val rnd = java.util.Random(i * 31L + 7)
            repeat(26) {
                val x = rnd.nextFloat() * 900f
                val y = rnd.nextFloat() * 600f
                val r = 30f + rnd.nextFloat() * 90f
                val alpha = 40 + rnd.nextInt(120)
                val c = (alpha shl 24) or (light and 0x00FFFFFF)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = android.graphics.RadialGradient(x, y, r, intArrayOf(c, c, c and 0x00FFFFFF), floatArrayOf(0f, 0.8f, 1f), android.graphics.Shader.TileMode.CLAMP)
                }
                canvas.drawCircle(x, y, r, paint)
            }
            val file = File(app.cacheDir, "sample_$i.jpg")
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            dao.insert(
                Photo(
                    session = "Geral", baseName = "IMG_10${40 + i}", savedBase = "IMG_10${40 + i}",
                    capturedAt = 1_700_000_000_000L + i * 60_000L, jpgUri = "file://${file.path}", rawUri = if (i % 2 == 0) "file://${file.path}" else null,
                    jpgSize = 4_200_000, rawSize = if (i % 2 == 0) 15_300_000 else 0,
                    aperture = "f/1.8", shutter = "1/200", iso = "ISO 400", favorite = i == 1, rating = i % 4,
                ),
            )
        }
    }

    @Test
    fun photos() {
        seedPhotos()
        rule.setContent { JrLinkTheme { PhotosScreen(app, PhotoActions(app, app.store, app.export), onOpenPhoto = {}) } }
        rule.mainClock.advanceTimeBy(1_000)
        save("fotos", androidx.compose.ui.test.hasText("RAW+JPG"))
    }

    @Test
    fun viewer() {
        seedPhotos()
        val actions = PhotoActions(app, app.store, app.export)
        val id = runBlocking { app.database.photoDao().inSession("Geral").last().id }
        rule.setContent { JrLinkTheme { ViewerScreen(app, actions, id, onBack = {}, onCalibrate = {}) } }
        rule.mainClock.advanceTimeBy(1_000)
        save("visualizador")
    }

    @Test
    fun settings() {
        rule.setContent { JrLinkTheme { SettingsScreen(onOpen = {}) } }
        save("ajustes")
    }

    @Test
    fun settingsShare() {
        rule.setContent { JrLinkTheme { SettingsSectionScreen(app, SettingsSection.Share, onBack = {}) } }
        rule.mainClock.advanceTimeBy(2_000)
        val logo = androidx.compose.ui.test.hasContentDescription("Marca d'água")
        runCatching { rule.waitUntil(15_000) { rule.onAllNodes(logo).fetchSemanticsNodes().isNotEmpty() } }
        rule.onNode(androidx.compose.ui.test.hasText("Posição")).performScrollTo()
        save("ajustes_envio", logo)
    }

    /**
     * A starter preset by name. Other tests in the same run can leave presets behind (so the pack is
     * not seeded again); the one needed is added back when missing, whatever ran before.
     */
    private suspend fun starter(name: String): com.example.core.preset.Preset {
        app.presets.seedIfEmpty()
        app.presets.all().firstOrNull { it.name.contains(name) }?.let { return it }
        val template = com.example.core.preset.StarterPresets.all.first { it.name.contains(name) }
        val id = app.presets.save(template.copy(id = 0))
        return app.presets.get(id)!!
    }

    /** Seeds the starter pack and gives the newest sample photo a preset, as a session with a look would. */
    private fun seedLooks(): Long = runBlocking {
        seedPhotos()
        val preset = starter("Outdoor")
        val photo = app.database.photoDao().inSession("Geral").last()
        app.database.photoDao().setLook(photo.id, preset.id, null)
        app.settings.set(com.example.core.data.SettingKeys.sessionPresetId, preset.id)
        app.presets.setFavorite(preset.id, true)
        app.presets.addCategory("Casamentos")
        photo.id
    }

    @Test
    fun presetsTab() {
        seedLooks()
        rule.setContent { JrLinkTheme { com.example.ui.preset.PresetsScreen(app, onEdit = {}) } }
        save("presets", androidx.compose.ui.test.hasText("Pack Externo"))
    }

    private fun editor(target: com.example.ui.preset.EditTarget, tab: String?, name: String) {
        rule.setContent { JrLinkTheme { com.example.ui.preset.EditorScreen(app, target, onDone = {}) } }
        runCatching { rule.waitUntil(15_000) { rule.onAllNodes(androidx.compose.ui.test.hasText("Luz")).fetchSemanticsNodes().isNotEmpty() } }
        if (tab != null) rule.onNode(androidx.compose.ui.test.hasText(tab)).performScrollTo().performClick()
        save(name)
    }

    @Test
    fun editorLight() {
        val id = seedLooks()
        editor(com.example.ui.preset.EditTarget.PhotoTarget(id), null, "editor_luz")
    }

    @Test
    fun editorColor() {
        val id = seedLooks()
        editor(com.example.ui.preset.EditTarget.PhotoTarget(id), "Cor", "editor_cor")
    }

    @Test
    fun editorCurve() {
        seedLooks()
        val preset = runBlocking { app.presets.all().first() }
        editor(com.example.ui.preset.EditTarget.PresetTarget(preset.id), "Curva", "editor_curva")
    }

    @Test
    fun editorGrading() {
        seedLooks()
        val preset = runBlocking { app.presets.all().first() }
        editor(com.example.ui.preset.EditTarget.PresetTarget(preset.id), "Color grading", "editor_grading")
    }

    @Test
    fun viewerWithLook() {
        val id = seedLooks()
        val actions = PhotoActions(app, app.store, app.export)
        rule.setContent { JrLinkTheme { ViewerScreen(app, actions, id, onBack = {}, onCalibrate = {}) } }
        save("visualizador_preset", androidx.compose.ui.test.hasText("Calibrar"))
    }

    @Test
    fun clientWithLook() {
        seedLooks()
        rule.setContent { JrLinkTheme { com.example.ui.client.ClientModeScreen(app, onExit = {}, onCalibrate = {}) } }
        save("cliente_preset", androidx.compose.ui.test.hasText("Original"))
    }

    @Test
    fun settingsPresets() {
        seedLooks()
        rule.setContent { JrLinkTheme { SettingsSectionScreen(app, SettingsSection.Presets, onBack = {}) } }
        save("ajustes_presets", androidx.compose.ui.test.hasText("Salvar cópia editada"))
    }

    @Test
    fun galleryBatch() {
        seedPhotos()
        runBlocking {
            val dao = app.database.photoDao()
            val ids = dao.inSession("Geral").map { it.id }
            dao.setMoment(ids.take(3), "Cerimônia")
            dao.setMoment(ids.drop(3), "Recepção")
            dao.setClientPick(ids[1], true)
            dao.setNote(ids[2], "mãe da noiva")
            app.settings.set(com.example.core.data.SettingKeys.momentsEnabled, true)
        }
        rule.setContent { JrLinkTheme { PhotosScreen(app, PhotoActions(app, app.store, app.export), onOpenPhoto = {}) } }
        runCatching { rule.waitUntil(15_000) { rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("IMG_1045")).fetchSemanticsNodes().isNotEmpty() } }
        listOf("IMG_1045", "IMG_1043").forEach { name ->
            rule.onNode(androidx.compose.ui.test.hasContentDescription(name)).performTouchInput { longClick() }
        }
        save("galeria_lote")
    }

    @Test
    fun clientHeart() {
        seedPhotos()
        runBlocking {
            app.database.photoDao().inSession("Geral").forEach { app.database.photoDao().setClientPick(it.id, true) }
            app.settings.set(com.example.core.data.SettingKeys.clientShowInfo, true)
            app.settings.set(com.example.core.data.SettingKeys.clientPin, "1234")
        }
        rule.setContent { JrLinkTheme { com.example.ui.client.ClientModeScreen(app, onExit = {}, onCalibrate = {}) } }
        save("cliente_coracao", androidx.compose.ui.test.hasText("Escolhida"))
    }

    @Test
    fun settingsProfiles() {
        runBlocking { app.settings.applyProfile(com.example.core.data.SettingsProfiles.starters.first()) }
        rule.setContent { JrLinkTheme { SettingsSectionScreen(app, SettingsSection.Profiles, onBack = {}) } }
        save("ajustes_perfis", androidx.compose.ui.test.hasText("Ensaio externo"))
    }

    @Test
    fun settingsDiagnostics() {
        com.example.core.log.AppLog.i("USB", "Câmera: Canon EOS 50D (firmware 1.0.9)")
        com.example.core.log.AppLog.i("DOWNLOAD", "IMG_1045.JPG salvo em 812 ms")
        com.example.core.log.AppLog.e("EDITADA", "Cópia editada de IMG_1046 falhou · memória 240/256 MB")
        rule.setContent { JrLinkTheme { SettingsSectionScreen(app, SettingsSection.Diagnostics, onBack = {}) } }
        save("ajustes_diagnostico", androidx.compose.ui.test.hasText("Salvar log completo"))
    }

    @Test
    fun settingsReview() {
        rule.setContent { JrLinkTheme { SettingsSectionScreen(app, SettingsSection.Review, onBack = {}) } }
        save("ajustes_revisao", androidx.compose.ui.test.hasText("Tamanho das miniaturas"))
    }

    @Test
    fun tools() {
        rule.setContent { JrLinkTheme { com.example.ui.tools.ToolsScreen(onBack = {}) } }
        save("ferramentas", androidx.compose.ui.test.hasText("Flash direto"))
    }

    @Test
    fun settingsMessages() {
        rule.setContent { JrLinkTheme { SettingsSectionScreen(app, SettingsSection.Messages, onBack = {}) } }
        save("ajustes_mensagens", androidx.compose.ui.test.hasText("Prévia pronta"))
    }

    @Test
    fun cull() {
        seedPhotos()
        runBlocking {
            val dao = app.database.photoDao()
            val list = dao.inSession("Geral")
            list.forEachIndexed { i, p ->
                dao.setAnalysis(p.id, 80f - i * 9, i == 4, 2, if (i == 2) 1 else 0, 88f - i * 8, 0L)
                dao.setBurst(p.id, if (i < 2) list.first().id else null, i == 0)
            }
        }
        rule.setContent { JrLinkTheme { com.example.ui.cull.CullScreen(app, onBack = {}, onOpenPhoto = {}) } }
        save("selecao_rapida", androidx.compose.ui.test.hasText("Sugerida"))
        // Finger held part-way down: the card follows it and the "MARCAR" stamp locks in.
        rule.onNode(androidx.compose.ui.test.hasContentDescription("IMG_1040")).performTouchInput {
            down(center)
            repeat(10) { moveBy(androidx.compose.ui.geometry.Offset(0f, 40f)) }
        }
        rule.mainClock.advanceTimeBy(100)
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File("build/screens/culling_arrastando.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun crop() {
        seedPhotos()
        val id = runBlocking {
            val p = app.database.photoDao().inSession("Geral").last()
            val spec = com.example.core.render.CropRatio.IG_PORTRAIT.centred(900f, 600f).copy(angle = -4f)
            app.database.photoDao().setCrop(listOf(p.id), com.example.core.render.CropGeometry.fitAfterRotation(spec, 900f, 600f).encode())
            p.id
        }
        rule.setContent { JrLinkTheme { com.example.ui.crop.CropScreen(app, id, onDone = {}) } }
        save("recorte", androidx.compose.ui.test.hasText("Instagram retrato"))
    }

    /** A small catalogue served from memory, with made-up "finished look" scenes as the DNG previews. */
    private fun seedShop() {
        val api = "https://example.test/api/presets.php"
        val looks = listOf(
            Triple("Moody", 0xFF1D2426.toInt(), 0xFFD9A066.toInt()), Triple("Wedding", 0xFFE9E1D6.toInt(), 0xFFB9A48C.toInt()),
            Triple("Cinematic", 0xFF0F2A33.toInt(), 0xFFFF9A4D.toInt()), Triple("Black & White", 0xFF161616.toInt(), 0xFFD8D8D8.toInt()),
            Triple("Bright & Airy", 0xFFF3EEE6.toInt(), 0xFF9CC7D9.toInt()), Triple("Autumn", 0xFF2A1A10.toInt(), 0xFFE08A3C.toInt()),
            Triple("Orange & Teal", 0xFF0E3B44.toInt(), 0xFFFFA25A.toInt()), Triple("Portrait", 0xFF3A2A26.toInt(), 0xFFF2C6A8.toInt()),
        )
        val files = HashMap<String, ByteArray>()
        val list = StringBuilder("[")
        looks.forEachIndexed { i, (category, bg, light) ->
            val w = if (i % 3 == 0) 1024 else 682
            val h = if (i % 3 == 0) 682 else 1024
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(bg)
            val rnd = java.util.Random(i * 17L + 3)
            repeat(18) {
                val x = rnd.nextFloat() * w
                val y = rnd.nextFloat() * h
                val r = 60f + rnd.nextFloat() * 180f
                val c = ((50 + rnd.nextInt(120)) shl 24) or (light and 0x00FFFFFF)
                canvas.drawCircle(x, y, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = android.graphics.RadialGradient(x, y, r, intArrayOf(c, c and 0x00FFFFFF), null, android.graphics.Shader.TileMode.CLAMP)
                })
            }
            val url = "https://example.test/uploads/$i.dng"
            // The "before": the same scene flatter and less saturated, as a photo looks before a preset.
            val flat = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(flat).drawBitmap(bmp, 0f, 0f, Paint().apply {
                colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setSaturation(0.35f) })
                alpha = 255
            })
            Canvas(flat).drawColor(0x33808080)
            files[url] = com.example.core.shop.TestDng.build(
                com.example.core.shop.TestDng.jpeg(bmp), w, h, com.example.core.shop.TestDng.xmp(exposure = "+0.${i + 1}0"),
                original = Triple(com.example.core.shop.TestDng.jpeg(flat), w, h),
            )
            if (i > 0) list.append(',')
            list.append("""{"id":${900 + i},"filename":"presets_id_${690 + i}.dng","category":"$category","downloadUrl":"$url"}""")
        }
        files[api] = list.append(']').toString().toByteArray()
        runBlocking { app.settings.set(com.example.core.data.SettingKeys.shopUrl, api) }
        app.shop.fetcher = com.example.core.shop.TestDng.Server(files)
    }

    @Test
    fun shop() {
        seedShop()
        rule.setContent { JrLinkTheme { com.example.ui.shop.ShopScreen(app, onOpen = {}) } }
        save("loja", androidx.compose.ui.test.hasText("Moody 690"))
    }

    @Test
    fun shopSelecting() {
        seedShop()
        rule.setContent { JrLinkTheme { com.example.ui.shop.ShopScreen(app, onOpen = {}) } }
        runCatching { rule.waitUntil(15_000) { rule.onAllNodes(androidx.compose.ui.test.hasText("Moody 690")).fetchSemanticsNodes().isNotEmpty() } }
        rule.onNode(androidx.compose.ui.test.hasText("Moody 690")).performTouchInput { longClick() }
        rule.onNode(androidx.compose.ui.test.hasText("Cinematográfico 692")).performClick()
        save("loja_selecao", androidx.compose.ui.test.hasText("Salvar 2"))
    }

    @Test
    fun shopBefore() {
        seedShop()
        rule.setContent {
            JrLinkTheme { com.example.ui.shop.ShopDetailScreen(app, 900L, onBack = {}, onOpenPhoto = {}, onEditPhoto = {}) }
        }
        runCatching { rule.waitUntil(15_000) { rule.onAllNodes(androidx.compose.ui.test.hasText("Usar numa foto")).fetchSemanticsNodes().isNotEmpty() } }
        runCatching { rule.waitUntil(8_000) { false } }
        // Finger held on the picture: the photo without the preset, with the "Antes" label.
        rule.onNode(androidx.compose.ui.test.hasContentDescription("Moody 690")).performTouchInput {
            down(center)
            advanceEventTime(600)
        }
        rule.mainClock.advanceTimeBy(600)
        save("loja_antes", androidx.compose.ui.test.hasText("Antes"))
    }

    @Test
    fun shopDetail() {
        seedShop()
        seedPhotos()
        rule.setContent {
            JrLinkTheme { com.example.ui.shop.ShopDetailScreen(app, 901L, onBack = {}, onOpenPhoto = {}, onEditPhoto = {}) }
        }
        save("loja_preset", androidx.compose.ui.test.hasText("Usar numa foto"))
        rule.onNode(androidx.compose.ui.test.hasText("Na minha foto")).performClick()
        save("loja_na_minha_foto")
    }

    @Test
    fun settingsSave() {
        rule.setContent { JrLinkTheme { SettingsSectionScreen(app, SettingsSection.Save, onBack = {}) } }
        save("ajustes_salvamento", androidx.compose.ui.test.hasText("Criar e ligar todas as pastas"))
    }

    @Test
    fun clientWatermark() {
        seedPhotos()
        runBlocking {
            app.settings.set(com.example.core.data.SettingKeys.clientWatermark, true)
            app.settings.set(com.example.core.data.SettingKeys.sessionName, "")
        }
        rule.setContent { JrLinkTheme { com.example.ui.client.ClientModeScreen(app, onExit = {}, onCalibrate = {}) } }
        save("cliente_marca_dagua", androidx.compose.ui.test.hasText("Recortar"))
    }

    @Test
    fun developer() {
        runBlocking { app.settings.set(com.example.core.data.SettingKeys.developerMode, true) }
        rule.setContent { JrLinkTheme { SettingsSectionScreen(app, SettingsSection.Developer, onBack = {}) } }
        save("ajustes_desenvolvedor", androidx.compose.ui.test.hasText("Simular foto recebida"))
    }

    @Test
    fun liveView() = live("live_view")

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun liveViewLandscape() = live("live_view_deitado")

    private fun live(name: String) {
        runBlocking {
            val preset = starter("Golden")
            app.settings.set(com.example.core.data.SettingKeys.livePresetId, preset.id)
            app.settings.set(com.example.core.data.SettingKeys.livePeaking, true)
            app.settings.set(com.example.core.data.SettingKeys.liveGrid, "ig45")
        }
        // A live-view sized frame: soft background, a sharp subject in the middle and a blown sky.
        val bmp = Bitmap.createBitmap(1024, 680, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(0xFF3A4A5C.toInt())
        canvas.drawRect(0f, 0f, 1024f, 160f, Paint().apply { color = 0xFFFFFFFF.toInt() })
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until 40) {
            p.color = if (i % 2 == 0) 0xFF202020.toInt() else 0xFFE8D0B0.toInt()
            canvas.drawRect(420f + (i % 8) * 24, 300f + (i / 8) * 40, 444f + (i % 8) * 24, 340f + (i / 8) * 40, p)
        }
        val jpeg = java.io.ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
        val frames = kotlinx.coroutines.flow.flow { while (true) { emit(jpeg); kotlinx.coroutines.delay(200) } }
        rule.setContent { JrLinkTheme { com.example.ui.live.LiveViewScreen(app, onBack = {}, frames = frames) } }
        save(name, androidx.compose.ui.test.hasContentDescription("Imagem ao vivo"))
    }
}
