package com.example.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.service.TapAccessibilityService
import com.example.ui.diagnostic.*
import com.example.ui.theme.*
import com.example.util.AppLogger
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticModeDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val isServiceActive by remember { mutableStateOf(TapAccessibilityService.isRunning()) }
    var selectedTab by remember { mutableIntStateOf(0) }

    val diagnosticLogs = remember { mutableStateListOf<DiagnosticLogEntry>() }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    fun addDiagnosticLog(type: String, coords: String, success: Boolean, latencyMs: Long, message: String) {
        val entry = DiagnosticLogEntry(
            timestamp = timeFormat.format(Date()),
            type = type,
            coordinates = coords,
            isSuccess = success,
            latencyMs = latencyMs,
            message = message
        )
        diagnosticLogs.add(0, entry)
        if (diagnosticLogs.size > 50) {
            diagnosticLogs.removeLast()
        }
        AppLogger.log("[DIAGNOSTIK] $type pada $coords -> ${if (success) "SUKSES" else "GAGAL"} (${latencyMs}ms): $message")
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 20.dp),
            shape = RoundedCornerShape(24.dp),
            color = ObsidianBg,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, CyberCyan.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(CyberCyan.copy(alpha = 0.15f))
                                .border(1.dp, CyberCyan, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Mode Diagnostik Sentuhan",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Uji Coba Langsung Layanan Aksesibilitas",
                                fontSize = 11.sp,
                                color = CyberCyan
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .background(SlateCardBg, CircleShape)
                            .border(1.dp, BorderSlate, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = TextLight,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Accessibility Service Status Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (isServiceActive) EmeraldReady.copy(alpha = 0.4f) else Color(0xFFEF4444).copy(alpha = 0.5f),
                            RoundedCornerShape(14.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isServiceActive) EmeraldReady.copy(alpha = 0.08f) else Color(0xFF1E1414)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(if (isServiceActive) EmeraldReady else Color(0xFFEF4444))
                            )
                            Column {
                                Text(
                                    text = if (isServiceActive) "Aksesibilitas Aktif & Siap Disimulasikan" else "Layanan Aksesibilitas Belum Aktif",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = if (isServiceActive) "TapAccessibilityService terhubung ke sistem OS Android" else "Perlu diaktifkan agar robot dapat mengirim ketukan",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }
                        }

                        if (!isServiceActive) {
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Buka Pengaturan", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(EmeraldReady.copy(alpha = 0.2f))
                                    .border(1.dp, EmeraldReady.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text("ONLINE ✓", color = EmeraldReady, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Tab Selector
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = SlateCardBg,
                    contentColor = CyberCyan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, BorderSlate, RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Tombol Sasaran", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Input Koordinat", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.PinDrop, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Kanvas Sentuh", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.CenterFocusStrong, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }

                // Main Interactive Panel Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (selectedTab) {
                        0 -> TargetButtonTestTab(onLog = ::addDiagnosticLog)
                        1 -> CustomCoordinatesTab(
                            screenWidthPx = screenWidthPx,
                            screenHeightPx = screenHeightPx,
                            onLog = ::addDiagnosticLog
                        )
                        2 -> TouchCanvasArenaTab(
                            initialX = screenWidthPx / 2f,
                            initialY = screenHeightPx / 2f,
                            onLog = ::addDiagnosticLog
                        )
                    }
                }

                HorizontalDivider(color = BorderSlate, thickness = 1.dp)

                // Diagnostic Log Feedback Console
                DiagnosticLogsConsole(
                    logs = diagnosticLogs,
                    onClearLogs = { diagnosticLogs.clear() }
                )
            }
        }
    }
}
