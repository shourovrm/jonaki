plugins {
    id("jonaki.android.application")
}

android {
    namespace = "app.jonaki"
    defaultConfig {
        applicationId = "app.jonaki"
        versionCode = 1
        versionName = "0.0.1"
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:tool-api"))
    implementation(project(":core:agent"))
    implementation(project(":tools:read-file"))
    implementation(project(":tools:write-file"))
    implementation(project(":tools:edit-file"))
    implementation(project(":tools:find-files"))
    implementation(project(":tools:search-files"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)

    testImplementation(libs.junit)
}
