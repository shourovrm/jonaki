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

    // The JVM build of the same bundled SQLite (spike S-1), so migration and
    // FTS5 tests run on the computer instead of a device.
    testImplementation(libs.sqlite.bundled.jvm)
}

// The Android build of the driver carries only Android native code; in JVM
// unit tests its classes would hide the JVM build's.
configurations.matching { configuration -> configuration.name.endsWith("UnitTestRuntimeClasspath") }.configureEach {
    exclude(group = "androidx.sqlite", module = "sqlite-bundled-android")
}
