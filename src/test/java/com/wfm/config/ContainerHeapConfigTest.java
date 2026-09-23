package com.wfm.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Structural guard: the container's heap sizing must be on the JVM command line, never left to an
 * environment variable the entrypoint does not read.
 *
 * <p><strong>What went wrong.</strong> {@code infra/ecs.tf} declared
 * {@code JAVA_OPTS=-XX:MaxRAMPercentage=75.0} on the ECS task while the image's entrypoint was
 * {@code java -jar app.jar}. {@code java} does not read {@code JAVA_OPTS} — that is a convention
 * of Spring Boot's launch script and of Tomcat's {@code catalina.sh}, not of the JVM. The setting
 * was therefore dead, the JVM fell back to the container default of {@code MaxRAMPercentage=25},
 * and the service ran a 1 GiB heap inside a 4 GiB task for months with three quarters of its
 * memory unused. Verified directly:
 *
 * <pre>
 * docker run --rm -m 4g -e JAVA_OPTS=-XX:MaxRAMPercentage=75.0 eclipse-temurin:21-jre \
 *     java -XX:+PrintFlagsFinal -version | grep MaxHeapSize
 *   MaxHeapSize = 1073741824   {ergonomic}      &lt;- 1 GiB, variable ignored
 *
 * docker run --rm -m 4g eclipse-temurin:21-jre \
 *     java -XX:MaxRAMPercentage=75.0 -XX:+PrintFlagsFinal -version | grep MaxHeapSize
 *   MaxHeapSize = 3221225472   {ergonomic}      &lt;- 3 GiB
 * </pre>
 *
 * <p>It surfaced as {@code java.lang.OutOfMemoryError: Java heap space} on the first solve large
 * enough to need the headroom — a 287-agent, 24 604-entity Vinted solve on 2026-09-23 — which is
 * the worst way to find out, because the symptom points at the solver rather than at the config.
 *
 * <p><strong>Why a test rather than a comment.</strong> The defect was invisible: every layer
 * looked correct in isolation, and nothing failed until a workload happened to need more than a
 * quarter of the memory. This asserts the flag is somewhere the JVM actually reads it, so the
 * setting cannot quietly revert to a variable that does nothing.
 *
 * <p>Reads the Dockerfile off disk — no Spring context, no container, no Docker required.
 */
class ContainerHeapConfigTest {

    private static final Path DOCKERFILE = Path.of("Dockerfile");

    /** The runtime ENTRYPOINT line, in JSON-array (exec) form. */
    private static final Pattern ENTRYPOINT = Pattern.compile("^\\s*ENTRYPOINT\\s*\\[(.+)]\\s*$");

    @Test
    @DisplayName("the entrypoint sets the heap on the command line, where the JVM reads it")
    void entrypointSetsHeapPercentage() throws IOException {
        String entrypoint = readEntrypoint();

        assertThat(entrypoint)
                .as("""
                        The ENTRYPOINT must pass a heap-sizing flag (-XX:MaxRAMPercentage or -Xmx) \
                        directly to java. Setting it via a JAVA_OPTS environment variable does \
                        NOT work with a `java -jar` entrypoint: the JVM never reads that variable, \
                        silently falls back to MaxRAMPercentage=25, and the service runs on a \
                        quarter of its container memory until some workload OOMs. \
                        Found ENTRYPOINT: %s""", entrypoint)
                .containsAnyOf("-XX:MaxRAMPercentage", "-Xmx");
    }

    @Test
    @DisplayName("the heap flag is not left to a JAVA_OPTS variable the entrypoint ignores")
    void entrypointDoesNotRelyOnJavaOptsVariable() throws IOException {
        String entrypoint = readEntrypoint();

        // A shell-form entrypoint that expands $JAVA_OPTS would be a legitimate alternative fix,
        // so this only fails the combination that is actually broken: a bare `java -jar` exec
        // entrypoint with no heap flag on it, which is what shipped.
        boolean expandsTheVariable = entrypoint.contains("JAVA_OPTS");
        boolean carriesTheFlag =
                entrypoint.contains("-XX:MaxRAMPercentage") || entrypoint.contains("-Xmx");

        assertThat(expandsTheVariable || carriesTheFlag)
                .as("""
                        ENTRYPOINT neither carries a heap flag nor expands $JAVA_OPTS, so whatever \
                        the ECS task definition declares for JAVA_OPTS is dead config. \
                        Found ENTRYPOINT: %s""", entrypoint)
                .isTrue();
    }

    private static String readEntrypoint() throws IOException {
        List<String> lines = Files.readAllLines(DOCKERFILE, StandardCharsets.UTF_8);
        String found = null;
        for (String line : lines) {
            Matcher m = ENTRYPOINT.matcher(line);
            if (m.matches()) {
                found = m.group(1);
            }
        }
        // Guards the guard: a moved or renamed Dockerfile must fail loudly, not vacuously pass.
        assertThat(found)
                .as("no ENTRYPOINT [...] line found in %s — this guard cannot do its job",
                        DOCKERFILE.toAbsolutePath())
                .isNotNull();
        return found;
    }
}
