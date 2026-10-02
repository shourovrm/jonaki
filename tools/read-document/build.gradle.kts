// An Android library, not a JVM one like the other tools: PdfBox-Android
// ships as an AAR (D-051). The tool still depends only on core/tool-api.
plugins {
    id("jonaki.android.library")
}

android {
    namespace = "app.jonaki.tools.readdocument"
    testOptions {
        // PdfBox-Android calls android.util.Log, which the JVM test stubs would otherwise throw on.
        unitTests.isReturnDefaultValues = true
    }
}

// PdfBox-Android reads its font and glyph tables from the app's assets; on the
// JVM, without PDFBoxResourceLoader.init, it looks for them on the classpath.
val pdfboxArchive: Configuration by configurations.creating

dependencies {
    implementation(project(":core:tool-api"))
    implementation(libs.pdfbox.android)
    pdfboxArchive(libs.pdfbox.android) {
        artifact { type = "aar" }
        isTransitive = false
    }
}

val pdfboxTestResources = layout.buildDirectory.dir("pdfbox-test-resources")

val unpackPdfboxAssets by tasks.registering(Sync::class) {
    from({ zipTree(pdfboxArchive.singleFile) }) {
        include("assets/**")
        eachFile { path = path.removePrefix("assets/") }
        includeEmptyDirs = false
    }
    into(pdfboxTestResources)
}

// AGP sets the unit test classpath itself, so the tables join the test resources.
android.sourceSets.getByName("test").resources.srcDir(pdfboxTestResources)
tasks.matching { task -> task.name.endsWith("UnitTestJavaRes") }.configureEach { dependsOn(unpackPdfboxAssets) }

tasks.withType<Test>().configureEach {
    // Small PDF files made for these tests live in testdata/documents/.
    systemProperty("jonaki.testdata", rootProject.file("testdata").path)
}
