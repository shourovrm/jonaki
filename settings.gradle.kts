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
include(":core:agent")
include(":tools:echo")
include(":tools:edit-file")
include(":tools:find-files")
include(":tools:read-file")
include(":tools:search-files")
include(":tools:write-file")
include(":spikes:fts5")
