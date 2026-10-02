pluginManagement {
    includeBuild("build-logic")
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
rootProject.name = "Jonaki"

include(":app")
include(":core:model")
include(":core:tool-api")
include(":core:provider-api")
include(":core:search-api")
include(":providers:openai-compatible")
include(":tools:echo")
include(":spikes:fts5")
