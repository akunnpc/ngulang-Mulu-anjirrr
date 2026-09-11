package com.example.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.File

object ImageMatcher {
    private const val TAG = "ImageMatcher"
    private var isOpenCVInitialized = false

    init {
        initOpenCV()
    }

    fun initOpenCV(): Boolean {
        if (isOpenCVInitialized) return true
        isOpenCVInitialized = OpenCVLoader.initDebug()
        Log.d(TAG, "OpenCV Initialization: ${if (isOpenCVInitialized) "SUCCESS" else "FAILED"}")
        return isOpenCVInitialized
    }

    data class MatchResult(
        val isMatched: Boolean,
        val confidence: Float,
        val screenX: Float,
        val screenY: Float,
        val rect: android.graphics.Rect
    )

    /**
     * Matches a target image template against a screen capture bitmap.
     * 
     * @param screenBitmap The current screen capture.
     * @param targetImagePath Path to the target image file on internal storage.
     * @param threshold Confidence threshold (default 0.8).
     * @param restrictRegion True to restrict matching to a specific rectangular sub-region.
     * @param rx X coordinate of region.
     * @param ry Y coordinate of region.
     * @param rw Width of region.
     * @param rh Height of region.
     */
    fun findMatch(
        screenBitmap: Bitmap,
        targetImagePath: String,
        threshold: Float,
        restrictRegion: Boolean = false,
        rx: Int = 0,
        ry: Int = 0,
        rw: Int = 0,
        rh: Int = 0
    ): MatchResult {
        if (!isOpenCVInitialized && !initOpenCV()) {
            Log.e(TAG, "OpenCV not initialized, cannot match templates.")
            return MatchResult(false, 0f, 0f, 0f, android.graphics.Rect())
        }

        val targetFile = File(targetImagePath)
        if (!targetFile.exists()) {
            Log.e(TAG, "Target image file does not exist: $targetImagePath")
            return MatchResult(false, 0f, 0f, 0f, android.graphics.Rect())
        }

        var screenMat: Mat? = null
        var targetMat: Mat? = null
        var resultMat: Mat? = null
        var searchAreaMat: Mat? = null

        try {
            // Load target template as Bitmap, convert to Mat
            val targetBitmap = BitmapFactory.decodeFile(targetImagePath) ?: return MatchResult(false, 0f, 0f, 0f, android.graphics.Rect())
            targetMat = Mat()
            Utils.bitmapToMat(targetBitmap, targetMat)

            // Convert screen Bitmap to Mat
            screenMat = Mat()
            Utils.bitmapToMat(screenBitmap, screenMat)

            // Ensure images are both colored or converted
            // matchTemplate requires source and template to have the same format (e.g. CV_8UC4 from Utils.bitmapToMat)
            
            val searchRect: Rect
            if (restrictRegion) {
                // Ensure the restricted region fits within screen dimensions
                val startX = rx.coerceIn(0, screenMat.cols() - 1)
                val startY = ry.coerceIn(0, screenMat.rows() - 1)
                val endX = (rx + rw).coerceIn(1, screenMat.cols())
                val endY = (ry + rh).coerceIn(1, screenMat.rows())
                
                val width = endX - startX
                val height = endY - startY

                if (width > targetMat.cols() && height > targetMat.rows()) {
                    searchRect = Rect(startX, startY, width, height)
                    searchAreaMat = screenMat.submat(searchRect)
                } else {
                    // Region is too small for template size, fall back to full screen
                    searchRect = Rect(0, 0, screenMat.cols(), screenMat.rows())
                    searchAreaMat = screenMat
                }
            } else {
                searchRect = Rect(0, 0, screenMat.cols(), screenMat.rows())
                searchAreaMat = screenMat
            }

            // Verify search matrix is larger than template matrix
            if (searchAreaMat.cols() < targetMat.cols() || searchAreaMat.rows() < targetMat.rows()) {
                Log.w(TAG, "Search area (${searchAreaMat.cols()}x${searchAreaMat.rows()}) is smaller than target template (${targetMat.cols()}x${targetMat.rows()})")
                return MatchResult(false, 0f, 0f, 0f, android.graphics.Rect())
            }

            // Create result matrix to store coefficients
            resultMat = Mat()
            
            // Convert to grayscale for robust and fast TM_CCOEFF_NORMED matching (OpenCV requires 1 or 3 channels)
            val graySearch = Mat()
            val grayTarget = Mat()
            Imgproc.cvtColor(searchAreaMat, graySearch, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.cvtColor(targetMat, grayTarget, Imgproc.COLOR_RGBA2GRAY)
            
            // Perform matchTemplate
            Imgproc.matchTemplate(graySearch, grayTarget, resultMat, Imgproc.TM_CCOEFF_NORMED)
            graySearch.release()
            grayTarget.release()

            // Find best match locations
            val mmr = Core.minMaxLoc(resultMat)
            val maxConfidence = mmr.maxVal.toFloat()

            if (maxConfidence >= threshold) {
                val matchLoc = mmr.maxLoc // Top-left of matched area relative to searchAreaMat
                
                // Center of matching area in screen coordinates
                val targetCenterRelX = matchLoc.x + (targetMat.cols() / 2.0)
                val targetCenterRelY = matchLoc.y + (targetMat.rows() / 2.0)

                val screenX = (searchRect.x + targetCenterRelX).toFloat()
                val screenY = (searchRect.y + targetCenterRelY).toFloat()

                val screenLeft = (searchRect.x + matchLoc.x).toInt()
                val screenTop = (searchRect.y + matchLoc.y).toInt()
                val screenRight = screenLeft + targetMat.cols()
                val screenBottom = screenTop + targetMat.rows()

                val bounds = android.graphics.Rect(screenLeft, screenTop, screenRight, screenBottom)

                Log.d(TAG, "Match FOUND! Conf: $maxConfidence at ($screenX, $screenY), Threshold: $threshold")
                return MatchResult(true, maxConfidence, screenX, screenY, bounds)
            } else {
                Log.v(TAG, "No match. Best confidence was: $maxConfidence, threshold: $threshold")
                return MatchResult(false, maxConfidence, 0f, 0f, android.graphics.Rect())
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in matchTemplate", e)
            return MatchResult(false, 0f, 0f, 0f, android.graphics.Rect())
        } finally {
            screenMat?.release()
            targetMat?.release()
            resultMat?.release()
            if (restrictRegion && searchAreaMat != screenMat) {
                searchAreaMat?.release()
            }
        }
    }

    /**
     * Finds multiple matching instances of a target image template on a screen capture bitmap.
     * Uses iterative template matching and region masking (Non-Maximum Suppression simulation).
     */
    fun findAllMatches(
        screenBitmap: Bitmap,
        targetImagePath: String,
        threshold: Float,
        maxMatches: Int = 5,
        restrictRegion: Boolean = false,
        rx: Int = 0,
        ry: Int = 0,
        rw: Int = 0,
        rh: Int = 0
    ): List<MatchResult> {
        val matches = mutableListOf<MatchResult>()
        if (!isOpenCVInitialized && !initOpenCV()) {
            Log.e(TAG, "OpenCV not initialized, cannot match templates.")
            return matches
        }

        val targetFile = File(targetImagePath)
        if (!targetFile.exists()) {
            Log.e(TAG, "Target image file does not exist: $targetImagePath")
            return matches
        }

        var screenMat: Mat? = null
        var targetMat: Mat? = null
        var searchAreaMat: Mat? = null
        var workMat: Mat? = null
        var grayTarget: Mat? = null

        try {
            val targetBitmap = BitmapFactory.decodeFile(targetImagePath) ?: return matches
            targetMat = Mat()
            Utils.bitmapToMat(targetBitmap, targetMat)

            screenMat = Mat()
            Utils.bitmapToMat(screenBitmap, screenMat)

            val searchRect: Rect
            if (restrictRegion) {
                val startX = rx.coerceIn(0, screenMat.cols() - 1)
                val startY = ry.coerceIn(0, screenMat.rows() - 1)
                val endX = (rx + rw).coerceIn(1, screenMat.cols())
                val endY = (ry + rh).coerceIn(1, screenMat.rows())
                val width = endX - startX
                val height = endY - startY

                if (width > targetMat.cols() && height > targetMat.rows()) {
                    searchRect = Rect(startX, startY, width, height)
                    searchAreaMat = screenMat.submat(searchRect)
                } else {
                    searchRect = Rect(0, 0, screenMat.cols(), screenMat.rows())
                    searchAreaMat = screenMat
                }
            } else {
                searchRect = Rect(0, 0, screenMat.cols(), screenMat.rows())
                searchAreaMat = screenMat
            }

            if (searchAreaMat.cols() < targetMat.cols() || searchAreaMat.rows() < targetMat.rows()) {
                return matches
            }

            workMat = searchAreaMat.clone()

            grayTarget = Mat()
            Imgproc.cvtColor(targetMat, grayTarget, Imgproc.COLOR_RGBA2GRAY)

            for (i in 0 until maxMatches) {
                val grayWork = Mat()
                Imgproc.cvtColor(workMat, grayWork, Imgproc.COLOR_RGBA2GRAY)
                val resultMat = Mat()
                Imgproc.matchTemplate(grayWork, grayTarget, resultMat, Imgproc.TM_CCOEFF_NORMED)
                val mmr = Core.minMaxLoc(resultMat)
                resultMat.release()
                grayWork.release()

                val maxConfidence = mmr.maxVal.toFloat()
                if (maxConfidence < threshold) {
                    break
                }

                val matchLoc = mmr.maxLoc
                val targetCenterRelX = matchLoc.x + (targetMat.cols() / 2.0)
                val targetCenterRelY = matchLoc.y + (targetMat.rows() / 2.0)

                val screenX = (searchRect.x + targetCenterRelX).toFloat()
                val screenY = (searchRect.y + targetCenterRelY).toFloat()

                val screenLeft = (searchRect.x + matchLoc.x).toInt()
                val screenTop = (searchRect.y + matchLoc.y).toInt()
                val screenRight = screenLeft + targetMat.cols()
                val screenBottom = screenTop + targetMat.rows()

                val bounds = android.graphics.Rect(screenLeft, screenTop, screenRight, screenBottom)
                matches.add(MatchResult(true, maxConfidence, screenX, screenY, bounds))

                // Mask out this matched area in workMat so it isn't picked again
                val x1 = matchLoc.x.toInt().coerceAtLeast(0)
                val y1 = matchLoc.y.toInt().coerceAtLeast(0)
                val x2 = (matchLoc.x + targetMat.cols()).toInt().coerceAtMost(workMat.cols())
                val y2 = (matchLoc.y + targetMat.rows()).toInt().coerceAtMost(workMat.rows())

                Imgproc.rectangle(
                    workMat,
                    Point(x1.toDouble(), y1.toDouble()),
                    Point(x2.toDouble(), y2.toDouble()),
                    Scalar(0.0, 0.0, 0.0, 0.0),
                    -1
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in findAllMatches", e)
        } finally {
            screenMat?.release()
            targetMat?.release()
            grayTarget?.release()
            workMat?.release()
            if (restrictRegion && searchAreaMat != screenMat) {
                searchAreaMat?.release()
            }
        }

        return matches
    }
}
