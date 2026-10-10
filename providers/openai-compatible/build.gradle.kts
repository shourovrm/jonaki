plugins {
    id("jonaki.jvm.library")
}

dependencies {
    api(project(":core:provider-api"))
    api(project(":core:balance-api"))
    // Only for the ImageGenerator interface that OpenRouterImageGenerator implements.
    implementation(project(":core:tool-api"))
    api(libs.okhttp)
    testImplementation(libs.okhttp.mockwebserver)
}

tasks.withType<Test>().configureEach {
    // Recorded OpenRouter errors live in the repository's testdata/ folder.
    systemProperty("jonaki.testdata", rootProject.file("testdata").path)
}
