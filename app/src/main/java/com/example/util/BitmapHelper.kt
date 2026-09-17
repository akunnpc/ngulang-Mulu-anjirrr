package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object BitmapHelper {

    fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun cropBitmap(
        bitmap: Bitmap,
        leftPct: Float,
        rightPct: Float,
        topPct: Float,
        bottomPct: Float
    ): Bitmap? {
        return try {
            val minX = minOf(leftPct, rightPct).coerceIn(0f, 1f)
            val maxX = maxOf(leftPct, rightPct).coerceIn(0f, 1f)
            val minY = minOf(topPct, bottomPct).coerceIn(0f, 1f)
            val maxY = maxOf(topPct, bottomPct).coerceIn(0f, 1f)

            val x = (minX * bitmap.width).toInt()
            val y = (minY * bitmap.height).toInt()
            val rawW = ((maxX - minX) * bitmap.width).toInt()
            val rawH = ((maxY - minY) * bitmap.height).toInt()

            val startX = x.coerceIn(0, bitmap.width - 1)
            val startY = y.coerceIn(0, bitmap.height - 1)
            val rectWidth = rawW.coerceIn(1, bitmap.width - startX)
            val rectHeight = rawH.coerceIn(1, bitmap.height - startY)

            Bitmap.createBitmap(bitmap, startX, startY, rectWidth, rectHeight)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveBitmapToFile(context: Context, bitmap: Bitmap, prefix: String = "target"): File {
        val filename = "${prefix}_${UUID.randomUUID()}.png"
        val targetFile = File(context.filesDir, filename)
        FileOutputStream(targetFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }
        return targetFile
    }
}
