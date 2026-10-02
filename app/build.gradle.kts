plugins {
    id("jonaki.android.application")
}

android {
    namespace = "app.jonaki"
    defaultConfig {
        applicationId = "app.jonaki"
        versionCode = 7
        versionName = "0.7.0"
    }
    packaging {
        resources {
            // Bouncy Castle comes with PdfBox-Android for certificate-locked PDFs. Its tables for
            // post-quantum ciphers (4.1 MB compressed) and certificate-path messages are never used (D-051).
            excludes += "org/bouncycastle/pqc/**"
            excludes += "org/bouncycastle/x509/*.properties"
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:tool-api"))
    implementation(project(":core:agent"))
    implementation(project(":tools:read-file"))
    implementation(project(":tools:write-file"))
    implementation(project(":tools:edit-file"))
    implementation(project(":tools:find-files"))
    implementation(project(":tools:search-files"))
    implementation(project(":tools:web-search"))
    implementation(project(":tools:web-fetch"))
    implementation(project(":tools:youtube-summarize"))
    implementation(project(":tools:memory"))
    implementation(project(":tools:share-file"))
    implementation(project(":tools:view-image"))
    implementation(project(":tools:read-document"))
    implementation(project(":tools:phone"))
    implementation(project(":tools:schedule"))
    // Scheduled tasks (plan M9).
    implementation(libs.work.runtime)
    implementation(project(":tools:mcp"))
    // Only for PDFBoxResourceLoader.init at start; read_document does the reading (D-051).
    implementation(libs.pdfbox.android)
    implementation(project(":core:provider-api"))
    implementation(project(":core:search-api"))
    implementation(project(":core:storage"))
    implementation(project(":core:skills"))
    implementation(project(":core:model-catalog"))
    implementation(project(":core:balance-api"))
    implementation(project(":providers:openai-compatible"))
    implementation(project(":providers:gemini"))
    implementation(project(":search:tavily"))
    implementation(project(":search:ollama"))
    implementation(project(":search:exa"))
    implementation(libs.okhttp)
    implementation(project(":core:ui"))
    implementation(project(":feature:threads"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:artifact"))
    implementation(project(":tools:artifact"))
    implementation(project(":feature:memory"))
    implementation(project(":feature:skills"))
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)

    testImplementation(libs.junit)
}
