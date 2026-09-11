package com.example.ui.diagnostic

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.TapAccessibilityService
import com.example.ui.theme.*
import com.example.util.AppLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TargetButtonTestTab(
    onLog: (type: String, coords: String, success: Boolean, latencyMs: Long, message: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    var targetButtonCenterX by remember { mutableFloatStateOf(0f) }
    var targetButtonCenterY by remember { mutableFloatStateOf(0f) }
    var targetClicksReceived by remember { mutableIntStateOf(0) }
    var lastTargetClickTime by remember { mutableStateOf<String?>(null) }
    var isTargetTesting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Uji Coba Langsung Tombol Target",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = Color.White
        )
        Text(
            text = "Tekan tombol pemicu di bawah. Layanan Aksesibilitas akan mengirimkan ketukan fisik ke koordinat persis Tombol Target. Jika Aksesibilitas berfungsi, tombol sasaran akan menerima sentuhan dan menambah counter klik.",
            fontSize = 11.sp,
            color = TextMuted,
            lineHeight = 15.sp
        )

        // Target Button Display Box
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSlate, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = SlateCardBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "🎯 TOMBOL TARGET FISIK (TARGET BUTTON)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberCyan
                )

                Button(
                    onClick = {
                        targetClicksReceived++
                        lastTargetClickTime = timeFormat.format(Date())
                        AppLogger.log("✅ Tombol sasaran berhasil menerima event klik fisik! Total klik: $targetClicksReceived")
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(64.dp)
                        .onGloballyPositioned { coordinates ->
                            val bounds = coordinates.boundsInWindow()
                            targetButtonCenterX = bounds.left + (bounds.width / 2f)
                            targetButtonCenterY = bounds.top + (bounds.height / 2f)
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (targetClicksReceived > 0) EmeraldReady else CyberCyan,
                        contentColor = Color.Black
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RadioButtonChecked,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "SASARAN SENTUHAN",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Klik Diterima: $targetClicksReceived kali",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ConsoleBg, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Koordinat Layar: (${targetButtonCenterX.toInt()}px, ${targetButtonCenterY.toInt()}px)",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = CyberCyan
                    )
                    if (lastTargetClickTime != null) {
                        Text(
                            text = "Waktu: $lastTargetClickTime",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = EmeraldReady
                        )
                    }
                }
            }
        }

        // Trigger Button
        Button(
            onClick = {
                if (!TapAccessibilityService.isRunning()) {
                    Toast.makeText(context, "Layanan Aksesibilitas belum aktif!", Toast.LENGTH_SHORT).show()
                    onLog("TARGET_TAP", "(${targetButtonCenterX.toInt()}, ${targetButtonCenterY.toInt()})", false, 0L, "Layanan Aksesibilitas belum berjalan")
                    return@Button
                }
                if (targetButtonCenterX <= 0f || targetButtonCenterY <= 0f) {
                    Toast.makeText(context, "Menunggu pengukuran koordinat tombol...", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                isTargetTesting = true
                val startTime = System.currentTimeMillis()
                coroutineScope.launch {
                    val initialCount = targetClicksReceived
                    val success = TapAccessibilityService.performTapSuspend(targetButtonCenterX, targetButtonCenterY)
                    val elapsed = System.currentTimeMillis() - startTime
                    delay(100)
                    val countAfter = targetClicksReceived

                    isTargetTesting = false
                    if (success) {
                        if (countAfter > initialCount) {
                            onLog("TARGET_TAP", "(${targetButtonCenterX.toInt()}, ${targetButtonCenterY.toInt()})", true, elapsed, "Gestur terkirim & klik fisik sukses diverifikasi oleh Tombol Sasaran! ✓")
                        } else {
                            onLog("TARGET_TAP", "(${targetButtonCenterX.toInt()}, ${targetButtonCenterY.toInt()})", true, elapsed, "Gestur terkirim ke OS, menunggu registrasi UI.")
                        }
                    } else {
                        onLog("TARGET_TAP", "(${targetButtonCenterX.toInt()}, ${targetButtonCenterY.toInt()})", false, elapsed, "OS membatalkan gestur ketukan.")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isTargetTesting,
            colors = ButtonDefaults.buttonColors(
                containerColor = EmeraldReady,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (isTargetTesting) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mengirimkan Ketukan Gestur...", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            } else {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("KIRIM KETUKAN KE TOMBOL SASARAN", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        // Reset Counter Button
        if (targetClicksReceived > 0) {
            OutlinedButton(
                onClick = {
                    targetClicksReceived = 0
                    lastTargetClickTime = null
                },
                modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSlate),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted)
            ) {
                Text("Reset Counter Sasaran", fontSize = 11.sp)
            }
        }
    }
}
