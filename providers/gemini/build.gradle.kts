plugins {
    id("jonaki.jvm.library")
}

dependencies {
    api(project(":core:provider-api"))
    // Only for the ImageGenerator interface that GeminiImageGenerator implements.
    implementation(project(":core:tool-api"))
    api(libs.okhttp)
    testImplementation(libs.okhttp.mockwebserver)
}

tasks.withType<Test>().configureEach {
    // Recorded responses from spike S-3 live in the repository's testdata/ folder.
    systemProperty("jonaki.testdata", rootProject.file("testdata").path)
}
