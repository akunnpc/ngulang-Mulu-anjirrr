package com.example.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.ScreenCaptureService
import com.example.ui.theme.*

@Composable
fun EngineStatusCard(
    activeServiceState: ScreenCaptureService.ServiceState,
    onStopAll: () -> Unit
) {
    val statusColor = when (activeServiceState) {
        ScreenCaptureService.ServiceState.STOPPED -> BorderSlate
        ScreenCaptureService.ServiceState.IDLE -> CyberCyan
        ScreenCaptureService.ServiceState.RUNNING -> EmeraldReady
        ScreenCaptureService.ServiceState.PAUSED -> WarningGold
    }

    val statusLabel = when (activeServiceState) {
        ScreenCaptureService.ServiceState.STOPPED -> "OFFLINE"
        ScreenCaptureService.ServiceState.IDLE -> "STANDBY"
        ScreenCaptureService.ServiceState.RUNNING -> "ACTIVE SCANNING"
        ScreenCaptureService.ServiceState.PAUSED -> "SUSPENDED"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, statusColor.copy(alpha = 0.35f), RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = SlateCardBg)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Pulsing radar-like dot
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(statusColor)
                    )
                    Column {
                        Text(
                            "Mesin Deteksi Auto-Tap",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Text(
                            text = when (activeServiceState) {
                                ScreenCaptureService.ServiceState.STOPPED -> "Sistem offline. Jalankan salah satu profil di bawah."
                                ScreenCaptureService.ServiceState.IDLE -> "Overlay aktif. Menunggu pemicu start di layar."
                                ScreenCaptureService.ServiceState.RUNNING -> "Sedang mencari kecocokan visual real-time..."
                                ScreenCaptureService.ServiceState.PAUSED -> "Pemindaian visual ditangguhkan sementara."
                            },
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                }

                // High-tech status tag
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(alpha = 0.12f))
                        .border(1.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // If active, show an integrated OFF switch bar at the bottom
            if (activeServiceState != ScreenCaptureService.ServiceState.STOPPED) {
                Button(
                    onClick = onStopAll,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEF4444).copy(alpha = 0.15f),
                        contentColor = Color(0xFFF87171)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "NONAKTIFKAN MESIN DETEKSI",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}
