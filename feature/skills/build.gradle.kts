plugins {
    id("jonaki.android.compose")
}

android {
    namespace = "app.jonaki.feature.skills"
}

dependencies {
    implementation(project(":core:ui"))
}
