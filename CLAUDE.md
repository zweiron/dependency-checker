# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build the plugin
./gradlew build

# Run unit tests only
./gradlew :plugin:test

# Run a single test class
./gradlew :plugin:test --tests "com.stehno.gradle.depchecker.CheckDependenciesTaskTest"

# Run a single test method (use the method name string)
./gradlew :plugin:test --tests "com.stehno.gradle.depchecker.CheckDependenciesTaskTest.checkDependencies: no dependencies"

# Clean build outputs
./gradlew clean
```

The functional tests (`plugin/src/functionalTest/`) are wired into the `check` lifecycle task and run via `./gradlew :plugin:functionalTest`. They use Spock + GradleRunner + MockServer and require a network connection (they resolve real Maven artifacts).

## Project Structure

This is a multi-project Gradle build. The root `settings.gradle` includes a single subproject:

```
plugin/          ← all plugin source lives here
  build.gradle
  src/
    main/groovy/com/stehno/gradle/depchecker/
    test/groovy/com/stehno/gradle/depchecker/
    functionalTest/groovy/com/stehno/gradle/depchecker/
```

The plugin is registered via `plugin/src/main/resources/META-INF/gradle-plugins/com.stehno.gradle.dependency-checker.properties`.

## Architecture

This is a Gradle plugin (`com.stehno.gradle.dependency-checker`) written in Groovy targeting Java 17. **Plugin entry point:** `DependencyCheckerPlugin` registers two tasks on the target project: `checkDependencies` and `checkAvailability`.

### Tasks

**`CheckDependenciesTask`** — detects duplicate dependencies (same `group:name`, different version) within each Gradle configuration. Iterates configurations, accumulates `group:name` keys with a `Set`, and reports any key seen twice in the same config. Key inputs: `configurations` (limit which configs to scan), `ignored` (coordinates to skip as `group:name` strings), `resultListenerClass` (hook for test assertions).

**`CheckAvailabilityTask`** — resolves all transitive dependencies recursively and checks each against remote Maven repositories via HTTP HEAD requests. `repoUrls` can be set in the build script or overridden at the CLI with `-PrepoUrls=url1,url2`. Uses `HttpHeadClient` (inner class in the same file) for the HTTP calls. `DependencyCoordinate` (`@Immutable`) represents a coordinate and builds the Maven repo path via `toPathSuffix()`. The CLI property override is read via injected `ProviderFactory.gradleProperty()`.

Both tasks call `notCompatibleWithConfigurationCache()` because they access `project.configurations` at execution time. A full config-cache fix would require capturing dependency data at configuration time using Gradle's lazy property API.

### Supporting types

- `DependencyCheckResults` — accumulates duplicate findings keyed by configuration name; uses `putAt` to support `results[cname] = key` Groovy syntax.
- `ResultListener` / `NoOpResultListener` — callback interface used by `CheckDependenciesTask` for test instrumentation; default is no-op.
- `TestResultListener` (test source) — static-state listener used in unit tests to assert on duplicates found; state is cleared in `@BeforeEach`.

### Test approach

Unit tests use JUnit 5 + Gradle's `ProjectBuilder` to construct an in-memory `Project`, apply the plugin, configure dependencies, and call `task.checkDependencies()` directly — no forked Gradle process. Each test creates its own subdirectory under the `@TempDir` to avoid project directory collisions. Assertions go through `TestResultListener`, which stores results in a static map.