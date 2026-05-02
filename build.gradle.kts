plugins {
    id("com.android.application") version "9.3.0-alpha03" apply false
    id("com.android.library") version "9.2.0" apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
