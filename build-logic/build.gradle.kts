// The convention plugins are written in Java because the `kotlin-dsl` plugin
// is not in the local Gradle cache and D-021 forbids new toolchain downloads.
plugins {
    `java-gradle-plugin`
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

dependencies {
    // compileOnly: the root build puts the real plugin jars on the classpath.
    compileOnly("com.android.tools.build:gradle:8.7.3")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0")
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "jonaki.android.application"
            implementationClass = "app.jonaki.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("jvmLibrary") {
            id = "jonaki.jvm.library"
            implementationClass = "app.jonaki.buildlogic.JvmLibraryConventionPlugin"
        }
    }
}
