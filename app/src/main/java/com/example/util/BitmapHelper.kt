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
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
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
            val x = (leftPct * bitmap.width).toInt()
            val y = (topPct * bitmap.height).toInt()
            val width = ((rightPct - leftPct) * bitmap.width).toInt()
            val height = ((bottomPct - topPct) * bitmap.height).toInt()

            val startX = x.coerceIn(0, bitmap.width - 1)
            val startY = y.coerceIn(0, bitmap.height - 1)
            val rectWidth = width.coerceIn(1, bitmap.width - startX)
            val rectHeight = height.coerceIn(1, bitmap.height - startY)

            Bitmap.createBitmap(bitmap, startX, startY, rectWidth, rectHeight)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveBitmapToFile(context: Context, bitmap: Bitmap, prefix: String = "target"): File {
        val filename = "${prefix}_${UUID.randomUUID()}.png"
        val targetFile = File(context.filesDir, filename)
        val out = FileOutputStream(targetFile)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.flush()
        out.close()
        return targetFile
    }
}
