package com.example.ui.diagnostic

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.TapAccessibilityService
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun TouchCanvasArenaTab(
    initialX: Float,
    initialY: Float,
    onLog: (type: String, coords: String, success: Boolean, latencyMs: Long, message: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var canvasSelectedX by remember { mutableFloatStateOf(initialX) }
    var canvasSelectedY by remember { mutableFloatStateOf(initialY) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Sentuh area kanvas untuk menentukan titik:",
                fontSize = 11.sp,
                color = TextMuted
            )
            Text(
                text = "(${canvasSelectedX.toInt()}px, ${canvasSelectedY.toInt()}px)",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CyberCyan
            )
        }

        // Interactive Canvas Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(ConsoleBg)
                .border(1.dp, BorderSlate, RoundedCornerShape(16.dp))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        canvasSelectedX = offset.x
                        canvasSelectedY = offset.y
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                // Subtle grid lines
                val gridStep = 40.dp.toPx()
                var gx = 0f
                while (gx < canvasWidth) {
                    drawLine(
                        color = Color(0xFF1E293B),
                        start = Offset(gx, 0f),
                        end = Offset(gx, canvasHeight),
                        strokeWidth = 1f
                    )
                    gx += gridStep
                }
                var gy = 0f
                while (gy < canvasHeight) {
                    drawLine(
                        color = Color(0xFF1E293B),
                        start = Offset(0f, gy),
                        end = Offset(canvasWidth, gy),
                        strokeWidth = 1f
                    )
                    gy += gridStep
                }

                // Selected Point Crosshair
                val cx = canvasSelectedX.coerceIn(0f, canvasWidth)
                val cy = canvasSelectedY.coerceIn(0f, canvasHeight)

                drawLine(
                    color = CyberCyan.copy(alpha = 0.5f),
                    start = Offset(cx, 0f),
                    end = Offset(cx, canvasHeight),
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
                drawLine(
                    color = CyberCyan.copy(alpha = 0.5f),
                    start = Offset(0f, cy),
                    end = Offset(canvasWidth, cy),
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )

                // Outer ring
                drawCircle(
                    color = CyberCyan.copy(alpha = 0.3f),
                    radius = 24.dp.toPx(),
                    center = Offset(cx, cy)
                )
                // Inner target point
                drawCircle(
                    color = EmeraldReady,
                    radius = 6.dp.toPx(),
                    center = Offset(cx, cy)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(SlateCardBg.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    "Tap di mana saja pada kotak ini",
                    fontSize = 9.sp,
                    color = TextMuted
                )
            }
        }

        // Trigger Tap at Canvas Coordinate
        Button(
            onClick = {
                if (!TapAccessibilityService.isRunning()) {
                    Toast.makeText(context, "Layanan Aksesibilitas belum aktif!", Toast.LENGTH_SHORT).show()
                    onLog("CANVAS_TAP", "(${canvasSelectedX.toInt()}, ${canvasSelectedY.toInt()})", false, 0L, "Layanan Aksesibilitas belum berjalan")
                    return@Button
                }

                coroutineScope.launch {
                    val startTime = System.currentTimeMillis()
                    val success = TapAccessibilityService.performTapSuspend(canvasSelectedX, canvasSelectedY)
                    val elapsed = System.currentTimeMillis() - startTime

                    if (success) {
                        onLog("CANVAS_TAP", "(${canvasSelectedX.toInt()}, ${canvasSelectedY.toInt()})", true, elapsed, "Ketukan berhasil dikirim ke titik kanvas")
                    } else {
                        onLog("CANVAS_TAP", "(${canvasSelectedX.toInt()}, ${canvasSelectedY.toInt()})", false, elapsed, "Gestur gagal dikirim ke titik kanvas")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = EmeraldReady,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("UJI KETUK TITIK KANVAS (${canvasSelectedX.toInt()}px, ${canvasSelectedY.toInt()}px)", fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}
