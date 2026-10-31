package com.susil.sonora.ui.replay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.susil.sonora.R
import com.susil.sonora.data.model.Song
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
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
    val subtitle: String,
    val description: String?,
    val creator: String?,
    val artworkUrl: String?,
    val songs: List<Song>,
    val playlistUrl: String?,
)

/** A polished social card with metadata and a compact tracklist, not a page screenshot. */
internal suspend fun renderPlaylistPoster(
    context: Context,
    request: PlaylistShareRequest,
    ratio: PlaylistPosterRatio = PlaylistPosterRatio.Portrait,
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

        val panel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x35000000 }
        canvas.drawRoundRect(RectF(MARGIN, 74f, width - MARGIN, height - 84f), 34f, 34f, panel)

        val heroSize = if (ratio == PlaylistPosterRatio.Story) 560f else 470f
        val heroTop = if (ratio == PlaylistPosterRatio.Story) 126f else 112f
        drawArtwork(canvas, lead, request.title, (width - heroSize) / 2f, heroTop, heroSize, circular = false)

        var y = heroTop + heroSize + if (ratio == PlaylistPosterRatio.Story) 62f else 48f
        val title = type.heading(if (ratio == PlaylistPosterRatio.Story) 70f else 62f, Color.WHITE)
        canvas.drawText(ellipsised(request.title, title, CONTENT_WIDTH), MARGIN + 34f, y, title)
        y += if (ratio == PlaylistPosterRatio.Story) 58f else 52f

        val creator = listOfNotNull(
            request.creator?.takeIf { it.isNotBlank() },
            request.subtitle.takeIf { it.isNotBlank() },
        ).firstOrNull()
        if (creator != null) {
            val byline = type.body(30f, 0xE0FFFFFF.toInt(), bold = true)
            canvas.drawText(ellipsised("Made by $creator", byline, CONTENT_WIDTH - 68f), MARGIN + 34f, y, byline)
            y += 44f
        }

        request.description?.trim()?.takeIf { it.isNotBlank() }?.let { description ->
            val desc = type.body(27f, 0xC8FFFFFF.toInt())
            val lines = wrap(description, desc, CONTENT_WIDTH - 68f, 2)
            lines.forEach { line ->
                canvas.drawText(line, MARGIN + 34f, y, desc)
                y += 36f
            }
            y += 10f
        }

        val count = context.resources.getQuantityString(
            R.plurals.replay_song_count,
            request.songs.size,
            request.songs.size.toString(),
        )
        val countPaint = type.label(24f, 0xB8FFFFFF.toInt(), tracking = 0.06f)
        canvas.drawText(count.uppercase(java.util.Locale.ROOT), MARGIN + 34f, y, countPaint)
        y += 42f

        val trackLabel = type.label(23f, 0xA8FFFFFF.toInt(), tracking = 0.12f)
        canvas.drawText(context.getString(R.string.playlist_share_track_preview).uppercase(java.util.Locale.ROOT), MARGIN + 34f, y, trackLabel)
        y += 22f

        val qr = request.playlistUrl?.let(::qrBitmap)
        val maxRowsBottom = height - 142f
        val rowHeight = if (ratio == PlaylistPosterRatio.Story) 98f else 82f
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

        // The footer is intentionally only the logo and the Sonora name — no "made with" text.
        val footer = type.heading(30f, 0xE6FFFFFF.toInt()).apply { textAlign = Paint.Align.RIGHT }
        val footerY = height - 58f
        canvas.drawText(BRAND, width - MARGIN - 34f, footerY, footer)
        drawLogo(canvas, context, width - MARGIN - 34f - footer.measureText(BRAND) - LOGO_GAP, footerY)
        bitmap
    }
}

private fun wrap(text: String, paint: Paint, width: Float, maxLines: Int): List<String> {
    val words = text.replace("\n", " ").trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    val lines = mutableListOf<String>()
    var line = ""
    for (word in words) {
        val candidate = if (line.isEmpty()) word else "$line $word"
        if (paint.measureText(candidate) <= width) line = candidate
        else if (line.isNotEmpty()) { lines += line; line = word }
        else line = word
        if (lines.size == maxLines) break
    }
    if (lines.size < maxLines && line.isNotEmpty()) lines += line
    if (lines.size == maxLines && words.joinToString(" ") != lines.joinToString(" ")) {
        lines[lines.lastIndex] = ellipsised(lines.last() + "…", paint, width)
    }
    return lines
}

/** A high-contrast, quiet-zone QR that remains scannable after social resizing. */
private fun qrBitmap(content: String): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, QR_SIZE_PX, QR_SIZE_PX, mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 4,
        EncodeHintType.CHARACTER_SET to "UTF-8",
    ))
    val bitmap = Bitmap.createBitmap(QR_SIZE_PX, QR_SIZE_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    val cell = QR_SIZE_PX.toFloat() / matrix.width
    for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
        if (matrix.get(x, y)) canvas.drawRect(x * cell, y * cell, (x + 1) * cell, (y + 1) * cell, paint)
    }
    bitmap
}.getOrNull()

private const val POSTER_WIDTH = 1080
private const val PORTRAIT_HEIGHT = 1350
private const val STORY_HEIGHT = 1920
private const val MARGIN = 52f
private const val CONTENT_WIDTH = POSTER_WIDTH - MARGIN * 2f - 68f
private const val BRAND = "Sonora"
private const val LOGO_GAP = 14f
private const val TRACK_PREVIEW_COUNT = 4
private const val TRACK_ART_SIZE = 58f
private const val QR_SIZE_PX = 280
