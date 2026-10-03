package com.example.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.BrandingWatermark
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Wifi
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.TetherApp
import com.example.core.data.SettingKeys
import com.example.core.data.Settings
import com.example.core.delivery.DeliveryRules
import kotlinx.coroutines.launch

/**
 * Ajustes → Fotografia Express. Everything it needs is here: its own network and password, what a
 * released photo is, the preview mark, price and Pix, and its copies folder. Nothing comes from the
 * Entrega local.
 */
@Composable
fun ExpressSettings(app: TetherApp, s: Settings) {
    val scope = rememberCoroutineScope()
    fun set(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, v: Boolean) = scope.launch { app.settings.set(key, v) }
    val on = s.expressEnabled
    val pickFolder = rememberFolderPicker(app) { scope.launch { app.settings.setOrClear(SettingKeys.expressFolder, it) } }

    SettingsGroup("Fotografia Express", Icons.Outlined.Collections) {
        ToggleRow(
            Icons.Outlined.Collections, "Fotografia Express",
            "Um cliente por vez, sem código: ele entra no Wi-Fi e as fotos já aparecem. Separada da Entrega local.",
            on,
        ) { set(SettingKeys.expressEnabled, it) }
        ToggleRow(
            Icons.Outlined.AutoMode, "Fotos novas entram sozinhas",
            "Cada foto da câmera vai direto para o cliente. Desligado, você manda pelo botão Mandar fotos ou pela galeria.",
            s.expressAuto, enabled = on,
        ) { set(SettingKeys.expressAuto, it) }
        ToggleRow(
            Icons.Outlined.PlayCircle, "Ligar o Wi-Fi ao abrir a aba",
            "A rede do Express sobe sozinha quando você abre a aba Express",
            s.expressAutoStart, enabled = on,
        ) { set(SettingKeys.expressAutoStart, it) }
    }

    SettingsGroup("Rede do Express", Icons.Outlined.Wifi) {
        ChoiceRow(
            "Como o cliente se conecta",
            listOf("direct" to "Wi-Fi Direct (o app cria)", "hotspot" to "Roteador do celular", "wifi" to "Wi-Fi do local"),
            s.expressNetwork,
            enabled = on,
            icon = Icons.Outlined.Wifi,
        ) { scope.launch { app.settings.set(SettingKeys.expressNetwork, it) } }
        InfoText(
            when (s.expressNetwork) {
                "hotspot" -> "Ligue o roteador Wi-Fi do celular com o mesmo nome e senha abaixo, para o QR funcionar."
                "wifi" -> "O celular e o cliente no mesmo Wi-Fi do local. O QR do Wi-Fi não aparece."
                else -> "O app cria a rede sozinho, com nome e senha fixos. A página do Express fica na porta 8090, separada da Entrega local."
            },
        )
        if (s.expressNetwork != "wifi") {
            var name by remember { mutableStateOf(s.expressSsid) }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(32); scope.launch { app.settings.set(SettingKeys.expressSsid, name) } },
                label = { Text("Nome da rede") },
                supportingText = { if (s.expressNetwork == "direct") Text("Aparece como ${DeliveryRules.ssid(name)}") },
                singleLine = true,
                enabled = on,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
            var password by remember { mutableStateOf(s.expressPassword) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(63); scope.launch { app.settings.set(SettingKeys.expressPassword, password) } },
                    label = { Text("Senha") },
                    supportingText = {
                        Text(if (password.isEmpty()) "Criada sozinha na primeira vez" else if (DeliveryRules.passwordOk(password)) "Fica igual sempre: a placa impressa continua valendo" else "Mínimo de 8 caracteres")
                    },
                    singleLine = true,
                    enabled = on,
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                )
                TextButton(onClick = {
                    password = DeliveryRules.newPassword()
                    scope.launch { app.settings.set(SettingKeys.expressPassword, password) }
                }, enabled = on) { Text("Gerar") }
            }
        }
    }

    SettingsGroup("Prévia antes do pagamento", Icons.Outlined.Lock) {
        ToggleRow(
            Icons.Outlined.Lock, "Modo prévia",
            "O cliente vê as fotos com marca d'água e só baixa as que você liberar depois do pagamento.",
            s.expressPreview, enabled = on,
        ) { set(SettingKeys.expressPreview, it) }
        SliderRow(
            "Força da marca na prévia", s.expressMarkOpacity, 20..80, step = 5,
            enabled = on && s.expressPreview, format = { "$it%" }, icon = Icons.Outlined.BrandingWatermark,
        ) { scope.launch { app.settings.set(SettingKeys.expressMarkOpacity, it) } }
        InfoText("A prévia tem 1280 px, o logo de Envio e cliente no centro e \"PRÉVIA\" repetido na foto. A foto sem marca só sai do celular depois de liberada.")
    }

    SettingsGroup("Foto liberada", Icons.Outlined.Image) {
        ChoiceRow(
            "O que o cliente recebe",
            listOf("edited" to "Editada inteira", "light" to "Leve (${if (s.lightShareSize > 0) "${s.lightShareSize} px" else "original"})", "original" to "JPG original"),
            s.expressSize,
            enabled = on,
            icon = Icons.Outlined.Image,
        ) { scope.launch { app.settings.set(SettingKeys.expressSize, it) } }
        ToggleRow(
            Icons.Outlined.BrandingWatermark, "Marca d'água na foto liberada",
            "Desligado (padrão), a foto paga vai limpa. Ligado, leva a marca de Envio e cliente.",
            s.expressWatermark, enabled = on && s.expressSize != "original",
        ) { set(SettingKeys.expressWatermark, it) }
    }

    SettingsGroup("Cliente e pagamento", Icons.Outlined.Payments) {
        var photographer by remember { mutableStateOf(s.expressPhotographer) }
        OutlinedTextField(
            value = photographer,
            onValueChange = { photographer = it.take(40); scope.launch { app.settings.set(SettingKeys.expressPhotographer, photographer) } },
            label = { Text("Seu nome na página e na prévia") },
            singleLine = true,
            enabled = on,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
        var price by remember { mutableStateOf(if (s.expressPriceCents > 0) "%d,%02d".format(s.expressPriceCents / 100, s.expressPriceCents % 100) else "") }
        OutlinedTextField(
            value = price,
            onValueChange = { typed ->
                price = typed.filter { it.isDigit() || it == ',' || it == '.' }.take(9)
                val cents = price.replace('.', ',').split(',').let { parts ->
                    val reais = parts.getOrNull(0)?.toIntOrNull() ?: 0
                    val dec = parts.getOrNull(1)?.padEnd(2, '0')?.take(2)?.toIntOrNull() ?: 0
                    reais * 100 + dec
                }
                scope.launch { app.settings.set(SettingKeys.expressPriceCents, cents) }
            },
            label = { Text("Valor de cada foto (R$)") },
            supportingText = { Text(if (s.expressPriceCents > 0) "Aparece para o cliente, com o total das favoritas" else "Vazio: o valor não aparece") },
            singleLine = true,
            enabled = on,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
        ToggleRow(
            Icons.Outlined.Payments, "Mostrar a chave Pix na página",
            if (s.pixKey.isBlank()) "Cadastre a chave em Ajustes → Mensagens" else "Chave: ${s.pixKey}, com botão de copiar",
            s.expressShowPix, enabled = on && s.pixKey.isNotBlank(),
        ) { set(SettingKeys.expressShowPix, it) }
        ToggleRow(
            Icons.Outlined.Favorite, "Cliente marca favoritas",
            "O coração ajuda a escolher o que comprar e aparece na galeria e no culling",
            s.expressPicks, enabled = on,
        ) { set(SettingKeys.expressPicks, it) }
        ToggleRow(
            Icons.Outlined.Vibration, "Vibrar quando o cliente marca ou baixa",
            null, s.deliveryVibrate, enabled = on,
        ) { set(SettingKeys.deliveryVibrate, it) }
    }

    SettingsGroup("Pasta do Express", Icons.Outlined.Folder) {
        FolderRow(
            "Cópia do que cada cliente recebeu (08 Express)",
            if (s.expressFolder == null) "Sem cópia: as fotos ficam só no cache do app até finalizar" else app.store.describeTree(s.expressFolder) + "/<cliente>",
            true,
            onPick = { pickFolder.launch(null) },
            onReset = s.expressFolder?.let { { scope.launch { app.settings.setOrClear(SettingKeys.expressFolder, null) } } },
        )
    }
}
