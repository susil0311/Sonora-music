package com.susil.sonora.ui.replay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.susil.sonora.R
import com.susil.sonora.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

internal enum class PlaylistPosterRatio(val labelRes: Int, val ratio: Float) {
    Portrait(R.string.playlist_share_ratio_portrait, 4f / 5f),
    Story(R.string.playlist_share_ratio_story, 9f / 16f),
}

/** Data needed to make a stable, shareable picture of a playlist. */
internal data class PlaylistShareRequest(
    val title: String,
    val username: String?,
    val artworkUrl: String?,
    val songs: List<Song>,
)

/** A polished social card with metadata and a compact tracklist, not a page screenshot. */
internal suspend fun renderPlaylistPoster(
    context: Context,
    request: PlaylistShareRequest,
    ratio: PlaylistPosterRatio = PlaylistPosterRatio.Portrait,
    showBranding: Boolean = true,
): Bitmap {
    val previewSongs = request.songs.take(TRACK_PREVIEW_COUNT)
    val leadUrl = request.artworkUrl ?: previewSongs.firstOrNull()?.thumbnailUrl
    val imageUrls = (listOfNotNull(leadUrl) + previewSongs.mapNotNull { it.thumbnailUrl }).distinct()
    val images = coroutineScope {
        imageUrls.associateWith { url -> async(Dispatchers.IO) { loadBitmap(context, url) } }
            .mapValues { (_, deferred) -> deferred.await() }
    }

    return withContext(Dispatchers.Default) {
        val width = POSTER_WIDTH
        val height = if (ratio == PlaylistPosterRatio.Story) STORY_HEIGHT else PORTRAIT_HEIGHT
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val type = Fonts(context)
        val lead = leadUrl?.let(images::get)
        drawBackdrop(canvas, lead, hue = 0f, height = height.toFloat())
        if (showBranding) drawBrandLockup(canvas, context, type, width - BRAND_RIGHT_INSET, BRAND_BASELINE)

        val heroSize = if (ratio == PlaylistPosterRatio.Story) 600f else 470f
        val heroTop = if (ratio == PlaylistPosterRatio.Story) 126f else 112f
        drawArtwork(canvas, lead, request.title, (width - heroSize) / 2f, heroTop, heroSize, circular = false)

        var y = heroTop + heroSize + if (ratio == PlaylistPosterRatio.Story) 62f else 48f
        val title = type.heading(if (ratio == PlaylistPosterRatio.Story) 70f else 62f, Color.WHITE)
        canvas.drawText(ellipsised(request.title, title, CONTENT_WIDTH), MARGIN + 34f, y, title)
        y += if (ratio == PlaylistPosterRatio.Story) 58f else 52f

        val count = context.resources.getQuantityString(
            R.plurals.replay_song_count,
            request.songs.size,
            request.songs.size.toString(),
        )
        val owner = request.username?.trim()?.takeIf { it.isNotEmpty() }
        val ownerX = MARGIN + 34f
        val rightX = width - MARGIN - 34f
        val countPaint = type.body(30f, 0xE0FFFFFF.toInt(), bold = true).apply {
            textAlign = if (owner == null) Paint.Align.LEFT else Paint.Align.RIGHT
        }
        val ownerPaint = type.body(29f, 0xB8FFFFFF.toInt(), bold = true)
        if (owner != null) {
            val ownerRoom = (CONTENT_WIDTH - countPaint.measureText(count) - 32f).coerceAtLeast(120f)
            canvas.drawText(ellipsised(owner, ownerPaint, ownerRoom), ownerX, y, ownerPaint)
            canvas.drawText(count, rightX, y, countPaint)
        } else {
            canvas.drawText(count, ownerX, y, countPaint)
        }
        y += if (ratio == PlaylistPosterRatio.Story) 68f else 62f

        val maxRowsBottom = height - 142f
        val rowHeight = if (ratio == PlaylistPosterRatio.Story) 108f else 82f
        previewSongs.forEachIndexed { index, song ->
            if (y + rowHeight > maxRowsBottom) return@forEachIndexed
            val rowTop = y
            drawArtwork(canvas, song.thumbnailUrl?.let(images::get), song.title, MARGIN + 34f, rowTop, TRACK_ART_SIZE, circular = false)
            val number = type.body(25f, 0xA8FFFFFF.toInt())
            canvas.drawText("%02d".format(index + 1), MARGIN + 34f + TRACK_ART_SIZE + 18f, rowTop + 32f, number)
            val textX = MARGIN + 34f + TRACK_ART_SIZE + 72f
            val titlePaint = type.body(29f, Color.WHITE, bold = true)
            val artistPaint = type.body(23f, 0xB8FFFFFF.toInt())
            canvas.drawText(ellipsised(song.title, titlePaint, CONTENT_WIDTH - 145f), textX, rowTop + 28f, titlePaint)
            canvas.drawText(ellipsised(song.artist, artistPaint, CONTENT_WIDTH - 145f), textX, rowTop + 57f, artistPaint)
            y += rowHeight
        }

        bitmap
    }
}

private const val POSTER_WIDTH = 1080
private const val PORTRAIT_HEIGHT = 1350
private const val STORY_HEIGHT = 1920
private const val MARGIN = 52f
private const val CONTENT_WIDTH = POSTER_WIDTH - MARGIN * 2f - 68f
private const val BRAND_RIGHT_INSET = 72f
private const val BRAND_BASELINE = 132f
private const val TRACK_PREVIEW_COUNT = 4
private const val TRACK_ART_SIZE = 58f
