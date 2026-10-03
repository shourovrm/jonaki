plugins {
    id("jonaki.android.compose")
}

android {
    namespace = "app.jonaki.feature.skills"
}

dependencies {
    implementation(project(":core:ui"))
    // BackHandler, so Back can ask before unsaved edits are lost; the app already ships this library.
    implementation(libs.activity.compose)
}
