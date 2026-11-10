package com.example

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material.icons.outlined.Storefront
import com.example.ui.shop.ShopDetailScreen
import com.example.ui.shop.ShopScreen
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.ui.platform.LocalConfiguration
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.core.camera.ConnectionState
import com.example.core.log.AppLog
import com.example.ui.PhotoActions
import com.example.ui.photos.PhotosScreen
import com.example.ui.photos.ViewerScreen
import com.example.ui.session.SessionScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsSection
import com.example.ui.settings.SettingsSectionScreen
import com.example.ui.card.CardScreen
import com.example.ui.client.ClientModeScreen
import com.example.ui.preset.EditTarget
import com.example.ui.preset.EditorScreen
import com.example.ui.preset.PresetsScreen
import com.example.ui.tools.ToolsScreen
import com.example.ui.cull.CullScreen
import com.example.ui.delivery.DeliveryScreen
import com.example.ui.crop.CropScreen
import com.example.ui.live.LiveViewScreen
import com.example.ui.components.ScreenTurn
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.ThumbUp
import com.example.core.data.Settings
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.activity.compose.LocalActivity
import com.example.ui.theme.Tone
import com.example.ui.theme.JrLinkTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.offset
import kotlinx.coroutines.launch
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.heightIn

class MainActivity : ComponentActivity() {

    private val app get() = application as TetherApp

    /** A screen asked from outside (the delivery notification). */
    private val pendingRoute = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The last position chosen, right away (the settings load a moment later).
        requestedOrientation = getSharedPreferences("screen", MODE_PRIVATE)
            .getInt("orientation", android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        enableEdgeToEdge()
        // The opening plays on a fresh start only (not after a turn of the screen), unless it was switched
        // off in Ajustes or Android's animations are off. Read from a small mirror so there is no blank wait.
        val screenPrefs = getSharedPreferences("screen", MODE_PRIVATE)
        val animationsOn = android.provider.Settings.Global.getFloat(contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        val intro = androidx.compose.runtime.mutableStateOf(savedInstanceState == null && animationsOn && screenPrefs.getBoolean("intro", true))
        // Notifications are asked for in the Session's "Primeiros passos", with the reason, not at launch.
        app.camera.findAndConnect()
        if (intent?.action == ACTION_DELIVERY) pendingRoute.value = intent.getStringExtra(EXTRA_ROUTE) ?: "delivery"

        val actions = PhotoActions(this, app.store, app.export, app.looks)

        setContent {
            val settings by app.settings.settings.collectAsState(initial = Settings())
            val cameraState by app.camera.state.collectAsState()
            WindowPreferences(
                nightBrightness = if (settings.nightMode) settings.nightBrightness else null,
                keepScreenOn = settings.keepScreenOn && cameraState is ConnectionState.Connected,
            )
            // The position chosen on the dial applies to every screen, always: a photo never turns the screen
            // (the photo itself turns with the phone instead, see PhoneTurned). Applied only once the real
            // choice is loaded, so the app never turns by itself on the way in.
            val chosen by remember { app.settings.settings.map { it.screenOrientation } }.collectAsState(initial = null)
            LaunchedEffect(chosen) {
                val id = chosen ?: return@LaunchedEffect
                val target = ScreenTurn.of(id).orientation
                if (requestedOrientation != target) {
                    requestedOrientation = target
                    AppLog.i("TELA", "Posição: " + ScreenTurn.of(id).label)
                }
                getSharedPreferences("screen", MODE_PRIVATE).edit().putInt("orientation", ScreenTurn.of(id).orientation).apply()
            }
            LaunchedEffect(settings.introEnabled) { screenPrefs.edit().putBoolean("intro", settings.introEnabled).apply() }
            LaunchedEffect(Unit) {
                var first = true
                com.example.ui.intro.IntroRequests.replay.collect { if (!first) intro.value = true; first = false }
            }
            JrLinkTheme(night = settings.nightMode) {
              val cameraLost by app.cameraLost.collectAsState()
              val sessionDot = when (cameraState) {
                  is ConnectionState.Connected -> Tone.Accent
                  is ConnectionState.Reconnecting -> Tone.Info
                  is ConnectionState.Failed -> Tone.Error
                  // The cable came out during a shoot: red until the camera is back or deliberately let go.
                  else -> if (cameraLost) Tone.Error else null
              }
              androidx.compose.runtime.CompositionLocalProvider(LocalSessionDot provides sessionDot) {
              androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                val nav = rememberNavController()
                val entry by nav.currentBackStackEntryAsState()
                val route = entry?.destination?.route ?: Tab.Session.route
                LaunchedEffect(route) { AppLog.i("TELA", route) }
                val snackbar = remember { SnackbarHostState() }
                var gallerySelecting by remember { mutableStateOf(false) }
                val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                // Tabs can be hidden in Ajustes → Tela (Sessão and Ajustes always stay); the Loja also in Ajustes → Presets.
                // Entrega and Express only take a place in the bar while they are switched on; off, they still
                // open from the Session's tools to be switched on.
                val hidden = settings.hiddenTabs.split(',').map { it.trim() }.toSet()
                val tabs = Tab.entries.filter {
                    (it != Tab.Shop || settings.shopEnabled) &&
                        (it != Tab.Delivery || settings.deliveryEnabled) &&
                        (it != Tab.Express || settings.expressEnabled) &&
                        (!it.hideable || it.route !in hidden)
                }
                val onTab = tabs.any { it.route == route }
                val goTo = { tab: Tab -> goToTab(nav, tab) }
                // Entrega and Express open as their tab, or as a page when that tab is hidden.
                val openTool = { tab: Tab -> if (tab in tabs) goTo(tab) else nav.navigate(tab.route) { launchSingleTop = true } }
                val asked by pendingRoute.collectAsState()
                LaunchedEffect(asked) {
                    val r = asked ?: return@LaunchedEffect
                    val tab = tabs.firstOrNull { it.route == r }
                    if (tab != null) goTo(tab) else nav.navigate(r) { launchSingleTop = true }
                    pendingRoute.value = null
                }
                val openSession = {
                    nav.navigate(Tab.Session.route) {
                        popUpTo(Tab.Session.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                LaunchedEffect(Unit) {
                    val cullMessages = app.culler.warnings.map { w ->
                        val problems = listOfNotNull(
                            "tremida ou fora de foco".takeIf { w.blurry },
                            "olhos fechados (${w.eyesClosed} de ${w.faces})".takeIf { w.eyesClosed > 0 },
                        )
                        "${w.photo.savedBase}: ${problems.joinToString(", ")}"
                    }
                    val messages = this
                    // Errors (a photo not saved, the camera) outrank the culling hints: a burst of "tremida"
                    // warnings used to wipe a save failure off the screen before anyone read it.
                    var importantUntil = 0L
                    merge(
                        app.camera.messages.map { it to true },
                        app.tether.errors.map { it to true },
                        app.notices.map { it to true },
                        cullMessages.map { it to false },
                    ).collect { (text, important) ->
                        AppLog.i("AVISO", text)
                        val now = System.currentTimeMillis()
                        if (!important && now < importantUntil) return@collect
                        if (important) importantUntil = now + 10_000
                        // The newest message replaces the one on screen: a burst of warnings used to queue up
                        // and keep showing old news for minutes.
                        snackbar.currentSnackbarData?.dismiss()
                        messages.launch {
                            snackbar.showSnackbar(
                                text,
                                withDismissAction = important,
                                duration = if (important) androidx.compose.material3.SnackbarDuration.Long else androidx.compose.material3.SnackbarDuration.Short,
                            )
                        }
                    }
                }

                // Every delete offers Desfazer while the photo waits in the trash.
                LaunchedEffect(Unit) {
                    val offers = this
                    app.trash.deleted.collect { batch ->
                        snackbar.currentSnackbarData?.dismiss()
                        offers.launch {
                            val n = batch.ids.size
                            val result = snackbar.showSnackbar(
                                if (n == 1) "Foto apagada do celular" else "$n fotos apagadas do celular",
                                actionLabel = "Desfazer",
                                duration = androidx.compose.material3.SnackbarDuration.Long,
                            )
                            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) app.trash.undo(batch)
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Tone.Background,
                    snackbarHost = {
                        SnackbarHost(snackbar) { data ->
                            androidx.compose.material3.Snackbar(
                                data,
                                containerColor = Tone.SurfaceHigh,
                                contentColor = Tone.Text,
                                actionColor = Tone.Accent,
                            )
                        }
                    },
                    bottomBar = {
                        if (!landscape && onTab) BottomTabs(tabs, route, onClick = goTo)
                    },
                ) { padding ->
                  Row(Modifier.fillMaxSize().padding(padding)) {
                    // Lying down, the tabs move to a rail on the side so the screen keeps its height.
                    if (landscape && onTab) {
                        NavigationRail(containerColor = Tone.Surface, modifier = Modifier.fillMaxHeight()) {
                          val railItem: @Composable (Tab) -> Unit = { tab ->
                              // The same tab as the bottom bar: the mint brand block over the one that is open.
                              BarTab(tab, route == tab.route, 80.dp) { goTo(tab) }
                          }
                          // The tools scroll when they don't fit the height of a phone lying down; Ajustes stays
                          // pinned at the bottom, never cut off.
                          androidx.compose.foundation.layout.Column(
                              Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                              verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                              horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                          ) { tabs.filter { it != Tab.Settings }.forEach { railItem(it) } }
                          androidx.compose.material3.HorizontalDivider(color = Tone.Line, modifier = Modifier.width(56.dp))
                          androidx.compose.foundation.layout.Box(Modifier.padding(vertical = 8.dp)) { railItem(Tab.Settings) }
                        }
                    }
                    com.example.ui.components.Motion.tabRoutes = Tab.entries.map { it.route }.toSet()
                    NavHost(
                        nav, startDestination = Tab.Session.route, modifier = Modifier.weight(1f).fillMaxHeight(),
                        enterTransition = com.example.ui.components.Motion.enter,
                        exitTransition = com.example.ui.components.Motion.exit,
                        popEnterTransition = com.example.ui.components.Motion.popEnter,
                        popExitTransition = com.example.ui.components.Motion.popExit,
                    ) {
                        composable(Tab.Session.route) {
                            SessionScreen(
                                app = app,
                                actions = actions,
                                onOpenPhoto = { nav.navigate("photo/$it") { launchSingleTop = true } },
                                onOpenSettings = { nav.navigate("settings/${SettingsSection.Save.route}") },
                                onOpenCard = { nav.navigate("card") },
                                onOpenClient = { nav.navigate("client") },
                                onOpenTools = { nav.navigate("tools") },
                                onOpenLive = { nav.navigate("live") },
                                onOpenProfiles = { nav.navigate("settings/${SettingsSection.Profiles.route}") },
                                onEditPhoto = { nav.navigate("editor/photo/$it") },
                                onCropPhoto = { nav.navigate("editor/crop/$it") },
                                onOpenShop = { goTo(Tab.Shop) },
                                onOpenPresets = { goTo(Tab.Presets) },
                                onOpenCull = { goTo(Tab.Cull) },
                                onOpenSection = { nav.navigate("settings/${it.route}") },
                                onOpenDelivery = { openTool(Tab.Delivery) },
                                onOpenExpress = { openTool(Tab.Express) },
                            )
                        }
                        composable(Tab.Cull.route) { CullScreen(app, onBack = null, onOpenPhoto = { nav.navigate("photo/$it") }) }
                        composable("live") { LiveViewScreen(app, onBack = { nav.popBackStack() }) }
                        composable(Tab.Delivery.route) {
                            DeliveryScreen(
                                app = app,
                                actions = actions,
                                onBack = if (Tab.Delivery in tabs) null else ({ nav.popBackStack() }),
                                onSettings = { nav.navigate("settings/${SettingsSection.Delivery.route}") },
                            )
                        }
                        composable(Tab.Express.route) {
                            com.example.ui.delivery.ExpressScreen(
                                app = app,
                                onBack = if (Tab.Express in tabs) null else ({ nav.popBackStack() }),
                                onSettings = { nav.navigate("settings/${SettingsSection.Express.route}") },
                            )
                        }
                        composable("tools") { ToolsScreen(onBack = { nav.popBackStack() }) }
                        composable("card") { CardScreen(app = app, onBack = { nav.popBackStack() }) }
                        composable("client") {
                            ClientModeScreen(
                                app = app,
                                onExit = { nav.popBackStack() },
                                onCalibrate = { id -> nav.navigate("editor/photo/$id") },
                                onCrop = { id -> nav.navigate("editor/crop/$id") },
                            )
                        }
                        composable("settings/{section}") { entry ->
                            val section = SettingsSection.entries.firstOrNull { it.route == entry.arguments?.getString("section") }
                                ?: SettingsSection.Save
                            SettingsSectionScreen(app = app, section = section, onBack = { nav.popBackStack() })
                        }
                        composable(Tab.Photos.route) {
                            PhotosScreen(
                                app = app,
                                actions = actions,
                                onOpenPhoto = { nav.navigate("photo/$it") },
                                onSelectingChange = { gallerySelecting = it },
                                onCrop = { id -> nav.navigate("editor/crop/$id") },
                                onOpenDelivery = { openTool(Tab.Delivery) },
                                onOpenExpress = { openTool(Tab.Express) },
                            )
                        }
                        composable(Tab.Presets.route) {
                            PresetsScreen(app = app, onEdit = { nav.navigate("editor/preset/$it") })
                        }
                        composable(Tab.Shop.route) {
                            ShopScreen(app = app, onOpen = { nav.navigate("shop/$it") })
                        }
                        composable("shop/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                            ShopDetailScreen(
                                app = app,
                                itemId = it.arguments?.getLong("id") ?: 0L,
                                onBack = { nav.popBackStack() },
                                onOpenPhoto = { id -> nav.navigate("photo/$id") },
                                onEditPhoto = { id -> nav.navigate("editor/photo/$id") },
                            )
                        }
                        composable("editor/preset/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                            EditorScreen(app, EditTarget.PresetTarget(it.arguments?.getLong("id") ?: 0L), onDone = { nav.popBackStack() })
                        }
                        composable("editor/photo/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                            EditorScreen(app, EditTarget.PhotoTarget(it.arguments?.getLong("id") ?: 0L), onDone = { nav.popBackStack() })
                        }
                        composable("editor/crop/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                            CropScreen(app, it.arguments?.getLong("id") ?: 0L, onDone = { nav.popBackStack() })
                        }
                        composable(Tab.Settings.route) { SettingsScreen(onOpen = { nav.navigate("settings/${it.route}") }) }
                        composable("photo/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                            ViewerScreen(
                                app = app,
                                actions = actions,
                                startId = it.arguments?.getLong("id") ?: 0L,
                                onBack = { nav.popBackStack() },
                                onCalibrate = { id -> nav.navigate("editor/photo/$id") },
                                onCrop = { id -> nav.navigate("editor/crop/$id") },
                                onOpenDelivery = { openTool(Tab.Delivery) },
                                onOpenExpress = { openTool(Tab.Express) },
                            )
                        }
                    }
                  }
                }
                // The opening plays over the app, which is already loading underneath.
                if (intro.value) com.example.ui.intro.BrandIntro(onDone = { intro.value = false })
              }
              }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_DELIVERY) pendingRoute.value = intent.getStringExtra(EXTRA_ROUTE) ?: "delivery"
        // Plugging the camera in while the app is open lands here.
        app.camera.findAndConnect()
    }

    override fun onPause() {
        super.onPause()
        AppLog.i("APP", "Em segundo plano · ${AppLog.memory()}")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppLog.i("APP", "Tela girou: ${if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) "deitada" else "em pé"}")
    }

    override fun onResume() {
        super.onResume()
        AppLog.i("APP", "Em primeiro plano · ${AppLog.memory()}")
        // Only retry silently from "no camera"; after a failure or a denied permission the user taps Conectar,
        // otherwise the permission dialog would reopen every time the activity resumes.
        if (app.camera.state.value == ConnectionState.NoCamera) app.camera.findAndConnect()
    }

    companion object {
        const val ACTION_DELIVERY = "com.example.OPEN_DELIVERY"
        const val EXTRA_ROUTE = "route"
    }
}

/** Night-mode brightness and keep-screen-on, applied to this activity's window only. */
@Composable
private fun WindowPreferences(nightBrightness: Int?, keepScreenOn: Boolean) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(nightBrightness, keepScreenOn) {
        val window = activity.window
        window.attributes = window.attributes.apply {
            screenBrightness = nightBrightness?.let { it.coerceIn(1, 100) / 100f }
                ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { }
    }
}

private fun goToTab(nav: androidx.navigation.NavHostController, tab: Tab) {
    nav.navigate(tab.route) {
        popUpTo(Tab.Session.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** A tab's icon: it gives a small spring hop when its tab is chosen, so the move is seen where the finger is. */
@Composable
private fun TabIcon(tab: Tab, selected: Boolean, tint: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified) {
    val hop = remember { androidx.compose.animation.core.Animatable(1f) }
    val first = remember { mutableStateOf(true) }
    LaunchedEffect(selected) {
        if (first.value) { first.value = false; return@LaunchedEffect }
        if (selected) {
            hop.snapTo(0.82f)
            hop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.4f, stiffness = 420f))
        }
    }
    val dot = if (tab == Tab.Session) LocalSessionDot.current else null
    androidx.compose.foundation.layout.Box {
        Icon(
            tab.icon, contentDescription = null,
            tint = if (tint == androidx.compose.ui.graphics.Color.Unspecified) androidx.compose.material3.LocalContentColor.current else tint,
            modifier = Modifier.graphicsLayer { scaleX = hop.value; scaleY = hop.value },
        )
        // The camera's state is seen from any tab: mint connected, cyan reconnecting, red when it failed.
        val shown by androidx.compose.animation.animateColorAsState(dot ?: androidx.compose.ui.graphics.Color.Transparent, label = "dot")
        if (dot != null || shown.alpha > 0f) {
            androidx.compose.foundation.layout.Box(
                Modifier.align(androidx.compose.ui.Alignment.TopEnd).offset(x = 3.dp, y = (-2).dp).size(8.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape).background(shown),
            )
        }
    }
}

/** Colour of the dot on the Sessão tab (camera connected, reconnecting, failed), or null for none. */
private val LocalSessionDot = androidx.compose.runtime.compositionLocalOf<androidx.compose.ui.graphics.Color?> { null }

/**
 * The tab bar. Up to five tabs share the width as usual; with more, each keeps a readable width and the
 * bar scrolls sideways, so a label is never squeezed and the open tab is scrolled into view.
 */
@Composable
private fun BottomTabs(tabs: List<Tab>, route: String, onClick: (Tab) -> Unit) {
    if (tabs.size <= 5) {
        NavigationBar(containerColor = Tone.Surface) {
            tabs.forEach { tab ->
                NavigationBarItem(
                    selected = route == tab.route,
                    onClick = { onClick(tab) },
                    icon = { TabIcon(tab, route == tab.route) },
                    label = { Text(tab.label, maxLines = 1, softWrap = false) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Tone.Accent,
                        selectedTextColor = Tone.Text,
                        indicatorColor = Tone.SurfaceHigh,
                        unselectedIconColor = Tone.TextMuted,
                        unselectedTextColor = Tone.TextMuted,
                    ),
                )
            }
        }
        return
    }
    // More than five: the tools slide sideways and Ajustes stays pinned on the right, always one tap away.
    val scroll = rememberScrollState()
    val tools = tabs.filter { it != Tab.Settings }
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxWidth().background(Tone.Surface).navigationBarsPadding(),
    ) {
        val itemWidth = maxOf(maxWidth / 5, 72.dp)
        val selected = tools.indexOfFirst { it.route == route }
        val density = androidx.compose.ui.platform.LocalDensity.current
        LaunchedEffect(selected, itemWidth) {
            if (selected >= 0) scroll.animateScrollTo(with(density) { (itemWidth * selected - itemWidth * 1.5f).roundToPx() }.coerceAtLeast(0))
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.Top) {
            Row(Modifier.weight(1f).horizontalScroll(scroll)) {
                tools.forEach { tab -> BarTab(tab, route == tab.route, itemWidth) { onClick(tab) } }
            }
            androidx.compose.foundation.layout.Box(Modifier.padding(vertical = 14.dp).width(1.dp).height(44.dp).background(Tone.Line))
            if (Tab.Settings in tabs) BarTab(Tab.Settings, route == Tab.Settings.route, itemWidth) { onClick(Tab.Settings) }
        }
    }
}

/** One tab of the scrolling bar: icon in its pill, the name under it; grows with large fonts instead of clipping. */
@Composable
private fun BarTab(tab: Tab, on: Boolean, width: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Column(
        Modifier
            .width(width)
            .heightIn(min = 80.dp)
            .selectable(selected = on, role = androidx.compose.ui.semantics.Role.Tab, onClick = onClick)
            .padding(top = 6.dp, bottom = 8.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        // The open tab: the brand's slanted block in mint over a light icon (the app's one sign of "chosen").
        androidx.compose.foundation.layout.Box(
            Modifier.size(width = 18.dp, height = 4.dp).clip(com.example.ui.theme.TileShape(slant = 0.9f, corner = 0.2f))
                .background(if (on) Tone.Accent else androidx.compose.ui.graphics.Color.Transparent),
        )
        androidx.compose.foundation.layout.Box(
            Modifier.padding(top = 6.dp).size(width = 64.dp, height = 28.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) { TabIcon(tab, on, tint = if (on) Tone.Text else Tone.TextMuted) }
        Text(
            tab.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (on) Tone.Text else Tone.TextMuted,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** [hideable]: can be taken off the bar in Ajustes → Tela. */
enum class Tab(val route: String, val label: String, val icon: ImageVector, val hideable: Boolean = true) {
    Session("session", "Sessão", Icons.Outlined.CameraAlt, hideable = false),
    Photos("photos", "Fotos", Icons.Outlined.PhotoLibrary),
    Delivery("delivery", "Entrega", Icons.Outlined.WifiTethering),
    Express("express", "Express", Icons.Outlined.Collections),
    Cull("cull", "Culling", Icons.Outlined.Swipe),
    Presets("presets", "Presets", Icons.Outlined.Style),
    Shop("shop", "Loja", Icons.Outlined.Storefront),
    Settings("settings", "Ajustes", Icons.Outlined.Tune, hideable = false),
}
