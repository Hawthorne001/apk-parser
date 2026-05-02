import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    id("com.android.library")
    id("maven-publish")
}

extensions.configure<LibraryExtension> {
    namespace = "net.dongliu.apk.parser"
    compileSdk = 37

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
        multiDexEnabled = true
    }
    lint {
        targetSdk = 37
    }

    buildTypes {
        getByName("release") {
//            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(fileTree("libs") { include("*.jar") })
//   https://mvnrepository.com/artifact/org.bouncycastle/bcprov-jdk18on
    implementation("org.bouncycastle:bcprov-jdk18on:1.84")
    //   https://mvnrepository.com/artifact/org.bouncycastle/bcpkix-jdk18on
    implementation("org.bouncycastle:bcpkix-jdk18on:1.84")
//    https://mvnrepository.com/artifact/androidx.annotation/annotation
    implementation("androidx.annotation:annotation:1.10.0")
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                from(components["release"])
            }
        }
    }
}
