plugins {
    id("jonaki.jvm.library")
}

dependencies {
    implementation(project(":core:tool-api"))
    implementation(project(":core:runtime-api"))
}
