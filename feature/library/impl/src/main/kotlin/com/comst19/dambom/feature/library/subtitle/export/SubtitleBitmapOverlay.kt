@file:Suppress("MagicNumber")

package com.comst19.dambom.feature.library.subtitle.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings
import com.comst19.dambom.feature.library.subtitle.model.SubtitleCue
import com.comst19.dambom.feature.library.subtitle.playback.SubtitleTimeline

@UnstableApi
internal class SubtitleBitmapOverlay(
    cues: List<SubtitleCue>,
) : BitmapOverlay() {
    private val timeline = SubtitleTimeline(cues)
    private val empty = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private var width = 1
    private var textSize = 1f
    private var previous: SubtitleCue? = null
    private var bitmap = empty
    private val settings =
        StaticOverlaySettings
            .Builder()
            .setBackgroundFrameAnchor(0f, -0.85f)
            .setOverlayFrameAnchor(0f, -1f)
            .build()

    override fun configure(videoSize: Size) {
        width = (videoSize.width * 0.9f).toInt().coerceAtLeast(1)
        textSize = minOf(videoSize.width, videoSize.height) * 0.055f
        previous = null
        bitmap = empty
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val cue = timeline.at(presentationTimeUs / 1_000)
        if (cue != previous) {
            previous = cue
            bitmap = cue?.let { render(it.text) } ?: empty
        }
        return bitmap
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = settings

    private fun render(text: String): Bitmap {
        val padding = (textSize * 0.4f).toInt().coerceAtLeast(1)
        val paint =
            TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = this@SubtitleBitmapOverlay.textSize
            }
        val layout =
            StaticLayout.Builder
                .obtain(text, 0, text.length, paint, (width - padding * 2).coerceAtLeast(1))
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .build()
        return Bitmap.createBitmap(width, layout.height + padding * 2, Bitmap.Config.ARGB_8888).also { image ->
            val canvas = Canvas(image)
            canvas.drawColor(Color.argb(190, 0, 0, 0))
            canvas.translate(padding.toFloat(), padding.toFloat())
            layout.draw(canvas)
        }
    }
}
