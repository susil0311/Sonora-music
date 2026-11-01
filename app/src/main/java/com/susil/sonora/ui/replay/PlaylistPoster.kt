package com.susil.sonora.ui.replay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
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

/**
 * Panel fill: solid, fade-to-black, or black. [accent] is the swatch color.
 * Build the three cover-tinted options with [swatches], passing the same
 * `ArtworkPalette.background` the playlist screen paints with.
 */
internal data class PlaylistPosterPalette(
    val label: String,
    val accent: Int,
    val surfaceTop: Int,
    val surfaceBottom: Int,
) {
    companion object {
        val Noir = PlaylistPosterPalette("Noir", 0xFF0C0C0C.toInt(), 0xFF0C0C0C.toInt(), 0xFF0C0C0C.toInt())

        /** Flat panel in [base] — the screen's own page color. */
        fun solid(base: Int) = PlaylistPosterPalette("Solid", base, base, base)

        /** [base] hue, brightened and saturated at the top, fading to near-black. */
        fun fade(base: Int): PlaylistPosterPalette {
            val hsv = FloatArray(3).also { Color.colorToHSV(base, it) }
            val chromatic = hsv[1] >= 0.12f
            val top = Color.HSVToColor(floatArrayOf(hsv[0], if (chromatic) maxOf(hsv[1], 0.85f) else hsv[1], 0.67f))
            val bottom = Color.HSVToColor(floatArrayOf(hsv[0], hsv[1] * 0.8f, 0.06f))
            return PlaylistPosterPalette("Fade", top, top, bottom)
        }

        /** The three swatches in order: solid, fade, black. */
        fun swatches(base: Int) = listOf(solid(base), fade(base), Noir)
    }
}

private fun averageColor(bitmap: Bitmap): Int =
    Bitmap.createScaledBitmap(bitmap, 1, 1, true).getPixel(0, 0) or 0xFF000000.toInt()

/** Data needed to make a stable, shareable picture of a playlist. */
internal data class PlaylistShareRequest(
    val title: String,
    val username: String?,
    val description: String? = null,
    val artworkUrl: String?,
    val songs: List<Song>,
    val ownerAvatarUrl: String? = null,
)

/** Spotify-style share card: cover + title + owner header, then a plain tracklist. */
internal suspend fun renderPlaylistPoster(
    context: Context,
    request: PlaylistShareRequest,
    ratio: PlaylistPosterRatio = PlaylistPosterRatio.Portrait,
    showBranding: Boolean = true,
    /** Null derives a fade from the cover's average color; pass the screen's swatch to match it. */
    palette: PlaylistPosterPalette? = null,
): Bitmap {
    val isStory = ratio == PlaylistPosterRatio.Story
    val previewCount = if (isStory) STORY_TRACK_PREVIEW_COUNT else PORTRAIT_TRACK_PREVIEW_COUNT
    val previewSongs = request.songs.take(previewCount)
    val leadUrl = request.artworkUrl ?: previewSongs.firstOrNull()?.thumbnailUrl
    val imageUrls = (listOfNotNull(leadUrl, request.ownerAvatarUrl) + previewSongs.mapNotNull { it.thumbnailUrl }).distinct()
    val images = coroutineScope {
        imageUrls.associateWith { url -> async(Dispatchers.IO) { loadBitmap(context, url) } }
            .mapValues { (_, deferred) -> deferred.await() }
    }

    return withContext(Dispatchers.Default) {
        val width = POSTER_WIDTH
        val height = if (isStory) STORY_HEIGHT else PORTRAIT_HEIGHT
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val type = Fonts(context)
        val lead = leadUrl?.let(images::get)
        val colors = palette ?: PlaylistPosterPalette.fade(lead?.let(::averageColor) ?: 0xFFAA0000.toInt())
        val frame = drawPlaylistFrame(canvas, width, height, colors)
        val pad = 38f

        // Header: cover left; title + owner row vertically centered beside it.
        val coverSize = if (isStory) 330f else 260f
        val coverLeft = frame.left + pad
        val coverTop = frame.top + pad
        drawArtwork(canvas, lead, request.title, coverLeft, coverTop, coverSize, circular = false)

        val textLeft = coverLeft + coverSize + 34f
        val textWidth = (frame.right - pad - textLeft).coerceAtLeast(250f)
        val titlePaint = type.heading(if (isStory) 56f else 50f, Color.WHITE)
        val titleLines = wrapPosterTitle(request.title, titlePaint, textWidth)
        val titleAdvance = titlePaint.textSize * TITLE_LINE_SPACING
        val avatarSize = if (isStory) 56f else 48f
        val gap = 26f
        val titleBlock = titleLines.size * titleAdvance
        val blockHeight = titleBlock + gap + avatarSize
        val blockTop = coverTop + (coverSize - blockHeight) / 2f
        titleLines.forEachIndexed { index, line ->
            canvas.drawText(line, textLeft, blockTop + titlePaint.textSize * 0.85f + index * titleAdvance, titlePaint)
        }

        val owner = cleanPlaylistOwner(request.username.orEmpty()).ifBlank { "Playlist owner" }
        val ownerAvatar = request.ownerAvatarUrl?.let(images::get)
        val avatarTop = blockTop + titleBlock + gap
        drawArtwork(canvas, ownerAvatar, owner, textLeft, avatarTop, avatarSize, circular = true)
        val ownerPaint = type.body(if (isStory) 31f else 28f, 0xF0FFFFFF.toInt())
        val ownerX = textLeft + avatarSize + 16f
        val ownerRoom = (frame.right - pad - ownerX).coerceAtLeast(100f)
        canvas.drawText(
            ellipsised("Playlist by $owner", ownerPaint, ownerRoom),
            ownerX,
            avatarTop + avatarSize * 0.70f,
            ownerPaint,
        )

        // Tracklist: no dividers, regular-weight white title, muted artist.
        val rowsTop = coverTop + coverSize + if (isStory) 44f else 36f
        val rowsBottom = frame.bottom - if (isStory) 118f else 96f
        val rowHeight = if (previewSongs.isEmpty()) 0f else (rowsBottom - rowsTop) / previewSongs.size
        val trackArtSize = minOf(if (isStory) 124f else 108f, rowHeight * 0.74f)
        val rowTitlePaint = type.body(if (isStory) 40f else 36f, Color.WHITE)
        val artistPaint = type.body(if (isStory) 32f else 29f, 0x99FFFFFF.toInt())

        previewSongs.forEachIndexed { index, song ->
            val rowTop = rowsTop + index * rowHeight
            val artTop = rowTop + (rowHeight - trackArtSize) / 2f
            drawArtwork(
                canvas,
                song.thumbnailUrl?.let(images::get),
                song.title,
                frame.left + pad,
                artTop,
                trackArtSize,
                circular = false,
            )
            val textX = frame.left + pad + trackArtSize + 28f
            val room = frame.right - pad - textX
            val titleBaseline = artTop + trackArtSize * 0.42f
            canvas.drawText(ellipsised(song.title, rowTitlePaint, room), textX, titleBaseline, rowTitlePaint)
            canvas.drawText(
                ellipsised(song.artist, artistPaint, room),
                textX,
                titleBaseline + if (isStory) 46f else 42f,
                artistPaint,
            )
        }

        // Brand lockup bottom-right, like the Spotify wordmark.
        if (showBranding) drawBrandLockup(canvas, context, type, frame.right - 36f, frame.bottom - 40f)

        bitmap
    }
}

/** Dark outer mat with a borderless gradient panel. */
private fun drawPlaylistFrame(
    canvas: Canvas,
    width: Int,
    height: Int,
    palette: PlaylistPosterPalette,
): RectF {
    canvas.drawColor(0xFF202124.toInt())
    val outer = RectF(30f, 40f, width - 30f, height - 40f)
    canvas.drawRoundRect(outer, 56f, 56f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0A0A0A.toInt() })

    val panel = RectF(70f, 80f, width - 70f, height - 80f)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            panel.left,
            panel.top,
            panel.left,
            panel.bottom,
            intArrayOf(palette.surfaceTop, palette.surfaceBottom),
            null,
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRoundRect(panel, 34f, 34f, fill)
    return panel
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
        val remainder = (listOf(lines.last()) + words.drop(index)).joinToString(" ")
        lines[lines.lastIndex] = ellipsised(remainder, paint, width)
    }
    return lines.ifEmpty { listOf(ellipsised(text, paint, width)) }
}

/** Keep human owner text; discard playlist labels and stale count/year metadata. */
private fun cleanPlaylistOwner(subtitle: String): String = subtitle.trim()
    .replace(Regex("(?i)^playlist\\s+by\\s+"), "")
    .split(Regex("\\s*[·•|]\\s*"))
    .map { it.trim() }
    .filterNot { part ->
        part.isBlank() || part.equals("playlist", ignoreCase = true) || part.equals("album", ignoreCase = true) ||
            part.matches(Regex("\\d{4}")) ||
            part.matches(Regex("(?i)\\d[\\d,.]*\\+?\\s*(?:tracks?|songs?|views?|hours?|mins?|minutes?)\\b.*"))
    }
    .joinToString(" · ")

private const val POSTER_WIDTH = 1080
private const val PORTRAIT_HEIGHT = 1350
private const val STORY_HEIGHT = 1920
private const val PORTRAIT_TRACK_PREVIEW_COUNT = 5
private const val STORY_TRACK_PREVIEW_COUNT = 7
private const val TITLE_LINE_SPACING = 1.04f
