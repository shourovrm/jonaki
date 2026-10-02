plugins {
    id("jonaki.android.compose")
}

android {
    namespace = "app.jonaki.feature.settings"
}

dependencies {
    // ThemeMode is part of this module's public state.
    api(project(":core:ui"))
}
