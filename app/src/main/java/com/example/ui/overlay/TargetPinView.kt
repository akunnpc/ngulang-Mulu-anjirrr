package com.example.ui.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

/**
 * Draggable floating pin targeting point (Circle + Crosshair + Number)
 */
class TargetPinView(
    context: Context,
    val stepNumber: Int,
    var targetId: Long,
    private val onPositionChanged: (TargetPinView, Float, Float) -> Unit,
    private val onPinClicked: (TargetPinView) -> Unit
) : FrameLayout(context) {

    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var isDragging = false
    private var isTouchEnabled = true

    val layoutParamsRef: WindowManager.LayoutParams

    fun setTouchable(touchable: Boolean, wm: WindowManager) {
        isTouchEnabled = touchable
        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        layoutParamsRef.flags = if (touchable) {
            baseFlags
        } else {
            baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        alpha = 1.0f
        try {
            wm.updateViewLayout(this, layoutParamsRef)
        } catch (e: Exception) {
            Log.e("TargetPinView", "Failed to update touchable state", e)
        }
    }

    init {
        val sizePx = (48 * context.resources.displayMetrics.density).toInt()
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParamsRef = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        // Add custom rendered pin graphics
        val pinDrawable = PinDrawableView(context, stepNumber)
        addView(pinDrawable, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        setOnTouchListener { _, event ->
            if (!isTouchEnabled) {
                return@setOnTouchListener false
            }
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParamsRef.x
                    initialY = layoutParamsRef.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (Math.hypot(dx.toDouble(), dy.toDouble()) > 8) {
                        isDragging = true
                    }
                    layoutParamsRef.x = (initialX + dx).toInt()
                    layoutParamsRef.y = (initialY + dy).toInt()
                    wm.updateViewLayout(this, layoutParamsRef)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        onPinClicked(this)
                    } else {
                        val w = if (width > 0) width.toFloat() else layoutParamsRef.width.toFloat()
                        val h = if (height > 0) height.toFloat() else layoutParamsRef.height.toFloat()
                        val centerX = layoutParamsRef.x + w / 2f
                        val centerY = layoutParamsRef.y + h / 2f
                        onPositionChanged(this, centerX, centerY)
                    }
                    true
                }
                else -> false
            }
        }
    }
}

class PinDrawableView(context: Context, private val stepNumber: Int) : View(context) {
    private val circlePaint = Paint().apply {
        color = Color.parseColor("#EE121722") // Dark Obsidian
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val ringPaint = Paint().apply {
        color = Color.parseColor("#00E5FF") // Neon Cyan
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    private val crosshairPaint = Paint().apply {
        color = Color.parseColor("#00E676") // Emerald
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 28f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        setShadowLayer(4f, 1f, 1f, Color.BLACK)
    }
    private val dotPaint = Paint().apply {
        color = Color.parseColor("#FFD700") // Gold center dot
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = (Math.min(width, height) / 2f) - 4f

        // Outer glow disc
        canvas.drawCircle(cx, cy, radius, circlePaint)
        canvas.drawCircle(cx, cy, radius, ringPaint)

        // Center crosshair lines
        canvas.drawLine(cx - radius * 0.45f, cy, cx + radius * 0.45f, cy, crosshairPaint)
        canvas.drawLine(cx, cy - radius * 0.45f, cx, cy + radius * 0.45f, crosshairPaint)

        // Step number label
        val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText("$stepNumber", cx, textY, textPaint)

        // Center precision dot
        canvas.drawCircle(cx, cy, 3.5f, dotPaint)
    }
}
