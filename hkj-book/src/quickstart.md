# Quickstart

~~~admonish info title="What You'll Learn"
- How to add Higher-Kinded-J to a Gradle or Maven project
- Java 25 preview mode configuration (required)
- Your first Effect Paths in under 5 minutes
~~~

---

## Prerequisites

Higher-Kinded-J requires **Java 25** or later with **preview features enabled**.

The library uses Java preview features including stable values and flexible constructor bodies. Without `--enable-preview`, your project will not compile.

---

## Gradle Setup

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

For SNAPSHOTS, add the Sonatype snapshots repository:

```gradle
repositories {
    mavenCentral()
    maven {
        url = uri("https://central.sonatype.com/repository/maven-snapshots/")
    }
}
```

---

## Maven Setup

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

---

## Simplify Imports with Module Import

Java 23+ supports module import declarations ([JEP 511](https://openjdk.org/jeps/511)), which let you import all exported types from a module in a single line. Instead of importing individual packages:

```java
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.focus.FocusPath;
// ... and more
```

You can write:

```java
import module org.higherkindedj.core;
```

This gives you access to `Path`, `MaybePath`, `EitherPath`, `ValidationPath`, `VTaskPath`, `FocusPath`, `AffinePath`, `TraversalPath`, and all other exported types from the core module.

~~~admonish note
Module imports require `--enable-preview` on Java 23–24. On Java 25+, the feature is standard and no flag is needed for module imports themselves (though HKJ still requires `--enable-preview` for other features).
~~~

---

## Handle Absence

When a value might not exist, use `MaybePath`:

```java
import org.higherkindedj.hkt.effect.Path;

var user = Path.maybe(repository.findById(id));   // Just(user) or Nothing
var name = user.map(User::name);                  // transforms only if present
var result = name.run().orElse("Anonymous");       // extract to standard Java
```

---

## Handle Errors

When an operation can fail with a typed error, use `EitherPath`:

```java
import org.higherkindedj.hkt.effect.Path;

var user = Path.maybe(repository.findById(userId))
    .toEitherPath(new AppError.NotFound(userId));     // Nothing becomes Left(error)

var order = user
    .via(u -> Path.either(orderService.create(u)))    // chain another operation
    .map(Order::confirm);                             // transform the success value

var result = order.run();                             // Either<AppError, Order>
```

---

## Validate Input

When you need *all* errors, not just the first, use `ValidationPath`:

```java
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.Semigroup;

Semigroup<List<String>> sg = Semigroup.listSemigroup();

var name = validateName(input.name());       // ValidationPath<List<String>, String>
var email = validateEmail(input.email());     // ValidationPath<List<String>, String>
var age = validateAge(input.age());           // ValidationPath<List<String>, Integer>

var user = name.zipWith3Accum(email, age, User::new);  // accumulates ALL errors
var result = user.run();                                // Validated<List<String>, User>
```

---

## Chain It Together

Combine absence, errors, and transformation in a single pipeline:

```java
import org.higherkindedj.hkt.effect.Path;

public EitherPath<AppError, Receipt> processPayment(String userId, BigDecimal amount) {
    return Path.maybe(userRepository.findById(userId))
        .toEitherPath(new AppError.UserNotFound(userId))
        .via(user -> Path.either(validateAmount(user, amount)))
        .via(validated -> Path.tryOf(() -> gateway.charge(validated))
            .toEitherPath(AppError.PaymentFailed::new))
        .map(charge -> new Receipt(charge.id(), amount));
}
```

---

## Getting Back to Standard Java

Every Path type unwraps to a standard Java value. You are never locked in:

```java
Maybe<User> maybe = maybePath.run();                         // → Maybe
Either<AppError, User> either = eitherPath.run();            // → Either
Optional<User> opt = maybePath.run().toOptional();           // → java.util.Optional
User user = eitherPath.run().getOrElse(User.anonymous());    // → raw value
String msg = eitherPath.run().fold(
    error -> "Failed: " + error,                             // handle error
    value -> "Success: " + value                             // handle success
);
```

---

~~~admonish tip title="See Also"
- [Effect Path Overview](effect/effect_path_overview.md) - The railway model explained in depth
- [Path Types](effect/path_types.md) - Choosing the right path for your problem
- [Cheat Sheet](cheatsheet.md) - One-page operator reference
- [Focus-Effect Integration](effect/focus_integration.md) - Combining effects with optics
~~~

---

**Next:** [Cheat Sheet](cheatsheet.md)
