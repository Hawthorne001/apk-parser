package com.lb.apkparserdemo.apk_info.app_icon

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import androidx.core.content.getSystemService

fun ApplicationInfo.isSystemApp() = this.flags and ApplicationInfo.FLAG_SYSTEM != 0

fun PackageInfo.isSystemApp() = this.applicationInfo!!.isSystemApp()

fun PackageManager.getInstalledPackagesCompat(flags: Int = 0): MutableList<PackageInfo> {
    if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU)
        return getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
    return getInstalledPackages(flags.toInt())
}

object AppInfoUtil {
    private var appIconSize = 0

    fun getAppIconSize(context: Context): Int {
        if (appIconSize > 0)
            return appIconSize
        val activityManager = context.getSystemService<ActivityManager>()!!
        appIconSize = try {
            activityManager.launcherLargeIconSize
        } catch (e: Exception) {
            ViewUtil.convertDpToPixels(context, 48f).toInt()
        }
        return appIconSize
    }
}
