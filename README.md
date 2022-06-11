# apk-parser

Apk parser for java/Android, forked from here after fixing some issues in it and collecting some fixes from others :

https://github.com/hsiafan/apk-parser

# Why use this library, as we can do it using the Android framework instead?

While the Android framework is more official and should work better in most cases, there are multiple reasons to use this library :

1. Can handle APK files that are not on the file system. The Android framework requires you to use a file path, always.
2. Can handle split APK files too. The Android framework can only handle the base ones or stand-alone files.
3. Can find some APK properties that aren't available on older Android versions.
4. While the Android framework is technically open sourced, it has various things that are protected and can't be reached, and also hard to import as your own code.

# Usage in gradle file

https://jitpack.io/#AndroidDeveloperLB/apk-parser/

# Known issues and notes

- The sample app shows that in some rare cases it fails to parse the label/icon/version-code of the app, or even the app itself (incredibly rare). It seems to occur only for system apps though. I hope that some day it could be fixed. Reported here: https://github.com/AndroidDeveloperLB/apk-parser/issues/1 https://github.com/AndroidDeveloperLB/apk-parser/issues/2  https://github.com/AndroidDeveloperLB/apk-parser/issues/3 https://github.com/AndroidDeveloperLB/apk-parser/issues/4
- The entire code is in Java. I personally prefer Kotlin. I hope one day the whole library would be in Kotlin. At the very least, we should have a clear understanding for everything, if it's nullable or not. This needs to be carefully done and without ruining the performance and memory usage of the library.
- Could be nice to have better optimization in memory usage and speed, because somehow the framework seems to be more efficient on both. I think a better optimization is needed. Maybe some sort of way to tell exactly what we want to get out of it, it would minimize such memory usage.
