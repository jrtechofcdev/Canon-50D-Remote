package com.example.revela

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.data.Settings
import com.example.ui.components.BrandSymbol
import com.example.ui.theme.Tone

/** A place in the app, as the menu lists it. [detail] is a live line (a count, "no ar"), or a fixed hint. */
data class Place(val route: String, val title: String, val detail: String? = null)

/**
 * The menu. Instead of a bar of tabs, the app's places are listed like the contents page of a photo book:
 * large serif names, one short line each, the open one marked with a bar in the brand colour. The studio
 * (logo and name) heads it, because the app is theirs.
 */
@Composable
fun RevelaDrawer(settings: Settings, places: List<Place>, footer: List<Place>, current: String, onGo: (String) -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxHeight()
            .widthIn(max = 340.dp)
            .fillMaxWidth(0.86f)
            .background(Tone.Surface)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.padding(start = 24.dp, end = 16.dp, top = 24.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (settings.brandLogo != null) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(context).data(java.io.File(settings.brandLogo!!)).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(44.dp),
                )
            } else BrandSymbol(40.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    settings.brandName.ifBlank { stringResource(R.string.app_name) },
                    style = MaterialTheme.typography.headlineSmall, color = Tone.Text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(stringResource(R.string.app_name) + " · " + com.example.BuildConfig.VERSION_NAME, style = MaterialTheme.typography.labelSmall, color = Tone.TextMuted)
            }
        }
        HorizontalDivider(color = Tone.Line)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            places.forEach { PlaceRow(it, it.route == current) { onGo(it.route) } }
        }
        HorizontalDivider(color = Tone.Line)
        Column(Modifier.padding(vertical = 8.dp)) {
            footer.forEach { PlaceRow(it, it.route == current, small = true) { onGo(it.route) } }
        }
    }
}

@Composable
private fun PlaceRow(place: Place, selected: Boolean, small: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (small) 48.dp else 60.dp)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The open place: a short bar in the brand colour on the edge, like a bookmark.
        Box(Modifier.width(4.dp).height(if (small) 22.dp else 30.dp).clip(MaterialTheme.shapes.extraSmall).background(if (selected) Tone.Accent else Tone.Surface))
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(
                place.title,
                style = if (small) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                color = if (selected) Tone.Text else Tone.Text.copy(alpha = 0.86f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            place.detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
