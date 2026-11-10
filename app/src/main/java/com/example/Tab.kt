package com.example

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.ui.graphics.vector.ImageVector

/** JR C50D Sync's tab bar (Revela has a menu instead); shared so Ajustes can list the tabs to hide. */
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
