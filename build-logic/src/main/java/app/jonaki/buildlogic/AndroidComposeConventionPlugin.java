package app.jonaki.buildlogic;

import com.android.build.api.dsl.LibraryExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

/** Adds Compose to an Android library module; apply after jonaki.android.library. */
public final class AndroidComposeConventionPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("jonaki.android.library");
        project.getPluginManager().apply("org.jetbrains.kotlin.plugin.compose");
        project.getExtensions().getByType(LibraryExtension.class).getBuildFeatures().setCompose(true);
    }
}
