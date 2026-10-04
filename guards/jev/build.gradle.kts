plugins {
    id("jonaki.jvm.library")
}

dependencies {
    api(project(":core:guard-api"))
    api(libs.okhttp)
    testImplementation(libs.okhttp.mockwebserver)
}

tasks.withType<Test>().configureEach {
    // The recorded Jev answers live in the repository's testdata/ folder.
    systemProperty("jonaki.testdata", rootProject.file("testdata").path)
}
