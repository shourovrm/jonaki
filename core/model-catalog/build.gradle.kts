plugins {
    id("jonaki.jvm.library")
}

dependencies {
    api(project(":core:provider-api"))
    api(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.okhttp.mockwebserver)
}

tasks.withType<Test>().configureEach {
    // A trimmed copy of OpenRouter's model list lives in testdata/openrouter/.
    systemProperty("jonaki.testdata", rootProject.file("testdata").path)
}
