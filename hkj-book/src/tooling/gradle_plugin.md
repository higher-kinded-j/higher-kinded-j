# Build Plugins: One-Line HKJ Setup

~~~admonish info title="What You'll Learn"
- How to replace multi-block build configuration with a single plugin for Gradle or Maven
- The full Gradle `hkj { }` extension DSL and its defaults
- Maven plugin configuration via `<configuration>` block
- How to enable Spring Boot integration
- How to override the HKJ library version
- Manual setup for projects not using the plugins
~~~

---

## Before and After

### Without the Plugin

```gradle
// build.gradle.kts
plugins { java }

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    implementation("io.github.higher-kinded-j:hkj-core:0.3.7-SNAPSHOT")
    annotationProcessor("io.github.higher-kinded-j:hkj-processor-plugins:0.3.7-SNAPSHOT")
    annotationProcessor("io.github.higher-kinded-j:hkj-checker:0.3.7-SNAPSHOT")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("--enable-preview", "-Xplugin:HKJChecker"))
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-preview")
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs("--enable-preview")
}
```

### With the Plugin

```gradle
// build.gradle.kts
plugins {
    id("io.github.higher-kinded-j.hkj") version "0.3.7-SNAPSHOT"
}
```

That is it. The plugin handles dependencies, preview flags, compile-time checks, and Javadoc configuration automatically.

### Using SNAPSHOT Versions

SNAPSHOT versions of the plugin are published to the Sonatype snapshots repository. Add it to `pluginManagement` in your `settings.gradle.kts`:

```gradle
// settings.gradle.kts
pluginManagement {
    repositories {
        maven {
            url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
        }
        gradlePluginPortal()
        mavenCentral()
    }
}
```

You also need the same repository in your project's `repositories` block so the plugin can resolve HKJ library dependencies:

```gradle
// build.gradle.kts
repositories {
    mavenCentral()
    maven {
        url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    }
}
```

~~~admonish note
Release versions are published to both Maven Central and the [Gradle Plugin Portal](https://plugins.gradle.org), so no extra repository configuration is needed for releases.
~~~

---

## Extension DSL Reference

The plugin creates an `hkj` extension block with these options:

```gradle
hkj {
    version = "0.3.7-SNAPSHOT"       // HKJ library version (default: plugin version)
    preview = true           // add --enable-preview flags (default: true)
    spring = false           // add hkj-spring-boot-starter (default: false)
    checks {
        pathTypeMismatch = true   // enable compile-time Path type checking (default: true)
    }
}
```

### Defaults

| Property | Default | Description |
|----------|---------|-------------|
| `version` | Plugin version | Version of HKJ libraries to use |
| `preview` | `true` | Adds `--enable-preview` to compile, test, exec, and javadoc tasks |
| `spring` | `false` | Adds `hkj-spring-boot-starter` to implementation dependencies |
| `checks.pathTypeMismatch` | `true` | Enables compile-time Path type mismatch detection |

With default settings, the plugin adds:

- `hkj-core` to `implementation`
- `hkj-processor-plugins` to `annotationProcessor`
- `hkj-checker` to `annotationProcessor`
- `--enable-preview` to `JavaCompile`, `Test`, `JavaExec`, and `Javadoc` tasks
- `-Xplugin:HKJChecker` to compiler arguments

---

## Spring Boot Mode

Enable Spring Boot integration to add the HKJ Spring Boot starter:

```gradle
hkj {
    spring = true
}
```

This adds `hkj-spring-boot-starter` to the `implementation` configuration, which provides auto-configuration for using HKJ types with Spring's dependency injection and web layer.

~~~admonish tip title="See Also"
- [Spring Boot Integration](../spring/spring_boot_integration.md) - Full guide to using HKJ with Spring Boot
~~~

---

## Version Management

By default, the plugin uses its own published version for HKJ dependencies. Override this to pin a different version:

```gradle
hkj {
    version = "0.2.2"    // use an older version
}
```

All HKJ dependencies (`hkj-core`, `hkj-processor-plugins`, `hkj-checker`, `hkj-spring-boot-starter`) use the same version.

---

## Disabling Features

### Disable Preview Features

If your project manages preview flags separately:

```gradle
hkj {
    preview = false
}
```

~~~admonish warning
Higher-Kinded-J requires `--enable-preview` on Java 25. Disabling this means you must configure the flags yourself, or compilation will fail.
~~~

### Disable Compile-Time Checks

To skip Path type mismatch detection:

```gradle
hkj {
    checks {
        pathTypeMismatch = false
    }
}
```

This removes `hkj-checker` from the annotation processor path and omits the `-Xplugin:HKJChecker` compiler argument.

---

## Maven Users

### With the HKJ Maven Plugin

The HKJ Maven plugin provides similar automation to the Gradle plugin. Add it with `<extensions>true</extensions>` so it can configure dependencies and compiler settings automatically:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.higher-kinded-j</groupId>
            <artifactId>hkj-bom</artifactId>
            <version>0.3.7-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<build>
    <plugins>
        <plugin>
            <groupId>io.github.higher-kinded-j</groupId>
            <artifactId>hkj-maven-plugin</artifactId>
            <version>0.3.7-SNAPSHOT</version>
            <extensions>true</extensions>
        </plugin>
    </plugins>
</build>
```

The plugin automatically adds `hkj-core`, annotation processors, compile-time checks, and preview feature flags. Configure options in the `<configuration>` block:

```xml
<configuration>
    <version>0.3.7-SNAPSHOT</version>   <!-- HKJ library version (default: plugin version) -->
    <preview>true</preview>              <!-- add --enable-preview flags (default: true) -->
    <spring>false</spring>               <!-- add hkj-spring-boot-starter (default: false) -->
    <pathTypeMismatch>true</pathTypeMismatch>  <!-- enable compile-time checks (default: true) -->
</configuration>
```

Run diagnostics with: `mvn hkj:diagnostics`

### Manual Maven Setup

If you prefer not to use the plugin, configure dependencies and compiler settings manually:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.higher-kinded-j</groupId>
            <artifactId>hkj-bom</artifactId>
            <version>0.3.7-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>io.github.higher-kinded-j</groupId>
        <artifactId>hkj-core</artifactId>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <release>25</release>
                <enablePreview>true</enablePreview>
                <annotationProcessorPaths>
                    <path>
                        <groupId>io.github.higher-kinded-j</groupId>
                        <artifactId>hkj-processor-plugins</artifactId>
                        <version>0.3.7-SNAPSHOT</version>
                    </path>
                    <path>
                        <groupId>io.github.higher-kinded-j</groupId>
                        <artifactId>hkj-checker</artifactId>
                        <version>0.3.7-SNAPSHOT</version>
                    </path>
                </annotationProcessorPaths>
                <compilerArgs>
                    <arg>-Xplugin:HKJChecker</arg>
                </compilerArgs>
            </configuration>
        </plugin>
    </plugins>
</build>
```

See the [Quickstart](../quickstart.md) page for a complete Maven configuration including test and execution plugins.

---

~~~admonish info title="Key Takeaways"
* **One line** (Gradle) or a short plugin block (Maven) replaces extensive build configuration
* **Sensible defaults** enable preview features and compile-time checks out of the box
* **Everything is optional** and can be disabled or overridden through DSL/configuration
* **The BOM** manages versions across all HKJ modules for both Gradle and Maven
~~~

---

**Previous:** [Tooling](ch_intro.md)
**Next:** [Compile-Time Checks](compile_checks.md)
