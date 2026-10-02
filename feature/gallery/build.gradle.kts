plugins {
    id("jonaki.android.compose")
}

android {
    namespace = "app.jonaki.feature.gallery"
}

dependencies {
    implementation(project(":core:ui"))
    // BackHandler, to step back from an album to Collections; already a dependency of the app.
    implementation(libs.activity.compose)
    // For the 360 dp and font scale 1.3 previews (D-029); already a dependency of the app.
    implementation(libs.compose.ui.tooling.preview)
}
