# Traversal Generator Plugins

~~~admonish info title="What You'll Learn"
- Which container types `@GenerateTraversals` supports out of the box
- How to enable third-party collection support (Eclipse Collections, Guava, Vavr, Apache Commons)
- How the plugin discovery mechanism works
- How to write your own generator for a custom container type
~~~

---

## What Are Generator Plugins?

When you annotate a record with `@GenerateTraversals`, the annotation processor needs to know *how* to traverse each field's container type. A `List<String>` field requires different generated code than an `Optional<String>` or an `Either<Error, String>`.

Each container type is handled by a **generator plugin**: a small class that implements the `TraversableGenerator` SPI (Service Provider Interface). The processor discovers these plugins at compile time via Java's `ServiceLoader` and delegates code generation to whichever plugin claims support for the field's type.

Higher-Kinded-J ships 23 generator plugins covering JDK types, HKJ core types, and four popular third-party collection libraries.

---

## Supported Types at a Glance

### JDK Standard Library (Always Available)

These generators are always active. No additional dependencies are required.

| Type | Focus | Behaviour |
|------|-------|-----------|
| `List<A>` | Each element | Traverses all elements via `Traversals.traverseList()` |
| `Set<A>` | Each element | Converts to list, traverses, converts back |
| `Optional<A>` | 0 or 1 element | Applies function if present; returns unchanged if empty |
| `Map<K, V>` | Each value | Traverses values whilst preserving keys |
| `A[]` | Each element | Converts to list, traverses, converts back to array |

### HKJ Core Types (Always Available)

These types are part of `hkj-core`, which is always on your classpath.

| Type | Focus | Behaviour |
|------|-------|-----------|
| `Maybe<A>` | 0 or 1 element | Applies function to `Just`; passes through `Nothing` |
| `Either<L, R>` | Right value | Applies function to `Right`; passes through `Left` |
| `Try<A>` | Success value | Applies function to `Success`; passes through `Failure` |
| `Validated<E, A>` | Valid value | Applies function to `Valid`; passes through `Invalid` |

### Third-Party Libraries (Add to Your Dependencies)

Generator plugins for third-party libraries activate automatically when the library is on the annotation processor's classpath. Simply add the library as a dependency; no further configuration is needed.

#### Eclipse Collections

```kotlin
dependencies {
    implementation("org.eclipse.collections:eclipse-collections:13.0.0")
}
```

| Type | Notes |
|------|-------|
| `ImmutableList<A>` | |
| `ImmutableSet<A>` | |
| `ImmutableBag<A>` | |
| `ImmutableSortedSet<A>` | Preserves natural ordering |
| `MutableList<A>` | |
| `MutableSet<A>` | |
| `MutableBag<A>` | |
| `MutableSortedSet<A>` | Preserves natural ordering |

#### Google Guava

```kotlin
dependencies {
    implementation("com.google.guava:guava:33.5.0-jre")
}
```

| Type | Notes |
|------|-------|
| `ImmutableList<A>` | Uses `ImmutableList.copyOf()` for reconstruction |
| `ImmutableSet<A>` | Uses `ImmutableSet.copyOf()` for reconstruction |

#### Vavr

```kotlin
dependencies {
    implementation("io.vavr:vavr:1.0.1")
}
```

| Type | Notes |
|------|-------|
| `io.vavr.collection.List<A>` | |
| `io.vavr.collection.Set<A>` | |

#### Apache Commons Collections

```kotlin
dependencies {
    implementation("org.apache.commons:commons-collections4:4.5.0")
}
```

| Type | Notes |
|------|-------|
| `HashBag<A>` | |
| `UnmodifiableList<A>` | |

---

## Using Third-Party Types with @GenerateTraversals

Once the library is on your classpath, usage is identical to JDK types:

```java
import org.eclipse.collections.api.list.ImmutableList;
import org.higherkindedj.optics.annotation.GenerateTraversals;

@GenerateTraversals
public record Warehouse(
    String name,
    ImmutableList<String> products
) {}

// The processor generates a traversal for the 'products' field automatically.
// Use it exactly like a List traversal:
Warehouse updated = Traversals.modify(
    WarehouseTraversals.products(),
    String::toUpperCase,
    warehouse
);
```

---

## How Plugin Discovery Works

The processor uses a three-layer mechanism to discover generators:

1. **SPI Interface** -- `TraversableGenerator` in `hkj-processor` defines the contract. Any class implementing this interface can be discovered.

2. **ServiceLoader** -- At compile time, the `TraversalProcessor` calls `ServiceLoader.load(TraversableGenerator.class)` to find all registered implementations.

3. **Avaje SPI** -- Each generator class is annotated with `@ServiceProvider(TraversableGenerator.class)`. The [Avaje SPI](https://avaje.io/spi/) annotation processor automatically generates the `META-INF/services` files and validates that the `module-info.java` `provides` clause is complete. A missing entry causes a compile error with a copy-pasteable fix.

```
TraversalProcessor
    │
    ▼
ServiceLoader.load(TraversableGenerator.class)
    │
    ├── ListGenerator         (supports List<A>)
    ├── OptionalGenerator     (supports Optional<A>)
    ├── EitherGenerator       (supports Either<L, R>)
    ├── GuavaImmutableListGenerator  (supports ImmutableList<A>)
    └── ... 18 more generators
```

For each record component, the processor iterates through all loaded generators and calls `supports(TypeMirror)`. The first generator that returns `true` handles code generation for that field.

---

## Writing a Custom Generator

If your project uses a container type that is not covered by the built-in plugins, you can write your own generator and register it as a service provider.

### The TraversableGenerator Interface

```java
public interface TraversableGenerator {

    /** Return true if this generator handles the given type. */
    boolean supports(TypeMirror type);

    /**
     * Declares the cardinality of elements in this container type.
     * Used by navigator generation to determine the correct path type:
     *   ZERO_OR_ONE  → AffinePath  (Optional, Either, Try, Validated)
     *   ZERO_OR_MORE → TraversalPath (List, Map, arrays, third-party collections)
     *
     * Default is ZERO_OR_MORE, which is correct for collection-like types.
     */
    default Cardinality getCardinality() {
        return Cardinality.ZERO_OR_MORE;
    }

    /**
     * Which type argument to focus on (0-indexed).
     * Default is 0. Override to 1 for types like Either<L, R>
     * where the traversal focuses on the second argument.
     */
    default int getFocusTypeArgumentIndex() {
        return 0;
    }

    /**
     * Generate the body of the modifyF method.
     * Returns a Palantir JavaPoet CodeBlock.
     */
    CodeBlock generateModifyF(
        RecordComponentElement component,
        ClassName recordClassName,
        List<? extends RecordComponentElement> allComponents);
}
```

~~~admonish note title="Cardinality and Navigator Generation"
The `getCardinality()` method influences both `@GenerateTraversals` and `@GenerateFocus(generateNavigators = true)`. When the Focus processor generates navigator classes, it consults each SPI generator's cardinality to determine whether a field should produce an `AffinePath` (zero or one element) or a `TraversalPath` (zero or more elements). Without this, SPI-registered types would default to `FocusPath`, losing the correct widening semantics.
~~~

### Step-by-Step Example

Suppose you want to add traversal support for a custom `NonEmptyList<A>` type.

**1. Create the generator class:**

```java
package com.example.generator;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import io.avaje.spi.ServiceProvider;
import java.util.List;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import org.higherkindedj.optics.processing.generator.BaseTraversableGenerator;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;
import org.higherkindedj.optics.util.Traversals;

@ServiceProvider(TraversableGenerator.class)
public class NonEmptyListGenerator extends BaseTraversableGenerator {

    private static final String FQN = "com.example.NonEmptyList";

    @Override
    public boolean supports(final TypeMirror type) {
        if (!(type instanceof DeclaredType declaredType)) return false;
        return declaredType.asElement().toString().equals(FQN);
    }

    @Override
    public CodeBlock generateModifyF(
            final RecordComponentElement component,
            final ClassName recordClassName,
            final List<? extends RecordComponentElement> allComponents) {

        final String componentName = component.getSimpleName().toString();
        final String constructorArgs =
            generateConstructorArgs(componentName, "newNonEmptyList", allComponents);

        return CodeBlock.builder()
            // Convert to java.util.List, traverse, convert back
            .addStatement(
                "final var javaList = source.$L().toList()", componentName)
            .addStatement(
                "final var effectOfList = $T.traverseList(javaList, f, applicative)",
                Traversals.class)
            .addStatement(
                "final var effectOfNonEmptyList = applicative.map(list -> com.example.NonEmptyList.of(list), effectOfList)")
            .addStatement(
                "return applicative.map(newNonEmptyList -> new $T($L), effectOfNonEmptyList)",
                recordClassName, constructorArgs)
            .build();
    }
}
```

**2. Add the module-info.java provides clause:**

```java
module com.example.generators {
    requires org.higherkindedj.processor;
    requires com.palantir.javapoet;
    requires java.compiler;
    requires static io.avaje.spi;

    provides org.higherkindedj.optics.processing.spi.TraversableGenerator
        with com.example.generator.NonEmptyListGenerator;
}
```

**3. Add Avaje SPI to your build:**

```kotlin
dependencies {
    implementation("io.github.higher-kinded-j:hkj-processor:LATEST-VERSION")
    implementation("com.palantir.javaformat:palantir-java-format:2.50.0")
    compileOnly("io.avaje:avaje-spi-core:2.8")
    annotationProcessor("io.avaje:avaje-spi-core:2.8")
}
```

**4. Add your generator module to the annotation processor path in projects that use it:**

```kotlin
dependencies {
    annotationProcessor("com.example:my-generators:1.0.0")
}
```

The `TraversalProcessor` will now discover your `NonEmptyListGenerator` via `ServiceLoader` and generate traversals for any `NonEmptyList<A>` field.

### Implementation Tips

- **Extend `BaseTraversableGenerator`** to inherit `getGenericTypeName()` and `generateConstructorArgs()` helper methods.
- **Use fully qualified names** in `supports()` to avoid false matches with similarly named types.
- **Reuse `Traversals.traverseList()`** when your type can be converted to a `java.util.List`. Most third-party generators follow this pattern: convert to list, traverse, convert back.
- **Override `getFocusTypeArgumentIndex()`** if your type's traversal target is not the first type parameter (e.g. `Either<L, R>` focuses on index 1).
- **Override `getCardinality()`** to return `Cardinality.ZERO_OR_ONE` for optional-like types (e.g. `Either`, `Try`, `Validated`). The default `ZERO_OR_MORE` is correct for collection-like types and does not need overriding.
- **Write integration tests** using Google's compile-testing library to verify generated code compiles and contains the expected statements.

---

~~~admonish info title="Key Takeaways"
* **23 built-in generators** cover JDK types, HKJ core types, Eclipse Collections, Guava, Vavr, and Apache Commons
* **Third-party support activates automatically** when the library is on the classpath; no configuration required
* **The SPI is extensible**: implement `TraversableGenerator`, register it with `@ServiceProvider`, and the processor discovers it at compile time
* **Most generators follow a common pattern**: convert to `java.util.List`, traverse with `Traversals.traverseList()`, convert back to the original type
* **Cardinality drives navigator widening**: `ZERO_OR_ONE` produces `AffinePath`; `ZERO_OR_MORE` produces `TraversalPath` in generated navigators
~~~

~~~admonish tip title="See Also"
- [Traversals](../optics/traversals.md) - Using generated traversals in practice
- [Common Data Structures](../optics/common_data_structure_traversals.md) - Traversals for Optional, Map, and Tuple types
- [Build Plugins](gradle_plugin.md) - The build plugin adds `hkj-processor-plugins` to your annotation processor path automatically
~~~

---

**Previous:** [Diagnostics](diagnostics.md)
**Next:** [Claude Code Skills](claude_code_skills.md)
