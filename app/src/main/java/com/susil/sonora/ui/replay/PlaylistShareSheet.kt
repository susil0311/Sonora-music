package com.susil.sonora.ui.replay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.susil.sonora.data.model.DetailPage
import com.susil.sonora.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class PlaylistCardMode(val ratio: Float, val songs: Int, val label: String) {
    POST(1.91f, 5, "Post"),
    STORY(9f / 16f, 7, "Story"),
}

@Composable
fun PlaylistShareSheet(page: DetailPage, songs: List<Song>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(PlaylistCardMode.POST) }
    var poster by remember(page, songs, mode) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(page, songs, mode) {
        poster = withContext(Dispatchers.IO) { renderPlaylistCard(context, page, songs, mode) }
    }
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Share playlist", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Spotify-style color extraction with up to ${mode.songs} songs", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        poster?.let { image ->
            Image(
                bitmap = image.asImageBitmap(), contentDescription = page.title,
                modifier = Modifier.fillMaxWidth().aspectRatio(mode.ratio).clip(RoundedCornerShape(18.dp)),
            )
        } ?: CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlaylistModeButton(PlaylistCardMode.POST, mode == PlaylistCardMode.POST) { mode = PlaylistCardMode.POST }
            PlaylistModeButton(PlaylistCardMode.STORY, mode == PlaylistCardMode.STORY) { mode = PlaylistCardMode.STORY }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ShareAction("Save", Icons.Rounded.Save, false, poster != null, Modifier.weight(1f)) {
                poster?.let { image -> scope.launch { saveToGallery(context, image, page.title, "sonora-playlist") } }
            }
            ShareAction("Share", Icons.Rounded.IosShare, true, poster != null, Modifier.weight(1f)) {
                poster?.let { image -> scope.launch {
                    cacheForSharing(context, image, "playlist-share.png")?.let { uri ->
                        context.startActivity(android.content.Intent.createChooser(sendIntent(uri), "Share playlist"))
                        onDismiss()
                    }
                } }
            }
        }
    }
}

@Composable
private fun PlaylistModeButton(mode: PlaylistCardMode, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) {
        Text(mode.label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

private suspend fun renderPlaylistCard(context: Context, page: DetailPage, songs: List<Song>, mode: PlaylistCardMode): Bitmap = withContext(Dispatchers.Default) {
    val width = 1080
    val height = if (mode == PlaylistCardMode.STORY) 1920 else 566
    val cover = page.thumbnailUrl?.let { loadBitmap(context, it) }
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawBackdrop(canvas, cover, 0f, height.toFloat())
    val type = Fonts(context)
    val white = Color.WHITE
    val margin = 64f
    val title = type.heading(if (mode == PlaylistCardMode.STORY) 72f else 54f, white)
    canvas.drawText(ellipsised(page.title, title, width - margin * 2), margin, if (mode == PlaylistCardMode.STORY) 150f else 86f, title)
    val author = page.headerLinesForShare(songs.size)
    val sub = type.body(34f, 0xCCFFFFFF.toInt())
    canvas.drawText(ellipsised(author, sub, width - margin * 2), margin, if (mode == PlaylistCardMode.STORY) 210f else 132f, sub)
    val description = page.description.orEmpty().replace('\n', ' ')
    if (description.isNotBlank()) {
        val desc = type.body(30f, 0xB3FFFFFF.toInt())
        canvas.drawText(ellipsised(description, desc, width - margin * 2), margin, if (mode == PlaylistCardMode.STORY) 262f else 178f, desc)
    }
    val maxSongs = songs.take(mode.songs)
    val startY = if (mode == PlaylistCardMode.STORY) 620f else 260f
    val rowGap = if (mode == PlaylistCardMode.STORY) 150f else 54f
    maxSongs.forEachIndexed { index, song ->
        val y = startY + index * rowGap
        val art = 76f
        drawArtwork(canvas, song.thumbnailUrl?.let { loadBitmap(context, it) }, song.title, margin, y - art + 12f, art, false)
        val paint = type.body(if (mode == PlaylistCardMode.STORY) 38f else 26f, white, bold = true)
        canvas.drawText(ellipsised(song.title, paint, width - margin * 2 - art - 24f), margin + art + 24f, y, paint)
        val artist = type.body(if (mode == PlaylistCardMode.STORY) 28f else 20f, 0xB3FFFFFF.toInt())
        canvas.drawText(ellipsised(song.artist, artist, width - margin * 2 - art - 24f), margin + art + 24f, y + 34f, artist)
    }
    val brand = type.body(26f, 0xCCFFFFFF.toInt(), bold = true)
    canvas.drawText("Sonora", width - margin - brand.measureText("Sonora"), height - 38f, brand)
    bitmap
}

private fun DetailPage.headerLinesForShare(count: Int): String {
    val subtitle = subtitle.takeIf { it.isNotBlank() } ?: "Playlist"
    return "$subtitle · $count songs"
}
