plugins {
    id("jonaki.android.compose")
}

android {
    namespace = "app.jonaki.feature.artifact"
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:tool-api"))
}
