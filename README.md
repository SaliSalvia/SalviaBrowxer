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
- **WorkManager**: Background tasks
- **OkHttp**: Network requests

## Modules

- **app**: Main application module
- **core**: Core functionality
  - common: Shared utilities
  - model: Data models
  - network: Network operations
  - database: Database access
  - storage: File storage
  - testing: Test utilities
- **feature**: Feature modules
  - browser: Browser functionality
  - downloads: Download management
  - bookmarks: Bookmark management
  - history: Browsing history
  - settings: Application settings
  - player: Media player
  - home: Home screen
- **media**: Media handling
  - detector: Media detection
  - resolver: Media resolution
  - extractor: Media extraction
  - downloader: Download engine
  - processor: Media processing

## Getting Started

1. Clone the repository:
   ```bash
   git clone https://github.com/SaliSalvia/SalviaBrowxer.git
   ```
2. Open in Android Studio
3. Add your logo to `app/src/main/res/drawable-nodpi/salviabrowxer_logo.png`
4. Build and run

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
- WRITE_EXTERNAL_STORAGE: To save downloaded files (on older Android versions)
- READ_EXTERNAL_STORAGE: To read existing files (on older Android versions)
- MANAGE_EXTERNAL_STORAGE: For advanced file management (optional)

## License

GPL-3.0

## Contributing

Contributions are welcome! Please open an issue or submit a pull request.