package com.example.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.datastore.preferences.core.Preferences
import com.example.TetherApp
import com.example.core.data.SettingKeys
import com.example.core.data.Settings
import com.example.core.payments.PricePlan
import com.example.ui.delivery.QrImage
import com.example.ui.theme.Tone
import kotlinx.coroutines.launch

/**
 * Ajustes → Pagamentos: the Pix (key, receiver, city, for a Pix code with the exact amount), the prices
 * of Fotografia Express (per photo, a package, all photos), the accepted ways to pay and what the
 * client's page shows.
 */
@Composable
fun PaymentSettings(app: TetherApp, s: Settings) {
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }

    SettingsGroup("Pix", Icons.Outlined.QrCode2) {
        var key by remember { mutableStateOf(s.pixKey) }
        OutlinedTextField(
            value = key,
            onValueChange = { key = it.take(80); scope.launch { app.settings.set(SettingKeys.pixKey, key) } },
            label = { Text("Chave Pix") },
            supportingText = { Text("A mesma do campo {pix} das mensagens") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
        ChoiceRow(
            "Tipo da chave",
            listOf("email" to "E-mail", "phone" to "Telefone", "cpf" to "CPF", "cnpj" to "CNPJ", "random" to "Aleatória"),
            s.pixKeyType,
        ) { scope.launch { app.settings.set(SettingKeys.pixKeyType, it) } }
        var name by remember { mutableStateOf(s.pixName) }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(25); scope.launch { app.settings.set(SettingKeys.pixName, name) } },
            label = { Text("Nome de quem recebe") },
            supportingText = { Text("Como aparece no app do banco do cliente (até 25 letras)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
        var city by remember { mutableStateOf(s.pixCity) }
        OutlinedTextField(
            value = city,
            onValueChange = { city = it.take(15); scope.launch { app.settings.set(SettingKeys.pixCity, city) } },
            label = { Text("Cidade") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
        OutlinedButton(onClick = { testing = true }, enabled = s.pixKey.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Testar: QR de R$ 1,00 no meu banco")
        }
        InfoText("O app monta o Pix Copia e Cola com o valor das fotos escolhidas, no próprio celular. Leia o QR de teste no app do seu banco: se aparecer seu nome e R$ 1,00, está certo.")
    }

    SettingsGroup("Valores da Fotografia Express", Icons.Outlined.Sell) {
        MoneyField("Valor de cada foto", s.expressPriceCents, SettingKeys.expressPriceCents, app)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            var n by remember { mutableStateOf(if (s.payPackPhotos > 0) s.payPackPhotos.toString() else "") }
            OutlinedTextField(
                value = n,
                onValueChange = { t -> n = t.filter(Char::isDigit).take(3); scope.launch { app.settings.set(SettingKeys.payPackPhotos, n.toIntOrNull() ?: 0) } },
                label = { Text("Pacote: fotos") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            Column(Modifier.weight(1.4f)) { MoneyField("por", s.payPackCents, SettingKeys.payPackCents, app) }
        }
        MoneyField("Todas as fotos do cliente", s.payAllCents, SettingKeys.payAllCents, app)
        val example = PricePlan.quote(s, 6, 10)
        InfoText(
            PricePlan.table(s)?.let { "O cliente vê: $it. O app sempre cobra o mais barato para ele" + (example?.let { e -> " (ex.: 6 fotos = ${PricePlan.money(e.cents)}, ${e.detail})." } ?: ".") }
                ?: "Sem valores, a página não mostra preço.",
        )
    }

    SettingsGroup("Formas de pagamento", Icons.Outlined.CreditCard) {
        val chosen = s.payMethods.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        PricePlan.methodLabels.forEach { (id, label) ->
            ToggleRow(if (id == "pix") Icons.Outlined.QrCode2 else Icons.Outlined.Payments, label, null, id in chosen) { on ->
                val next = if (on) chosen + id else chosen - id
                scope.launch { app.settings.set(SettingKeys.payMethods, PricePlan.methodLabels.keys.filter { it in next }.joinToString(",")) }
            }
        }
        var note by remember { mutableStateOf(s.payNote) }
        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(120); scope.launch { app.settings.set(SettingKeys.payNote, note) } },
            label = { Text("Observação para o cliente") },
            placeholder = { Text("Ex.: Pagamento no balcão. Aceito parcelar no cartão.") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
    }

    SettingsGroup("Na página do cliente", Icons.Outlined.Visibility) {
        ToggleRow(
            Icons.Outlined.Inventory2, "Mostrar valores e pagamento no Express",
            "Uma linha discreta com os valores e o botão Pagar, que abre o QR do Pix com o total das favoritas",
            s.expressShowPix,
        ) { scope.launch { app.settings.set(SettingKeys.expressShowPix, it) } }
    }

    if (testing) {
        val code = remember(s) { PricePlan.pix(s.copy(payMethods = "pix"), 100) }
        AlertDialog(
            onDismissRequest = { testing = false },
            containerColor = Tone.Surface,
            title = { Text("Pix de teste: R$ 1,00") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    code?.let { QrImage(it, 220.dp, contentDescription = "QR do Pix de teste") }
                    Text("Leia com o app do banco. Não precisa pagar: só confira nome, cidade e valor.", style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
                }
            },
            confirmButton = { TextButton(onClick = { testing = false }) { Text("Fechar") } },
        )
    }
}

/** A money field in reais ("15,00"), stored in cents. Empty = 0. */
@Composable
private fun MoneyField(label: String, cents: Int, key: Preferences.Key<Int>, app: TetherApp) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(if (cents > 0) "%d,%02d".format(cents / 100, cents % 100) else "") }
    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            text = typed.filter { it.isDigit() || it == ',' || it == '.' }.take(9)
            scope.launch { app.settings.set(key, centsOf(text)) }
        },
        label = { Text(label) },
        prefix = { Text("R$ ") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

internal fun centsOf(text: String): Int {
    val parts = text.replace('.', ',').split(',')
    val reais = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val dec = parts.getOrNull(1)?.padEnd(2, '0')?.take(2)?.toIntOrNull() ?: 0
    return reais * 100 + dec
}
