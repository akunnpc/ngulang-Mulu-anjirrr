package com.example.ui.main

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.permission.AppPermissionManager
import com.example.ui.theme.*

@Composable
fun PermissionSectionCard(
    isOverlayGranted: Boolean,
    isAccessibilityGranted: Boolean,
    isNotificationGranted: Boolean,
    isBatteryOptimizationIgnored: Boolean,
    onOpenOverlaySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onOpenDiagnostic: () -> Unit
) {
    var showAllPermissionsDetail by remember { mutableStateOf(false) }
    val allMandatoryGranted = isOverlayGranted && isAccessibilityGranted

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (allMandatoryGranted) EmeraldReady.copy(alpha = 0.35f)
                else Color(0xFFF59E0B).copy(alpha = 0.5f),
                RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (allMandatoryGranted) SlateCardBg
            else Color(0xFF1E1B18)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with Status Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (allMandatoryGranted) Icons.Default.CheckCircle else Icons.Default.Shield,
                        contentDescription = null,
                        tint = if (allMandatoryGranted) EmeraldReady else Color(0xFFF59E0B),
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = if (allMandatoryGranted) "Status Izin: Lengkap & Siap Digunakan" else "Status Izin: Perlu Diaktifkan",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (allMandatoryGranted) "Semua izin utama sudah aktif ✅" else "Aktifkan izin secara berurutan di bawah ini",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }

                if (allMandatoryGranted) {
                    TextButton(
                        onClick = { showAllPermissionsDetail = !showAllPermissionsDetail },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (showAllPermissionsDetail) "Tutup ▲" else "Detail ▼",
                            fontSize = 11.sp,
                            color = CyberCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (!allMandatoryGranted || showAllPermissionsDetail) {
                HorizontalDivider(color = BorderSlate, thickness = 1.dp)

                // STEP 1: OVERLAY PERMISSION
                PermissionStepItem(
                    stepNumber = 1,
                    title = "Izin Tampilan di Atas Aplikasi Lain (Overlay)",
                    description = "Diperlukan untuk memunculkan tombol melayang, menu kontrol, dan pin target di atas layar.",
                    isGranted = isOverlayGranted,
                    buttonText = "Aktifkan Izin 1",
                    onActionClick = onOpenOverlaySettings
                )

                // STEP 2: ACCESSIBILITY SERVICE (CRITICAL FOR CLICKS)
                PermissionStepItem(
                    stepNumber = 2,
                    title = "Layanan Aksesibilitas (Auto Tap Gesture)",
                    description = "Sangat Penting! Diperlukan agar robot sistem Android dapat menekan/mengklik tombol target secara otomatis.",
                    isGranted = isAccessibilityGranted,
                    buttonText = "Aktifkan Izin 2",
                    guideHint = "Buka menu, cari 'Auto Tap by Image', lalu nyalakan tombol Aktif.",
                    secondaryButtonText = "🧪 Uji Coba Ketukan (Mode Diagnostik)",
                    onSecondaryClick = onOpenDiagnostic,
                    onActionClick = onOpenAccessibilitySettings
                )

                // STEP 3: NOTIFICATIONS (ANDROID 13+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PermissionStepItem(
                        stepNumber = 3,
                        title = "Izin Notifikasi (Android 13+)",
                        description = "Menjaga proses auto-clicker tetap berjalan lancar di bar notifikasi.",
                        isGranted = isNotificationGranted,
                        buttonText = "Aktifkan Izin 3",
                        onActionClick = onRequestNotificationPermission
                    )
                }

                // STEP 4: BATTERY OPTIMIZATION
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PermissionStepItem(
                        stepNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) 4 else 3,
                        title = "Abaikan Hemat Baterai (Latar Belakang)",
                        description = "Mencegah sistem HP mematikan auto-clicker saat game berat berjalan.",
                        isGranted = isBatteryOptimizationIgnored,
                        buttonText = "Beri Izin",
                        onActionClick = onOpenBatteryOptimizationSettings
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionStepItem(
    stepNumber: Int,
    title: String,
    description: String,
    isGranted: Boolean,
    buttonText: String,
    guideHint: String? = null,
    secondaryButtonText: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
    onActionClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isGranted) EmeraldReady.copy(alpha = 0.3f)
                else BorderSlate,
                RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) EmeraldReady.copy(alpha = 0.05f)
            else SlateCardBg
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isGranted) EmeraldReady.copy(alpha = 0.2f)
                                else CyberCyan.copy(alpha = 0.2f)
                            )
                            .border(
                                1.dp,
                                if (isGranted) EmeraldReady
                                else CyberCyan,
                                RoundedCornerShape(6.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$stepNumber",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (isGranted) EmeraldReady else CyberCyan
                        )
                    }

                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }

                // Status Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isGranted) EmeraldReady.copy(alpha = 0.15f)
                            else Color(0xFFEF4444).copy(alpha = 0.15f)
                        )
                        .border(
                            1.dp,
                            if (isGranted) EmeraldReady.copy(alpha = 0.4f)
                            else Color(0xFFEF4444).copy(alpha = 0.4f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isGranted) "AKTIF ✓" else "BELUM ✕",
                        color = if (isGranted) EmeraldReady else Color(0xFFF87171),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = description,
                fontSize = 11.sp,
                color = TextMuted,
                lineHeight = 15.sp
            )

            if (!isGranted && guideHint != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF59E0B).copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "💡 Petunjuk: $guideHint",
                        fontSize = 10.sp,
                        color = Color(0xFFFDE68A)
                    )
                }
            }

            if (!isGranted) {
                Button(
                    onClick = onActionClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberCyan,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Text(buttonText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            if (secondaryButtonText != null && onSecondaryClick != null) {
                OutlinedButton(
                    onClick = onSecondaryClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
                    border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(secondaryButtonText, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
    }
}
