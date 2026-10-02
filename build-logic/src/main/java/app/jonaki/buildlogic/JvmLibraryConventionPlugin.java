package app.jonaki.buildlogic;

import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.jetbrains.kotlin.gradle.dsl.JvmTarget;
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension;

/**
 * Plain Kotlin modules (core, tools) that need no Android API, so their tests
 * run fast on the JVM.
 */
public final class JvmLibraryConventionPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("org.jetbrains.kotlin.jvm");

        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        java.setSourceCompatibility(JavaVersion.VERSION_17);
        java.setTargetCompatibility(JavaVersion.VERSION_17);
        project.getExtensions().getByType(KotlinJvmProjectExtension.class)
                .getCompilerOptions().getJvmTarget().set(JvmTarget.JVM_17);

        project.getDependencies().add("testImplementation", "junit:junit:4.13.2");

        // The project's one check command is `gradle testReleaseUnitTest
        // assembleRelease`; Android modules have that task, JVM modules only
        // have `test`. The alias makes the one command cover both.
        project.getTasks().register("testReleaseUnitTest", task -> {
            task.setGroup("verification");
            task.setDescription("Runs the JVM unit tests (alias of test).");
            task.dependsOn("test");
        });
    }
}
