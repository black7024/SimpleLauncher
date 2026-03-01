package com.simplelauncher.ui.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.net.Uri
import com.simplelauncher.data.LauncherStateRepository
import java.io.ByteArrayOutputStream

object ImageTools {
    fun decodeBase64(base64: String): Bitmap {
        val bytes = LauncherStateRepository.decodeBytes(base64)
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inDither = true
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    fun compressUriToBase64(contentResolver: ContentResolver, uri: Uri, maxSize: Int): String? {
        var source: Bitmap? = null
        var scaled: Bitmap? = null
        var output: ByteArrayOutputStream? = null
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: return null

            val sample = calculateInSampleSize(options.outWidth, options.outHeight, maxSize, maxSize)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sample.coerceAtLeast(1)
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inDither = true
                inMutable = true
            }

            source = contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            } ?: return null

            scaled = scaleBitmap(source!!, maxSize, maxSize)
            output = ByteArrayOutputStream()
            scaled!!.compress(Bitmap.CompressFormat.PNG, 90, output)
            LauncherStateRepository.encodeBytes(output.toByteArray())
        } catch (_: Throwable) {
            null
        } finally {
            try {
                output?.close()
            } catch (_: Throwable) {
            }
            if (scaled != null && scaled != source && !scaled!!.isRecycled) {
                scaled!!.recycle()
            }
            if (source != null && !source!!.isRecycled) {
                source!!.recycle()
            }
        }
    }

    private fun scaleBitmap(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        val srcWidth = bitmap.width.toFloat()
        val srcHeight = bitmap.height.toFloat()
        val scale = minOf(width / srcWidth, height / srcHeight)
        val matrix = Matrix().apply { postScale(scale, scale) }
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val scaled = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        val left = (width - scaled.width) / 2f
        val top = (height - scaled.height) / 2f
        canvas.drawBitmap(scaled, left, top, null)
        if (scaled != bitmap && !scaled.isRecycled) {
            scaled.recycle()
        }
        return result
    }

    private fun calculateInSampleSize(srcWidth: Int, srcHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (srcHeight > reqHeight || srcWidth > reqWidth) {
            var halfHeight = srcHeight / 2
            var halfWidth = srcWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }
}
