# Manual Gradle and Maven Setup

~~~admonish info title="What You'll Learn"
- Full manual `build.gradle.kts` configuration for Higher-Kinded-J
- Full manual `pom.xml` configuration for Higher-Kinded-J
- Snapshot repository configuration for both build tools
~~~

Most projects should use the [HKJ build plugin](gradle_plugin.md): a single line replaces all the boilerplate below. This page documents the full manual configuration for the projects that cannot use the plugin (constrained environments, in-house build frameworks, or plugins that conflict with the HKJ plugin). If the plugin is an option for you, start there instead.

---

## Gradle Manual Setup

```gradle
// build.gradle.kts
plugins { java }

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    implementation("io.github.higher-kinded-j:hkj-core:LATEST_VERSION")

    // Optional: generates Focus paths and Effect paths for your records
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

### Gradle SNAPSHOT Configuration

For SNAPSHOT versions of HKJ, add the Sonatype snapshots repository:

```gradle
repositories {
    mavenCentral()
    maven {
        url = uri("https://central.sonatype.com/repository/maven-snapshots/")
    }
}
```

---

## Maven Manual Setup

```xml
<properties>
    <maven.compiler.release>25</maven.compiler.release>
    <maven.compiler.enablePreview>true</maven.compiler.enablePreview>
</properties>

<dependencies>
    <dependency>
        <groupId>io.github.higher-kinded-j</groupId>
        <artifactId>hkj-core</artifactId>
        <version>LATEST_VERSION</version>
    </dependency>
</dependencies>

<build>
    <plugins>
        <!-- Optional: generates Focus paths and Effect paths for your records -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.14.1</version>
            <configuration>
                <annotationProcessorPaths>
                    <path>
                        <groupId>io.github.higher-kinded-j</groupId>
                        <artifactId>hkj-processor-plugins</artifactId>
                        <version>LATEST_VERSION</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
        <!-- Required: enable preview features for tests -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <configuration>
                <argLine>--enable-preview</argLine>
            </configuration>
        </plugin>
        <!-- Required: enable preview features for application execution -->
        <plugin>
            <groupId>org.codehaus.mojo</groupId>
            <artifactId>exec-maven-plugin</artifactId>
            <configuration>
                <executable>java</executable>
                <arguments>
                    <argument>--enable-preview</argument>
                </arguments>
            </configuration>
        </plugin>
    </plugins>
</build>
```

### Maven SNAPSHOT Configuration

For SNAPSHOT versions, add the Sonatype snapshots repository to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>central-snapshots</id>
        <url>https://central.sonatype.com/repository/maven-snapshots/</url>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
</repositories>
```

---

~~~admonish tip title="Prefer the Plugin"
The [HKJ build plugin](gradle_plugin.md) handles all of the above (dependencies, preview flags, annotation processors, and compile-time Path type checking) with a single `plugins { }` block. Unless you have a specific reason to avoid it, the plugin is the recommended path.
~~~

---

## Incremental compilation

The HKJ annotation processors register with Gradle's incremental annotation processing (as aggregating processors: cross-spec features such as nested mapping resolution may read any annotated element in the compilation, and the mapping processors also read the classpath index described below). A source set using them keeps incremental compilation; no configuration is needed. Unregistered third-party processors on the same processor path disable incrementality for the whole source set, so if compile times regress, audit the other entries on the path first.

## Multi-module builds

A `@GenerateMapping` spec compiled in one module is found by the specs of another: each generated `Impl` of a `MappingSpec` is accompanied by an index entry (an empty class in `org.higherkindedj.mapping.index`, shipped in the jar like any other class), and a compilation reads the index off its classpath and resolves nested, sealed and merge pairs against the specs it names, exactly as against its own. Four things follow for the build:

- **Every module that declares specs needs `hkj-processor` on its processor path**, not only the one that nests them. A module compiled without it has no `Impl` and no entry, and a downstream spec reports the pair as having no usable source.
- **A dependency's new spec may need a clean downstream build.** Gradle recompiles a source set when its classpath changes, but decides which sources to recompile from what they reference, and nothing downstream references an index entry; a spec added to a dependency that no downstream source names may therefore not be seen until the downstream module is rebuilt from clean or one of its sources changes.
- **The index is classpath-only.** A module with a `module-info` neither writes nor reads it, not supported yet, since the index is one package two modules cannot share; the same rule keeps two spec-carrying jars off a module path together as automatic modules. In both cases the route is a leaf calling the other `Impl`'s `asValidatedPrism()`.
- **The index can be turned off.** The processor option `-Ahkj.mapping.index=false` (in Gradle, `options.compilerArgs.add("-Ahkj.mapping.index=false")` on the compile task) makes a compilation write no entries and read none, which is what a library destined for a module path beside other spec-carrying jars wants; a use site that then lacks a pair says the index is off.

See [Across modules](../mapping/structure.md#across-modules) for how precedence and ambiguity are reported.

## Lombok

Lombok and the HKJ processors run in the same javac invocation, and the pairing is covered by a test in the HKJ build: a `@Data` class works as a bean-shaped `@GenerateMapping` wire, with the generated getters and setters visible to the bean analyser. **Order matters: list Lombok before `hkj-processor`** (within a javac round, processors run in listed order, and the bean analyser needs the accessors already materialised; the reverse order fails with a clear diagnostic). No binding artefact is needed beyond that:

```gradle
dependencies {
    annotationProcessor("org.projectlombok:lombok:LOMBOK_VERSION")
    annotationProcessor("io.github.higher-kinded-j:hkj-processor:LATEST_VERSION")
    compileOnly("org.projectlombok:lombok:LOMBOK_VERSION")
}
```

---

**Previous:** [Build Plugins](gradle_plugin.md)
**Next:** [Compile-Time Checks](compile_checks.md)
