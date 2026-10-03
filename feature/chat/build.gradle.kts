plugins {
    id("jonaki.android.compose")
}

android {
    namespace = "app.jonaki.feature.chat"
}

dependencies {
    implementation(project(":core:ui"))
    // For the 360 dp and font scale 1.3 previews (D-029); already a dependency of the app.
    implementation(libs.compose.ui.tooling.preview)
}
