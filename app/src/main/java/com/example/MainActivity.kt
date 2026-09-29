package com.example

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
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
import com.example.ui.crop.CropScreen
import com.example.ui.live.LiveViewScreen
import com.example.ui.components.CaptureFab
import com.example.ui.components.ScreenTurn
import com.example.ui.components.ScreenOverride
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.outlined.AutoAwesome
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

class MainActivity : ComponentActivity() {

    private val app get() = application as TetherApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The last position chosen, right away (the settings load a moment later).
        requestedOrientation = getSharedPreferences("screen", MODE_PRIVATE)
            .getInt("orientation", android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        enableEdgeToEdge()
        askNotificationPermissionOnce()
        app.camera.findAndConnect()

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
            JrLinkTheme(night = settings.nightMode) {
                val nav = rememberNavController()
                val entry by nav.currentBackStackEntryAsState()
                val route = entry?.destination?.route ?: Tab.Session.route
                LaunchedEffect(route) { AppLog.i("TELA", route) }
                val snackbar = remember { SnackbarHostState() }
                var gallerySelecting by remember { mutableStateOf(false) }
                val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                val onTab = Tab.entries.any { it.route == route }
                val goTo = { tab: Tab ->
                    nav.navigate(tab.route) {
                        popUpTo(Tab.Session.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                val openSession = {
                    nav.navigate(Tab.Session.route) {
                        popUpTo(Tab.Session.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                // On every tab except Culling (its own buttons sit there) and the Session once it is capturing.
                val showFab = Tab.entries.any { it.route == route } && route != Tab.Cull.route &&
                    !(route == Tab.Session.route && cameraState is ConnectionState.Connected) &&
                    !(route == Tab.Photos.route && gallerySelecting)

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
                        if (showFab) {
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
                        if (!landscape && onTab) {
                            NavigationBar(containerColor = Tone.Surface) {
                                Tab.entries.forEach { tab ->
                                    NavigationBarItem(
                                        selected = route == tab.route,
                                        onClick = { goTo(tab) },
                                        icon = { Icon(tab.icon, contentDescription = null) },
                                        label = { Text(tab.label) },
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
                        }
                    },
                ) { padding ->
                  Row(Modifier.fillMaxSize().padding(padding)) {
                    // Lying down, the tabs move to a rail on the side so the screen keeps its height.
                    if (landscape && onTab) {
                        NavigationRail(containerColor = Tone.Surface, modifier = Modifier.fillMaxHeight()) {
                            Spacer(Modifier.weight(1f))
                            Tab.entries.forEach { tab ->
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
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    NavHost(nav, startDestination = Tab.Session.route, modifier = Modifier.weight(1f).fillMaxHeight()) {
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
                            )
                        }
                        composable(Tab.Cull.route) { CullScreen(app, onBack = null, onOpenPhoto = { nav.navigate("photo/$it") }) }
                        composable("live") { LiveViewScreen(app, onBack = { nav.popBackStack() }) }
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
                            )
                        }
                        composable(Tab.Presets.route) {
                            PresetsScreen(app = app, onEdit = { nav.navigate("editor/preset/$it") })
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
                            )
                        }
                    }
                  }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
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

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Session("session", "Sessão", Icons.Outlined.CameraAlt),
    Photos("photos", "Fotos", Icons.Outlined.PhotoLibrary),
    Cull("cull", "Culling", Icons.Outlined.Swipe),
    Presets("presets", "Presets", Icons.Outlined.AutoAwesome),
    Settings("settings", "Ajustes", Icons.Outlined.Tune),
}
