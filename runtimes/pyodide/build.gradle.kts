// An Android library: Python runs in Pyodide inside a hidden WebView (D-013, D-069).
plugins {
    id("jonaki.android.library")
}

android {
    namespace = "app.jonaki.runtimes.pyodide"
}

dependencies {
    implementation(project(":core:runtime-api"))
    implementation(project(":core:tool-api"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.core)
}
