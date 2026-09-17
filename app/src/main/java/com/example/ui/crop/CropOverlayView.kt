package com.example.ui.crop

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * View transparan yang ditumpuk di atas ImageView foto full.
 * Tugasnya cuma gambar kotak crop yang bisa digeser & di-resize pakai jari,
 * lalu ngasih tau posisi kotak itu dalam bentuk PERSEN (0f..1f) relatif
 * terhadap ukuran View ini sendiri — bukan piksel absolut.
 *
 * Kenapa persen? Karena View di layar ukurannya beda-beda tergantung device,
 * sedangkan bitmap asli resolusinya beda lagi. Persen ini yang nanti dipakai
 * buat hitung crop di bitmap asli.
 */
class CropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // Kotak crop dalam koordinat PIKSEL LAYAR (bukan persen) — dipakai buat gambar & deteksi sentuhan.
    // Nilai awal: kotak di tengah, ukurannya 60% dari lebar/tinggi View.
    private val cropRect = RectF()

    private var viewInitialized = false

    // Ukuran minimum kotak crop dalam dp, biar gak bisa di-resize kekecilan sampai gak kelihatan
    private val minSizePx = 40f * resources.displayMetrics.density

    // Radius area "pegangan" sudut, biar jari gak perlu presisi banget nyentuh pojok
    private val handleTouchRadius = 40f * resources.displayMetrics.density

    private val dimPaint = Paint().apply {
        color = Color.parseColor("#AA000000") // gelapin area di luar kotak crop
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint().apply {
        color = Color.parseColor("#00E5FF") // cyan cerah
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
    }

    private val handlePaint = Paint().apply {
        color = Color.parseColor("#00E5FF")
        style = Paint.Style.FILL
    }

    private val handleStrokePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * resources.displayMetrics.density
    }

    private val handleRadiusPx = 8f * resources.displayMetrics.density

    // Mode drag yang lagi aktif: null kalau gak ada jari yang nyentuh kotak
    private var dragMode: DragMode? = null
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private enum class DragMode {
        MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!viewInitialized && w > 0 && h > 0) {
            // Set posisi awal kotak crop: tengah, 60% ukuran View
            val marginX = w * 0.2f
            val marginY = h * 0.2f
            cropRect.set(marginX, marginY, w - marginX, h - marginY)
            viewInitialized = true
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Gambar 4 persegi gelap di luar cropRect (atas, bawah, kiri, kanan)
        canvas.drawRect(0f, 0f, width.toFloat(), cropRect.top, dimPaint)
        canvas.drawRect(0f, cropRect.bottom, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, dimPaint)
        canvas.drawRect(cropRect.right, cropRect.top, width.toFloat(), cropRect.bottom, dimPaint)

        // Border kotak crop
        canvas.drawRect(cropRect, borderPaint)

        // 4 titik pegangan di pojok (dengan aksen stroke putih rapi)
        drawHandle(canvas, cropRect.left, cropRect.top)
        drawHandle(canvas, cropRect.right, cropRect.top)
        drawHandle(canvas, cropRect.left, cropRect.bottom)
        drawHandle(canvas, cropRect.right, cropRect.bottom)
    }

    private fun drawHandle(canvas: Canvas, cx: Float, cy: Float) {
        canvas.drawCircle(cx, cy, handleRadiusPx, handlePaint)
        canvas.drawCircle(cx, cy, handleRadiusPx, handleStrokePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragMode = detectDragMode(event.x, event.y)
                lastTouchX = event.x
                lastTouchY = event.y
                return dragMode != null
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                lastTouchX = event.x
                lastTouchY = event.y

                when (dragMode) {
                    DragMode.MOVE -> moveRect(dx, dy)
                    DragMode.TOP_LEFT -> resizeRect(dx, dy, resizeLeft = true, resizeTop = true)
                    DragMode.TOP_RIGHT -> resizeRect(dx, dy, resizeRight = true, resizeTop = true)
                    DragMode.BOTTOM_LEFT -> resizeRect(dx, dy, resizeLeft = true, resizeBottom = true)
                    DragMode.BOTTOM_RIGHT -> resizeRect(dx, dy, resizeRight = true, resizeBottom = true)
                    null -> return false
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragMode = null
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /** Cek jari nyentuh pegangan pojok yang mana, atau area tengah kotak (buat geser), atau di luar (null). */
    private fun detectDragMode(x: Float, y: Float): DragMode? {
        fun near(px: Float, py: Float) = abs(x - px) <= handleTouchRadius && abs(y - py) <= handleTouchRadius

        return when {
            near(cropRect.left, cropRect.top) -> DragMode.TOP_LEFT
            near(cropRect.right, cropRect.top) -> DragMode.TOP_RIGHT
            near(cropRect.left, cropRect.bottom) -> DragMode.BOTTOM_LEFT
            near(cropRect.right, cropRect.bottom) -> DragMode.BOTTOM_RIGHT
            cropRect.contains(x, y) -> DragMode.MOVE
            else -> null
        }
    }

    private fun moveRect(dx: Float, dy: Float) {
        var newLeft = cropRect.left + dx
        var newRight = cropRect.right + dx
        var newTop = cropRect.top + dy
        var newBottom = cropRect.bottom + dy

        // Jangan sampai kotak keluar dari batas View
        if (newLeft < 0f) { newRight -= newLeft; newLeft = 0f }
        if (newTop < 0f) { newBottom -= newTop; newTop = 0f }
        if (newRight > width) { newLeft -= (newRight - width); newRight = width.toFloat() }
        if (newBottom > height) { newTop -= (newBottom - height); newBottom = height.toFloat() }

        cropRect.set(newLeft, newTop, newRight, newBottom)
    }

    private fun resizeRect(
        dx: Float, dy: Float,
        resizeLeft: Boolean = false,
        resizeRight: Boolean = false,
        resizeTop: Boolean = false,
        resizeBottom: Boolean = false
    ) {
        var left = cropRect.left
        var right = cropRect.right
        var top = cropRect.top
        var bottom = cropRect.bottom

        if (resizeLeft) left = (left + dx).coerceIn(0f, right - minSizePx)
        if (resizeRight) right = (right + dx).coerceIn(left + minSizePx, width.toFloat())
        if (resizeTop) top = (top + dy).coerceIn(0f, bottom - minSizePx)
        if (resizeBottom) bottom = (bottom + dy).coerceIn(top + minSizePx, height.toFloat())

        cropRect.set(left, top, right, bottom)
    }

    /**
     * Set kotak kembali ke tengah
     */
    fun resetCenter() {
        if (width > 0 && height > 0) {
            val marginX = width * 0.2f
            val marginY = height * 0.2f
            cropRect.set(marginX, marginY, width - marginX, height - marginY)
            invalidate()
        }
    }

    /**
     * Set kotak crop memenuhi seluruh view
     */
    fun setFull() {
        if (width > 0 && height > 0) {
            cropRect.set(0f, 0f, width.toFloat(), height.toFloat())
            invalidate()
        }
    }

    /**
     * Ambil posisi kotak crop sekarang, dikonversi ke PERSEN (0f..1f) relatif ukuran View.
     * Ini yang dipanggil pas user tap tombol "Lanjut".
     */
    fun getCropPercent(): CropPercent {
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        return CropPercent(
            left = (cropRect.left / w).coerceIn(0f, 1f),
            top = (cropRect.top / h).coerceIn(0f, 1f),
            right = (cropRect.right / w).coerceIn(0f, 1f),
            bottom = (cropRect.bottom / h).coerceIn(0f, 1f)
        )
    }

    data class CropPercent(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )
}
