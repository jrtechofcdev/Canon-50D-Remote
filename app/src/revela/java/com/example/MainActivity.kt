package com.example

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.core.camera.ConnectionState
import com.example.core.data.Settings
import com.example.core.log.AppLog
import com.example.revela.BrandSetupScreen
import com.example.revela.CaptureScreen
import com.example.revela.Place
import com.example.revela.RevelaDrawer
import com.example.ui.PhotoActions
import com.example.ui.card.CardScreen
import com.example.ui.client.ClientModeScreen
import com.example.ui.components.ScreenTurn
import com.example.ui.crop.CropScreen
import com.example.ui.cull.CullScreen
import com.example.ui.delivery.DeliveryScreen
import com.example.ui.delivery.ExpressScreen
import com.example.ui.live.LiveViewScreen
import com.example.ui.photos.PhotosScreen
import com.example.ui.photos.ViewerScreen
import com.example.ui.preset.EditTarget
import com.example.ui.preset.EditorScreen
import com.example.ui.preset.PresetsScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsSection
import com.example.ui.settings.SettingsSectionScreen
import com.example.ui.shop.ShopDetailScreen
import com.example.ui.shop.ShopScreen
import com.example.ui.theme.JrLinkTheme
import com.example.ui.theme.Tone
import com.example.ui.tools.ToolsScreen
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * Revela: the same tools as JR C50D Sync, arranged its own way. The Capture screen is home; every other
 * place opens from the menu on its left (docs/PLANO_REVELA.md). The first launch asks for "Sua marca".
 */
class MainActivity : ComponentActivity() {

    private val app get() = application as TetherApp

    /** A screen asked from outside (the delivery notification). */
    private val pendingRoute = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = getSharedPreferences("screen", MODE_PRIVATE)
            .getInt("orientation", android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        enableEdgeToEdge()
        app.camera.findAndConnect()
        if (intent?.action == ACTION_DELIVERY) pendingRoute.value = intent.getStringExtra(EXTRA_ROUTE) ?: "delivery"
        val actions = PhotoActions(this, app.store, app.export, app.looks)

        setContent {
            // Null until the settings are read, so the first launch never flashes the wrong screen.
            val loaded by app.settings.settings.collectAsState(initial = null)
            val settings = loaded ?: Settings()
            val cameraState by app.camera.state.collectAsState()
            WindowPreferences(
                nightBrightness = if (settings.nightMode) settings.nightBrightness else null,
                keepScreenOn = settings.keepScreenOn && cameraState is ConnectionState.Connected,
            )
            val chosen by remember { app.settings.settings.map { it.screenOrientation } }.collectAsState(initial = null)
            LaunchedEffect(chosen) {
                val id = chosen ?: return@LaunchedEffect
                val target = ScreenTurn.of(id).orientation
                if (requestedOrientation != target) requestedOrientation = target
                getSharedPreferences("screen", MODE_PRIVATE).edit().putInt("orientation", target).apply()
            }
            JrLinkTheme(night = settings.nightMode, accent = settings.brandColor.takeIf { it != 0 }?.let { Color(it) }) {
                if (loaded == null) {
                    Box(Modifier.fillMaxSize().background(Tone.Background))
                } else {
                    RevelaApp(settings, actions)
                }
            }
        }
    }

    @Composable
    private fun RevelaApp(settings: Settings, actions: PhotoActions) {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val start = remember { if (settings.brandSetupDone) CAPTURE else WELCOME }
        val route = entry?.destination?.route ?: start
        LaunchedEffect(route) { AppLog.i("TELA", route) }
        val drawer = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val snackbar = remember { SnackbarHostState() }
        Messages(snackbar)

        val asked by pendingRoute.collectAsState()
        LaunchedEffect(asked) {
            val r = asked ?: return@LaunchedEffect
            nav.navigate(r) { launchSingleTop = true }
            pendingRoute.value = null
        }

        val photos by remember { app.photos }.collectAsState(initial = emptyList())
        val delivery by app.delivery.status.collectAsState()
        val express by app.express.status.collectAsState()
        val onAir = stringResource(com.example.R.string.nav_on_air)
        val off = stringResource(com.example.R.string.nav_off)
        val places = listOfNotNull(
            Place(CAPTURE, stringResource(com.example.R.string.nav_capture), stringResource(com.example.R.string.nav_capture_sub)),
            Place("photos", stringResource(com.example.R.string.nav_gallery), photos.size.toString()),
            Place("cull", stringResource(com.example.R.string.nav_cull), stringResource(com.example.R.string.nav_cull_sub)),
            Place("presets", stringResource(com.example.R.string.nav_looks), stringResource(com.example.R.string.nav_looks_sub)),
            Place("shop", stringResource(com.example.R.string.nav_shop)).takeIf { settings.shopEnabled },
            Place("delivery", stringResource(com.example.R.string.nav_delivery), if (delivery is com.example.core.delivery.DeliveryStatus.Running) onAir else off),
            Place("express", stringResource(com.example.R.string.nav_express), if (express is com.example.core.delivery.DeliveryStatus.Running) onAir else off),
            Place("live", stringResource(com.example.R.string.nav_live)),
            Place("card", stringResource(com.example.R.string.nav_card)),
            Place("client", stringResource(com.example.R.string.nav_client)),
            Place("tools", stringResource(com.example.R.string.nav_tools)),
        )
        val footer = listOf(
            Place(BRAND, stringResource(com.example.R.string.nav_brand)),
            Place("settings", stringResource(com.example.R.string.nav_settings)),
        )
        val topLevel = (places + footer).map { it.route }.toSet()
        val go = { r: String ->
            scope.launch { drawer.close() }
            if (r != route) goTo(nav, r)
        }
        val openMenu: () -> Unit = { scope.launch { drawer.open() } }

        ModalNavigationDrawer(
            drawerState = drawer,
            // The edge swipe opens it from any main place; never over a photo, where swipes change frames.
            gesturesEnabled = drawer.isOpen || (route in topLevel && route != CAPTURE),
            scrimColor = Color.Black.copy(alpha = 0.6f),
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = Tone.Surface, drawerShape = androidx.compose.ui.graphics.RectangleShape) {
                    RevelaDrawer(settings, places, footer, route, go)
                }
            },
        ) {
            Box(Modifier.fillMaxSize().background(Tone.Background)) {
                NavHost(
                    nav, startDestination = start, modifier = Modifier.fillMaxSize(),
                    enterTransition = com.example.ui.components.Motion.enter,
                    exitTransition = com.example.ui.components.Motion.exit,
                    popEnterTransition = com.example.ui.components.Motion.popEnter,
                    popExitTransition = com.example.ui.components.Motion.popExit,
                ) {
                    composable(WELCOME) {
                        BrandSetupScreen(app, settings, firstRun = true, onDone = {
                            nav.navigate(CAPTURE) { popUpTo(WELCOME) { inclusive = true } }
                        }, onBack = null)
                    }
                    composable(BRAND) {
                        BrandSetupScreen(app, settings, firstRun = false, onDone = { nav.popBackStack() }, onBack = { nav.popBackStack() })
                    }
                    composable(CAPTURE) {
                        CaptureScreen(
                            app, settings,
                            onMenu = openMenu,
                            onOpenPhoto = { nav.navigate("photo/$it") { launchSingleTop = true } },
                            onOpenGallery = { goTo(nav, "photos") },
                            onOpenLive = { nav.navigate("live") },
                        )
                    }
                    composable("photos") {
                        PhotosScreen(
                            app = app, actions = actions,
                            onOpenPhoto = { nav.navigate("photo/$it") },
                            onSelectingChange = {},
                            onCrop = { id -> nav.navigate("editor/crop/$id") },
                            onOpenDelivery = { goTo(nav, "delivery") },
                            onOpenExpress = { goTo(nav, "express") },
                        )
                    }
                    composable("cull") { CullScreen(app, onBack = { nav.popBackStack() }, onOpenPhoto = { nav.navigate("photo/$it") }) }
                    composable("live") { LiveViewScreen(app, onBack = { nav.popBackStack() }) }
                    composable("delivery") {
                        DeliveryScreen(app = app, actions = actions, onBack = { nav.popBackStack() }, onSettings = { nav.navigate("settings/${SettingsSection.Delivery.route}") })
                    }
                    composable("express") {
                        ExpressScreen(app = app, onBack = { nav.popBackStack() }, onSettings = { nav.navigate("settings/${SettingsSection.Express.route}") })
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
                    composable("settings") { SettingsScreen(onOpen = { nav.navigate("settings/${it.route}") }) }
                    composable("settings/{section}") { e ->
                        val section = SettingsSection.entries.firstOrNull { it.route == e.arguments?.getString("section") } ?: SettingsSection.Save
                        SettingsSectionScreen(app = app, section = section, onBack = { nav.popBackStack() })
                    }
                    composable("presets") { PresetsScreen(app = app, onEdit = { nav.navigate("editor/preset/$it") }) }
                    composable("shop") { ShopScreen(app = app, onOpen = { nav.navigate("shop/$it") }) }
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
                    composable("photo/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                        ViewerScreen(
                            app = app, actions = actions,
                            startId = it.arguments?.getLong("id") ?: 0L,
                            onBack = { nav.popBackStack() },
                            onCalibrate = { id -> nav.navigate("editor/photo/$id") },
                            onCrop = { id -> nav.navigate("editor/crop/$id") },
                            onOpenDelivery = { goTo(nav, "delivery") },
                            onOpenExpress = { goTo(nav, "express") },
                        )
                    }
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 8.dp)) { data ->
                    Snackbar(data, containerColor = Tone.SurfaceHigh, contentColor = Tone.Text, actionColor = Tone.Accent)
                }
            }
        }
    }

    /** Camera, saving and culling messages, and Undo after a delete: the newest replaces the one on screen. */
    @Composable
    private fun Messages(snackbar: SnackbarHostState) {
        val undo = stringResource(com.example.R.string.undo)
        val one = stringResource(com.example.R.string.deleted_one)
        val many = stringResource(com.example.R.string.deleted_many)
        LaunchedEffect(Unit) {
            val cull = app.culler.warnings.map { w ->
                val problems = listOfNotNull(
                    "tremida ou fora de foco".takeIf { w.blurry },
                    "olhos fechados (${w.eyesClosed} de ${w.faces})".takeIf { w.eyesClosed > 0 },
                )
                "${w.photo.savedBase}: ${problems.joinToString(", ")}"
            }
            var importantUntil = 0L
            merge(
                app.camera.messages.map { it to true },
                app.tether.errors.map { it to true },
                app.notices.map { it to true },
                cull.map { it to false },
            ).collect { (text, important) ->
                AppLog.i("AVISO", text)
                val now = System.currentTimeMillis()
                if (!important && now < importantUntil) return@collect
                if (important) importantUntil = now + 10_000
                snackbar.currentSnackbarData?.dismiss()
                launch {
                    snackbar.showSnackbar(text, withDismissAction = important, duration = if (important) SnackbarDuration.Long else SnackbarDuration.Short)
                }
            }
        }
        LaunchedEffect(Unit) {
            app.trash.deleted.collect { batch ->
                snackbar.currentSnackbarData?.dismiss()
                launch {
                    val n = batch.ids.size
                    val result = snackbar.showSnackbar(if (n == 1) one else many.format(n), actionLabel = undo, duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) app.trash.undo(batch)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_DELIVERY) pendingRoute.value = intent.getStringExtra(EXTRA_ROUTE) ?: "delivery"
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
        if (app.camera.state.value == ConnectionState.NoCamera) app.camera.findAndConnect()
    }

    companion object {
        const val ACTION_DELIVERY = "com.example.OPEN_DELIVERY"
        const val EXTRA_ROUTE = "route"
        const val CAPTURE = "capture"
        const val WELCOME = "welcome"
        const val BRAND = "brand"
    }
}

/** A main place opens like a tab: one Capture underneath, the place on top, its state kept. */
private fun goTo(nav: NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo(MainActivity.CAPTURE) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Night brightness and keep-screen-on, for this window only. */
@Composable
private fun WindowPreferences(nightBrightness: Int?, keepScreenOn: Boolean) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(nightBrightness, keepScreenOn) {
        val window = activity.window
        window.attributes = window.attributes.apply {
            screenBrightness = nightBrightness?.let { it.coerceIn(1, 100) / 100f } ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { }
    }
}
