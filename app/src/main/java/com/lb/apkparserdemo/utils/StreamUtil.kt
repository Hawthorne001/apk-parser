package com.lb.apkparserdemo.utils

import java.io.Closeable

fun Closeable?.closeSilently() {
    if (this != null) try {
        this.close()
    } catch (_: Exception) {
    }
}

class StreamUtil {

}
