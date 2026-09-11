package com.example

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.core.permission.AppPermissionManager
import com.example.data.*
import com.example.service.OverlayService
import com.example.service.ScreenCaptureService
import com.example.ui.DiagnosticModeDialog
import com.example.ui.main.*
import com.example.ui.theme.*
import com.example.util.AppLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var repository: ProfileRepository
    private var selectedProfileToStart: Profile? = null

    // Screen projection request launcher
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val profile = selectedProfileToStart
            if (profile == null) {
                Toast.makeText(this, "Silakan pilih profil terlebih dahulu.", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }

            val hasApp = profile.packageName.isNotEmpty()
            val launchIntent = if (hasApp) {
                packageManager.getLaunchIntentForPackage(profile.packageName)
            } else null

            if (hasApp && launchIntent == null) {
                Toast.makeText(this, "Aplikasi target tidak ditemukan, mungkin sudah dihapus", Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }

            lifecycleScope.launch {
                if (hasApp && launchIntent != null) {
                    try {
                        startActivity(launchIntent)
                        AppLogger.log("Membuka aplikasi target: '${profile.appName}'...")
                        delay(2500L) // Jeda 2.5 detik agar aplikasi target selesai loading
                    } catch (e: Exception) {
                        AppLogger.log("Gagal membuka aplikasi target: ${e.message}")
                    }
                }

                // Start foreground capture service
                val captureIntent = Intent(this@MainActivity, ScreenCaptureService::class.java).apply {
                    action = ScreenCaptureService.ACTION_START_PROJECTION
                    putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(captureIntent)
                } else {
                    startService(captureIntent)
                }

                // Start floating overlay controls
                val overlayIntent = Intent(this@MainActivity, OverlayService::class.java).apply {
                    putExtra(OverlayService.EXTRA_PROFILE_NAME, profile.name)
                    putExtra(OverlayService.EXTRA_PROFILE_ID, profile.id)
                }
                startService(overlayIntent)

                // Small delay to ensure virtualDisplay and overlay views are completely initialized
                delay(400L)

                // Trigger tapping matching loop inside screen capture service
                val tapIntent = Intent(this@MainActivity, ScreenCaptureService::class.java).apply {
                    action = ScreenCaptureService.ACTION_START_TAPPING
                    putExtra(ScreenCaptureService.EXTRA_PROFILE_ID, profile.id)
                }
                startService(tapIntent)
            }
        } else {
            Toast.makeText(this, "Izin perekaman layar diperlukan untuk auto-tap visual", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = AppDatabase.getDatabase(this)
        repository = ProfileRepository(db.autoTapDao())

        setContent {
            MyApplicationTheme {
                MainScreen(
                    repository = repository,
                    onStartProfile = { profile ->
                        selectedProfileToStart = profile
                        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                    },
                    onStopAll = {
                        stopService(Intent(this, ScreenCaptureService::class.java))
                        stopService(Intent(this, OverlayService::class.java))
                    }
                )
            }
        }
    }
}

@Composable
fun MainScreen(
    repository: ProfileRepository,
    onStartProfile: (Profile) -> Unit,
    onStopAll: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val profiles by repository.allProfiles.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeServiceState by ScreenCaptureService.state.collectAsStateWithLifecycle()
    val activeProfileId by ScreenCaptureService.activeProfileId.collectAsStateWithLifecycle()

    // Permissions State
    var isOverlayGranted by remember { mutableStateOf(AppPermissionManager.isOverlayGranted(context)) }
    var isAccessibilityGranted by remember { mutableStateOf(AppPermissionManager.isAccessibilityGranted()) }
    var isNotificationGranted by remember { mutableStateOf(AppPermissionManager.isNotificationGranted(context)) }
    var isBatteryOptimizationIgnored by remember { mutableStateOf(AppPermissionManager.isBatteryOptimizationIgnored(context)) }

    var showDiagnosticDialog by remember { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isNotificationGranted = granted
    }

    // Live logs flow
    val logsList by AppLogger.logs.collectAsStateWithLifecycle()

    // Refresh permission states on Resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isOverlayGranted = AppPermissionManager.isOverlayGranted(context)
                isAccessibilityGranted = AppPermissionManager.isAccessibilityGranted()
                isNotificationGranted = AppPermissionManager.isNotificationGranted(context)
                isBatteryOptimizationIgnored = AppPermissionManager.isBatteryOptimizationIgnored(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianBg)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CyberCyan.copy(alpha = 0.15f))
                                .border(1.dp, CyberCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                .size(20.dp)
                                .border(2.dp, CyberCyan, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(CyberCyan, RoundedCornerShape(3.dp))
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "Auto Tap",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White,
                                letterSpacing = (-0.5).sp
                            )
                            Text(
                                text = "IMAGE DETECTION V1.2",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = CyberCyan,
                                letterSpacing = 1.5.sp
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDiagnosticDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
                            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.BugReport, contentDescription = "Diagnostik", modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Diagnostik", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        if (activeServiceState != ScreenCaptureService.ServiceState.STOPPED) {
                            OutlinedButton(
                                onClick = onStopAll,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = "Stop", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("STOP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else if (isOverlayGranted && isAccessibilityGranted) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50.dp))
                                    .background(EmeraldReady.copy(alpha = 0.1f))
                                    .border(1.dp, EmeraldReady.copy(alpha = 0.3f), RoundedCornerShape(50.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "READY",
                                    color = EmeraldReady,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(BorderSlate)
                )
            }
        },
        containerColor = ObsidianBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Permission checks
            PermissionSectionCard(
                isOverlayGranted = isOverlayGranted,
                isAccessibilityGranted = isAccessibilityGranted,
                isNotificationGranted = isNotificationGranted,
                isBatteryOptimizationIgnored = isBatteryOptimizationIgnored,
                onOpenOverlaySettings = { context.startActivity(AppPermissionManager.getOverlaySettingsIntent(context)) },
                onOpenAccessibilitySettings = { context.startActivity(AppPermissionManager.getAccessibilitySettingsIntent()) },
                onRequestNotificationPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onOpenBatteryOptimizationSettings = {
                    try {
                        context.startActivity(AppPermissionManager.getBatteryOptimizationIntent(context))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Buka Pengaturan Baterai untuk mengabaikan hemat daya.", Toast.LENGTH_SHORT).show()
                    }
                },
                onOpenDiagnostic = { showDiagnosticDialog = true }
            )

            // 2. Engine status card
            EngineStatusCard(
                activeServiceState = activeServiceState,
                onStopAll = onStopAll
            )

            // 3. Profiles listing
            ProfileListSection(
                profiles = profiles,
                activeProfileId = activeProfileId,
                activeServiceState = activeServiceState,
                onAddNewProfile = {
                    val intent = Intent(context, ProfileEditorActivity::class.java)
                    context.startActivity(intent)
                },
                onStartProfile = { profile ->
                    if (!isOverlayGranted || !isAccessibilityGranted) {
                        Toast.makeText(context, "Berikan semua izin sistem terlebih dahulu!", Toast.LENGTH_SHORT).show()
                        return@ProfileListSection
                    }
                    coroutineScope.launch {
                        val relation = repository.getProfileWithTargetsDirect(profile.id)
                        relation?.targets?.forEach { target ->
                            repository.updateTargetImage(target.copy(tapCount = 0))
                        }
                        onStartProfile(profile)
                    }
                },
                onEditProfile = { profile ->
                    val intent = Intent(context, ProfileEditorActivity::class.java).apply {
                        putExtra(ProfileEditorActivity.EXTRA_PROFILE_ID, profile.id)
                    }
                    context.startActivity(intent)
                },
                onDeleteProfile = { profile ->
                    coroutineScope.launch {
                        val relation = repository.getProfileWithTargetsDirect(profile.id)
                        relation?.targets?.forEach { target ->
                            File(target.filePath).delete()
                        }
                        repository.deleteProfile(profile)
                        Toast.makeText(context, "Profil dihapus", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f)
            )

            // 4. Live logger console
            LiveLogConsoleCard(
                logsList = logsList,
                onClearLogs = { AppLogger.clear() }
            )
        }
    }

    if (showDiagnosticDialog) {
        DiagnosticModeDialog(
            onDismiss = { showDiagnosticDialog = false }
        )
    }
}
