plugins {
    id("jonaki.android.compose")
}

android {
    namespace = "app.jonaki.feature.memory"
}

dependencies {
    implementation(project(":core:ui"))
}
