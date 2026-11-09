plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.salvia.salviabrowxer.media.downloader"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
// Kotlin 2.4 removed the String-based kotlinOptions DSL; compilerOptions is the supported way.
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}
dependencies {
    implementation(project(":core:model"))
    implementation(project(":media:resolver"))
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // SegmentedFetcher runs the segment transfers in parallel with bounded concurrency.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // DashDownloader and HlsDownloader are pure OkHttp + file IO, so they are exercised against a
    // real local HTTP server here rather than only on a device.
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}
