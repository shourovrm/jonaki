package app.jonaki.buildlogic;

import com.android.build.api.dsl.ApplicationExtension;
import com.android.build.api.dsl.ApkSigningConfig;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.jetbrains.kotlin.gradle.dsl.JvmTarget;
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension;

/**
 * Settings shared by the application module: SDK levels, arm64 only, Java 17,
 * Compose, and release signing with jonaki.keystore (AGENTS.md, Build and
 * release).
 */
public final class AndroidApplicationConventionPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("com.android.application");
        project.getPluginManager().apply("org.jetbrains.kotlin.android");
        project.getPluginManager().apply("org.jetbrains.kotlin.plugin.compose");

        ApplicationExtension android = project.getExtensions().getByType(ApplicationExtension.class);
        android.setCompileSdk(AndroidSdk.COMPILE_SDK);
        android.getDefaultConfig().setMinSdk(AndroidSdk.MIN_SDK);
        android.getDefaultConfig().setTargetSdk(AndroidSdk.TARGET_SDK);
        android.getDefaultConfig().getNdk().getAbiFilters().add("arm64-v8a");
        android.getCompileOptions().setSourceCompatibility(JavaVersion.VERSION_17);
        android.getCompileOptions().setTargetCompatibility(JavaVersion.VERSION_17);
        android.getBuildFeatures().setCompose(true);

        ApkSigningConfig releaseSigning = android.getSigningConfigs().create("release");
        releaseSigning.setStoreFile(project.getRootProject().file("jonaki.keystore"));
        releaseSigning.setStorePassword("android");
        releaseSigning.setKeyAlias("jonaki");
        releaseSigning.setKeyPassword("android");

        android.getBuildTypes().getByName("release", release -> {
            release.setMinifyEnabled(true);
            release.setShrinkResources(true);
            release.proguardFiles(
                    android.getDefaultProguardFile("proguard-android-optimize.txt"),
                    project.file("proguard-rules.pro"));
            release.setSigningConfig(releaseSigning);
        });

        project.getExtensions().getByType(KotlinAndroidProjectExtension.class)
                .getCompilerOptions().getJvmTarget().set(JvmTarget.JVM_17);
    }
}
