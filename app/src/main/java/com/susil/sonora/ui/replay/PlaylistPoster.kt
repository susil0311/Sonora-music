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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal enum class PlaylistPosterRatio(val labelRes: Int, val ratio: Float) {
    Portrait(R.string.playlist_share_ratio_portrait, 4f / 5f),
    Story(R.string.playlist_share_ratio_story, 9f / 16f),
}

/**
 * Outline color only; the panel fill is always the cover-tinted fade.
 */
internal data class PlaylistPosterPalette(
    val label: String,
    val accent: Int,
) {
    companion object {
        private fun shade(
            base: Int,
            label: String,
            sat: Float,
            value: Float,
        ): PlaylistPosterPalette {
            val hsv = FloatArray(3).also { Color.colorToHSV(base, it) }
            val saturation = if (hsv[1] >= 0.12f) {
                maxOf(hsv[1], sat)
            } else {
                hsv[1]
            }

            return PlaylistPosterPalette(
                label,
                Color.HSVToColor(floatArrayOf(hsv[0], saturation, value)),
            )
        }

        fun vivid(base: Int) = shade(base, "Vivid", 0.85f, 0.90f)

        fun deep(base: Int) = shade(base, "Deep", 0.90f, 0.60f)

        fun light(base: Int) = shade(base, "Light", 0.55f, 1.00f)

        fun swatches(base: Int) = listOf(
            deep(base),
            vivid(base),
            light(base),
        )
    }
}

/**
 * Three-stop cover-tinted gradient.
 */
private fun panelFade(base: Int): IntArray {
    val hsv = FloatArray(3).also { Color.colorToHSV(base, it) }

    val saturation = if (hsv[1] >= 0.12f) {
        maxOf(hsv[1], 0.82f)
    } else {
        hsv[1]
    }

    val top = Color.HSVToColor(
        floatArrayOf(hsv[0], saturation, 0.62f)
    )

    val middle = Color.HSVToColor(
        floatArrayOf(hsv[0], saturation * 0.88f, 0.34f)
    )

    val bottom = Color.HSVToColor(
        floatArrayOf(hsv[0], hsv[1] * 0.72f, 0.055f)
    )

    return intArrayOf(top, middle, bottom)
}

/**
 * Estimates average artwork color using a small sampled bitmap.
 */
private fun averageColor(bitmap: Bitmap): Int {
    if (bitmap.width <= 0 || bitmap.height <= 0) {
        return Color.BLACK
    }

    val sampleSize = 16
    val sample = Bitmap.createScaledBitmap(
        bitmap,
        sampleSize,
        sampleSize,
        true,
    )

    return try {
        var red = 0L
        var green = 0L
        var blue = 0L

        val pixels = IntArray(sampleSize * sampleSize)

        sample.getPixels(
            pixels,
            0,
            sampleSize,
            0,
            0,
            sampleSize,
            sampleSize,
        )

        pixels.forEach { pixel ->
            red += Color.red(pixel)
            green += Color.green(pixel)
            blue += Color.blue(pixel)
        }

        val count = pixels.size.toFloat()

        Color.rgb(
            (red / count).roundToInt().coerceIn(0, 255),
            (green / count).roundToInt().coerceIn(0, 255),
            (blue / count).roundToInt().coerceIn(0, 255),
        )
    } finally {
        if (sample !== bitmap && !sample.isRecycled) {
            sample.recycle()
        }
    }
}

/**
 * Data needed to make a stable, shareable picture of a playlist.
 */
internal data class PlaylistShareRequest(
    val title: String,
    val username: String?,
    val description: String? = null,
    val artworkUrl: String?,
    val songs: List<Song>,
    val ownerAvatarUrl: String? = null,
)

/**
 * Spotify-style share card:
 * cover + title + owner header, followed by a tracklist.
 */
internal suspend fun renderPlaylistPoster(
    context: Context,
    request: PlaylistShareRequest,
    ratio: PlaylistPosterRatio = PlaylistPosterRatio.Portrait,
    showBranding: Boolean = true,
    palette: PlaylistPosterPalette? = null,
    surface: Int? = null,
): Bitmap {

    val isStory = ratio == PlaylistPosterRatio.Story

    val previewCount = if (isStory) {
        STORY_TRACK_PREVIEW_COUNT
    } else {
        PORTRAIT_TRACK_PREVIEW_COUNT
    }

    val previewSongs = request.songs.take(previewCount)

    val leadUrl = request.artworkUrl
        ?: previewSongs.firstOrNull()?.thumbnailUrl

    val imageUrls = (
        listOfNotNull(leadUrl, request.ownerAvatarUrl) +
            previewSongs.mapNotNull { it.thumbnailUrl }
        ).distinct()

    // Limit concurrent image loading to reduce memory pressure.
    val imageLoadSemaphore = Semaphore(IMAGE_LOAD_CONCURRENCY)

    val images = coroutineScope {
        imageUrls.map { url ->
            url to async(Dispatchers.IO) {
                imageLoadSemaphore.withPermit {
                    loadBitmap(context, url)
                }
            }
        }.associate { (url, deferred) ->
            url to deferred.await()
        }
    }

    return withContext(Dispatchers.Default) {

        val width = POSTER_WIDTH

        val height = if (isStory) {
            STORY_HEIGHT
        } else {
            PORTRAIT_HEIGHT
        }

        val bitmap = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888,
        )

        val canvas = Canvas(bitmap)
        val type = Fonts(context)

        val lead = leadUrl?.let(images::get)

        val base = surface
            ?: lead?.let(::averageColor)
            ?: DEFAULT_SURFACE

        val border = (palette ?: PlaylistPosterPalette.vivid(base)).accent

        val (fadeTop, fadeMiddle, fadeBottom) = panelFade(base)

        val frame = drawPlaylistFrame(
            canvas,
            width,
            height,
            fadeTop,
            fadeMiddle,
            fadeBottom,
            border,
        )

        val pad = 38f

        // Header artwork.
        val coverSize = if (isStory) 330f else 260f

        val coverLeft = frame.left + pad
        val coverTop = frame.top + pad

        drawArtwork(
            canvas,
            lead,
            request.title,
            coverLeft,
            coverTop,
            coverSize,
            circular = false,
        )

        // Header text.
        val textLeft = coverLeft + coverSize + 34f

        val textWidth = (
            frame.right - pad - textLeft
            ).coerceAtLeast(MIN_HEADER_TEXT_WIDTH)

        val titlePaint = type.heading(
            if (isStory) 56f else 50f,
            Color.WHITE,
        )

        val titleLines = wrapPosterTitle(
            request.title,
            titlePaint,
            textWidth,
        )

        val titleAdvance = titlePaint.textSize * TITLE_LINE_SPACING

        val avatarSize = if (isStory) 56f else 48f
        val gap = 10f

        val titleBlock = titleLines.size * titleAdvance

        val descPaint = type.body(
            if (isStory) 27f else 24f,
            0xD0FFFFFF.toInt(),
        )

        val descLines = wrapPosterDescription(
            request.description.orEmpty(),
            descPaint,
            textWidth,
            maxLines = if (isStory) 3 else 2,
        )

        val descAdvance = if (isStory) 38f else 32f
        val descGap = if (descLines.isEmpty()) 0f else 16f
        val descHeight = descLines.size * descAdvance

        val blockHeight = titleBlock +
            gap +
            avatarSize +
            descGap +
            descHeight

        val blockTop = maxOf(
            coverTop,
            coverTop + (coverSize - blockHeight) / 2f,
        )

        titleLines.forEachIndexed { index, line ->

            canvas.drawText(
                line,
                textLeft,
                blockTop +
                    titlePaint.textSize * 0.85f +
                    index * titleAdvance,
                titlePaint,
            )
        }

        // Playlist owner.
        val owner = cleanPlaylistOwner(
            request.username.orEmpty()
        ).ifBlank {
            "Playlist owner"
        }

        val ownerAvatar = request.ownerAvatarUrl?.let(images::get)

        val avatarTop = blockTop + titleBlock + gap

        drawArtwork(
            canvas,
            ownerAvatar,
            owner,
            textLeft,
            avatarTop,
            avatarSize,
            circular = true,
        )

        val ownerPaint = type.body(
            if (isStory) 31f else 28f,
            0xF0FFFFFF.toInt(),
        )

        val ownerX = textLeft + avatarSize + 16f

        val total = request.songs.size

        val count = context.resources.getQuantityString(
            R.plurals.track_count_plural,
            total,
            total,
        )

        val countPaint = type.body(
            if (isStory) 28f else 25f,
            0xD8FFFFFF.toInt(),
            bold = true,
        ).apply {
            textAlign = Paint.Align.RIGHT
        }

        canvas.drawText(
            count,
            frame.right - pad,
            avatarTop + avatarSize * 0.70f,
            countPaint,
        )

        val ownerRoom = (
            frame.right -
                pad -
                ownerX -
                countPaint.measureText(count) -
                24f
            ).coerceAtLeast(MIN_OWNER_TEXT_WIDTH)

        canvas.drawText(
            ellipsised(owner, ownerPaint, ownerRoom),
            ownerX,
            avatarTop + avatarSize * 0.70f,
            ownerPaint,
        )

        // Playlist description.
        val descTop = avatarTop + avatarSize + descGap

        descLines.forEachIndexed { index, line ->

            canvas.drawText(
                line,
                textLeft,
                descTop +
                    descPaint.textSize * 0.85f +
                    index * descAdvance,
                descPaint,
            )
        }

        // Tracklist layout.
        val headerBottom = maxOf(
            coverTop + coverSize,
            descTop + descHeight,
        )

        val rowsTop = headerBottom +
            if (isStory) 44f else 36f

        val footerReserve = if (isStory) 118f else 96f

        val rowsBottom = frame.bottom - footerReserve

        val availableTrackHeight = (
            rowsBottom - rowsTop
            ).coerceAtLeast(0f)

        val rowHeight = if (previewSongs.isEmpty()) {
            0f
        } else {
            availableTrackHeight / previewSongs.size
        }

        // Do not draw rows if the header leaves insufficient space.
        if (rowHeight > MIN_TRACK_ROW_HEIGHT) {

            val trackArtSize = minOf(
                if (isStory) 124f else 108f,
                rowHeight * 0.74f,
            )

            val rowTitlePaint = type.body(
                if (isStory) 40f else 36f,
                Color.WHITE,
            )

            val artistPaint = type.body(
                if (isStory) 32f else 29f,
                0xB8FFFFFF.toInt(),
            )

            previewSongs.forEachIndexed { index, song ->

                val rowTop = rowsTop + index * rowHeight

                val artTop = rowTop +
                    (rowHeight - trackArtSize) / 2f

                drawArtwork(
                    canvas,
                    song.thumbnailUrl?.let(images::get),
                    song.title,
                    frame.left + pad,
                    artTop,
                    trackArtSize,
                    circular = false,
                )

                val textX = frame.left +
                    pad +
                    trackArtSize +
                    28f

                val room = (
                    frame.right - pad - textX
                    ).coerceAtLeast(0f)

                val titleBaseline = artTop + trackArtSize * 0.42f

                canvas.drawText(
                    ellipsised(song.title, rowTitlePaint, room),
                    textX,
                    titleBaseline,
                    rowTitlePaint,
                )

                canvas.drawText(
                    ellipsised(song.artist, artistPaint, room),
                    textX,
                    titleBaseline + if (isStory) 46f else 42f,
                    artistPaint,
                )
            }
        }

        // Sonora branding.
        if (showBranding) {

            val credit = type.body(
                if (isStory) 32f else 29f,
                0xB8FFFFFF.toInt(),
            ).apply {
                textAlign = Paint.Align.CENTER
            }

            canvas.drawText(
                "Shared from Sonora",
                (frame.left + frame.right) / 2f,
                frame.bottom - 44f,
                credit,
            )
        }

        bitmap
    }
}

/**
 * Dark outer mat with a cover-tinted gradient panel.
 */
private fun drawPlaylistFrame(
    canvas: Canvas,
    width: Int,
    height: Int,
    fadeTop: Int,
    fadeMiddle: Int,
    fadeBottom: Int,
    border: Int,
): RectF {

    canvas.drawColor(0xFF202124.toInt())

    val outer = RectF(
        30f,
        40f,
        width - 30f,
        height - 40f,
    )

    canvas.drawRoundRect(
        outer,
        56f,
        56f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0A0A0A.toInt()
        },
    )

    val panel = RectF(
        70f,
        80f,
        width - 70f,
        height - 80f,
    )

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {

        shader = LinearGradient(
            panel.left,
            panel.top,
            panel.left,
            panel.bottom,
            intArrayOf(
                fadeTop,
                fadeMiddle,
                fadeBottom,
            ),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    canvas.drawRoundRect(
        panel,
        34f,
        34f,
        fill,
    )

    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = border
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    canvas.drawRoundRect(
        panel,
        34f,
        34f,
        stroke,
    )

    return panel
}

/**
 * Wrap a long playlist name to two lines.
 */
private fun wrapPosterTitle(
    text: String,
    paint: Paint,
    width: Float,
): List<String> {

    val words = text.trim()
        .split(Regex("\\s+"))
        .filter(String::isNotEmpty)

    if (words.isEmpty()) return listOf("")

    val lines = mutableListOf<String>()

    var current = ""
    var index = 0

    while (index < words.size && lines.size < 2) {

        val candidate = if (current.isEmpty()) {
            words[index]
        } else {
            "$current ${words[index]}"
        }

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

    if (current.isNotEmpty() && lines.size < 2) {
        lines += current
    }

    if (index < words.size && lines.isNotEmpty()) {

        val remainder = (
            listOf(lines.last()) + words.drop(index)
            ).joinToString(" ")

        lines[lines.lastIndex] = ellipsised(
            remainder,
            paint,
            width,
        )
    }

    return lines.ifEmpty {
        listOf(ellipsised(text, paint, width))
    }
}

/**
 * Wrap description to a maximum number of lines.
 */
private fun wrapPosterDescription(
    text: String,
    paint: Paint,
    width: Float,
    maxLines: Int,
): List<String> {

    val words = text.replace(Regex("\\s+"), " ")
        .trim()
        .split(' ')
        .filter(String::isNotEmpty)

    if (words.isEmpty() || maxLines <= 0 || width <= 0f) {
        return emptyList()
    }

    val lines = mutableListOf<String>()

    var current = ""
    var index = 0

    while (index < words.size && lines.size < maxLines) {

        val candidate = if (current.isEmpty()) {
            words[index]
        } else {
            "$current ${words[index]}"
        }

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

    if (current.isNotEmpty() && lines.size < maxLines) {
        lines += current
    }

    if (index < words.size && lines.isNotEmpty()) {

        val remainder = (
            listOf(lines.last()) + words.drop(index)
            ).joinToString(" ")

        lines[lines.lastIndex] = ellipsised(
            remainder,
            paint,
            width,
        )
    }

    return lines
}

/**
 * Keep human owner text; discard playlist labels and stale metadata.
 */
private fun cleanPlaylistOwner(subtitle: String): String = subtitle.trim()
    .replace(Regex("(?i)^playlist\\s+by\\s+"), "")
    .split(Regex("\\s*[·•|]\\s*"))
    .map { it.trim() }
    .filterNot { part ->
        part.isBlank() ||
            part.equals("playlist", ignoreCase = true) ||
            part.equals("album", ignoreCase = true) ||
            part.matches(Regex("\\d{4}")) ||
            part.matches(
                Regex(
                    "(?i)\\d[\\d,.]*\\+?\\s*(?:tracks?|songs?|views?|hours?|mins?|minutes?)\\b.*"
                )
            )
    }
    .joinToString(" · ")

private const val POSTER_WIDTH = 1080
private const val PORTRAIT_HEIGHT = 1350
private const val STORY_HEIGHT = 1920

private const val PORTRAIT_TRACK_PREVIEW_COUNT = 5
private const val STORY_TRACK_PREVIEW_COUNT = 7

private const val TITLE_LINE_SPACING = 1.04f

private const val IMAGE_LOAD_CONCURRENCY = 3

private const val MIN_HEADER_TEXT_WIDTH = 250f
private const val MIN_OWNER_TEXT_WIDTH = 100f
private const val MIN_TRACK_ROW_HEIGHT = 48f

private const val DEFAULT_SURFACE = 0xFFAA0000.toInt()
