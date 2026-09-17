package com.example.ui.editor

import android.graphics.Bitmap
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.crop.CropOverlayView
import com.example.ui.theme.*

/**
 * Pemotongan gambar target menggunakan arsitektur CropOverlayView di atas ImageView FIT_CENTER.
 * Kotak crop digeser & di-resize dengan jari, posisi dihitung dalam persen lalu dikonversi
 * ke piksel bitmap asli secara presisi.
 */
@Composable
fun ImageCropperContent(
    bitmap: Bitmap,
    onCropConfirmed: (Bitmap) -> Unit,
    onCropFailed: () -> Unit
) {
    var overlayRef by remember { mutableStateOf<CropOverlayView?>(null) }
    var imageRef by remember { mutableStateOf<ImageView?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF14171F))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Judul header
        Text(
            text = "Geser & Sesuaikan Kotak ke Bagian Target",
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
        )

        // Container tempat meletakkan ImageView + CropOverlayView di atasnya
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0D0F14))
                .border(1.dp, BorderSlate.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    val frameLayout = FrameLayout(context).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    }

                    val imageView = ImageView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setImageBitmap(bitmap)
                    }
                    imageRef = imageView
                    frameLayout.addView(imageView)

                    val cropOverlayView = CropOverlayView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    }
                    overlayRef = cropOverlayView
                    frameLayout.addView(cropOverlayView)

                    frameLayout
                },
                update = {
                    imageRef?.setImageBitmap(bitmap)
                }
            )
        }

        // Tombol Cepat: Pusatkan & Penuh
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { overlayRef?.resetCenter() },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSlate)
                ) {
                    Icon(
                        Icons.Default.CenterFocusStrong,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = CyberCyan
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pusatkan", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = { overlayRef?.setFull() },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSlate)
                ) {
                    Icon(
                        Icons.Default.Fullscreen,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = CyberCyan
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Penuh", fontSize = 12.sp)
                }
            }
        }

        // Tombol LANJUT
        Button(
            onClick = {
                val overlay = overlayRef
                val img = imageRef
                if (overlay != null && img != null && img.width > 0 && img.height > 0) {
                    val percent = overlay.getCropPercent()
                    val cropped = performCrop(bitmap, percent, img.width.toFloat(), img.height.toFloat())
                    if (cropped != null) {
                        onCropConfirmed(cropped)
                    } else {
                        onCropFailed()
                    }
                } else {
                    onCropFailed()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1E232A),
                contentColor = Color.White
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.7f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                text = "LANJUT",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                letterSpacing = 1.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Default.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = CyberCyan
            )
        }
    }
}

/**
 * Konversi persentase kotak crop dari CropOverlayView ke piksel asli Bitmap
 * dengan memperhitungkan FIT_CENTER dan letterboxing.
 */
private fun performCrop(
    bitmap: Bitmap,
    percent: CropOverlayView.CropPercent,
    viewWidth: Float,
    viewHeight: Float
): Bitmap? {
    val bitmapWidth = bitmap.width.toFloat()
    val bitmapHeight = bitmap.height.toFloat()

    // Hitung scale FIT_CENTER: gambar di-scale sekecil mungkin biar muat, sambil jaga rasio
    val scale = minOf(viewWidth / bitmapWidth, viewHeight / bitmapHeight)
    val displayedWidth = bitmapWidth * scale
    val displayedHeight = bitmapHeight * scale

    // Area kosong (letterbox) di kiri-kanan atau atas-bawah akibat FIT_CENTER
    val offsetX = (viewWidth - displayedWidth) / 2f
    val offsetY = (viewHeight - displayedHeight) / 2f

    // Konversi titik crop dari "posisi di View" ke "posisi di gambar yang ditampilkan"
    // lalu ke "posisi di piksel bitmap asli"
    fun viewPercentToBitmapPx(
        viewPercent: Float,
        viewSize: Float,
        offset: Float,
        displayedSize: Float,
        bitmapSize: Float
    ): Float {
        val posInView = viewPercent * viewSize
        val posInDisplayedImage = posInView - offset
        val posPercentInImage = (posInDisplayedImage / displayedSize).coerceIn(0f, 1f)
        return posPercentInImage * bitmapSize
    }

    val left = viewPercentToBitmapPx(percent.left, viewWidth, offsetX, displayedWidth, bitmapWidth)
    val top = viewPercentToBitmapPx(percent.top, viewHeight, offsetY, displayedHeight, bitmapHeight)
    val right = viewPercentToBitmapPx(percent.right, viewWidth, offsetX, displayedWidth, bitmapWidth)
    val bottom = viewPercentToBitmapPx(percent.bottom, viewHeight, offsetY, displayedHeight, bitmapHeight)

    val cropX = left.toInt().coerceIn(0, bitmap.width - 1)
    val cropY = top.toInt().coerceIn(0, bitmap.height - 1)
    val cropW = (right - left).toInt().coerceIn(1, bitmap.width - cropX)
    val cropH = (bottom - top).toInt().coerceIn(1, bitmap.height - cropY)

    return try {
        Bitmap.createBitmap(bitmap, cropX, cropY, cropW, cropH)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
