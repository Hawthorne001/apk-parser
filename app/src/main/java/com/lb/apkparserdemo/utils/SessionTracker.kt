package com.lb.apkparserdemo.utils

object SessionTracker {
    private val scannedPackages = HashSet<String>()

    @Synchronized
    fun addPackage(packageName: String) {
        scannedPackages.add(packageName)
    }

    @Synchronized
    fun clear() {
        scannedPackages.clear()
    }

    @Synchronized
    fun getScannedPackages(): Set<String> {
        return scannedPackages.toSet()
    }
}
