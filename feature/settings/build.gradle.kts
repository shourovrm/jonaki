plugins {
    id("jonaki.android.compose")
}

android {
    namespace = "app.jonaki.feature.settings"
}

dependencies {
    // ThemeMode is part of this module's public state.
    api(project(":core:ui"))
    // For the 360 dp and font scale 1.3 previews (D-029); already a dependency of the app.
    implementation(libs.compose.ui.tooling.preview)
}
