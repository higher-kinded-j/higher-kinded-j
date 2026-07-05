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

### With HKJ Gradle Plugin (Recommended)

```gradle
// build.gradle.kts
plugins {
    id("io.github.higher-kinded-j.hkj") version "LATEST_VERSION"
}
```

This single line configures dependencies, preview features, annotation processors, `-parameters` (parameter names for copy strategies and the upcoming mapper), and compile-time Path type checking automatically. See the [Gradle Plugin](tooling/gradle_plugin.md) documentation for the full DSL reference.

For **SNAPSHOT** versions of the plugin, add the Sonatype snapshots repository to your `settings.gradle.kts`:

```gradle
// settings.gradle.kts
pluginManagement {
    repositories {
        maven {
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        }
        gradlePluginPortal()
        mavenCentral()
    }
}
```

You also need the snapshots repository in your project's `repositories` block so the plugin can resolve HKJ library dependencies:

```gradle
// build.gradle.kts
repositories {
    mavenCentral()
    maven {
        url = uri("https://central.sonatype.com/repository/maven-snapshots/")
    }
}
```

### Prefer Not to Use the Plugin?

If your project cannot apply the HKJ plugin, see [Manual Gradle and Maven Setup](tooling/manual_setup.md) for the full `build.gradle.kts` configuration.

---

## Maven Setup

### With HKJ Maven Plugin (Recommended)

```xml
<build>
    <plugins>
        <plugin>
            <groupId>io.github.higher-kinded-j</groupId>
            <artifactId>hkj-maven-plugin</artifactId>
            <version>LATEST_VERSION</version>
            <extensions>true</extensions>
        </plugin>
    </plugins>
</build>
```

The plugin automatically adds `hkj-core`, annotation processors, compile-time checks, and `--enable-preview` flags. See the [Build Plugins](tooling/gradle_plugin.md) documentation for the full configuration reference.

### Prefer Not to Use the Plugin?

If your project cannot apply the HKJ Maven plugin, see [Manual Gradle and Maven Setup](tooling/manual_setup.md) for the full `pom.xml` configuration.

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
import org.higherkindedj.hkt.Semigroups;

Semigroup<List<String>> sg = Semigroups.list();

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
