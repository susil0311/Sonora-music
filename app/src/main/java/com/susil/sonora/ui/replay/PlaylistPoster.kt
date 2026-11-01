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
 * Outline color only; the panel fill is always the cover-tinted fade (see [panelFade]).
 * Build the three cover-derived options with [swatches] from the same
 * `ArtworkPalette.background` the playlist screen paints with.
 */
internal data class PlaylistPosterPalette(
    val label: String,
    /** Border color, also the swatch color. */
    val accent: Int,
) {
    companion object {
        private fun shade(base: Int, label: String, sat: Float, value: Float): PlaylistPosterPalette {
            val h = FloatArray(3).also { Color.colorToHSV(base, it) }
            // Greys stay grey; chromatic covers keep their hue, with saturation floored at [sat].
            val s = if (h[1] >= 0.12f) maxOf(h[1], sat) else h[1]
            return PlaylistPosterPalette(label, Color.HSVToColor(floatArrayOf(h[0], s, value)))
        }

        /** The cover's hue, bright and saturated. Default outline. */
        fun vivid(base: Int) = shade(base, "Vivid", 0.85f, 0.90f)

        /** Same hue, darker. */
        fun deep(base: Int) = shade(base, "Deep", 0.90f, 0.60f)

        /** Same hue, lighter and less saturated. */
        fun light(base: Int) = shade(base, "Light", 0.55f, 1.00f)

        /** The three outline options, all within the cover's hue: deep, vivid, light. */
        fun swatches(base: Int) = listOf(deep(base), vivid(base), light(base))
    }
}

/** Panel gradient endpoints for [base]: bright saturated top fading to near-black. */
private fun panelFade(base: Int): Pair<Int, Int> {
    val h = FloatArray(3).also { Color.colorToHSV(base, it) }
    val s = if (h[1] >= 0.12f) maxOf(h[1], 0.85f) else h[1]
    val top = Color.HSVToColor(floatArrayOf(h[0], s, 0.6f))
    val bottom = Color.HSVToColor(floatArrayOf(h[0], h[1] * 0.8f, 0.06f))
    return top to bottom
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
    /** Outline color. Null uses the vivid option from [surface]. */
    palette: PlaylistPosterPalette? = null,
    /** Cover color the panel fade is built from (the screen's `ArtworkPalette.background`). Null averages the cover. */
    surface: Int? = null,
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
        val base = surface ?: lead?.let(::averageColor) ?: 0xFFAA0000.toInt()
        val border = (palette ?: PlaylistPosterPalette.vivid(base)).accent
        val (fadeTop, fadeBottom) = panelFade(base)
        val frame = drawPlaylistFrame(canvas, width, height, fadeTop, fadeBottom, border)
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
        val gap = 10f
        val titleBlock = titleLines.size * titleAdvance
        val descPaint = type.body(if (isStory) 27f else 24f, 0xD0FFFFFF.toInt())
        val descLines = wrapPosterDescription(
            request.description.orEmpty(),
            descPaint,
            textWidth,
            maxLines = if (isStory) 3 else 2,
        )
        val descAdvance = if (isStory) 38f else 32f
        val descGap = if (descLines.isEmpty()) 0f else 16f
        val descHeight = descLines.size * descAdvance
        val blockHeight = titleBlock + gap + avatarSize + descGap + descHeight
        val blockTop = maxOf(coverTop, coverTop + (coverSize - blockHeight) / 2f)
        titleLines.forEachIndexed { index, line ->
            canvas.drawText(line, textLeft, blockTop + titlePaint.textSize * 0.85f + index * titleAdvance, titlePaint)
        }

        val owner = cleanPlaylistOwner(request.username.orEmpty()).ifBlank { "Playlist owner" }
        val ownerAvatar = request.ownerAvatarUrl?.let(images::get)
        val avatarTop = blockTop + titleBlock + gap
        drawArtwork(canvas, ownerAvatar, owner, textLeft, avatarTop, avatarSize, circular = true)
        val ownerPaint = type.body(if (isStory) 31f else 28f, 0xF0FFFFFF.toInt())
        val ownerX = textLeft + avatarSize + 16f
        val total = request.songs.size
        val count = context.resources.getQuantityString(R.plurals.track_count_plural, total, total)
        val countPaint = type.body(if (isStory) 28f else 25f, 0xD8FFFFFF.toInt(), bold = true).apply {
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(count, frame.right - pad, avatarTop + avatarSize * 0.70f, countPaint)
        val ownerRoom = (frame.right - pad - ownerX - countPaint.measureText(count) - 24f).coerceAtLeast(100f)
        canvas.drawText(
            ellipsised(owner, ownerPaint, ownerRoom),
            ownerX,
            avatarTop + avatarSize * 0.70f,
            ownerPaint,
        )

        val descTop = avatarTop + avatarSize + descGap
        descLines.forEachIndexed { index, line ->
            canvas.drawText(line, textLeft, descTop + descPaint.textSize * 0.85f + index * descAdvance, descPaint)
        }
        val headerBottom = maxOf(coverTop + coverSize, descTop + descHeight)

        // Tracklist: no dividers, regular-weight white title, muted artist.
        val rowsTop = headerBottom + if (isStory) 44f else 36f
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

        // Quiet credit line, bottom center; no logo.
        if (showBranding) {
            val credit = type.body(if (isStory) 32f else 29f, 0xA6FFFFFF.toInt()).apply {
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Shared from Sonora", (frame.left + frame.right) / 2f, frame.bottom - 44f, credit)
        }

        bitmap
    }
}

/** Dark outer mat with a borderless gradient panel. */
private fun drawPlaylistFrame(
    canvas: Canvas,
    width: Int,
    height: Int,
    fadeTop: Int,
    fadeBottom: Int,
    border: Int,
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
            intArrayOf(fadeTop, fadeBottom),
            null,
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRoundRect(panel, 34f, 34f, fill)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = border
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    canvas.drawRoundRect(panel, 34f, 34f, stroke)
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

/** Wraps the description to [maxLines], ellipsising the last line; blank text gives no lines. */
private fun wrapPosterDescription(text: String, paint: Paint, width: Float, maxLines: Int): List<String> {
    val words = text.replace(Regex("\\s+"), " ").trim().split(' ').filter(String::isNotEmpty)
    if (words.isEmpty()) return emptyList()
    val lines = mutableListOf<String>()
    var current = ""
    var index = 0
    while (index < words.size && lines.size < maxLines) {
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
    if (current.isNotEmpty() && lines.size < maxLines) lines += current
    if (index < words.size && lines.isNotEmpty()) {
        val remainder = (listOf(lines.last()) + words.drop(index)).joinToString(" ")
        lines[lines.lastIndex] = ellipsised(remainder, paint, width)
    }
    return lines
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
