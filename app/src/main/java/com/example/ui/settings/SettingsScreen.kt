package com.example.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.TetherApp
import com.example.core.camera.ConnectionState
import com.example.core.data.DownloadMode
import com.example.core.data.Settings
import com.example.core.data.SettingKeys
import com.example.core.export.WatermarkLayout
import com.example.core.ptp.EosProp
import com.example.core.ptp.EosValues
import com.example.ui.theme.Tone
import kotlinx.coroutines.launch
import java.io.File

enum class SettingsSection(val route: String, val title: String, val summary: String, val icon: ImageVector) {
    Save("save", "Salvamento", "Formato, pastas RAW e JPG, sessão e nomes", Icons.Outlined.Folder),
    Review("review", "Revisão", "Slide automático, estrelas, histograma, estouro", Icons.Outlined.PhotoLibrary),
    Camera("camera", "Câmera", "Bateria, cartão, presets, bracketing", Icons.Outlined.CameraAlt),
    Share("share", "Envio e cliente", "WhatsApp leve, marca d'água, modo cliente", Icons.Outlined.Send),
    Alerts("alerts", "Alertas", "Vibração, desconexão, reenvio, resumo", Icons.Outlined.NotificationsActive),
    Display("display", "Tela", "Modo escuro total, brilho, tela ligada", Icons.Outlined.DarkMode),
    Diagnostics("diagnostics", "Diagnóstico", "Log da conexão USB", Icons.Outlined.BugReport),
}

/** Category list. Each category opens its own page so no page gets too long to scan. */
@Composable
fun SettingsScreen(onOpen: (SettingsSection) -> Unit) {
    SettingsPage(title = "Ajustes", onBack = null) {
        Spacer(Modifier.height(8.dp))
        SettingsSection.entries.forEach { section ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onOpen(section) }
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(section.icon, contentDescription = null, tint = Tone.TextMuted)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(section.title, style = MaterialTheme.typography.titleMedium)
                    Text(section.summary, style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = Tone.TextMuted)
            }
        }
    }
}

@Composable
fun SettingsSectionScreen(app: TetherApp, section: SettingsSection, onBack: () -> Unit) {
    val s by app.settings.settings.collectAsState(initial = null)
    val settings = s ?: return
    SettingsPage(title = section.title, onBack = onBack) {
        when (section) {
            SettingsSection.Save -> SaveSettings(app, settings)
            SettingsSection.Review -> ReviewSettings(app, settings)
            SettingsSection.Camera -> CameraSettings(app, settings)
            SettingsSection.Share -> ShareSettings(app, settings)
            SettingsSection.Alerts -> AlertSettings(app, settings)
            SettingsSection.Display -> DisplaySettings(app, settings)
            SettingsSection.Diagnostics -> DiagnosticsSettings(app)
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsPage(title: String, onBack: (() -> Unit)?, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                // Keeps lines readable on tablets instead of stretching edge to edge.
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } }
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            content()
        }
    }
}

// ---- Salvamento ---------------------------------------------------------------------------------

@Composable
private fun SaveSettings(app: TetherApp, s: Settings) {
    val scope = rememberCoroutineScope()
    val pickJpg = rememberFolderPicker(app) { scope.launch { app.settings.setOrClear(SettingKeys.jpgFolder, it) } }
    val pickRaw = rememberFolderPicker(app) { scope.launch { app.settings.setOrClear(SettingKeys.rawFolder, it) } }

    SettingsGroup("O QUE VEM PARA O CELULAR") {
        ChoiceRow("Formato", DownloadMode.entries.map { it to it.label }, s.downloadMode) {
            scope.launch { app.settings.setDownloadMode(it) }
        }
        InfoText("A câmera grava tudo no cartão. Aqui você escolhe o que também é salvo no celular.")
    }

    SettingsGroup("PASTAS") {
        FolderRow("Pasta dos JPG", app.store.describeTree(s.jpgFolder), s.downloadMode.jpg,
            onPick = { pickJpg.launch(null) }, onReset = s.jpgFolder?.let { { scope.launch { app.settings.setOrClear(SettingKeys.jpgFolder, null) } } })
        FolderRow("Pasta dos RAW (CR2)", app.store.describeTree(s.rawFolder), s.downloadMode.raw,
            onPick = { pickRaw.launch(null) }, onReset = s.rawFolder?.let { { scope.launch { app.settings.setOrClear(SettingKeys.rawFolder, null) } } })
    }

    SettingsGroup("SESSÃO / CLIENTE") {
        var name by remember { mutableStateOf(s.sessionName) }
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                scope.launch { app.settings.set(SettingKeys.sessionName, it) }
            },
            label = { Text("Nome da sessão") },
            placeholder = { Text("Ex.: Casamento Ana e Leo") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        val example = s.sessionName.ifBlank { "Casamento" }
        ToggleRow("Criar subpasta com o nome da sessão", "Ex.: …/JPG/$example/", s.sessionSubfolder) {
            scope.launch { app.settings.set(SettingKeys.sessionSubfolder, it) }
        }
        ToggleRow("Renomear arquivos com o nome da sessão", "Ex.: ${example}_0001.JPG em vez de IMG_1234.JPG", s.renameWithSession) {
            scope.launch { app.settings.set(SettingKeys.renameWithSession, it) }
        }
    }
}

@Composable
private fun rememberFolderPicker(app: TetherApp, onPicked: (String) -> Unit) =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            app.store.takePersistablePermission(uri)
            onPicked(uri.toString())
        }
    }

@Composable
private fun FolderRow(title: String, path: String, active: Boolean, onPick: () -> Unit, onReset: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = if (active) Tone.Text else Tone.TextMuted)
        Text(path, style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
        if (!active) Text("Não usada no formato atual", style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPick) { Text("Escolher pasta") }
            onReset?.let { TextButton(onClick = it) { Text("Usar padrão") } }
        }
    }
}

// ---- Revisão -----------------------------------------------------------------------------------

@Composable
private fun ReviewSettings(app: TetherApp, s: Settings) {
    val scope = rememberCoroutineScope()
    fun set(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, v: Boolean) = scope.launch { app.settings.set(key, v) }

    SettingsGroup("FOTO NOVA") {
        ToggleRow("Ir para a foto nova automaticamente", "Com uma foto aberta em tela cheia, desliza para a que acabou de chegar", s.autoSlideToNew) { set(SettingKeys.autoSlideToNew, it) }
        ToggleRow("Só se eu estiver na última foto", "Não interrompe quando você está revendo fotos antigas", s.autoSlideOnlyAtLatest, enabled = s.autoSlideToNew) { set(SettingKeys.autoSlideOnlyAtLatest, it) }
        ToggleRow("Abrir em tela cheia ao receber", "Na tela Sessão, cada foto nova abre direto no visualizador", s.autoOpenViewer) { set(SettingKeys.autoOpenViewer, it) }
    }
    SettingsGroup("AVALIAÇÃO") {
        ToggleRow("Estrelas (1 a 5)", "Mostra as estrelas no visualizador e o filtro na galeria", s.ratingEnabled) { set(SettingKeys.ratingEnabled, it) }
    }
    SettingsGroup("EXPOSIÇÃO") {
        ToggleRow("Histograma", null, s.histogramEnabled) { set(SettingKeys.histogramEnabled, it) }
        ToggleRow("Histograma RGB", "Desligado: só luminância", s.histogramRgb, enabled = s.histogramEnabled) { set(SettingKeys.histogramRgb, it) }
        ToggleRow("Alerta de estouro", "Áreas estouradas piscam em vermelho ao abrir a foto", s.highlightAlert) { set(SettingKeys.highlightAlert, it) }
        SliderRow("Limite do estouro", s.highlightThreshold, 235..255, enabled = s.highlightAlert) {
            scope.launch { app.settings.set(SettingKeys.highlightThreshold, it) }
        }
        InfoText("255 marca só o branco total. Valores menores avisam antes, quando o detalhe já começa a sumir.")
    }
}

// ---- Câmera ------------------------------------------------------------------------------------

@Composable
private fun CameraSettings(app: TetherApp, s: Settings) {
    val scope = rememberCoroutineScope()
    val state by app.camera.state.collectAsState()
    fun set(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, v: Boolean) = scope.launch { app.settings.set(key, v) }

    SettingsGroup("CONEXÃO") {
        ToggleRow("Manter a câmera ligada", "Evita o desligamento automático da 50D durante a sessão", s.keepCameraAwake) { set(SettingKeys.keepCameraAwake, it) }
        ToggleRow("Mostrar bateria da câmera", "No topo da Sessão e aviso quando estiver baixa", s.showBattery) { set(SettingKeys.showBattery, it) }
        if (state is ConnectionState.Connected) {
            OutlinedButton(onClick = app.camera::disconnect, modifier = Modifier.padding(vertical = 8.dp)) { Text("Encerrar conexão") }
        }
    }
    SettingsGroup("CARTÃO") {
        ToggleRow("Navegar no cartão", "Botão na Sessão para ver e baixar fotos que já estão no cartão", s.cardBrowserEnabled) { set(SettingKeys.cardBrowserEnabled, it) }
    }
    SettingsGroup("PRESETS DE EXPOSIÇÃO") {
        ToggleRow("Usar presets", "Salve ISO, Av, Tv, ±EV e WB e aplique com um toque na Sessão", s.presetsEnabled) { set(SettingKeys.presetsEnabled, it) }
        val presets = s.exposurePresets
        if (presets.isEmpty()) InfoText("Nenhum preset salvo. Na Sessão, toque em Presets → Salvar atual.")
        presets.forEachIndexed { i, p ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        listOfNotNull(
                            p.shutter?.let { EosValues.label(EosProp.SHUTTER_SPEED, it) },
                            p.aperture?.let { EosValues.label(EosProp.APERTURE, it) },
                            p.iso?.let { "ISO " + EosValues.label(EosProp.ISO, it) },
                            p.exposureCompensation?.let { EosValues.label(EosProp.EXPOSURE_COMPENSATION, it) + " EV" },
                            p.whiteBalance?.let { EosValues.label(EosProp.WHITE_BALANCE, it) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = Tone.TextMuted,
                    )
                }
                IconButton(onClick = { scope.launch { app.settings.savePresets(presets.filterIndexed { j, _ -> j != i }) } }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Apagar preset ${p.name}", tint = Tone.TextMuted)
                }
            }
        }
    }
    SettingsGroup("BRACKETING (AEB)") {
        ToggleRow("Bracketing pelo app", "Botão na Sessão que faz a sequência variando a exposição", s.aebEnabled) { set(SettingKeys.aebEnabled, it) }
        ChoiceRow("Fotos por sequência", listOf(3 to "3", 5 to "5", 7 to "7"), s.aebShots, enabled = s.aebEnabled) {
            scope.launch { app.settings.set(SettingKeys.aebShots, it) }
        }
        ChoiceRow(
            "Intervalo entre fotos",
            listOf(1 to "1/3", 2 to "2/3", 3 to "1 EV", 4 to "1⅓", 5 to "1⅔", 6 to "2 EV"),
            s.aebStepThirds,
            enabled = s.aebEnabled,
        ) { scope.launch { app.settings.set(SettingKeys.aebStepThirds, it) } }
        InfoText("Em P, Tv e Av o app varia a compensação (até ±2 EV). Em M varia a velocidade. No final volta ao valor original.")
    }
}

// ---- Envio e cliente ---------------------------------------------------------------------------

@Composable
private fun ShareSettings(app: TetherApp, s: Settings) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    fun set(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, v: Boolean) = scope.launch { app.settings.set(key, v) }
    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val path = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.export.importLogo(uri) }
            app.settings.setOrClear(SettingKeys.watermarkPath, path)
            if (path != null) app.settings.set(SettingKeys.watermarkEnabled, true)
        }
    }

    SettingsGroup("ENVIO LEVE (WHATSAPP)") {
        ToggleRow("Opção \"JPG leve\" ao enviar", "Cópia reduzida, rápida no 4G. O original não muda.", s.lightShareEnabled) { set(SettingKeys.lightShareEnabled, it) }
        ChoiceRow(
            "Tamanho (lado maior)",
            listOf(1080 to "1080 px", 1600 to "1600 px", 2048 to "2048 px", 0 to "Original"),
            s.lightShareSize,
            enabled = s.lightShareEnabled,
        ) { scope.launch { app.settings.set(SettingKeys.lightShareSize, it) } }
        SliderRow("Qualidade JPG", s.lightShareQuality, 60..100, step = 5, enabled = s.lightShareEnabled, format = { "$it%" }) {
            scope.launch { app.settings.set(SettingKeys.lightShareQuality, it) }
        }
    }

    SettingsGroup("MARCA D'ÁGUA") {
        ToggleRow("Aplicar marca d'água", "Só nas cópias leves enviadas. Os arquivos salvos ficam sem marca.", s.watermarkEnabled, enabled = s.watermarkPath != null) {
            set(SettingKeys.watermarkEnabled, it)
        }
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickLogo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Text(if (s.watermarkPath == null) "Escolher logo (PNG)" else "Trocar logo")
            }
        }
        if (s.watermarkPath != null) {
            WatermarkPreview(s)
            Text("Posição", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
            PositionGrid(s.watermarkPosition) { scope.launch { app.settings.set(SettingKeys.watermarkPosition, it) } }
            SliderRow("Tamanho", s.watermarkSizePct, 5..60, format = { "$it%" }) { scope.launch { app.settings.set(SettingKeys.watermarkSizePct, it) } }
            SliderRow("Opacidade", s.watermarkOpacity, 10..100, step = 5, format = { "$it%" }) { scope.launch { app.settings.set(SettingKeys.watermarkOpacity, it) } }
            SliderRow("Margem", s.watermarkMarginPct, 0..15, format = { "$it%" }) { scope.launch { app.settings.set(SettingKeys.watermarkMarginPct, it) } }
        } else {
            InfoText("Use um PNG com fundo transparente (ex.: logo Jr_Tech_OFC).")
        }
    }

    SettingsGroup("MODO CLIENTE") {
        ToggleRow("Mostrar botão Modo cliente", "Tela cheia só com as fotos, para o cliente acompanhar", s.clientModeEnabled) { set(SettingKeys.clientModeEnabled, it) }
        ToggleRow("Mostrar nome e exposição", null, s.clientShowInfo, enabled = s.clientModeEnabled) { set(SettingKeys.clientShowInfo, it) }
        ToggleRow("Só favoritas", "Mostra apenas as fotos marcadas com estrela de favorita", s.clientOnlyFavorites, enabled = s.clientModeEnabled) { set(SettingKeys.clientOnlyFavorites, it) }
        ChoiceRow(
            "Passar as fotos",
            listOf(0 to "Só quando chega foto", 3 to "A cada 3 s", 5 to "5 s", 10 to "10 s"),
            s.clientSlideSeconds,
            enabled = s.clientModeEnabled,
        ) { scope.launch { app.settings.set(SettingKeys.clientSlideSeconds, it) } }
        var pin by remember { mutableStateOf(s.clientPin) }
        OutlinedTextField(
            value = pin,
            onValueChange = { v ->
                pin = v.filter(Char::isDigit).take(6)
                scope.launch { app.settings.set(SettingKeys.clientPin, pin) }
            },
            label = { Text("PIN para sair (opcional)") },
            placeholder = { Text("Vazio = sem PIN") },
            singleLine = true,
            enabled = s.clientModeEnabled,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        InfoText("Para sair do modo cliente, segure o dedo na tela por 3 segundos.")
    }
}

@Composable
private fun WatermarkPreview(s: Settings) {
    val path = s.watermarkPath ?: return
    val logoSize = remember(path) {
        BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(path, it) }
            .let { it.outWidth.coerceAtLeast(1) to it.outHeight.coerceAtLeast(1) }
    }
    BoxWithConstraints(
        Modifier.fillMaxWidth().aspectRatio(3f / 2f).clip(MaterialTheme.shapes.small).background(Tone.SurfaceHigh),
    ) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.roundToPx() }
        val hPx = with(density) { maxHeight.roundToPx() }
        val r = WatermarkLayout.place(wPx, hPx, logoSize.first, logoSize.second, s.watermarkPosition, s.watermarkSizePct, s.watermarkMarginPct)
        Text("Prévia", style = MaterialTheme.typography.labelSmall, color = Tone.TextMuted, modifier = Modifier.padding(8.dp))
        AsyncImage(
            model = File(path),
            contentDescription = "Logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .offset(x = with(density) { r.left.toDp() }, y = with(density) { r.top.toDp() })
                .size(with(density) { r.width().toDp() }, with(density) { r.height().toDp() })
                .alpha(s.watermarkOpacity / 100f),
        )
    }
}

@Composable
private fun PositionGrid(selected: Int, onPick: (Int) -> Unit) {
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in 0..2) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (col in 0..2) {
                    val index = row * 3 + col
                    Box(
                        Modifier
                            .size(width = 56.dp, height = 40.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(if (index == selected) Tone.Accent else Tone.SurfaceHigh)
                            .border(1.dp, Tone.Line, MaterialTheme.shapes.extraSmall)
                            .clickable { onPick(index) },
                    )
                }
            }
        }
    }
}

// ---- Alertas -----------------------------------------------------------------------------------

@Composable
private fun AlertSettings(app: TetherApp, s: Settings) {
    val scope = rememberCoroutineScope()
    fun set(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, v: Boolean) = scope.launch { app.settings.set(key, v) }

    SettingsGroup("AVISOS") {
        ToggleRow("Vibrar a cada foto recebida", "Um toque curto, sem som", s.vibrateOnReceive) { set(SettingKeys.vibrateOnReceive, it) }
        ToggleRow("Alerta se a câmera desconectar", "Vibração forte e notificação", s.alertOnDisconnect) { set(SettingKeys.alertOnDisconnect, it) }
        ToggleRow("Aviso de cartão quase cheio", null, s.cardLowWarning) { set(SettingKeys.cardLowWarning, it) }
        SliderRow("Avisar abaixo de", s.cardLowShots, 10..300, step = 10, enabled = s.cardLowWarning, format = { "$it fotos" }) {
            scope.launch { app.settings.set(SettingKeys.cardLowShots, it) }
        }
        ToggleRow("Aviso de memória do celular", null, s.phoneLowWarning) { set(SettingKeys.phoneLowWarning, it) }
        SliderRow("Avisar abaixo de", s.phoneLowGb, 1..20, enabled = s.phoneLowWarning, format = { "$it GB" }) {
            scope.launch { app.settings.set(SettingKeys.phoneLowGb, it) }
        }
    }
    SettingsGroup("REENVIO AUTOMÁTICO") {
        ToggleRow("Baixar fotos pendentes ao reconectar", "Fotos feitas com o cabo solto, ou que falharam, vêm do cartão quando a câmera volta", s.autoResend) { set(SettingKeys.autoResend, it) }
        ToggleRow("Perguntar antes de baixar", null, s.resendAsk, enabled = s.autoResend) { set(SettingKeys.resendAsk, it) }
    }
    SettingsGroup("FIM DA SESSÃO") {
        ToggleRow("Mostrar resumo da sessão", "Ao encerrar: total, espaço usado, favoritas e envio ao Lightroom", s.sessionSummary) { set(SettingKeys.sessionSummary, it) }
    }
}

// ---- Tela --------------------------------------------------------------------------------------

@Composable
private fun DisplaySettings(app: TetherApp, s: Settings) {
    val scope = rememberCoroutineScope()
    SettingsGroup("MODO ESCURO TOTAL") {
        ToggleRow("Ativar", "Cores ainda mais escuras e brilho reduzido, para igrejas e eventos à noite", s.nightMode) {
            scope.launch { app.settings.set(SettingKeys.nightMode, it) }
        }
        SliderRow("Brilho da tela", s.nightBrightness, 1..100, enabled = s.nightMode, format = { "$it%" }) {
            scope.launch { app.settings.set(SettingKeys.nightBrightness, it) }
        }
    }
    SettingsGroup("TELA") {
        ToggleRow("Manter a tela ligada", "Enquanto a câmera estiver conectada", s.keepScreenOn) {
            scope.launch { app.settings.set(SettingKeys.keepScreenOn, it) }
        }
    }
}

// ---- Diagnóstico -------------------------------------------------------------------------------

@Composable
private fun DiagnosticsSettings(app: TetherApp) {
    val log by app.camera.log.collectAsState()
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
        Text("Últimos eventos da conexão", style = MaterialTheme.typography.bodyMedium, color = Tone.TextMuted, modifier = Modifier.weight(1f))
        TextButton(onClick = { copyLog(context, log) }) { Text("Copiar log") }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .clip(MaterialTheme.shapes.small)
            .background(Tone.Surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (log.isEmpty()) Text("Sem eventos ainda.", color = Tone.TextMuted, style = MaterialTheme.typography.bodySmall)
        log.takeLast(80).forEach {
            Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Tone.TextMuted)
        }
    }
}

private fun copyLog(context: Context, lines: List<String>) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Log Tether 50D", lines.joinToString("\n")))
}
