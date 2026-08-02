package io.github.stellarsunset.conventions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * Materializes the plugin's bundled SpotBugs exclude filter to a real file so {@code
 * spotbugs.excludeFilter} (a {@code RegularFileProperty}, which won't accept a jar-resource URL) can
 * consume it.
 *
 * <p>This exists as a task rather than a configuration-time file write so the output is a declared
 * {@code @OutputFile}: SpotBugs wires it in as an input and therefore depends on this task, the file
 * is recreated after {@code clean}, and the whole thing survives the configuration cache (which
 * skips the configuration phase on warm runs, so any config-time write would never happen).
 */
@CacheableTask
public abstract class SpotBugsExcludeFilterTask extends DefaultTask {

    /**
     * The filter XML, read from the plugin's classpath resource at configuration time and passed in
     * as an input so a plugin upgrade that changes the filter regenerates the file.
     */
    @Input
    public abstract Property<String> getContent();

    /** Where the filter is written; consumed as {@code spotbugs.excludeFilter}. */
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void write() {
        Path target = getOutputFile().get().getAsFile().toPath();
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(target, getContent().get(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write SpotBugs exclude filter to " + target, e);
        }
    }
}
