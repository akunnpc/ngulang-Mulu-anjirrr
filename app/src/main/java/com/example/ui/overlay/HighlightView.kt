package com.example.ui.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.View

class HighlightView(context: Context) : View(context) {
    private val borderPaint = Paint().apply {
        color = Color.parseColor("#00E676") // Neon green
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val fillPaint = Paint().apply {
        color = Color.parseColor("#1A00E676") // Translucent green fill
        style = Paint.Style.FILL
    }

    private val ripplePaint = Paint().apply {
        color = Color.parseColor("#00B0FF") // Bright cyan/blue ripple
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 28f
        isFakeBoldText = true
        isAntiAlias = true
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }

    private val textBgPaint = Paint().apply {
        color = Color.parseColor("#AA121212") // Dark background for readability
        style = Paint.Style.FILL
    }

    private var activeRect: Rect? = null
    private var activeX: Float = 0f
    private var activeY: Float = 0f
    private var targetName: String = ""
    private var animProgress = 0f // 1.0f down to 0.0f

    private val handlerRef = Handler(Looper.getMainLooper())
    private var animRunnable: Runnable? = null

    fun showHighlight(rect: Rect, x: Float, y: Float, name: String) {
        // Run on UI main thread
        handlerRef.post {
            activeRect = rect
            activeX = x
            activeY = y
            targetName = name
            animProgress = 1.0f

            invalidate()

            // Cancel previous animation runnable
            animRunnable?.let { handlerRef.removeCallbacks(it) }

            // Start fade out animation loop
            animRunnable = object : Runnable {
                override fun run() {
                    animProgress -= 0.08f // speed of fade out
                    if (animProgress > 0f) {
                        invalidate()
                        handlerRef.postDelayed(this, 30)
                    } else {
                        activeRect = null
                        invalidate()
                    }
                }
            }
            handlerRef.post(animRunnable!!)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val rect = activeRect ?: return

        // Draw highlighting border and translucent fill with alpha based on animProgress
        borderPaint.alpha = (animProgress * 255).toInt()
        fillPaint.alpha = (animProgress * 30).toInt()
        canvas.drawRect(rect, fillPaint)
        canvas.drawRect(rect, borderPaint)

        // Draw target step name label on top of the matched bounding box
        val label = targetName
        val textWidth = textPaint.measureText(label)
        val textHeight = textPaint.textSize
        val bgPadding = 12f
        val bgLeft = rect.left.toFloat()
        val bgTop = (rect.top - textHeight - bgPadding * 2).coerceAtLeast(0f)
        textBgPaint.alpha = (animProgress * 180).toInt()
        canvas.drawRect(bgLeft, bgTop, bgLeft + textWidth + bgPadding * 2, bgTop + textHeight + bgPadding * 2, textBgPaint)

        textPaint.alpha = (animProgress * 255).toInt()
        canvas.drawText(label, bgLeft + bgPadding, bgTop + textHeight + bgPadding - 4f, textPaint)

        // Draw a stylish contracting and expanding tap ripple circle centered at the click coordinates
        // Contracting circle (Target lock)
        val outerRadius = 80f * animProgress
        ripplePaint.strokeWidth = 6f * animProgress
        ripplePaint.color = Color.parseColor("#00E676") // Neon Green
        ripplePaint.alpha = (animProgress * 255).toInt()
        canvas.drawCircle(activeX, activeY, outerRadius, ripplePaint)

        // Expanding translucent ripple circle
        val innerRadius = 30f + 70f * (1f - animProgress)
        ripplePaint.strokeWidth = 3f
        ripplePaint.color = Color.parseColor("#00B0FF") // Cyan
        ripplePaint.alpha = (animProgress * 150).toInt()
        canvas.drawCircle(activeX, activeY, innerRadius, ripplePaint)
    }
}
