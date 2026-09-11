package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.ProfileRepository
import com.example.util.AppLogger
import com.example.util.ImageMatcher
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

data class MatchHighlightEvent(
    val rect: android.graphics.Rect,
    val screenX: Float,
    val screenY: Float,
    val targetName: String
)

class ScreenCaptureService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureJob: Job? = null

    private var screenWidth = 720
    private var screenHeight = 1280
    private var screenDpi = 320

    private var lastCachedBitmap: Bitmap? = null

    enum class ServiceState {
        STOPPED,
        IDLE,     // Projection is ready, but matching loop is not running
        RUNNING,  // Matching loop is running
        PAUSED    // Matching loop is paused
    }

    override fun onCreate() {
        super.onCreate()
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand: action = $action")

        when (action) {
            ACTION_START_PROJECTION -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                if (resultCode == Activity.RESULT_OK && resultData != null) {
                    startForegroundWithNotification()
                    initProjection(resultCode, resultData)
                } else {
                    Log.e(TAG, "Failed to start projection due to invalid result code/data")
                    stopSelf()
                }
            }
            ACTION_START_TAPPING -> {
                val profileId = intent.getLongExtra(EXTRA_PROFILE_ID, -1L)
                if (profileId != -1L) {
                    startTappingLoop(profileId)
                } else {
                    Log.e(TAG, "Failed to start tapping: Profile ID is invalid")
                }
            }
            ACTION_PAUSE_TAPPING -> {
                pauseTapping()
            }
            ACTION_RESUME_TAPPING -> {
                val profileId = _activeProfileId.value
                if (profileId != null) {
                    startTappingLoop(profileId)
                }
            }
            ACTION_STOP -> {
                stopProjection()
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun initProjection(resultCode: Int, resultData: Intent) {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            screenWidth = bounds.width()
            screenHeight = bounds.height()
            screenDpi = resources.configuration.densityDpi
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
            screenDpi = metrics.densityDpi
        }

        Log.d(TAG, "Screen Dimensions: ${screenWidth}x${screenHeight} @ $screenDpi DPI")

        try {
            mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, resultData)
            
            // Set callback to handle unexpected projection termination
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    super.onStop()
                    Log.w(TAG, "MediaProjection stopped from system")
                    stopProjection()
                }
            }, null)

            // Setup image reader to capture full screen dimensions
            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "AutoTapScreenCapture",
                screenWidth, screenHeight, screenDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )

            _state.value = ServiceState.IDLE
            AppLogger.log("Screen capture initialized. Ready to Tap.")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MediaProjection", e)
            AppLogger.log("Error initializing Screen Capture: ${e.message}")
            stopSelf()
        }
    }

    private fun startTappingLoop(profileId: Long) {
        _activeProfileId.value = profileId
        _state.value = ServiceState.RUNNING
        _totalTapCount.value = 0
        
        AppLogger.log("Starting Auto-Tap visual template scanning in sequence mode...")

        captureJob?.cancel()
        captureJob = serviceScope.launch(Dispatchers.Default) {
            val db = AppDatabase.getDatabase(this@ScreenCaptureService)
            val repository = ProfileRepository(db.autoTapDao())

            var currentStepIndex = 0
            var stepStartTime = System.currentTimeMillis()

            var waitCount = 0
            while (imageReader == null && waitCount < 10 && isActive) {
                delay(100)
                waitCount++
            }

            while (isActive && _state.value == ServiceState.RUNNING) {
                val profileWithTargets = repository.getProfileWithTargetsDirect(profileId)
                if (profileWithTargets == null) {
                    AppLogger.log("Profile not found! Stopped.")
                    withContext(Dispatchers.Main) {
                        pauseTapping()
                    }
                    break
                }

                val profile = profileWithTargets.profile

                // If profile has no targets configured yet, auto-create a default POINT target at screen center so auto-tapping starts immediately!
                if (profileWithTargets.targets.isEmpty()) {
                    val defaultX = if (screenWidth > 0) screenWidth / 2f else 540f
                    val defaultY = if (screenHeight > 0) screenHeight / 2f else 960f
                    AppLogger.log("Profil belum memiliki target sentuhan. Menyiapkan 'Titik Sentuh #1' otomatis di tengah layar (${defaultX.toInt()}, ${defaultY.toInt()})...")
                    val defaultTarget = com.example.data.TargetImage(
                        profileId = profileId,
                        name = "Titik Sentuh #1",
                        filePath = "",
                        threshold = 0.8f,
                        delayAfterTapMs = 500L,
                        maxTaps = 0,
                        priority = 0,
                        targetType = "POINT",
                        pointX = defaultX,
                        pointY = defaultY,
                        actionType = "TAP"
                    )
                    repository.insertTargetImage(defaultTarget)
                    delay(250)
                    continue
                }

                // Sort all targets deterministically by priority and ID (Step 1, Step 2, Step 3, etc.)
                val targets = profileWithTargets.targets
                    .filter { it.maxTaps == 0 || it.tapCount < it.maxTaps }
                    .sortedWith(compareBy<com.example.data.TargetImage> { it.priority }.thenBy { it.id })

                if (targets.isEmpty()) {
                    if (profile.loopSequence && profileWithTargets.targets.isNotEmpty()) {
                        AppLogger.log("Semua target telah mencapai batas maks ketukan. Mengulang siklus sequence dari awal...")
                        profileWithTargets.targets.forEach { target ->
                            repository.updateTargetImage(target.copy(tapCount = 0))
                        }
                        currentStepIndex = 0
                        delay(200)
                        continue
                    } else {
                        AppLogger.log("Semua target telah memenuhi batas maks ketukan. Selesai.")
                        withContext(Dispatchers.Main) {
                            pauseTapping()
                        }
                        break
                    }
                }

                // Safety bounds check for currentStepIndex
                if (currentStepIndex >= targets.size) {
                    if (profile.loopSequence) {
                        currentStepIndex = 0
                        stepStartTime = System.currentTimeMillis()
                        AppLogger.log("Sequence selesai. Mengulangi dari awal (Langkah 1).")
                    } else {
                        AppLogger.log("Semua langkah sequence selesai. Menghentikan macro.")
                        withContext(Dispatchers.Main) {
                            pauseTapping()
                        }
                        break
                    }
                }

                val target = targets[currentStepIndex]

                // If target is a manual coordinate point, execute directly without screenshot/image matching
                if (target.targetType == "POINT") {
                    val tapX = if (target.pointX > 0f) target.pointX else (if (screenWidth > 0) screenWidth / 2f else 540f)
                    val tapY = if (target.pointY > 0f) target.pointY else (if (screenHeight > 0) screenHeight / 2f else 960f)

                    AppLogger.log("Langkah ${currentStepIndex + 1}/${targets.size} (Titik Koordinat): '${target.name}' di (${tapX.toInt()}, ${tapY.toInt()})")
                    
                    val pointRect = android.graphics.Rect(
                        (tapX - 40).toInt().coerceAtLeast(0),
                        (tapY - 40).toInt().coerceAtLeast(0),
                        (tapX + 40).toInt(),
                        (tapY + 40).toInt()
                    )

                    when (target.actionType) {
                        "LONG_PRESS" -> {
                            val pressSent = TapAccessibilityService.performLongPressSuspend(tapX, tapY, target.holdDurationMs)
                            if (pressSent) {
                                val newCount = target.tapCount + 1
                                repository.updateTargetImage(target.copy(tapCount = newCount))
                                AppLogger.log("Tekan tahan sentuh selama ${target.holdDurationMs}ms berhasil pada '${target.name}'! Aksi ke-$newCount")
                                _totalTapCount.value = _totalTapCount.value + 1
                                _matchHighlights.tryEmit(MatchHighlightEvent(pointRect, tapX, tapY, "${target.name} (Tahan)"))
                            } else {
                                AppLogger.log("⚠️ Tekan tahan gagal. Pastikan Layanan Aksesibilitas diaktifkan di Pengaturan Android.")
                            }
                        }
                        "WAIT_ONLY" -> {
                            AppLogger.log("Langkah ${currentStepIndex + 1}/${targets.size}: Menunggu delay pada titik '${target.name}'...")
                            _matchHighlights.tryEmit(MatchHighlightEvent(pointRect, tapX, tapY, "${target.name} (Tunggu)"))
                        }
                        else -> { // TAP
                            val tapSent = TapAccessibilityService.performTapSuspend(tapX, tapY)
                            if (tapSent) {
                                val newCount = target.tapCount + 1
                                repository.updateTargetImage(target.copy(tapCount = newCount))
                                _totalTapCount.value = _totalTapCount.value + 1
                                AppLogger.log("Ketukan berhasil pada '${target.name}' (${tapX.toInt()}, ${tapY.toInt()})! Total ketukan: ${_totalTapCount.value}")
                                _matchHighlights.tryEmit(MatchHighlightEvent(pointRect, tapX, tapY, target.name))
                            } else {
                                AppLogger.log("⚠️ Ketukan gagal pada '${target.name}'. Pastikan Layanan Aksesibilitas diaktifkan di Pengaturan Android.")
                            }
                        }
                    }

                    currentStepIndex++
                    delay(target.delayAfterTapMs.coerceAtLeast(50L))

                    if (currentStepIndex >= targets.size) {
                        if (profile.loopSequence) {
                            currentStepIndex = 0
                            AppLogger.log("Sequence selesai. Mengulangi dari awal (Langkah 1).")
                        } else {
                            AppLogger.log("Semua langkah sequence selesai. Menghentikan macro.")
                            withContext(Dispatchers.Main) {
                                pauseTapping()
                            }
                            break
                        }
                    }
                    stepStartTime = System.currentTimeMillis()
                    continue
                }

                // Acquire current frame
                val screenshot = captureScreenshot()
                if (screenshot == null) {
                    // Frame not ready or capture failed, wait briefly
                    delay(100)
                    continue
                }

                // Match against current step target
                val matches = if (target.allowMultiMatch) {
                    ImageMatcher.findAllMatches(
                        screenBitmap = screenshot,
                        targetImagePath = target.filePath,
                        threshold = target.threshold,
                        maxMatches = 5,
                        restrictRegion = target.restrictRegion,
                        rx = target.regionX,
                        ry = target.regionY,
                        rw = target.regionWidth,
                        rh = target.regionHeight
                    )
                } else {
                    val singleMatch = ImageMatcher.findMatch(
                        screenBitmap = screenshot,
                        targetImagePath = target.filePath,
                        threshold = target.threshold,
                        restrictRegion = target.restrictRegion,
                        rx = target.regionX,
                        ry = target.regionY,
                        rw = target.regionWidth,
                        rh = target.regionHeight
                    )
                    if (singleMatch.isMatched) listOf(singleMatch) else emptyList()
                }

                if (target.actionType == "WAIT_DISAPPEAR") {
                    if (matches.isNotEmpty()) {
                        // Image is still visible, stay on this step and wait
                        AppLogger.log("Langkah ${currentStepIndex + 1}: '${target.name}' masih terdeteksi. Menunggu hingga menghilang...")
                        screenshot.recycle()
                        
                        // Check timeout condition for WAIT_DISAPPEAR
                        val timeoutSecs = if (target.timeoutSeconds > 0) target.timeoutSeconds else 10
                        val elapsedSeconds = (System.currentTimeMillis() - stepStartTime) / 1000
                        if (elapsedSeconds >= timeoutSecs) {
                            AppLogger.log("❌ Batas Waktu Terlampaui (Timeout): Gambar '${target.name}' tetap terdeteksi setelah ${timeoutSecs} detik. Menghentikan macro.")
                            withContext(Dispatchers.Main) {
                                pauseTapping()
                            }
                            break
                        }
                        
                        delay(profile.captureIntervalMs)
                        continue
                    } else {
                        // Image has successfully disappeared!
                        AppLogger.log("Langkah ${currentStepIndex + 1}: '${target.name}' telah menghilang. Melanjutkan ke langkah berikutnya.")
                        screenshot.recycle()
                        
                        currentStepIndex++
                        delay(target.delayAfterTapMs)
                        
                        if (currentStepIndex >= targets.size) {
                            if (profile.loopSequence) {
                                currentStepIndex = 0
                                AppLogger.log("Sequence selesai. Mengulangi dari awal (Langkah 1).")
                            } else {
                                AppLogger.log("Semua langkah sequence selesai. Menghentikan macro.")
                                withContext(Dispatchers.Main) {
                                    pauseTapping()
                                }
                                break
                            }
                        }
                        stepStartTime = System.currentTimeMillis()
                        continue
                    }
                }

                if (matches.isNotEmpty()) {
                    AppLogger.log("Langkah ${currentStepIndex + 1} ditemukan ${matches.size} kemunculan '${target.name}' di layar. Memproses pembelian berurutan...")

                    for ((index, match) in matches.withIndex()) {
                        AppLogger.log("   -> Memproses item #${index + 1} di (${match.screenX.toInt()}, ${match.screenY.toInt()}) dengan konfidensi ${String.format("%.2f", match.confidence)}")

                        when (target.actionType) {
                            "LONG_PRESS" -> {
                                val pressSent = TapAccessibilityService.performLongPressSuspend(match.screenX, match.screenY, target.holdDurationMs)
                                if (pressSent) {
                                    val newCount = target.tapCount + 1
                                    repository.updateTargetImage(target.copy(tapCount = newCount))
                                    AppLogger.log("Tekan tahan sentuh selama ${target.holdDurationMs}ms berhasil pada '${target.name}'! Aksi ke-$newCount")
                                    _totalTapCount.value = _totalTapCount.value + 1
                                    _matchHighlights.tryEmit(MatchHighlightEvent(match.rect, match.screenX, match.screenY, "${target.name} (#${index + 1} Tahan)"))
                                } else {
                                    AppLogger.log("⚠️ Tekan tahan gagal pada '${target.name}'. Apakah Layanan Aksesibilitas aktif?")
                                }
                            }
                            "WAIT_ONLY" -> {
                                AppLogger.log("Langkah ${currentStepIndex + 1}: '${target.name}' terdeteksi! (Tunggu Saja).")
                                _matchHighlights.tryEmit(MatchHighlightEvent(match.rect, match.screenX, match.screenY, "${target.name} (#${index + 1} Tunggu)"))
                            }
                            else -> { // "TAP"
                                val tapSent = TapAccessibilityService.performTapSuspend(match.screenX, match.screenY)
                                if (tapSent) {
                                    val newCount = target.tapCount + 1
                                    repository.updateTargetImage(target.copy(tapCount = newCount))
                                    AppLogger.log("Ketukan berhasil pada '${target.name}' (#${index + 1})! Total ketukan: $newCount")
                                    _totalTapCount.value = _totalTapCount.value + 1
                                    _matchHighlights.tryEmit(MatchHighlightEvent(match.rect, match.screenX, match.screenY, "${target.name} (#${index + 1})"))
                                } else {
                                    AppLogger.log("⚠️ Ketukan gagal pada '${target.name}'. Apakah Layanan Aksesibilitas aktif?")
                                }
                            }
                        }

                        // Delay between multiple items if there is another one
                        if (index < matches.size - 1) {
                            delay(target.delayAfterTapMs.coerceAtLeast(200L))
                        }
                    }

                    // Recycle screenshot
                    screenshot.recycle()

                    // Increment step index after processing ALL matched instances of this target
                    currentStepIndex++
                    
                    // Delay after tap
                    delay(target.delayAfterTapMs)

                    // Post-increment check if we have finished all steps
                    if (currentStepIndex >= targets.size) {
                        if (profile.loopSequence) {
                            currentStepIndex = 0
                            AppLogger.log("Sequence selesai. Mengulangi dari awal (Langkah 1).")
                        } else {
                            AppLogger.log("Semua langkah sequence selesai. Menghentikan macro.")
                            withContext(Dispatchers.Main) {
                                pauseTapping()
                            }
                            break
                        }
                    }

                    // Reset timeout start time for the next step
                    stepStartTime = System.currentTimeMillis()
                } else {
                    screenshot.recycle()

                    // Check timeout condition only if a positive timeout is set (> 0)
                    if (target.timeoutSeconds > 0) {
                        val timeoutSecs = target.timeoutSeconds
                        val elapsedSeconds = (System.currentTimeMillis() - stepStartTime) / 1000
                        if (elapsedSeconds >= timeoutSecs) {
                            AppLogger.log("❌ Batas Waktu Tunggu Terlampaui (Timeout): Gambar '${target.name}' (Langkah ${currentStepIndex + 1}) tidak ditemukan setelah ${timeoutSecs} detik. Menghentikan macro.")
                            withContext(Dispatchers.Main) {
                                pauseTapping()
                            }
                            break
                        }
                    }

                    // Sleep for the defined frame interval
                    delay(profile.captureIntervalMs)
                }
            }
        }
    }

    private fun pauseTapping() {
        captureJob?.cancel()
        _state.value = ServiceState.PAUSED
        AppLogger.log("Auto-Tap visual scanner paused.")
    }

    private suspend fun captureScreenshot(): Bitmap? = withContext(Dispatchers.Default) {
        val reader = imageReader ?: return@withContext null
        var img = try {
            reader.acquireLatestImage()
        } catch (e: Exception) {
            null
        }

        if (img == null) {
            delay(50)
            img = try {
                reader.acquireLatestImage()
            } catch (e: Exception) {
                null
            }
        }

        if (img == null) {
            // Return cached bitmap copy if screen is static so template matching loop continues to scan and click!
            synchronized(this@ScreenCaptureService) {
                val cached = lastCachedBitmap
                if (cached != null && !cached.isRecycled) {
                    return@withContext try {
                        cached.copy(cached.config ?: Bitmap.Config.ARGB_8888, false)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            return@withContext null
        }

        try {
            val planes = img.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * img.width

            val bitmap = Bitmap.createBitmap(
                img.width + rowPadding / pixelStride,
                img.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            val cleanBitmap = Bitmap.createBitmap(bitmap, 0, 0, img.width, img.height)
            if (cleanBitmap != bitmap) {
                bitmap.recycle()
            }

            synchronized(this@ScreenCaptureService) {
                try {
                    lastCachedBitmap?.recycle()
                } catch (e: Exception) {}
                lastCachedBitmap = try {
                    cleanBitmap.copy(cleanBitmap.config ?: Bitmap.Config.ARGB_8888, false)
                } catch (e: Exception) {
                    null
                }
            }

            cleanBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error cropping layout bitmap", e)
            null
        } finally {
            img.close()
        }
    }

    private fun stopProjection() {
        captureJob?.cancel()
        _state.value = ServiceState.STOPPED
        _activeProfileId.value = null

        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null

        synchronized(this@ScreenCaptureService) {
            try {
                lastCachedBitmap?.recycle()
            } catch (e: Exception) {}
            lastCachedBitmap = null
        }

        AppLogger.log("Screen capture session stopped.")
    }

    private fun startForegroundWithNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Auto Tap Screen Capturer")
            .setContentText("Mencari gambar target di layar secara real-time...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Auto Tap Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Channel untuk layanan deteksi gambar di latar belakang"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        stopProjection()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val CHANNEL_ID = "AutoTapServiceChannel"
        private const val NOTIFICATION_ID = 4200

        const val ACTION_START_PROJECTION = "com.example.action.START_PROJECTION"
        const val ACTION_START_TAPPING = "com.example.action.START_TAPPING"
        const val ACTION_PAUSE_TAPPING = "com.example.action.PAUSE_TAPPING"
        const val ACTION_RESUME_TAPPING = "com.example.action.RESUME_TAPPING"
        const val ACTION_STOP = "com.example.action.STOP"

        const val EXTRA_RESULT_CODE = "com.example.extra.RESULT_CODE"
        const val EXTRA_RESULT_DATA = "com.example.extra.RESULT_DATA"
        const val EXTRA_PROFILE_ID = "com.example.extra.PROFILE_ID"

        private val _state = MutableStateFlow(ServiceState.STOPPED)
        val state: StateFlow<ServiceState> = _state

        private val _activeProfileId = MutableStateFlow<Long?>(null)
        val activeProfileId: StateFlow<Long?> = _activeProfileId

        private val _totalTapCount = MutableStateFlow(0)
        val totalTapCount: StateFlow<Int> = _totalTapCount

        private val _matchHighlights = MutableSharedFlow<MatchHighlightEvent>(extraBufferCapacity = 10)
        val matchHighlights: SharedFlow<MatchHighlightEvent> = _matchHighlights
    }
}
