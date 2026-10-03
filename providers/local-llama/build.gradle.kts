// An Android library because it carries llama.cpp as native code (D-133).
// The JNI layer is in src/main/cpp; llama.cpp itself is the git submodule
// src/main/cpp/llama.cpp, pinned to release b11366.
plugins {
    id("jonaki.android.library")
}

android {
    namespace = "app.jonaki.providers.localllama"
    ndkVersion = "28.2.13676358"
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake {
                // AGP builds release native code as RelWithDebInfo (-O2); llama.cpp is tuned for -O3.
                arguments += listOf("-DCMAKE_BUILD_TYPE=Release", "-DANDROID_STL=c++_static")
            }
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }
}

dependencies {
    api(project(":core:provider-api"))
}
