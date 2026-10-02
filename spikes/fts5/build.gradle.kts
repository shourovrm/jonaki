// Spike S-1 (D-009): does the bundled SQLite driver support FTS5 with the
// trigram tokenizer for mixed Bangla and English text? The JVM artifact ships
// the same SQLite build as the Android one, so a JVM test answers it.
plugins {
    id("jonaki.jvm.library")
}

dependencies {
    testImplementation("androidx.sqlite:sqlite-bundled-jvm:2.5.2")
}
