plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.salvia.salviabrowxer.core.model"
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
    implementation("androidx.room:room-runtime:2.8.5")
    // MediaUrlRules and MediaSniffAdmission are pure Kotlin, so they are tested here rather than
    // through :app.
    testImplementation("junit:junit:4.13.2")
}
