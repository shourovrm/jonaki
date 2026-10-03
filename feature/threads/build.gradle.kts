plugins {
    id("jonaki.android.compose")
}

android {
    namespace = "app.jonaki.feature.threads"
}

dependencies {
    implementation(project(":core:ui"))
    implementation(libs.compose.ui.tooling.preview)
}
