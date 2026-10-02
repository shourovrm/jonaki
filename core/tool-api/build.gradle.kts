plugins {
    id("jonaki.jvm.library")
}

dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlinx.coroutines.core)
}
