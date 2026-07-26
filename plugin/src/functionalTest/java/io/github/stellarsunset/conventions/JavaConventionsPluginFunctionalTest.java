package io.github.stellarsunset.conventions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Functional tests that apply the published plugin to a throwaway project and drive real Gradle
 * builds against it. These resolve the underlying tool artifacts (google-java-format, checkstyle,
 * error_prone_core, spotbugs), so they require network access.
 */
class JavaConventionsPluginFunctionalTest {

    @Test
    void registersToolTasks(@TempDir File projectDir) throws Exception {
        writeProject(projectDir, /* ignoreFailures= */ true);

        BuildResult result = runner(projectDir, "tasks", "--all").build();

        String output = result.getOutput();
        assertTrue(output.contains("spotlessCheck"), "spotless task present");
        assertTrue(output.contains("checkstyleMain"), "checkstyle task present");
        assertTrue(output.contains("spotbugsMain"), "spotbugs task present");
        assertTrue(output.contains("release"), "auto-semver release task present");
    }

    @Test
    void checkPassesOnFormattedProject(@TempDir File projectDir) throws Exception {
        writeProject(projectDir, /* ignoreFailures= */ true);

        // Let Spotless format the source, then a full check should succeed end-to-end.
        runner(projectDir, "spotlessApply").build();
        BuildResult result = runner(projectDir, "check").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":spotlessCheck").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":checkstyleMain").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":spotbugsMain").getOutcome());
    }

    @Test
    void spotbugsIgnoresMutableRecordComponents(@TempDir File projectDir) throws Exception {
        // ignoreFailures = false, so any surviving SpotBugs finding *would* fail the build.
        writeProject(projectDir, /* ignoreFailures= */ false);

        // A record whose sole component is a mutable List: without the bundled exclude filter this
        // trips EI_EXPOSE_REP (accessor hands out the internal list) and EI_EXPOSE_REP2 (canonical
        // constructor stores the caller's reference), which at MAX effort / MEDIUM confidence fail
        // spotbugsMain. The exclude filter drops both, so the task should pass.
        File source = new File(projectDir, "src/main/java/com/example/Sample.java");
        write(
                source,
                "package com.example;\n"
                        + "import java.util.List;\n"
                        + "public record Sample(List<String> items) {}\n");

        BuildResult result = runner(projectDir, "spotbugsMain").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":spotbugsMain").getOutcome());
    }

    @Test
    void spotlessCheckFailsOnUnformattedProject(@TempDir File projectDir) throws Exception {
        writeProject(projectDir, /* ignoreFailures= */ true);

        BuildResult result = runner(projectDir, "spotlessCheck").buildAndFail();

        // spotlessCheck is an aggregate task; the per-format spotlessJavaCheck is what actually
        // fails, and the aggregate never executes once its dependency fails.
        assertEquals(TaskOutcome.FAILED, result.task(":spotlessJavaCheck").getOutcome());
    }

    @Test
    void checkstyleAllowsUndocumentedPublicApi(@TempDir File projectDir) throws Exception {
        // ignoreFailures = false, so any checkstyle violation *would* fail the build.
        writeProject(projectDir, /* ignoreFailures= */ false);

        // Format first, so the only thing google_checks could object to on the sample is the missing
        // Javadoc on its public type and method — which the plugin suppresses.
        runner(projectDir, "spotlessApply").build();
        BuildResult result = runner(projectDir, "checkstyleMain").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":checkstyleMain").getOutcome());
    }

    @Test
    void applyRepoTemplateWritesManagedAndSeedFiles(@TempDir File projectDir) throws Exception {
        writeProject(projectDir, /* ignoreFailures= */ true);

        BuildResult result = runner(projectDir, "applyRepoTemplate").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":applyRepoTemplate").getOutcome());
        assertTrue(new File(projectDir, ".gitignore").isFile(), ".gitignore written");
        assertTrue(new File(projectDir, ".editorconfig").isFile(), ".editorconfig written");
        assertTrue(new File(projectDir, "renovate.json5").isFile(), "renovate.json5 written");
        assertTrue(
                new File(projectDir, ".idea/externalDependencies.xml").isFile(),
                ".idea/externalDependencies.xml written");
        assertTrue(
                new File(projectDir, ".idea/codeStyles/Project.xml").isFile(),
                ".idea/codeStyles/Project.xml written");
        assertTrue(new File(projectDir, "justfile").isFile(), "seed justfile written");

        // The managed header is stamped and the version token is resolved.
        String editorconfig = Files.readString(new File(projectDir, ".editorconfig").toPath());
        assertTrue(
                editorconfig.contains("Managed by io.github.stellarsunset.java-conventions"),
                "managed header present");
        assertTrue(!editorconfig.contains("@pluginVersion@"), "version token replaced");
    }

    @Test
    void applyRepoTemplatePreviewWritesNothingAndSeedIsPreserved(@TempDir File projectDir)
            throws Exception {
        writeProject(projectDir, /* ignoreFailures= */ true);

        // A seed file that already exists must never be clobbered.
        File justfile = new File(projectDir, "justfile");
        Files.writeString(justfile.toPath(), "# custom\n");

        // --preview reports actions but writes nothing.
        runner(projectDir, "applyRepoTemplate", "--preview").build();
        assertTrue(!new File(projectDir, ".gitignore").exists(), "--preview wrote no managed file");

        // A real run creates managed files but leaves the existing seed file untouched.
        runner(projectDir, "applyRepoTemplate").build();
        assertTrue(new File(projectDir, ".gitignore").isFile(), "managed file created on real run");
        assertEquals("# custom\n", Files.readString(justfile.toPath()), "seed justfile preserved");
    }

    private GradleRunner runner(File projectDir, String... arguments) {
        return GradleRunner.create()
                .forwardOutput()
                .withPluginClasspath()
                // Run the build in-process so the functionalTest JVM's JaCoCo agent instruments the
                // plugin code under test; TestKit otherwise forks a separate daemon and coverage is
                // lost.
                .withDebug(true)
                .withProjectDir(projectDir)
                .withArguments(arguments);
    }

    private void writeProject(File projectDir, boolean ignoreFailures) throws Exception {
        write(
                new File(projectDir, "settings.gradle"),
                "rootProject.name = 'sample'\n");
        write(
                new File(projectDir, "build.gradle"),
                """
                plugins {
                  id('io.github.stellarsunset.java-conventions')
                }

                repositories {
                  mavenCentral()
                  gradlePluginPortal()
                }

                javaConventions {
                  ignoreFailures.set(%s)
                }
                """
                        .formatted(ignoreFailures));

        // Deliberately messy formatting so spotlessCheck fails until spotlessApply is run.
        File source =
                new File(projectDir, "src/main/java/com/example/Sample.java");
        assertTrue(source.getParentFile().mkdirs(), "source dirs created");
        write(
                source,
                "package com.example;\n"
                        + "public final class Sample {\n"
                        + "public static int add(int a,int b){return a+b;}\n"
                        + "}\n");

        initGitRepository(projectDir);
    }

    private static void write(File file, String content) throws IOException {
        Files.writeString(file.toPath(), content);
    }

    private static void initGitRepository(File dir) throws IOException, InterruptedException {
        run(dir, "git", "init", "--initial-branch=main");
        run(dir, "git", "config", "user.name", "test");
        run(dir, "git", "config", "user.email", "test@example.com");
        run(dir, "git", "config", "commit.gpgsign", "false");
        run(dir, "git", "add", ".");
        run(dir, "git", "commit", "-m", "initial");
    }

    private static void run(File dir, String... command) throws IOException, InterruptedException {
        Process process =
                new ProcessBuilder(command).directory(dir).redirectErrorStream(true).start();
        assertEquals(0, process.waitFor(), "command failed: " + String.join(" ", command));
    }
}
