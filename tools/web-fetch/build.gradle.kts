plugins {
    id("jonaki.jvm.library")
}

dependencies {
    implementation(project(":core:tool-api"))
    implementation(libs.jsoup)
    implementation(libs.readability4j)
    testImplementation(libs.okhttp.mockwebserver)
}
