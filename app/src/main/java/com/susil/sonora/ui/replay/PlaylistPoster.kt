package com.susil.sonora.ui.replay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
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
    val previewCount = if (ratio == PlaylistPosterRatio.Story) {
        STORY_TRACK_PREVIEW_COUNT
    } else {
        PORTRAIT_TRACK_PREVIEW_COUNT
    }
    val previewSongs = request.songs.take(previewCount)
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
        drawPlaylistTextScrim(canvas, heroTop + heroSize - 24f, height.toFloat())
        val heroX = (width - heroSize) / 2f
        drawArtwork(canvas, lead, request.title, heroX, heroTop, heroSize, circular = false)
        val coverEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x38FFFFFF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRoundRect(
            heroX,
            heroTop,
            heroX + heroSize,
            heroTop + heroSize,
            heroSize * 0.10f,
            heroSize * 0.10f,
            coverEdge,
        )

        val title = type.heading(if (ratio == PlaylistPosterRatio.Story) 70f else 62f, Color.WHITE)
        val titleBaseline = heroTop + heroSize +
            (if (ratio == PlaylistPosterRatio.Story) 62f else 48f)
        val titleLines = wrapPosterTitle(request.title, title, CONTENT_WIDTH)
        val titleLineAdvance = title.textSize * TITLE_LINE_SPACING
        titleLines.forEachIndexed { index, line ->
            canvas.drawText(line, MARGIN + 34f, titleBaseline + index * titleLineAdvance, title)
        }
        val titleExtraHeight = (titleLines.size - 1).coerceAtLeast(0) * titleLineAdvance
        val metadataBaseline = titleBaseline +
            (if (ratio == PlaylistPosterRatio.Story) 58f else 52f) + titleExtraHeight

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
            canvas.drawText(ellipsised(owner, ownerPaint, ownerRoom), ownerX, metadataBaseline, ownerPaint)
            canvas.drawText(count, rightX, metadataBaseline, countPaint)
        } else {
            canvas.drawText(count, ownerX, metadataBaseline, countPaint)
        }
        canvas.drawText(
            context.getString(R.string.songs),
            ownerX,
            metadataBaseline + if (ratio == PlaylistPosterRatio.Story) 42f else 36f,
            type.label(24f, 0xA8FFFFFF.toInt(), tracking = 0.12f),
        )

        var rowsTop = metadataBaseline + if (ratio == PlaylistPosterRatio.Story) 68f else 62f
        val maxRowsBottom = height - 142f
        val rowHeight = if (ratio == PlaylistPosterRatio.Story) 108f else 82f
        val trackArtSize = if (ratio == PlaylistPosterRatio.Story) 76f else 58f
        val visibleTrackCount = ((maxRowsBottom - rowsTop) / rowHeight)
            .toInt()
            .coerceIn(0, previewSongs.size)
        val visibleSongs = previewSongs.take(visibleTrackCount)
        val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x24FFFFFF.toInt()
            strokeWidth = 1.5f
        }

        visibleSongs.forEachIndexed { index, song ->
            val rowTop = rowsTop
            val artTop = rowTop + (rowHeight - trackArtSize) / 2f
            drawArtwork(
                canvas,
                song.thumbnailUrl?.let(images::get),
                song.title,
                MARGIN + 34f,
                artTop,
                trackArtSize,
                circular = false,
            )
            val titleBaseline = rowTop + rowHeight * 0.43f
            val number = type.body(25f, 0xA8FFFFFF.toInt())
            canvas.drawText(
                "%02d".format(index + 1),
                MARGIN + 34f + trackArtSize + 18f,
                titleBaseline,
                number,
            )
            val textX = MARGIN + 34f + trackArtSize + 72f
            val titlePaint = type.body(29f, Color.WHITE, bold = true)
            val artistPaint = type.body(23f, 0xB8FFFFFF.toInt())
            canvas.drawText(
                ellipsised(song.title, titlePaint, CONTENT_WIDTH - 145f),
                textX,
                titleBaseline,
                titlePaint,
            )
            canvas.drawText(
                ellipsised(song.artist, artistPaint, CONTENT_WIDTH - 145f),
                textX,
                titleBaseline + 30f,
                artistPaint,
            )
            if (index < visibleSongs.lastIndex) {
                canvas.drawLine(
                    MARGIN + 34f,
                    rowTop + rowHeight - 1f,
                    width - MARGIN - 34f,
                    rowTop + rowHeight - 1f,
                    divider,
                )
            }
            rowsTop += rowHeight
        }

        bitmap
    }
}

/** Darkens only the text area beneath the cover so bright palettes stay legible. */
private fun drawPlaylistTextScrim(canvas: Canvas, startY: Float, height: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f,
            startY,
            0f,
            height,
            intArrayOf(
                Color.TRANSPARENT,
                0x58000000.toInt(),
                0x66000000.toInt(),
                0x78000000.toInt(),
            ),
            floatArrayOf(0f, 0.08f, 0.20f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRect(0f, startY, POSTER_WIDTH.toFloat(), height, paint)
}

/** Wrap a long playlist name to two lines, preserving the title before trimming its end. */
private fun wrapPosterTitle(text: String, paint: Paint, width: Float): List<String> {
    val words = text.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    if (words.isEmpty()) return listOf("")

    val lines = mutableListOf<String>()
    var current = ""
    var index = 0
    while (index < words.size && lines.size < 2) {
        val candidate = if (current.isEmpty()) words[index] else "$current ${words[index]}"
        if (paint.measureText(candidate) <= width) {
            current = candidate
            index++
        } else if (current.isNotEmpty()) {
            lines += current
            current = ""
        } else {
            lines += ellipsised(words[index], paint, width)
            index++
        }
    }
    if (current.isNotEmpty() && lines.size < 2) lines += current
    if (index < words.size && lines.isNotEmpty()) {
        val last = lines.last()
        val remainder = (listOf(last) + words.drop(index)).joinToString(" ")
        lines[lines.lastIndex] = ellipsised(remainder, paint, width)
    }
    return lines.ifEmpty { listOf(ellipsised(text, paint, width)) }
}

private const val POSTER_WIDTH = 1080
private const val PORTRAIT_HEIGHT = 1350
private const val STORY_HEIGHT = 1920
private const val MARGIN = 52f
private const val CONTENT_WIDTH = POSTER_WIDTH - MARGIN * 2f - 68f
private const val BRAND_RIGHT_INSET = 72f
private const val BRAND_BASELINE = 132f
private const val PORTRAIT_TRACK_PREVIEW_COUNT = 5
private const val STORY_TRACK_PREVIEW_COUNT = 7
private const val TITLE_LINE_SPACING = 1.04f
