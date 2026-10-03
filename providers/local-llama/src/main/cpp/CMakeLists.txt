cmake_minimum_required(VERSION 3.31.6)

project(jonaki_llama C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# llama.cpp is a git submodule pinned to release b11366 (D-133). Only the
# library, ggml's CPU backend and the common sources for chat templates,
# tool-call grammars and sampling are built; static, so that the app ships
# one .so.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(LLAMA_SUBPROCESS OFF CACHE BOOL "" FORCE)
set(LLAMA_LLGUIDANCE OFF CACHE BOOL "" FORCE)

# One fixed CPU level instead of GGML_CPU_ALL_VARIANTS, which needs one
# dynamically loaded .so per level. armv8.2-a with dotprod, fp16 and i8mm is
# the feature set of the "armv8.6" variant the loader chose on the test phone
# (Snapdragon 7s Gen 3). LocalLlamaProvider checks /proc/cpuinfo for these
# features before loading the library, so an older phone gets an error
# message instead of an illegal-instruction crash.
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16+i8mm" CACHE STRING "" FORCE)
# ggml's own thread pool; OpenMP would add libomp.so to the APK.
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
# KleidiAI is downloaded at configure time, which would be a further dependency.
set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "" FORCE)
set(GGML_OPENCL OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)

# Lets the linker drop the parts of the static libraries the JNI layer never calls.
add_compile_options(-ffunction-sections -fdata-sections)

add_subdirectory(llama.cpp build-llama)

add_library(jonaki_llama SHARED jonaki_llama.cpp)

# Only the JNIEXPORT functions need to be visible.
target_compile_options(jonaki_llama PRIVATE -fvisibility=hidden -fvisibility-inlines-hidden)

target_link_libraries(jonaki_llama PRIVATE llama-common llama ggml android log)

target_link_options(jonaki_llama PRIVATE
    -Wl,--gc-sections
    # Symbols of the static libraries stay hidden, so only the JNI functions are exported.
    -Wl,--exclude-libs,ALL
    # NDK r28 already links with 16 KB pages; stated here so the requirement is visible.
    -Wl,-z,max-page-size=16384)
