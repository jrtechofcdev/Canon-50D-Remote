package com.example.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Church
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.example.TetherApp
import com.example.core.data.SettingKeys
import com.example.core.data.Settings
import com.example.core.render.Enhance
import kotlinx.coroutines.launch

/**
 * Ajustes → Melhorar: less grain and more definition, without touching colour. The strength used when it
 * is turned on, and the areas where it applies by itself; with none, it is turned on by hand per photo.
 */
@Composable
fun EnhanceSettings(app: TetherApp, s: Settings) {
    val scope = rememberCoroutineScope()
    fun set(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, on: Boolean) = scope.launch { app.settings.set(key, on) }

    SettingsGroup("Intensidade", Icons.Outlined.AutoFixHigh) {
        InfoText(
            "Tira o granulado do ISO alto e devolve a definição das bordas, sem mexer na cor. Roda no celular, sem internet. " +
                "O arquivo original (JPG e RAW) nunca é alterado: o resultado vai na cópia editada e nas cópias que saem do app.",
        )
        ChoiceRow(
            "Intensidade",
            listOf(Enhance.AUTO, Enhance.LIGHT, Enhance.NORMAL, Enhance.STRONG).map { it to Enhance.label(it) },
            s.enhanceLevel,
        ) { scope.launch { app.settings.set(SettingKeys.enhanceLevel, it) } }
    }

    SettingsGroup("Aplicar sozinho em", Icons.Outlined.NewReleases) {
        InfoText("Sem nenhuma área ligada, o Melhorar é só manual: em cada foto (Mais ações → Melhorar) ou em lote na galeria.")
        ToggleRow(Icons.Outlined.NewReleases, "Fotos novas", "Cada foto que chega da câmera já vem melhorada na cópia editada", s.enhanceOnNew) { set(SettingKeys.enhanceOnNew, it) }
        ToggleRow(Icons.Outlined.WifiTethering, "Entrega local e Express", "As cópias que o cliente baixa saem melhoradas (o \"original\" continua puro)", s.enhanceOnDelivery) { set(SettingKeys.enhanceOnDelivery, it) }
        ToggleRow(Icons.Outlined.Church, "Versão pronta da igreja", "A versão pronta que a mídia baixa sai melhorada (o original continua puro)", s.enhanceOnChurch) { set(SettingKeys.enhanceOnChurch, it) }
        ToggleRow(Icons.AutoMirrored.Outlined.Send, "Envio e WhatsApp", "A versão leve enviada pelo app sai melhorada", s.enhanceOnShare) { set(SettingKeys.enhanceOnShare, it) }
    }
}
