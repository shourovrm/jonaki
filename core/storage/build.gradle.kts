plugins {
    id("jonaki.android.library")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

android {
    namespace = "app.jonaki.core.storage"
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(project(":core:model"))
    api(libs.room.runtime)
    implementation(libs.sqlite.bundled)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    ksp(libs.room.compiler)
}
