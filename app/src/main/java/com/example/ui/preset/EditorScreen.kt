package com.example.ui.preset

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.TetherApp
import com.example.core.analysis.ImageAnalyzer
import com.example.core.data.Photo
import com.example.core.data.SettingKeys
import com.example.core.preset.CurvePoint
import com.example.core.preset.Preset
import com.example.core.preset.PresetSettings
import com.example.core.render.Looks
import com.example.ui.theme.Tone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the editor is working on: a preset (id 0 = new one) or one photo's calibration. */
sealed interface EditTarget {
    data class PresetTarget(val id: Long) : EditTarget
    data class PhotoTarget(val id: Long) : EditTarget
}

@Composable
fun EditorScreen(app: TetherApp, target: EditTarget, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loaded by remember { mutableStateOf(false) }
    var preset by remember { mutableStateOf<Preset?>(null) }
    var photo by remember { mutableStateOf<Photo?>(null) }
    var basePresetId by remember { mutableStateOf<Long?>(null) }
    var source by remember { mutableStateOf<String?>(null) }
    var settings by remember { mutableStateOf(PresetSettings()) }
    val history = remember { mutableStateListOf<PresetSettings>() }
    var historyIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(target) {
        when (target) {
            is EditTarget.PresetTarget -> {
                val p = if (target.id > 0) app.presets.get(target.id) else null
                preset = p ?: Preset(name = "Meu preset", group = "Meus presets")
                settings = preset!!.settings
                source = app.database.photoDao().latestDisplayUri() ?: SampleImage.uri(context)
            }
            is EditTarget.PhotoTarget -> {
                val ph = app.database.photoDao().get(target.id)
                photo = ph
                basePresetId = ph?.presetId
                settings = ph?.let { app.looks.settingsFor(it) } ?: PresetSettings()
                source = ph?.displayUri
            }
        }
        history.clear(); history.add(settings); historyIndex = 0
        loaded = true
    }

    fun commit() {
        if (history.getOrNull(historyIndex) == settings) return
        while (history.size > historyIndex + 1) history.removeAt(history.lastIndex)
        history.add(settings)
        if (history.size > 60) history.removeAt(0)
        historyIndex = history.lastIndex
    }

    var rendered by remember { mutableStateOf<Bitmap?>(null) }
    var original by remember { mutableStateOf<Bitmap?>(null) }
    var showOriginal by remember { mutableStateOf(false) }
    var showClipping by remember { mutableStateOf(false) }
    var clipMask by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(source) {
        val src = source ?: return@LaunchedEffect
        original = app.looks.base(src, Looks.EDITOR_EDGE)
        snapshotFlow { settings }.collectLatest { s ->
            busy = true
            rendered = app.looks.render(src, s, Looks.EDITOR_EDGE)
            busy = false
        }
    }
    LaunchedEffect(rendered, showClipping) {
        val bmp = rendered
        clipMask = if (!showClipping || bmp == null) null else withContext(Dispatchers.Default) { clippingMask(bmp) }
    }

    var tab by remember { mutableStateOf(EditorTab.Light) }
    var menu by remember { mutableStateOf(false) }
    var askName by remember { mutableStateOf<String?>(null) } // "save" or "new-from-photo"
    var confirmExit by remember { mutableStateOf(false) }
    val dirty = history.size > 1 || historyIndex > 0

    BackHandler { if (dirty) confirmExit = true else onDone() }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier.fillMaxWidth().background(Tone.Background).statusBarsPadding().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { if (dirty) confirmExit = true else onDone() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
            Column(Modifier.weight(1f)) {
                Text(
                    when (target) {
                        is EditTarget.PresetTarget -> preset?.name ?: "Preset"
                        is EditTarget.PhotoTarget -> "Calibrar ${photo?.savedBase.orEmpty()}"
                    },
                    style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text("Segure a foto: antes", style = MaterialTheme.typography.labelSmall, color = Tone.TextMuted)
            }
            IconButton(onClick = { showClipping = !showClipping }) {
                Icon(Icons.Outlined.WbSunny, contentDescription = "Mostrar estouro", tint = if (showClipping) Tone.Info else Tone.TextMuted)
            }
            IconButton(enabled = historyIndex > 0, onClick = { historyIndex--; settings = history[historyIndex] }) {
                Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Desfazer")
            }
            IconButton(enabled = historyIndex < history.lastIndex, onClick = { historyIndex++; settings = history[historyIndex] }) {
                Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = "Refazer")
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Mais opções") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Tone.SurfaceHigh) {
                    DropdownMenuItem(text = { Text("Zerar todos os ajustes") }, onClick = {
                        menu = false; settings = PresetSettings(settings.values.filterKeys { it in PresetSettings.META_KEYS }); commit()
                    })
                    if (target is EditTarget.PhotoTarget) {
                        DropdownMenuItem(text = { Text("Aplicar às próximas fotos") }, onClick = {
                            menu = false
                            scope.launch {
                                basePresetId?.let { app.settings.set(SettingKeys.sessionPresetId, it) }
                                app.settings.set(SettingKeys.sessionAdjust, settings.encode())
                            }
                        })
                        DropdownMenuItem(text = { Text("Salvar como preset") }, onClick = { menu = false; askName = "new-from-photo" })
                    } else {
                        DropdownMenuItem(text = { Text("Renomear") }, onClick = { menu = false; askName = "save" })
                    }
                }
            }
            IconButton(onClick = {
                when (target) {
                    is EditTarget.PresetTarget -> if (target.id == 0L) askName = "save" else scope.launch {
                        app.presets.save(preset!!.copy(settings = settings)); onDone()
                    }
                    is EditTarget.PhotoTarget -> scope.launch { applyToPhoto(app, photo, basePresetId, settings); onDone() }
                }
            }) { Icon(Icons.Filled.Check, contentDescription = "Salvar", tint = Tone.Accent) }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) { detectTapGestures(onPress = { showOriginal = true; tryAwaitRelease(); showOriginal = false }) },
            contentAlignment = Alignment.Center,
        ) {
            val shown = if (showOriginal) original else rendered ?: original
            shown?.let { Image(it.asImageBitmap(), contentDescription = if (showOriginal) "Original" else "Com ajustes", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
            if (!showOriginal) clipMask?.let { Image(it.asImageBitmap(), contentDescription = "Áreas estouradas", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
            if (showOriginal) {
                Text("Original", style = MaterialTheme.typography.labelMedium, color = Color.White,
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 2.dp))
            }
            if (!loaded || (busy && rendered == null)) CircularProgressIndicator(color = Tone.Info, modifier = Modifier.size(32.dp))
        }

        Column(Modifier.fillMaxWidth().background(Tone.Background).navigationBarsPadding()) {
            if (target is EditTarget.PhotoTarget) {
                val all by app.presets.presets.collectAsState(initial = emptyList())
                PresetStrip(all, basePresetId) { chosen ->
                    basePresetId = chosen?.id
                    settings = chosen?.settings ?: PresetSettings()
                    commit()
                }
            }
            ScrollableTabRow(
                selectedTabIndex = tab.ordinal,
                containerColor = Tone.Background,
                contentColor = Tone.Text,
                edgePadding = 8.dp,
                indicator = { positions ->
                    TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[tab.ordinal]), color = Tone.Accent)
                },
            ) {
                EditorTab.entries.forEach { t ->
                    Tab(selected = tab == t, onClick = { tab = t }, text = { Text(t.label) },
                        selectedContentColor = Tone.Text, unselectedContentColor = Tone.TextMuted)
                }
            }
            Column(Modifier.fillMaxWidth().height(300.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp)) {
                EditorPanel(tab, settings, onChange = { settings = it }, onCommit = ::commit)
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    askName?.let { mode ->
        NameDialog(
            initialName = if (mode == "save") preset?.name.orEmpty() else "Calibração ${photo?.savedBase.orEmpty()}",
            initialGroup = if (mode == "save") preset?.group ?: "Meus presets" else "Meus presets",
            onDismiss = { askName = null },
            onSave = { name, group ->
                askName = null
                scope.launch {
                    if (mode == "save") {
                        app.presets.save(preset!!.copy(name = name, group = group, settings = settings))
                        onDone()
                    } else {
                        val id = app.presets.save(Preset(name = name, group = group, settings = settings, source = "criado"))
                        basePresetId = id
                        applyToPhoto(app, photo, id, settings)
                        onDone()
                    }
                }
            },
        )
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            containerColor = Tone.Surface,
            title = { Text("Descartar alterações?") },
            text = { Text("Os ajustes feitos agora não serão salvos.") },
            confirmButton = { TextButton(onClick = { confirmExit = false; onDone() }) { Text("Descartar", color = Tone.Error) } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Continuar editando") } },
        )
    }
}

/** Saves a photo's calibration: only the preset link when nothing was changed, else the full settings. */
private suspend fun applyToPhoto(app: TetherApp, photo: Photo?, presetId: Long?, settings: PresetSettings) {
    val ph = photo ?: return
    val presetSettings = presetId?.let { app.presets.get(it)?.settings }
    val adjust = if (presetSettings == settings) null else settings.encode()
    runCatching { app.looks.setLook(ph, presetId, adjust) }
}

private fun clippingMask(bmp: Bitmap): Bitmap? {
    val px = IntArray(bmp.width * bmp.height)
    bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    val m = ImageAnalyzer.measure(px, 250)
    if (m.clippedCount == 0) return null
    val mask = IntArray(px.size) { if (m.clipped[it]) 0xFFFF3B30.toInt() else 0 }
    return Bitmap.createBitmap(mask, bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
}

@Composable
fun PresetStrip(presets: List<Preset>, selected: Long?, onPick: (Preset?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PresetChip("Sem preset", selected == null) { onPick(null) }
        presets.forEach { p -> PresetChip(p.name, selected == p.id) { onPick(p) } }
    }
}

@Composable
fun PresetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Tone.SurfaceHigh, selectedLabelColor = Tone.Accent, labelColor = Tone.TextMuted,
        ),
    )
}

@Composable
fun NameDialog(initialName: String, initialGroup: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    var group by remember { mutableStateOf(initialGroup) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Tone.Surface,
        title = { Text("Salvar preset") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, label = { Text("Nome") }, singleLine = true)
                OutlinedTextField(value = group, onValueChange = { group = it.take(40) }, label = { Text("Grupo") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim(), group.trim()) }, enabled = name.isNotBlank()) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** The sliders and tools for one tab. */
@Composable
private fun EditorPanel(tab: EditorTab, s: PresetSettings, onChange: (PresetSettings) -> Unit, onCommit: () -> Unit) {
    @Composable
    fun sliders(list: List<SliderSpec>) = list.forEach { spec ->
        AdjustSlider(spec, s.num(spec.key, spec.default.toDouble()).toFloat(), { onChange(s.with(spec.key, it.toDouble())) }, onCommit)
    }

    when (tab) {
        EditorTab.Light -> sliders(EditorControls.light)
        EditorTab.Color -> {
            PanelTitle("Balanço de branco")
            sliders(if (EditorControls.usesKelvin(s)) EditorControls.kelvin else EditorControls.wbRelative)
            PanelTitle("Presença")
            sliders(EditorControls.presence)
            val mono = EditorControls.isMono(s)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("Preto e branco", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(
                    checked = mono,
                    onCheckedChange = { on -> onChange(s.withText("ConvertToGrayscale", if (on) "True" else "False").withText("Treatment", if (on) "Black & White" else "Color")); onCommit() },
                    colors = SwitchDefaults.colors(checkedTrackColor = Tone.Accent, checkedThumbColor = Tone.OnAccent),
                )
            }
            MixerPanel(s, mono, onChange, onCommit)
        }
        EditorTab.Effects -> {
            sliders(EditorControls.effects)
            PanelTitle("Vinheta")
            sliders(EditorControls.vignette)
            PanelTitle("Granulação")
            sliders(EditorControls.grain)
        }
        EditorTab.Detail -> {
            PanelTitle("Nitidez")
            sliders(EditorControls.sharpening)
            PanelTitle("Ruído")
            sliders(EditorControls.noise)
        }
        EditorTab.Curve -> CurvePanel(s, onChange, onCommit)
        EditorTab.Grading -> GradingPanel(s, onChange, onCommit)
        EditorTab.Calibration -> sliders(EditorControls.calibration)
    }
}

@Composable
private fun MixerPanel(s: PresetSettings, mono: Boolean, onChange: (PresetSettings) -> Unit, onCommit: () -> Unit) {
    var band by remember { mutableStateOf("Orange") }
    PanelTitle(if (mono) "Mistura P&B" else "Mistura de cores")
    ChipRow { EditorControls.mixer.forEach { (key, color) -> Swatch(color, band == key, EditorControls.mixerLabels.getValue(key)) { band = key } } }
    val label = EditorControls.mixerLabels.getValue(band)
    val specs = if (mono) listOf(SliderSpec("GrayMixer$band", label, -100f, 100f)) else listOf(
        SliderSpec("HueAdjustment$band", "Matiz · $label", -100f, 100f),
        SliderSpec("SaturationAdjustment$band", "Saturação · $label", -100f, 100f),
        SliderSpec("LuminanceAdjustment$band", "Luminância · $label", -100f, 100f),
    )
    specs.forEach { spec -> AdjustSlider(spec, s.num(spec.key).toFloat(), { onChange(s.with(spec.key, it.toDouble())) }, onCommit) }
}

@Composable
private fun CurvePanel(s: PresetSettings, onChange: (PresetSettings) -> Unit, onCommit: () -> Unit) {
    var channel by remember { mutableIntStateOf(0) }
    val channels = listOf("RGB" to Tone.Text, "Vermelho" to Color(0xFFE5484D), "Verde" to Color(0xFF46A758), "Azul" to Color(0xFF3E63DD))
    PanelTitle("Curva de pontos")
    ChipRow {
        channels.forEachIndexed { i, (label, _) -> PresetChip(label, channel == i) { channel = i } }
    }
    val key = PresetSettings.CURVE_KEYS[channel]
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        CurveEditor(
            points = s.curve(key),
            color = channels[channel].second,
            onChange = { pts -> onChange(s.withCurve(key, if (pts == PresetSettings.IDENTITY_CURVE) null else pts)) },
            onCommit = onCommit,
        )
    }
    Text("Toque para criar um ponto, arraste para mover, toque duas vezes para apagar.",
        style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted, modifier = Modifier.padding(top = 6.dp))
    TextButton(onClick = { onChange(s.withCurve(key, null)); onCommit() }) { Text("Restaurar curva") }
    PanelTitle("Curva paramétrica")
    EditorControls.parametric.forEach { spec -> AdjustSlider(spec, s.num(spec.key).toFloat(), { onChange(s.with(spec.key, it.toDouble())) }, onCommit) }
}

@Composable
private fun GradingPanel(s: PresetSettings, onChange: (PresetSettings) -> Unit, onCommit: () -> Unit) {
    var zone by remember { mutableIntStateOf(0) }
    val (key, _) = EditorControls.gradingZones[zone]
    ChipRow { EditorControls.gradingZones.forEachIndexed { i, (_, label) -> PresetChip(label, zone == i) { zone = i } } }
    val hueKey = "ColorGrade${key}Hue"
    val satKey = "ColorGrade${key}Sat"
    val lumKey = "ColorGrade${key}Lum"
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        ColorWheel(
            hue = s.num(hueKey).toFloat(),
            sat = s.num(satKey).toFloat(),
            onChange = { h, sat -> onChange(s.with(hueKey, h.toDouble()).with(satKey, sat.toDouble())) },
            onCommit = onCommit,
        )
    }
    listOf(
        SliderSpec(hueKey, "Matiz", 0f, 360f, track = Track.Hue),
        SliderSpec(satKey, "Saturação", 0f, 100f),
        SliderSpec(lumKey, "Luminância", -100f, 100f),
    ).plus(EditorControls.gradingCommon).forEach { spec ->
        AdjustSlider(spec, s.num(spec.key, spec.default.toDouble()).toFloat(), { onChange(s.with(spec.key, it.toDouble())) }, onCommit)
    }
    Spacer(Modifier.width(1.dp))
}
