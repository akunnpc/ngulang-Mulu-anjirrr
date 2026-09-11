package com.example.ui.diagnostic

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.TapAccessibilityService
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CustomCoordinatesTab(
    screenWidthPx: Float,
    screenHeightPx: Float,
    onLog: (type: String, coords: String, success: Boolean, latencyMs: Long, message: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var inputX by remember { mutableStateOf((screenWidthPx / 2f).toInt().toString()) }
    var inputY by remember { mutableStateOf((screenHeightPx / 2f).toInt().toString()) }
    var selectedGestureType by remember { mutableStateOf("TAP") }
    var isCustomTesting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Uji Koordinat Layar Manual",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = Color.White
        )

        // Preset Shortcuts
        Text("Pintasan Posisi:", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    inputX = (screenWidthPx / 2f).toInt().toString()
                    inputY = (screenHeightPx / 2f).toInt().toString()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = SlateCardBg, contentColor = CyberCyan),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                Text("Pusat Layar", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    inputX = (screenWidthPx / 2f).toInt().toString()
                    inputY = (screenHeightPx * 0.25f).toInt().toString()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = SlateCardBg, contentColor = TextLight),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSlate),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                Text("Area Atas", fontSize = 10.sp)
            }

            Button(
                onClick = {
                    inputX = (screenWidthPx / 2f).toInt().toString()
                    inputY = (screenHeightPx * 0.75f).toInt().toString()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = SlateCardBg, contentColor = TextLight),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSlate),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                Text("Area Bawah", fontSize = 10.sp)
            }
        }

        // Coordinate Text Inputs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = inputX,
                onValueChange = { inputX = it.filter { char -> char.isDigit() } },
                label = { Text("Koordinat X (px)") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = BorderSlate,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = CyberCyan
                ),
                shape = RoundedCornerShape(10.dp)
            )

            OutlinedTextField(
                value = inputY,
                onValueChange = { inputY = it.filter { char -> char.isDigit() } },
                label = { Text("Koordinat Y (px)") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = BorderSlate,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = CyberCyan
                ),
                shape = RoundedCornerShape(10.dp)
            )
        }

        // Gesture Type Selection
        Text("Tipe Aksi Gestur:", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "TAP" to "Ketuk (80ms)",
                "LONG_500" to "Tahan (500ms)",
                "LONG_1000" to "Tahan (1s)",
                "DOUBLE_TAP" to "Ganda (2x)"
            ).forEach { (type, label) ->
                val isSelected = selectedGestureType == type
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) CyberCyan.copy(alpha = 0.2f) else SlateCardBg)
                        .border(
                            1.dp,
                            if (isSelected) CyberCyan else BorderSlate,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedGestureType = type }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 9.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) CyberCyan else TextLight,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Execution Button
        Button(
            onClick = {
                val x = inputX.toFloatOrNull() ?: 0f
                val y = inputY.toFloatOrNull() ?: 0f

                if (!TapAccessibilityService.isRunning()) {
                    Toast.makeText(context, "Layanan Aksesibilitas belum aktif!", Toast.LENGTH_SHORT).show()
                    onLog("CUSTOM_$selectedGestureType", "(${x.toInt()}, ${y.toInt()})", false, 0L, "Layanan Aksesibilitas belum berjalan")
                    return@Button
                }

                isCustomTesting = true
                coroutineScope.launch {
                    val startTime = System.currentTimeMillis()
                    val success: Boolean
                    when (selectedGestureType) {
                        "LONG_500" -> {
                            success = TapAccessibilityService.performLongPressSuspend(x, y, 500L)
                        }
                        "LONG_1000" -> {
                            success = TapAccessibilityService.performLongPressSuspend(x, y, 1000L)
                        }
                        "DOUBLE_TAP" -> {
                            val first = TapAccessibilityService.performTapSuspend(x, y)
                            delay(120L)
                            val second = TapAccessibilityService.performTapSuspend(x, y)
                            success = first && second
                        }
                        else -> { // TAP
                            success = TapAccessibilityService.performTapSuspend(x, y)
                        }
                    }
                    val elapsed = System.currentTimeMillis() - startTime
                    isCustomTesting = false

                    if (success) {
                        onLog("CUSTOM_$selectedGestureType", "(${x.toInt()}, ${y.toInt()})", true, elapsed, "Gestur $selectedGestureType berhasil diselesaikan oleh OS Android")
                    } else {
                        onLog("CUSTOM_$selectedGestureType", "(${x.toInt()}, ${y.toInt()})", false, elapsed, "Gestur ditolak/dibatalkan oleh OS")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isCustomTesting,
            colors = ButtonDefaults.buttonColors(
                containerColor = CyberCyan,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (isCustomTesting) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mengirimkan Gestur...", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("KIRIM GESTUR UJI (${inputX}px, ${inputY}px)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}
