package app.jonaki.buildlogic;

import com.android.build.api.dsl.LibraryExtension;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.jetbrains.kotlin.gradle.dsl.JvmTarget;
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension;

/**
 * Android library modules (storage, screens). Screens also apply
 * jonaki.android.compose on top of this.
 */
public final class AndroidLibraryConventionPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("com.android.library");
        project.getPluginManager().apply("org.jetbrains.kotlin.android");

        LibraryExtension android = project.getExtensions().getByType(LibraryExtension.class);
        android.setCompileSdk(AndroidSdk.COMPILE_SDK);
        android.getDefaultConfig().setMinSdk(AndroidSdk.MIN_SDK);
        android.getDefaultConfig().getNdk().getAbiFilters().add("arm64-v8a");
        android.getCompileOptions().setSourceCompatibility(JavaVersion.VERSION_17);
        android.getCompileOptions().setTargetCompatibility(JavaVersion.VERSION_17);

        project.getExtensions().getByType(KotlinAndroidProjectExtension.class)
                .getCompilerOptions().getJvmTarget().set(JvmTarget.JVM_17);

        project.getDependencies().add("testImplementation", "junit:junit:4.13.2");
    }
}
