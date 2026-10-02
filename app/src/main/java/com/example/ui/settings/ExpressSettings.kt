package com.example.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.BrandingWatermark
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.example.TetherApp
import com.example.core.data.SettingKeys
import com.example.core.data.Settings
import kotlinx.coroutines.launch

/** Ajustes → Fotografia Express: on/off, preview mark and how photos arrive. The network is the Entrega local's. */
@Composable
fun ExpressSettings(app: TetherApp, s: Settings) {
    val scope = rememberCoroutineScope()
    fun set(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, v: Boolean) = scope.launch { app.settings.set(key, v) }

    SettingsGroup("Fotografia Express", Icons.Outlined.Collections) {
        ToggleRow(
            Icons.Outlined.Collections, "Fotografia Express",
            "Um cliente por vez, sem código: ele entra no Wi-Fi e as fotos já aparecem no portal.",
            s.expressEnabled,
        ) { set(SettingKeys.expressEnabled, it) }
        ToggleRow(
            Icons.Outlined.AutoMode, "Fotos novas entram sozinhas",
            "Cada foto da câmera vai direto para o cliente do Express. Desligado, você escolhe na galeria.",
            s.expressAuto, enabled = s.expressEnabled,
        ) { set(SettingKeys.expressAuto, it) }
        InfoText("Rede, senha, tamanho da foto e a pasta de cópias são os mesmos de Ajustes → Entrega local.")
    }

    SettingsGroup("Prévia antes do pagamento", Icons.Outlined.Lock) {
        ToggleRow(
            Icons.Outlined.Lock, "Modo prévia",
            "O cliente vê as fotos com marca d'água e só baixa as que você liberar depois do pagamento.",
            s.expressPreview, enabled = s.expressEnabled,
        ) { set(SettingKeys.expressPreview, it) }
        SliderRow(
            "Força da marca na prévia", s.expressMarkOpacity, 20..80, step = 5,
            enabled = s.expressEnabled && s.expressPreview, format = { "$it%" }, icon = Icons.Outlined.BrandingWatermark,
        ) { scope.launch { app.settings.set(SettingKeys.expressMarkOpacity, it) } }
        InfoText("A prévia usa o logo de Envio e cliente no centro e \"PRÉVIA\" repetido na foto, em tamanho menor (1280 px). Vale para as fotos preparadas daqui em diante.")
    }
}
