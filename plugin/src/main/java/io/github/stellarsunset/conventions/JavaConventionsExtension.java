package io.github.stellarsunset.conventions;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * Configuration for the {@link JavaConventionsPlugin}.
 *
 * <p>The plugin is intentionally opinionated, so there is very little to configure. The two knobs
 * that matter in practice are the set of packages NullAway should treat as {@code @NonNull} by
 * default, and whether static-analysis findings should fail the build.
 *
 * <pre>{@code
 * javaConventions {
 *     nullAwayAnnotatedPackages.add("io.github.stellarsunset")
 *     ignoreFailures.set(false)
 * }
 * }</pre>
 */
public abstract class JavaConventionsExtension {

    public static final String NAME = "javaConventions";

    /**
     * Packages NullAway should treat as annotated (i.e. assume {@code @NonNull} by default and
     * enforce null-safety within). NullAway only runs when this list is non-empty; otherwise the
     * check is disabled so the plugin is safe to apply to projects that have not opted in yet.
     */
    public abstract ListProperty<String> getNullAwayAnnotatedPackages();

    /**
     * When {@code true}, Checkstyle and SpotBugs report findings without failing the build. Defaults
     * to {@code false} so violations fail {@code check}. Note that ErrorProne and Spotless are
     * enforced by the compiler / {@code spotlessCheck} respectively and are not affected by this
     * flag.
     */
    public abstract Property<Boolean> getIgnoreFailures();
}
