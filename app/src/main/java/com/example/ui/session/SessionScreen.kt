package com.example.ui.session

import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.example.core.camera.ShutterState
import com.example.ui.live.ShutterButton
import com.example.ui.components.RotateButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.TetherApp
import com.example.core.camera.CameraStatus
import com.example.core.camera.ConnectionState
import com.example.core.camera.IntervalState
import com.example.core.data.Photo
import com.example.core.data.Settings
import com.example.core.ptp.EosProp
import com.example.core.ptp.EosValues
import com.example.core.tether.Transfer
import com.example.ui.components.FormatTag
import com.example.ui.components.Readout
import com.example.ui.components.SectionLabel
import com.example.ui.components.ValuePickerSheet
import com.example.core.data.SettingKeys
import com.example.ui.PhotoActions
import com.example.ui.ShareSet
import com.example.ui.theme.Tone
import com.example.ui.preset.LookImage
import com.example.ui.preset.SessionLookSheet
import com.example.ui.theme.BrandGradient
import com.example.ui.components.BrandMark
import com.example.ui.components.BrandSymbol
import com.example.ui.components.StateTile
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import androidx.compose.animation.togetherWith
import com.example.ui.components.shutterFlash
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import com.example.ui.components.pressScale
import com.example.ui.theme.Space
import com.example.ui.components.StatusMark

@Composable
fun SessionScreen(
    app: TetherApp,
    actions: PhotoActions,
    onOpenPhoto: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCard: () -> Unit,
    onOpenClient: () -> Unit,
    onOpenTools: () -> Unit = {},
    onOpenLive: () -> Unit = {},
    onOpenProfiles: () -> Unit = {},
    onEditPhoto: (Long) -> Unit = {},
    onCropPhoto: (Long) -> Unit = {},
    onOpenShop: (() -> Unit)? = null,
    onOpenPresets: () -> Unit = {},
    onOpenCull: () -> Unit = {},
    onOpenSection: (com.example.ui.settings.SettingsSection) -> Unit = {},
    onOpenDelivery: () -> Unit = {},
    onOpenExpress: () -> Unit = {},
) {
    var deliverFor by remember { mutableStateOf<com.example.core.data.Photo?>(null) }
    val deliveryStatus by app.delivery.status.collectAsState()
    val deliveryOnline by app.delivery.online.collectAsState()
    val expressStatus by app.express.status.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var whatsFor by remember { mutableStateOf<com.example.core.data.Photo?>(null) }
    var noteFor by remember { mutableStateOf<com.example.core.data.Photo?>(null) }
    val state by app.camera.state.collectAsState()
    val shutter by app.camera.shutter.collectAsState()
    val activeProfile by app.settings.activeProfile.collectAsState(initial = "")
    var showProfiles by remember { mutableStateOf(false) }
    val status by app.camera.status.collectAsState()
    val interval by app.camera.interval.collectAsState()
    val transfer by app.tether.transfer.collectAsState()
    val received by app.tether.receivedThisSession.collectAsState()
    val settings by app.settings.settings.collectAsState(initial = Settings())
    val photos by remember { app.photos }.collectAsState(initial = emptyList())
    val looks by app.presets.presets.collectAsState(initial = emptyList())
    var showLooks by remember { mutableStateOf(false) }
    var showMoments by remember { mutableStateOf(false) }
    val connected = state is ConnectionState.Connected
    val scope = rememberCoroutineScope()

    val bracket by app.camera.bracketing.collectAsState()
    val pending by app.tether.pendingOnCard.collectAsState()

    var picker by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var showInterval by remember { mutableStateOf(false) }
    var showPresets by remember { mutableStateOf(false) }
    var showSummary by remember { mutableStateOf(false) }
    // null: closed; false: rename or go back to a session; true: a new session for the next client.
    var sessionSheet by remember { mutableStateOf<Boolean?>(null) }
    var follow by remember { mutableStateOf<Pair<String, String>?>(null) }
    val sessionCount = remember(photos, settings.sessionKey) { photos.count { it.session == settings.sessionKey } }
    val activeClient by androidx.compose.runtime.produceState<String?>(null, settings.deliveryActiveSession, settings.deliveryEnabled, deliveryStatus) {
        value = app.delivery.activeClient()?.client
    }

    val currentSettings by rememberUpdatedState(settings)
    LaunchedEffect(Unit) {
        app.tether.saved.collect { if (currentSettings.autoOpenViewer) onOpenPhoto(it.id) }
    }

    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding()) {
        // Lying down (a phone on its side, a tablet): the photo, the controls and the shutter side by side.
        val lying = maxWidth > maxHeight && maxWidth >= 600.dp
        val top: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(Modifier.weight(1f), subtitle = "Captura vinculada · Canon 50D")
                RotateButton(app, settings.screenOrientation)
            }
            Spacer(Modifier.height(12.dp))
            SessionChip(settings.sessionName, sessionCount, Modifier.fillMaxWidth()) { sessionSheet = false }
            Spacer(Modifier.height(16.dp))
            ConnectionHeader(state, received, status, settings.showBattery, onConnect = app.camera::findAndConnect)
            // Camera warnings while it is connected; the phone's free space whenever it runs low.
            val phoneLow by app.phoneLowGb.collectAsState()
            com.example.ui.components.CameraWarningChips(
                com.example.ui.components.cameraWarnings(if (connected) status else CameraStatus(), settings, phoneLow),
                Modifier.padding(top = 10.dp),
            )
            FolderNotice(settings, app.store.lostFolders.collectAsState().value, onOpenSettings)
            PendingBanner(pending.size, onDownload = app.tether::downloadPending, onDismiss = app.tether::dismissPending)
            FirstStepsCard(
                settings,
                onDone = { scope.launch { app.settings.set(SettingKeys.firstStepsDone, true) } },
            )
            SaveProblemBanner(
                app.tether.saveProblem.collectAsState().value,
                onOpenSettings = { onOpenSection(com.example.ui.settings.SettingsSection.Save) },
                onDismiss = app.tether::dismissSaveProblem,
            )
        }
        // The photo is what the photographer checks between shots, so it comes right after the status.
        val photo: @Composable () -> Unit = {
            LatestPhoto(photos.firstOrNull(), transfer, onOpenPhoto) { p, mod -> LookImage(app, p, looks, mod) }
            if (photos.size > 1) {
                Spacer(Modifier.height(10.dp))
                RecentStrip(photos.drop(1).take(20), onOpenPhoto)
            }
        }
        val controls: @Composable () -> Unit = {
            ExposureBar(status, connected) { title, prop -> picker = title to prop }
            Spacer(Modifier.height(16.dp))
            ToolsRow(
                settings = settings,
                connected = connected,
                bracket = bracket,
                hasSessionPhotos = photos.any { it.session == settings.sessionKey },
                onPresets = { showPresets = true },
                onBracket = { app.camera.startBracket(settings.aebShots, settings.aebStepThirds) },
                onCard = onOpenCard,
                onClient = onOpenClient,
                onNight = { scope.launch { app.settings.set(SettingKeys.nightMode, !settings.nightMode) } },
                onSummary = { showSummary = true },
                sessionLook = looks.firstOrNull { it.id == settings.sessionPresetId && settings.applyPresetToNew }?.name,
                onLook = { showLooks = true },
                onMoment = { showMoments = true },
                onTools = onOpenTools,
                onLive = onOpenLive,
                profile = activeProfile,
                onProfile = { showProfiles = true },
                hasLatest = photos.firstOrNull() != null,
                latestName = photos.firstOrNull()?.savedBase,
                onEditLatest = { photos.firstOrNull()?.let { onEditPhoto(it.id) } },
                onCropLatest = { photos.firstOrNull()?.let { onCropPhoto(it.id) } },
                onWhatsAppLatest = { whatsFor = photos.firstOrNull() },
                onLightroomLatest = {
                    photos.firstOrNull()?.let { p ->
                        val set = if (p.editedUri != null) ShareSet.EDITED else if (p.jpgUri != null) ShareSet.JPG else ShareSet.RAW
                        scope.launch { actions.openInLightroom(listOf(p), set, settings) }
                    }
                },
                onNoteLatest = { noteFor = photos.firstOrNull() },
                onShop = onOpenShop?.takeIf { settings.shopEnabled },
                onPresetsTab = onOpenPresets,
                onCull = onOpenCull,
                onFolders = { onOpenSection(com.example.ui.settings.SettingsSection.Save) },
                onMessages = { onOpenSection(com.example.ui.settings.SettingsSection.Messages) },
                onLog = { onOpenSection(com.example.ui.settings.SettingsSection.Diagnostics) },
                // Capturar → Selecionar → Editar → Entregar: the last step one tap away.
                onDelivery = onOpenDelivery,
                deliveryState = when (val d = deliveryStatus) {
                    // "→ Ana": who gets the photos that go by themselves, so the next client's never go to the last one.
                    is com.example.core.delivery.DeliveryStatus.Running -> buildString {
                        append(if (deliveryOnline > 0) "no ar · $deliveryOnline" else "no ar")
                        activeClient?.let { append(" → $it") }
                    }
                    else -> null
                },
                onDeliverLatest = if (settings.deliveryEnabled) ({ deliverFor = photos.firstOrNull() }) else null,
                onExpressLatest = if (settings.expressEnabled) ({
                    photos.firstOrNull()?.let { com.example.ui.delivery.sendToExpress(app, context, scope, listOf(it.id), onOpenExpress) }
                }) else null,
                onExpress = onOpenExpress,
                expressState = if (settings.expressEnabled && expressStatus is com.example.core.delivery.DeliveryStatus.Running) {
                    if (settings.expressPreview) "no ar · prévia" else "no ar · liberado"
                } else null,
            )
        }
        val capture: @Composable (Boolean) -> Unit = { vertical ->
            CaptureBar(
                vertical = vertical,
                connected = connected,
                interval = interval,
                shutter = shutter,
                live = settings.liveViewEnabled,
                latest = photos.firstOrNull(),
                onShoot = { app.camera.capture(settings.shutterWait, settings.shutterFocusFirst) },
                onInterval = { if (interval.running) app.camera.stopInterval() else showInterval = true },
                onLive = onOpenLive,
                onOpenLatest = onOpenPhoto,
            )
        }

        if (lying) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1.15f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp)) { photo() }
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 16.dp, end = 12.dp)) {
                    top()
                    Spacer(Modifier.height(16.dp))
                    controls()
                    Spacer(Modifier.height(16.dp))
                }
                // The shutter rail sits under the right thumb, like the grip of a camera.
                capture(true)
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    top()
                    Spacer(Modifier.height(16.dp))
                    photo()
                    Spacer(Modifier.height(16.dp))
                    controls()
                }
                // Docked at the bottom: the shutter is always under the thumb, wherever the page is scrolled.
                capture(false)
            }
        }
    }

    picker?.let { (title, prop) ->
        val options = status.options(prop).ifEmpty { EosValues.defaultValues(prop) }
        ValuePickerSheet(
            title = title,
            prop = prop,
            options = options,
            current = status.value(prop),
            onPick = {
                app.camera.setProperty(prop, it)
                picker = null
            },
            onDismiss = { picker = null },
        )
    }

    if (showPresets) {
        PresetsSheet(
            presets = settings.exposurePresets,
            status = status,
            connected = connected,
            onApply = {
                app.camera.applyPreset(it)
                showPresets = false
            },
            onSave = { preset -> scope.launch { app.settings.savePresets(settings.exposurePresets + preset) } },
            onDismiss = { showPresets = false },
        )
    }

    if (showMoments) {
        MomentSheet(
            moments = settings.moments,
            current = settings.currentMoment,
            subfolder = settings.momentSubfolder,
            onPick = { m -> scope.launch { app.settings.set(SettingKeys.currentMoment, m) }; showMoments = false },
            onDismiss = { showMoments = false },
        )
    }

    if (showProfiles) {
        com.example.ui.settings.ProfileSheet(app, onManage = onOpenProfiles, onDismiss = { showProfiles = false })
    }

    if (showLooks) {
        SessionLookSheet(app = app, presets = looks, settings = settings, onDismiss = { showLooks = false })
    }

    if (showSummary) {
        SessionSummaryDialog(
            app = app,
            actions = actions,
            settings = settings,
            connected = connected,
            onDisconnect = {
                showSummary = false
                app.camera.disconnect()
            },
            onNewSession = {
                showSummary = false
                sessionSheet = true
            },
            onDismiss = { showSummary = false },
        )
    }

    sessionSheet?.let { startNew ->
        SessionNameSheet(
            app = app,
            current = settings.sessionName,
            startNew = startNew,
            onStarted = { name ->
                sessionSheet = null
                if (name.isNotBlank()) scope.launch {
                    val active = app.delivery.activeClient()
                    if (active != null && !active.client.equals(name, ignoreCase = true)) follow = name to active.client
                }
            },
            onDismiss = { sessionSheet = null },
        )
    }

    follow?.let { (name, previous) ->
        FollowSessionDialog(
            session = name,
            previousClient = previous,
            onFollow = {
                follow = null
                scope.launch { app.delivery.followSession(name) }
            },
            onKeep = { follow = null },
        )
    }

    deliverFor?.let { p -> com.example.ui.delivery.DeliverSheet(app, listOf(p.id), onDismiss = { deliverFor = null }, onOpenDelivery = onOpenDelivery) }
    whatsFor?.let { p ->
        com.example.ui.photos.WhatsAppSheet(app, actions, settings, listOf(p), onDismiss = { whatsFor = null })
    }
    noteFor?.let { p ->
        com.example.ui.photos.NoteSheet(app, p, onDismiss = { noteFor = null })
    }

    if (showInterval) {
        IntervalDialog(
            onStart = { seconds, shots ->
                app.camera.startInterval(seconds, shots)
                showInterval = false
            },
            onDismiss = { showInterval = false },
        )
    }
}

@Composable
private fun ConnectionHeader(state: ConnectionState, received: Int, status: CameraStatus, showBattery: Boolean, onConnect: () -> Unit) {
    // One line for the state, with the brand block in the state's colour; the details below it.
    val mark by androidx.compose.animation.animateColorAsState(
        when (state) {
            is ConnectionState.Connected -> Tone.Accent
            is ConnectionState.Failed -> Tone.Error
            is ConnectionState.Reconnecting -> Tone.Warning
            else -> Tone.Line
        },
        androidx.compose.animation.core.tween(com.example.ui.components.Motion.LONG),
        label = "state",
    )
    val title = when (state) {
        is ConnectionState.Connected -> "Conectada · ${state.model.removePrefix("Canon ")}"
        ConnectionState.Connecting -> "Conectando…"
        ConnectionState.WaitingPermission -> "Permissão USB"
        ConnectionState.NoCamera -> "Desconectada"
        is ConnectionState.Failed -> "Falha na conexão"
        is ConnectionState.Reconnecting -> "Reconectando ${state.attempt}/${com.example.core.camera.Reconnect.TRIES}"
    }
    val detail = when (state) {
        is ConnectionState.Connected -> null
        ConnectionState.Connecting -> "Abrindo sessão com a câmera"
        ConnectionState.WaitingPermission -> "Toque em Permitir no aviso do Android"
        ConnectionState.NoCamera -> "Ligue a 50D e conecte o cabo USB OTG"
        is ConnectionState.Failed -> state.message
        is ConnectionState.Reconnecting -> "${state.reason}. As fotos feitas agora chegam depois."
    }
    val stateLine: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state == ConnectionState.Connecting) com.example.ui.components.BrandLoader(22.dp) else StatusMark(mark, height = 20.dp)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                androidx.compose.animation.AnimatedContent(
                    targetState = title,
                    transitionSpec = {
                        (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(com.example.ui.components.Motion.SHORT, 80)) +
                            androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(com.example.ui.components.Motion.MEDIUM, 80, com.example.ui.components.Motion.Emphasized)) { it / 2 }) togetherWith
                            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(100))
                    },
                    label = "title",
                ) { Text(it, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                detail?.let { com.example.ui.components.RollingText(it, style = MaterialTheme.typography.bodyMedium, color = Tone.TextMuted) }
            }
        }
    }
    when (state) {
        is ConnectionState.Connected -> Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
            stateLine()
            // The brand's mint → cyan line: one of the two places the gradient appears (the other is the shutter).
            Box(Modifier.fillMaxWidth().height(2.dp).clip(MaterialTheme.shapes.extraSmall).background(com.example.ui.theme.BrandGradient))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                if (showBattery) status.value(EosProp.BATTERY_POWER)?.let { MiniReadout("Bateria", EosValues.batteryLabel(it)) }
                status.shotsLeft?.let { MiniReadout("Cartão", "$it") }
                MiniReadout("Recebidas", "$received")
            }
        }
        ConnectionState.Connecting, is ConnectionState.Reconnecting -> stateLine()
        // Not connected: connecting is the one thing to do here, so it is the screen's mint button.
        else -> com.example.ui.components.JrCard {
            stateLine()
            com.example.ui.components.JrButton("Conectar câmera", onConnect, Modifier.fillMaxWidth(), icon = Icons.Outlined.Usb)
        }
    }
}

/** A small label and its value side by side, the value with steady digits. */
@Composable
private fun MiniReadout(label: String, value: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Tone.TextMuted)
        Spacer(Modifier.width(Space.xs + 2.dp))
        Text(value, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), color = Tone.Text)
    }
}

@Composable
private fun FolderNotice(settings: Settings, lost: Set<com.example.core.storage.FileKind>, onOpenSettings: () -> Unit) {
    val missing = buildList {
        if (settings.downloadMode.jpg && settings.jpgFolder == null) add("JPG")
        if (settings.downloadMode.raw && settings.rawFolder == null) add("RAW")
    }
    val gone = lost.filter { it == com.example.core.storage.FileKind.JPG || it == com.example.core.storage.FileKind.RAW }.map { it.folderName }
    if (missing.isEmpty() && gone.isEmpty()) return
    Spacer(Modifier.height(Space.m))
    if (gone.isNotEmpty()) {
        // The folder that was working stopped (deleted, card out, permission gone): nothing is lost, but say so.
        com.example.ui.components.JrBanner(
            com.example.ui.components.BannerKind.Error,
            "A pasta ${gone.joinToString(" e ")} parou de responder",
            text = "As fotos estão indo para a pasta interna do app. Toque para escolher a pasta de novo.",
            onClick = onOpenSettings,
        )
    } else {
        com.example.ui.components.JrBanner(
            com.example.ui.components.BannerKind.Info,
            "Pasta ${missing.joinToString(" e ")} não escolhida",
            text = "As fotos vão para a pasta interna do app. Toque para escolher.",
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun ExposureBar(status: CameraStatus, connected: Boolean, onPick: (String, Int) -> Unit) {
    fun text(prop: Int) = status.value(prop)?.let { EosValues.label(prop, it) } ?: "--"
    val mode = status.value(EosProp.SHOOTING_MODE)
    // Like on the camera body: Tv is only settable in Tv/M/B, Av only in Av/M/B.
    val tvEditable = connected && mode in setOf(1, 3, 4)
    val avEditable = connected && mode in setOf(2, 3, 4)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(Tone.Surface)
            .padding(4.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Readout("Modo", text(EosProp.SHOOTING_MODE), false, {}, Modifier.weight(1f))
            Readout("Tv", text(EosProp.SHUTTER_SPEED), tvEditable, { onPick("Velocidade", EosProp.SHUTTER_SPEED) }, Modifier.weight(1.2f))
            Readout("Av", text(EosProp.APERTURE), avEditable, { onPick("Abertura", EosProp.APERTURE) }, Modifier.weight(1.2f))
        }
        Row(Modifier.fillMaxWidth()) {
            Readout("ISO", text(EosProp.ISO), connected, { onPick("ISO", EosProp.ISO) }, Modifier.weight(1f))
            Readout("±EV", text(EosProp.EXPOSURE_COMPENSATION), connected && mode != 3, { onPick("Compensação", EosProp.EXPOSURE_COMPENSATION) }, Modifier.weight(1.2f))
            Readout("WB", text(EosProp.WHITE_BALANCE), connected, { onPick("Balanço de branco", EosProp.WHITE_BALANCE) }, Modifier.weight(1.2f))
        }
    }
}

/**
 * The capture controls, fixed where the thumb is: a bar across the bottom when standing, a rail on the
 * right when lying down. Interval on one side, the shutter in the middle, live view (or the last frame)
 * on the other.
 */
@Composable
private fun CaptureBar(
    vertical: Boolean,
    connected: Boolean,
    interval: IntervalState,
    shutter: ShutterState,
    live: Boolean,
    latest: Photo?,
    onShoot: () -> Unit,
    onInterval: () -> Unit,
    onLive: () -> Unit,
    onOpenLatest: (Long) -> Unit,
) {
    val hint = when (shutter) {
        is ShutterState.Focusing -> "Focando…"
        ShutterState.Fired -> "Disparou"
        is ShutterState.Failed -> "Não disparou"
        ShutterState.Idle -> if (connected) null else "Conecte a câmera"
    }
    val shutterBlock: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Smaller and grey until there is a camera to fire: the shutter is the biggest thing on screen only when it works.
            ShutterButton(shutter, connected, if (connected) 72.dp else 60.dp, Modifier.semantics { contentDescription = "Disparar" }, onShoot)
            Text(
                hint ?: " ", style = MaterialTheme.typography.labelSmall, maxLines = 1,
                color = if (shutter is ShutterState.Failed) Tone.Error else Tone.TextMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
    val intervalBlock: @Composable () -> Unit = {
        CaptureSide(
            icon = Icons.Outlined.Timer,
            label = if (interval.running) "${interval.done}/${interval.total}" else "Intervalo",
            on = interval.running,
            enabled = connected,
            onClick = onInterval,
        )
    }
    val otherBlock: @Composable () -> Unit = {
        if (live) {
            CaptureSide(Icons.Outlined.Videocam, "Live view", on = false, enabled = connected, onClick = onLive)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(52.dp).clip(MaterialTheme.shapes.small).background(Tone.SurfaceHigh)
                        .shutterFlash(latest?.id)
                        .clickable(enabled = latest != null) { latest?.let { onOpenLatest(it.id) } },
                ) {
                    latest?.lookUri?.let {
                        AsyncImage(model = it, contentDescription = "Última foto", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
                Text("Última", style = MaterialTheme.typography.labelSmall, color = Tone.TextMuted, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
    if (vertical) {
        Column(
            Modifier.fillMaxHeight().width(104.dp).background(Tone.Surface).navigationBarsPadding().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            otherBlock()
            shutterBlock()
            intervalBlock()
        }
    } else {
        Row(
            Modifier.fillMaxWidth().background(Tone.Surface).padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { intervalBlock() }
            shutterBlock()
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { otherBlock() }
        }
    }
}

/** A control beside the shutter: the app's tool tile, mint while it runs (the interval timer). */
@Composable
private fun CaptureSide(icon: ImageVector, label: String, on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    com.example.ui.components.JrTile(icon, label, onClick, enabled = enabled, active = on)
}

@Composable
private fun LatestPhoto(
    photo: Photo?,
    transfer: Transfer?,
    onOpen: (Long) -> Unit,
    image: @Composable (Photo, Modifier) -> Unit,
) {
    SectionLabel("Última foto")
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 2f)
            .clip(MaterialTheme.shapes.medium)
            .background(Tone.Surface)
            // Every new frame lands with a shutter blink: the photographer sees it arrived without reading.
            .shutterFlash(photo?.id)
            .clickable(enabled = photo != null) { photo?.let { onOpen(it.id) } },
    ) {
        Crossfade(targetState = photo?.takeIf { it.displayUri != null }, label = "latest") { shown ->
            if (shown != null) {
                image(shown, Modifier.fillMaxSize())
            } else {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    com.example.ui.components.BrandLoader(48.dp, still = true)
                    Spacer(Modifier.height(Space.m))
                    Text(
                        "Cada foto feita pelo botão da câmera ou pelo disparador aparece aqui e é salva sozinha no celular.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Tone.TextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
        if (photo != null) {
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Tone.Scrim).padding(horizontal = Space.m, vertical = Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(photo.savedBase, style = MaterialTheme.typography.labelLarge, color = Tone.OnPhoto, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(
                    listOfNotNull(photo.shutter, photo.aperture, photo.iso).joinToString("   "),
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    color = Tone.OnPhoto,
                )
            }
            FormatTag(photo.rawUri != null, photo.jpgUri != null, Modifier.align(Alignment.TopEnd).padding(Space.s))
        }
    }
    if (transfer != null) {
        // The file coming in, with the brand loader filling a block per quarter.
        Row(Modifier.padding(top = Space.s), verticalAlignment = Alignment.CenterVertically) {
            com.example.ui.components.BrandLoader(20.dp, progress = transfer.progress)
            Spacer(Modifier.width(Space.s))
            Text(
                "Recebendo ${transfer.fileName}" + if (transfer.queued > 0) " · mais ${transfer.queued} na fila" else "",
                style = MaterialTheme.typography.bodySmall,
                color = Tone.TextMuted,
            )
        }
    }
}

@Composable
private fun RecentStrip(photos: List<Photo>, onOpen: (Long) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(photos, key = { it.id }) { photo ->
            AsyncImage(
                model = photo.lookUri,
                contentDescription = photo.savedBase,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .animateItem()
                    .size(width = 84.dp, height = 56.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(Tone.Surface)
                    .clickable { onOpen(photo.id) },
            )
        }
    }
}

@Composable
internal fun IntervalDialog(onStart: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    var seconds by remember { mutableStateOf("10") }
    var shots by remember { mutableStateOf("30") }
    val s = seconds.toIntOrNull()?.takeIf { it in 1..3600 }
    val n = shots.toIntOrNull()?.takeIf { it in 1..9999 }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Tone.Surface,
        title = { Text("Intervalômetro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = seconds, onValueChange = { seconds = it.filter(Char::isDigit) },
                    label = { Text("Intervalo (segundos)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = shots, onValueChange = { shots = it.filter(Char::isDigit) },
                    label = { Text("Quantidade de fotos") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (s != null && n != null) {
                    val total = s.toLong() * (n - 1)
                    Text("Duração: ${total / 60} min ${total % 60} s", style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onStart(s!!, n!!) },
                enabled = s != null && n != null,
                colors = ButtonDefaults.buttonColors(containerColor = Tone.Accent, contentColor = Tone.OnAccent),
            ) { Text("Iniciar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
