package com.fumoran.mathreview.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException

/** 配图压缩：最长边 1600、JPEG 85，并按 EXIF 修正方向。 */
object ImageStore {

    fun compressToJpeg(bytes: ByteArray, out: File, orientation: Int = ExifInterface.ORIENTATION_NORMAL) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("图片解码失败")

        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / (sample * 2) >= 1600) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            ?: throw IOException("图片解码失败")

        bmp = rotate(bmp, orientation)
        val scale = minOf(1f, 1600f / maxOf(bmp.width, bmp.height))
        if (scale < 1f) {
            val scaled = Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * scale).toInt().coerceAtLeast(1),
                (bmp.height * scale).toInt().coerceAtLeast(1),
                true
            )
            if (scaled !== bmp) bmp.recycle()
            bmp = scaled
        }

        out.parentFile?.mkdirs()
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bmp.recycle()
    }

    fun fromUri(context: Context, uri: Uri, out: File) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("无法读取所选图片")
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        compressToJpeg(bytes, out, orientation)
    }

    fun fromBytes(bytes: ByteArray, out: File) {
        val orientation = runCatching {
            ExifInterface(bytes.inputStream()).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        compressToJpeg(bytes, out, orientation)
    }

    private fun rotate(src: Bitmap, orientation: Int): Bitmap {
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val flip = orientation == ExifInterface.ORIENTATION_FLIP_HORIZONTAL
        if (degrees == 0f && !flip) return src
        val m = Matrix()
        if (flip) m.postScale(-1f, 1f)
        if (degrees != 0f) m.postRotate(degrees)
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        if (rotated !== src) src.recycle()
        return rotated
    }
}
