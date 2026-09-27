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

// A module only exists when it has a public API and a caller outside itself.
// Feature screens live in :app; this list is the whole graph.
include(":app")

// Core
include(":core:model")
include(":core:database")

// Media
include(":media:detector")
include(":media:resolver")
include(":media:downloader")