plugins {
    id("jonaki.jvm.library")
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
    api(libs.okhttp)
}
