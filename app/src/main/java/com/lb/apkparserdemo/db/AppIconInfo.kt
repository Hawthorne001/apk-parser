package com.lb.apkparserdemo.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_icons")
data class AppIconInfo(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val lastUpdateTime: Long,
    val iconFileName: String,
    val frameworkIconFileName: String
)
