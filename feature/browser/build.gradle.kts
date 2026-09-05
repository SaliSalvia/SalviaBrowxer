plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.salvia.salviabrowxer.feature.browser"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        targetSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(project(":app"))
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":media:detector"))
    implementation("androidx.webkit:webkit:1.10.0")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
}