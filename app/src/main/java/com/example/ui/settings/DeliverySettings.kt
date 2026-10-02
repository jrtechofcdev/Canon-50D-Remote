package com.example.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BrandingWatermark
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.TetherApp
import com.example.core.data.SettingKeys
import com.example.core.data.Settings
import com.example.core.delivery.DeliveryRules
import kotlinx.coroutines.launch

/** Ajustes → Entrega local: the network, what the client gets, how photos arrive and how long access lasts. */
@Composable
fun DeliverySettings(app: TetherApp, s: Settings) {
    val scope = rememberCoroutineScope()
    fun set(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, v: Boolean) = scope.launch { app.settings.set(key, v) }
    val pickFolder = rememberFolderPicker(app) { scope.launch { app.settings.setOrClear(SettingKeys.deliveryFolder, it) } }

    SettingsGroup("Entrega local", Icons.Outlined.WifiTethering) {
        ToggleRow(
            Icons.Outlined.WifiTethering, "Entrega local",
            "O cliente baixa as fotos pelo Wi-Fi do seu celular. Desligada, nada roda e nenhuma permissão é pedida.",
            s.deliveryEnabled,
        ) { set(SettingKeys.deliveryEnabled, it) }
        InfoText("As mudanças de rede valem na próxima vez que a entrega for iniciada.")
    }

    SettingsGroup("Rede", Icons.Outlined.Wifi) {
        ChoiceRow(
            "Como o cliente se conecta",
            listOf("direct" to "Wi-Fi Direct (o app cria)", "hotspot" to "Roteador do celular", "wifi" to "Wi-Fi do local"),
            s.deliveryNetwork,
            enabled = s.deliveryEnabled,
            icon = Icons.Outlined.Wifi,
        ) { scope.launch { app.settings.set(SettingKeys.deliveryNetwork, it) } }
        InfoText(
            when (s.deliveryNetwork) {
                "hotspot" -> "Ligue o roteador Wi-Fi do celular nas configurações do Android com o mesmo nome e senha abaixo, para o QR funcionar."
                "wifi" -> "O celular e os clientes no mesmo Wi-Fi do local. O QR do Wi-Fi não aparece: a rede é do local."
                else -> "O app cria a rede sozinho, com nome e senha fixos, e o celular fica em 192.168.49.1. Android 10 ou mais novo."
            },
        )
        if (s.deliveryNetwork != "wifi") {
            var name by remember { mutableStateOf(s.deliverySsid) }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(32); scope.launch { app.settings.set(SettingKeys.deliverySsid, name) } },
                label = { Text("Nome da rede") },
                supportingText = { if (s.deliveryNetwork == "direct") Text("Aparece como ${DeliveryRules.ssid(name)}") },
                singleLine = true,
                enabled = s.deliveryEnabled,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
            var password by remember { mutableStateOf(s.deliveryPassword) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(63); scope.launch { app.settings.set(SettingKeys.deliveryPassword, password) } },
                    label = { Text("Senha") },
                    supportingText = {
                        Text(if (password.isEmpty()) "Criada sozinha na primeira entrega" else if (DeliveryRules.passwordOk(password)) "Fica igual sempre: a placa impressa continua valendo" else "Mínimo de 8 caracteres")
                    },
                    singleLine = true,
                    enabled = s.deliveryEnabled,
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                )
                TextButton(onClick = {
                    password = DeliveryRules.newPassword()
                    scope.launch { app.settings.set(SettingKeys.deliveryPassword, password) }
                }, enabled = s.deliveryEnabled) { Text("Gerar") }
            }
        }
    }

    SettingsGroup("O que o cliente recebe", Icons.Outlined.Image) {
        ChoiceRow(
            "Tamanho da foto",
            listOf("light" to "Leve (${if (s.lightShareSize > 0) "${s.lightShareSize} px" else "original"})", "edited" to "Editada inteira", "original" to "JPG original"),
            s.deliverySize,
            enabled = s.deliveryEnabled,
            icon = Icons.Outlined.Image,
        ) { scope.launch { app.settings.set(SettingKeys.deliverySize, it) } }
        ToggleRow(
            Icons.Outlined.BrandingWatermark, "Marca d'água", "A mesma de Envio e cliente. Não vale para o JPG original.",
            s.deliveryWatermark, enabled = s.deliveryEnabled && s.deliverySize != "original",
        ) { set(SettingKeys.deliveryWatermark, it) }
        ToggleRow(
            Icons.Outlined.Favorite, "Cliente marca favoritas", "O coração no portal chega como escolha do cliente na galeria e no culling",
            s.deliveryPicks, enabled = s.deliveryEnabled,
        ) { set(SettingKeys.deliveryPicks, it) }
        var photographer by remember { mutableStateOf(s.deliveryPhotographer) }
        OutlinedTextField(
            value = photographer,
            onValueChange = { photographer = it.take(40); scope.launch { app.settings.set(SettingKeys.deliveryPhotographer, photographer) } },
            label = { Text("Seu nome no portal e na placa") },
            singleLine = true,
            enabled = s.deliveryEnabled,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
    }

    SettingsGroup("Como as fotos chegam", Icons.Outlined.Bolt) {
        ChoiceRow(
            "Modo",
            listOf("manual" to "Manual (você escolhe)", "auto" to "Automático (cada foto nova)"),
            s.deliveryMode,
            enabled = s.deliveryEnabled,
            icon = Icons.Outlined.Bolt,
        ) { scope.launch { app.settings.set(SettingKeys.deliveryMode, it) } }
        ToggleRow(
            Icons.Outlined.Collections, "Galeria do evento",
            "No automático, as fotos vão para uma galeria aberta a todos com o código do evento. Desligado, cada cliente vê só as dele.",
            s.deliveryEventGallery, enabled = s.deliveryEnabled && s.deliveryMode == "auto",
        ) { set(SettingKeys.deliveryEventGallery, it) }
    }

    SettingsGroup("Acesso e limpeza", Icons.Outlined.Lock) {
        ChoiceRow(
            "Cada código vale por",
            listOf(6 to "6 horas", 24 to "24 horas", 72 to "3 dias", 0 to "Até encerrar"),
            s.deliveryValidityHours,
            enabled = s.deliveryEnabled,
            icon = Icons.Outlined.AccessTime,
        ) { scope.launch { app.settings.set(SettingKeys.deliveryValidityHours, it) } }
        ToggleRow(
            Icons.Outlined.CleaningServices, "Apagar as cópias ao parar",
            "Libera espaço. O histórico fica, e as fotos são preparadas de novo na próxima entrega.",
            s.deliveryClearOnStop, enabled = s.deliveryEnabled,
        ) { set(SettingKeys.deliveryClearOnStop, it) }
    }

    SettingsGroup("Pasta das entregas", Icons.Outlined.Folder) {
        FolderRow(
            "Cópia do que foi entregue (07 Entregas)",
            if (s.deliveryFolder == null) "Sem cópia: as fotos ficam só no cache do app" else app.store.describeTree(s.deliveryFolder) + "/<cliente>",
            true,
            onPick = { pickFolder.launch(null) },
            onReset = s.deliveryFolder?.let { { scope.launch { app.settings.setOrClear(SettingKeys.deliveryFolder, null) } } },
        )
    }
}
