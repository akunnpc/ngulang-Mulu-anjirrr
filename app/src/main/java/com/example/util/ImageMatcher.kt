package com.example.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object ImageMatcher {
    private const val TAG = "ImageMatcher"
    private var isOpenCVInitialized = false

    private data class CachedTemplate(
        val lastModified: Long,
        val grayTargetMat: Mat,
        val width: Int,
        val height: Int
    )

    private val templateCache = ConcurrentHashMap<String, CachedTemplate>()

    init {
        initOpenCV()
    }

    fun initOpenCV(): Boolean {
        if (isOpenCVInitialized) return true
        isOpenCVInitialized = OpenCVLoader.initDebug()
        Log.d(TAG, "OpenCV Initialization: ${if (isOpenCVInitialized) "SUCCESS" else "FAILED"}")
        return isOpenCVInitialized
    }

    fun clearCache() {
        templateCache.values.forEach { 
            try { it.grayTargetMat.release() } catch (e: Exception) {} 
        }
        templateCache.clear()
    }

    private fun getOrLoadTemplate(path: String): CachedTemplate? {
        val file = File(path)
        if (!file.exists()) return null
        val lastMod = file.lastModified()
        val cached = templateCache[path]
        if (cached != null && cached.lastModified == lastMod) {
            return cached
        }

        try {
            cached?.grayTargetMat?.release()
        } catch (e: Exception) {}

        val bmp = BitmapFactory.decodeFile(path) ?: return null
        return try {
            val colorMat = Mat()
            Utils.bitmapToMat(bmp, colorMat)
            val grayMat = Mat()
            Imgproc.cvtColor(colorMat, grayMat, Imgproc.COLOR_RGBA2GRAY)
            colorMat.release()
            val template = CachedTemplate(lastMod, grayMat, bmp.width, bmp.height)
            templateCache[path] = template
            template
        } catch (e: Exception) {
            Log.e(TAG, "Error caching template for $path", e)
            null
        } finally {
            bmp.recycle()
        }
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

        val template = getOrLoadTemplate(targetImagePath)
        if (template == null) {
            Log.e(TAG, "Target template could not be loaded: $targetImagePath")
            return MatchResult(false, 0f, 0f, 0f, android.graphics.Rect())
        }

        var screenMat: Mat? = null
        var resultMat: Mat? = null
        var searchAreaMat: Mat? = null
        var graySearch: Mat? = null

        try {
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

                if (width > template.width && height > template.height) {
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

            if (searchAreaMat.cols() < template.width || searchAreaMat.rows() < template.height) {
                Log.w(TAG, "Search area (${searchAreaMat.cols()}x${searchAreaMat.rows()}) is smaller than target template (${template.width}x${template.height})")
                return MatchResult(false, 0f, 0f, 0f, android.graphics.Rect())
            }

            resultMat = Mat()
            graySearch = Mat()
            Imgproc.cvtColor(searchAreaMat, graySearch, Imgproc.COLOR_RGBA2GRAY)

            Imgproc.matchTemplate(graySearch, template.grayTargetMat, resultMat, Imgproc.TM_CCOEFF_NORMED)

            val mmr = Core.minMaxLoc(resultMat)
            val maxConfidence = mmr.maxVal.toFloat()

            if (maxConfidence >= threshold) {
                val matchLoc = mmr.maxLoc

                val targetCenterRelX = matchLoc.x + (template.width / 2.0)
                val targetCenterRelY = matchLoc.y + (template.height / 2.0)

                val screenX = (searchRect.x + targetCenterRelX).toFloat()
                val screenY = (searchRect.y + targetCenterRelY).toFloat()

                val screenLeft = (searchRect.x + matchLoc.x).toInt()
                val screenTop = (searchRect.y + matchLoc.y).toInt()
                val screenRight = screenLeft + template.width
                val screenBottom = screenTop + template.height

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
            graySearch?.release()
            resultMat?.release()
            if (restrictRegion && searchAreaMat != screenMat) {
                searchAreaMat?.release()
            }
            screenMat?.release()
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

        val template = getOrLoadTemplate(targetImagePath)
        if (template == null) {
            Log.e(TAG, "Target template could not be loaded: $targetImagePath")
            return matches
        }

        var screenMat: Mat? = null
        var searchAreaMat: Mat? = null
        var workMat: Mat? = null

        try {
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

                if (width > template.width && height > template.height) {
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

            if (searchAreaMat.cols() < template.width || searchAreaMat.rows() < template.height) {
                return matches
            }

            workMat = searchAreaMat.clone()

            for (i in 0 until maxMatches) {
                val grayWork = Mat()
                Imgproc.cvtColor(workMat, grayWork, Imgproc.COLOR_RGBA2GRAY)
                val resultMat = Mat()
                Imgproc.matchTemplate(grayWork, template.grayTargetMat, resultMat, Imgproc.TM_CCOEFF_NORMED)
                val mmr = Core.minMaxLoc(resultMat)
                resultMat.release()
                grayWork.release()

                val maxConfidence = mmr.maxVal.toFloat()
                if (maxConfidence < threshold) {
                    break
                }

                val matchLoc = mmr.maxLoc
                val targetCenterRelX = matchLoc.x + (template.width / 2.0)
                val targetCenterRelY = matchLoc.y + (template.height / 2.0)

                val screenX = (searchRect.x + targetCenterRelX).toFloat()
                val screenY = (searchRect.y + targetCenterRelY).toFloat()

                val screenLeft = (searchRect.x + matchLoc.x).toInt()
                val screenTop = (searchRect.y + matchLoc.y).toInt()
                val screenRight = screenLeft + template.width
                val screenBottom = screenTop + template.height

                val bounds = android.graphics.Rect(screenLeft, screenTop, screenRight, screenBottom)
                matches.add(MatchResult(true, maxConfidence, screenX, screenY, bounds))

                // Mask out this matched area in workMat so it isn't picked again
                val x1 = matchLoc.x.toInt().coerceAtLeast(0)
                val y1 = matchLoc.y.toInt().coerceAtLeast(0)
                val x2 = (matchLoc.x + template.width).toInt().coerceAtMost(workMat.cols())
                val y2 = (matchLoc.y + template.height).toInt().coerceAtMost(workMat.rows())

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
            workMat?.release()
            if (restrictRegion && searchAreaMat != screenMat) {
                searchAreaMat?.release()
            }
            screenMat?.release()
        }

        return matches
    }
}
