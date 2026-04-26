package com.lb.apkparserdemo.activities.comparison

import com.lb.apkparserdemo.db.AppIconInfo

data class ComparisonItem(
    val info: AppIconInfo,
    val matchScore: Float? = null
)
