# AGENTS.md

Gradle plugin project — single subproject (`:plugin`). All source and build logic lives under `plugin/`. No build script at the root, only `settings.gradle`.

## Commands

Always use the Gradle wrapper from the repo root.

```bash
./gradlew :plugin:build          # compile + unit tests + functional tests
./gradlew :plugin:check          # same as build minus jar/publish steps
./gradlew :plugin:test           # unit tests only (fast, no subprocess)
./gradlew :plugin:functionalTest # functional tests only (launches real Gradle builds)
```

Run a single unit test:
```bash
./gradlew :plugin:test --tests "com.stehno.gradle.depchecker.CheckDependenciesTaskTest.<method name>"
```

Run a single Spock functional test:
```bash
./gradlew :plugin:functionalTest --tests "com.stehno.gradle.depchecker.CheckAvailabilityTaskSpec.<feature name>"
```

Publish to local Maven repo (under `plugin/build/local-repo/`):
```bash
./gradlew :plugin:publishAllPublicationsToLocalRepoRepository
```

## Test source sets

| Source set | Location | Framework | Speed |
|---|---|---|---|
| `test` | `plugin/src/test/groovy/` | JUnit Jupiter 6 + `ProjectBuilder` | Fast, in-process |
| `functionalTest` | `plugin/src/functionalTest/groovy/` | Spock + `GradleRunner` | Slow, real subprocess |

`check` depends on both. `functionalTest` classpath extends from `test` classpath.

## Functional test prerequisite

`CheckAvailabilityTaskSpec` starts a MockServer on **port 1080**. If port 1080 is occupied, functional tests fail. No other external services needed.

## Toolchain

- **Gradle 8.14.4** — pinned in `gradle/wrapper/gradle-wrapper.properties`
- **Java 17** source and target compatibility
- **Groovy** — all plugin source and all tests

## Architecture note (config-cache compliance)

Tasks must never access the `Project` object at execution time. Dependency data is captured at configuration time into typed `Property` providers (`configDeps: MapProperty<String, String>`, `dependencyCoordinates: ListProperty<String>`) which are then read inside task actions. Do not break this pattern when adding features.

## Testing quirks

- `CheckDependenciesTaskTest` uses `TestResultListener` as a test hook via its static accessor methods (`clear()`, `hasDuplicates()`, `duplicatesFor()`); the state is cleared in `@BeforeEach`. Do not remove the `clear()` call.
- One unit test is `@Disabled` with a `// FIXME` comment (`'check depends on checkDependencies'`) — this is intentional and expected.
- `resultListenerClass` on `CheckDependenciesTask` is an `@Internal` test-only hook typed as `Class<? extends ResultListener>`; in tests, Groovy coerces a string literal to the class via `Class.forName()`, so you can assign either a class reference or a fully-qualified class name string.

## Agent tool permissions

`.claude/settings.local.json` pre-approves only `./gradlew :plugin:test *`. Running `./gradlew :plugin:functionalTest` will require explicit user approval.
