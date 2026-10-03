package com.example.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.BrandingWatermark
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Exposure
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.HdrStrong
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LastPage
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.SdCardAlert
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.UsbOff
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WbSunny
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
import com.example.TetherApp
import com.example.core.camera.ConnectionState
import com.example.core.data.DEFAULT_SHOP_URL
import com.example.core.data.DownloadMode
import androidx.compose.material.icons.outlined.Storefront
import com.example.core.data.Settings
import com.example.core.data.SettingKeys
import com.example.core.data.WatermarkSource
import com.example.core.messages.MessageDefaults
import com.example.core.messages.MessageKind
import com.example.ui.photos.legacyTemplate
import com.example.ui.photos.messages
import com.example.ui.photos.template
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import com.example.core.export.WatermarkLayout
import com.example.core.ptp.EosProp
import com.example.core.ptp.EosValues
import com.example.ui.theme.Tone
import com.example.ui.theme.WordmarkStyle
import com.example.ui.components.BrandSymbol
import com.example.BuildConfig
import com.example.core.log.AppLog
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Code

/** The app's chain, Capturar → Controlar → Melhorar → Entregar, plus the system: how Ajustes is grouped. */
enum class SettingsModule(val title: String) {
    Camera("Câmera"),
    Capture("Captura e salvamento"),
    Photography("Fotografia"),
    Editing("Edição"),
    Delivery("Entrega"),
    System("Sistema"),
}

enum class SettingsSection(val route: String, val title: String, val summary: String, val icon: ImageVector, val module: SettingsModule) {
    Profiles("profiles", "Perfis e backup", "Sets prontos por evento, salvar e restaurar tudo", Icons.Outlined.SettingsSuggest, SettingsModule.System),
    Save("save", "Salvamento", "Formato, pastas RAW e JPG, sessão e nomes", Icons.Outlined.Folder, SettingsModule.Capture),
    Review("review", "Revisão", "Slide automático, galeria, culling, histograma", Icons.Outlined.PhotoLibrary, SettingsModule.Photography),
    Camera("camera", "Câmera", "Bateria, cartão, exposições salvas, bracketing", Icons.Outlined.CameraAlt, SettingsModule.Camera),
    Share("share", "Envio e cliente", "WhatsApp, marca d'água, modo cliente", Icons.Outlined.Send, SettingsModule.Delivery),
    Delivery("delivery", "Entrega local", "Wi-Fi do fotógrafo, portal, códigos e QR", Icons.Outlined.WifiTethering, SettingsModule.Delivery),
    Payments("payments", "Pagamentos", "Pix copia e cola, valores, pacotes e formas de pagamento", Icons.Outlined.Payments, SettingsModule.Delivery),
    Express("express", "Fotografia Express", "Sem código, prévia com marca e liberar após o pagamento", Icons.Outlined.Collections, SettingsModule.Delivery),
    Presets("presets", "Presets", "Look das fotos novas, cópia editada, pastas", Icons.Outlined.Style, SettingsModule.Editing),
    Messages("messages", "Mensagens", "Textos prontos para o WhatsApp do cliente", Icons.AutoMirrored.Outlined.Chat, SettingsModule.Delivery),
    Alerts("alerts", "Alertas", "Vibração, desconexão, reenvio, resumo", Icons.Outlined.NotificationsActive, SettingsModule.System),
    Display("display", "Tela", "Modo escuro total, brilho, tela ligada", Icons.Outlined.DarkMode, SettingsModule.System),
    Diagnostics("diagnostics", "Diagnóstico", "Log do app salvo no celular, pasta e envio", Icons.Outlined.BugReport, SettingsModule.System),
    Developer("developer", "Desenvolvedor", "Testes da câmera, foto simulada, teste de entrega", Icons.Outlined.Code, SettingsModule.System),
}

/** Category list. Each category opens its own page so no page gets too long to scan. */
@Composable
fun SettingsScreen(onOpen: (SettingsSection) -> Unit) {
    val app = LocalContext.current.applicationContext as? TetherApp
    val developer by (app?.settings?.settings?.collectAsState(initial = null) ?: remember { mutableStateOf(null) })
    SettingsPage(title = "Ajustes", onBack = null) {
        Spacer(Modifier.height(8.dp))
        val shown = SettingsSection.entries.filter { it != SettingsSection.Developer || developer?.developerMode == true }
        SettingsModule.entries.forEach { module ->
          val sections = shown.filter { it.module == module }
          if (sections.isNotEmpty()) Text(
              module.title,
              style = MaterialTheme.typography.labelLarge,
              color = Tone.TextMuted,
              modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 2.dp),
          )
          sections.forEach { section ->
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
        Spacer(Modifier.height(40.dp))
        AboutFooter(app, developer?.developerMode == true)
    }
}

@Composable
private fun AboutFooter(app: TetherApp?, unlocked: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Seven taps on the brand unlock Desenvolvedor, like Android's build number.
    var taps by remember { mutableStateOf(0) }
    var toast by remember { mutableStateOf<android.widget.Toast?>(null) }
    fun say(text: String) {
        toast?.cancel()
        toast = android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).also { it.show() }
    }
    Column(
        Modifier.fillMaxWidth().clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null,
        ) {
            if (unlocked) { say("O modo desenvolvedor já está ligado"); return@clickable }
            taps++
            when {
                taps >= DEV_TAPS -> {
                    taps = 0
                    scope.launch { app?.settings?.set(SettingKeys.developerMode, true) }
                    say("Modo desenvolvedor ligado. Veja Ajustes → Desenvolvedor.")
                }
                taps >= DEV_TAPS - 4 -> say("Faltam ${DEV_TAPS - taps} toques para o modo desenvolvedor")
            }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandSymbol(36.dp)
        Spacer(Modifier.height(10.dp))
        Text("JR C50D Sync ${BuildConfig.VERSION_NAME}", style = WordmarkStyle.copy(fontSize = 16.sp), color = Tone.Text)
        Text("por Jr_Tech_OFC · Nossa Arte, Seu Sucesso", style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun SettingsSectionScreen(app: TetherApp, section: SettingsSection, onBack: () -> Unit) {
    val s by app.settings.settings.collectAsState(initial = null)
    val settings = s ?: return
    SettingsPage(title = section.title, onBack = onBack) {
        when (section) {
            SettingsSection.Profiles -> ProfilesSettings(app)
            SettingsSection.Save -> SaveSettings(app, settings)
            SettingsSection.Review -> ReviewSettings(app, settings)
            SettingsSection.Camera -> CameraSettings(app, settings)
            SettingsSection.Share -> ShareSettings(app, settings)
            SettingsSection.Presets -> PresetSettingsSection(app, settings)
            SettingsSection.Messages -> MessageSettings(app, settings)
            SettingsSection.Alerts -> AlertSettings(app, settings)
            SettingsSection.Display -> DisplaySettings(app, settings)
            SettingsSection.Diagnostics -> DiagnosticsSettings(app, settings)
            SettingsSection.Developer -> DeveloperSettings(app, settings)
            SettingsSection.Delivery -> DeliverySettings(app, settings)
            SettingsSection.Express -> ExpressSettings(app, settings)
            SettingsSection.Payments -> PaymentSettings(app, settings)
        }
        Spacer(Modifier.height(88.dp))
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

private const val DEV_TAPS = 7

// ---- Salvamento ---------------------------------------------------------------------------------

@Composable
private fun SaveSettings(app: TetherApp, s: Settings) {
    val scope = rememberCoroutineScope()
    val pickJpg = rememberFolderPicker(app) { scope.launch { app.settings.setOrClear(SettingKeys.jpgFolder, it) } }
    val pickRaw = rememberFolderPicker(app) { scope.launch { app.settings.setOrClear(SettingKeys.rawFolder, it) } }
    val pickBackup = rememberFolderPicker(app) { scope.launch { app.settings.setOrClear(SettingKeys.backupFolder, it) } }
    val pickMirror = rememberFolderPicker(app) { scope.launch { app.settings.setOrClear(SettingKeys.mirrorFolder, it) } }
    val pickNotes = rememberFolderPicker(app) { scope.launch { app.settings.setOrClear(SettingKeys.notesFolder, it) } }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) scope.launch {
            app.settings.set(SettingKeys.gpsEnabled, true)
            if (app.camera.state.value is ConnectionState.Connected) app.location.start()
        }
    }

    AppFoldersCard(app, s)

    SettingsGroup("O que vem para o celular", Icons.Outlined.PhoneAndroid) {
        ToggleRow(
            Icons.Outlined.Download,
            "Salvar fotos automaticamente",
            if (s.autoDownload) "Cada foto feita vem para o celular na hora e aparece na galeria"
            else "As fotos ficam só no cartão; baixe pelo aviso na Sessão ou pela tela Cartão",
            s.autoDownload,
        ) { scope.launch { app.settings.set(SettingKeys.autoDownload, it) } }
        ChoiceRow("Formato", DownloadMode.entries.map { it to it.label }, s.downloadMode) {
            scope.launch { app.settings.setDownloadMode(it) }
        }
        InfoText("A câmera grava tudo no cartão. Aqui você escolhe o que também é salvo no celular.")
    }

    SettingsGroup("2º cartão: cópia de cada foto", Icons.Outlined.SdCard) {
        ToggleRow(
            Icons.Outlined.ContentCopy,
            "Copiar cada foto para uma segunda pasta",
            "Como uma câmera de 2 cartões: o original vai para a pasta principal e uma cópia conferida para esta",
            s.mirrorEnabled,
            enabled = s.mirrorFolder != null,
        ) { scope.launch { app.settings.set(SettingKeys.mirrorEnabled, it) } }
        FolderRow(
            "Pasta do 2º cartão",
            s.mirrorFolder?.let { app.store.describeTree(it) } ?: "Nenhuma (escolha uma pasta, de preferência no cartão SD)",
            true,
            onPick = { pickMirror.launch(null) },
            onReset = s.mirrorFolder?.let { { scope.launch { app.settings.setOrClear(SettingKeys.mirrorFolder, null); app.settings.set(SettingKeys.mirrorEnabled, false) } } },
        )
        InfoText("Separa por sessão e por formato (…/Sessão/JPG e …/Sessão/RAW). Se a cópia falhar, o app avisa e continua fotografando.")
    }

    SettingsGroup("Notas das fotos", Icons.Outlined.EditNote) {
        FolderRow(
            "Pasta das notas",
            s.notesFolder?.let { app.store.describeTree(it) } ?: "Só dentro do app",
            true,
            onPick = { pickNotes.launch(null) },
            onReset = s.notesFolder?.let { { scope.launch { app.settings.setOrClear(SettingKeys.notesFolder, null) } } },
        )
        InfoText("Uma pasta por foto, com nota.txt e os áudios (audio_1.m4a, audio_2.m4a…).")
    }

    SettingsGroup("Pastas", Icons.Outlined.Folder) {
        FolderRow("Pasta dos JPG", app.store.describeTree(s.jpgFolder), s.downloadMode.jpg,
            onPick = { pickJpg.launch(null) }, onReset = s.jpgFolder?.let { { scope.launch { app.settings.setOrClear(SettingKeys.jpgFolder, null) } } })
        FolderRow("Pasta dos RAW (CR2)", app.store.describeTree(s.rawFolder), s.downloadMode.raw,
            onPick = { pickRaw.launch(null) }, onReset = s.rawFolder?.let { { scope.launch { app.settings.setOrClear(SettingKeys.rawFolder, null) } } })
    }

    SettingsGroup("Sessão / cliente", Icons.Outlined.Badge) {
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
        ToggleRow(Icons.Outlined.CreateNewFolder, "Criar subpasta com o nome da sessão", "Ex.: …/JPG/$example/", s.sessionSubfolder) {
            scope.launch { app.settings.set(SettingKeys.sessionSubfolder, it) }
        }
        ToggleRow(Icons.Outlined.DriveFileRenameOutline, "Renomear arquivos com o nome da sessão", "Ex.: ${example}_0001.JPG em vez de IMG_1234.JPG", s.renameWithSession) {
            scope.launch { app.settings.set(SettingKeys.renameWithSession, it) }
        }
    }

    SettingsGroup("Backup", Icons.Outlined.Backup) {
        FolderRow(
            "Pasta de backup",
            s.backupFolder?.let { app.store.describeTree(it) } ?: "Nenhuma (escolha o cartão SD ou um pendrive OTG)",
            true,
            onPick = { pickBackup.launch(null) },
            onReset = s.backupFolder?.let { { scope.launch { app.settings.setOrClear(SettingKeys.backupFolder, null) } } },
        )
        ToggleRow(Icons.Outlined.Backup, "Oferecer backup ao finalizar sessão", "O botão fica em destaque no resumo da sessão", s.backupOffer, enabled = s.backupFolder != null) {
            scope.launch { app.settings.set(SettingKeys.backupOffer, it) }
        }
        InfoText("Copia RAW, JPG, editadas e notas da sessão e confere o tamanho de cada arquivo.")
    }

    SettingsGroup("Localização", Icons.Outlined.LocationOn) {
        ToggleRow(Icons.Outlined.LocationOn, "GPS nas fotos", "Grava onde a foto foi feita nas cópias editadas e nos JPG leves. Usa o GPS do celular, funciona sem internet", s.gpsEnabled) { on ->
            if (on && !app.location.hasPermission()) askLocation.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
            else scope.launch {
                app.settings.set(SettingKeys.gpsEnabled, on)
                if (on && app.camera.state.value is ConnectionState.Connected) app.location.start() else if (!on) app.location.stop()
            }
        }
    }

    SettingsGroup("Momentos do evento", Icons.Outlined.Flag) {
        ToggleRow(Icons.Outlined.Flag, "Usar momentos", "Chip Momento na Sessão: cada foto nova recebe o momento escolhido", s.momentsEnabled) {
            scope.launch { app.settings.set(SettingKeys.momentsEnabled, it) }
        }
        var list by remember { mutableStateOf(s.moments.joinToString("\n")) }
        OutlinedTextField(
            value = list,
            onValueChange = {
                list = it
                scope.launch { app.settings.set(SettingKeys.momentList, it.lines().map(String::trim).filter(String::isNotEmpty).joinToString("|")) }
            },
            label = { Text("Momentos, um por linha") },
            enabled = s.momentsEnabled,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            minLines = 3,
        )
        ToggleRow(Icons.Outlined.FolderSpecial, "Subpasta por momento", "Ex.: …/JPG/${s.sessionName.ifBlank { "Casamento" }}/02 Cerimônia/", s.momentSubfolder, enabled = s.momentsEnabled) {
            scope.launch { app.settings.set(SettingKeys.momentSubfolder, it) }
        }
    }
}

/**
 * "Pastas do app": one tap creates the whole JR C50D Sync structure in the folder picked (the phone's
 * root opens first) and links every setting that saves something. Each folder shows whether it is linked.
 */
@Composable
private fun AppFoldersCard(app: TetherApp, s: Settings) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        app.store.takePersistablePermission(uri)
        busy = true
        scope.launch {
            val msg = runCatching {
                val r = com.example.core.storage.AppFolders.createAndLink(context, app.settings, uri)
                runCatching { app.presets.attachFolder() }
                if (r.created > 0) "${r.created} pastas criadas e ${r.linked} ligadas" else "Pastas já existiam: ${r.linked} ligadas"
            }.getOrElse { it.message ?: "Não deu para criar as pastas" }
            busy = false
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    SettingsGroup("Pastas do app", Icons.Outlined.FolderSpecial) {
        val values = mapOf(
            SettingKeys.jpgFolder to s.jpgFolder, SettingKeys.rawFolder to s.rawFolder, SettingKeys.editedFolder to s.editedFolder,
            SettingKeys.presetsFolder to s.presetsFolder, SettingKeys.notesFolder to s.notesFolder, SettingKeys.mirrorFolder to s.mirrorFolder,
            SettingKeys.backupFolder to s.backupFolder, SettingKeys.logFolder to s.logFolder,
        )
        Text(
            s.appFolder?.let { "Estrutura em ${app.store.describeTree(it)}" } ?: "Crie de uma vez todas as pastas que o app usa, já ligadas.",
            style = MaterialTheme.typography.bodyMedium,
            color = Tone.Text,
        )
        Button(
            onClick = { create.launch(com.example.core.storage.AppFolders.phoneRoot) },
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(containerColor = Tone.Accent, contentColor = Tone.OnAccent),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).heightIn(min = 48.dp),
        ) {
            Icon(Icons.Outlined.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(if (busy) "  Criando…" else if (s.appFolder == null) "  Criar e ligar todas as pastas" else "  Criar de novo / religar")
        }
        InfoText(
            "No seletor, crie ou abra a pasta \"${com.example.core.storage.AppFolders.ROOT_NAME}\" na raiz do celular e toque em Usar esta pasta. " +
                "Se escolher outra pasta, o app cria \"${com.example.core.storage.AppFolders.ROOT_NAME}\" dentro dela.",
        )
        com.example.core.storage.AppFolders.linked.forEach { f ->
            val on = values[f.key] != null
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (on) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = if (on) "Ligada" else "Não ligada",
                    tint = if (on) Tone.Accent else Tone.TextMuted,
                    modifier = Modifier.size(18.dp),
                )
                Text("  ${f.label}", style = MaterialTheme.typography.bodyMedium, color = Tone.Text, modifier = Modifier.weight(1f))
                Text(f.path.joinToString("/"), style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
            }
        }
    }
}

@Composable
internal fun rememberFolderPicker(app: TetherApp, onPicked: (String) -> Unit) =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            app.store.takePersistablePermission(uri)
            onPicked(uri.toString())
        }
    }

@Composable
internal fun FolderRow(title: String, path: String, active: Boolean, onPick: () -> Unit, onReset: (() -> Unit)?) {
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

// ---- Mensagens ----------------------------------------------------------------------------------

@Composable
private fun MessageSettings(app: TetherApp, s: Settings) {
    val scope = rememberCoroutineScope()
    SettingsGroup("Envio pelo WhatsApp", Icons.Outlined.Send) {
        ToggleRow(Icons.AutoMirrored.Outlined.Chat, "Mensagem automática ao enviar", "Usa o texto \"Envio das fotos\" abaixo", s.whatsAutoMessage) {
            scope.launch { app.settings.set(SettingKeys.whatsAutoMessage, it) }
        }
    }
    InfoText("Campos preenchidos sozinhos: {cliente} (nome digitado ou da sessão), {quantidade}, {data}, {lista} (nomes das fotos) e {pix}. Use *asteriscos* para negrito no WhatsApp.")
    SettingsGroup("Pix", Icons.Outlined.Payments) {
        var pix by remember { mutableStateOf(s.pixKey) }
        OutlinedTextField(
            value = pix,
            onValueChange = { pix = it.take(80); scope.launch { app.settings.set(SettingKeys.pixKey, pix) } },
            label = { Text("Chave Pix (entra no lugar de {pix})") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
    }
    MessageLibraryEditor(app, s)
}

/**
 * Every message, each with its short code: tap one to edit name, code and text; add new ones for
 * any step of the job. When sending, typing the code brings the message.
 */
@Composable
private fun MessageLibraryEditor(app: TetherApp, s: Settings) {
    val scope = rememberCoroutineScope()
    val defaults = remember(s) { com.example.core.messages.MessageLibrary.defaults { s.legacyTemplate(it) } }
    var list by remember { mutableStateOf(s.messages()) }
    var open by remember { mutableStateOf<String?>(null) }
    fun save(next: List<com.example.core.messages.MessageTemplate>) {
        list = next
        scope.launch { app.settings.set(SettingKeys.messageLibrary, com.example.core.messages.MessageLibrary.encode(next)) }
    }
    SettingsGroup("Mensagens (${list.size})", Icons.AutoMirrored.Outlined.Chat) {
        list.forEach { t ->
            val expanded = open == t.id
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { open = if (expanded) null else t.id }
                    .heightIn(min = 52.dp).padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(t.name, style = MaterialTheme.typography.bodyLarge, color = Tone.Text)
                    Text("Atalho: ${t.code}", style = MaterialTheme.typography.bodySmall, color = Tone.Accent)
                }
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, tint = Tone.TextMuted)
            }
            if (expanded) {
                var name by remember(t.id) { mutableStateOf(t.name) }
                var code by remember(t.id) { mutableStateOf(t.code) }
                var text by remember(t.id) { mutableStateOf(t.text) }
                fun update() = save(list.map { if (it.id == t.id) it.copy(name = name.trim().ifBlank { t.name }, code = code.trim().lowercase().ifBlank { t.code }, text = text) else it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it.take(40); update() }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.weight(1.4f))
                    OutlinedTextField(code, { code = it.filter { c -> !c.isWhitespace() }.take(12); update() }, label = { Text("Atalho") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(text, { text = it; update() }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), minLines = 4)
                Row {
                    defaults.firstOrNull { it.id == t.id }?.takeIf { it.text != text || it.name != name || it.code != code }?.let { d ->
                        TextButton(onClick = { name = d.name; code = d.code; text = d.text; update() }) { Text("Voltar ao padrão") }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { open = null; save(list.filter { it.id != t.id }) }) { Text("Apagar", color = Tone.Error) }
                }
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val taken = list.map { it.code }.toSet()
                val t = com.example.core.messages.MessageTemplate("m${System.currentTimeMillis()}", "Nova mensagem", com.example.core.messages.MessageLibrary.codeFor("nova", taken), "Olá, {cliente}!\n\n")
                save(list + t)
                open = t.id
            }) { Text("Nova mensagem") }
            TextButton(onClick = { open = null; save(defaults) }) { Text("Restaurar as padrão") }
        }
    }
}

// ---- Presets -----------------------------------------------------------------------------------

@Composable
private fun PresetSettingsSection(app: TetherApp, s: Settings) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    fun set(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, v: Boolean) = scope.launch { app.settings.set(key, v) }
    val presets by app.presets.presets.collectAsState(initial = emptyList())
    val pickPresets = rememberFolderPicker(app) { uri ->
        scope.launch {
            app.settings.setOrClear(SettingKeys.presetsFolder, uri)
            val added = app.presets.attachFolder()
            val msg = if (added > 0) "$added preset(s) da pasta importados" else "Presets copiados para a pasta"
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val pickEdited = rememberFolderPicker(app) { scope.launch { app.settings.setOrClear(SettingKeys.editedFolder, it) } }

    SettingsGroup("Fotos novas", Icons.Outlined.AddAPhoto) {
        val current = presets.firstOrNull { it.id == s.sessionPresetId }
        ToggleRow(
            Icons.Outlined.Style,
            "Aplicar o look da sessão",
            current?.let { "Preset atual: ${it.name}. Troque na Sessão (Look) ou na aba Presets." }
                ?: "Nenhum preset escolhido. Escolha na Sessão (Look) ou na aba Presets.",
            s.applyPresetToNew,
        ) { set(SettingKeys.applyPresetToNew, it) }
        if (s.sessionAdjust.isNotBlank()) {
            InfoText("Uma calibração feita numa foto está sendo levada para as próximas.")
            TextButton(onClick = { scope.launch { app.settings.set(SettingKeys.sessionAdjust, "") } }) { Text("Voltar ao preset sem a calibração") }
        }
        InfoText("O look é como um perfil de cor: a foto aparece processada, mas o JPG e o RAW da câmera não mudam.")
    }

    SettingsGroup("Cópia editada", Icons.Outlined.SaveAlt) {
        ToggleRow(Icons.Outlined.SaveAlt, "Salvar cópia editada", "Um JPG com o preset aplicado, ao lado do original", s.saveEditedCopy) { set(SettingKeys.saveEditedCopy, it) }
        SliderRow("Qualidade do JPG", s.editedQuality, 70..100, enabled = s.saveEditedCopy) {
            scope.launch { app.settings.set(SettingKeys.editedQuality, it) }
        }
        FolderRow("Pasta das editadas", app.store.describeTree(s.editedFolder), s.saveEditedCopy,
            onPick = { pickEdited.launch(null) }, onReset = s.editedFolder?.let { { scope.launch { app.settings.setOrClear(SettingKeys.editedFolder, null) } } })
        InfoText("Só com RAW (sem JPG), a cópia sai do JPEG embutido no CR2, em resolução menor.")
    }

    SettingsGroup("Pasta de presets", Icons.Outlined.FolderOpen) {
        FolderRow(
            "Pasta de presets",
            s.presetsFolder?.let { app.store.describeTree(it) } ?: "Só dentro do app",
            true,
            onPick = { pickPresets.launch(null) },
            onReset = s.presetsFolder?.let { { scope.launch { app.settings.setOrClear(SettingKeys.presetsFolder, null) } } },
        )
        InfoText("Cada preset é salvo nela como .xmp. Arquivos .xmp, .dng e .lrtemplate colocados nela entram no app quando ele abre.")
        InfoText("Baixar, na Loja, grava em JR_LR_Presets/JR_LR_Presets_DNG e JR_LR_Presets/JR_LR_Presets_XMP dentro dela.")
    }

    SettingsGroup("Loja de presets", Icons.Outlined.Storefront) {
        ToggleRow(Icons.Outlined.Storefront, "Aba Loja", "Presets grátis da sua API, com prévia, usar, salvar e baixar", s.shopEnabled) {
            set(SettingKeys.shopEnabled, it)
        }
        var url by remember(s.shopUrl) { mutableStateOf(s.shopUrl) }
        OutlinedTextField(
            value = url,
            onValueChange = { url = it.trim() },
            label = { Text("Endereço da API") },
            singleLine = true,
            enabled = s.shopEnabled,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (url != s.shopUrl && url.startsWith("http")) {
                TextButton(onClick = { scope.launch { app.settings.set(SettingKeys.shopUrl, url); app.shop.refresh() } }) { Text("Salvar endereço") }
            }
            if (s.shopUrl != DEFAULT_SHOP_URL) {
                TextButton(onClick = { scope.launch { app.settings.set(SettingKeys.shopUrl, DEFAULT_SHOP_URL); app.shop.refresh() } }) { Text("Voltar ao padrão") }
            }
        }
        InfoText("Só a Loja usa a internet. Um preset salvo fica no celular e funciona sem sinal em todo o app.")
    }

    SettingsGroup("Visualizador e modo cliente", Icons.Outlined.Visibility) {
        ToggleRow(Icons.Outlined.Tune, "Presets no visualizador", "Faixa de presets e botão Calibrar embaixo da foto", s.viewerPresets) { set(SettingKeys.viewerPresets, it) }
        ToggleRow(Icons.Outlined.Style, "Presets no modo cliente", "O cliente pode trocar o look da foto", s.clientPresetsEnabled) { set(SettingKeys.clientPresetsEnabled, it) }
        ToggleRow(
            Icons.Outlined.Lock,
            "Pedir PIN para calibrar",
            if (s.clientPin.isEmpty()) "Defina um PIN em Envio e cliente para usar" else "No modo cliente, Calibrar pede o PIN",
            s.clientCalibratePin,
            enabled = s.clientPresetsEnabled,
        ) { set(SettingKeys.clientCalibratePin, it) }
    }

    SettingsGroup("Limites", Icons.Outlined.Info) {
        InfoText(
            "A prévia imita o processamento do Lightroom, sem ser idêntica. O XMP exportado dá o resultado exato no Lightroom. " +
                "Máscaras, remoção de manchas, correção de lente e perfis de câmera da Adobe não são aplicados.",
        )
    }
}

// ---- Revisão -----------------------------------------------------------------------------------

@Composable
private fun ReviewSettings(app: TetherApp, s: Settings) {
    val scope = rememberCoroutineScope()
    fun set(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, v: Boolean) = scope.launch { app.settings.set(key, v) }

    SettingsGroup("Foto nova", Icons.Outlined.AddPhotoAlternate) {
        ToggleRow(Icons.Outlined.SkipNext, "Ir para a foto nova automaticamente", "Com uma foto aberta em tela cheia, desliza para a que acabou de chegar", s.autoSlideToNew) { set(SettingKeys.autoSlideToNew, it) }
        ToggleRow(Icons.Outlined.LastPage, "Só se eu estiver na última foto", "Não interrompe quando você está revendo fotos antigas", s.autoSlideOnlyAtLatest, enabled = s.autoSlideToNew) { set(SettingKeys.autoSlideOnlyAtLatest, it) }
        ToggleRow(Icons.Outlined.Fullscreen, "Abrir em tela cheia ao receber", "Na tela Sessão, cada foto nova abre direto no visualizador", s.autoOpenViewer) { set(SettingKeys.autoOpenViewer, it) }
    }
    SettingsGroup("Galeria", Icons.Outlined.GridView) {
        ChoiceRow(
            "Tamanho das miniaturas",
            listOf(84 to "Pequenas", 112 to "Médias", 150 to "Grandes"),
            s.galleryThumb,
            icon = Icons.Outlined.GridView,
        ) { scope.launch { app.settings.set(SettingKeys.galleryThumb, it) } }
    }
    SettingsGroup("Avaliação", Icons.Outlined.StarOutline) {
        ToggleRow(Icons.Outlined.StarOutline, "Estrelas (1 a 5)", "Mostra as estrelas no visualizador e o filtro na galeria", s.ratingEnabled) { set(SettingKeys.ratingEnabled, it) }
    }
    SettingsGroup("Seleção com análise local", Icons.Outlined.FactCheck) {
        InfoText("Cada foto é analisada no próprio celular, sem internet: nitidez, rostos e olhos, exposição e fotos parecidas.")
        ToggleRow(Icons.Outlined.BlurOn, "Alerta de foto tremida", "Selo \"Tremida\" e aviso quando a foto sai tremida ou fora de foco", s.blurAlert) { set(SettingKeys.blurAlert, it) }
        SliderRow("Sensibilidade do alerta", s.blurThreshold, 30..70, enabled = s.blurAlert, format = { v -> when { v < 44 -> "baixa ($v)"; v < 57 -> "média ($v)"; else -> "alta ($v)" } }) {
            scope.launch { app.settings.set(SettingKeys.blurThreshold, it) }
        }
        ToggleRow(Icons.Outlined.VisibilityOff, "Olhos fechados", "Detecta rostos e avisa quando alguém piscou", s.eyesCheck) { set(SettingKeys.eyesCheck, it) }
        ToggleRow(Icons.Outlined.Vibration, "Vibrar no problema", "Dois toques curtos, diferentes do aviso de foto recebida", s.blurVibrate, enabled = s.blurAlert || s.eyesCheck) { set(SettingKeys.blurVibrate, it) }
        ToggleRow(Icons.Outlined.ThumbUp, "Sugerir as melhores", "Entre fotos parecidas, marca a melhor como Sugerida", s.cullSuggestions) { set(SettingKeys.cullSuggestions, it) }
        ToggleRow(Icons.Outlined.HideImage, "Esconder rejeitadas na galeria", "Os arquivos continuam salvos; veja na aba Culling → Rejeitadas", s.hideRejected) { set(SettingKeys.hideRejected, it) }
    }
    SettingsGroup("Anotações", Icons.Outlined.EditNote) {
        ToggleRow(Icons.Outlined.EditNote, "Notas nas fotos", "Texto ou áudio em cada foto, pelo botão de nota no visualizador", s.notesEnabled) { set(SettingKeys.notesEnabled, it) }
    }
    SettingsGroup("Exposição", Icons.Outlined.BarChart) {
        ToggleRow(Icons.Outlined.BarChart, "Histograma", null, s.histogramEnabled) { set(SettingKeys.histogramEnabled, it) }
        ToggleRow(Icons.Outlined.Palette, "Histograma RGB", "Desligado: só luminância", s.histogramRgb, enabled = s.histogramEnabled) { set(SettingKeys.histogramRgb, it) }
        ToggleRow(Icons.Outlined.WbSunny, "Alerta de estouro", "Áreas estouradas piscam em vermelho ao abrir a foto", s.highlightAlert) { set(SettingKeys.highlightAlert, it) }
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

    SettingsGroup("Conexão", Icons.Outlined.Usb) {
        ToggleRow(Icons.Outlined.PowerSettingsNew, "Manter a câmera ligada", "Evita o desligamento automático da 50D durante a sessão", s.keepCameraAwake) { set(SettingKeys.keepCameraAwake, it) }
        ToggleRow(Icons.Outlined.BatteryStd, "Mostrar bateria da câmera", "No topo da Sessão e aviso quando estiver baixa", s.showBattery) { set(SettingKeys.showBattery, it) }
        ToggleRow(
            Icons.Outlined.Schedule,
            "Acertar o relógio da câmera",
            "Ao conectar, a 50D fica com a hora do celular. Útil com duas câmeras no mesmo evento. Se a câmera não aceitar, a opção se desliga sozinha",
            s.syncCameraClock,
        ) { set(SettingKeys.syncCameraClock, it) }
        ToggleRow(Icons.Outlined.Videocam, "Live view", "Chip Live view na Sessão: a imagem da câmera no celular, como monitor externo", s.liveViewEnabled) { set(SettingKeys.liveViewEnabled, it) }
        ToggleRow(Icons.Outlined.Calculate, "Calculadora de flash e foco", "Chip \"Flash e foco\" na Sessão", s.toolsEnabled) { set(SettingKeys.toolsEnabled, it) }
        if (state is ConnectionState.Connected) {
            OutlinedButton(onClick = app.camera::disconnect, modifier = Modifier.padding(vertical = 8.dp)) { Text("Encerrar conexão") }
        }
    }
    SettingsGroup("Cartão", Icons.Outlined.SdCard) {
        ToggleRow(Icons.Outlined.SdCard, "Navegar no cartão", "Botão na Sessão para ver e baixar fotos que já estão no cartão", s.cardBrowserEnabled) { set(SettingKeys.cardBrowserEnabled, it) }
    }
    SettingsGroup("Exposições salvas", Icons.Outlined.Exposure) {
        ToggleRow(Icons.Outlined.Exposure, "Usar exposições salvas", "Salve ISO, Av, Tv, ±EV e WB e aplique com um toque na Sessão", s.presetsEnabled) { set(SettingKeys.presetsEnabled, it) }
        val presets = s.exposurePresets
        if (presets.isEmpty()) InfoText("Nenhuma exposição salva. Na Sessão, toque em Exposições → Salvar atual.")
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
    SettingsGroup("Bracketing (AEB)", Icons.Outlined.HdrStrong) {
        ToggleRow(Icons.Outlined.HdrStrong, "Bracketing pelo app", "Botão na Sessão que faz a sequência variando a exposição", s.aebEnabled) { set(SettingKeys.aebEnabled, it) }
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
            if (path != null) {
                app.settings.setOrClear(SettingKeys.watermarkPath, path)
                app.settings.set(SettingKeys.watermarkSource, WatermarkSource.Custom.id)
            }
        }
    }

    SettingsGroup("Envio leve (WhatsApp)", Icons.Outlined.Compress) {
        ToggleRow(Icons.Outlined.Compress, "Opção \"JPG leve\" ao enviar", "Cópia reduzida, rápida no 4G. O original não muda.", s.lightShareEnabled) { set(SettingKeys.lightShareEnabled, it) }
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

    SettingsGroup("WhatsApp do cliente", Icons.AutoMirrored.Outlined.Chat) {
        ToggleRow(Icons.Outlined.Person, "Pedir o nome do cliente", "Obrigatório antes de abrir a conversa; entra no {cliente} da mensagem", s.whatsRequireName) { set(SettingKeys.whatsRequireName, it) }
        ToggleRow(Icons.AutoMirrored.Outlined.Chat, "Mensagem automática", "A conversa abre com o texto de Ajustes → Mensagens → Envio das fotos", s.whatsAutoMessage) { set(SettingKeys.whatsAutoMessage, it) }
        ChoiceRow(
            "O que vai por padrão",
            listOf("LIGHT" to "Pronta (leve + marca)", "EDITED" to "Editada", "JPG" to "JPG original"),
            s.whatsDefaultSet,
            icon = Icons.Outlined.Image,
        ) { scope.launch { app.settings.set(SettingKeys.whatsDefaultSet, it) } }
        InfoText("O envio é em dois toques: 1 abre a conversa do cliente com a mensagem; 2 manda as fotos nessa mesma conversa.")
    }

    SettingsGroup("Marca d'água", Icons.Outlined.BrandingWatermark) {
        ToggleRow(Icons.Outlined.BrandingWatermark, "Aplicar marca d'água", "Só nas cópias leves enviadas. Os arquivos salvos ficam sem marca.", s.watermarkEnabled) {
            set(SettingKeys.watermarkEnabled, it)
        }
        val source = WatermarkSource.of(s.watermarkSource)
        ChoiceRow(
            "Logo",
            WatermarkSource.entries.map { it.id to it.label },
            source.id,
            enabled = s.watermarkEnabled,
        ) { id ->
            if (id == WatermarkSource.Custom.id && s.watermarkPath == null) {
                pickLogo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } else {
                scope.launch { app.settings.set(SettingKeys.watermarkSource, id) }
            }
        }
        if (source == WatermarkSource.Custom) {
            OutlinedButton(
                onClick = { pickLogo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.padding(bottom = 8.dp),
            ) { Text("Trocar PNG") }
        }
        if (s.watermarkEnabled) {
            WatermarkPreview(app, s)
            Text("Posição", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
            PositionGrid(s.watermarkPosition) { scope.launch { app.settings.set(SettingKeys.watermarkPosition, it) } }
            SliderRow("Tamanho", s.watermarkSizePct, 5..60, format = { "$it%" }) { scope.launch { app.settings.set(SettingKeys.watermarkSizePct, it) } }
            SliderRow("Opacidade", s.watermarkOpacity, 10..100, step = 5, format = { "$it%" }) { scope.launch { app.settings.set(SettingKeys.watermarkOpacity, it) } }
            SliderRow("Margem", s.watermarkMarginPct, 0..15, format = { "$it%" }) { scope.launch { app.settings.set(SettingKeys.watermarkMarginPct, it) } }
        }
    }

    SettingsGroup("Modo cliente", Icons.Outlined.Slideshow) {
        ToggleRow(Icons.Outlined.Slideshow, "Mostrar botão Modo cliente", "Tela cheia só com as fotos, para o cliente acompanhar", s.clientModeEnabled) { set(SettingKeys.clientModeEnabled, it) }
        ToggleRow(Icons.Outlined.Send, "Cliente recebe no WhatsApp", "Botão para o cliente digitar o número dele e receber a foto pronta", s.clientWhatsApp, enabled = s.clientModeEnabled) { set(SettingKeys.clientWhatsApp, it) }
        ToggleRow(Icons.Outlined.FavoriteBorder, "Cliente escolhe as fotos", "Coração em cada foto; a seleção aparece na galeria e no resumo da sessão", s.clientPicksEnabled, enabled = s.clientModeEnabled) { set(SettingKeys.clientPicksEnabled, it) }
        ToggleRow(Icons.Outlined.Info, "Mostrar nome e exposição", null, s.clientShowInfo, enabled = s.clientModeEnabled) { set(SettingKeys.clientShowInfo, it) }
        ToggleRow(Icons.Outlined.Verified, "Assinatura Jr_Tech_OFC", "Símbolo e nome discretos no canto da tela", s.clientShowBrand, enabled = s.clientModeEnabled) { set(SettingKeys.clientShowBrand, it) }
        ToggleRow(
            Icons.Outlined.BrandingWatermark,
            "Marca d'água sobre a foto",
            "A marca escolhida em Marca d'água aparece na foto, só na tela: quem fotografar o celular leva a sua marca junto",
            s.clientWatermark,
            enabled = s.clientModeEnabled,
        ) { set(SettingKeys.clientWatermark, it) }
        SliderRow("Opacidade da marca na tela", s.clientWatermarkOpacity, 10..90, step = 5, enabled = s.clientModeEnabled && s.clientWatermark, format = { "$it%" }) {
            scope.launch { app.settings.set(SettingKeys.clientWatermarkOpacity, it) }
        }
        ToggleRow(Icons.Outlined.Favorite, "Só favoritas", "Mostra apenas as fotos marcadas com estrela de favorita", s.clientOnlyFavorites, enabled = s.clientModeEnabled) { set(SettingKeys.clientOnlyFavorites, it) }
        ToggleRow(Icons.Outlined.Crop, "Botão Recortar", "Recorte rápido no modo cliente (pede o PIN, se houver)", s.clientCropEnabled, enabled = s.clientModeEnabled) { set(SettingKeys.clientCropEnabled, it) }
        ToggleRow(Icons.AutoMirrored.Outlined.Logout, "Botão Sair", "No canto de cima. Com PIN, ele é pedido para sair", s.clientExitButton, enabled = s.clientModeEnabled) { on ->
            // At least one way out always stays on.
            scope.launch {
                app.settings.set(SettingKeys.clientExitButton, on)
                if (!on) app.settings.set(SettingKeys.clientExitHold, true)
            }
        }
        ToggleRow(Icons.Outlined.TouchApp, "Sair segurando o dedo 2 s", "Um dedo parado na tela por 2 segundos; um anel mostra o tempo", s.clientExitHold, enabled = s.clientModeEnabled && s.clientExitButton) { set(SettingKeys.clientExitHold, it) }
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
        InfoText(
            when {
                s.clientExitButton && s.clientExitHold -> "Para sair: botão Sair no canto de cima, ou segure o dedo na tela por 2 segundos."
                s.clientExitButton -> "Para sair: botão Sair no canto de cima."
                else -> "Para sair: segure o dedo na tela por 2 segundos."
            },
        )
    }
}

/** Where the logo lands on a 3:2 frame, with the real logo, size and opacity. */
@Composable
private fun WatermarkPreview(app: TetherApp, s: Settings) {
    val logo by produceState<android.graphics.Bitmap?>(null, s.watermarkSource, s.watermarkPath) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.export.loadLogo(s) }
    }
    val bitmap = logo ?: return
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    BoxWithConstraints(
        Modifier.fillMaxWidth().aspectRatio(3f / 2f).clip(MaterialTheme.shapes.small).background(Color(0xFF6B6F66)),
    ) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.roundToPx() }
        val hPx = with(density) { maxHeight.roundToPx() }
        val r = WatermarkLayout.place(wPx, hPx, bitmap.width, bitmap.height, s.watermarkPosition, s.watermarkSizePct, s.watermarkMarginPct)
        Text("Prévia sobre uma foto", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(8.dp))
        Image(
            bitmap = image,
            contentDescription = "Marca d'água",
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

    SettingsGroup("Avisos", Icons.Outlined.NotificationsActive) {
        ToggleRow(Icons.Outlined.Vibration, "Vibrar a cada foto recebida", "Um toque curto, sem som", s.vibrateOnReceive) { set(SettingKeys.vibrateOnReceive, it) }
        ToggleRow(Icons.Outlined.UsbOff, "Alerta se a câmera desconectar", "Vibração forte e notificação", s.alertOnDisconnect) { set(SettingKeys.alertOnDisconnect, it) }
        ToggleRow(Icons.Outlined.SdCardAlert, "Aviso de cartão quase cheio", null, s.cardLowWarning) { set(SettingKeys.cardLowWarning, it) }
        SliderRow("Avisar abaixo de", s.cardLowShots, 10..300, step = 10, enabled = s.cardLowWarning, format = { "$it fotos" }) {
            scope.launch { app.settings.set(SettingKeys.cardLowShots, it) }
        }
        ToggleRow(Icons.Outlined.Storage, "Aviso de memória do celular", null, s.phoneLowWarning) { set(SettingKeys.phoneLowWarning, it) }
        SliderRow("Avisar abaixo de", s.phoneLowGb, 1..20, enabled = s.phoneLowWarning, format = { "$it GB" }) {
            scope.launch { app.settings.set(SettingKeys.phoneLowGb, it) }
        }
    }
    SettingsGroup("Reenvio automático", Icons.Outlined.Sync) {
        ToggleRow(Icons.Outlined.Sync, "Baixar fotos pendentes ao reconectar", "Fotos feitas com o cabo solto, ou que falharam, vêm do cartão quando a câmera volta", s.autoResend) { set(SettingKeys.autoResend, it) }
        ToggleRow(Icons.Outlined.HelpOutline, "Perguntar antes de baixar", null, s.resendAsk, enabled = s.autoResend) { set(SettingKeys.resendAsk, it) }
    }
    SettingsGroup("Fim da sessão", Icons.Outlined.Summarize) {
        ToggleRow(Icons.Outlined.Summarize, "Mostrar resumo da sessão", "Ao encerrar: total, espaço usado, favoritas e envio ao Lightroom", s.sessionSummary) { set(SettingKeys.sessionSummary, it) }
    }
}

// ---- Tela --------------------------------------------------------------------------------------

@Composable
private fun DisplaySettings(app: TetherApp, s: Settings) {
    val scope = rememberCoroutineScope()
    SettingsGroup("Modo escuro total", Icons.Outlined.Bedtime) {
        ToggleRow(Icons.Outlined.Bedtime, "Ativar", "Cores ainda mais escuras e brilho reduzido, para igrejas e eventos à noite", s.nightMode) {
            scope.launch { app.settings.set(SettingKeys.nightMode, it) }
        }
        SliderRow("Brilho da tela", s.nightBrightness, 1..100, enabled = s.nightMode, format = { "$it%" }) {
            scope.launch { app.settings.set(SettingKeys.nightBrightness, it) }
        }
    }
    SettingsGroup("Posição da tela", Icons.Outlined.ScreenRotation) {
        ChoiceRow(
            "O app todo fica",
            com.example.ui.components.ScreenTurn.entries.map { it.id to it.label },
            com.example.ui.components.ScreenTurn.of(s.screenOrientation).id,
            icon = Icons.Outlined.ScreenRotation,
        ) { scope.launch { app.settings.set(SettingKeys.screenOrientation, it) } }
        ToggleRow(
            Icons.Outlined.ScreenRotation,
            "Girar com a foto",
            "No visualizador e no modo cliente: foto em pé deixa a tela em pé, foto deitada deixa a tela deitada, sempre em tela cheia",
            s.rotateWithPhoto,
        ) { scope.launch { app.settings.set(SettingKeys.rotateWithPhoto, it) } }
        InfoText("Automático segue o sensor do celular mesmo com a rotação do Android travada. Nas posições fixas a tela não gira sozinha. O botão de girar fica na Sessão, no visualizador, no modo cliente e no live view.")
    }
    SettingsGroup("Tela", Icons.Outlined.Smartphone) {
        ToggleRow(Icons.Outlined.LightMode, "Manter a tela ligada", "Enquanto a câmera estiver conectada", s.keepScreenOn) {
            scope.launch { app.settings.set(SettingKeys.keepScreenOn, it) }
        }
    }
    SettingsGroup("Abas da barra", Icons.Outlined.Tab) {
        InfoText("Esconda as abas que não usa. Sessão e Ajustes ficam sempre. Com mais de 5 abas, a barra rola para o lado.")
        val hidden = s.hiddenTabs.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        com.example.Tab.entries.filter { it.hideable }.forEach { tab ->
            ToggleRow(tab.icon, tab.label, null, tab.route !in hidden) { show ->
                val next = if (show) hidden - tab.route else hidden + tab.route
                scope.launch { app.settings.set(SettingKeys.hiddenTabs, next.joinToString(",")) }
            }
        }
    }
}

// ---- Diagnóstico -------------------------------------------------------------------------------

@Composable
private fun DiagnosticsSettings(app: TetherApp, s: Settings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recent by AppLog.recent.collectAsState()
    val lastExit by AppLog.lastExit.collectAsState()
    var onlyErrors by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val pickFolder = rememberFolderPicker(app) { scope.launch { app.settings.setOrClear(SettingKeys.logFolder, it) } }
    val today = remember(recent.size) { AppLog.todayFile() }

    lastExit?.let { exit ->
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(Tone.Error.copy(alpha = 0.12f)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.ReportProblem, contentDescription = null, tint = Tone.Error)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Da última vez o app fechou sozinho", style = MaterialTheme.typography.bodyLarge)
                Text(exit, style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
                Text("Está registrado no log. Toque em Compartilhar e mande o arquivo.", style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
            }
        }
    }

    SettingsGroup("Log do app", Icons.Outlined.Description) {
        ToggleRow(
            Icons.Outlined.EditNote,
            "Salvar log completo",
            "Conexão, downloads, memória a cada foto, telas e avisos, num arquivo por dia. Desligado: só erros e fechamentos inesperados, para ter o máximo de desempenho",
            s.logEnabled,
        ) { scope.launch { app.settings.set(SettingKeys.logEnabled, it) } }
        FolderRow(
            "Pasta dos logs",
            s.logFolder?.let { app.store.describeTree(it) } ?: "Só dentro do app (use Compartilhar para mandar)",
            true,
            onPick = { pickFolder.launch(null) },
            onReset = s.logFolder?.let { { scope.launch { app.settings.setOrClear(SettingKeys.logFolder, null) } } },
        )
        InfoText(
            "Hoje: ${today.name}" + (if (today.exists()) " · ${today.length() / 1024} KB" else "") +
                ". Os últimos 10 dias ficam guardados e o arquivo é copiado para a pasta a cada 15 s.",
        )
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { scope.launch { shareLog(context) } },
                colors = ButtonDefaults.buttonColors(containerColor = Tone.Accent, contentColor = Tone.OnAccent),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Compartilhar", maxLines = 1)
            }
            OutlinedButton(onClick = { copyLog(context, recent) }) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copiar", modifier = Modifier.size(18.dp))
            }
            OutlinedButton(onClick = { confirmClear = true }) {
                Icon(Icons.Outlined.Delete, contentDescription = "Apagar logs", modifier = Modifier.size(18.dp))
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
        Text("Últimos registros", style = MaterialTheme.typography.bodyMedium, color = Tone.TextMuted, modifier = Modifier.weight(1f))
        FilterChip(
            selected = onlyErrors,
            onClick = { onlyErrors = !onlyErrors },
            label = { Text("Só erros") },
            leadingIcon = { Icon(Icons.Outlined.ErrorOutline, contentDescription = null, modifier = Modifier.size(16.dp)) },
        )
    }
    val shown = recent.filter { !onlyErrors || it.contains(" E/") }.takeLast(150)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .clip(MaterialTheme.shapes.small)
            .background(Tone.Surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (shown.isEmpty()) Text("Sem registros ainda.", color = Tone.TextMuted, style = MaterialTheme.typography.bodySmall)
        shown.forEach {
            Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = if (it.contains(" E/")) Tone.Error else Tone.TextMuted)
        }
    }

    if (confirmClear) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Tone.Surface,
            title = { Text("Apagar os logs?") },
            text = { Text("Os arquivos de log guardados no app são apagados. Cópias na pasta escolhida continuam lá.") },
            confirmButton = { TextButton(onClick = { AppLog.clear(); confirmClear = false }) { Text("Apagar", color = Tone.Error) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }
}

/** Joins the last days of log into one file and opens the share sheet (WhatsApp, Drive, e-mail…). */
private suspend fun shareLog(context: Context) {
    val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { AppLog.bundle() }
    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(android.content.Intent.EXTRA_STREAM, uri)
        .putExtra(android.content.Intent.EXTRA_SUBJECT, "Log do JR C50D Sync")
        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(android.content.Intent.createChooser(send, "Enviar log"))
}

private fun copyLog(context: Context, lines: List<String>) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Log JR C50D Sync", lines.joinToString("\n")))
}
