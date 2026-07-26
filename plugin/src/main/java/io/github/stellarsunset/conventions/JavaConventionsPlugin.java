package io.github.stellarsunset.conventions;

import com.diffplug.gradle.spotless.SpotlessExtension;
import com.github.spotbugs.snom.Confidence;
import com.github.spotbugs.snom.Effort;
import com.github.spotbugs.snom.SpotBugsExtension;
import com.github.spotbugs.snom.SpotBugsTask;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Properties;
import net.ltgt.gradle.errorprone.CheckSeverity;
import net.ltgt.gradle.errorprone.ErrorProneOptions;
import net.ltgt.gradle.nullaway.NullAwayExtension;
import net.ltgt.gradle.nullaway.NullAwayOptions;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.Provider;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.plugins.quality.CheckstyleExtension;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.testing.jacoco.tasks.JacocoReport;

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
 *   <li><a href="https://spotbugs.github.io">SpotBugs</a> bytecode analysis
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

        // Bundled companion plugin: automatic semantic versioning from annotated git tags.
        project.getPluginManager().apply("io.github.stellarsunset.auto-semver");

        configureErrorProneAndNullAway(project, extension);
        configureSpotless(project);
        configureCheckstyle(project, extension);
        configureSpotBugs(project, extension);
        configureJacoco(project);
    }

    private void configureErrorProneAndNullAway(Project project, JavaConventionsExtension extension) {
        project.getPluginManager().apply("net.ltgt.errorprone");
        project.getPluginManager().apply("net.ltgt.nullaway");

        project.getDependencies()
                .add("errorprone", "com.google.errorprone:error_prone_core:" + versions.errorProneCore());
        project.getDependencies().add("errorprone", "com.uber.nullaway:nullaway:" + versions.nullaway());

        // Packages NullAway should enforce null-safety within, sourced from the extension.
        NullAwayExtension nullAway = project.getExtensions().getByType(NullAwayExtension.class);
        nullAway.getAnnotatedPackages().set(extension.getNullAwayAnnotatedPackages());

        // NullAway runs as an error only once packages are opted in; otherwise it is switched fully
        // OFF. This severity MUST be set through the nullaway DSL rather than errorprone's `checks`
        // map: the nullaway plugin emits its own `-Xep:NullAway` argument whenever severity is not
        // OFF, and an enabled-but-unconfigured NullAway (no AnnotatedPackages, no OnlyNullMarked)
        // crashes the compiler. Setting OFF here makes the plugin skip the check entirely, so it is
        // never instantiated and the plugin stays safe to apply to projects that have not opted in.
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
        // google_checks.xml ships inside the checkstyle jar; pull it straight from the resolved
        // artifact so the ruleset always matches the tool version. The `checkstyle` configuration
        // resolves to the tool jar plus all its transitive dependencies, so narrow it to the single
        // checkstyle jar — fromArchiveEntry requires exactly one archive.
        FileCollection checkstyleJar =
                project.getConfigurations()
                        .getByName("checkstyle")
                        .filter(file -> file.getName().startsWith("checkstyle-"));
        checkstyle.setConfig(
                project.getResources().getText().fromArchiveEntry(checkstyleJar, "google_checks.xml"));
        // The Google ruleset reports at "warning" severity by default, which never fails a build.
        // Promote to "error" so violations are enforced (respecting ignoreFailures below).
        checkstyle.getConfigProperties().put("org.checkstyle.google.severity", "error");

        // google_checks requires a Javadoc comment on every public/protected type and method
        // (MissingJavadocType / MissingJavadocMethod), which is heavier than we want. Point the
        // ruleset's built-in SuppressionFilter at our bundled checkstyle-suppressions.xml, which
        // turns just those two checks off; every other check (including Javadoc *formatting*) stays
        // enforced. Checkstyle resolves the property as a file/URL, so the resource URL works
        // straight out of the plugin jar.
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

        project.getTasks()
                .withType(SpotBugsTask.class)
                .configureEach(
                        task -> {
                            task.getReports().maybeCreate("html").getRequired().set(true);
                            task.getReports().maybeCreate("xml").getRequired().set(true);
                        });
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

    /** Tool runtime versions injected at build time from the version catalog. */
    private record ToolVersions(
            String errorProneCore, String nullaway, String googleJavaFormat, String checkstyle) {

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
                    props.getProperty("checkstyle"));
        }
    }
}
