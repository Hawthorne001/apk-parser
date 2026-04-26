package com.lb.apkparserdemo.apk_info.app_icon

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES

fun ApplicationInfo.isSystemApp() = this.flags and ApplicationInfo.FLAG_SYSTEM != 0

fun PackageInfo.isSystemApp() = this.applicationInfo!!.isSystemApp()

fun PackageManager.getInstalledPackagesCompat(flags: Int = 0): MutableList<PackageInfo> {
    if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU)
        return getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
    return getInstalledPackages(flags)
}

object AppInfoUtil {
    private var appIconSize = 0

    fun getAppIconSize(context: Context): Int {
        if (appIconSize > 0)
            return appIconSize
        appIconSize = context.resources.getDimensionPixelSize(com.lb.apkparserdemo.R.dimen.app_icon_size)
        return appIconSize
    }
}
