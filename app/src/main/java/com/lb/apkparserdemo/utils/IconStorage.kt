package com.lb.apkparserdemo.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.io.FileOutputStream

object IconStorage {
    private var appIconSize = 0

    private const val ICONS_DIR = "cached_icons"

    fun getAppIconSize(context: Context): Int {
        if (appIconSize > 0)
            return appIconSize
        appIconSize = context.resources.getDimensionPixelSize(com.lb.apkparserdemo.R.dimen.app_icon_size)
        return appIconSize
    }

    fun saveIcon(context: Context, fileName: String, bitmap: Bitmap): Boolean {
        val dir = File(context.filesDir, ICONS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, fileName)
        return try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun saveIcon(context: Context, fileName: String, drawable: Drawable): Boolean {
        val appIconSize = getAppIconSize(context)
        val bitmap = drawable.toBitmap(width = appIconSize, height = appIconSize)
        return saveIcon(context, fileName, bitmap)
    }

    fun deleteIcon(context: Context, fileName: String) {
        val file = File(File(context.filesDir, ICONS_DIR), fileName)
        if (file.exists()) {
            file.delete()
        }
    }

    fun getIconFile(context: Context, fileName: String): File {
        return File(File(context.filesDir, ICONS_DIR), fileName)
    }

    fun clearCache(context: Context) {
        val dir = File(context.filesDir, ICONS_DIR)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }
        val bitmap = Bitmap.createBitmap(
                drawable.intrinsicWidth.coerceAtLeast(1),
                drawable.intrinsicHeight.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
