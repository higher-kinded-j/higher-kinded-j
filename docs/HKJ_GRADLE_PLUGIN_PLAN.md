# HKJ Gradle Plugin: Implementation Plan and Task List

> Based on the analysis in [HKJ_GRADLE_PLUGIN_ANALYSIS.md](HKJ_GRADLE_PLUGIN_ANALYSIS.md).
> Recommended approach: **Custom Javac Plugin (`hkj-checker`) + Gradle Plugin (`hkj-gradle-plugin`)**.

---

## Overview

This plan covers two new modules, their testing strategies, hkj-book documentation, and integration with the existing build. All code must follow the project's established conventions:

- **Java 25** with `--enable-preview`
- **Google Java Style Guide** (enforced by Spotless)
- **British English** in all documentation and comments
- **JUnit 6**, **AssertJ**, and **JQwik** for testing
- **No emojis** in code or documentation
- **Admonishment-based** documentation style per `docs/STYLE-GUIDE.md`
- **Tutorial format** per `docs/TUTORIAL-STYLE-GUIDE.md`
- **Performance testing** per `docs/PERFORMANCE-TESTING-GUIDE.md`

---

## Phase 1: Javac Plugin (`hkj-checker`)

### 1.1 Module Setup

**New module:** `hkj-checker`

**Add to `settings.gradle.kts`:**
```kotlin
include(
    // ... existing modules ...
    "hkj-checker"
)
```

**`hkj-checker/build.gradle.kts`:**
```kotlin
plugins {
    `java-library`
    alias(libs.plugins.maven.publish)
}

dependencies {
    // No external dependencies; uses only JDK-provided com.sun.source.* APIs
    // Test dependencies
    testImplementation(libs.junit.bom)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.compile.testing)  // Google compile-testing for javac plugin tests
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf(
        "--add-exports", "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
        "--add-exports", "jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
        "--add-exports", "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED"
    ))
}
```

**Key decision:** The `hkj-checker` module has **zero external runtime dependencies**. It uses only `com.sun.source.*` (exported from `jdk.compiler`) and, where necessary for deep type resolution, `com.sun.tools.javac.code.Type` (internal, accessed via `--add-exports`).

### 1.2 Source Structure

```
hkj-checker/
├── build.gradle.kts
├── src/main/java/
│   ├── module-info.java
│   └── org/higherkindedj/checker/
│       ├── HKJCheckerPlugin.java           # com.sun.source.util.Plugin entry point
│       ├── PathTypeMismatchChecker.java     # TreeScanner visiting method invocations
│       ├── PathTypeRegistry.java            # Maps concrete Path types and their relationships
│       └── DiagnosticMessages.java          # Centralised error message formatting
├── src/main/resources/
│   └── META-INF/services/
│       └── com.sun.source.util.Plugin       # Contains: org.higherkindedj.checker.HKJCheckerPlugin
└── src/test/java/
    └── org/higherkindedj/checker/
        ├── HKJCheckerPluginTest.java        # Plugin registration and lifecycle tests
        ├── PathTypeMismatchCheckerTest.java  # Core detection tests using compile-testing
        ├── PathTypeRegistryTest.java         # Registry correctness tests
        └── testdata/                         # Compilable Java source files for testing
            ├── MismatchInVia.java
            ├── MismatchInZipWith.java
            ├── MismatchInThen.java
            ├── MismatchInRecoverWith.java
            ├── MismatchInOrElse.java
            ├── CorrectUsage.java
            └── GenericPathUsage.java
```

### 1.3 Implementation Tasks

#### Task 1.3.1: `module-info.java`

```java
module org.higherkindedj.checker {
    requires jdk.compiler;
    provides com.sun.source.util.Plugin
        with org.higherkindedj.checker.HKJCheckerPlugin;
}
```

#### Task 1.3.2: `HKJCheckerPlugin.java`

- Implement `com.sun.source.util.Plugin`
- `getName()` returns `"HKJChecker"`
- `init(JavacTask, String...)`:
  - Parse optional arguments (e.g., `-Ahkj.checks.pathMismatch=true`)
  - Register a `TaskListener` that fires on `TaskEvent.Kind.ANALYZE`
  - On each `ANALYZE` event, create and run `PathTypeMismatchChecker` over the compilation unit

#### Task 1.3.3: `PathTypeRegistry.java`

- Static registry of all 24 concrete Path types and their fully qualified names
- Method: `isPathType(TypeMirror)` — returns true if the type is a concrete Path
- Method: `getPathCategory(TypeMirror)` — returns the category (e.g., "MaybePath", "EitherPath") for error messages
- Method: `areSamePathFamily(TypeMirror, TypeMirror)` — checks if two types belong to the same Path family
- Method: `suggestedConversion(String fromType, String toType)` — returns the conversion method name (e.g., `toEitherPath()`)

**Concrete Path types to register (24):**

| Path Type | Capability Level | Error Type |
|-----------|-----------------|------------|
| `MaybePath` | Recoverable | Unit |
| `EitherPath` | Recoverable | E (generic) |
| `TryPath` | Recoverable | Throwable |
| `IOPath` | Effectful | — |
| `VTaskPath` | Chainable | — |
| `ValidationPath` | Combinable | E (with Semigroup) |
| `IdPath` | Chainable | — |
| `OptionalPath` | Recoverable | Unit |
| `GenericPath` | Chainable | — |
| `TrampolinePath` | Chainable | — |
| `FreePath` | Chainable | — |
| `FreeApPath` | Combinable | — |
| `ListPath` | Chainable | — |
| `StreamPath` | Chainable | — |
| `VStreamPath` | Chainable | — |
| `DefaultVStreamPath` | Chainable | — |
| `DefaultVTaskPath` | Chainable | — |
| `NonDetPath` | Chainable | — |
| `ReaderPath` | Chainable | — |
| `WithStatePath` | Chainable | — |
| `WriterPath` | Chainable | — |
| `LazyPath` | Chainable | — |
| `CompletableFuturePath` | Chainable | — |

#### Task 1.3.4: `PathTypeMismatchChecker.java`

Extends `TreeScanner<Void, Void>`. Core logic in `visitMethodInvocation`:

1. **Match target methods:** `via()`, `then()`, `zipWith()`, `zipWith3()`, `recoverWith()`, `orElse()`
2. **Resolve receiver type:** Use `Trees.instance(task)` and `Types` to get the concrete type of the receiver expression
3. **Resolve argument/return type:**
   - For `via(Function)`: resolve the return type of the lambda/method reference argument
   - For `then(Supplier)`: resolve the return type of the supplier
   - For `zipWith(Combinable, BiFunction)`: resolve the type of the first argument
   - For `recoverWith(Function)`: resolve the return type of the recovery function
   - For `orElse(Supplier)`: resolve the return type of the supplier
4. **Compare types:** If both receiver and argument resolve to concrete Path types, check they are the same family
5. **Report diagnostic:** Use `Trees.instance(task).printMessage(Diagnostic.Kind.ERROR, message, node)` to report at the exact source location

**Important:** Follow a **no false positives** policy. If either type cannot be resolved (e.g., generic parameters, complex inference), skip the check silently. It is better to miss a true mismatch than to report a false error.

#### Task 1.3.5: `DiagnosticMessages.java`

Centralised message formatting (keeps messages consistent across all check sites):

```java
public final class DiagnosticMessages {
    private DiagnosticMessages() {}

    public static String pathTypeMismatch(
            String methodName,
            String expectedType,
            String actualType) {
        return "Path type mismatch in %s(): expected %s but received %s. "
            .formatted(methodName, expectedType, actualType)
            + "Each Path type can only chain with the same type. "
            + "Use conversion methods (toEitherPath, toMaybePath, etc.) to change types.";
    }
}
```

### 1.4 Testing Strategy for `hkj-checker`

**Framework:** Google compile-testing (`com.google.testing.compile:compile-testing`)

This framework compiles Java source in-process and lets you assert on diagnostics.

#### Unit Tests

| Test Class | What It Covers |
|-----------|----------------|
| `HKJCheckerPluginTest` | Plugin loads, getName() returns "HKJChecker", init() registers listener |
| `PathTypeRegistryTest` | All 24 types registered, `isPathType` returns correct results, `areSamePathFamily` logic |
| `DiagnosticMessagesTest` | Message formatting, parameter interpolation |

#### Integration Tests (compile-testing)

| Test Data File | Scenario | Expected Outcome |
|---------------|----------|------------------|
| `MismatchInVia.java` | `Path.just(1).via(_ -> Path.io(() -> 2))` | ERROR with "expected MaybePath but received IOPath" |
| `MismatchInZipWith.java` | `Path.just(1).zipWith(Path.right("x"), ...)` | ERROR with "expected MaybePath but received EitherPath" |
| `MismatchInThen.java` | `Path.right("a").then(() -> Path.just(1))` | ERROR with "expected EitherPath but received MaybePath" |
| `MismatchInRecoverWith.java` | `Path.just(1).recoverWith(_ -> Path.tryOf(...))` | ERROR with type mismatch |
| `MismatchInOrElse.java` | `Path.just(1).orElse(() -> Path.success(2))` | ERROR with type mismatch |
| `CorrectUsage.java` | All paths chain with same type | No errors, compilation succeeds |
| `GenericPathUsage.java` | GenericPath with various witness types | No false positives |

#### Property-Based Tests (JQwik)

- Generate random pairs of Path type names and verify `areSamePathFamily` is reflexive and symmetric
- Generate valid method name strings and verify `DiagnosticMessages.pathTypeMismatch` always produces a non-empty, well-formed message

#### Test Conventions (per `docs/TESTING-GUIDE.md`)

- Use `@DisplayName` for readable test names
- Use `@Nested` classes to group related tests
- Use AssertJ for all assertions
- Follow naming: `methodName_condition_expectedResult`
- Include `@ParameterizedTest` with `@MethodSource` for testing across all 24 Path types

---

## Phase 2: Gradle Plugin (`hkj-gradle-plugin`)

### 2.1 Module Setup

**New module:** `hkj-gradle-plugin`

**Add to `settings.gradle.kts`:**
```kotlin
include(
    // ... existing modules ...
    "hkj-checker",
    "hkj-gradle-plugin"
)
```

**`hkj-gradle-plugin/build.gradle.kts`:**
```kotlin
plugins {
    `java-gradle-plugin`
    alias(libs.plugins.maven.publish)
}

gradlePlugin {
    plugins {
        create("hkj") {
            id = "io.github.higher-kinded-j.hkj"
            implementationClass = "org.higherkindedj.gradle.HKJPlugin"
            displayName = "Higher-Kinded-J Plugin"
            description = "Configures HKJ dependencies, preview features, and compile-time checks"
        }
    }
}

dependencies {
    testImplementation(libs.junit.bom)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
}
```

**Note:** The Gradle plugin is written in **Java** (not Kotlin), consistent with the project's Java-first approach. Gradle's `java-gradle-plugin` supports Java implementations.

### 2.2 Source Structure

```
hkj-gradle-plugin/
├── build.gradle.kts
├── src/main/java/
│   └── org/higherkindedj/gradle/
│       ├── HKJPlugin.java               # Plugin entry point
│       ├── HKJExtension.java            # Top-level DSL extension
│       └── HKJChecksExtension.java      # Nested checks { } DSL block
├── src/main/resources/
│   └── META-INF/gradle-plugins/
│       └── io.github.higher-kinded-j.hkj.properties
└── src/test/java/
    └── org/higherkindedj/gradle/
        ├── HKJPluginTest.java            # Unit tests for plugin application
        └── HKJPluginFunctionalTest.java  # Functional tests with TestKit
```

### 2.3 Implementation Tasks

#### Task 2.3.1: `HKJExtension.java`

```java
/**
 * Extension DSL for the HKJ Gradle plugin.
 *
 * <pre>{@code
 * hkj {
 *     version = "0.3.0"
 *     preview = true
 *     spring = false
 *     checks {
 *         pathTypeMismatch = true
 *     }
 * }
 * }</pre>
 */
public abstract class HKJExtension {
    /** HKJ library version. Defaults to the plugin version. */
    public abstract Property<String> getVersion();

    /** Whether to add --enable-preview flags. Defaults to true. */
    public abstract Property<Boolean> getPreview();

    /** Whether to add hkj-spring-boot-starter. Defaults to false. */
    public abstract Property<Boolean> getSpring();

    /** Compile-time check configuration. */
    public abstract HKJChecksExtension getChecks();
}
```

#### Task 2.3.2: `HKJChecksExtension.java`

```java
/**
 * Configuration for HKJ compile-time checks.
 */
public abstract class HKJChecksExtension {
    /** Enable Path type mismatch detection. Defaults to true. */
    public abstract Property<Boolean> getPathTypeMismatch();
}
```

#### Task 2.3.3: `HKJPlugin.java`

Implements `Plugin<Project>`. Responsibilities:

1. **Create extension:** `project.getExtensions().create("hkj", HKJExtension.class)`
2. **Set defaults:** version = plugin version, preview = true, spring = false, pathTypeMismatch = true
3. **After evaluate:**
   - Add `hkj-core` to `implementation` configuration
   - Add `hkj-processor-plugins` to `annotationProcessor` configuration
   - If `preview` is true: add `--enable-preview` to `JavaCompile`, `Test`, `JavaExec`, and `Javadoc` tasks
   - If `checks.pathTypeMismatch` is true: add `hkj-checker` to `annotationProcessor` and add `-Xplugin:HKJChecker` to compiler args
   - If `spring` is true: add `hkj-spring-boot-starter` to `implementation`
4. **Register diagnostics task:** `hkjDiagnostics` that prints current configuration

**Important:** Use `project.afterEvaluate` to read extension values, but prefer lazy configuration with `Provider` where possible to support Gradle's configuration avoidance API.

#### Task 2.3.4: `hkjDiagnostics` Task

Registered by the plugin. Prints:

```
HKJ Configuration:
  Version:            0.3.0
  Preview features:   enabled
  Spring integration: disabled
  Compile-time checks:
    Path type mismatch: enabled
  Dependencies added:
    implementation:          io.github.higher-kinded-j:hkj-core:0.3.0
    annotationProcessor:     io.github.higher-kinded-j:hkj-processor-plugins:0.3.0
    annotationProcessor:     io.github.higher-kinded-j:hkj-checker:0.3.0
  Compiler args added:
    --enable-preview
    -Xplugin:HKJChecker
```

### 2.4 Testing Strategy for `hkj-gradle-plugin`

#### Unit Tests (`HKJPluginTest.java`)

Using Gradle's `ProjectBuilder`:

| Test | Verifies |
|------|----------|
| `plugin_appliesSuccessfully` | No exception when applied |
| `plugin_createsHKJExtension` | Extension is registered |
| `plugin_addsCoreDependency` | `hkj-core` in implementation |
| `plugin_addsProcessorDependency` | `hkj-processor-plugins` in annotationProcessor |
| `plugin_addsPreviewFlags_whenEnabled` | `--enable-preview` on JavaCompile tasks |
| `plugin_skipsPreviewFlags_whenDisabled` | No `--enable-preview` when `preview = false` |
| `plugin_addsCheckerDependency_whenEnabled` | `hkj-checker` in annotationProcessor |
| `plugin_addsXpluginArg_whenChecksEnabled` | `-Xplugin:HKJChecker` in compiler args |
| `plugin_skipsChecker_whenDisabled` | No checker when `pathTypeMismatch = false` |
| `plugin_addsSpringStarter_whenEnabled` | `hkj-spring-boot-starter` when `spring = true` |
| `plugin_registersHKJDiagnosticsTask` | Task exists and is in `help` group |

#### Functional Tests (`HKJPluginFunctionalTest.java`)

Using Gradle TestKit (`GradleRunner`):

| Test | Verifies |
|------|----------|
| `build_succeeds_withDefaultConfiguration` | A minimal project with just `plugins { id("...") }` compiles |
| `build_succeeds_withSpringEnabled` | Spring starter is resolved |
| `diagnostics_task_printsConfiguration` | `./gradlew hkjDiagnostics` output is correct |
| `checker_reportsPathMismatch` | Compile fails with diagnostic when source has mismatch |
| `checker_silent_onCorrectUsage` | Compile succeeds with correct Path usage |
| `customVersion_overridesDefault` | `hkj { version = "0.2.2" }` changes dependency versions |

**Test project fixtures:** Create minimal `build.gradle` and Java source files in `src/test/resources/fixtures/`.

---

## Phase 3: Enhanced Diagnostics and Runtime Error Messages

### 3.1 Improve Runtime Error Messages

**Target:** All concrete Path implementations under `hkj-core/src/main/java/org/higherkindedj/hkt/effect/`

**Approach:** Search for `IllegalArgumentException` throws in `via()`, `then()`, `zipWith()`, `recoverWith()`, and `orElse()` across all Path implementations. Replace generic messages with descriptive ones following this pattern:

```java
throw new IllegalArgumentException(
    "Type mismatch in via(): expected " + this.getClass().getSimpleName()
    + " but mapper returned " + result.getClass().getSimpleName()
    + ". Each Path type can only chain with the same type."
    + " Use conversion methods (toEitherPath, toMaybePath, etc.) to change types.");
```

**Scope:** All 24 Path types, all `instanceof` check sites.

### 3.2 Runtime Error Message Tests

For each updated Path type, add or update test cases that:

1. Trigger the type mismatch (e.g., `Path.just(1).via(_ -> Path.io(() -> 2))`)
2. Assert the exception message contains:
   - The method name (`via`, `zipWith`, `then`, etc.)
   - The expected type name (`MaybePath`)
   - The actual type name (`IOPath`)
   - The suggestion text ("conversion methods")

Use `assertThatThrownBy(...).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(...)`.

---

## Phase 4: hkj-book Documentation

### 4.1 New Book Chapter: Compile-Time Checking

**Location:** `hkj-book/src/tooling/` (new chapter)

**Files to create:**

```
hkj-book/src/tooling/
├── ch_intro.md           # Chapter introduction
├── gradle_plugin.md      # Gradle plugin setup and configuration
├── compile_checks.md     # Compile-time Path type mismatch checking
└── diagnostics.md        # hkjDiagnostics task and troubleshooting
```

**Add to `SUMMARY.md`:**
```markdown
- [Tooling](tooling/ch_intro.md)
  - [Gradle Plugin](tooling/gradle_plugin.md)
  - [Compile-Time Checks](tooling/compile_checks.md)
  - [Diagnostics](tooling/diagnostics.md)
```

#### `ch_intro.md` — Chapter Introduction

Follow `docs/STYLE-GUIDE.md` chapter introduction template:

```markdown
# Tooling: Build-Time Safety for Higher-Kinded-J

> _"Make illegal states unrepresentable."_
> — Yaron Minsky

Higher-Kinded-J provides build-time tooling that catches common errors
before your code reaches production.

~~~admonish info title="In This Chapter"
- **Gradle Plugin** – One-line setup that configures dependencies, preview
  features, and compile-time checks automatically. Replaces multi-block
  boilerplate configuration.
- **Compile-Time Checks** – A javac plugin that detects Path type mismatches
  at compile time, preventing runtime `IllegalArgumentException` errors.
- **Diagnostics** – The `hkjDiagnostics` Gradle task that reports your
  current HKJ configuration for troubleshooting.
~~~

## Chapter Contents

1. [Gradle Plugin](gradle_plugin.md) - One-line project setup
2. [Compile-Time Checks](compile_checks.md) - Path type mismatch detection
3. [Diagnostics](diagnostics.md) - Configuration reporting and troubleshooting

---

**Next:** [Gradle Plugin](gradle_plugin.md)
```

#### `gradle_plugin.md` — Gradle Plugin Setup

Structure (per style guide):

1. Title: `# Gradle Plugin: One-Line HKJ Setup`
2. "What You'll Learn" admonishment
3. Before/after comparison (manual config vs. plugin)
4. Extension DSL reference (`hkj { ... }`)
5. Spring Boot mode
6. Version management
7. Maven users section (manual configuration)
8. Key Takeaways
9. See Also (link to compile checks, Spring integration chapter)
10. Navigation links

#### `compile_checks.md` — Compile-Time Checks

Structure (per style guide, problem-first for advanced topics):

1. Title: `# Compile-Time Checks: Catching Path Mismatches Early`
2. "What You'll Learn" admonishment
3. **The problem:** Show code that compiles but fails at runtime
4. **The solution:** Enable the checker, show the compile-time error
5. What the checker detects (table of methods)
6. How it works (javac plugin, ANALYZE phase)
7. Configuration options
8. Limitations (no false positives policy, cases it cannot detect)
9. Warning admonishment for known limitations
10. Key Takeaways
11. See Also
12. Navigation links

#### `diagnostics.md` — Diagnostics Task

1. Title: `# Diagnostics: Understanding Your HKJ Configuration`
2. "What You'll Learn" admonishment
3. Running the task (`./gradlew hkjDiagnostics`)
4. Reading the output
5. Common issues and fixes
6. Key Takeaways
7. Navigation links

### 4.2 Update Existing Documentation

#### Update `effect/compiler_errors.md`

Add a section about the new compile-time checker:

```markdown
## Compile-Time Path Type Mismatch Detection

~~~admonish tip title="Automated Detection"
The HKJ Gradle plugin includes a compile-time checker that catches
Path type mismatches before runtime. See [Compile-Time Checks](../tooling/compile_checks.md).
~~~
```

#### Update `quickstart.md`

Replace the manual Gradle configuration section with:

```markdown
## Gradle Setup

### With HKJ Gradle Plugin (Recommended)

```gradle
plugins {
    id("io.github.higher-kinded-j.hkj") version "LATEST_VERSION"
}
```

### Manual Setup

```gradle
dependencies {
    implementation("io.github.higher-kinded-j:hkj-core:LATEST_VERSION")
    annotationProcessor("io.github.higher-kinded-j:hkj-processor-plugins:LATEST_VERSION")
}
// ... preview flags ...
```
```

#### Update `effect/ch_intro.md`

Add a note admonishment pointing to the tooling chapter:

```markdown
~~~admonish note title="Compile-Time Safety"
The HKJ Gradle plugin can detect Path type mismatches at compile time.
See [Compile-Time Checks](../tooling/compile_checks.md) for setup.
~~~
```

### 4.3 Documentation Quality Checklist

Per `docs/STYLE-GUIDE.md`, every new page must satisfy:

- [ ] Title is clear and descriptive
- [ ] "What You'll Learn" admonishment is present
- [ ] Example code links are included (if applicable)
- [ ] Sections are separated with horizontal rules
- [ ] "Key Takeaways" uses info admonishment (if applicable)
- [ ] "See Also" section for internal links (if applicable)
- [ ] Previous/Next navigation links at the end
- [ ] British English spelling throughout
- [ ] No emojis
- [ ] All code examples are properly formatted
- [ ] File names use lowercase with underscores

---

## Phase 5: Integration and Publishing

### 5.1 Root Build Integration

**Update `build.gradle.kts`:**

- Add `hkj-checker` and `hkj-gradle-plugin` to any root-level task orchestration
- Ensure `hkj-checker` is built before `hkj-gradle-plugin` (if the plugin embeds the checker version)

### 5.2 Version Catalogue Updates

**Update `gradle/libs.versions.toml`:**

Add compile-testing dependency if not already present:
```toml
[libraries]
compile-testing = { module = "com.google.testing.compile:compile-testing", version = "0.21.0" }
```

### 5.3 Publishing

| Module | Target | Plugin ID |
|--------|--------|-----------|
| `hkj-checker` | Maven Central | N/A (regular jar) |
| `hkj-gradle-plugin` | Maven Central + Gradle Plugin Portal | `io.github.higher-kinded-j.hkj` |

Both use the existing `com.vanniktech.maven.publish` plugin, consistent with other modules.

### 5.4 CI Integration

- Add `hkj-checker:test` and `hkj-gradle-plugin:test` to the CI pipeline
- Ensure functional tests run with `--enable-preview`
- Add the new modules to the coverage reporting

---

## Task Checklist

### Phase 1: Javac Plugin (`hkj-checker`)

- [ ] Create `hkj-checker/build.gradle.kts`
- [ ] Add `hkj-checker` to `settings.gradle.kts`
- [ ] Write `module-info.java`
- [ ] Implement `HKJCheckerPlugin.java`
- [ ] Implement `PathTypeRegistry.java` with all 24 Path types
- [ ] Implement `PathTypeMismatchChecker.java` (TreeScanner)
- [ ] Implement `DiagnosticMessages.java`
- [ ] Create `META-INF/services/com.sun.source.util.Plugin`
- [ ] Write `HKJCheckerPluginTest.java`
- [ ] Write `PathTypeRegistryTest.java` (including parametrised tests for all 24 types)
- [ ] Write `PathTypeMismatchCheckerTest.java` with compile-testing
- [ ] Create test data files (mismatch and correct usage scenarios)
- [ ] Write property-based tests with JQwik for registry and message formatting
- [ ] Verify `./gradlew :hkj-checker:test` passes
- [ ] Verify Spotless formatting passes

### Phase 2: Gradle Plugin (`hkj-gradle-plugin`)

- [ ] Create `hkj-gradle-plugin/build.gradle.kts`
- [ ] Add `hkj-gradle-plugin` to `settings.gradle.kts`
- [ ] Implement `HKJExtension.java`
- [ ] Implement `HKJChecksExtension.java`
- [ ] Implement `HKJPlugin.java`
- [ ] Register `hkjDiagnostics` task
- [ ] Create `META-INF/gradle-plugins/io.github.higher-kinded-j.hkj.properties`
- [ ] Write `HKJPluginTest.java` (ProjectBuilder unit tests)
- [ ] Write `HKJPluginFunctionalTest.java` (TestKit functional tests)
- [ ] Create test fixture projects in `src/test/resources/fixtures/`
- [ ] Verify `./gradlew :hkj-gradle-plugin:test` passes
- [ ] Verify Spotless formatting passes

### Phase 3: Runtime Error Messages

- [ ] Audit all Path implementations for `IllegalArgumentException` throw sites
- [ ] Update error messages in `via()` / `flatMap()` across all Path types
- [ ] Update error messages in `then()` across all Path types
- [ ] Update error messages in `zipWith()` / `zipWith3()` across all Path types
- [ ] Update error messages in `recoverWith()` across all Path types
- [ ] Update error messages in `orElse()` across all Path types
- [ ] Add/update tests asserting improved error message content
- [ ] Verify `./gradlew :hkj-core:test` passes

### Phase 4: hkj-book Documentation

- [ ] Create `hkj-book/src/tooling/ch_intro.md`
- [ ] Create `hkj-book/src/tooling/gradle_plugin.md`
- [ ] Create `hkj-book/src/tooling/compile_checks.md`
- [ ] Create `hkj-book/src/tooling/diagnostics.md`
- [ ] Add Tooling chapter to `SUMMARY.md`
- [ ] Update `effect/compiler_errors.md` with checker reference
- [ ] Update `quickstart.md` with plugin setup
- [ ] Update `effect/ch_intro.md` with compile-time safety note
- [ ] Run documentation quality checklist on all new pages
- [ ] Verify British English spelling throughout
- [ ] Verify all internal links resolve
- [ ] Verify all code examples compile (or are clearly marked as pseudocode)

### Phase 5: Integration and Publishing

- [ ] Update `gradle/libs.versions.toml` if needed
- [ ] Update root `build.gradle.kts` for new modules
- [ ] Configure Maven Central publishing for `hkj-checker`
- [ ] Configure Gradle Plugin Portal publishing for `hkj-gradle-plugin`
- [ ] Verify full build: `./gradlew build`
- [ ] Verify all tests pass: `./gradlew test`
- [ ] Update `CONTRIBUTING.md` to mention new modules
- [ ] Update root `README.md` with Gradle plugin usage

---

## Risk Mitigations

| Risk | Mitigation |
|------|------------|
| `com.sun.source` API changes in future JDKs | Stay within exported API surface; minimise use of `com.sun.tools.javac` internals; add integration test against latest JDK EA |
| Lambda return type resolution too complex | Start with direct Path factory calls (e.g., `Path.just()`, `Path.io()`); skip unresolvable lambdas (no false positives) |
| Gradle plugin version coupling | Plugin version determines HKJ dependency versions; allow user override via `hkj { version = "..." }` |
| Build time regression from checker | Benchmark compilation with and without checker; ensure overhead is < 5% |
| Test data files become stale | Test data files import from `org.higherkindedj.hkt.effect`; compilation failures in test data indicate API changes |

---

## Implementation Order

**Recommended sequence:**

1. **Phase 3 first** (runtime error messages) — smallest scope, immediate user value, no new modules
2. **Phase 1** (javac plugin) — foundational, enables compile-time detection
3. **Phase 2** (Gradle plugin) — wraps Phase 1 for ergonomics
4. **Phase 4** (documentation) — can begin in parallel with Phase 2
5. **Phase 5** (integration/publishing) — final integration and release

Each phase is independently shippable and provides incremental value.
