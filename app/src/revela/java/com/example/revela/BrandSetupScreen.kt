package com.example.revela

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.TetherApp
import com.example.brand.BrandStyle
import com.example.core.data.SettingKeys
import com.example.core.data.Settings
import com.example.ui.components.BrandSymbol
import com.example.ui.theme.DisplayStyle
import com.example.ui.theme.Tone
import com.example.ui.theme.luminanceValue
import kotlinx.coroutines.launch

/** Colours offered for the brand; the photographer can also type any hex. Each reads on the darkroom black. */
internal val BrandSwatches = listOf(
    BrandStyle.Safelight,
    Color(0xFFF2B33D), Color(0xFFFF6F61), Color(0xFFF06292), Color(0xFFC77DFF),
    Color(0xFF7C9CFF), Color(0xFF2EC4B6), Color(0xFF7ED957), Color(0xFFD9C3A0), Color(0xFFF2EBE0),
)

/**
 * "Sua marca": the studio's name, colour, logo and language. Shown once on the first launch (with the
 * welcome) and later from the menu. What is set here is what clients see.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrandSetupScreen(app: TetherApp, settings: Settings, firstRun: Boolean, onDone: () -> Unit, onBack: (() -> Unit)?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember(settings.brandName) { mutableStateOf(settings.brandName) }
    var color by remember(settings.brandColor) { mutableStateOf(settings.brandColor.takeIf { it != 0 }?.let { Color(it) } ?: BrandStyle.Safelight) }
    var hex by remember { mutableStateOf("") }
    var logo by remember(settings.brandLogo) { mutableStateOf(settings.brandLogo) }
    var logoVersion by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val logoFailed = stringResource(R.string.logo_failed)
    val needName = stringResource(R.string.brand_need_name)

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val saved = BrandLogo.save(context, uri)
            if (saved == null) error = logoFailed else { logo = saved; logoVersion++ }
        }
    }

    Box(Modifier.fillMaxSize().background(Tone.Background)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                if (onBack != null) {
                    IconButton(onClick = onBack, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = Tone.Text)
                    }
                } else Spacer(Modifier.height(40.dp))

                if (firstRun) {
                    BrandSymbol(56.dp)
                    Spacer(Modifier.height(20.dp))
                    Text(stringResource(R.string.welcome_title), style = DisplayStyle.copy(fontSize = MaterialTheme.typography.displayMedium.fontSize, lineHeight = MaterialTheme.typography.displayMedium.lineHeight), color = Tone.Text)
                    Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodyLarge, color = Tone.TextMuted, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(32.dp))
                    Text(stringResource(R.string.brand_title), style = MaterialTheme.typography.headlineMedium, color = Tone.Text)
                } else {
                    Text(stringResource(R.string.brand_title), style = DisplayStyle, color = Tone.Text)
                }
                Text(stringResource(R.string.brand_intro), style = MaterialTheme.typography.bodyMedium, color = Tone.TextMuted, modifier = Modifier.padding(top = 6.dp))

                // Name
                Section(stringResource(R.string.brand_name))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40); error = null },
                    placeholder = { Text(stringResource(R.string.brand_name_hint), color = Tone.TextMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    colors = fieldColors(),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Colour
                Section(stringResource(R.string.brand_color), stringResource(R.string.brand_color_hint))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BrandSwatches.forEach { swatch -> Swatch(swatch, swatch.toArgb() == color.toArgb()) { color = swatch; hex = "" } }
                }
                OutlinedTextField(
                    value = hex,
                    onValueChange = { typed ->
                        hex = typed.take(7)
                        parseHex(typed)?.let { color = it }
                    },
                    label = { Text(stringResource(R.string.brand_color_custom)) },
                    placeholder = { Text("#FF7A3D", color = Tone.TextMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                    colors = fieldColors(),
                    modifier = Modifier.padding(top = 16.dp).widthIn(max = 240.dp),
                )

                // Logo
                Section(stringResource(R.string.brand_logo), stringResource(R.string.brand_logo_none))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        Modifier.size(88.dp).clip(MaterialTheme.shapes.medium).background(Tone.Surface)
                            .border(1.dp, Tone.Line, MaterialTheme.shapes.medium),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (logo != null) {
                            AsyncImage(
                                model = coil.request.ImageRequest.Builder(context).data(java.io.File(logo!!)).memoryCacheKey("logo$logoVersion").diskCachePolicy(coil.request.CachePolicy.DISABLED).build(),
                                contentDescription = stringResource(R.string.brand_logo),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(10.dp),
                            )
                        } else BrandSymbol(36.dp, tint = Tone.Line)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(
                            onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            border = BorderStroke(1.dp, Tone.Line),
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text(stringResource(if (logo == null) R.string.brand_logo_pick else R.string.brand_logo_change), color = Tone.Text) }
                        if (logo != null) {
                            TextButton(onClick = { scope.launch { BrandLogo.remove(context); logo = null } }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.brand_logo_remove), color = Tone.TextMuted)
                            }
                        }
                    }
                }

                // What a client sees, with the choices above.
                Section(stringResource(R.string.brand_preview))
                ClientPreview(name.ifBlank { stringResource(R.string.brand_name_hint).substringAfter(" ").trim() }, color, logo, logoVersion)

                if (Build.VERSION.SDK_INT >= 33) LanguagePicker()

                error?.let { Text(it, color = Tone.Error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 16.dp)) }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        val studio = name.trim()
                        if (studio.length < 2) { error = needName; return@Button }
                        scope.launch {
                            val s = app.settings
                            s.set(SettingKeys.brandName, studio)
                            s.set(SettingKeys.deliveryPhotographer, studio)
                            s.set(SettingKeys.expressPhotographer, studio)
                            s.set(SettingKeys.brandColor, color.toArgb())
                            s.setOrClear(SettingKeys.brandLogo, logo)
                            s.set(SettingKeys.brandSetupDone, true)
                            onDone()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = onColor(color)),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text(stringResource(if (firstRun) R.string.brand_start else R.string.brand_save), style = MaterialTheme.typography.titleMedium) }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun Section(title: String, hint: String? = null) {
    Spacer(Modifier.height(28.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, color = Tone.Text)
    hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted, modifier = Modifier.padding(top = 2.dp)) }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .border(if (selected) 2.dp else 1.dp, if (selected) Tone.Text else Tone.Line, CircleShape)
            .padding(5.dp)
            .clip(CircleShape)
            .background(color)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "#" + Integer.toHexString(color.toArgb()).takeLast(6).uppercase() },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = onColor(color), modifier = Modifier.size(20.dp))
    }
}

/** How the client page looks with this brand: their name, the logo and the main button in the colour. */
@Composable
private fun ClientPreview(studio: String, color: Color, logo: String?, version: Int) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Color(0xFF181614)).padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (logo != null) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(context).data(java.io.File(logo)).memoryCacheKey("logo$version").build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(28.dp).widthIn(max = 120.dp),
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(studio, style = MaterialTheme.typography.titleMedium, color = Color(0xFFF2EBE0), maxLines = 1)
        }
        Text(stringResource(R.string.brand_preview_hello), style = MaterialTheme.typography.headlineMedium, color = Color(0xFFF2EBE0), modifier = Modifier.padding(top = 20.dp))
        Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(96.dp).clip(MaterialTheme.shapes.extraSmall).background(Color(0xFF3A3532)))
        Box(
            Modifier.padding(top = 12.dp).fillMaxWidth().heightIn(min = 48.dp).clip(MaterialTheme.shapes.small).background(color),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(R.string.brand_preview_button), style = MaterialTheme.typography.titleSmall, color = onColor(color)) }
    }
}

/** Android 13+: the app's own language, without changing the phone's. */
@androidx.annotation.RequiresApi(33)
@Composable
private fun LanguagePicker() {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(LocaleManager::class.java) }
    var current by remember { mutableStateOf(manager?.applicationLocales?.toLanguageTags().orEmpty()) }
    Section(stringResource(R.string.brand_language))
    val options = listOf("" to stringResource(R.string.lang_system), "pt-BR" to "Português", "en" to "English")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (tag, label) ->
            val on = current == tag
            Box(
                Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(if (on) Tone.SurfaceHigh else Tone.Surface)
                    .border(1.dp, if (on) Tone.Accent else Tone.Line, MaterialTheme.shapes.small)
                    .selectable(selected = on, role = Role.RadioButton) {
                        current = tag
                        manager?.applicationLocales = if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
                    }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) Tone.Text else Tone.TextMuted) }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Tone.Accent,
    unfocusedBorderColor = Tone.Line,
    focusedTextColor = Tone.Text,
    unfocusedTextColor = Tone.Text,
    cursorColor = Tone.Accent,
    focusedLabelColor = Tone.Accent,
    unfocusedLabelColor = Tone.TextMuted,
)

internal fun onColor(c: Color) = if (c.luminanceValue() > 0.36f) Color(0xFF14110E) else Color.White

internal fun parseHex(typed: String): Color? {
    val h = typed.trim().removePrefix("#")
    if (!Regex("[0-9a-fA-F]{6}").matches(h)) return null
    return Color(0xFF000000 or h.toLong(16))
}
