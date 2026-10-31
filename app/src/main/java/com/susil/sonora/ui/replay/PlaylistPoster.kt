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

/** Data needed to make a stable, shareable picture of a playlist. */
internal data class PlaylistShareRequest(
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    val songs: List<Song>,
    val playlistUrl: String?,
)

/**
 * A portrait playlist poster, not a screenshot of the scrolling page.
 *
 * The square sleeve leads, followed by the playlist name, creator/count and a
 * small track preview. It is composed from the playlist's own metadata and the
 * same artwork helpers as Lyrics and Replay, so it has no phone chrome and can
 * be shared or saved at identical quality on every screen size.
 */
internal suspend fun renderPlaylistPoster(context: Context, request: PlaylistShareRequest): Bitmap {
    val previewSongs = request.songs.take(TRACK_PREVIEW_COUNT)
    val leadUrl = request.artworkUrl ?: previewSongs.firstOrNull()?.thumbnailUrl
    val imageUrls = (listOfNotNull(leadUrl) + previewSongs.mapNotNull { it.thumbnailUrl })
        .distinct()
    val images = coroutineScope {
        imageUrls.associateWith { url -> async(Dispatchers.IO) { loadBitmap(context, url) } }
            .mapValues { (_, deferred) -> deferred.await() }
    }

    return withContext(Dispatchers.Default) {
        val bitmap = Bitmap.createBitmap(POSTER_WIDTH, POSTER_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val type = Fonts(context)
        val lead = leadUrl?.let(images::get)
        drawBackdrop(canvas, lead, hue = 0f, height = POSTER_HEIGHT.toFloat())

        val brand = type.heading(38f, 0xE6FFFFFF.toInt()).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText(BRAND, RIGHT, 112f, brand)
        drawLogo(canvas, context, RIGHT - brand.measureText(BRAND) - LOGO_GAP, 112f)

        val label = type.label(27f, 0xB3FFFFFF.toInt(), tracking = 0.16f)
        canvas.drawText(
            context.getString(R.string.playlist_share_card_label).uppercase(java.util.Locale.ROOT),
            MARGIN,
            148f,
            label,
        )

        val heroSize = 600f
        val heroX = (POSTER_WIDTH - heroSize) / 2f
        drawArtwork(canvas, lead, request.title, heroX, 175f, heroSize, circular = false)

        val titlePaint = type.heading(66f, Color.WHITE)
        canvas.drawText(ellipsised(request.title, titlePaint, CONTENT_WIDTH), MARGIN, 900f, titlePaint)

        val songCount = context.resources.getQuantityString(
            R.plurals.replay_song_count,
            request.songs.size,
            request.songs.size.toString(),
        )
        val metadata = listOf(request.subtitle.trim(), songCount).filter { it.isNotBlank() }.joinToString(" · ")
        val metadataPaint = type.body(34f, 0xCCFFFFFF.toInt())
        canvas.drawText(ellipsised(metadata, metadataPaint, CONTENT_WIDTH), MARGIN, 955f, metadataPaint)

        val qr = request.playlistUrl?.let(::qrBitmap)
        val qrSize = 210f
        val qrX = RIGHT - qrSize
        val qrY = 1065f
        if (qr != null) {
            val frame = RectF(qrX, qrY, qrX + qrSize, qrY + qrSize)
            canvas.drawRoundRect(frame, 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            canvas.drawBitmap(
                qr,
                null,
                RectF(frame.left + 8f, frame.top + 8f, frame.right - 8f, frame.bottom - 8f),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
            val scan = type.label(20f, 0xD9FFFFFF.toInt(), tracking = 0.08f).apply {
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(context.getString(R.string.playlist_share_scan), qrX + qrSize / 2, 1298f, scan)
        }

        if (previewSongs.isNotEmpty()) {
            canvas.drawText(
                context.getString(R.string.playlist_share_track_preview).uppercase(java.util.Locale.ROOT),
                MARGIN,
                1015f,
                type.label(24f, 0x99FFFFFF.toInt(), tracking = 0.12f),
            )
            previewSongs.forEachIndexed { index, song ->
                val rowTop = 1030f + index * TRACK_ROW_HEIGHT
                drawArtwork(
                    canvas,
                    song.thumbnailUrl?.let(images::get),
                    song.title,
                    MARGIN,
                    rowTop,
                    TRACK_ART_SIZE,
                    circular = false,
                )
                val number = type.body(27f, 0x8CFFFFFF.toInt())
                canvas.drawText("%02d".format(index + 1), MARGIN + TRACK_ART_SIZE + 18f, rowTop + 34f, number)
                val textX = MARGIN + TRACK_ART_SIZE + 74f
                val textWidth = if (qr != null) qrX - QR_GAP - textX else RIGHT - textX
                val title = type.body(30f, Color.WHITE, bold = true)
                canvas.drawText(ellipsised(song.title, title, textWidth), textX, rowTop + 29f, title)
                val artist = type.body(24f, 0xB3FFFFFF.toInt())
                canvas.drawText(ellipsised(song.artist, artist, textWidth), textX, rowTop + 57f, artist)
            }
        }

        val footer = type.label(23f, 0x80FFFFFF.toInt(), tracking = 0.1f)
        canvas.drawText(context.getString(R.string.playlist_share_footer), MARGIN, 1330f, footer)
        bitmap
    }
}

/** A high-contrast, quiet-zone QR that remains scannable after social resizing. */
private fun qrBitmap(content: String): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        QR_SIZE_PX,
        QR_SIZE_PX,
        mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 4,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        ),
    )
    val bitmap = Bitmap.createBitmap(QR_SIZE_PX, QR_SIZE_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    val cell = QR_SIZE_PX.toFloat() / matrix.width
    for (y in 0 until matrix.height) {
        for (x in 0 until matrix.width) {
            if (matrix.get(x, y)) {
                canvas.drawRect(x * cell, y * cell, (x + 1) * cell, (y + 1) * cell, paint)
            }
        }
    }
    bitmap
}.getOrNull()

private const val POSTER_WIDTH = 1080
private const val POSTER_HEIGHT = 1350
private const val MARGIN = 72f
private const val RIGHT = POSTER_WIDTH - MARGIN
private const val CONTENT_WIDTH = POSTER_WIDTH - MARGIN * 2f
private const val BRAND = "Sonora"
private const val LOGO_GAP = 18f
private const val TRACK_PREVIEW_COUNT = 3
private const val TRACK_ART_SIZE = 52f
private const val TRACK_ROW_HEIGHT = 82f
private const val QR_SIZE_PX = 280
private const val QR_GAP = 24f
