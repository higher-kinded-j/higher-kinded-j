# Manual Gradle and Maven Setup

~~~admonish info title="What You'll Learn"
- Full manual `build.gradle.kts` configuration for Higher-Kinded-J
- Full manual `pom.xml` configuration for Higher-Kinded-J
- Snapshot repository configuration for both build tools
~~~

Most projects should use the [HKJ build plugin](gradle_plugin.md): a single line replaces all the boilerplate below. This page documents the full manual configuration for the projects that cannot use the plugin (constrained environments, in-house build frameworks, or plugins that conflict with the HKJ plugin). If the plugin is an option for you, start there instead.

Both configurations below pin the release to **25** and enable preview features, which go together: `javac` accepts `--enable-preview` only for the release it is running on, so the toolchain has to be Java 25 as well. [Prerequisites](../quickstart.md#prerequisites) explains what the flag is for, and where it is genuinely needed. A Maven build that sets neither a release nor a source compiles at Java 8, where the mapping processors [refuse every spec](../mapping/rules.md#java-level).

The Spring Boot starter, and the processor that generates `@HkjHttpClient` clients, are not covered here: [Declarative HTTP Clients](../spring/declarative_http_clients.md#step-1-add-the-starter) shows both, for Gradle and Maven.

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

    // The annotation processor: generates the optics, Focus paths and Effect paths your annotations ask for
    annotationProcessor("io.github.higher-kinded-j:hkj-processor-plugins:LATEST_VERSION")
}

// Enable Java preview features, for code that reaches the preview classes (see Prerequisites).
// -parameters and -implicit:class keep incremental builds sound (see Incremental compilation).
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("--enable-preview", "-parameters", "-implicit:class"))
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

## Maven Manual Setup {#maven-manual-setup}

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
        <!-- The annotation processor: generates the optics, Focus paths and Effect paths your annotations ask for -->
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
        <!-- Enable preview features for tests, for code that reaches the preview classes -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <configuration>
                <argLine>--enable-preview</argLine>
            </configuration>
        </plugin>
        <!-- Enable preview features for application execution, for code that reaches the preview classes -->
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
The [HKJ build plugin](gradle_plugin.md) handles all of the above (dependencies, preview flags, compiler flags, annotation processors, and compile-time Path type checking) with a single `plugins { }` block. Unless you have a specific reason to avoid it, the plugin is the recommended path.
~~~

---

## Incremental compilation {#incremental-compilation}

The HKJ annotation processors register with Gradle's incremental annotation processing, so a change recompiles what it touches rather than the whole source set. Each registers as one of Gradle's two kinds:

| Processor | Registered as | After a change, Gradle |
|---|---|---|
| The generators in `hkj-processor` | aggregating | reprocesses every annotated type, reading each unchanged one from its class file |
| The `@HkjHttpClient` processor | isolating | regenerates a client's files only when it recompiles that client |
| `CompanionAnnotationProcessor`, in both | isolating | has nothing to redo, as it generates nothing |

The generators aggregate because a spec's output can depend on any other annotated type in the compilation. Nested mapping resolution reads the other specs, and the mapping processors also read the classpath index described in [Multi-module builds](#multi-module-builds). Each client's files depend only on that client and what it extends. `CompanionAnnotationProcessor` claims hkj's annotations so that `-Xlint:processing` does not report them.

Two compiler flags keep incremental builds sound. The [HKJ build plugin](gradle_plugin.md) adds both, and the Gradle setup on this page passes them itself.

- **`-parameters` keeps an incremental build writing what a clean build writes.** Without it javac reads the parameters of an unchanged interface's methods as `arg0`, `arg1`, since the class file keeps no names for them.
- **`-implicit:class` keeps a modular source set building under `-Werror`.** Its javadoc-only `package-info.java` files have no class file, so an incremental compile reads them from the source path. With no policy set, javac warns `Implicitly compiled files were not subject to annotation processing`. The flag sets javac's default policy explicitly, which only silences that warning.

Where a name was lost that way, what each processor does depends on whether the name would reach run time:

| Read from a class file without `-parameters` | What the processor does |
|---|---|
| A `@GenerateMerge` spec, whose Impl names each source in its null check | Refuses it: `merge method '…' was read from a class file that records no parameter names` |
| A client binding written without its name, as a bare `@PathVariable`, on a method inherited from a base that Gradle did not recompile, or from a jar | Refuses the binding, `cannot name the binding of`, as [Declarative HTTP Clients](../spring/declarative_http_clients.md#generic-clients) says |
| Any other method a generator writes again from a class file | Names its parameters `arg0`, `arg1`, which changes nothing a program does |

`@GenerateMapping`, `@GenerateMerge`, `@GenerateErrorEnvelope`, `@GenerateIsos`, `@PathSource` and `@HkjHttpClient` refuse a class name that a hand-written class already has, as a clean build does. Where Gradle reads that class from its class file, the message is `the generated class '…' has the name of a class on the classpath`, or for a Path `A type of that name is already on the classpath`. Only a class file carrying HKJ's `@Generated`, which every generator writes, is a generator's to replace. A hand-written class added after its client is the exception: Gradle reruns `@HkjHttpClient` only on a client it recompiles, so the clash is reported when the client next compiles, or by a clean build. The other generators do not refuse a class file's name yet.

A class file keeps no annotation retained in source alone, so what such an annotation decides happens only where Gradle compiles its type from source:

- **A warning is reported by the build compiling its declaration.** An incremental build that reads the declaration from its class file does not report it again, as javac repeats none of its own lint warnings for a class it does not recompile. So a warning `@SuppressWarnings` kept quiet stays quiet.
- **A check reading another library's source-only annotation needs source.** The [`@Singular` refusal](../mapping/rules.md#nullable-where-build-throws) is one, so the next clean build reports what an incremental one let through.
- **A Lombok bean's class file is read as Lombok's by `lombok.Generated`.** Lombok writes it by default from 1.18.34. With an earlier Lombok, an incremental build reads the bean's methods as hand-written, and can refuse what a clean build accepts. Use Lombok 1.18.34 or later, or set `lombok.addLombokGeneratedAnnotation = true` in `lombok.config`.
- **A base's source-only annotation reaches a client from source alone.** An annotation such as `@SuppressWarnings` on a base interface's method is copied onto the client's generated interface only where Gradle compiles the base. The client compiles the same either way.

A type-use annotation another processor writes reaches an optic from source alone too, for another reason. Where Gradle reads a record from its class file and deletes that processor's output, javac never resolves the annotation in that compilation again. That holds in the class file and in a record recompiled beside it, so the optic leaves it off. Under `@NullMarked` a `@Nullable` left off changes the optic's signature; the next clean build writes it.

Unregistered third-party processors on the same processor path disable incrementality for the whole source set, so if compile times regress, audit the other entries on the path first. Gradle's `--info` output names the cause of a full recompilation, as in `Full recompilation is required because ...`.

## Multi-module builds

A `@GenerateMapping` spec compiled in one module is found by the specs of another through an index the processor writes beside each generated `Impl` ([Across modules](../mapping/structure.md#across-modules) explains the resolution). The index travels in the jar as an empty class in the package `org.higherkindedj.mapping.index`, and four things follow for the build:

- **Every module that declares specs needs `hkj-processor` on its processor path**, not only the one that nests them. A module compiled without it has no `Impl` and no index entry, and a downstream spec reports its pair as having no usable source.
- **A spec newly added to a dependency may need a clean downstream build.** Gradle recompiles a source set when its classpath changes, but chooses which sources to recompile from what they reference, and no downstream source references an index entry. Until the downstream module is rebuilt from clean, or one of its sources changes, the new spec can go unseen.
- **The index is classpath-only.** A module with a `module-info` neither writes nor reads it, not supported yet, since the index is one package that two modules cannot share; for the same reason two spec-carrying jars cannot serve as automatic modules side by side. Across such a boundary, delegate with a leaf calling the other `Impl`'s `asValidatedPrism()`.
- **The index can be turned off.** The processor option `-Ahkj.mapping.index=false` (in Gradle, `options.compilerArgs.add("-Ahkj.mapping.index=false")` on the compile task) makes a compilation write no entries and read none, which is what a library bound for a module path beside other spec-carrying jars wants. A downstream spec that then cannot find a pair says the index is off.

A [shared vocabulary](../mapping/codecs.md#shared-vocabulary-mix-in-interfaces) is the exception to all four, because it is a plain interface rather than a spec and is found by ordinary inheritance rather than through the index. The module publishing one needs **no processor at all**, only the library its members name (`hkj-core` for a `ValidatedPrism` leaf, `hkj-api` for a `Getter`), and none of the caveats above apply to it: no index entry to go unseen, and a `module-info` or a disabled index does not stop a downstream spec extending it. So the module that owns an API's house vocabulary need not be a mapping module at all. What it does need is to be on the compile classpath wherever a spec extending it is used. A dependency's spec is read from its class file against the consumer's compile classpath, so a vocabulary the dependency declares with `implementation` leaves that spec unreadable downstream: a use site nesting it fails, naming the missing interface. Either the consumer declares the vocabulary's module itself, or the dependency exposes it with `api` rather than `implementation`, which needs the `java-library` plugin applied alongside HKJ's.

Navigators across modules use no index either. A [navigator](../optics/focus_navigation.md#which-fields-get-a-navigator) into a record declared in a dependency composes the `Focus` class that dependency generated, so **a module whose `@GenerateFocus` records are navigated from elsewhere needs the processor too**; without it, a consumer generates no navigator for those fields and says so in a note. A consumer composes only what the dependency published, so the two need not share a processor version or generator plugins to compile. What a consumer does need is to see the types a field names: a field whose type comes from one of the dependency's own dependencies is left out of the consumer's navigator, with a note, unless that type is on the consumer's compile classpath. Either the consumer declares that module itself, or the dependency exposes it with `api` rather than `implementation`, which needs the `java-library` plugin applied alongside HKJ's, since HKJ's applies plain `java`. As with specs, a dependency newly given the processor may need a clean downstream build before its records become navigable: no downstream source references the new `Focus` classes, so Gradle has nothing to recompile.

## Lombok {#lombok}

Lombok and the HKJ processors run in the same javac invocation, and the pairing is covered by a test in the HKJ build: a `@Data` class works as a bean-shaped `@GenerateMapping` wire, with the generated getters and setters visible to the bean analyser. Use Lombok 1.18.34 or later, which marks what it writes `@lombok.Generated`, so an [incremental build](#incremental-compilation) reads a bean it did not recompile as Lombok's. **Order matters: list Lombok before `hkj-processor`** (within a javac round, processors run in listed order, and the bean analyser needs the accessors already materialised; the reverse order fails with a clear diagnostic). No binding artefact is needed beyond that:

```gradle
dependencies {
    annotationProcessor("org.projectlombok:lombok:LOMBOK_VERSION")
    annotationProcessor("io.github.higher-kinded-j:hkj-processor:LATEST_VERSION")
    compileOnly("org.projectlombok:lombok:LOMBOK_VERSION")
}
```

## Mapping over types other processors generate {#mapping-over-types-other-processors-generate}

A mapping may name a type another annotation processor writes in the same compilation: a wire class generated from a schema, say. Such a type exists only from the round after the one that writes it, so the mapping processors wait. A `@GenerateMapping` or `@GenerateMerge` spec whose domain or wire type, or anything read from them (a record component, a bean property, a builder, a mix-in method), names a type not written yet is generated once that type exists, and so is any spec that nests it. So is a mapping spec setting `@MapField` or `@MapKey` to such a type's constant. It maps as it would were the type written by hand, with nothing to configure.

If the type never appears, as when its processor is missing from the processor path, the spec generates nothing, nor does a spec nesting it. javac names what is missing, and code that uses the spec's `Impl` reports that `Impl` missing too; supply the type and both errors go.

## Optics over types other processors generate {#optics-over-types-other-processors-generate}

The optics generators wait in the same way. A type carrying `@GenerateLenses`, `@GenerateFocus`, `@GeneratePrisms`, `@GenerateGetters`, `@GenerateSetters`, `@GenerateFolds` or `@GenerateTraversals`, or a `@GenerateIsos` method, that names a type not written yet is generated once that type exists. So is one that reads a constant such a type declares, as `@GenerateLenses(targetPackage = Packages.OPTICS)` does. In a clean build, the generated class is exactly what it would be were the type written by hand.

The wait covers a component's type with every type argument in it, a type-parameter bound, a permitted subtype, a superclass or interface, an iso method's return type, and a type-use annotation. Each type in your sources that the declaration reaches is read the same way, so a record waits with a record it holds, and a navigator never steps into one still waiting. A record Gradle's [incremental build](#incremental-compilation) reads from its class file waits only for a declaration recompiled beside it that it reaches, since the class file names each of its own types in full. There, an optic may lose a type-use annotation another processor writes.

If the type never appears, the generator writes nothing for the declaration, and says so there with what never resolved, as [Compiler Messages](../optics/compiler_errors.md#generated-nothing-no-round-resolved) shows. A declaration read from its class file has no position, so the message names it in full. javac's own error names the missing type where the source writes it.

---

**Previous:** [Build Plugins](gradle_plugin.md)
**Next:** [Compile-Time Checks](compile_checks.md)
