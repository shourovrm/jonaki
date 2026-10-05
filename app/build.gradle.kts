plugins {
    id("jonaki.android.application")
}

android {
    namespace = "app.jonaki"
    // The NDK that builds providers:local-llama; packaging strips its .so with this NDK's tools
    // (without it AGP looks for its own default NDK, finds none and packs the 105 MB unstripped file).
    ndkVersion = "28.2.13676358"
    defaultConfig {
        applicationId = "app.jonaki"
        versionCode = 14
        versionName = "1.4.1"
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
    implementation(project(":tools:search-chats"))
    implementation(project(":tools:propose-skill"))
    implementation(project(":tools:share-file"))
    implementation(project(":tools:export-pdf"))
    implementation(project(":tools:view-image"))
    implementation(project(":tools:read-document"))
    implementation(project(":tools:phone"))
    implementation(project(":tools:schedule"))
    // Scheduled tasks (plan M9).
    implementation(libs.work.runtime)
    implementation(project(":tools:mcp"))
    implementation(project(":tools:delegate"))
    implementation(project(":tools:run-code"))
    implementation(project(":core:runtime-api"))
    implementation(project(":runtimes:javascript"))
    implementation(project(":runtimes:pyodide"))
    // Only for PDFBoxResourceLoader.init at start; read_document does the reading (D-051).
    implementation(libs.pdfbox.android)
    implementation(project(":core:provider-api"))
    implementation(project(":core:search-api"))
    implementation(project(":core:storage"))
    implementation(project(":core:skills"))
    implementation(project(":core:model-catalog"))
    implementation(project(":core:local-models"))
    implementation(project(":core:balance-api"))
    implementation(project(":core:guard-api"))
    implementation(project(":guards:jev"))
    implementation(project(":providers:openai-compatible"))
    implementation(project(":providers:gemini"))
    implementation(project(":providers:local-llama"))
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
    implementation(project(":feature:gallery"))
    implementation(project(":feature:onboarding"))
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)

    testImplementation(libs.junit)
    // PythonSetupTest serves a fake Pyodide release, as the pyodide module's own tests do.
    testImplementation(libs.okhttp.mockwebserver)
}
