plugins {
    id("com.android.application") version "9.4.0-alpha08" apply false
    id("com.android.library") version "9.3.1" apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
