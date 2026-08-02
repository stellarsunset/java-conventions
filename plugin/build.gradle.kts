plugins {
    id("com.gradle.plugin-publish") version "2.1.0"
    jacoco
    alias(libs.plugins.auto.semver)
    alias(libs.plugins.auto.publish)
}

description = "Opinionated Gradle conventions bundling common Java linting and quality tools."

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.errorprone.plugin)
    implementation(libs.nullaway.plugin)
    implementation(libs.spotless.plugin)
    implementation(libs.spotbugs.plugin)
    implementation(libs.auto.semver)
    implementation(libs.auto.publish)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val toolVersions = mapOf(
    "errorProneCoreVersion" to libs.versions.errorprone.core.get(),
    "nullawayVersion" to libs.versions.nullaway.core.get(),
    "googleJavaFormatVersion" to libs.versions.google.java.format.get(),
    "checkstyleVersion" to libs.versions.checkstyle.get(),
    "pluginVersion" to project.version.toString(),
)

tasks.processResources {
    inputs.properties(toolVersions)
    filesMatching("**/versions.properties") {
        expand(toolVersions)
    }
}

gradlePlugin {
    website.set("https://github.com/stellarsunset/java-conventions")
    vcsUrl.set("https://github.com/stellarsunset/java-conventions")
    val javaConventions by plugins.creating {
        id = "io.github.stellarsunset.java-conventions"
        implementationClass = "io.github.stellarsunset.conventions.JavaConventionsPlugin"
        displayName = "Java conventions plugin"
        description = "Opinionated Gradle conventions bundling ErrorProne, NullAway, Spotless " +
                "(google-java-format), Checkstyle (google_checks), SpotBugs, JaCoCo, auto-semver, and auto-publish."
        tags.set(listOf("java", "conventions", "linting", "checkstyle", "spotless", "errorprone", "spotbugs"))
    }
}

val functionalTestSourceSet = sourceSets.create("functionalTest") {
}

configurations["functionalTestImplementation"].extendsFrom(configurations["testImplementation"])
configurations["functionalTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

val functionalTest by tasks.registering(Test::class) {
    testClassesDirs = functionalTestSourceSet.output.classesDirs
    classpath = functionalTestSourceSet.runtimeClasspath
    useJUnitPlatform()
}

gradlePlugin.testSourceSets.add(functionalTestSourceSet)

tasks.test {
    useJUnitPlatform()
}

// Coverage for the functionalTest source set. Its build runs in-process (GradleRunner debug mode)
// so the functionalTest JVM's JaCoCo agent captures the plugin code exercised by real builds.
val functionalTestReport by tasks.registering(JacocoReport::class) {
    dependsOn(functionalTest)
    executionData(functionalTest.get())
    sourceSets(sourceSets["main"])
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.named<Task>("check") {
    dependsOn(functionalTest)
    finalizedBy(tasks.jacocoTestReport, functionalTestReport)
}

tasks.jacocoTestReport {
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.javadoc {
    options.outputLevel = JavadocOutputLevel.QUIET
}
