package com.prathap.tvhome

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/**
 * Family photos stored on the TV itself (sent with "Send Files to TV"
 * or any file app). Nothing is uploaded anywhere.
 */
object PhotoStore {

    fun permission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasPermission(c: Context): Boolean =
        c.checkSelfPermission(permission()) == PackageManager.PERMISSION_GRANTED

    /** All photos on the TV, newest first (small images such as icons are skipped). */
    fun list(c: Context): List<Uri> {
        if (!hasPermission(c)) return emptyList()
        val out = ArrayList<Uri>()
        val base = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.SIZE)
        try {
            c.contentResolver.query(base, proj, null, null,
                MediaStore.Images.Media.DATE_ADDED + " DESC")?.use { cur ->
                val idCol = cur.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val sizeCol = cur.getColumnIndex(MediaStore.Images.Media.SIZE)
                while (cur.moveToNext() && out.size < 300) {
                    if (sizeCol >= 0) {
                        val size = cur.getLong(sizeCol)
                        if (size in 1 until 40_000) continue
                    }
                    out.add(ContentUris.withAppendedId(base, cur.getLong(idCol)))
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    /** Loads a photo scaled down to roughly screen size, turned the right way up. */
    fun decode(c: Context, uri: Uri, maxW: Int, maxH: Int): Bitmap? {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxW && bounds.outHeight / (sample * 2) >= maxH) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bmp = c.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null
            val rotation = orientation(c, uri)
            if (rotation == 0) return bmp
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        } catch (_: Throwable) {
            return null
        }
    }

    private fun orientation(c: Context, uri: Uri): Int {
        if (Build.VERSION.SDK_INT < 24) return 0
        return try {
            c.contentResolver.openInputStream(uri)?.use { s ->
                when (ExifInterface(s).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (_: Exception) {
            0
        }
    }
}
