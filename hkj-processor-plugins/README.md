# HKJ Processor Plugins

Traversal generator plugins for Higher-Kinded-J's `@GenerateTraversals` annotation processor. This module provides `TraversableGenerator` implementations that teach the processor how to generate type-safe traversal code for a wide range of collection and container types.

## How It Works

When you annotate a Java record with `@GenerateTraversals`, the `TraversalProcessor` in `hkj-processor` generates traversal optics for each record component whose type is a supported container. The processor discovers generator plugins at compile time via Java's `ServiceLoader` mechanism.

This module ships 22 generators covering JDK collections, Higher-Kinded-J core types, and four popular third-party libraries.

## Supported Types

### JDK Standard Library

| Type | Generator | Focus |
|------|-----------|-------|
| `java.util.List<A>` | `ListGenerator` | Each element |
| `java.util.Set<A>` | `SetGenerator` | Each element |
| `java.util.Optional<A>` | `OptionalGenerator` | 0 or 1 element |
| `java.util.Map<K, V>` | `MapValueGenerator` | Each value (index 1) |
| `A[]` | `ArrayGenerator` | Each element |

### Higher-Kinded-J Core Types

| Type | Generator | Focus |
|------|-----------|-------|
| `Maybe<A>` | `MaybeGenerator` | 0 or 1 element |
| `Either<L, R>` | `EitherGenerator` | Right value (index 1) |
| `Try<A>` | `TryGenerator` | Success value |
| `Validated<E, A>` | `ValidatedGenerator` | Valid value (index 1) |

### Eclipse Collections

| Type | Generator |
|------|-----------|
| `ImmutableBag<A>` | `EclipseImmutableBagGenerator` |
| `ImmutableList<A>` | `EclipseImmutableListGenerator` |
| `ImmutableSet<A>` | `EclipseImmutableSetGenerator` |
| `ImmutableSortedSet<A>` | `EclipseImmutableSortedSetGenerator` |
| `MutableBag<A>` | `EclipseMutableBagGenerator` |
| `MutableList<A>` | `EclipseMutableListGenerator` |
| `MutableSet<A>` | `EclipseMutableSetGenerator` |
| `MutableSortedSet<A>` | `EclipseMutableSortedSetGenerator` |

### Google Guava

| Type | Generator |
|------|-----------|
| `ImmutableList<A>` | `GuavaImmutableListGenerator` |
| `ImmutableSet<A>` | `GuavaImmutableSetGenerator` |

### Vavr

| Type | Generator |
|------|-----------|
| `io.vavr.collection.List<A>` | `VavrListGenerator` |
| `io.vavr.collection.Set<A>` | `VavrSetGenerator` |

### Apache Commons Collections

| Type | Generator |
|------|-----------|
| `HashBag<A>` | `ApacheHashBagGenerator` |
| `UnmodifiableList<A>` | `ApacheUnmodifiableListGenerator` |

## Architecture

```
TraversableGenerator (SPI interface in hkj-processor)
└── BaseTraversableGenerator (abstract base in this module)
    ├── JDK generators (basejdk/)
    ├── HKJ type generators (hkj/)
    ├── EclipseBaseSingleIterableTraversableGenerator
    │   └── Eclipse Collections generators (eclipse/)
    ├── EclipseBaseSortedSetTraversableGenerator
    │   └── Eclipse sorted set generators (eclipse/)
    ├── GuavaBaseSingleIterableTraversableGenerator
    │   └── Guava generators (guava/)
    ├── ApacheBaseSingleIterableTraversableGenerator
    │   └── Apache generators (apache/)
    └── VavrBaseSingleIterableTraversableGenerator
        └── Vavr generators (vavr/)
```

### Key Components

- **`TraversableGenerator`** (SPI) -- The interface in `hkj-processor` that all generators implement. Defines three methods: `supports(TypeMirror)`, `getFocusTypeArgumentIndex()`, and `generateModifyF(...)`.
- **`BaseTraversableGenerator`** -- Abstract base class providing helper methods for extracting generic type names and generating record constructor arguments.
- **`@ServiceProvider`** -- Each concrete generator uses [Avaje SPI](https://avaje.io/spi/) to automatically generate `META-INF/services` entries and validate the `module-info.java` `provides` clause at compile time.

## Usage

If you use the HKJ Gradle or Maven build plugin, `hkj-processor-plugins` is added to your annotation processor path automatically. No additional configuration is needed for JDK types and HKJ core types.

For third-party library support (Eclipse Collections, Guava, Vavr, Apache Commons), simply add the library to your project's dependencies. The generators detect supported types at compile time regardless of whether the library is present at runtime.

### Manual Setup

If not using the build plugin, add the processor plugins to your annotation processor path:

**Gradle:**
```kotlin
dependencies {
    annotationProcessor("io.github.higher-kinded-j:hkj-processor-plugins:LATEST-VERSION")
}
```

**Maven:**
```xml
<annotationProcessorPaths>
    <path>
        <groupId>io.github.higher-kinded-j</groupId>
        <artifactId>hkj-processor-plugins</artifactId>
        <version>LATEST-VERSION</version>
    </path>
</annotationProcessorPaths>
```

## Writing a Custom Generator

To add support for a new container type, implement `TraversableGenerator` and register it as a service provider. See the [Traversal Generator Plugins](https://higher-kinded-j.github.io/higher-kinded-j/tooling/generator_plugins.html) page in the HKJ Book for a complete guide.

## Testing

Each generator has integration tests using the [compile-testing](https://github.com/google/compile-testing) library. Tests compile source records annotated with `@GenerateTraversals` and assert that the generated code contains the expected traversal logic.

Run tests with:

```bash
./gradlew :hkj-processor-plugins:test
```

## License

[MIT License](../LICENSE.md)
