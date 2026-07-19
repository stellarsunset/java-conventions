package io.github.stellarsunset.conventions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import org.gradle.api.Project;
import org.gradle.api.plugins.quality.CheckstyleExtension;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests that apply the plugin to an in-memory {@link Project} and assert that the expected
 * plugins and extensions are wired up. The project directory is a real git repository because the
 * bundled auto-semver plugin resolves the project version from git at configuration time.
 */
class JavaConventionsPluginTest {

    @Test
    void appliesBundledPlugins(@TempDir File projectDir) throws Exception {
        initGitRepository(projectDir);

        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build();
        project.getPlugins().apply(JavaConventionsPlugin.class);

        assertTrue(project.getPlugins().hasPlugin("java"), "java");
        assertTrue(project.getPlugins().hasPlugin("jacoco"), "jacoco");
        assertTrue(
                project.getPlugins().hasPlugin("io.github.stellarsunset.auto-semver"), "auto-semver");
        assertTrue(project.getPlugins().hasPlugin("net.ltgt.errorprone"), "errorprone");
        assertTrue(project.getPlugins().hasPlugin("net.ltgt.nullaway"), "nullaway");
        assertTrue(project.getPlugins().hasPlugin("com.diffplug.spotless"), "spotless");
        assertTrue(project.getPlugins().hasPlugin("checkstyle"), "checkstyle");
        assertTrue(project.getPlugins().hasPlugin("com.github.spotbugs"), "spotbugs");
    }

    @Test
    void registersExtensionWithDefaults(@TempDir File projectDir) throws Exception {
        initGitRepository(projectDir);

        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build();
        project.getPlugins().apply(JavaConventionsPlugin.class);

        JavaConventionsExtension extension =
                project.getExtensions().findByType(JavaConventionsExtension.class);
        assertNotNull(extension, "extension registered");
        assertEquals(false, extension.getIgnoreFailures().get(), "ignoreFailures defaults to false");
        assertTrue(extension.getNullAwayAnnotatedPackages().get().isEmpty(), "no annotated packages");
    }

    @Test
    void configuresCheckstyleWithGoogleRuleset(@TempDir File projectDir) throws Exception {
        initGitRepository(projectDir);

        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build();
        project.getPlugins().apply(JavaConventionsPlugin.class);

        CheckstyleExtension checkstyle = project.getExtensions().getByType(CheckstyleExtension.class);
        assertNotNull(checkstyle.getConfig(), "checkstyle config set");
        assertEquals(
                "error",
                checkstyle.getConfigProperties().get("org.checkstyle.google.severity"),
                "google severity promoted to error");
    }

    /** Initializes a git repository with a single commit so auto-semver can infer version 0.0.1. */
    private static void initGitRepository(File dir) throws IOException, InterruptedException {
        run(dir, "git", "init", "--initial-branch=main");
        run(dir, "git", "config", "user.name", "test");
        run(dir, "git", "config", "user.email", "test@example.com");
        run(dir, "git", "config", "commit.gpgsign", "false");
        assertTrue(new File(dir, "README.md").createNewFile(), "seed file created");
        run(dir, "git", "add", ".");
        run(dir, "git", "commit", "-m", "initial");
    }

    private static void run(File dir, String... command) throws IOException, InterruptedException {
        Process process =
                new ProcessBuilder(command).directory(dir).redirectErrorStream(true).start();
        int exit = process.waitFor();
        assertEquals(0, exit, "command failed: " + String.join(" ", command));
    }
}
