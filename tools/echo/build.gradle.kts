plugins {
    id("jonaki.jvm.library")
}

dependencies {
    implementation(project(":core:tool-api"))
    testImplementation(libs.kotlinx.coroutines.core)
}
