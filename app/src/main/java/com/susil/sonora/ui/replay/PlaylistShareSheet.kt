package com.susil.sonora.ui.replay

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.susil.sonora.R
import com.susil.sonora.ui.haptics.Haptic
import com.susil.sonora.ui.haptics.rememberHaptics
import com.susil.sonora.ui.theme.rememberArtworkPalette
import kotlinx.coroutines.launch

@Composable
internal fun PlaylistShareSheet(
    request: PlaylistShareRequest,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var poster by remember(request) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(request) { mutableStateOf(false) }
    var saved by remember(request) { mutableStateOf(false) }
    var ratio by remember(request) { mutableStateOf(PlaylistPosterRatio.Portrait) }
    var showBranding by remember(request) { mutableStateOf(true) }

    // Same extraction the playlist screen paints with, so the card matches the page.
    val art = rememberArtworkPalette(imageUrl = request.artworkUrl, keyColors = null)
    // `background` is deliberately theme-darkened for in-app surfaces. The
    // poster should preserve the cover's vivid extracted color instead of
    // exporting a muddy gradient.
    val base = art.accent.toArgb()
    val swatches = remember(base) { PlaylistPosterPalette.swatches(base) }
    var picked by remember(request) { mutableIntStateOf(1) }
    val palette = swatches[picked.coerceIn(0, swatches.lastIndex)]

    LaunchedEffect(request, ratio, showBranding, picked, base) {
        poster = null
        failed = false
        saved = false
        poster = runCatching { renderPlaylistPoster(context, request, ratio, showBranding, palette, base) }
            .onFailure { failed = true }
            .getOrNull()
    }

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.playlist_share_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.playlist_share_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PlaylistPosterRatio.entries.forEach { option ->
                Text(
                    text = stringResource(option.labelRes),
                    color = if (ratio == option) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (ratio == option) MaterialTheme.colorScheme.primary
                            else androidx.compose.ui.graphics.Color.Transparent,
                            RoundedCornerShape(10.dp),
                        )
                        .clickable { ratio = option }
                        .padding(vertical = 10.dp),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.playlist_share_border_color),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            swatches.forEachIndexed { index, option ->
                val selected = picked == index
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Color(option.accent))
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.onBackground
                            else MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        )
                        .semantics {
                            contentDescription = option.label
                            this.selected = selected
                        }
                        .clickable { picked = index },
                )
            }
        }

        ShareBrandingControl(
            showBranding = showBranding,
            onShowBrandingChange = { showBranding = it },
        )

        Box(
            Modifier
                .fillMaxWidth((0.72f * ratio.ratio).coerceIn(0.36f, 0.92f))
                .align(Alignment.CenterHorizontally)
                .aspectRatio(ratio.ratio)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val image = poster
            when {
                image != null -> Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = stringResource(R.string.your_playlist_card),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(),
                )
                failed -> Text(
                    text = stringResource(R.string.couldnt_draw_picture),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                else -> CircularProgressIndicator()
            }
        }

        val ready = poster != null
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShareAction(
                label = stringResource(if (saved) R.string.saved else R.string.save),
                icon = Icons.Rounded.Download,
                accent = false,
                enabled = ready && !saved,
                modifier = Modifier.weight(1f),
                saved = saved,
                savedLabel = stringResource(R.string.saved_bang),
            ) {
                val image = poster ?: return@ShareAction
                haptics.play(Haptic.Tap)
                scope.launch {
                    saved = saveToGallery(context, image, request.title, "sonora-playlist")
                }
            }
            ShareAction(
                label = stringResource(R.string.share),
                icon = Icons.Rounded.IosShare,
                accent = true,
                enabled = ready,
                modifier = Modifier.weight(1f),
            ) {
                val image = poster ?: return@ShareAction
                haptics.play(Haptic.Tap)
                scope.launch {
                    val uri = cacheForSharing(
                        context,
                        image,
                        "playlist-${System.currentTimeMillis()}.png",
                    )
                    if (uri == null) {
                        failed = true
                    } else {
                        context.startActivity(
                            Intent.createChooser(
                                sendIntent(uri),
                                context.getString(R.string.share_playlist_image),
                            ),
                        )
                        onDismiss()
                    }
                }
            }
        }
    }
}
