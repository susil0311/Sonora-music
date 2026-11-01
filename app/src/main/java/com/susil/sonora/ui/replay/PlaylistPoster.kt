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

/** Border and panel treatments for the Spotify-inspired playlist card. */
internal enum class PlaylistPosterPalette(
    val label: String,
    val accent: Int,
    val surfaceTop: Int,
    val surfaceBottom: Int,
) {
    Ruby("Ruby", 0xFFDF2638.toInt(), 0xFF7F101A.toInt(), 0xFF160B0D.toInt()),
    Violet("Violet", 0xFFB47BFF.toInt(), 0xFF48266B.toInt(), 0xFF100D16.toInt()),
    Teal("Teal", 0xFF56D6C3.toInt(), 0xFF14584F.toInt(), 0xFF0B1514.toInt()),
}

/** Data needed to make a stable, shareable picture of a playlist. */
internal data class PlaylistShareRequest(
    val title: String,
    val username: String?,
    val description: String? = null,
    val artworkUrl: String?,
    val songs: List<Song>,
    val ownerAvatarUrl: String? = null,
)

/** A polished social card with metadata and a compact tracklist, not a page screenshot. */
internal suspend fun renderPlaylistPoster(
    context: Context,
    request: PlaylistShareRequest,
    ratio: PlaylistPosterRatio = PlaylistPosterRatio.Portrait,
    showBranding: Boolean = true,
    palette: PlaylistPosterPalette = PlaylistPosterPalette.Ruby,
): Bitmap {
    val previewCount = if (ratio == PlaylistPosterRatio.Story) {
        STORY_TRACK_PREVIEW_COUNT
    } else {
        PORTRAIT_TRACK_PREVIEW_COUNT
    }
    val previewSongs = request.songs.take(previewCount)
    val leadUrl = request.artworkUrl ?: previewSongs.firstOrNull()?.thumbnailUrl
    val imageUrls = (listOfNotNull(leadUrl, request.ownerAvatarUrl) + previewSongs.mapNotNull { it.thumbnailUrl }).distinct()
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
        val frame = drawPlaylistFrame(canvas, width, height, palette)
        val isStory = ratio == PlaylistPosterRatio.Story
        if (showBranding) drawBrandLockup(canvas, context, type, frame.right - 36f, frame.top + 68f)

        // A two-column Spotify-inspired header: cover on the left, readable
        // title, creator profile, and description on the right.
        val coverSize = if (isStory) 360f else 280f
        val coverLeft = frame.left + 38f
        val coverTop = frame.top + 112f
        drawArtwork(canvas, lead, request.title, coverLeft, coverTop, coverSize, circular = false)
        val coverStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x4DFFFFFF
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRoundRect(coverLeft, coverTop, coverLeft + coverSize, coverTop + coverSize, 26f, 26f, coverStroke)

        val textLeft = coverLeft + coverSize + 38f
        val textWidth = (frame.right - 38f - textLeft).coerceAtLeast(250f)
        val titlePaint = type.heading(if (isStory) 58f else 50f, Color.WHITE)
        val titleBase = coverTop + if (isStory) 70f else 64f
        val titleLines = wrapPosterTitle(request.title, titlePaint, textWidth)
        val titleAdvance = titlePaint.textSize * TITLE_LINE_SPACING
        titleLines.forEachIndexed { index, line ->
            canvas.drawText(line, textLeft, titleBase + index * titleAdvance, titlePaint)
        }
        val titleBottom = titleBase + (titleLines.lastIndex.coerceAtLeast(0) * titleAdvance) + titlePaint.textSize * 0.76f

        val owner = cleanPlaylistOwner(request.username.orEmpty()).ifBlank { "Playlist owner" }
        val ownerAvatar = request.ownerAvatarUrl?.let(images::get)
        val avatarSize = if (isStory) 56f else 48f
        val avatarTop = titleBottom + 22f
        drawArtwork(canvas, ownerAvatar, owner, textLeft, avatarTop, avatarSize, circular = true)
        val ownerLinePaint = type.body(if (isStory) 31f else 27f, 0xF0FFFFFF.toInt())
        val count = context.resources.getQuantityString(
            R.plurals.replay_song_count,
            previewSongs.size,
            previewSongs.size.toString(),
        )
        val countPaint = type.body(if (isStory) 28f else 24f, 0xD8FFFFFF.toInt(), bold = true).apply {
            textAlign = Paint.Align.RIGHT
        }
        val ownerTextX = textLeft + avatarSize + 16f
        val countRight = frame.right - 38f
        val ownerPrefix = "Playlist by "
        val ownerRoom = (
            countRight - ownerTextX - countPaint.measureText(count) - 24f - ownerLinePaint.measureText(ownerPrefix)
        ).coerceAtLeast(100f)
        val ownerBaseline = avatarTop + avatarSize * 0.70f
        canvas.drawText(ownerPrefix + ellipsised(owner, ownerLinePaint, ownerRoom), ownerTextX, ownerBaseline, ownerLinePaint)
        canvas.drawText(count, countRight, ownerBaseline, countPaint)

        val description = request.description?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        val descriptionPaint = type.body(if (isStory) 27f else 23f, 0xD0FFFFFF.toInt())
        val descriptionLines = if (description.isBlank()) emptyList() else wrapPosterDescription(
            description,
            descriptionPaint,
            textWidth,
            maxLines = if (isStory) 3 else 2,
        )
        val descriptionAdvance = if (isStory) 38f else 31f
        val descriptionBase = avatarTop + avatarSize + if (descriptionLines.isEmpty()) 0f else 32f
        descriptionLines.forEachIndexed { index, line ->
            canvas.drawText(line, textLeft, descriptionBase + index * descriptionAdvance, descriptionPaint)
        }
        val descriptionBottom = if (descriptionLines.isEmpty()) avatarTop + avatarSize else
            descriptionBase + descriptionLines.lastIndex * descriptionAdvance + descriptionPaint.textSize * 0.78f

        val rowsTop = maxOf(coverTop + coverSize, descriptionBottom) + if (isStory) 42f else 36f
        val rowsBottom = height - if (isStory) 112f else 88f
        val rowHeight = if (previewSongs.isEmpty()) 0f else (rowsBottom - rowsTop) / previewSongs.size
        val trackArtSize = minOf(if (isStory) 118f else 100f, rowHeight * 0.72f)
        val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x24FFFFFF.toInt()
            strokeWidth = 1.5f
        }

        previewSongs.forEachIndexed { index, song ->
            val rowTop = rowsTop + index * rowHeight
            val artTop = rowTop + (rowHeight - trackArtSize) / 2f
            drawArtwork(
                canvas,
                song.thumbnailUrl?.let(images::get),
                song.title,
                frame.left + 38f,
                artTop,
                trackArtSize,
                circular = false,
            )
            val titleBaseline = rowTop + rowHeight * 0.42f
            val textX = frame.left + 38f + trackArtSize + 28f
            val rowTitlePaint = type.body(if (isStory) 38f else 33f, Color.WHITE, bold = true)
            val artistPaint = type.body(if (isStory) 30f else 27f, 0xC8FFFFFF.toInt())
            canvas.drawText(
                ellipsised(song.title, rowTitlePaint, frame.right - 38f - textX),
                textX,
                titleBaseline,
                rowTitlePaint,
            )
            canvas.drawText(
                ellipsised(song.artist, artistPaint, frame.right - 38f - textX),
                textX,
                titleBaseline + if (isStory) 42f else 37f,
                artistPaint,
            )
            if (index < previewSongs.lastIndex) {
                canvas.drawLine(
                    frame.left + 38f,
                    rowTop + rowHeight - 1f,
                    frame.right - 38f,
                    rowTop + rowHeight - 1f,
                    divider,
                )
            }
        }

        bitmap
    }
}

/** A dark outer mat, selected-color stroke, and subdued matching panel. */
private fun drawPlaylistFrame(
    canvas: Canvas,
    width: Int,
    height: Int,
    palette: PlaylistPosterPalette,
): RectF {
    canvas.drawColor(0xFF202124.toInt())
    val outer = RectF(30f, 40f, width - 30f, height - 40f)
    val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF08090B.toInt() }
    canvas.drawRoundRect(outer, 56f, 56f, outerPaint)

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
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.accent
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    canvas.drawRoundRect(panel, 34f, 34f, border)
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
        val last = lines.last()
        val remainder = (listOf(last) + words.drop(index)).joinToString(" ")
        lines[lines.lastIndex] = ellipsised(remainder, paint, width)
    }
    return lines.ifEmpty { listOf(ellipsised(text, paint, width)) }
}

/** Wraps the description into a fixed number of lines without letting it collide with tracks. */
private fun wrapPosterDescription(
    text: String,
    paint: Paint,
    width: Float,
    maxLines: Int,
): List<String> {
    val words = text.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
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
