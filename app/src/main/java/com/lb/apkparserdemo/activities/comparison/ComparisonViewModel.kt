package com.lb.apkparserdemo.activities.comparison

import android.app.Application
import android.graphics.BitmapFactory
import androidx.core.graphics.get
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.lb.apkparserdemo.db.AppDatabase
import com.lb.apkparserdemo.utils.IconStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ComparisonViewModel(application: Application) : AndroidViewModel(application) {
    private val _items = MutableLiveData<List<ComparisonItem>>()
    val items: LiveData<List<ComparisonItem>> = _items

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    init {
        loadItems()
    }

    private fun loadItems() {
        _loading.value = true
        viewModelScope.launch {
            val db = AppDatabase.getDatabase(getApplication())
            val allItems = db.appIconDao().getAll()
            val scannedPackages = com.lb.apkparserdemo.utils.SessionTracker.getScannedPackages()
            val baseList = allItems
                .filter { scannedPackages.isEmpty() || scannedPackages.contains(it.packageName) }
                .sortedBy { it.appName.lowercase() }

            // Perform pixel-by-pixel comparison for all items before showing them
            val finalItems = withContext(Dispatchers.Default) {
                baseList.map { info ->
                    val context = getApplication<Application>()
                    val libFile = IconStorage.getIconFile(context, info.iconFileName)
                    val fwFile = IconStorage.getIconFile(context, info.frameworkIconFileName)
                    val score = compareIcons(libFile, fwFile)
                    ComparisonItem(info, score)
                }
            }

            _items.value = finalItems
            _loading.value = false
        }
    }

    private fun compareIcons(libFile: File, fwFile: File): Float {
        if (!libFile.exists() || !fwFile.exists()) return 0f

        val libBitmap = BitmapFactory.decodeFile(libFile.absolutePath) ?: return 0f
        val fwBitmap = BitmapFactory.decodeFile(fwFile.absolutePath) ?: return 0f

        if (libBitmap.width != fwBitmap.width || libBitmap.height != fwBitmap.height) return 0f

        var matchingPixels = 0
        val totalPixels = libBitmap.width * libBitmap.height

        for (y in 0 until libBitmap.height) {
            for (x in 0 until libBitmap.width) {
                if (libBitmap[x, y] == fwBitmap[x, y]) {
                    matchingPixels++
                }
            }
        }
        return matchingPixels.toFloat() / totalPixels.toFloat()
    }
}
