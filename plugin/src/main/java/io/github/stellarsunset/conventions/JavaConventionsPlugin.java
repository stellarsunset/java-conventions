package io.github.stellarsunset.conventions;

import com.diffplug.gradle.spotless.SpotlessExtension;
import com.github.spotbugs.snom.Confidence;
import com.github.spotbugs.snom.Effort;
import com.github.spotbugs.snom.SpotBugsExtension;
import com.github.spotbugs.snom.SpotBugsTask;
import net.ltgt.gradle.errorprone.CheckSeverity;
import net.ltgt.gradle.errorprone.ErrorProneOptions;
import net.ltgt.gradle.nullaway.NullAwayExtension;
import net.ltgt.gradle.nullaway.NullAwayOptions;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.plugins.quality.CheckstyleExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.testing.jacoco.tasks.JacocoReport;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Opinionated Gradle conventions for the author's open-source Java repositories.
 *
 * <p>Applying this single plugin wires up a consistent linting, formatting, static-analysis,
 * coverage, and versioning stack across projects following Google conventions where possible:
 *
 * <ul>
 *   <li>{@code java} + {@code jacoco} base plugins
 *   <li><a href="https://errorprone.info">ErrorProne</a> compile-time bug detection
 *   <li><a href="https://github.com/uber/NullAway">NullAway</a> null-safety (opt-in per package)
 *   <li><a href="https://github.com/diffplug/spotless">Spotless</a> with google-java-format
 *   <li>Checkstyle using Google's bundled {@code google_checks.xml}
 *   <li><a href="https://spotbugs.github.io">SpotBugs</a> bytecode analysis (with {@code
 *       EI_EXPOSE_REP}/{@code EI_EXPOSE_REP2} excluded for records/DTOs)
 *   <li><a href="https://github.com/stellarsunset/auto-semver">auto-semver</a> git-tag versioning
 * </ul>
 */
public class JavaConventionsPlugin implements Plugin<Project> {

    private final ToolVersions versions = ToolVersions.load();

    @Override
    public void apply(Project project) {
        JavaConventionsExtension extension =
                project.getExtensions().create(JavaConventionsExtension.NAME, JavaConventionsExtension.class);
        extension.getIgnoreFailures().convention(false);

        // Base plugins.
        project.getPluginManager().apply("java");
        project.getPluginManager().apply("jacoco");

        project.getPluginManager().apply("io.github.stellarsunset.auto-semver");
        project.getPluginManager().apply("io.github.stellarsunset.auto-publish");

        configureErrorProneAndNullAway(project, extension);
        configureSpotless(project);
        configureCheckstyle(project, extension);
        configureSpotBugs(project, extension);
        configureJacoco(project);
        configureRepoTemplate(project);
    }

    private void configureRepoTemplate(Project project) {
        project.getTasks()
                .register(
                        "applyRepoTemplate",
                        ApplyRepoTemplateTask.class,
                        task -> {
                            task.setGroup("conventions");
                            task.setDescription(
                                    "Write the standard java-conventions repo files (.gitignore, .editorconfig, "
                                            + "renovate.json5, shared .idea settings, justfile).");
                            task.getPluginVersion().set(versions.pluginVersion());
                        });
    }

    private void configureErrorProneAndNullAway(Project project, JavaConventionsExtension extension) {
        project.getPluginManager().apply("net.ltgt.errorprone");
        project.getPluginManager().apply("net.ltgt.nullaway");

        project.getDependencies()
                .add("errorprone", "com.google.errorprone:error_prone_core:" + versions.errorProneCore());
        project.getDependencies().add("errorprone", "com.uber.nullaway:nullaway:" + versions.nullaway());

        NullAwayExtension nullAway = project.getExtensions().getByType(NullAwayExtension.class);
        nullAway.getAnnotatedPackages().set(extension.getNullAwayAnnotatedPackages());

        Provider<CheckSeverity> nullAwaySeverity =
                extension
                        .getNullAwayAnnotatedPackages()
                        .map(packages -> packages.isEmpty() ? CheckSeverity.OFF : CheckSeverity.ERROR);

        project.getTasks()
                .withType(JavaCompile.class)
                .configureEach(
                        task -> {
                            ErrorProneOptions errorProne =
                                    ((ExtensionAware) task.getOptions())
                                            .getExtensions()
                                            .getByType(ErrorProneOptions.class);
                            errorProne.getDisableWarningsInGeneratedCode().set(true);

                            NullAwayOptions nullAwayOptions =
                                    ((ExtensionAware) errorProne)
                                            .getExtensions()
                                            .getByType(NullAwayOptions.class);
                            nullAwayOptions.getSeverity().set(nullAwaySeverity);
                        });
    }

    private void configureSpotless(Project project) {
        project.getPluginManager().apply("com.diffplug.spotless");

        SpotlessExtension spotless = project.getExtensions().getByType(SpotlessExtension.class);
        spotless.java(
                java -> {
                    java.googleJavaFormat(versions.googleJavaFormat());
                    java.target("src/**/*.java");
                    java.removeUnusedImports();
                    java.trimTrailingWhitespace();
                    java.endWithNewline();
                });
    }

    private void configureCheckstyle(Project project, JavaConventionsExtension extension) {
        project.getPluginManager().apply("checkstyle");

        CheckstyleExtension checkstyle = project.getExtensions().getByType(CheckstyleExtension.class);
        checkstyle.setToolVersion(versions.checkstyle());

        FileCollection checkstyleJar =
                project.getConfigurations()
                        .getByName("checkstyle")
                        .filter(file -> file.getName().startsWith("checkstyle-"));
        checkstyle.setConfig(
                project.getResources().getText().fromArchiveEntry(checkstyleJar, "google_checks.xml"));

        checkstyle.getConfigProperties().put("org.checkstyle.google.severity", "error");

        URL suppressions = JavaConventionsPlugin.class.getResource("checkstyle-suppressions.xml");
        if (suppressions == null) {
            throw new IllegalStateException("checkstyle-suppressions.xml not found on classpath");
        }
        checkstyle
                .getConfigProperties()
                .put("org.checkstyle.google.suppressionfilter.config", suppressions.toString());

        project.getTasks()
                .withType(Checkstyle.class)
                .configureEach(task -> task.setIgnoreFailures(extension.getIgnoreFailures().getOrElse(false)));
    }

    private void configureSpotBugs(Project project, JavaConventionsExtension extension) {
        project.getPluginManager().apply("com.github.spotbugs");

        SpotBugsExtension spotbugs = project.getExtensions().getByType(SpotBugsExtension.class);
        spotbugs.getEffort().set(Effort.MAX);
        spotbugs.getReportLevel().set(Confidence.MEDIUM);
        spotbugs.getIgnoreFailures().set(extension.getIgnoreFailures());

        Provider<String> annotations =
                spotbugs.getToolVersion().map(version -> "com.github.spotbugs:spotbugs-annotations:" + version);
        project.getDependencies().addProvider("compileOnly", annotations);
        project.getDependencies().addProvider("testCompileOnly", annotations);

        String excludeFilterXml = readResource("spotbugs-exclude.xml");
        TaskProvider<SpotBugsExcludeFilterTask> excludeFilter =
                project.getTasks()
                        .register(
                                "spotbugsExcludeFilter",
                                SpotBugsExcludeFilterTask.class,
                                task -> {
                                    task.getContent().set(excludeFilterXml);
                                    task.getOutputFile()
                                            .set(
                                                    project.getLayout()
                                                            .getBuildDirectory()
                                                            .file("spotbugs/exclude-filter.xml"));
                                });
        spotbugs.getExcludeFilter().set(excludeFilter.flatMap(SpotBugsExcludeFilterTask::getOutputFile));

        project.getTasks()
                .withType(SpotBugsTask.class)
                .configureEach(
                        task -> {
                            task.getReports().maybeCreate("html").getRequired().set(true);
                            task.getReports().maybeCreate("xml").getRequired().set(true);
                        });
    }

    /**
     * Read a bundled classpath resource (relative to this class's package) as UTF-8 text.
     */
    private static String readResource(String resourceName) {
        try (InputStream in = JavaConventionsPlugin.class.getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IllegalStateException(resourceName + " not found on classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + resourceName, e);
        }
    }

    private void configureJacoco(Project project) {
        project.getTasks()
                .withType(JacocoReport.class)
                .configureEach(
                        report -> {
                            report.getReports().getXml().getRequired().set(true);
                            report.getReports().getHtml().getRequired().set(true);
                        });
        project.getTasks().named("test").configure(test -> test.finalizedBy("jacocoTestReport"));
    }

    /**
     * Tool runtime versions (and the plugin's own version) injected at build time.
     */
    private record ToolVersions(
            String errorProneCore,
            String nullaway,
            String googleJavaFormat,
            String checkstyle,
            String pluginVersion) {

        static ToolVersions load() {
            Properties props = new Properties();
            try (InputStream in =
                         JavaConventionsPlugin.class.getResourceAsStream("versions.properties")) {
                if (in == null) {
                    throw new IllegalStateException("versions.properties not found on classpath");
                }
                props.load(in);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to load tool versions", e);
            }
            return new ToolVersions(
                    props.getProperty("errorProneCore"),
                    props.getProperty("nullaway"),
                    props.getProperty("googleJavaFormat"),
                    props.getProperty("checkstyle"),
                    props.getProperty("pluginVersion"));
        }
    }
}
