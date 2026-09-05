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
include(":core:common")
include(":core:model")
include(":core:network")
include(":core:database")
include(":core:storage")
include(":core:testing")

// Feature modules
include(":feature:browser")
include(":feature:downloads")
include(":feature:bookmarks")
include(":feature:history")
include(":feature:settings")
include(":feature:player")
include(":feature:home")

// Media modules
include(":media:detector")
include(":media:resolver")
include(":media:extractor")
include(":media:downloader")
include(":media:processor")