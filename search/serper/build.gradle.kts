plugins {
    id("jonaki.jvm.library")
}

dependencies {
    api(project(":core:search-api"))
    api(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.okhttp.mockwebserver)
}
