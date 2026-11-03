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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.susil.sonora.data.model.Song
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
    var ratio by remember(request) { mutableStateOf(PlaylistPosterRatio.Portrait) }
    val requiredSongs = minOf(if (ratio == PlaylistPosterRatio.Story) 7 else 5, request.songs.size)
    var choosing by remember(request) { mutableStateOf(request.songs.size > 5) }
    var selectedSongs by remember(request) {
        mutableStateOf(if (request.songs.size <= 5) request.songs else emptyList())
    }
    if (choosing) {
        PlaylistSongPicker(
            songs = request.songs,
            selectedSongs = selectedSongs,
            requiredSongs = requiredSongs,
            ratio = ratio,
            onRatioChange = { nextRatio ->
                ratio = nextRatio
                val nextLimit = minOf(if (nextRatio == PlaylistPosterRatio.Story) 7 else 5, request.songs.size)
                selectedSongs = selectedSongs.take(nextLimit)
            },
            onToggle = { song ->
                selectedSongs = if (song in selectedSongs) {
                    selectedSongs - song
                } else if (selectedSongs.size < requiredSongs) {
                    selectedSongs + song
                } else {
                    selectedSongs
                }
            },
            onMove = { from, to ->
                selectedSongs = selectedSongs.toMutableList().apply {
                    add(to, removeAt(from))
                }
            },
            onContinue = {
                selectedSongs = it
                choosing = false
            },
        )
        return
    }
    val posterRequest = request.copy(songs = selectedSongs)
    var poster by remember(posterRequest) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(request) { mutableStateOf(false) }
    var saved by remember(request) { mutableStateOf(false) }
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

    LaunchedEffect(posterRequest, ratio, showBranding, picked, base) {
        poster = null
        failed = false
        saved = false
        poster = runCatching { renderPlaylistPoster(context, posterRequest, ratio, showBranding, palette, base) }
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

@Composable
private fun PlaylistSongPicker(
    songs: List<Song>,
    selectedSongs: List<Song>,
    requiredSongs: Int,
    ratio: PlaylistPosterRatio,
    onRatioChange: (PlaylistPosterRatio) -> Unit,
    onToggle: (Song) -> Unit,
    onMove: (Int, Int) -> Unit,
    onContinue: (List<Song>) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.playlist_poster_choose_songs),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onBackground,
        )
        Text(
            text = stringResource(R.string.playlist_poster_choose_songs_description),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceVariant, RoundedCornerShape(14.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PlaylistPosterRatio.entries.forEach { option ->
                Text(
                    text = stringResource(option.labelRes),
                    color = if (ratio == option) colors.onPrimary else colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (ratio == option) colors.primary
                            else androidx.compose.ui.graphics.Color.Transparent,
                            RoundedCornerShape(10.dp),
                        )
                        .clickable { onRatioChange(option) }
                        .padding(vertical = 10.dp),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.primaryContainer)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    R.string.playlist_poster_selected_count,
                    selectedSongs.size,
                    requiredSongs,
                ),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            if (selectedSongs.size == requiredSongs) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = colors.onPrimaryContainer,
                )
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                // Keep the action row inside the bottom-sheet viewport. The
                // playlist remains fully browsable because this list scrolls.
                .heightIn(min = 180.dp, max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(songs, key = { index, song -> "${song.videoId}-$index" }) { _, song ->
                val selectedIndex = selectedSongs.indexOf(song)
                val selected = selectedIndex >= 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) colors.secondaryContainer else colors.surfaceVariant)
                        .clickable { onToggle(song) }
                        .padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (selected) colors.primary else colors.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Text(
                                text = (selectedIndex + 1).toString(),
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.onPrimary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                    if (selected) {
                        IconButton(
                            onClick = { onMove(selectedIndex, (selectedIndex - 1).coerceAtLeast(0)) },
                            enabled = selectedIndex > 0,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowUpward,
                                contentDescription = stringResource(R.string.playlist_poster_move_up),
                            )
                        }
                        IconButton(
                            onClick = {
                                onMove(
                                    selectedIndex,
                                    (selectedIndex + 1).coerceAtMost(selectedSongs.lastIndex),
                                )
                            },
                            enabled = selectedIndex < selectedSongs.lastIndex,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowDownward,
                                contentDescription = stringResource(R.string.playlist_poster_move_down),
                            )
                        }
                    }
                }
            }
        }
        ShareAction(
            label = stringResource(R.string.playlist_poster_continue),
            icon = Icons.Rounded.Check,
            accent = true,
            enabled = selectedSongs.size == requiredSongs,
            modifier = Modifier.fillMaxWidth(),
        ) {
            onContinue(selectedSongs)
        }
    }
}
