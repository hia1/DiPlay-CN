package com.shilapi.xcertplay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.shilapi.xcertplay.airplay.ClusterTurnCardOverlay
import com.shilapi.xcertplay.host.R
import kotlin.math.min

/** A proportional placement sketch for the two saved turn-card positions. */
internal class ClusterCardPlacementPreview(context: Context) : View(context) {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(160, 171, 193)
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density
    }
    private val full = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(51, 139, 255) }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 179, 71) }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 13f * resources.displayMetrics.scaledDensity
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val margin = 14f * density
        val legendHeight = 28f * density
        val scale = min((width - margin * 2) / 1920f,
            (height - margin * 2 - legendHeight) / 720f).coerceAtLeast(0f)
        if (scale <= 0f) return
        val panelWidth = 1920f * scale
        val panelHeight = 720f * scale
        val left = (width - panelWidth) / 2f
        val top = margin + legendHeight
        canvas.drawRoundRect(RectF(left, top, left + panelWidth, top + panelHeight),
            7f * density, 7f * density, stroke)
        fun card(x: Int, y: Int, size: Int, paint: Paint) {
            val rect = ClusterTurnCardOverlay.card(1920, 720, x, y, size)
            canvas.drawRoundRect(RectF(left + rect.left * scale, top + rect.top * scale,
                left + (rect.left + rect.width) * scale, top + (rect.top + rect.height) * scale),
                5f * density, 5f * density, paint)
        }
        card(AirPlayPersistence.loadClusterTurnCardOverlayXPercent(context),
            AirPlayPersistence.loadClusterTurnCardOverlayYPercent(context),
            AirPlayPersistence.loadClusterTurnCardOverlaySizePercent(context), full)
        if (AirPlayPersistence.loadClusterSmallWindowMode(context) != 0) {
            card(AirPlayPersistence.loadClusterSmallWindowCardXPercent(context),
                AirPlayPersistence.loadClusterSmallWindowCardYPercent(context),
                AirPlayPersistence.loadClusterSmallWindowCardSizePercent(context), small)
        }
        canvas.drawCircle(left + 6f * density, margin + 10f * density, 4f * density, full)
        canvas.drawText(context.getString(R.string.card_preview_full), left + 16f * density,
            margin + 14f * density, label)
        if (AirPlayPersistence.loadClusterSmallWindowMode(context) != 0) {
            val smallLeft = left + panelWidth * 0.48f
            canvas.drawCircle(smallLeft, margin + 10f * density, 4f * density, small)
            canvas.drawText(context.getString(R.string.card_preview_small), smallLeft + 10f * density,
                margin + 14f * density, label)
        }
    }
}
