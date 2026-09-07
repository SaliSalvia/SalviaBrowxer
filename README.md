# SalviaBrowxer

A fast, modern, and premium native Android browser with intelligent media detection and download capabilities.

## Features

- **Modern Browser**: Fast, smooth, and native browsing experience
- **Media Detection**: Automatically detects downloadable media from web pages
- **Media Download**: Download videos, audio, and other media with quality selection
- **Background Downloads**: Continue downloading even when you leave the app
- **Multiple Tabs**: Browse with multiple tabs
- **Bookmarks**: Save your favorite websites
- **History**: Keep track of your browsing history
- **Private Browsing**: Browse without saving history
- **Dark Theme**: Premium dark visual identity
- **Customizable**: Adjust settings to your preference

## Architecture

- **Clean Architecture + MVVM**: Separation of concerns with ViewModels, Use Cases, and Repositories
- **Modular**: Organized into feature and core modules
- **Jetpack Compose**: Modern UI toolkit
- **Kotlin**: First-class Kotlin support
- **Coroutines**: Asynchronous programming with Kotlin Coroutines
- **Room**: Persistence with SQLite
- **DataStore**: Preferences storage
- **OkHttp**: Network requests

## Modules

- **app**: Main application module (Compose UI, ViewModels, DI, download service)
- **core:model**: Shared data models and the single source of truth for media extension/MIME lists
- **core:database**: Room database and DAOs
- **media:detector**: DOM/WebView media detection
- **media:resolver**: Direct media resolution
- **media:downloader**: OkHttp download engine

## Getting Started

1. Clone the repository:
   ```bash
   git clone https://github.com/SaliSalvia/SalviaBrowxer.git
   ```
2. Open in Android Studio
3. Add your logo to `app/src/main/res/drawable-nodpi/salviabrowxer_logo.png`
4. Build and run

The Gradle wrapper is committed to the repository (`gradlew`, `gradlew.bat` and
`gradle/wrapper/gradle-wrapper.jar`, pinned to Gradle 8.7), so a local Android/Gradle install is not
required:

```bash
./gradlew :app:assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk
```

### CI debug APK

`Android CI` (`.github/workflows/android_ci.yml`) builds the debug variant on JDK 17 and uploads it
as the `salviabrowxer-debug-apk` artifact:

```bash
gh run download --name salviabrowxer-debug-apk
```

## Requirements

- Android Studio (latest version)
- JDK 17+
- Android SDK 34+
- Minimum SDK: 24 (Android 7.0 Nougat)

## Configuration

Add your logo to `app/src/main/res/drawable-nodpi/salviabrowxer_logo.png`

## Permissions

The app requires the following permissions:
- INTERNET: For web browsing
- ACCESS_NETWORK_STATE: To check network status
- WAKE_LOCK: To keep device awake during downloads
- FOREGROUND_SERVICE: For download service
- WRITE_EXTERNAL_STORAGE: To save downloaded files (Android 9 and below only)
- READ_EXTERNAL_STORAGE: To read existing files (Android 12 and below only)

Downloaded media is written to the app-scoped external storage directory
(`Android/data/com.salvia.salviabrowxer/files/Downloads`) so no runtime storage permission is needed,
and each finished file is handed to `MediaScannerConnection` so it also shows up in the system
downloads UI. Finished files are shared with other apps through a `FileProvider`.

## License

GPL-3.0

## Contributing

Contributions are welcome! Please open an issue or submit a pull request.