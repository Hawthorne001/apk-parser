package com.lb.apkparserdemo.activities.activity_main

import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.lb.apkparserdemo.apk_info.AbstractZipFilter
import com.lb.apkparserdemo.apk_info.ApacheZipArchiveInputStreamFilter
import com.lb.apkparserdemo.apk_info.ApacheZipFileFilter
import com.lb.apkparserdemo.apk_info.ApkInfo
import com.lb.apkparserdemo.apk_info.MultiZipFilter
import com.lb.apkparserdemo.apk_info.ZipFileFilter
import com.lb.apkparserdemo.apk_info.ZipInputStreamFilter
import com.lb.apkparserdemo.apk_info.app_icon.ApkIconFetcher
import com.lb.apkparserdemo.apk_info.app_icon.AppInfoUtil
import com.lb.apkparserdemo.apk_info.app_icon.getInstalledPackagesCompat
import com.lb.apkparserdemo.apk_info.app_icon.isSystemApp
import com.lb.common_utils.BaseViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import java.io.FileInputStream
import java.util.Locale
import java.util.concurrent.Executors
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

private const val VALIDATE_RESOURCES = true
private const val GET_APK_TYPE = true
private val ZIP_FILTER_TYPE: MainActivityViewModel.Companion.ZipFilterType =
        MainActivityViewModel.Companion.ZipFilterType.ZipFileFilter

class MainActivityViewModel(application: Application) : BaseViewModel(application) {
    val appsHandledLiveData = CounterMutableLiveData()
    val apkFilesHandledLiveData = CounterMutableLiveData()
    val frameworkErrorsOfApkTypeLiveData = CounterMutableLiveData()
    val parsingErrorsLiveData = CounterMutableLiveData()
    val wrongApkTypeErrorsLiveData = CounterMutableLiveData()
    val wrongPackageNameErrorsLiveData = CounterMutableLiveData()
    val failedGettingAppIconErrorsLiveData = CounterMutableLiveData()
    val wrongLabelErrorsLiveData = CounterMutableLiveData()
    val wrongVersionCodeErrorsLiveData = CounterMutableLiveData()
    val wrongVersionNameErrorsLiveData = CounterMutableLiveData()
    val systemAppsErrorsCountLiveData = CounterMutableLiveData()
    val isDoneLiveData = MutableLiveData<Boolean>(false)

    private var fetchAppInfoJob: Job? = null
    private val fetchAppInfoDispatcher: CoroutineDispatcher =
            Executors.newFixedThreadPool(1).asCoroutineDispatcher()

    @Suppress("ConstantConditionIf")
    @UiThread
    fun init() {
        if (fetchAppInfoJob != null) return
        fetchAppInfoJob = viewModelScope.launch {
            runInterruptible(fetchAppInfoDispatcher) {
                performTests()
            }
        }
    }

    @WorkerThread
    private fun performTests() {
        val localeList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val list = applicationContext.resources.configuration.locales
            val result = mutableListOf<Locale>()
            for (i in 0 until list.size()) {
                result.add(list.get(i))
            }
            result
        } else {
            listOf(Locale.getDefault())
        }
//        Locales.matcher=SystemLocaleMatcher()
        val mainLocale = localeList.firstOrNull()
        val context = applicationContext
        val appIconSize = AppInfoUtil.getAppIconSize(context)
        val packageManager = context.packageManager
        Log.d("AppLog", "getting all package infos: locale:$mainLocale")
        var startTime = System.currentTimeMillis()
        val appsToFocusOn = HashSet<String>()
                .also {
//                    it.add("com.google.android.odad")
//                    it.add("com.google.android.cellbroadcastreceiver")
//                    it.add("com.google.android.apps.pixel.dcservice")
//                    it.add("com.google.android.photopicker")
//                    it.add("com.google.android.storagemanager")

                }
        val installedPackages =
                packageManager.getInstalledPackagesCompat(PackageManager.GET_META_DATA)
                        .filter { appsToFocusOn.isEmpty() || appsToFocusOn.contains(it.packageName) }

        var endTime = System.currentTimeMillis()
        Log.d("AppLog", "time taken: ${endTime - startTime}. total apps to process: ${installedPackages.size}")
        startTime = endTime
        var apksHandledSoFar = 0
        for ((index, packageInfo) in installedPackages.withIndex()) {
            val packageName = packageInfo.packageName
            // Log.d("AppLog", "processing index $index: $packageName")
            val isSystemApp = packageInfo.isSystemApp()

            val baseApkPath = packageInfo.applicationInfo!!.publicSourceDir
            val splitApkPaths = packageInfo.applicationInfo!!.splitPublicSourceDirs?.toList()
                    ?: emptyList()
            val allApkFilePaths = listOf(baseApkPath) + splitApkPaths

            // Always build master table if splits exist, to ensure correct labels/icons
            val apkInfo = try {
                val filters = allApkFilePaths.map { getZipFilter(it, ZIP_FILTER_TYPE) }
                val info = ApkInfo.getConsolidatedApkInfo(
                        mainLocale, filters,
                        requestParseManifestXmlTagForApkType = GET_APK_TYPE,
                        requestParseResources = VALIDATE_RESOURCES
                )
                filters.forEach {
                    try {
                        it.close()
                    } catch (ignored: Exception) {
                    }
                }
                info
            } catch (e: Throwable) {
                Log.e("AppLog", "failed to parse apk for $packageName", e)
                null
            }

            if (apkInfo == null) {
                parsingErrorsLiveData.inc()
                if (isSystemApp) systemAppsErrorsCountLiveData.inc()
                Log.e("AppLog", "can't parse apk for \"$packageName\" in: \"$baseApkPath\" isSystemApp?$isSystemApp")
                continue
            }
            val currentApkInfo = apkInfo
            if (VALIDATE_RESOURCES) {
                //check if the library can get app icon, if required
                val appIcon = ApkIconFetcher.getApkIcon(
                        context, mainLocale, object : ApkIconFetcher.ZipFilterCreator {
                    override fun generateZipFilter(): AbstractZipFilter =
                            MultiZipFilter(allApkFilePaths.map { getZipFilter(it, ZIP_FILTER_TYPE) })
                }, currentApkInfo, appIconSize
                )
                if (packageInfo.applicationInfo!!.icon != 0 && appIcon == null) {
                    failedGettingAppIconErrorsLiveData.inc()
                    if (isSystemApp) systemAppsErrorsCountLiveData.inc()
                    Log.e("AppLog", "fetching error: can\'t get app icon for \"$packageName\" in: \"$baseApkPath\"")
                }
            }
            when {
                GET_APK_TYPE && currentApkInfo.apkType == ApkInfo.ApkType.UNKNOWN -> {
                    wrongApkTypeErrorsLiveData.inc()
                    if (isSystemApp) systemAppsErrorsCountLiveData.inc()
                    Log.e("AppLog", "can\'t get apk type for \"$packageName\" in: \"$baseApkPath\" isSystemApp?$isSystemApp")
                }

                GET_APK_TYPE && currentApkInfo.apkType == ApkInfo.ApkType.SPLIT -> {
                    wrongApkTypeErrorsLiveData.inc()
                    if (isSystemApp) systemAppsErrorsCountLiveData.inc()
                    Log.e("AppLog", "detected as split apk, but in fact a main apk, for \"$packageName\" in: \"$baseApkPath\" isSystemApp?$isSystemApp")
                }

                else -> {}
            }
            val apkMeta = currentApkInfo.apkMetaTranslator.apkMeta
            if (packageInfo.packageName != apkMeta.packageName) {
                wrongPackageNameErrorsLiveData.inc()
                if (isSystemApp) systemAppsErrorsCountLiveData.inc()
                Log.e("AppLog", "apk package name is different for $baseApkPath : " + "correct one is: \"${packageInfo.packageName}\" vs found: \"${apkMeta.packageName}\" isSystemApp?$isSystemApp")
            }
            //compare version name using library vs framework
            if (VALIDATE_RESOURCES && packageInfo.versionName != apkMeta.versionName) {
                wrongVersionNameErrorsLiveData.inc()
                if (isSystemApp) systemAppsErrorsCountLiveData.inc()
                Log.e("AppLog", "apk version name is different for \"$packageName\" on $baseApkPath : " + "correct one is: \"${packageInfo.versionName}\" vs found: \"${apkMeta.versionName}\" isSystemApp?$isSystemApp")
            }
            if (versionCodeCompat(packageInfo) != apkMeta.versionCode) {
                wrongVersionCodeErrorsLiveData.inc()
                if (isSystemApp) systemAppsErrorsCountLiveData.inc()
                Log.e("AppLog", "apk version code is different for \"$packageName\" on $baseApkPath : correct one is: " + "${
                    versionCodeCompat(packageInfo)
                } vs found: ${apkMeta.versionCode} isSystemApp?$isSystemApp"
                )
            }
            //compare app label using library vs framework
            val labelOfLibrary = apkMeta.label ?: apkMeta.packageName
            if (VALIDATE_RESOURCES) {
                val expectedAppLabel = packageInfo.applicationInfo!!.loadLabel(packageManager)
                if (expectedAppLabel != labelOfLibrary.toString()) {
                    wrongLabelErrorsLiveData.inc()
                    if (isSystemApp) systemAppsErrorsCountLiveData.inc()
                    Log.e("AppLog", "label mismatch for \"${packageName}\": correct=\"$expectedAppLabel\" vs found=\"$labelOfLibrary\" apks:${allApkFilePaths.joinToString()}")
                }

                // Mirror framework's nonLocalizedLabel (hardcoded string in manifest)
                val nonLocalizedLabelOfLibrary = currentApkInfo.apkMetaTranslator.nonLocalizedLabel
                val nonLocalizedLabelOfFramework = packageInfo.applicationInfo?.nonLocalizedLabel?.toString()
                if (nonLocalizedLabelOfFramework != nonLocalizedLabelOfLibrary) {
                    Log.e("AppLog", "nonLocalizedLabel mismatch for \"$packageName\": framework=\"$nonLocalizedLabelOfFramework\" library=\"$nonLocalizedLabelOfLibrary\"")
                }

                // Get the default translation (usually from values/strings.xml)
                currentApkInfo.apkMetaTranslator.getDefaultLabel()
            }
            apkFilesHandledLiveData.inc()
            ++apksHandledSoFar
            appsHandledLiveData.inc()
        }
        endTime = System.currentTimeMillis()
        Log.d("AppLog", "time taken(ms): ${endTime - startTime} . handled ${installedPackages.size} apps, apksCount:$apksHandledSoFar")
        Log.d("AppLog", "averageTime(ms):${(endTime - startTime).toFloat() / installedPackages.size.toFloat()} per app, ${(endTime - startTime).toFloat() / apksHandledSoFar.toFloat()} per APK")
        Log.d("AppLog", "Final stats: labelErrors=${wrongLabelErrorsLiveData.value}, iconErrors=${failedGettingAppIconErrorsLiveData.value}, parsingErrors=${parsingErrorsLiveData.value}")
        Log.e("AppLog", "done")
        isDoneLiveData.postValue(true)
    }

    companion object {
        @Suppress("DEPRECATION")
        fun versionCodeCompat(packageInfo: PackageInfo) =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode else packageInfo.versionCode.toLong()

        enum class ZipFilterType {
            //this is the order from fastest to slowest, according to my tests:
            ZipFileFilter, ApacheZipArchiveInputStreamFilter, ZipInputStreamFilter, ApacheZipFileFilter
        }

        fun getZipFilter(apkFilePath: String, zipFilterType: ZipFilterType): AbstractZipFilter {
            return when (zipFilterType) {
                ZipFilterType.ZipFileFilter -> ZipFileFilter(ZipFile(apkFilePath))
                ZipFilterType.ApacheZipFileFilter -> {
                    ApacheZipFileFilter(org.apache.commons.compress.archivers.zip.ZipFile.Builder().setPath(apkFilePath).get())
                }

                ZipFilterType.ApacheZipArchiveInputStreamFilter -> ApacheZipArchiveInputStreamFilter(
                        ZipArchiveInputStream(
                                FileInputStream(
                                        apkFilePath
                                ), "UTF8", true, true
                        )
                )

                ZipFilterType.ZipInputStreamFilter -> ZipInputStreamFilter(
                        ZipInputStream(
                                FileInputStream(apkFilePath)
                        )
                )
            }
        }
    }
}
