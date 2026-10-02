plugins {
    id("jonaki.android.compose")
}

android {
    namespace = "app.jonaki.feature.chat"
}

dependencies {
    implementation(project(":core:ui"))
}
