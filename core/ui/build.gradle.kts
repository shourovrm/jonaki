plugins {
    id("jonaki.android.compose")
}

android {
    namespace = "app.jonaki.core.ui"
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.material3)
    api(libs.compose.material.icons.core)
}
