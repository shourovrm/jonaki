// Plugin versions are fixed by D-021 so that the Gradle cache of the user's
// other apps is reused. The convention plugins in build-logic apply them.
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library") version "8.7.3" apply false
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
    id("androidx.room") version "2.7.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
}
