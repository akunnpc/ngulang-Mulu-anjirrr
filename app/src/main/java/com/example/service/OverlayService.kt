package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.InputType
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.data.AppDatabase
import com.example.data.Profile
import com.example.data.ProfileRepository
import com.example.data.TargetImage
import com.example.service.ScreenCaptureService.Companion.ACTION_PAUSE_TAPPING
import com.example.service.ScreenCaptureService.Companion.ACTION_RESUME_TAPPING
import com.example.service.ScreenCaptureService.Companion.ACTION_START_TAPPING
import com.example.service.ScreenCaptureService.Companion.ACTION_STOP
import com.example.service.ScreenCaptureService.Companion.EXTRA_PROFILE_ID
import com.example.ui.overlay.HighlightView
import com.example.ui.overlay.TargetPinView
import com.example.util.AppLogger
import com.example.util.CalibrationPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverlayService : Service() {

    private val job = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + job)

    private var windowManager: WindowManager? = null
    private var overlayView: FrameLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var isExpanded = false
    private var arePinsVisible = true
    private var currentProfileId: Long = -1L
    private lateinit var repository: ProfileRepository

    // Floating Target Pins
    private val activePins = mutableListOf<TargetPinView>()
    private var activeConfigDialogView: View? = null
    private var activeCalibrationDialogView: View? = null
    private var displayListener: DisplayManager.DisplayListener? = null

    // programmatically created subviews
    private var collapsedLayout: LinearLayout? = null
    private var expandedLayout: LinearLayout? = null
    private var playPauseButton: ImageView? = null
    private var togglePinsButton: ImageView? = null
    private var statusText: TextView? = null
    private var profileNameText: TextView? = null
    private var tapCountText: TextView? = null
    private var highlightView: HighlightView? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val db = AppDatabase.getDatabase(this)
        repository = ProfileRepository(db.autoTapDao())

        // Initialize the full-screen transparent highlighting overlay
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val highlightParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or 
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        highlightView = HighlightView(this)
        windowManager?.addView(highlightView, highlightParams)

        // Register display listener for screen rotation changes
        val dm = getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
            override fun onDisplayChanged(displayId: Int) {
                updateHighlightViewLayout()
            }
        }
        dm?.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))

        createFloatingWidget()
        observeServiceState()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateHighlightViewLayout()
    }

    private fun updateHighlightViewLayout() {
        val wm = windowManager ?: return
        val hv = highlightView ?: return
        try {
            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            val highlightParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or 
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            wm.updateViewLayout(hv, highlightParams)
            hv.requestLayout()
            hv.invalidate()
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memperbarui layout highlightView", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val profileName = intent?.getStringExtra(EXTRA_PROFILE_NAME) ?: "No Profile"
        profileNameText?.text = profileName
        val profileId = intent?.getLongExtra(EXTRA_PROFILE_ID, -1L) ?: -1L
        if (profileId != -1L) {
            currentProfileId = profileId
            loadExistingPointTargets(profileId)
        }
        return START_NOT_STICKY
    }

    private fun loadExistingPointTargets(profileId: Long) {
        serviceScope.launch(Dispatchers.IO) {
            var data = repository.getProfileWithTargetsDirect(profileId)
            if (data != null && data.targets.isEmpty()) {
                val dm = resources.displayMetrics
                val defaultTarget = TargetImage(
                    profileId = profileId,
                    name = "Titik 1",
                    filePath = "",
                    threshold = 0.8f,
                    delayAfterTapMs = 500L,
                    maxTaps = 0,
                    priority = 0,
                    targetType = "POINT",
                    pointX = dm.widthPixels / 2f,
                    pointY = dm.heightPixels / 2f,
                    actionType = "TAP"
                )
                repository.insertTargetImage(defaultTarget)
                data = repository.getProfileWithTargetsDirect(profileId)
            }
            withContext(Dispatchers.Main) {
                // Clear active pins
                clearAllPins()
                val pointTargets = data?.targets?.filter { it.targetType == "POINT" } ?: emptyList()
                pointTargets.sortedBy { it.priority }.forEachIndexed { index, target ->
                    addPinViewToScreen(target.pointX, target.pointY, index + 1, target.id)
                }
            }
        }
    }

    private fun clearAllPins() {
        activePins.forEach { pin ->
            try { windowManager?.removeView(pin) } catch (e: Exception) {}
        }
        activePins.clear()
    }

    private fun addPinViewToScreen(x: Float, y: Float, stepNumber: Int, targetId: Long): TargetPinView {
        val wm = windowManager ?: return TargetPinView(this, stepNumber, targetId, { _, _, _ -> }, {})
        val pinView = TargetPinView(
            context = this,
            stepNumber = stepNumber,
            targetId = targetId,
            onPositionChanged = { pin, newX, newY ->
                serviceScope.launch(Dispatchers.IO) {
                    val data = repository.getProfileWithTargetsDirect(currentProfileId)
                    val existingTarget = data?.targets?.find { it.id == pin.targetId }
                    if (existingTarget != null) {
                        repository.updateTargetImage(existingTarget.copy(pointX = newX, pointY = newY))
                        AppLogger.log("Titik Sentuh #${pin.stepNumber} dipindah ke (${newX.toInt()}, ${newY.toInt()})")
                    }
                }
            },
            onPinClicked = { pin ->
                showPinConfigDialog(pin)
            }
        )

        // Set initial coordinates
        val pinSize = (48 * resources.displayMetrics.density).toInt()
        pinView.layoutParamsRef.x = (x - pinSize / 2f).toInt().coerceAtLeast(0)
        pinView.layoutParamsRef.y = (y - pinSize / 2f).toInt().coerceAtLeast(0)

        wm.addView(pinView, pinView.layoutParamsRef)
        activePins.add(pinView)

        if (ScreenCaptureService.state.value == ScreenCaptureService.ServiceState.RUNNING) {
            pinView.setTouchable(false, wm)
        }

        return pinView
    }

    private fun dismissCalibrationDialog() {
        activeCalibrationDialogView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing calibration dialog view", e)
            }
            activeCalibrationDialogView = null
        }
    }

    private fun getRealScreenMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val defaultDisplay = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        val wm = windowManager ?: (getSystemService(Context.WINDOW_SERVICE) as WindowManager)
        if (defaultDisplay != null) {
            @Suppress("DEPRECATION")
            defaultDisplay.getRealMetrics(metrics)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.maximumWindowMetrics.bounds
            metrics.widthPixels = bounds.width()
            metrics.heightPixels = bounds.height()
            metrics.densityDpi = resources.configuration.densityDpi
        } else {
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
        }

        val mode = CalibrationPrefs.getOrientationMode(this)
        val rotation = defaultDisplay?.rotation ?: Surface.ROTATION_0
        val isLandscape = when (mode) {
            CalibrationPrefs.MODE_LANDSCAPE -> true
            CalibrationPrefs.MODE_PORTRAIT -> false
            else -> rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270 || (metrics.widthPixels > metrics.heightPixels)
        }

        if (isLandscape && metrics.widthPixels < metrics.heightPixels) {
            val temp = metrics.widthPixels
            metrics.widthPixels = metrics.heightPixels
            metrics.heightPixels = temp
        } else if (!isLandscape && metrics.widthPixels > metrics.heightPixels) {
            val temp = metrics.widthPixels
            metrics.widthPixels = metrics.heightPixels
            metrics.heightPixels = temp
        }
        return metrics
    }

    private fun showCalibrationDialog() {
        dismissCalibrationDialog()
        val wm = windowManager ?: return
        val context = this
        val density = resources.displayMetrics.density

        val realMetrics = getRealScreenMetrics()
        val realW = realMetrics.widthPixels
        val realH = realMetrics.heightPixels
        val isLandscape = realW > realH

        var currentMode = CalibrationPrefs.getOrientationMode(context)
        var currentOffsetX = CalibrationPrefs.getGlobalOffsetX(context)
        var currentOffsetY = CalibrationPrefs.getGlobalOffsetY(context)

        // Main full-screen translucent overlay container
        val rootLayout = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#99000000"))
        }

        // Dialog Card
        val dialogCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            val bg = GradientDrawable().apply {
                cornerRadius = dpToPx(16).toFloat()
                setColor(Color.parseColor("#161922"))
                setStroke(dpToPx(2), Color.parseColor("#FFD700"))
            }
            background = bg

            val lp = FrameLayout.LayoutParams(
                (340 * density).toInt(),
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
            layoutParams = lp
        }

        // Header Row
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(10) }
        }

        val titleText = TextView(context).apply {
            text = "🎯 Kalibrasi Layar & Titik Klik"
            setTextColor(Color.parseColor("#FFD700"))
            textSize = 16f
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.parseColor("#8899A6"))
            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            setOnClickListener { dismissCalibrationDialog() }
        }
        headerRow.addView(titleText)
        headerRow.addView(closeBtn)
        dialogCard.addView(headerRow)

        // Scrollview
        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (380 * density).toInt()
            )
        }

        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // 1. Info Layar Saat Ini
        val screenInfoBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
            val bg = GradientDrawable().apply {
                cornerRadius = dpToPx(8).toFloat()
                setColor(Color.parseColor("#222834"))
            }
            background = bg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(10) }
        }
        val screenResText = TextView(context).apply {
            text = "Resolusi Layar: ${realW} × ${realH} px"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 12f
            paint.isFakeBoldText = true
        }
        val screenOrientText = TextView(context).apply {
            text = "Orientasi Saat Ini: ${if (isLandscape) "Miring (Landscape)" else "Tegak (Portrait)"}"
            setTextColor(Color.parseColor("#B0BEC5"))
            textSize = 12f
        }
        screenInfoBox.addView(screenResText)
        screenInfoBox.addView(screenOrientText)
        contentLayout.addView(screenInfoBox)

        // 2. Mode Orientasi Paksa
        val modeLabel = TextView(context).apply {
            text = "Mode Orientasi Layar:"
            setTextColor(Color.WHITE)
            textSize = 13f
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(4) }
        }
        contentLayout.addView(modeLabel)

        val modeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(12) }
        }

        val btnAuto = Button(context)
        val btnLandscape = Button(context)
        val btnPortrait = Button(context)

        fun updateModeButtons() {
            val selectedBg = GradientDrawable().apply {
                cornerRadius = dpToPx(8).toFloat()
                setColor(Color.parseColor("#00E5FF"))
            }
            btnAuto.apply {
                background = if (currentMode == CalibrationPrefs.MODE_AUTO) selectedBg else GradientDrawable().apply {
                    cornerRadius = dpToPx(8).toFloat()
                    setColor(Color.parseColor("#2A313E"))
                }
                setTextColor(if (currentMode == CalibrationPrefs.MODE_AUTO) Color.BLACK else Color.WHITE)
            }
            btnLandscape.apply {
                background = if (currentMode == CalibrationPrefs.MODE_LANDSCAPE) selectedBg else GradientDrawable().apply {
                    cornerRadius = dpToPx(8).toFloat()
                    setColor(Color.parseColor("#2A313E"))
                }
                setTextColor(if (currentMode == CalibrationPrefs.MODE_LANDSCAPE) Color.BLACK else Color.WHITE)
            }
            btnPortrait.apply {
                background = if (currentMode == CalibrationPrefs.MODE_PORTRAIT) selectedBg else GradientDrawable().apply {
                    cornerRadius = dpToPx(8).toFloat()
                    setColor(Color.parseColor("#2A313E"))
                }
                setTextColor(if (currentMode == CalibrationPrefs.MODE_PORTRAIT) Color.BLACK else Color.WHITE)
            }
        }

        btnAuto.apply {
            text = "Otomatis"
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(36), 1f).apply { rightMargin = dpToPx(4) }
            setOnClickListener {
                currentMode = CalibrationPrefs.MODE_AUTO
                CalibrationPrefs.setOrientationMode(context, currentMode)
                updateModeButtons()
                Toast.makeText(context, "Orientasi diatur ke Otomatis", Toast.LENGTH_SHORT).show()
            }
        }
        btnLandscape.apply {
            text = "Landscape"
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(36), 1f).apply { rightMargin = dpToPx(4) }
            setOnClickListener {
                currentMode = CalibrationPrefs.MODE_LANDSCAPE
                CalibrationPrefs.setOrientationMode(context, currentMode)
                updateModeButtons()
                Toast.makeText(context, "Orientasi dipaksa ke Landscape (Miring)", Toast.LENGTH_SHORT).show()
            }
        }
        btnPortrait.apply {
            text = "Portrait"
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(36), 1f)
            setOnClickListener {
                currentMode = CalibrationPrefs.MODE_PORTRAIT
                CalibrationPrefs.setOrientationMode(context, currentMode)
                updateModeButtons()
                Toast.makeText(context, "Orientasi dipaksa ke Portrait (Tegak)", Toast.LENGTH_SHORT).show()
            }
        }
        updateModeButtons()
        modeRow.addView(btnAuto)
        modeRow.addView(btnLandscape)
        modeRow.addView(btnPortrait)
        contentLayout.addView(modeRow)

        // 3. Kalibrasi Offset Horizontal (X)
        val xLabel = TextView(context).apply {
            text = "Geser Titik Tengah Horizontal (X):"
            setTextColor(Color.WHITE)
            textSize = 13f
            paint.isFakeBoldText = true
        }
        contentLayout.addView(xLabel)

        val xHint = TextView(context).apply {
            text = "(-) Geser KIRI  |  (+) Geser KANAN"
            setTextColor(Color.parseColor("#8899A6"))
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(4) }
        }
        contentLayout.addView(xHint)

        val xControlRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(12) }
        }

        val editX = EditText(context).apply {
            setText(currentOffsetX.toString())
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 14f
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            val bg = GradientDrawable().apply {
                cornerRadius = dpToPx(6).toFloat()
                setColor(Color.parseColor("#222834"))
            }
            background = bg
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(36), 1f).apply {
                leftMargin = dpToPx(4)
                rightMargin = dpToPx(4)
            }
        }

        fun makeStepButton(label: String, delta: Int, onApply: (Int) -> Unit): Button {
            return Button(context).apply {
                text = label
                textSize = 11f
                setTextColor(Color.WHITE)
                val bg = GradientDrawable().apply {
                    cornerRadius = dpToPx(6).toFloat()
                    setColor(Color.parseColor("#2A313E"))
                }
                background = bg
                layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(36)).apply {
                    rightMargin = dpToPx(2)
                }
                setOnClickListener {
                    onApply(delta)
                }
            }
        }

        val btnXMinus50 = makeStepButton("-50", -50) { delta ->
            val cur = editX.text.toString().toIntOrNull() ?: currentOffsetX
            currentOffsetX = cur + delta
            editX.setText(currentOffsetX.toString())
        }
        val btnXMinus10 = makeStepButton("-10", -10) { delta ->
            val cur = editX.text.toString().toIntOrNull() ?: currentOffsetX
            currentOffsetX = cur + delta
            editX.setText(currentOffsetX.toString())
        }
        val btnXPlus10 = makeStepButton("+10", 10) { delta ->
            val cur = editX.text.toString().toIntOrNull() ?: currentOffsetX
            currentOffsetX = cur + delta
            editX.setText(currentOffsetX.toString())
        }
        val btnXPlus50 = makeStepButton("+50", 50) { delta ->
            val cur = editX.text.toString().toIntOrNull() ?: currentOffsetX
            currentOffsetX = cur + delta
            editX.setText(currentOffsetX.toString())
        }
        xControlRow.addView(btnXMinus50)
        xControlRow.addView(btnXMinus10)
        xControlRow.addView(editX)
        xControlRow.addView(btnXPlus10)
        xControlRow.addView(btnXPlus50)
        contentLayout.addView(xControlRow)

        // 4. Kalibrasi Offset Vertikal (Y)
        val yLabel = TextView(context).apply {
            text = "Geser Titik Tengah Vertikal (Y):"
            setTextColor(Color.WHITE)
            textSize = 13f
            paint.isFakeBoldText = true
        }
        contentLayout.addView(yLabel)

        val yHint = TextView(context).apply {
            text = "(-) Geser ATAS  |  (+) Geser BAWAH"
            setTextColor(Color.parseColor("#8899A6"))
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(4) }
        }
        contentLayout.addView(yHint)

        val yControlRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(14) }
        }

        val editY = EditText(context).apply {
            setText(currentOffsetY.toString())
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 14f
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            val bg = GradientDrawable().apply {
                cornerRadius = dpToPx(6).toFloat()
                setColor(Color.parseColor("#222834"))
            }
            background = bg
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(36), 1f).apply {
                leftMargin = dpToPx(4)
                rightMargin = dpToPx(4)
            }
        }

        val btnYMinus50 = makeStepButton("-50", -50) { delta ->
            val cur = editY.text.toString().toIntOrNull() ?: currentOffsetY
            currentOffsetY = cur + delta
            editY.setText(currentOffsetY.toString())
        }
        val btnYMinus10 = makeStepButton("-10", -10) { delta ->
            val cur = editY.text.toString().toIntOrNull() ?: currentOffsetY
            currentOffsetY = cur + delta
            editY.setText(currentOffsetY.toString())
        }
        val btnYPlus10 = makeStepButton("+10", 10) { delta ->
            val cur = editY.text.toString().toIntOrNull() ?: currentOffsetY
            currentOffsetY = cur + delta
            editY.setText(currentOffsetY.toString())
        }
        val btnYPlus50 = makeStepButton("+50", 50) { delta ->
            val cur = editY.text.toString().toIntOrNull() ?: currentOffsetY
            currentOffsetY = cur + delta
            editY.setText(currentOffsetY.toString())
        }
        yControlRow.addView(btnYMinus50)
        yControlRow.addView(btnYMinus10)
        yControlRow.addView(editY)
        yControlRow.addView(btnYPlus10)
        yControlRow.addView(btnYPlus50)
        contentLayout.addView(yControlRow)

        // 5. Tombol Uji Klik / Test Tap
        val btnTestTap = Button(context).apply {
            text = "🎯 Uji Ketuk (Tes Titik Klik Sekarang)"
            setTextColor(Color.BLACK)
            textSize = 13f
            paint.isFakeBoldText = true
            val bg = GradientDrawable().apply {
                cornerRadius = dpToPx(8).toFloat()
                setColor(Color.parseColor("#00E5FF"))
            }
            background = bg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(40)
            ).apply { bottomMargin = dpToPx(14) }

            setOnClickListener {
                val offX = editX.text.toString().toIntOrNull() ?: currentOffsetX
                val offY = editY.text.toString().toIntOrNull() ?: currentOffsetY
                currentOffsetX = offX
                currentOffsetY = offY

                val testX = ((realW / 2f) + offX).coerceIn(0f, realW.toFloat())
                val testY = ((realH / 2f) + offY).coerceIn(0f, realH.toFloat())

                val testRect = Rect(
                    (testX - 40).toInt().coerceAtLeast(0),
                    (testY - 40).toInt().coerceAtLeast(0),
                    (testX + 40).toInt(),
                    (testY + 40).toInt()
                )
                highlightView?.showHighlight(testRect, testX, testY, "Uji Titik Klik")

                serviceScope.launch {
                    val tapped = TapAccessibilityService.performTapSuspend(testX, testY)
                    withContext(Dispatchers.Main) {
                        if (tapped) {
                            Toast.makeText(context, "Ketukan diuji di (${testX.toInt()}, ${testY.toInt()})", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "⚠️ Aksesibilitas tidak aktif. Aktifkan di pengaturan.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        contentLayout.addView(btnTestTap)

        // 6. Batas Layar / Margin Notch Preset (Mirip Crop)
        val boundsLabel = TextView(context).apply {
            text = "Batas Tepi Layar Game (Safe-Margin Notch):"
            setTextColor(Color.WHITE)
            textSize = 13f
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(4) }
        }
        contentLayout.addView(boundsLabel)

        val boundsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(14) }
        }

        fun makeBoundsButton(label: String, margin: Int): Button {
            return Button(context).apply {
                text = label
                textSize = 11f
                setTextColor(Color.WHITE)
                val bg = GradientDrawable().apply {
                    cornerRadius = dpToPx(8).toFloat()
                    setColor(Color.parseColor("#2A313E"))
                }
                background = bg
                layoutParams = LinearLayout.LayoutParams(0, dpToPx(36), 1f).apply {
                    rightMargin = dpToPx(4)
                }
                setOnClickListener {
                    CalibrationPrefs.setMargins(context, margin, 0, margin, 0)
                    Toast.makeText(context, "Batas layar: margin $margin px", Toast.LENGTH_SHORT).show()
                }
            }
        }

        boundsRow.addView(makeBoundsButton("Penuh (0px)", 0))
        boundsRow.addView(makeBoundsButton("Notch 60px", 60))
        boundsRow.addView(makeBoundsButton("Notch 100px", 100))
        contentLayout.addView(boundsRow)

        scrollView.addView(contentLayout)
        dialogCard.addView(scrollView)

        // Footer Action Buttons
        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(12) }
        }

        val btnReset = Button(context).apply {
            text = "Reset 0"
            setTextColor(Color.parseColor("#FF9800"))
            val bg = GradientDrawable().apply {
                cornerRadius = dpToPx(8).toFloat()
                setColor(Color.parseColor("#222834"))
            }
            background = bg
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(40), 1f).apply {
                rightMargin = dpToPx(6)
            }
            setOnClickListener {
                CalibrationPrefs.reset(context)
                currentOffsetX = 0
                currentOffsetY = 0
                editX.setText("0")
                editY.setText("0")
                currentMode = CalibrationPrefs.MODE_AUTO
                updateModeButtons()
                Toast.makeText(context, "Kalibrasi di-reset ke default", Toast.LENGTH_SHORT).show()
            }
        }

        val btnSave = Button(context).apply {
            text = "Simpan & Terapkan"
            setTextColor(Color.BLACK)
            paint.isFakeBoldText = true
            val bg = GradientDrawable().apply {
                cornerRadius = dpToPx(8).toFloat()
                setColor(Color.parseColor("#00E5FF"))
            }
            background = bg
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(40), 1.5f)
            setOnClickListener {
                val offX = editX.text.toString().toIntOrNull() ?: currentOffsetX
                val offY = editY.text.toString().toIntOrNull() ?: currentOffsetY
                CalibrationPrefs.setGlobalOffsetX(context, offX)
                CalibrationPrefs.setGlobalOffsetY(context, offY)
                CalibrationPrefs.setOrientationMode(context, currentMode)
                Toast.makeText(context, "Kalibrasi tersimpan! Offset: ($offX, $offY)", Toast.LENGTH_SHORT).show()
                dismissCalibrationDialog()
            }
        }

        actionRow.addView(btnReset)
        actionRow.addView(btnSave)
        dialogCard.addView(actionRow)

        rootLayout.addView(dialogCard)
        rootLayout.setOnClickListener {
            dismissCalibrationDialog()
        }
        dialogCard.setOnClickListener {
            // consume clicks
        }

        val dialogParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        activeCalibrationDialogView = rootLayout
        try {
            wm.addView(rootLayout, dialogParams)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menampilkan dialog kalibrasi", e)
        }
    }

    private fun dismissPinConfigDialog() {
        activeConfigDialogView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing config dialog view", e)
            }
            activeConfigDialogView = null
        }
    }

    private fun showPinConfigDialog(pin: TargetPinView) {
        dismissPinConfigDialog()
        val wm = windowManager ?: return

        serviceScope.launch(Dispatchers.IO) {
            val data = repository.getProfileWithTargetsDirect(currentProfileId)
            val target = data?.targets?.find { it.id == pin.targetId }
            if (target == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@OverlayService, "Target titik tidak ditemukan.", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                val context = this@OverlayService
                val density = resources.displayMetrics.density

                // Main full-screen translucent overlay container
                val rootLayout = FrameLayout(context).apply {
                    setBackgroundColor(Color.parseColor("#99000000")) // semi-transparent dark backdrop
                }

                // Inner Dialog Card Container
                val dialogCard = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
                    val bg = GradientDrawable().apply {
                        cornerRadius = dpToPx(16).toFloat()
                        setColor(Color.parseColor("#161922")) // Dark Slate background
                        setStroke(dpToPx(2), Color.parseColor("#00E5FF")) // Cyber Cyan border
                    }
                    background = bg

                    val lp = FrameLayout.LayoutParams(
                        (320 * density).toInt(),
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.CENTER
                    }
                    layoutParams = lp
                }

                // Header Row
                val headerRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dpToPx(12) }
                }

                val titleText = TextView(context).apply {
                    text = "⚙️ Pengaturan Titik #${pin.stepNumber}"
                    setTextColor(Color.parseColor("#00E5FF"))
                    textSize = 16f
                    paint.isFakeBoldText = true
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val closeBtn = ImageView(context).apply {
                    setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                    setColorFilter(Color.parseColor("#8899A6"))
                    setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
                    setOnClickListener { dismissPinConfigDialog() }
                }
                headerRow.addView(titleText)
                headerRow.addView(closeBtn)
                dialogCard.addView(headerRow)

                // Scrollview for form fields
                val scrollView = ScrollView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                val formLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                }

                // 1. Label & Name Field
                val nameLabel = TextView(context).apply {
                    text = "Nama Titik / Tombol:"
                    setTextColor(Color.parseColor("#8899A6"))
                    textSize = 12f
                }
                val nameInput = EditText(context).apply {
                    setText(target.name)
                    setTextColor(Color.WHITE)
                    setHintTextColor(Color.parseColor("#556070"))
                    hint = "Misal: Tombol Pasukan A"
                    textSize = 14f
                    val inputBg = GradientDrawable().apply {
                        cornerRadius = dpToPx(8).toFloat()
                        setColor(Color.parseColor("#222834"))
                        setStroke(dpToPx(1), Color.parseColor("#333D4F"))
                    }
                    background = inputBg
                    setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dpToPx(4)
                        bottomMargin = dpToPx(12)
                    }
                }
                formLayout.addView(nameLabel)
                formLayout.addView(nameInput)

                // 2. Action Type (Tap vs Hold)
                val actionLabel = TextView(context).apply {
                    text = "Tipe Tindakan:"
                    setTextColor(Color.parseColor("#8899A6"))
                    textSize = 12f
                }
                val actionRadioGroup = RadioGroup(context).apply {
                    orientation = RadioGroup.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dpToPx(4)
                        bottomMargin = dpToPx(8)
                    }
                }
                val tapRadio = RadioButton(context).apply {
                    text = "Ketuk (Tap)"
                    setTextColor(Color.WHITE)
                    textSize = 13f
                    id = View.generateViewId()
                }
                val holdRadio = RadioButton(context).apply {
                    text = "Tahan (Hold)"
                    setTextColor(Color.WHITE)
                    textSize = 13f
                    id = View.generateViewId()
                }
                actionRadioGroup.addView(tapRadio)
                actionRadioGroup.addView(holdRadio)
                if (target.actionType == "LONG_PRESS") {
                    holdRadio.isChecked = true
                } else {
                    tapRadio.isChecked = true
                }
                formLayout.addView(actionLabel)
                formLayout.addView(actionRadioGroup)

                // Hold Duration Input Container
                val holdContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    visibility = if (target.actionType == "LONG_PRESS") View.VISIBLE else View.GONE
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dpToPx(10) }
                }
                val holdLabel = TextView(context).apply {
                    text = "Durasi Tahan (ms):"
                    setTextColor(Color.parseColor("#8899A6"))
                    textSize = 12f
                }
                val holdInput = EditText(context).apply {
                    inputType = InputType.TYPE_CLASS_NUMBER
                    setText(target.holdDurationMs.toString())
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    val inputBg = GradientDrawable().apply {
                        cornerRadius = dpToPx(8).toFloat()
                        setColor(Color.parseColor("#222834"))
                        setStroke(dpToPx(1), Color.parseColor("#333D4F"))
                    }
                    background = inputBg
                    setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dpToPx(4) }
                }
                holdContainer.addView(holdLabel)
                holdContainer.addView(holdInput)
                formLayout.addView(holdContainer)

                actionRadioGroup.setOnCheckedChangeListener { _, checkedId ->
                    holdContainer.visibility = if (checkedId == holdRadio.id) View.VISIBLE else View.GONE
                }

                // 3. Delay / Jeda Waktu Setelah Sentuh (ms, Detik, Menit)
                val delaySectionTitle = TextView(context).apply {
                    text = "⏱️ Delay / Interval Setelah Sentuh:"
                    setTextColor(Color.parseColor("#00E5FF"))
                    textSize = 13f
                    paint.isFakeBoldText = true
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dpToPx(4) }
                }
                formLayout.addView(delaySectionTitle)

                // Determine initial unit and value from target.delayAfterTapMs
                val rawDelay = target.delayAfterTapMs
                var initialUnit = "ms"
                var initialValueStr = rawDelay.toString()
                if (rawDelay >= 60000L && rawDelay % 60000L == 0L) {
                    initialUnit = "mnt"
                    initialValueStr = (rawDelay / 60000L).toString()
                } else if (rawDelay >= 1000L && rawDelay % 1000L == 0L) {
                    initialUnit = "dtk"
                    initialValueStr = (rawDelay / 1000L).toString()
                }

                val delayInputRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dpToPx(4) }
                }

                val delayInput = EditText(context).apply {
                    inputType = InputType.TYPE_CLASS_NUMBER
                    setText(initialValueStr)
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    val inputBg = GradientDrawable().apply {
                        cornerRadius = dpToPx(8).toFloat()
                        setColor(Color.parseColor("#222834"))
                        setStroke(dpToPx(1), Color.parseColor("#333D4F"))
                    }
                    background = inputBg
                    setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
                    layoutParams = LinearLayout.LayoutParams(
                        dpToPx(85),
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                val unitRadioGroup = RadioGroup(context).apply {
                    orientation = RadioGroup.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { leftMargin = dpToPx(4) }
                }

                val radioMs = RadioButton(context).apply {
                    text = "ms"
                    setTextColor(Color.WHITE)
                    textSize = 11f
                    id = View.generateViewId()
                }
                val radioSec = RadioButton(context).apply {
                    text = "Detik"
                    setTextColor(Color.WHITE)
                    textSize = 11f
                    id = View.generateViewId()
                }
                val radioMin = RadioButton(context).apply {
                    text = "Menit"
                    setTextColor(Color.WHITE)
                    textSize = 11f
                    id = View.generateViewId()
                }
                unitRadioGroup.addView(radioMs)
                unitRadioGroup.addView(radioSec)
                unitRadioGroup.addView(radioMin)

                when (initialUnit) {
                    "mnt" -> radioMin.isChecked = true
                    "dtk" -> radioSec.isChecked = true
                    else -> radioMs.isChecked = true
                }

                delayInputRow.addView(delayInput)
                delayInputRow.addView(unitRadioGroup)
                formLayout.addView(delayInputRow)

                // Subtitle preview
                val delayPreviewText = TextView(context).apply {
                    text = "Jeda eksekusi: $rawDelay ms (${rawDelay / 1000f} detik)"
                    setTextColor(Color.parseColor("#00E676"))
                    textSize = 11f
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dpToPx(4)
                        bottomMargin = dpToPx(10)
                    }
                }
                formLayout.addView(delayPreviewText)

                val updatePreview = {
                    val num = delayInput.text.toString().toDoubleOrNull() ?: 0.0
                    val multiplier = when (unitRadioGroup.checkedRadioButtonId) {
                        radioMin.id -> 60000L
                        radioSec.id -> 1000L
                        else -> 1L
                    }
                    val finalMs = (num * multiplier).toLong()
                    delayPreviewText.text = "Jeda eksekusi: $finalMs ms (${finalMs / 1000f} detik)"
                }

                delayInput.addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updatePreview() }
                    override fun afterTextChanged(s: android.text.Editable?) {}
                })
                unitRadioGroup.setOnCheckedChangeListener { _, _ -> updatePreview() }

                // 4. Coordinates Info
                val coordText = TextView(context).apply {
                    text = "📍 Posisi Titik: (${target.pointX.toInt()} px, ${target.pointY.toInt()} px)"
                    setTextColor(Color.parseColor("#8899A6"))
                    textSize = 11f
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dpToPx(14) }
                }
                formLayout.addView(coordText)

                // 5. Action Buttons (Hapus, Batal, Simpan)
                val buttonsRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                // Delete Button
                val deleteBtn = Button(context).apply {
                    text = "Hapus"
                    setTextColor(Color.WHITE)
                    textSize = 12f
                    paint.isFakeBoldText = true
                    val btnBg = GradientDrawable().apply {
                        cornerRadius = dpToPx(8).toFloat()
                        setColor(Color.parseColor("#D32F2F"))
                    }
                    background = btnBg
                    layoutParams = LinearLayout.LayoutParams(0, dpToPx(38), 1f).apply { rightMargin = dpToPx(6) }
                    setOnClickListener {
                        serviceScope.launch(Dispatchers.IO) {
                            repository.deleteTargetImage(target)
                            AppLogger.log("Titik Sentuh #${pin.stepNumber} dihapus.")
                            withContext(Dispatchers.Main) {
                                dismissPinConfigDialog()
                                loadExistingPointTargets(currentProfileId)
                                Toast.makeText(this@OverlayService, "Titik #${pin.stepNumber} telah dihapus", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                // Cancel Button
                val cancelBtn = Button(context).apply {
                    text = "Batal"
                    setTextColor(Color.WHITE)
                    textSize = 12f
                    val btnBg = GradientDrawable().apply {
                        cornerRadius = dpToPx(8).toFloat()
                        setColor(Color.parseColor("#333742"))
                    }
                    background = btnBg
                    layoutParams = LinearLayout.LayoutParams(0, dpToPx(38), 1f).apply { rightMargin = dpToPx(6) }
                    setOnClickListener { dismissPinConfigDialog() }
                }

                // Save Button
                val saveBtn = Button(context).apply {
                    text = "Simpan"
                    setTextColor(Color.parseColor("#121212"))
                    textSize = 12f
                    paint.isFakeBoldText = true
                    val btnBg = GradientDrawable().apply {
                        cornerRadius = dpToPx(8).toFloat()
                        setColor(Color.parseColor("#00E5FF")) // Cyber Cyan
                    }
                    background = btnBg
                    layoutParams = LinearLayout.LayoutParams(0, dpToPx(38), 1.2f)
                    setOnClickListener {
                        val inputNum = delayInput.text.toString().toDoubleOrNull() ?: 500.0
                        val multiplier = when (unitRadioGroup.checkedRadioButtonId) {
                            radioMin.id -> 60000L
                            radioSec.id -> 1000L
                            else -> 1L
                        }
                        val finalDelayMs = (inputNum * multiplier).toLong().coerceAtLeast(10L)

                        val actType = if (holdRadio.isChecked) "LONG_PRESS" else "TAP"
                        val holdMs = holdInput.text.toString().toLongOrNull() ?: 1000L
                        val newName = if (nameInput.text.isNotBlank()) nameInput.text.toString().trim() else target.name

                        serviceScope.launch(Dispatchers.IO) {
                            val updated = target.copy(
                                name = newName,
                                actionType = actType,
                                holdDurationMs = holdMs,
                                delayAfterTapMs = finalDelayMs
                            )
                            repository.updateTargetImage(updated)
                            AppLogger.log("Pengaturan Titik #${pin.stepNumber} disimpan: Delay $finalDelayMs ms ($actType)")
                            withContext(Dispatchers.Main) {
                                dismissPinConfigDialog()
                                Toast.makeText(this@OverlayService, "Pengaturan Titik #${pin.stepNumber} disimpan! (Delay: $finalDelayMs ms)", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                buttonsRow.addView(deleteBtn)
                buttonsRow.addView(cancelBtn)
                buttonsRow.addView(saveBtn)
                formLayout.addView(buttonsRow)

                scrollView.addView(formLayout)
                dialogCard.addView(scrollView)
                rootLayout.addView(dialogCard)

                // Root backdrop click dismisses dialog
                rootLayout.setOnClickListener { dismissPinConfigDialog() }
                dialogCard.setOnClickListener { /* prevent dismiss when clicking dialog card */ }

                // WindowManager LayoutParams for interactive dialog window
                val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }

                val dialogParams = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.CENTER
                    softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                }

                wm.addView(rootLayout, dialogParams)
                activeConfigDialogView = rootLayout
            }
        }
    }

    private fun addNewManualPoint() {
        if (currentProfileId == -1L) {
            Toast.makeText(this, "Silakan pilih atau buat profil terlebih dahulu.", Toast.LENGTH_SHORT).show()
            return
        }

        serviceScope.launch(Dispatchers.IO) {
            val data = repository.getProfileWithTargetsDirect(currentProfileId)
            val currentCount = data?.targets?.size ?: 0
            val pointCount = data?.targets?.count { it.targetType == "POINT" } ?: 0
            val nextStepNumber = pointCount + 1

            // Place in a convenient visible area (center screen offset)
            val dm = resources.displayMetrics
            val spawnX = dm.widthPixels / 2f
            val spawnY = (dm.heightPixels / 3f) + (pointCount * 80)

            val newTarget = TargetImage(
                profileId = currentProfileId,
                name = "Titik $nextStepNumber",
                filePath = "",
                threshold = 0.8f,
                delayAfterTapMs = 500L,
                maxTaps = 0,
                priority = currentCount,
                targetType = "POINT",
                pointX = spawnX,
                pointY = spawnY
            )

            val insertedId = repository.insertTargetImage(newTarget)
            AppLogger.log("Titik Sentuh $nextStepNumber ditambahkan pada ($spawnX, $spawnY)")

            withContext(Dispatchers.Main) {
                addPinViewToScreen(spawnX, spawnY, nextStepNumber, insertedId)
                Toast.makeText(this@OverlayService, "Titik Sentuh $nextStepNumber dibuat! Geser pin ke tombol yang diinginkan.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun removeLastManualPoint() {
        if (activePins.isEmpty()) {
            Toast.makeText(this, "Tidak ada titik sentuh manual untuk dihapus.", Toast.LENGTH_SHORT).show()
            return
        }

        val lastPin = activePins.removeAt(activePins.size - 1)
        try { windowManager?.removeView(lastPin) } catch (e: Exception) {}

        serviceScope.launch(Dispatchers.IO) {
            val data = repository.getProfileWithTargetsDirect(currentProfileId)
            val targetToDelete = data?.targets?.find { it.id == lastPin.targetId }
            if (targetToDelete != null) {
                repository.deleteTargetImage(targetToDelete)
                AppLogger.log("Titik Sentuh ${lastPin.stepNumber} telah dihapus.")
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(this@OverlayService, "Titik Sentuh ${lastPin.stepNumber} dihapus.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun togglePinsVisibility() {
        arePinsVisible = !arePinsVisible
        activePins.forEach { pin ->
            pin.visibility = if (arePinsVisible) View.VISIBLE else View.GONE
        }
        togglePinsButton?.setColorFilter(if (arePinsVisible) Color.parseColor("#00E5FF") else Color.parseColor("#757575"))
        Toast.makeText(this, if (arePinsVisible) "Titik sentuh ditampilkan" else "Titik sentuh disembunyikan", Toast.LENGTH_SHORT).show()
    }

    private fun createFloatingWidget() {
        val context = this
        overlayView = FrameLayout(context)

        // Setup layout params for System Overlay
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 60
            y = 180
        }

        // --- DRAWABLES (Rounded corners programmatically) ---
        val collapsedBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#121212")) // Dark Onyx
            setStroke(dpToPx(2), Color.parseColor("#00E5FF")) // Cyan Accent ring
        }

        val expandedBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(16).toFloat()
            setColor(Color.parseColor("#161922")) // Dark Slate
            setStroke(dpToPx(2), Color.parseColor("#00E5FF")) // Neon Cyan border
        }

        // ================= COLLAPSED LAYOUT =================
        collapsedLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = collapsedBg
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            
            // Icon (Target crosshair)
            val icon = ImageView(context).apply {
                setImageResource(android.R.drawable.ic_menu_compass)
                setColorFilter(Color.parseColor("#00E5FF")) // Cyan
                layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40))
            }
            addView(icon)
        }

        // ================= EXPANDED CONTROLLER LAYOUT =================
        expandedLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = expandedBg
            visibility = View.GONE
            setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))

            // Text Info Panel
            val infoLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    rightMargin = dpToPx(8)
                }
            }

            profileNameText = TextView(context).apply {
                text = "Auto Tap"
                setTextColor(Color.WHITE)
                textSize = 12f
                paint.isFakeBoldText = true
            }
            infoLayout.addView(profileNameText)

            statusText = TextView(context).apply {
                text = "Idle"
                setTextColor(Color.parseColor("#AAAAAA"))
                textSize = 10f
            }
            infoLayout.addView(statusText)

            tapCountText = TextView(context).apply {
                text = "Ketukan: 0"
                setTextColor(Color.parseColor("#00E676")) // Vibrant neon green
                textSize = 10f
                paint.isFakeBoldText = true
            }
            infoLayout.addView(tapCountText)

            addView(infoLayout)

            // Button 1: Play/Pause
            playPauseButton = ImageView(context).apply {
                setImageResource(android.R.drawable.ic_media_play)
                setColorFilter(Color.parseColor("#00E676")) // Neon green
                setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
                layoutParams = LinearLayout.LayoutParams(dpToPx(36), dpToPx(36)).apply {
                    rightMargin = dpToPx(6)
                }
                isClickable = true
                
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#222834"))
                }
                background = bg

                setOnClickListener {
                    toggleTappingState()
                }
            }
            addView(playPauseButton)

            // Button 2: Tambah Titik Manual (+)
            val addPointButton = ImageView(context).apply {
                setImageResource(android.R.drawable.ic_input_add)
                setColorFilter(Color.parseColor("#00E5FF")) // Cyan
                setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
                layoutParams = LinearLayout.LayoutParams(dpToPx(36), dpToPx(36)).apply {
                    rightMargin = dpToPx(6)
                }
                isClickable = true
                
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#222834"))
                }
                background = bg

                setOnClickListener {
                    addNewManualPoint()
                }
            }
            addView(addPointButton)

            // Button 3: Hapus Titik Terakhir (-)
            val removePointButton = ImageView(context).apply {
                setImageResource(android.R.drawable.ic_delete)
                setColorFilter(Color.parseColor("#FF9800")) // Orange
                setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
                layoutParams = LinearLayout.LayoutParams(dpToPx(36), dpToPx(36)).apply {
                    rightMargin = dpToPx(6)
                }
                isClickable = true
                
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#222834"))
                }
                background = bg

                setOnClickListener {
                    removeLastManualPoint()
                }
            }
            addView(removePointButton)

            // Button 4: Toggle Pin Visibility (Eye icon)
            togglePinsButton = ImageView(context).apply {
                setImageResource(android.R.drawable.ic_menu_view)
                setColorFilter(Color.parseColor("#00E5FF")) // Cyan
                setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
                layoutParams = LinearLayout.LayoutParams(dpToPx(36), dpToPx(36)).apply {
                    rightMargin = dpToPx(6)
                }
                isClickable = true
                
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#222834"))
                }
                background = bg

                setOnClickListener {
                    togglePinsVisibility()
                }
            }
            addView(togglePinsButton)

            // Button 5: Kalibrasi Titik & Batas Layar
            val calibrateButton = ImageView(context).apply {
                setImageResource(android.R.drawable.ic_menu_crop)
                setColorFilter(Color.parseColor("#FFD700")) // Gold
                setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
                layoutParams = LinearLayout.LayoutParams(dpToPx(36), dpToPx(36)).apply {
                    rightMargin = dpToPx(6)
                }
                isClickable = true
                contentDescription = "Kalibrasi Titik & Batas Layar"
                
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#222834"))
                }
                background = bg

                setOnClickListener {
                    showCalibrationDialog()
                }
            }
            addView(calibrateButton)

            // Button 6: Minimize / Collapse
            val minimizeButton = ImageView(context).apply {
                setImageResource(android.R.drawable.ic_media_previous)
                setColorFilter(Color.parseColor("#AAAAAA"))
                setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
                layoutParams = LinearLayout.LayoutParams(dpToPx(36), dpToPx(36)).apply {
                    rightMargin = dpToPx(6)
                }
                isClickable = true
                
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#222834"))
                }
                background = bg

                setOnClickListener {
                    setExpanded(false)
                }
            }
            addView(minimizeButton)

            // Button 6: Stop/Close Overlay
            val stopButton = ImageView(context).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                setColorFilter(Color.parseColor("#FF1744")) // Vibrant Red
                setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
                layoutParams = LinearLayout.LayoutParams(dpToPx(36), dpToPx(36))
                isClickable = true
                
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#222834"))
                }
                background = bg

                setOnClickListener {
                    stopAllServices()
                }
            }
            addView(stopButton)
        }

        // Add sublayouts to frame container
        overlayView?.addView(collapsedLayout)
        overlayView?.addView(expandedLayout)

        // Set touch/drag listeners
        setupDragListener()

        // Inflate view into WindowManager
        windowManager?.addView(overlayView, layoutParams)
    }

    private fun toggleTappingState() {
        val currentState = ScreenCaptureService.state.value
        
        if (currentState != ScreenCaptureService.ServiceState.RUNNING && !TapAccessibilityService.isRunning()) {
            Toast.makeText(this, "⚠️ Layanan Aksesibilitas belum aktif! Silakan aktifkan di Pengaturan agar auto-clicker dapat menekan layar.", Toast.LENGTH_LONG).show()
            try {
                val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Cannot open accessibility settings", e)
            }
            return
        }

        val intent = Intent(this, ScreenCaptureService::class.java).apply {
            if (currentState == ScreenCaptureService.ServiceState.RUNNING) {
                action = ACTION_PAUSE_TAPPING
            } else {
                action = ACTION_START_TAPPING
                putExtra(EXTRA_PROFILE_ID, currentProfileId)
            }
        }
        startService(intent)
    }

    private fun stopAllServices() {
        // Stop screen capturing
        val stopCaptureIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ACTION_STOP
        }
        startService(stopCaptureIntent)

        // Stop this overlay service
        stopSelf()
    }

    private fun setPinsTouchable(touchable: Boolean) {
        val wm = windowManager ?: return
        activePins.forEach { pin ->
            pin.setTouchable(touchable, wm)
        }
    }

    private fun observeServiceState() {
        serviceScope.launch {
            ScreenCaptureService.state.collect { state ->
                when (state) {
                    ScreenCaptureService.ServiceState.STOPPED -> {
                        statusText?.text = "Stopped"
                        playPauseButton?.setImageResource(android.R.drawable.ic_media_play)
                        playPauseButton?.setColorFilter(Color.parseColor("#00E676"))
                        setPinsTouchable(true)
                    }
                    ScreenCaptureService.ServiceState.IDLE -> {
                        statusText?.text = "Ready"
                        playPauseButton?.setImageResource(android.R.drawable.ic_media_play)
                        playPauseButton?.setColorFilter(Color.parseColor("#00E676"))
                        setPinsTouchable(true)
                    }
                    ScreenCaptureService.ServiceState.RUNNING -> {
                        statusText?.text = "Running..."
                        playPauseButton?.setImageResource(android.R.drawable.ic_media_pause)
                        playPauseButton?.setColorFilter(Color.parseColor("#FFD700")) // Gold/Yellow pause
                        dismissPinConfigDialog()
                        // When running, make all target pins non-touchable so virtual taps pass directly through to the app/game!
                        setPinsTouchable(false)
                    }
                    ScreenCaptureService.ServiceState.PAUSED -> {
                        statusText?.text = "Paused"
                        playPauseButton?.setImageResource(android.R.drawable.ic_media_play)
                        playPauseButton?.setColorFilter(Color.parseColor("#00E676"))
                        setPinsTouchable(true)
                    }
                }
            }
        }

        serviceScope.launch {
            ScreenCaptureService.totalTapCount.collect { count ->
                tapCountText?.text = "Ketukan: $count"
            }
        }

        serviceScope.launch {
            ScreenCaptureService.matchHighlights.collect { event ->
                highlightView?.showHighlight(event.rect, event.screenX, event.screenY, event.targetName)
            }
        }
    }

    private fun setExpanded(expanded: Boolean) {
        isExpanded = expanded
        if (isExpanded) {
            collapsedLayout?.visibility = View.GONE
            expandedLayout?.visibility = View.VISIBLE
        } else {
            collapsedLayout?.visibility = View.VISIBLE
            expandedLayout?.visibility = View.GONE
        }
    }

    private fun setupDragListener() {
        overlayView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val lp = layoutParams ?: return false
                val wm = windowManager ?: return false

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = lp.x
                        initialY = lp.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()

                        // Treat as drag if moved more than 8 pixels
                        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                            isDragging = true
                        }

                        if (isDragging) {
                            lp.x = initialX + dx
                            lp.y = initialY + dy
                            wm.updateViewLayout(overlayView, lp)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            if (!isExpanded) {
                                setExpanded(true)
                            }
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return Math.round(dp * density)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        displayListener?.let {
            val dm = getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            dm?.unregisterDisplayListener(it)
            displayListener = null
        }
        dismissCalibrationDialog()
        dismissPinConfigDialog()
        clearAllPins()
        overlayView?.let {
            try { windowManager?.removeView(it) } catch (e: Exception) {}
        }
        highlightView?.let {
            try { windowManager?.removeView(it) } catch (e: Exception) {}
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "OverlayService"
        const val EXTRA_PROFILE_NAME = "com.example.extra.PROFILE_NAME"
        const val EXTRA_PROFILE_ID = "com.example.extra.PROFILE_ID"
    }
}
