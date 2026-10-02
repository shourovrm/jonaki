plugins {
    id("jonaki.jvm.library")
}

dependencies {
    api(libs.okhttp)
    // For Call.await, which cancels a download when its coroutine is cancelled.
    implementation(project(":core:tool-api"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.okhttp.mockwebserver)
}
