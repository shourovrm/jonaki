plugins {
    id("jonaki.android.compose")
}

android {
    namespace = "app.jonaki.feature.chat"
}

dependencies {
    implementation(project(":core:ui"))
    // The attachment line of a message (D-042) and which files count as images.
    implementation(project(":core:agent"))
    implementation(project(":core:tool-api"))
    // For the 360 dp and font scale 1.3 previews (D-029); already a dependency of the app.
    implementation(libs.compose.ui.tooling.preview)
}
