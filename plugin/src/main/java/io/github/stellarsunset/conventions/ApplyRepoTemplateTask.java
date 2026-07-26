package io.github.stellarsunset.conventions;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.work.DisableCachingByDefault;

/**
 * Writes the standard repo-hygiene files that are versioned centrally in this plugin into the
 * consuming repository, so a fleet of repos can share (and update in one place) their {@code
 * .gitignore}, {@code .editorconfig}, Renovate config, shared {@code .idea} IDE settings, and a
 * starter {@code justfile}.
 *
 * <p>Files come in two flavours:
 *
 * <ul>
 *   <li><b>Managed</b> — fully owned by the plugin and rewritten on every run. Hand edits are
 *       overwritten; change them here in the plugin instead.
 *   <li><b>Seed</b> — written only when missing, then owned by the repo. Never clobbered.
 * </ul>
 *
 * <p>Run {@code ./gradlew applyRepoTemplate}. Pass {@code --preview} to print what would change
 * without touching the working tree.
 */
@DisableCachingByDefault(because = "Reconciles template files into the working tree; no cacheable outputs.")
public abstract class ApplyRepoTemplateTask extends DefaultTask {

    private enum Mode {
        /** Rewritten on every run. */
        MANAGED,
        /** Written only when absent, then left alone. */
        SEED
    }

    private record Template(String resource, String target, Mode mode) {}

    /** Resource directory (relative to this class's package) holding the template payloads. */
    private static final String RESOURCE_ROOT = "repotemplate/";

    /** Token in managed templates replaced with the plugin version at write time. */
    private static final String VERSION_TOKEN = "@pluginVersion@";

    private static final List<Template> TEMPLATES =
            List.of(
                    new Template("gitignore", ".gitignore", Mode.MANAGED),
                    new Template("gitattributes", ".gitattributes", Mode.MANAGED),
                    new Template("editorconfig", ".editorconfig", Mode.MANAGED),
                    new Template("renovate.json5", "renovate.json5", Mode.MANAGED),
                    new Template(
                            "idea/externalDependencies.xml",
                            ".idea/externalDependencies.xml",
                            Mode.MANAGED),
                    new Template(
                            "idea/google-java-format.xml", ".idea/google-java-format.xml", Mode.MANAGED),
                    new Template(
                            "idea/codeStyles/codeStyleConfig.xml",
                            ".idea/codeStyles/codeStyleConfig.xml",
                            Mode.MANAGED),
                    new Template(
                            "idea/codeStyles/Project.xml", ".idea/codeStyles/Project.xml", Mode.MANAGED),
                    new Template("justfile", "justfile", Mode.SEED));

    /** Version stamped into the managed files' "do not edit" header. */
    @Internal
    public abstract Property<String> getPluginVersion();

    /** When true, report the actions without writing anything. */
    private boolean preview;

    @Option(
            option = "preview",
            description = "Print the changes that would be made without writing any files.")
    public void setPreview(boolean preview) {
        this.preview = preview;
    }

    @Internal
    public boolean isPreview() {
        return preview;
    }

    @TaskAction
    public void applyTemplate() {
        String version = getPluginVersion().getOrElse("unspecified");
        // Templates target the repository root, wherever the plugin happens to be applied.
        Path root = getProject().getRootDir().toPath();

        for (Template template : TEMPLATES) {
            String content =
                    readResource(RESOURCE_ROOT + template.resource()).replace(VERSION_TOKEN, version);
            Path target = root.resolve(template.target());
            String action = reconcile(target, content, template.mode(), preview);
            getLogger().lifecycle("  {}  {}", action, root.relativize(target));
        }

        if (preview) {
            getLogger().lifecycle("--preview: no files were written.");
        }
    }

    private static String reconcile(Path target, String content, Mode mode, boolean preview) {
        boolean exists = Files.exists(target);
        if (exists && mode == Mode.SEED) {
            return "kept    ";
        }
        String current = exists ? readFile(target) : null;
        if (content.equals(current)) {
            return "ok      ";
        }
        if (!preview) {
            writeFile(target, content);
        }
        return exists ? "updated " : "created ";
    }

    private static String readResource(String path) {
        try (InputStream in = ApplyRepoTemplateTask.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing template resource: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read template resource " + path, e);
        }
    }

    private static String readFile(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + file, e);
        }
    }

    private static void writeFile(Path file, String content) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + file, e);
        }
    }
}
