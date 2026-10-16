pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "chronos-game-engine"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(
    ":core-domain",
    ":core-application",
    ":adapter-persistence",
    ":adapter-network",
    ":adapter-presentation",
    ":bootstrap"
)
