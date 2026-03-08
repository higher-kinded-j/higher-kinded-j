# Compile-Time Checks: Catching Path Mismatches Early

~~~admonish info title="What You'll Learn"
- Why Path type mismatches are dangerous and hard to catch in tests
- How the HKJ compile-time checker detects mismatches before runtime
- Which methods and Path types the checker covers
- Known limitations and the no-false-positives policy
~~~

---

## The Problem

Each Path type can only chain with the same type. This code compiles without errors:

```java
MaybePath<Integer> result = Path.just(1)
    .via(n -> Path.io(() -> n + 1));    // returns IOPath, not MaybePath
```

But at runtime it throws `IllegalArgumentException`:

```
java.lang.IllegalArgumentException: Type mismatch in via():
    expected MaybePath but mapper returned IOPath.
    Each Path type can only chain with the same type.
    Use conversion methods (toEitherPath, toMaybePath, etc.) to change types.
```

This class of bug is particularly insidious because:

- The code compiles successfully
- It passes type checking (generics erasure hides the mismatch)
- It only fails at runtime when the specific code path executes
- In branching logic, the mismatch may lurk in a rarely-tested branch

---

## The Solution

The HKJ checker is a javac compiler plugin that detects Path type mismatches during compilation. With the Gradle plugin, it is enabled by default:

```gradle
plugins {
    id("io.github.higher-kinded-j.hkj") version "0.3.7-SNAPSHOT"
}
```

Now the same code produces a compile-time error:

```
error: Path type mismatch in via(): expected MaybePath but received IOPath.
    Each Path type can only chain with the same type.
    Use conversion methods (toEitherPath, toMaybePath, etc.) to change types.
    .via(n -> Path.io(() -> n + 1));
                   ^
```

The fix is to convert at the boundary:

```java
MaybePath<Integer> result = Path.just(1)
    .via(n -> Path.io(() -> n + 1).toMaybePath());    // explicit conversion
```

---

## What the Checker Detects

The checker inspects calls to Path composition methods and verifies that the receiver and argument resolve to the same Path family.

| Method | What Is Checked |
|--------|----------------|
| `via(Function)` | Return type of the function matches the receiver's Path type |
| `then(Supplier)` | Return type of the supplier matches the receiver's Path type |
| `zipWith(Combinable, BiFunction)` | Type of the first argument matches the receiver's Path type |
| `zipWith3(Combinable, Combinable, TriFunction)` | Types of both `Combinable` arguments match the receiver |
| `recoverWith(Function)` | Return type of the recovery function matches the receiver's Path type |
| `orElse(Supplier)` | Return type of the supplier matches the receiver's Path type |

---

## How It Works

The checker is a standard `com.sun.source.util.Plugin` that hooks into the Java compiler's `ANALYZE` phase:

1. **Registration** -- The `HKJCheckerPlugin` registers a `TaskListener` that fires after type attribution is complete
2. **Tree scanning** -- A `TreeScanner` visits every method invocation in the compilation unit
3. **Method matching** -- The scanner identifies calls to `via`, `then`, `zipWith`, `recoverWith`, and `orElse`
4. **Type resolution** -- Using the compiler's type information, it resolves the concrete Path types of the receiver and argument
5. **Family comparison** -- If both types are concrete Path types, it checks they belong to the same family
6. **Diagnostic reporting** -- Mismatches are reported as compiler errors at the exact source location

The checker runs as part of normal compilation. There is no separate build step or additional tool to invoke.

---

## Configuration

### Enabling (Default)

The Gradle plugin enables the checker by default. No configuration is needed:

```gradle
plugins {
    id("io.github.higher-kinded-j.hkj") version "0.3.7-SNAPSHOT"
}
```

### Disabling

To disable compile-time checks:

```gradle
hkj {
    checks {
        pathTypeMismatch = false
    }
}
```

### Maven Plugin

The Maven plugin also enables the checker by default:

```xml
<plugin>
    <groupId>io.github.higher-kinded-j</groupId>
    <artifactId>hkj-maven-plugin</artifactId>
    <version>0.3.7-SNAPSHOT</version>
    <extensions>true</extensions>
</plugin>
```

To disable:

```xml
<configuration>
    <pathTypeMismatch>false</pathTypeMismatch>
</configuration>
```

### Manual Setup (Without Build Plugins)

**Gradle:**

```gradle
dependencies {
    annotationProcessor("io.github.higher-kinded-j:hkj-checker:0.3.7-SNAPSHOT")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xplugin:HKJChecker")
}
```

**Maven:**

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
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
```

---

## Limitations

The checker follows a strict **no-false-positives policy**. It is better to miss a real mismatch than to report a false error.

~~~admonish warning title="Cases the Checker Cannot Detect"
- **Generic type parameters** -- When a method returns `T` where `T` extends a Path type, the concrete type may not be resolvable at compile time
- **Complex lambda inference** -- Deeply nested lambdas or method references with ambiguous return types may prevent type resolution
- **Indirect construction** -- Path instances created through helper methods, factories, or dependency injection are not tracked
- **Runtime polymorphism** -- When the Path type is determined by a runtime condition (e.g., a method that returns different Path types based on input)
~~~

In these cases, the checker silently skips the check. Runtime type checking in the Path implementations provides a safety net for cases the compiler cannot catch.

---

~~~admonish info title="Key Takeaways"
* **Path type mismatches** compile successfully but fail at runtime, making them hard to catch in testing
* **The HKJ checker** is a javac plugin that detects these mismatches during compilation
* **Enabled by default** when using the HKJ Gradle or Maven plugin; no configuration needed
* **No false positives** -- the checker only reports errors it is certain about
* **Runtime checks** in the Path implementations catch anything the compile-time checker misses
~~~

~~~admonish tip title="See Also"
- [Build Plugins](gradle_plugin.md) - Plugin setup and configuration
- [Type Conversions](../effect/conversions.md) - How to convert between Path types
- [Common Compiler Errors](../effect/compiler_errors.md) - Other compile-time issues and fixes
~~~

---

**Previous:** [Build Plugins](gradle_plugin.md)
**Next:** [Diagnostics](diagnostics.md)
