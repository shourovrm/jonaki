plugins {
    id("jonaki.jvm.library")
}

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
}
