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
import com.example.ui.components.CaptureFab
import com.example.ui.components.ScreenTurn
import com.example.ui.components.ScreenOverride
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
        askNotificationPermissionOnce()
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
            // The position chosen in the menu applies to every screen; the viewer and client mode may ask
            // for another one while a photo is shown. Applied only once the real choice is loaded, so the
            // app never turns by itself on the way in.
            val chosen by remember { app.settings.settings.map { it.screenOrientation } }.collectAsState(initial = null)
            val override by ScreenOverride.orientation.collectAsState()
            LaunchedEffect(chosen, override) {
                val id = chosen ?: return@LaunchedEffect
                val target = override ?: ScreenTurn.of(id).orientation
                if (requestedOrientation != target) {
                    requestedOrientation = target
                    AppLog.i("TELA", "Posição: " + (override?.let { "segue a foto" } ?: ScreenTurn.of(id).label))
                }
                getSharedPreferences("screen", MODE_PRIVATE).edit().putInt("orientation", ScreenTurn.of(id).orientation).apply()
            }
            LaunchedEffect(settings.introEnabled) { screenPrefs.edit().putBoolean("intro", settings.introEnabled).apply() }
            LaunchedEffect(Unit) {
                var first = true
                com.example.ui.intro.IntroRequests.replay.collect { if (!first) intro.value = true; first = false }
            }
            JrLinkTheme(night = settings.nightMode) {
              androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                val nav = rememberNavController()
                val entry by nav.currentBackStackEntryAsState()
                val route = entry?.destination?.route ?: Tab.Session.route
                LaunchedEffect(route) { AppLog.i("TELA", route) }
                val snackbar = remember { SnackbarHostState() }
                var gallerySelecting by remember { mutableStateOf(false) }
                val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                // Tabs can be hidden in Ajustes → Tela (Sessão and Ajustes always stay); the Loja also in Ajustes → Presets.
                val hidden = settings.hiddenTabs.split(',').map { it.trim() }.toSet()
                val tabs = Tab.entries.filter { (it != Tab.Shop || settings.shopEnabled) && (!it.hideable || it.route !in hidden) }
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
                // Only on the Session, and only while the camera is not capturing yet. Scrolling the screen up
                // hides it (it covered the tools at the bottom); scrolling back down brings it back.
                var fabShown by remember { mutableStateOf(true) }
                LaunchedEffect(route) { fabShown = true }
                val showFab = route == Tab.Session.route && cameraState !is ConnectionState.Connected
                val fabScroll = remember {
                    object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                        override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                            if (available.y < -6f) fabShown = false else if (available.y > 6f) fabShown = true
                            return androidx.compose.ui.geometry.Offset.Zero
                        }
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
                    merge(app.camera.messages, app.tether.errors, cullMessages).collect {
                        AppLog.i("AVISO", it)
                        snackbar.showSnackbar(it)
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Tone.Background,
                    snackbarHost = { SnackbarHost(snackbar) },
                    floatingActionButton = {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showFab && fabShown,
                            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 2 },
                            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 },
                        ) {
                            CaptureFab(
                                state = cameraState,
                                onStart = {
                                    app.camera.findAndConnect()
                                    openSession()
                                },
                                onOpenSession = openSession,
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
                          // Six tabs don't fit the height of a phone lying down: the rail scrolls, so Ajustes
                          // is never cut off.
                          androidx.compose.foundation.layout.Column(
                              Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                              verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp, androidx.compose.ui.Alignment.CenterVertically),
                              horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                          ) {
                            tabs.forEach { tab ->
                                NavigationRailItem(
                                    selected = route == tab.route,
                                    onClick = { goTo(tab) },
                                    icon = { Icon(tab.icon, contentDescription = null) },
                                    label = { Text(tab.label) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = Tone.Accent,
                                        selectedTextColor = Tone.Text,
                                        indicatorColor = Tone.SurfaceHigh,
                                        unselectedIconColor = Tone.TextMuted,
                                        unselectedTextColor = Tone.TextMuted,
                                    ),
                                )
                            }
                          }
                        }
                    }
                    com.example.ui.components.Motion.tabRoutes = Tab.entries.map { it.route }.toSet()
                    NavHost(
                        nav, startDestination = Tab.Session.route, modifier = Modifier.weight(1f).fillMaxHeight().nestedScroll(fabScroll),
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

    private fun askNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
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
    Icon(
        tab.icon, contentDescription = null,
        tint = if (tint == androidx.compose.ui.graphics.Color.Unspecified) androidx.compose.material3.LocalContentColor.current else tint,
        modifier = Modifier.graphicsLayer { scaleX = hop.value; scaleY = hop.value },
    )
}

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
    val scroll = rememberScrollState()
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxWidth().background(Tone.Surface).navigationBarsPadding(),
    ) {
        val itemWidth = maxOf(maxWidth / tabs.size, 72.dp)
        val selected = tabs.indexOfFirst { it.route == route }
        val density = androidx.compose.ui.platform.LocalDensity.current
        LaunchedEffect(selected, itemWidth) {
            if (selected >= 0) scroll.animateScrollTo(with(density) { (itemWidth * selected - itemWidth * 2).roundToPx() }.coerceAtLeast(0))
        }
        Row(Modifier.horizontalScroll(scroll)) {
            tabs.forEach { tab ->
                val on = route == tab.route
                androidx.compose.foundation.layout.Column(
                    Modifier
                        .width(itemWidth)
                        .height(80.dp)
                        .clickable(role = androidx.compose.ui.semantics.Role.Tab) { onClick(tab) }
                        .padding(top = 12.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                ) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.size(width = 64.dp, height = 32.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                            .background(if (on) Tone.SurfaceHigh else androidx.compose.ui.graphics.Color.Transparent),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) { TabIcon(tab, on, tint = if (on) Tone.Accent else Tone.TextMuted) }
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (on) Tone.Text else Tone.TextMuted,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
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
