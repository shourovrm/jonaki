plugins {
    id("jonaki.jvm.library")
}

dependencies {
    api(project(":core:provider-api"))
    api(libs.okhttp)
    testImplementation(libs.okhttp.mockwebserver)
}
