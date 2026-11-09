package com.susil.sonora.ui.replay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.susil.sonora.R
import com.susil.sonora.data.model.DetailPage
import com.susil.sonora.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class PlaylistCardMode(
    val ratio: Float,
    val songs: Int,
    val labelRes: Int,
    val layoutRes: Int,
) {
    PORTRAIT(4f / 5f, 5, R.string.playlist_share_portrait, R.string.playlist_share_portrait_layout),
    STORY(9f / 16f, 7, R.string.playlist_share_story, R.string.playlist_share_story_layout),
}

@Composable
fun PlaylistShareSheet(
    page: DetailPage,
    songs: List<Song>,
    accountName: String = "",
    accountAvatarUrl: String? = null,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(PlaylistCardMode.PORTRAIT) }
    val requiredSongs = minOf(mode.songs, songs.size)
    var choosing by remember(page, songs) { mutableStateOf(songs.size > 5) }
    var selectedSongs by remember(page, songs) {
        mutableStateOf(if (songs.size <= 5) songs else emptyList())
    }

    if (choosing) {
        PlaylistSongPicker(
            songs = songs,
            selectedSongs = selectedSongs,
            requiredSongs = requiredSongs,
            mode = mode,
            onModeChange = { nextMode ->
                mode = nextMode
                selectedSongs = selectedSongs.take(minOf(nextMode.songs, songs.size))
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
            onSelectAll = { selectedSongs = songs.take(requiredSongs) },
            onClearSelection = { selectedSongs = emptyList() },
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

    var poster by remember(page, selectedSongs, mode) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(page, selectedSongs, mode) {
        poster = withContext(Dispatchers.IO) {
            renderPlaylistCard(context, page, selectedSongs, mode, accountName, accountAvatarUrl)
        }
    }

    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.share_playlist_card), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.playlist_share_preview_description, stringResource(mode.labelRes), selectedSongs.size),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        poster?.let { image ->
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = page.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(mode.ratio)
                    .clip(RoundedCornerShape(18.dp)),
            )
        } ?: CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlaylistModeButton(PlaylistCardMode.PORTRAIT, mode == PlaylistCardMode.PORTRAIT) {
                mode = PlaylistCardMode.PORTRAIT
                selectedSongs = selectedSongs.take(minOf(mode.songs, songs.size))
            }
            PlaylistModeButton(PlaylistCardMode.STORY, mode == PlaylistCardMode.STORY) {
                mode = PlaylistCardMode.STORY
                selectedSongs = selectedSongs.take(minOf(mode.songs, songs.size))
            }
        }
        androidx.compose.material3.TextButton(onClick = { choosing = true }) {
            Text(stringResource(R.string.playlist_share_choose_songs))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ShareAction(
                label = stringResource(R.string.save),
                accessibilityLabel = stringResource(R.string.playlist_share_save_poster),
                icon = Icons.Rounded.Save,
                accent = false,
                enabled = poster != null,
                modifier = Modifier.weight(1f),
            ) {
                poster?.let { image ->
                    scope.launch { saveToGallery(context, image, page.title, "sonora-playlist") }
                }
            }
            ShareAction(
                label = stringResource(R.string.share),
                accessibilityLabel = stringResource(R.string.playlist_share_share_poster),
                icon = Icons.Rounded.IosShare,
                accent = true,
                enabled = poster != null,
                modifier = Modifier.weight(1f),
            ) {
                poster?.let { image ->
                    scope.launch {
                        cacheForSharing(context, image, "playlist-share.png")?.let { uri ->
                            context.startActivity(
                                android.content.Intent.createChooser(
                                    sendIntent(uri),
                                    context.getString(R.string.playlist_share_chooser_title),
                                ),
                            )
                            onDismiss()
                        }
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
    mode: PlaylistCardMode,
    onModeChange: (PlaylistCardMode) -> Unit,
    onToggle: (Song) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onContinue: (List<Song>) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val modeName = stringResource(mode.layoutRes)
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.playlist_share_choose_songs), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.playlist_share_select_up_to, requiredSongs, modeName),
            color = colors.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth().background(colors.surfaceVariant, RoundedCornerShape(14.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PlaylistCardMode.entries.forEach { option ->
                Text(
                    stringResource(option.labelRes),
                    color = if (mode == option) colors.onPrimary else colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (mode == option) colors.primary else androidx.compose.ui.graphics.Color.Transparent,
                            RoundedCornerShape(10.dp),
                        )
                        .selectable(
                            selected = mode == option,
                            role = Role.RadioButton,
                            onClick = { onModeChange(option) },
                        )
                        .padding(vertical = 10.dp),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.primaryContainer).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.playlist_share_selected_count, selectedSongs.size, requiredSongs),
                color = colors.onPrimaryContainer,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (selectedSongs.size == requiredSongs) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onPrimaryContainer)
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.material3.TextButton(
                onClick = onSelectAll,
                enabled = selectedSongs.size < requiredSongs,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.playlist_share_select_all))
            }
            androidx.compose.material3.TextButton(
                onClick = onClearSelection,
                enabled = selectedSongs.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.playlist_share_clear_selection))
            }
        }
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(songs, key = { index, song -> "${song.videoId}-$index" }) { _, song ->
                val selectedIndex = selectedSongs.indexOf(song)
                val selected = selectedIndex >= 0
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) colors.secondaryContainer else colors.surfaceVariant)
                        .toggleable(
                            value = selected,
                            role = Role.Checkbox,
                            onValueChange = { onToggle(song) },
                        )
                        .semantics {
                            stateDescription = if (selected) {
                                context.getString(
                                    R.string.playlist_share_song_selected_position,
                                    selectedIndex + 1,
                                    requiredSongs,
                                )
                            } else {
                                context.getString(R.string.playlist_share_not_selected)
                            }
                        }
                        .padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(38.dp).clip(CircleShape)
                            .background(if (selected) colors.primary else colors.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Text("${selectedIndex + 1}", color = colors.onPrimary)
                        } else {
                            Icon(Icons.Rounded.Add, contentDescription = null, tint = colors.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.onSurface)
                        Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.onSurfaceVariant)
                    }
                    if (selected) {
                        IconButton(
                            onClick = { onMove(selectedIndex, (selectedIndex - 1).coerceAtLeast(0)) },
                            enabled = selectedIndex > 0,
                        ) {
                            Icon(Icons.Rounded.ArrowUpward, contentDescription = stringResource(R.string.playlist_share_move_up))
                        }
                        IconButton(
                            onClick = {
                                onMove(selectedIndex, (selectedIndex + 1).coerceAtMost(selectedSongs.lastIndex))
                            },
                            enabled = selectedIndex < selectedSongs.lastIndex,
                        ) {
                            Icon(Icons.Rounded.ArrowDownward, contentDescription = stringResource(R.string.playlist_share_move_down))
                        }
                    }
                }
            }
        }
        ShareAction(
            label = stringResource(R.string.playlist_share_continue),
            icon = Icons.Rounded.Check,
            accent = true,
            enabled = selectedSongs.size == requiredSongs,
            modifier = Modifier.fillMaxWidth(),
        ) {
            onContinue(selectedSongs)
        }
    }
}

@Composable
private fun PlaylistModeButton(mode: PlaylistCardMode, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) {
        Text(
            stringResource(mode.labelRes),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

private suspend fun renderPlaylistCard(
    context: Context,
    page: DetailPage,
    songs: List<Song>,
    mode: PlaylistCardMode,
    accountName: String,
    accountAvatarUrl: String?,
): Bitmap = withContext(Dispatchers.Default) {
    val width = 1080
    val height = if (mode == PlaylistCardMode.STORY) 1920 else 1350
    val cover = page.thumbnailUrl?.let { loadBitmap(context, it) }
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawBackdrop(canvas, cover, 0f, height.toFloat())
    val type = Fonts(context)
    val white = Color.WHITE
    val margin = 70f
    val title = type.heading(if (mode == PlaylistCardMode.STORY) 72f else 58f, white)
    val headerTop = if (mode == PlaylistCardMode.STORY) 150f else 120f
    val titleLineHeight = title.textSize * 1.12f
    val titleLines = wrapText(page.title, title, width - margin * 2, 2)
    titleLines.forEachIndexed { line, value ->
        canvas.drawText(value, margin, headerTop + line * titleLineHeight, title)
    }
    val sub = type.body(36f, 0xCCFFFFFF.toInt())
    val subLineHeight = sub.textSize * 1.25f
    val authorTop = headerTop + titleLines.size * titleLineHeight + 28f
    val displayName = accountName.trim().ifBlank {
        context.getString(R.string.playlist_share_playlist_fallback)
    }
    drawPosterAvatar(
        canvas = canvas,
        bitmap = accountAvatarUrl?.let { loadBitmap(context, it) },
        name = displayName,
        centerX = margin + 28f,
        centerY = authorTop - 14f,
        radius = 28f,
        type = type,
    )
    // Keep the identity order unambiguous: avatar on the left, name on the right.
    val nameX = margin + 74f
    val namePaint = type.body(36f, Color.WHITE)
    canvas.drawText(displayName, nameX, authorTop, namePaint)
    val authorLines = wrapText(page.headerLinesForShare(context, songs.size), sub, width - margin * 2, 2)
    val metadataTop = authorTop + 38f
    authorLines.forEachIndexed { line, value ->
        canvas.drawText(value, margin, metadataTop + line * subLineHeight, sub)
    }
    val description = page.description.orEmpty().replace('\n', ' ')
    val desc = type.body(30f, 0xB3FFFFFF.toInt())
    val descLines = wrapText(description, desc, width - margin * 2, 3)
    val descTop = metadataTop + authorLines.size * subLineHeight + 18f
    descLines.forEachIndexed { line, value ->
        canvas.drawText(value, margin, descTop + line * 38f, desc)
    }
    val previewSongs = songs.take(mode.songs)
    val defaultStartY = if (mode == PlaylistCardMode.STORY) 620f else 500f
    val headerBottom = if (descLines.isEmpty()) {
        metadataTop + authorLines.size * subLineHeight
    } else {
        descTop + descLines.size * 38f
    }
    val startY = maxOf(defaultStartY, headerBottom + 86f)
    val bottomY = height - if (mode == PlaylistCardMode.STORY) 140f else 110f
    val rowGap = if (previewSongs.isEmpty()) 0f
    else ((bottomY - startY) / previewSongs.size).coerceAtLeast(138f)
    previewSongs.forEachIndexed { index, song ->
        val y = startY + index * rowGap
        val art = if (mode == PlaylistCardMode.STORY) 110f else 96f
        drawArtwork(
            canvas,
            song.thumbnailUrl?.let { loadBitmap(context, it) },
            song.title,
            margin,
            y - art + 12f,
            art,
            false,
        )
        val titlePaint = type.body(if (mode == PlaylistCardMode.STORY) 38f else 34f, white)
        val textX = margin + art + 28f
        val room = width - margin - textX
        val songTitleLines = wrapText(song.title, titlePaint, room, 2)
        val songTitleLineHeight = titlePaint.textSize * 1.08f
        songTitleLines.forEachIndexed { line, value ->
            canvas.drawText(value, textX, y + line * songTitleLineHeight, titlePaint)
        }
        val artist = type.body(if (mode == PlaylistCardMode.STORY) 28f else 25f, 0xB3FFFFFF.toInt())
        val artistTop = y + songTitleLines.size * songTitleLineHeight + 4f
        val artistLineHeight = artist.textSize * 1.12f
        wrapText(song.artist, artist, room, 2).forEachIndexed { line, value ->
            canvas.drawText(value, textX, artistTop + line * artistLineHeight, artist)
        }
    }
    val credit = type.body(if (mode == PlaylistCardMode.STORY) 34f else 31f, 0xA6FFFFFF.toInt()).apply {
        textAlign = android.graphics.Paint.Align.CENTER
    }
    canvas.drawText(context.getString(R.string.playlist_share_shared_from), width / 2f, height - 44f, credit)
    bitmap
}

private fun drawPosterAvatar(
    canvas: Canvas,
    bitmap: Bitmap?,
    name: String,
    centerX: Float,
    centerY: Float,
    radius: Float,
    type: Fonts,
) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.save()
    canvas.clipRect(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
    canvas.clipPath(android.graphics.Path().apply {
        addCircle(centerX, centerY, radius, android.graphics.Path.Direction.CW)
    })
    if (bitmap != null) {
        val scale = maxOf(radius * 2f / bitmap.width, radius * 2f / bitmap.height)
        val scaledWidth = bitmap.width * scale
        val scaledHeight = bitmap.height * scale
        canvas.drawBitmap(
            bitmap,
            null,
            RectF(
                centerX - scaledWidth / 2f,
                centerY - scaledHeight / 2f,
                centerX + scaledWidth / 2f,
                centerY + scaledHeight / 2f,
            ),
            paint,
        )
    } else {
        paint.color = 0x66FFFFFF
        canvas.drawCircle(centerX, centerY, radius, paint)
        val initials = name.trim().split(Regex("\\s+"))
            .filter(String::isNotBlank)
            .take(2)
            .joinToString("") { it.first().uppercase() }
        val initialsPaint = type.body(radius, Color.WHITE).apply {
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            initials.ifBlank { "S" },
            centerX,
            centerY - (initialsPaint.ascent() + initialsPaint.descent()) / 2f,
            initialsPaint,
        )
    }
    canvas.restore()
}

private fun wrapText(text: String, paint: android.graphics.Paint, maxWidth: Float, maxLines: Int): List<String> {
    val words = text.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    if (words.isEmpty()) return emptyList()
    val lines = mutableListOf<String>()
    var current = ""
    for (word in words) {
        if (current.isEmpty() && paint.measureText(word) > maxWidth) {
            lines += ellipsised(word, paint, maxWidth)
            if (lines.size == maxLines) break
            continue
        }
        val candidate = if (current.isEmpty()) word else "$current $word"
        if (current.isNotEmpty() && paint.measureText(candidate) > maxWidth) {
            lines += current
            current = word
            if (lines.size == maxLines) break
        } else {
            current = candidate
        }
    }
    if (lines.size < maxLines && current.isNotEmpty()) lines += current
    if (lines.size > maxLines) return lines.take(maxLines)
    if (words.joinToString(" ") != lines.joinToString(" ")) {
        val last = lines.lastOrNull().orEmpty()
        lines[lines.lastIndex] = ellipsised(last, paint, maxWidth)
    }
    return lines
}

private fun DetailPage.headerLinesForShare(context: Context, count: Int): String {
    // Some playlist responses already put “N songs/tracks” in the subtitle.
    // The poster adds its own localized count, so remove that suffix first to
    // avoid rendering the number twice.
    val subtitle = subtitle
        .replace(Regex("(?i)(?:\\s*[·•,|]\\s*)?\\d+\\s+(?:songs?|tracks?)\\s*$"), "")
        .trim()
        .trim('·', '•', ',', '|')
        .trim()
        .takeIf { it.isNotBlank() }
        ?: context.getString(R.string.playlist_share_playlist_fallback)
    val songCount = context.resources.getQuantityString(R.plurals.song_count_plural, count, count)
    return context.getString(R.string.playlist_share_metadata, subtitle, songCount)
}
