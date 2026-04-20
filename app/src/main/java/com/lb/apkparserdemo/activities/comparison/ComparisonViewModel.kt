package com.lb.apkparserdemo.activities.comparison

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.lb.apkparserdemo.db.AppDatabase
import com.lb.apkparserdemo.db.AppIconInfo
import kotlinx.coroutines.launch

class ComparisonViewModel(application: Application) : AndroidViewModel(application) {
    private val _items = MutableLiveData<List<AppIconInfo>>()
    val items: LiveData<List<AppIconInfo>> = _items

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    init {
        loadItems()
    }

    private fun loadItems() {
        _loading.value = true
        viewModelScope.launch {
            val db = AppDatabase.getDatabase(getApplication())
            val list = db.appIconDao().getAll()
            _items.value = list
            _loading.value = false
        }
    }
}
