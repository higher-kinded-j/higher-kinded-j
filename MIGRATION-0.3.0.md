# Migration Guide: 0.2.x to 0.3.0

This guide explains how to migrate your Higher-Kinded-J code from version 0.2.x to 0.3.0.

## What Changed

Version 0.3.0 introduces an **embedded kind system** that adds compile-time arity checking for witness types. This catches type errors earlier and makes the type system self-documenting.

### Summary of Changes

| Component | Before (0.2.x) | After (0.3.0) |
|-----------|----------------|---------------|
| `Kind<F, A>` | No bounds | `Kind<F extends WitnessArity<?>, A>` |
| `Kind2<F, A, B>` | No bounds | `Kind2<F extends WitnessArity<TypeArity.Binary>, A, B>` |
| Type classes | `Functor<F>` | `Functor<F extends WitnessArity<TypeArity.Unary>>` |
| Witness classes | `final class Witness {}` | `final class Witness implements WitnessArity<TypeArity.Unary> {}` |

### New Types

Two new types in `org.higherkindedj.hkt`:

- **`TypeArity`** - Sealed interface representing type constructor arity
  - `TypeArity.Unary` - Single parameter types (`* -> *`) like `Maybe<A>`, `List<A>`
  - `TypeArity.Binary` - Two parameter types (`* -> * -> *`) like `Either<L, R>`

- **`WitnessArity<A extends TypeArity>`** - Marker interface that witness types implement to declare their arity

---

## Migration Options

You can migrate your code either **manually** or using **OpenRewrite** for automated refactoring.

---

## Option 1: Manual Migration

### Step 1: Update Witness Classes

Add `implements WitnessArity<TypeArity.Unary>` or `implements WitnessArity<TypeArity.Binary>` to your witness classes.

**Before:**
```java
public interface MyTypeKind<A> extends Kind<MyTypeKind.Witness, A> {
    final class Witness {
        private Witness() {}
    }
}
```

**After:**
```java
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

public interface MyTypeKind<A> extends Kind<MyTypeKind.Witness, A> {
    final class Witness implements WitnessArity<TypeArity.Unary> {
        private Witness() {}
    }
}
```

### Step 2: Update Parameterized Witnesses

For witnesses with type parameters (partial application pattern):

**Before:**
```java
public interface MyEitherKind<L, R> extends Kind<MyEitherKind.Witness<L>, R> {
    final class Witness<L> {
        private Witness() {}
    }
}
```

**After:**
```java
public interface MyEitherKind<L, R> extends Kind<MyEitherKind.Witness<L>, R> {
    final class Witness<L> implements WitnessArity<TypeArity.Unary> {
        private Witness() {}
    }
}
```

### Step 3: Update Binary Witnesses

For types extending `Kind2`:

**Before:**
```java
public interface MyPairKind2<A, B> extends Kind2<MyPairKind2.Witness, A, B> {
    final class Witness {
        private Witness() {}
    }
}
```

**After:**
```java
public interface MyPairKind2<A, B> extends Kind2<MyPairKind2.Witness, A, B> {
    final class Witness implements WitnessArity<TypeArity.Binary> {
        private Witness() {}
    }
}
```

### Step 4: Update Generic Method Signatures

If you have generic methods that accept `Kind<F, A>` with unbounded `F`:

**Before:**
```java
public <F, A, B> Kind<F, B> transform(Kind<F, A> fa, Function<A, B> f, Functor<F> functor) {
    return functor.map(f, fa);
}
```

**After:**
```java
public <F extends WitnessArity<TypeArity.Unary>, A, B> Kind<F, B> transform(
        Kind<F, A> fa, Function<A, B> f, Functor<F> functor) {
    return functor.map(f, fa);
}
```

---

## Option 2: Automated Migration with OpenRewrite

The `hkj-openrewrite` module provides recipes to automate the migration.

### Gradle Setup

Add the OpenRewrite plugin and recipe dependency to your `build.gradle.kts`:

```kotlin
plugins {
    id("org.openrewrite.rewrite") version "7.3.1"
}

dependencies {
    rewrite("io.github.higher-kinded-j:hkj-openrewrite:0.3.0")
}

rewrite {
    activeRecipe("org.higherkindedj.openrewrite.UpgradeToV2")
}
```

Run the migration:

```bash
./gradlew rewriteRun
```

### Maven Setup

Add the OpenRewrite plugin to your `pom.xml`:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.openrewrite.maven</groupId>
            <artifactId>rewrite-maven-plugin</artifactId>
            <version>5.45.0</version>
            <configuration>
                <activeRecipes>
                    <recipe>org.higherkindedj.openrewrite.UpgradeToV2</recipe>
                </activeRecipes>
            </configuration>
            <dependencies>
                <dependency>
                    <groupId>io.github.higher-kinded-j</groupId>
                    <artifactId>hkj-openrewrite</artifactId>
                    <version>0.3.0</version>
                </dependency>
            </dependencies>
        </plugin>
    </plugins>
</build>
```

Run the migration:

```bash
mvn rewrite:run
```

### Available Recipes

| Recipe | Description |
|--------|-------------|
| `org.higherkindedj.openrewrite.UpgradeToV2` | **Composite recipe** - runs all migration recipes |
| `org.higherkindedj.openrewrite.AddWitnessArityToWitness` | Adds `WitnessArity` to witness classes |
| `org.higherkindedj.openrewrite.AddArityBoundsToTypeParameters` | Adds bounds to generic type parameters |
| `org.higherkindedj.openrewrite.AddWitnessArityImports` | Adds required imports |

### Dry Run

To preview changes without applying them:

**Gradle:**
```bash
./gradlew rewriteDryRun
```

**Maven:**
```bash
mvn rewrite:dryRun
```

---

## Common Compilation Errors After Upgrade

### Error: "type argument F is not within bounds"

**Cause:** A type parameter `F` is used with `Kind<F, A>` but doesn't have the required bounds.

**Fix:** Add bounds to the type parameter:
```java
// Before
<F> void process(Kind<F, String> input)

// After
<F extends WitnessArity<?>> void process(Kind<F, String> input)

// Or more specifically for unary type classes:
<F extends WitnessArity<TypeArity.Unary>> void process(Kind<F, String> input)
```

### Error: "Witness is not a valid substitute for F"

**Cause:** A witness class doesn't implement `WitnessArity`.

**Fix:** Add the appropriate `implements` clause to the witness class.

### Error: "cannot find symbol: WitnessArity"

**Cause:** Missing import statement.

**Fix:** Add the imports:
```java
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
```

---

## Quick Reference

### Unary Types (Most Common)

Types with one type parameter like `Maybe<A>`, `List<A>`, `IO<A>`:

```java
final class Witness implements WitnessArity<TypeArity.Unary> {
    private Witness() {}
}
```

### Binary Types

Types with two type parameters used with `Kind2` like `Either<L, R>`:

```java
final class Witness implements WitnessArity<TypeArity.Binary> {
    private Witness() {}
}
```

### Partially Applied Types

Binary types used as unary (fixing one parameter) like `Either<Error, ?>`:

```java
// The parameterized witness is still Unary because it represents * -> *
final class Witness<L> implements WitnessArity<TypeArity.Unary> {
    private Witness() {}
}
```

---

## Why This Change?

The embedded kind system provides:

1. **Compile-time safety** - Errors caught at compile time, not runtime
2. **Self-documenting types** - The arity is explicit in the type signature
3. **Better IDE support** - Autocompletion and error highlighting work correctly
4. **Clearer error messages** - The compiler tells you exactly what's wrong

For more details, see the [Type Arity documentation](https://higher-kinded-j.github.io/latest/hkts/type-arity.html).

---

## Need Help?

- [GitHub Discussions](https://github.com/higher-kinded-j/higher-kinded-j/discussions) - Ask questions and get help
- [Issue Tracker](https://github.com/higher-kinded-j/higher-kinded-j/issues) - Report bugs or problems with migration
