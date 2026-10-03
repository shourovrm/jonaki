// An Android library: JavaScriptSandbox (androidx.javascriptengine) runs the
// program in Android System WebView's isolated sandbox process.
plugins {
    id("jonaki.android.library")
}

android {
    namespace = "app.jonaki.runtimes.javascript"
}

dependencies {
    implementation(project(":core:runtime-api"))
    implementation(project(":core:tool-api"))
    implementation(libs.javascriptengine)
    implementation(libs.kotlinx.serialization.json)
}
