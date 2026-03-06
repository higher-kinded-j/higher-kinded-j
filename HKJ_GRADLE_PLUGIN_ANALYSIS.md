# HKJ Gradle/Maven Plugin: Feasibility & Approach Comparison

## Motivation

Users currently consume higher-kinded-j with boilerplate configuration:

```gradle
dependencies {
    implementation("io.github.higher-kinded-j:hkj-core:LATEST_VERSION")
    annotationProcessor("io.github.higher-kinded-j:hkj-processor-plugins:LATEST_VERSION")
}

// Required: enable Java preview features
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--enable-preview")
}
tasks.withType<Test>().configureEach {
    jvmArgs("--enable-preview")
}
tasks.withType<JavaExec>().configureEach {
    jvmArgs("--enable-preview")
}
```

A Gradle plugin could reduce this to:

```gradle
plugins {
    id("io.github.higher-kinded-j.hkj") version "LATEST_VERSION"
}
```

Beyond ergonomics, a plugin provides an extensible platform for compile-time checks (like the Path type mismatch detection), build-time validation, and future tooling — all configured automatically.

---

## Part 1: What a HKJ Gradle Plugin Would Do

### 1.1 Core Responsibilities (Day 1)

1. **Auto-add dependencies** — `hkj-core`, `hkj-processor-plugins` at the correct version
2. **Configure `--enable-preview`** — on JavaCompile, Test, JavaExec, and Javadoc tasks
3. **Configure annotation processor path** — automatically wire `hkj-processor-plugins`
4. **Extension DSL** — allow users to configure options:

```gradle
hkj {
    version = "0.3.0"           // override default version
    checks {
        pathTypeMismatch = true  // enable compile-time Path type checking
    }
    spring = false               // don't add Spring starter
    preview = true               // manage --enable-preview (default true)
}
```

### 1.2 Extended Responsibilities (Future)

5. **Compile-time checks** — Path type mismatch detection (the specific use case that prompted this analysis)
6. **Generated source integration** — auto-configure `sourceSets` for generated code visibility in IDEs
7. **Spring Boot mode** — when `spring = true`, add `hkj-spring-boot-starter` instead
8. **Migration recipes** — integrate OpenRewrite recipes for version upgrades
9. **Diagnostics task** — `./gradlew hkjDiagnostics` to report configuration issues

### 1.3 Wider Benefits Beyond Path Type Checking

| Benefit | Details |
|---------|---------|
| **Reduced onboarding friction** | Single plugin line vs. multi-block configuration |
| **Version coherence** | Plugin version determines all HKJ dependency versions — no mismatches |
| **Upgrade path** | Plugin can include migration recipes for breaking changes |
| **IDE integration** | Plugin can configure generated source directories for IntelliJ/Eclipse |
| **Build cache friendliness** | Plugin can configure annotation processor isolation correctly |
| **Extensibility platform** | Future checks, validations, code generation can be added without user config changes |
| **Spring starter parity** | Same "one line" experience that Spring Boot users already get |

---

## Part 2: Compile-Time Check Implementation — Approach Comparison

The Path type mismatch problem requires **call-site analysis**: inspecting what happens inside method bodies where `via()`, `zipWith()`, `then()` are called. Five approaches are compared below.

### 2.1 Error Prone BugChecker

**How it works:** Error Prone is a javac plugin by Google that provides a framework for writing custom `BugChecker` classes. Checkers visit AST nodes (method invocations, assignments, etc.) and report warnings/errors.

**User setup (without a Gradle plugin):**
```gradle
plugins {
    id("net.ltgt.errorprone") version "4.1.0"
}
dependencies {
    errorprone("com.google.errorprone:error_prone_core:2.36.0")
    errorprone("io.github.higher-kinded-j:hkj-checker:0.3.0")
}
```

**User setup (with HKJ Gradle plugin):**
```gradle
plugins {
    id("io.github.higher-kinded-j.hkj") version "0.3.0"
}
hkj {
    checks.pathTypeMismatch = true  // adds Error Prone + hkj-checker automatically
}
```

**Capability assessment:**

| Capability | Rating | Notes |
|------------|--------|-------|
| Inspect method call sites | YES | `MethodInvocationTreeMatcher` visits every method call |
| Resolve receiver types | YES | `ASTHelpers.getReceiverType(tree)` gives resolved generic types |
| Resolve lambda return types | YES | After javac attribution, lambda types are resolved |
| Suggest fixes | YES | `SuggestedFix` API can propose code changes |
| IDE integration | PARTIAL | IntelliJ has Error Prone support; VS Code/Eclipse limited |

**Advantages:**
- Mature framework, well-documented API
- `CollectionIncompatibleType` checker is a close precedent for our use case
- Rich `ASTHelpers` and `Matchers` utility libraries
- Auto-fix suggestions via `SuggestedFix`
- Active maintenance by Google

**Disadvantages:**
- **Java 25 compatibility is fragile** — Error Prone relies heavily on `com.sun.tools.javac` internals. Error Prone 2.32.0 crashes on JDK 25 with `NoSuchFieldError: TypeTag.UNKNOWN`. Newer versions fix this, but each JDK release risks breakage.
- **Heavy dependency** — `error_prone_core` pulls in Guava, protobuf, AutoValue, and many other transitive dependencies
- **Requires `net.ltgt.errorprone` Gradle plugin** — another third-party plugin dependency
- **JVM `--add-exports` flags** — Error Prone needs javac internals opened, which requires additional compiler flags
- **Build time impact** — Error Prone adds measurable overhead to compilation
- **Users may already have Error Prone configured** — potential conflicts with existing setup

**Risk for HKJ specifically:**
The project uses Java 25 with `--enable-preview`. Error Prone's track record with bleeding-edge JDK versions is poor — each JDK release cycle requires waiting for Error Prone compatibility fixes. This creates a maintenance burden and blocks users from adopting new JDK versions promptly.

---

### 2.2 Custom Javac Plugin (com.sun.source.util.Plugin)

**How it works:** Java's `com.sun.source.util.Plugin` and `TaskListener` APIs allow hooking into javac's compilation phases. A plugin registered via `META-INF/services/com.sun.source.util.Plugin` can listen for the `ANALYZE` event (post-type-checking) and walk the fully-attributed AST.

**User setup (without a Gradle plugin):**
```gradle
dependencies {
    annotationProcessor("io.github.higher-kinded-j:hkj-checker:0.3.0")
}
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xplugin:HKJChecker")
}
```

**User setup (with HKJ Gradle plugin):**
```gradle
plugins {
    id("io.github.higher-kinded-j.hkj") version "0.3.0"
}
hkj {
    checks.pathTypeMismatch = true  // adds -Xplugin:HKJChecker automatically
}
```

**Capability assessment:**

| Capability | Rating | Notes |
|------------|--------|-------|
| Inspect method call sites | YES | `TreeScanner` visits `MethodInvocationTree` nodes |
| Resolve receiver types | YES | After ANALYZE phase, types are fully attributed |
| Resolve lambda return types | YES | Lambda types resolved during ANALYZE |
| Suggest fixes | NO | No built-in fix suggestion API |
| IDE integration | MINIMAL | Build output only; no IDE-specific integration |

**Advantages:**
- **Zero external dependencies** — uses only JDK-provided APIs (`com.sun.source.*`)
- **Lightweight** — no Guava, no protobuf, just a single jar
- **Full AST access** — same tree API that Error Prone uses internally
- **Standard service discovery** — `META-INF/services/com.sun.source.util.Plugin`
- **Can be on annotation processor path** — no special Gradle plugin needed for basic setup
- **Same internal APIs as Error Prone** — `com.sun.source.tree.*` and `com.sun.source.util.*` are in the `jdk.compiler` module, which is a supported (exported) API

**Disadvantages:**
- **`com.sun.source` is supported but not standardized** — it's exported from `jdk.compiler` and considered stable, but changes between JDK versions are possible
- **No utility library** — must implement AST matching, type resolution helpers from scratch (Error Prone's `ASTHelpers` is very convenient)
- **No auto-fix** — can only report diagnostics, not suggest code changes
- **Requires `--add-opens` or module access** — some deeper type resolution may need `com.sun.tools.javac.*` (internal, not exported)
- **Less community support** — fewer examples and documentation than Error Prone

**Key distinction from Error Prone:**
The `com.sun.source.tree` and `com.sun.source.util` packages are **exported from `jdk.compiler`** and are meant to be consumed by tools. This is a *supported* API, unlike the `com.sun.tools.javac.code` internals that Error Prone uses (and that break between JDK versions). A javac plugin that stays within `com.sun.source.*` is more portable than Error Prone.

**However:** For resolving generic type arguments on receivers (e.g., "this `MaybePath<String>` is the concrete receiver type"), you may need `com.sun.tools.javac.code.Type` — which is internal. The depth of type analysis needed determines whether you can stay in the supported API surface.

---

### 2.3 Checker Framework

**How it works:** The Checker Framework provides a pluggable type system for Java. Custom checkers define type qualifiers (annotations) and type rules. It hooks into javac as a plugin and performs type-checking with the extended type system.

**User setup:**
```gradle
dependencies {
    compileOnly("org.checkerframework:checker-qual:3.48.0")
    annotationProcessor("org.checkerframework:checker:3.48.0")
    annotationProcessor("io.github.higher-kinded-j:hkj-checker:0.3.0")
}
```

**Capability assessment:**

| Capability | Rating | Notes |
|------------|--------|-------|
| Inspect method call sites | YES | Full type-checking phase access |
| Resolve receiver types | YES | Built-in type resolution |
| Resolve lambda return types | YES | Full type inference |
| Suggest fixes | NO | Reports errors only |
| IDE integration | MODERATE | IntelliJ plugin available |

**Advantages:**
- Powerful pluggable type system — good for "types that flow through code"
- Can express complex type relationships
- Established in the nullness checking community

**Disadvantages:**
- **Very heavy dependency** — Checker Framework is large and complex
- **Designed for annotation-based type systems** — our problem (same-kind Path matching) doesn't naturally map to qualifier annotations
- **Steep learning curve** for writing custom checkers
- **Java 25 compatibility unknown** — historically lags behind JDK releases
- **Significant build time overhead** — re-runs type checking with extended rules
- **Overkill** — we need a single call-site check, not a type system extension

**Verdict:** Not a good fit. The Checker Framework is designed for pervasive type system extensions (nullness, tainting, immutability), not for checking a specific API usage pattern.

---

### 2.4 Bytecode Analysis (SpotBugs/PMD Style)

**How it works:** Analyze compiled `.class` files or source files after compilation. SpotBugs works on bytecode; PMD works on source AST.

**Capability assessment:**

| Capability | Rating | Notes |
|------------|--------|-------|
| Inspect method call sites | PARTIAL | Bytecode has `INVOKEVIRTUAL` but generic types are erased |
| Resolve receiver types | NO | Bytecode uses erased types — `Chainable` not `MaybePath<String>` |
| Resolve lambda return types | NO | Lambdas compiled to `invokedynamic` — return types erased |
| Suggest fixes | NO | No source code access |
| IDE integration | MODERATE | SpotBugs has IntelliJ/Eclipse plugins |

**Verdict:** **Cannot work.** The core of our check requires knowing concrete generic types (e.g., "this is `MaybePath<String>`, not just `Chainable<String>`"). Java's type erasure means this information is lost in bytecode. PMD's source analysis doesn't perform type resolution.

---

### 2.5 Custom Standalone Lint Tool

**How it works:** A purpose-built tool that parses Java source files and performs the check. Could use JavaParser, Eclipse JDT, or Tree-sitter for parsing.

**User setup:**
```gradle
// Separate Gradle task
tasks.register<JavaExec>("hkjLint") {
    mainClass = "org.higherkindedj.lint.HKJLint"
    classpath = configurations.detachedConfiguration(
        dependencies.create("io.github.higher-kinded-j:hkj-lint:0.3.0"))
    args(sourceSets.main.get().java.srcDirs.map { it.absolutePath })
}
check.dependsOn("hkjLint")
```

**Capability assessment:**

| Capability | Rating | Notes |
|------------|--------|-------|
| Inspect method call sites | YES | Source parsing finds method calls |
| Resolve receiver types | PARTIAL | Requires building a type model — complex |
| Resolve lambda return types | PARTIAL | Must re-implement type inference — extremely complex |
| Suggest fixes | YES | Full source access |
| IDE integration | POOR | Separate tool, not integrated in compilation |

**Advantages:**
- Complete control — no dependency on javac internals
- Portable across JDK versions (the tool runs on any JDK)
- Can target specific files/patterns

**Disadvantages:**
- **Must re-implement type resolution** — this is essentially reimplementing a subset of javac's type checker, which is enormous work
- **Separate build step** — not part of compilation, easy to skip
- **Maintenance burden** — keeping type resolution correct across Java language changes
- **JavaParser doesn't resolve types** without additional symbol resolution setup
- **Eclipse JDT** can resolve types but is a massive dependency

**Verdict:** Too much effort for too little benefit. Reimplementing type resolution is the wrong approach when javac already does it.

---

## Part 3: Comparison Matrix

| Dimension | Error Prone | Javac Plugin | Checker Framework | Bytecode Analysis | Standalone Lint |
|-----------|-------------|--------------|-------------------|-------------------|-----------------|
| **Can detect Path mismatches** | YES | YES | YES (overkill) | NO (erasure) | PARTIAL |
| **External dependencies** | Heavy (Guava, protobuf, etc.) | None (JDK only) | Heavy | Moderate | Moderate-Heavy |
| **Java 25 compatibility** | FRAGILE (breaks each JDK) | GOOD (supported API surface) | UNKNOWN | N/A | GOOD |
| **Build time overhead** | Moderate | Minimal | Significant | Separate step | Separate step |
| **User ergonomics (standalone)** | Moderate (needs EP plugin) | Good (compiler arg) | Poor (complex setup) | Moderate | Poor |
| **User ergonomics (via HKJ plugin)** | Good | Excellent | Moderate | Moderate | Moderate |
| **Maintenance burden** | Low (framework handles boilerplate) | Medium (manual AST walking) | High | N/A | Very High |
| **Auto-fix suggestions** | YES | NO | NO | NO | YES (custom) |
| **IDE integration** | Moderate | Minimal | Moderate | Moderate | Poor |
| **Extensibility for future checks** | Excellent | Good | Excellent | Poor | Good |
| **Distribution** | Separate jar on errorprone classpath | Jar on processor/plugin path | Separate jar | Separate jar | Separate jar |

---

## Part 4: Recommendation

### Primary: Custom Javac Plugin wrapped by HKJ Gradle Plugin

**Rationale:**

1. **Zero external dependencies** — The javac plugin uses only `com.sun.source.*` APIs exported from `jdk.compiler`. No Guava, no protobuf, no third-party Gradle plugin required.

2. **Java 25 resilience** — The `com.sun.source.tree` and `com.sun.source.util` packages are the *supported* tool API in the JDK. While not immune to change, they are far more stable than the `com.sun.tools.javac.code` internals that Error Prone depends on.

3. **Minimal overhead** — A focused plugin that checks ~5 method patterns adds negligible build time compared to Error Prone's full analysis framework.

4. **Clean user experience** — The HKJ Gradle plugin wraps everything:

```gradle
plugins {
    id("io.github.higher-kinded-j.hkj") version "0.3.0"
}

// That's it. Dependencies, preview flags, and checks are configured automatically.
// Optionally customize:
hkj {
    checks {
        pathTypeMismatch = true  // enabled by default
    }
}
```

5. **Aligned with project philosophy** — HKJ already has custom annotation processors. A javac plugin is a natural extension of the same build-time tooling philosophy.

6. **Extensible** — Future checks can be added to the same plugin without requiring users to update their configuration.

### Why Not Error Prone?

Error Prone is an excellent tool, but its fit for HKJ is poor:

- **JDK 25 fragility** — HKJ targets Java 25 with preview features. Error Prone has historically broken on every major JDK release due to its reliance on `com.sun.tools.javac` internals. This creates an ongoing maintenance burden and blocks HKJ users from adopting new JDKs.

- **Dependency weight** — Adding Error Prone to a project that currently has zero build-tool dependencies beyond Gradle and the JDK is a significant footprint increase. It pulls in ~50+ transitive dependencies.

- **Configuration complexity** — Even with a wrapper plugin, Error Prone requires `--add-exports` flags, the `net.ltgt.errorprone` plugin, and careful version management. This complexity leaks through to users.

- **Philosophical mismatch** — Error Prone is designed for large-scale codebases at Google where hundreds of checks run simultaneously. HKJ needs exactly one check. The framework overhead doesn't justify the capability for this use case.

### Hybrid Option: Build Javac Plugin, Provide Error Prone Adapter Later

The javac plugin's core logic (AST walking, type checking) can be factored into a shared library. If demand exists, an Error Prone `BugChecker` adapter can wrap this logic to integrate with teams that already use Error Prone. This gives the best of both worlds without forcing Error Prone on all users.

---

## Part 5: HKJ Gradle Plugin — Architecture

### 5.1 Module Structure

```
hkj-gradle-plugin/               # New module
├── build.gradle.kts              # Gradle plugin publishing config
├── src/main/kotlin/
│   └── io/github/higherkindedj/gradle/
│       ├── HKJPlugin.kt          # Plugin entry point
│       ├── HKJExtension.kt       # DSL extension (hkj { ... })
│       └── HKJChecksExtension.kt # Checks sub-extension
└── src/test/kotlin/
    └── ...                        # Functional tests

hkj-checker/                      # New module (javac plugin)
├── build.gradle.kts
├── src/main/java/
│   ├── module-info.java
│   └── org/higherkindedj/checker/
│       ├── HKJCheckerPlugin.java           # com.sun.source.util.Plugin implementation
│       ├── PathTypeMismatchChecker.java     # Core check logic
│       └── PathTypeRegistry.java           # Known Path types and relationships
├── src/main/resources/
│   └── META-INF/services/
│       └── com.sun.source.util.Plugin      # Service registration
└── src/test/java/
    └── ...                                  # Compile-time test cases
```

### 5.2 What the Gradle Plugin Does

```kotlin
class HKJPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("hkj", HKJExtension::class.java)

        project.afterEvaluate {
            val version = extension.version.getOrElse(pluginVersion)

            // 1. Add core dependency
            project.dependencies.add("implementation",
                "io.github.higher-kinded-j:hkj-core:$version")

            // 2. Add annotation processor
            project.dependencies.add("annotationProcessor",
                "io.github.higher-kinded-j:hkj-processor-plugins:$version")

            // 3. Configure --enable-preview
            if (extension.preview.getOrElse(true)) {
                project.tasks.withType<JavaCompile>().configureEach {
                    options.compilerArgs.add("--enable-preview")
                }
                project.tasks.withType<Test>().configureEach {
                    jvmArgs("--enable-preview")
                }
                project.tasks.withType<JavaExec>().configureEach {
                    jvmArgs("--enable-preview")
                }
            }

            // 4. Configure compile-time checks
            if (extension.checks.pathTypeMismatch.getOrElse(true)) {
                project.dependencies.add("annotationProcessor",
                    "io.github.higher-kinded-j:hkj-checker:$version")
                project.tasks.withType<JavaCompile>().configureEach {
                    options.compilerArgs.add("-Xplugin:HKJChecker")
                }
            }

            // 5. Spring mode
            if (extension.spring.getOrElse(false)) {
                project.dependencies.add("implementation",
                    "io.github.higher-kinded-j:hkj-spring-boot-starter:$version")
            }
        }
    }
}
```

### 5.3 What the Javac Plugin Does

```java
public class HKJCheckerPlugin implements Plugin {
    @Override
    public String getName() { return "HKJChecker"; }

    @Override
    public void init(JavacTask task, String... args) {
        task.addTaskListener(new TaskListener() {
            @Override
            public void finished(TaskEvent event) {
                if (event.getKind() == TaskEvent.Kind.ANALYZE) {
                    CompilationUnitTree cu = event.getCompilationUnit();
                    new PathTypeMismatchChecker(task).scan(cu, null);
                }
            }
        });
    }
}
```

The `PathTypeMismatchChecker` extends `TreeScanner<Void, Void>` and overrides `visitMethodInvocation`:

```java
@Override
public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
    // 1. Is this via(), zipWith(), then(), recoverWith(), or orElse()?
    // 2. Get the receiver's resolved type — is it a concrete Path type?
    // 3. Get the argument/lambda's resolved return type
    // 4. If types are concrete and mismatched, report a diagnostic
    return super.visitMethodInvocation(node, unused);
}
```

### 5.4 Publishing

The Gradle plugin publishes to both:
- **Gradle Plugin Portal** — for `plugins { id("io.github.higher-kinded-j.hkj") }` syntax
- **Maven Central** — for buildscript classpath consumption and Maven users

The javac plugin (`hkj-checker`) publishes to Maven Central only, as it's a regular jar.

### 5.5 Maven Support

For Maven users, the javac plugin works directly without a Maven plugin:

```xml
<dependencies>
    <dependency>
        <groupId>io.github.higher-kinded-j</groupId>
        <artifactId>hkj-core</artifactId>
        <version>${hkj.version}</version>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <compilerArgs>
                    <arg>--enable-preview</arg>
                    <arg>-Xplugin:HKJChecker</arg>
                </compilerArgs>
                <annotationProcessorPaths>
                    <path>
                        <groupId>io.github.higher-kinded-j</groupId>
                        <artifactId>hkj-processor-plugins</artifactId>
                        <version>${hkj.version}</version>
                    </path>
                    <path>
                        <groupId>io.github.higher-kinded-j</groupId>
                        <artifactId>hkj-checker</artifactId>
                        <version>${hkj.version}</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

A Maven plugin wrapper could be added later if demand warrants it.

---

## Part 6: Implementation Phases

### Phase 1: Javac Plugin (hkj-checker)

**Deliverables:**
- New `hkj-checker` module with `HKJCheckerPlugin`
- `PathTypeMismatchChecker` detecting mismatches in `via()`, `zipWith()`, `then()`, `recoverWith()`, `orElse()`
- `PathTypeRegistry` listing all 18 concrete Path types
- Test suite using compile-testing framework
- Published to Maven Central

**Estimated scope:** ~500-800 lines of checker code + tests

**User value:** Immediate compile-time warnings for Path type mismatches, consumable by both Gradle and Maven users via standard compiler configuration.

### Phase 2: Gradle Plugin (hkj-gradle-plugin)

**Deliverables:**
- New `hkj-gradle-plugin` module (Kotlin DSL)
- `HKJPlugin` with extension DSL
- Auto-configuration of dependencies, preview flags, and checker
- Functional test suite
- Published to Gradle Plugin Portal and Maven Central

**Estimated scope:** ~300-500 lines of plugin code + tests

**User value:** One-line setup for the complete HKJ toolchain.

### Phase 3: Enhanced Diagnostics

**Deliverables:**
- `./gradlew hkjDiagnostics` task reporting configuration status
- Improved runtime error messages mentioning the checker
- hkj-book chapter on compile-time checking

### Phase 4: Extensibility

**Deliverables:**
- Additional checks (e.g., GenericPath witness type consistency)
- Optional Error Prone adapter for teams that already use Error Prone
- OpenRewrite recipe integration for version migration

---

## Part 7: Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| `com.sun.source` API changes | Low | Medium | API is exported from `jdk.compiler`; changes are rare and usually additive |
| Type resolution needs `com.sun.tools.javac` internals | Medium | High | Prototype the checker first to determine if `com.sun.source` APIs suffice; if not, use `--add-opens` with clear documentation |
| Gradle Plugin Portal publishing complexity | Low | Low | Well-documented process; similar to existing Maven Central publishing |
| Users on non-javac compilers (Eclipse ECJ) | Low | Low | Plugin is opt-in; users on ECJ lose checks but nothing else breaks |
| Lambda type resolution edge cases | Medium | Medium | Start with the common cases; skip unresolvable types (no false positives policy) |

---

## Part 8: Decision Summary

| Approach | Recommended? | Rationale |
|----------|-------------|-----------|
| **Custom Javac Plugin + Gradle Plugin** | **YES** | Zero deps, Java 25 resilient, extensible, clean UX |
| Error Prone BugChecker | NO (for now) | JDK 25 fragility, heavy deps, can add adapter later |
| Checker Framework | NO | Overkill for single check pattern |
| Bytecode Analysis | NO | Type erasure makes this impossible |
| Standalone Lint Tool | NO | Requires reimplementing type resolution |
| Approach 3 (Method Overloads) | NO | Java overload resolution ambiguity |

**Recommended implementation order:** Phase 1 (javac plugin) → Phase 2 (Gradle plugin) → Phase 3 (diagnostics) → Phase 4 (extensibility)

The javac plugin is the foundation and provides immediate value. The Gradle plugin wraps it for convenience. Each phase is independently useful and shippable.
