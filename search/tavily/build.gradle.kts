plugins {
    id("jonaki.jvm.library")
}

dependencies {
    api(project(":core:search-api"))
    api(project(":core:balance-api"))
    api(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.okhttp.mockwebserver)
}

tasks.withType<Test>().configureEach {
    // Recorded responses from spike S-4 live in the repository's testdata/ folder.
    systemProperty("jonaki.testdata", rootProject.file("testdata").path)
}
