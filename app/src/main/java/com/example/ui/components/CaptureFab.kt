package com.example.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.core.camera.ConnectionState
import com.example.ui.theme.Tone

/**
 * Starts tethered capture from any tab: connects the 50D and opens the Session.
 * Once connected it shrinks to a camera button that brings the Session back.
 */
@Composable
fun CaptureFab(state: ConnectionState, onStart: () -> Unit, onOpenSession: () -> Unit) {
    when (state) {
        is ConnectionState.Connected -> FloatingActionButton(
            onClick = onOpenSession,
            containerColor = Tone.Accent,
            contentColor = Tone.OnAccent,
        ) { Icon(Icons.Outlined.CameraAlt, contentDescription = "Voltar para a captura") }

        ConnectionState.Connecting, ConnectionState.WaitingPermission -> ExtendedFloatingActionButton(
            onClick = onOpenSession,
            containerColor = Tone.SurfaceHigh,
            contentColor = Tone.Text,
            icon = { CircularProgressIndicator(Modifier.size(20.dp), color = Tone.Info, strokeWidth = 2.dp) },
            text = { Text(if (state == ConnectionState.WaitingPermission) "Permita a USB" else "Conectando…") },
        )

        else -> ExtendedFloatingActionButton(
            onClick = onStart,
            containerColor = Tone.Accent,
            contentColor = Tone.OnAccent,
            icon = { Icon(Icons.Outlined.Usb, contentDescription = null) },
            text = { Text(if (state is ConnectionState.Failed) "Tentar de novo" else "Iniciar captura") },
        )
    }
}
