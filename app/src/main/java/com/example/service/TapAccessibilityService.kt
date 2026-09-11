package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class TapAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "TapAccessibilityService connected successfully with canPerformGestures capability enabled.")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        Log.d(TAG, "TapAccessibilityService unbound")
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Log.d(TAG, "TapAccessibilityService destroyed")
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No events to process - we only use this for simulating gestural clicks
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service Interrupted")
    }

    /**
     * Performs a simulated gesture click (tap) at the specified screen coordinates.
     */
    fun tapAt(x: Float, y: Float, callback: ((Boolean) -> Unit)? = null) {
        val safeX = if (x.isNaN() || x < 0f) 100f else x.coerceAtLeast(1f)
        val safeY = if (y.isNaN() || y < 0f) 100f else y.coerceAtLeast(1f)

        val clickPath = Path().apply {
            moveTo(safeX, safeY)
            lineTo(safeX + 0.5f, safeY + 0.5f)
        }
        
        val stroke = GestureDescription.StrokeDescription(clickPath, 0L, 50L)
        val gesture = GestureDescription.Builder().apply {
            addStroke(stroke)
        }.build()

        mainHandler.post {
            var callbackInvoked = false
            try {
                val success = dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        super.onCompleted(gestureDescription)
                        Log.d(TAG, "Gesture click successfully completed at ($safeX, $safeY)")
                        if (!callbackInvoked) {
                            callbackInvoked = true
                            callback?.invoke(true)
                        }
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        super.onCancelled(gestureDescription)
                        Log.e(TAG, "Gesture click cancelled at ($safeX, $safeY)")
                        if (!callbackInvoked) {
                            callbackInvoked = true
                            callback?.invoke(false)
                        }
                    }
                }, mainHandler)

                if (!success && !callbackInvoked) {
                    Log.e(TAG, "Failed to dispatch gesture click at ($safeX, $safeY)")
                    callbackInvoked = true
                    callback?.invoke(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during dispatchGesture tap", e)
                if (!callbackInvoked) {
                    callbackInvoked = true
                    callback?.invoke(false)
                }
            }
        }
    }

    /**
     * Performs a simulated gesture press and hold at the specified screen coordinates.
     */
    fun longPressAt(x: Float, y: Float, durationMs: Long, callback: ((Boolean) -> Unit)? = null) {
        val safeX = if (x.isNaN() || x < 0f) 100f else x.coerceAtLeast(1f)
        val safeY = if (y.isNaN() || y < 0f) 100f else y.coerceAtLeast(1f)

        val clickPath = Path().apply {
            moveTo(safeX, safeY)
            lineTo(safeX + 0.5f, safeY + 0.5f)
        }
        
        val stroke = GestureDescription.StrokeDescription(clickPath, 0L, durationMs.coerceAtLeast(150L))
        val gesture = GestureDescription.Builder().apply {
            addStroke(stroke)
        }.build()

        mainHandler.post {
            var callbackInvoked = false
            try {
                val success = dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        super.onCompleted(gestureDescription)
                        Log.d(TAG, "Gesture long-press successfully completed at ($safeX, $safeY) for ${durationMs}ms")
                        if (!callbackInvoked) {
                            callbackInvoked = true
                            callback?.invoke(true)
                        }
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        super.onCancelled(gestureDescription)
                        Log.e(TAG, "Gesture long-press cancelled at ($safeX, $safeY)")
                        if (!callbackInvoked) {
                            callbackInvoked = true
                            callback?.invoke(false)
                        }
                    }
                }, mainHandler)

                if (!success && !callbackInvoked) {
                    Log.e(TAG, "Failed to dispatch gesture long-press at ($safeX, $safeY)")
                    callbackInvoked = true
                    callback?.invoke(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during dispatchGesture long-press", e)
                if (!callbackInvoked) {
                    callbackInvoked = true
                    callback?.invoke(false)
                }
            }
        }
    }

    companion object {
        private const val TAG = "TapAccessibility"
        
        @Volatile
        private var instance: TapAccessibilityService? = null

        /**
         * Checks if the Accessibility Service is currently enabled and connected.
         */
        fun isRunning(): Boolean = instance != null

        /**
         * Utility function to trigger a tap at (x, y) if the service is running.
         */
        fun performTap(x: Float, y: Float, callback: ((Boolean) -> Unit)? = null): Boolean {
            val service = instance
            if (service != null) {
                service.tapAt(x, y, callback)
                return true
            }
            Log.e(TAG, "Cannot tap: TapAccessibilityService is not running.")
            callback?.invoke(false)
            return false
        }

        /**
         * Suspend function that dispatches a tap and waits for the OS gesture completion callback.
         */
        suspend fun performTapSuspend(x: Float, y: Float): Boolean {
            val service = instance ?: run {
                Log.e(TAG, "Cannot tap: TapAccessibilityService is not running.")
                return false
            }
            return withTimeoutOrNull(2500L) {
                suspendCancellableCoroutine { cont ->
                    service.tapAt(x, y) { success ->
                        if (cont.isActive) {
                            cont.resume(success)
                        }
                    }
                }
            } ?: false
        }

        /**
         * Suspend function that dispatches a long-press and waits for the OS gesture completion callback.
         */
        suspend fun performLongPressSuspend(x: Float, y: Float, durationMs: Long): Boolean {
            val service = instance ?: run {
                Log.e(TAG, "Cannot long-press: TapAccessibilityService is not running.")
                return false
            }
            return withTimeoutOrNull(durationMs + 2500L) {
                suspendCancellableCoroutine { cont ->
                    service.longPressAt(x, y, durationMs) { success ->
                        if (cont.isActive) {
                            cont.resume(success)
                        }
                    }
                }
            } ?: false
        }

        /**
         * Utility function to trigger a long-press at (x, y) for a duration if the service is running.
         */
        fun performLongPress(x: Float, y: Float, durationMs: Long, callback: ((Boolean) -> Unit)? = null): Boolean {
            val service = instance
            if (service != null) {
                service.longPressAt(x, y, durationMs, callback)
                return true
            }
            Log.e(TAG, "Cannot long-press: TapAccessibilityService is not running.")
            callback?.invoke(false)
            return false
        }
    }
}
