package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.SdCardAlert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.core.camera.CameraStatus
import com.example.core.data.Settings
import com.example.core.ptp.EosProp
import com.example.core.ptp.EosValues
import com.example.ui.theme.Tone

/** A problem with the camera that stays on screen while it lasts. */
data class CameraWarning(val icon: ImageVector, val text: String)

/**
 * Low battery and an almost full card, for as long as they last. The notification only comes once;
 * these stay where the photographer is looking (Sessão and live view) until the battery or card changes.
 */
fun cameraWarnings(status: CameraStatus, settings: Settings): List<CameraWarning> = buildList {
    status.value(EosProp.BATTERY_POWER)?.takeIf { EosValues.batteryIsLow(it) }?.let {
        add(CameraWarning(Icons.Outlined.BatteryAlert, "Bateria ${EosValues.batteryLabel(it)}"))
    }
    status.shotsLeft?.takeIf { settings.cardLowWarning && it < settings.cardLowShots }?.let {
        add(CameraWarning(Icons.Outlined.SdCardAlert, if (it == 1) "Cartão: 1 disparo" else "Cartão: $it disparos"))
    }
}

/** The warnings as small red chips; they slide in and out as the problem starts and ends. */
@Composable
fun CameraWarningChips(warnings: List<CameraWarning>, modifier: Modifier = Modifier, onDark: Boolean = false) {
    AnimatedVisibility(
        warnings.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            warnings.forEach { w ->
                Row(
                    Modifier.clip(CircleShape)
                        .background(if (onDark) Color.Black.copy(alpha = 0.6f) else Tone.Error.copy(alpha = 0.14f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(w.icon, contentDescription = null, tint = Tone.Error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(w.text, style = MaterialTheme.typography.labelMedium, color = if (onDark) Color.White else Tone.Text)
                }
            }
        }
    }
}
