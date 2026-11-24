package com.example.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Church
import androidx.compose.material.icons.outlined.Language
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
import androidx.compose.ui.unit.dp
import com.example.TetherApp
import com.example.core.data.SettingKeys
import com.example.core.data.Settings
import com.example.core.delivery.DeliveryRules
import com.example.core.delivery.network.MdnsResponder
import kotlinx.coroutines.launch

/** Ajustes → Entrega da igreja: the tab, the network the media team joins and the address with a name. */
@Composable
fun ChurchSettings(app: TetherApp, s: Settings) {
    val scope = rememberCoroutineScope()

    SettingsGroup("Entrega da igreja", Icons.Outlined.Church) {
        InfoText(
            "A mídia da igreja abre uma página no celular ou no notebook e vê as fotos chegando enquanto você fotografa. " +
                "Cada pessoa entra com nome e cargo, e você decide quem vê e quem baixa a original, a pronta e o RAW.",
        )
        ToggleRow(Icons.Outlined.Church, "Aba Igreja na barra", "Sem a aba, o painel abre pelas ferramentas da Sessão", s.churchEnabled) {
            scope.launch { app.settings.set(SettingKeys.churchEnabled, it) }
        }
    }

    SettingsGroup("Rede da igreja", Icons.Outlined.Wifi) {
        ChoiceRow(
            "Como a mídia se conecta",
            listOf("wifi" to "Wi-Fi da igreja", "hotspot" to "Roteador do celular", "direct" to "Wi-Fi Direct (o app cria)"),
            s.churchNetwork,
            icon = Icons.Outlined.Wifi,
        ) { scope.launch { app.settings.set(SettingKeys.churchNetwork, it) } }
        InfoText(
            when (s.churchNetwork) {
                "hotspot" -> "Ligue o roteador Wi-Fi do celular com o mesmo nome e senha abaixo, para o QR do Wi-Fi funcionar."
                "direct" -> "O app cria a rede sozinho, com nome e senha fixos. Bom quando a igreja não tem Wi-Fi."
                else -> "O celular e a equipe de mídia no mesmo Wi-Fi da igreja. É o mais rápido para muitos aparelhos e notebooks."
            } + " A página da igreja fica na porta 8100, separada da Entrega local e do Express.",
        )
        if (s.churchNetwork != "wifi") {
            var name by remember { mutableStateOf(s.churchSsid) }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(32); scope.launch { app.settings.set(SettingKeys.churchSsid, name) } },
                label = { Text("Nome da rede") },
                supportingText = { if (s.churchNetwork == "direct") Text("Aparece como ${DeliveryRules.ssid(name)}") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
            var password by remember { mutableStateOf(s.churchPassword) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(63); scope.launch { app.settings.set(SettingKeys.churchPassword, password) } },
                    label = { Text("Senha") },
                    supportingText = { Text(if (password.isEmpty()) "Criada sozinha na primeira vez" else if (DeliveryRules.passwordOk(password)) "Fica igual sempre" else "Mínimo de 8 caracteres") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                )
                TextButton(onClick = {
                    password = DeliveryRules.newPassword()
                    scope.launch { app.settings.set(SettingKeys.churchPassword, password) }
                }) { Text("Gerar") }
            }
        }
    }

    SettingsGroup("Endereço com nome", Icons.Outlined.Language) {
        var domain by remember { mutableStateOf(s.churchDomain) }
        OutlinedTextField(
            value = domain,
            onValueChange = { domain = it.take(30); scope.launch { app.settings.set(SettingKeys.churchDomain, domain) } },
            label = { Text("Nome do endereço") },
            suffix = { Text(".local") },
            supportingText = { Text(if (domain.isBlank()) "Vazio: só o endereço com números" else "Também dá para digitar http://${MdnsResponder.hostName(domain, "igreja")}.local:8100") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
        InfoText("Funciona no iPhone e no computador. Alguns Android só abrem pelo número, por isso o QR continua com ele.")
    }
}
