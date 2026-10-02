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
include(":core:storage")
include(":core:model-catalog")
include(":core:balance-api")
include(":core:ui")
include(":core:skills")
include(":feature:threads")
include(":feature:chat")
include(":feature:settings")
include(":feature:memory")
include(":feature:artifact")
include(":feature:skills")
include(":providers:openai-compatible")
include(":providers:gemini")
include(":search:tavily")
include(":search:ollama")
include(":search:exa")
include(":tools:edit-file")
include(":tools:find-files")
include(":tools:read-file")
include(":tools:search-files")
include(":tools:write-file")
include(":tools:web-search")
include(":tools:web-fetch")
include(":tools:youtube-summarize")
include(":tools:memory")
include(":tools:artifact")
include(":tools:share-file")
include(":tools:view-image")
include(":tools:read-document")
include(":tools:delegate")
include(":spikes:fts5")
