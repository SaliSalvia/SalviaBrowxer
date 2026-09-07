pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SalviaBrowxer"
include(":app")

// Core modules
include(":core:model")
include(":core:database")

// Media modules
include(":media:detector")
include(":media:resolver")
include(":media:downloader")
