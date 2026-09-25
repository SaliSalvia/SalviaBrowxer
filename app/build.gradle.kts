import java.util.Base64
import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
}

// Release signing is driven entirely by environment variables so CI can sign
// with the owner's keystore without the keystore or passwords living in git.
val signingKeystoreBase64: String? = System.getenv("SIGNING_KEYSTORE_BASE64")
val signingKeystorePassword: String? = System.getenv("SIGNING_KEYSTORE_PASSWORD")
val signingKeyAlias: String? = System.getenv("SIGNING_KEY_ALIAS")
val signingKeyPassword: String? = System.getenv("SIGNING_KEY_PASSWORD")

// The keystore secret is pasted as base64 and is very often line-wrapped (termux
// `base64` wraps at 76 columns), so decode leniently: strip whitespace and ignore
// any character outside the base64 alphabet instead of failing the whole build.
val decodedSigningKeystore: ByteArray? = signingKeystoreBase64
    ?.filterNot { it.isWhitespace() }
    ?.takeIf { it.isNotEmpty() }
    ?.let { raw -> runCatching { Base64.getMimeDecoder().decode(raw) }.getOrNull() }
    ?.takeIf { it.size > 512 }

android {
    namespace = "com.salvia.salviabrowxer"
    compileSdk = 34

    signingConfigs {
        create("release") {
            // Only wire the config when all env vars are present (CI / local release builds).
            // Gradle skips it silently otherwise, keeping debug builds dependency-free.
            if (decodedSigningKeystore != null && signingKeystorePassword != null && signingKeyAlias != null) {
                val tmpKeystore = File.createTempFile("salviabrowxer", ".jks")
                tmpKeystore.deleteOnExit()
                tmpKeystore.writeBytes(decodedSigningKeystore)
                storeFile = tmpKeystore
                storePassword = signingKeystorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword ?: signingKeystorePassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.salvia.salviabrowxer"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Keep release APKs small and make sure the production variant exercises R8.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sign with the owner's key when env vars are provided (CI); otherwise unsigned.
            if (decodedSigningKeystore != null && signingKeystorePassword != null && signingKeyAlias != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/LICENSE-notice.md"
        }
    }

    lint {
        abortOnError = true
        // Full release lint runs in CI only if explicitly requested; keeping it off
        // here keeps assembleRelease lean (R8 + dexing are already the heavy steps).
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kapt {
    correctErrorTypes = true
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":core:storage"))
    implementation(project(":media:detector"))
    implementation(project(":media:resolver"))
    implementation(project(":media:extractor"))
    implementation(project(":media:downloader"))
    implementation(project(":media:processor"))

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")

    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("androidx.webkit:webkit:1.10.0")

    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("io.coil-kt:coil-compose:2.5.0")

    implementation("com.google.dagger:hilt-android:2.48.1")
    kapt("com.google.dagger:hilt-compiler:2.48.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    implementation("org.jsoup:jsoup:1.17.2")
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.mockito:mockito-core:5.3.1")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
