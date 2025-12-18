# Effect Path API - Phase 3 Implementation Plan

> **Status**: Implementation Complete (v1.1)
> **Last Updated**: 2025-12-16
> **Phase 2 Completion**: Complete
> **Phase 3 Completion**: Core implementation complete, some tests outstanding
> **Java Baseline**: Java 25 (RELEASE_25)
> **Branch**: claude/phase3-implementation-review-yFCyW

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Phase 3 Scope Overview](#phase-3-scope-overview)
3. [Sub-Phase Organisation](#sub-phase-organisation)
4. [Phase 3a: Extensibility](#phase-3a-extensibility)
5. [Phase 3b: Advanced Effects](#phase-3b-advanced-effects)
6. [Phase 3c: Java Stdlib & Collections](#phase-3c-java-stdlib--collections)
7. [Testing Strategy](#testing-strategy)
8. [Documentation Requirements](#documentation-requirements)
9. [Success Criteria](#success-criteria)
10. [Phase 4 Forward Look](#phase-4-forward-look)

---

## Executive Summary

Phase 3 builds upon the Phase 1 and Phase 2 foundations to provide:

| Category | Components |
|----------|------------|
| **Extensibility** | @PathSource, @PathConfig annotations, PathProvider SPI, PathRegistry |
| **GenericPath Enhancements** | MonadError support, Natural transformations (mapK) |
| **Advanced Effect Paths** | ReaderPath, StatePath, WriterPath, LazyPath |
| **Java Stdlib Bridges** | CompletableFuturePath, ListPath, StreamPath |
| **Traverse Support** | Concrete traverse overloads for all path types |

### Phase Dependencies

```
Phase 1 (Complete)          Phase 2 (Complete)           Phase 3
─────────────────           ──────────────────           ───────
MaybePath                   ValidationPath               @PathSource
EitherPath          ───►    IdPath               ───►    PathProvider SPI
TryPath                     OptionalPath                 ReaderPath
IOPath                      GenericPath                  StatePath
Capability interfaces       @GeneratePathBridge          WriterPath
                            Accumulating                 LazyPath
                            PathOps                      CompletableFuturePath
                                                         ListPath
                                                         StreamPath
```

---

## Phase 3 Scope Overview

### New Path Types

| Path Type | Underlying Type | Capabilities | Priority |
|-----------|-----------------|--------------|----------|
| **ReaderPath<R, A>** | Reader<R, A> | Chainable, Composable | High |
| **StatePath<S, A>** | State<S, A> | Chainable, Composable | High |
| **WriterPath<W, A>** | Writer<W, A> | Chainable, Composable, Accumulating | High |
| **LazyPath<A>** | Lazy<A> | Chainable, Composable | High |
| **CompletableFuturePath<A>** | CompletableFuture<A> | Chainable, Recoverable, Effectful | High |
| **ListPath<A>** | List<A> | Chainable, Composable, Foldable | Medium |
| **StreamPath<A>** | Stream<A> | Chainable, Composable, Foldable | Medium |

### New Annotations

| Annotation | Target | Purpose |
|------------|--------|---------|
| **@PathSource** | Type | Generate custom Path wrapper for effect type |
| **@PathConfig** | Package | Global configuration for Path generation |

### New Infrastructure

| Component | Purpose |
|-----------|---------|
| **PathProvider<F>** | SPI for providing Path instances for custom types |
| **PathRegistry** | Central registry for PathProvider discovery |
| **NaturalTransformation<F, G>** | Transform between monad types |

---

## Sub-Phase Organisation

Phase 3 is organised into three sub-phases that can be worked on with some parallelism:

```
Phase 3a: Extensibility          Phase 3b: Advanced Effects       Phase 3c: Stdlib & Collections
────────────────────────         ─────────────────────────        ─────────────────────────────
@PathSource annotation           ReaderPath                       CompletableFuturePath
@PathConfig annotation           StatePath                        ListPath
PathProvider SPI                 WriterPath                       StreamPath
PathRegistry                     LazyPath                         Concrete traverse overloads
MonadError for GenericPath
Natural transformations (mapK)

         │                                │                                │
         └────────────────────────────────┼────────────────────────────────┘
                                          ▼
                              Testing, Examples, Documentation
```

### Sub-Phase Dependencies

| Sub-Phase | Dependencies | Can Start After |
|-----------|--------------|-----------------|
| **3a** | Phase 2 complete | Phase 2 |
| **3b** | Phase 2 complete | Phase 2 (parallel with 3a) |
| **3c** | Phase 2 complete | Phase 2 (parallel with 3a, 3b) |

---

## Phase 3a: Extensibility

### A1: @PathSource Annotation

**Purpose**: Generate custom Path types for library authors

**File**: `hkj-annotations/src/main/java/org/higherkindedj/hkt/effect/annotation/PathSource.java`

```java
package org.higherkindedj.hkt.effect.annotation;

import java.lang.annotation.*;

/**
 * Generates a Path wrapper for a custom effect type.
 *
 * <p>When applied to a type that has a Monad instance, this annotation
 * causes the annotation processor to generate a corresponding Path class
 * with fluent composition methods.
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * @PathSource(
 *     witness = ApiResultKind.Witness.class,
 *     errorType = ApiError.class
 * )
 * public sealed interface ApiResult<A> permits ApiSuccess, ApiFailure {
 *     <B> ApiResult<B> map(Function<? super A, ? extends B> f);
 *     <B> ApiResult<B> flatMap(Function<? super A, ? extends ApiResult<B>> f);
 * }
 * }</pre>
 *
 * <p>This generates {@code ApiResultPath<A>} implementing {@code Recoverable<ApiError, A>}.
 *
 * @see PathConfig
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface PathSource {

    /**
     * The HKT witness class for this type.
     * Must be a class with a {@code Witness} inner type marker.
     */
    Class<?> witness();

    /**
     * The error type if this effect supports error handling.
     * If specified, the generated Path will implement {@link Recoverable}.
     * If not specified, defaults to {@code Void.class} (no error recovery).
     */
    Class<?> errorType() default Void.class;

    /**
     * Custom name for the generated Path class.
     * Defaults to {@code {TypeName}Path}.
     */
    String pathClassName() default "";

    /**
     * Which capability interfaces to implement.
     * Defaults to inferring from the type's available operations.
     */
    Capability[] capabilities() default {};

    /**
     * Capabilities that can be implemented by generated Path types.
     */
    enum Capability {
        COMPOSABLE,
        COMBINABLE,
        CHAINABLE,
        RECOVERABLE,
        EFFECTFUL,
        ACCUMULATING
    }
}
```

**Tests**: `PathSourceProcessorTest.java`

### A2: @PathConfig Annotation

**Purpose**: Global configuration for Path code generation

**File**: `hkj-annotations/src/main/java/org/higherkindedj/hkt/effect/annotation/PathConfig.java`

```java
package org.higherkindedj.hkt.effect.annotation;

import java.lang.annotation.*;

/**
 * Global configuration for Path code generation.
 *
 * <p>Apply this annotation to a package-info.java to configure
 * default settings for all @PathSource annotations in that package.
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // In package-info.java
 * @PathConfig(
 *     generateToString = true,
 *     generateEquals = true,
 *     pathSuffix = "Path"
 * )
 * package com.example.effects;
 * }</pre>
 */
@Target(ElementType.PACKAGE)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface PathConfig {

    /**
     * Whether to generate toString() methods.
     * Default: true
     */
    boolean generateToString() default true;

    /**
     * Whether to generate equals() and hashCode() methods.
     * Default: true
     */
    boolean generateEquals() default true;

    /**
     * Suffix for generated Path class names.
     * Default: "Path"
     */
    String pathSuffix() default "Path";

    /**
     * Whether to generate factory methods in the Path class.
     * Default: true
     */
    boolean generateFactories() default true;

    /**
     * Whether to generate conversion methods to other Path types.
     * Default: true
     */
    boolean generateConversions() default true;
}
```

### A3: PathProvider SPI

**Purpose**: Service Provider Interface for automatic path resolution

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/effect/spi/PathProvider.java`

```java
package org.higherkindedj.hkt.effect.spi;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.typeclass.Monad;
import org.higherkindedj.hkt.typeclass.MonadError;

import java.util.Optional;

/**
 * Service Provider Interface for creating Path instances from Kind values.
 *
 * <p>Implement this interface to provide Path support for custom effect types.
 * Implementations are discovered via {@link java.util.ServiceLoader}.
 *
 * <h2>Implementation Example</h2>
 * <pre>{@code
 * public class ApiResultPathProvider implements PathProvider<ApiResultKind.Witness> {
 *
 *     @Override
 *     public Class<?> witnessType() {
 *         return ApiResultKind.Witness.class;
 *     }
 *
 *     @Override
 *     public <A> Chainable<A> createPath(Kind<ApiResultKind.Witness, A> value) {
 *         return ApiResultPath.of(ApiResultKind.narrow(value));
 *     }
 *
 *     @Override
 *     public Monad<ApiResultKind.Witness> monad() {
 *         return ApiResultMonad.INSTANCE;
 *     }
 *
 *     @Override
 *     public Optional<MonadError<ApiResultKind.Witness, ?>> monadError() {
 *         return Optional.of(ApiResultMonad.INSTANCE);
 *     }
 * }
 * }</pre>
 *
 * <h2>Registration</h2>
 * <p>Register in {@code META-INF/services/org.higherkindedj.hkt.effect.spi.PathProvider}:
 * <pre>
 * com.example.ApiResultPathProvider
 * </pre>
 *
 * @param <F> the witness type of the effect
 */
public interface PathProvider<F> {

    /**
     * Returns the witness type class this provider handles.
     */
    Class<?> witnessType();

    /**
     * Creates a Path instance from a Kind value.
     *
     * @param value the Kind value to wrap
     * @param <A> the value type
     * @return a Chainable path wrapping the value
     */
    <A> Chainable<A> createPath(Kind<F, A> value);

    /**
     * Returns the Monad instance for this effect type.
     */
    Monad<F> monad();

    /**
     * Returns the MonadError instance if this effect supports error handling.
     *
     * @return Optional containing MonadError, or empty if not supported
     */
    Optional<MonadError<F, ?>> monadError();

    /**
     * Returns a human-readable name for this provider.
     * Used in error messages and debugging.
     */
    default String name() {
        return witnessType().getSimpleName() + "PathProvider";
    }
}
```

**Tests**: `PathProviderTest.java`

### A4: PathRegistry

**Purpose**: Central registry for PathProvider discovery and lookup

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/effect/spi/PathRegistry.java`

```java
package org.higherkindedj.hkt.effect.spi;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.effect.capability.Chainable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for PathProvider instances.
 *
 * <p>Provides automatic discovery and lookup of PathProviders via ServiceLoader,
 * enabling {@code Path.from(kind)} to work with any registered effect type.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Automatic path creation from any registered type
 * Kind<ApiResultKind.Witness, User> kind = apiService.getUser(id);
 * Optional<Chainable<User>> path = PathRegistry.createPath(kind, ApiResultKind.Witness.class);
 *
 * // Or with the Path factory
 * Chainable<User> path = Path.from(kind, ApiResultKind.Witness.class);
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe. Providers are loaded lazily on first access.
 */
public final class PathRegistry {

    private static final Map<Class<?>, PathProvider<?>> providers = new ConcurrentHashMap<>();
    private static volatile boolean loaded = false;

    private PathRegistry() {}

    /**
     * Creates a Path from a Kind value using the appropriate registered provider.
     *
     * @param value the Kind value to wrap
     * @param witnessType the witness type class
     * @param <F> the witness type
     * @param <A> the value type
     * @return Optional containing the created path, or empty if no provider found
     */
    public static <F, A> Optional<Chainable<A>> createPath(
            Kind<F, A> value,
            Class<?> witnessType) {
        ensureLoaded();
        @SuppressWarnings("unchecked")
        PathProvider<F> provider = (PathProvider<F>) providers.get(witnessType);
        if (provider == null) {
            return Optional.empty();
        }
        return Optional.of(provider.createPath(value));
    }

    /**
     * Returns the provider for a given witness type, if registered.
     */
    public static Optional<PathProvider<?>> getProvider(Class<?> witnessType) {
        ensureLoaded();
        return Optional.ofNullable(providers.get(witnessType));
    }

    /**
     * Registers a provider manually.
     * Useful for testing or when ServiceLoader is not available.
     */
    public static void register(PathProvider<?> provider) {
        providers.put(provider.witnessType(), provider);
    }

    /**
     * Returns all registered providers.
     */
    public static Collection<PathProvider<?>> allProviders() {
        ensureLoaded();
        return Collections.unmodifiableCollection(providers.values());
    }

    /**
     * Clears all registered providers.
     * Primarily for testing purposes.
     */
    public static void clear() {
        providers.clear();
        loaded = false;
    }

    private static void ensureLoaded() {
        if (!loaded) {
            synchronized (PathRegistry.class) {
                if (!loaded) {
                    loadProviders();
                    loaded = true;
                }
            }
        }
    }

    private static void loadProviders() {
        ServiceLoader<PathProvider> loader = ServiceLoader.load(PathProvider.class);
        for (PathProvider<?> provider : loader) {
            providers.put(provider.witnessType(), provider);
        }
    }
}
```

**Tests**: `PathRegistryTest.java`

### A5: MonadError Support for GenericPath

**Purpose**: Enable recovery operations on GenericPath when MonadError is available

**File**: Update `hkj-core/src/main/java/org/higherkindedj/hkt/effect/GenericPath.java`

**Changes**:
```java
public final class GenericPath<F, A> implements Chainable<A> {

    private final Kind<F, A> value;
    private final Monad<F> monad;
    private final @Nullable MonadError<F, ?> monadError;  // NEW

    // New constructor accepting MonadError
    private GenericPath(Kind<F, A> value, Monad<F> monad, @Nullable MonadError<F, ?> monadError) {
        this.value = Objects.requireNonNull(value, "value must not be null");
        this.monad = Objects.requireNonNull(monad, "monad must not be null");
        this.monadError = monadError;
    }

    // New factory with MonadError
    public static <F, E, A> GenericPath<F, A> of(
            Kind<F, A> value,
            MonadError<F, E> monadError) {
        return new GenericPath<>(value, monadError, monadError);
    }

    /**
     * Returns whether this GenericPath supports error recovery.
     */
    public boolean supportsRecovery() {
        return monadError != null;
    }

    /**
     * Recovers from errors using the provided recovery function.
     *
     * @param recovery function to recover from errors
     * @param <E> the error type
     * @return a new GenericPath with recovery applied
     * @throws UnsupportedOperationException if MonadError was not provided
     */
    @SuppressWarnings("unchecked")
    public <E> GenericPath<F, A> recover(Function<? super E, ? extends A> recovery) {
        if (monadError == null) {
            throw new UnsupportedOperationException(
                "GenericPath does not support recovery. " +
                "Use GenericPath.of(value, monadError) to enable recovery.");
        }
        MonadError<F, E> typedMonadError = (MonadError<F, E>) monadError;
        Kind<F, A> recovered = typedMonadError.handleErrorWith(
            value,
            e -> monad.of(recovery.apply(e))
        );
        return new GenericPath<>(recovered, monad, monadError);
    }

    /**
     * Maps over the error type.
     *
     * @param mapper function to transform errors
     * @param <E> the original error type
     * @param <E2> the new error type (must be same due to MonadError constraint)
     * @return a new GenericPath with mapped errors
     */
    @SuppressWarnings("unchecked")
    public <E> GenericPath<F, A> mapError(Function<? super E, ? extends E> mapper) {
        if (monadError == null) {
            throw new UnsupportedOperationException(
                "GenericPath does not support mapError. " +
                "Use GenericPath.of(value, monadError) to enable error operations.");
        }
        MonadError<F, E> typedMonadError = (MonadError<F, E>) monadError;
        Kind<F, A> mapped = typedMonadError.handleErrorWith(
            value,
            e -> typedMonadError.raiseError(mapper.apply(e))
        );
        return new GenericPath<>(mapped, monad, monadError);
    }
}
```

**Tests**: `GenericPathMonadErrorTest.java`

### A6: Natural Transformations

**Purpose**: Transform between different monad types

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/effect/NaturalTransformation.java`

```java
package org.higherkindedj.hkt.effect;

import org.higherkindedj.hkt.Kind;

/**
 * A natural transformation from functor F to functor G.
 *
 * <p>Natural transformations allow converting computations from one
 * effect type to another while preserving structure. This is the
 * categorical concept of a morphism between functors.
 *
 * <h2>Laws</h2>
 * <p>For a natural transformation {@code nt: F ~> G}, the following must hold:
 * <pre>
 * nt.apply(fa.map(f)) == nt.apply(fa).map(f)
 * </pre>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // Convert Maybe to Either
 * NaturalTransformation<MaybeKind.Witness, EitherKind.Witness<String>> maybeToEither =
 *     new NaturalTransformation<>() {
 *         @Override
 *         public <A> Kind<EitherKind.Witness<String>, A> apply(Kind<MaybeKind.Witness, A> fa) {
 *             Maybe<A> maybe = MaybeKindHelper.narrow(fa);
 *             return EitherKind.widen(maybe.toEither("Value was Nothing"));
 *         }
 *     };
 * }</pre>
 *
 * @param <F> the source witness type
 * @param <G> the target witness type
 */
@FunctionalInterface
public interface NaturalTransformation<F, G> {

    /**
     * Applies this natural transformation to a Kind value.
     *
     * @param fa the source Kind value
     * @param <A> the value type
     * @return the transformed Kind value
     */
    <A> Kind<G, A> apply(Kind<F, A> fa);

    /**
     * Composes this natural transformation with another.
     *
     * @param after the transformation to apply after this one
     * @param <H> the final target witness type
     * @return a composed natural transformation
     */
    default <H> NaturalTransformation<F, H> andThen(NaturalTransformation<G, H> after) {
        return new NaturalTransformation<>() {
            @Override
            public <A> Kind<H, A> apply(Kind<F, A> fa) {
                return after.apply(NaturalTransformation.this.apply(fa));
            }
        };
    }

    /**
     * Returns the identity natural transformation.
     */
    static <F> NaturalTransformation<F, F> identity() {
        return new NaturalTransformation<>() {
            @Override
            public <A> Kind<F, A> apply(Kind<F, A> fa) {
                return fa;
            }
        };
    }
}
```

**GenericPath.mapK method**:
```java
/**
 * Transforms this GenericPath to a different monad type using a natural transformation.
 *
 * @param transform the natural transformation to apply
 * @param targetMonad the Monad instance for the target type
 * @param <G> the target witness type
 * @return a new GenericPath in the target monad
 */
public <G> GenericPath<G, A> mapK(
        NaturalTransformation<F, G> transform,
        Monad<G> targetMonad) {
    return new GenericPath<>(transform.apply(value), targetMonad, null);
}

/**
 * Transforms this GenericPath to a different monad type, preserving error handling.
 */
public <G, E> GenericPath<G, A> mapK(
        NaturalTransformation<F, G> transform,
        MonadError<G, E> targetMonadError) {
    return new GenericPath<>(transform.apply(value), targetMonadError, targetMonadError);
}
```

**Tests**: `NaturalTransformationTest.java`, `GenericPathMapKTest.java`

---

## Phase 3b: Advanced Effects

### B1: ReaderPath<R, A>

**Purpose**: Environment-dependent computations (dependency injection pattern)

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/effect/ReaderPath.java`

```java
package org.higherkindedj.hkt.effect;

import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.effect.capability.Composable;
import org.higherkindedj.hkt.reader.Reader;

import java.util.Objects;
import java.util.function.Function;

/**
 * A Path wrapper for {@link Reader} computations.
 *
 * <p>ReaderPath represents computations that depend on some environment {@code R}
 * to produce a value {@code A}. This is the functional programming approach to
 * dependency injection.
 *
 * <h2>Use Cases</h2>
 * <ul>
 *   <li>Dependency injection without frameworks</li>
 *   <li>Configuration access throughout computation</li>
 *   <li>Database connection passing</li>
 *   <li>Logger or context propagation</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // Define environment
 * record AppConfig(String dbUrl, int timeout) {}
 *
 * // Build computation
 * ReaderPath<AppConfig, String> getDbUrl = ReaderPath.asks(AppConfig::dbUrl);
 * ReaderPath<AppConfig, Connection> connect = getDbUrl
 *     .via(url -> ReaderPath.asks(cfg -> DriverManager.getConnection(url)));
 *
 * // Run with environment
 * Connection conn = connect.run(new AppConfig("jdbc:...", 30));
 * }</pre>
 *
 * @param <R> the environment type
 * @param <A> the result type
 */
public final class ReaderPath<R, A> implements Chainable<A> {

    private final Reader<R, A> reader;

    private ReaderPath(Reader<R, A> reader) {
        this.reader = Objects.requireNonNull(reader, "reader must not be null");
    }

    // ===== Factory Methods =====

    /**
     * Creates a ReaderPath from a Reader.
     */
    public static <R, A> ReaderPath<R, A> of(Reader<R, A> reader) {
        return new ReaderPath<>(reader);
    }

    /**
     * Creates a ReaderPath that returns a constant value.
     */
    public static <R, A> ReaderPath<R, A> pure(A value) {
        return new ReaderPath<>(Reader.of(value));
    }

    /**
     * Creates a ReaderPath that extracts from the environment.
     */
    public static <R, A> ReaderPath<R, A> asks(Function<? super R, ? extends A> f) {
        return new ReaderPath<>(Reader.asks(f));
    }

    /**
     * Creates a ReaderPath that returns the entire environment.
     */
    public static <R> ReaderPath<R, R> ask() {
        return new ReaderPath<>(Reader.ask());
    }

    // ===== Terminal Operations =====

    /**
     * Runs this computation with the given environment.
     */
    public A run(R environment) {
        return reader.run(environment);
    }

    /**
     * Returns the underlying Reader.
     */
    public Reader<R, A> toReader() {
        return reader;
    }

    // ===== Composable Implementation =====

    @Override
    public <B> ReaderPath<R, B> map(Function<? super A, ? extends B> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        return new ReaderPath<>(reader.map(mapper));
    }

    @Override
    public ReaderPath<R, A> peek(java.util.function.Consumer<? super A> consumer) {
        Objects.requireNonNull(consumer, "consumer must not be null");
        return map(a -> {
            consumer.accept(a);
            return a;
        });
    }

    // ===== Chainable Implementation =====

    @Override
    @SuppressWarnings("unchecked")
    public <B> ReaderPath<R, B> via(Function<? super A, ? extends Chainable<B>> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        Reader<R, B> flatMapped = reader.flatMap(a -> {
            Chainable<B> result = mapper.apply(a);
            if (result instanceof ReaderPath<?, ?> rp) {
                return ((ReaderPath<R, B>) rp).reader;
            }
            throw new IllegalArgumentException(
                "ReaderPath.via must return ReaderPath. Got: " + result.getClass());
        });
        return new ReaderPath<>(flatMapped);
    }

    @Override
    public <B> ReaderPath<R, B> then(java.util.function.Supplier<? extends Chainable<B>> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        return via(_ -> supplier.get());
    }

    // ===== Reader-Specific Operations =====

    /**
     * Modifies the environment before running this computation.
     */
    public <R2> ReaderPath<R2, A> local(Function<? super R2, ? extends R> f) {
        return new ReaderPath<>(reader.local(f));
    }

    /**
     * Combines with another ReaderPath using a combining function.
     */
    @SuppressWarnings("unchecked")
    public <B, C> ReaderPath<R, C> zipWith(
            ReaderPath<R, B> other,
            java.util.function.BiFunction<? super A, ? super B, ? extends C> combiner) {
        Objects.requireNonNull(other, "other must not be null");
        Objects.requireNonNull(combiner, "combiner must not be null");
        return via(a -> other.map(b -> combiner.apply(a, b)));
    }

    // ===== Conversions =====

    /**
     * Converts to IOPath by providing the environment.
     */
    public IOPath<A> toIOPath(R environment) {
        return IOPath.delay(() -> run(environment));
    }

    /**
     * Converts to GenericPath.
     */
    public GenericPath<?, A> toGenericPath() {
        // Implementation depends on ReaderKind structure
        throw new UnsupportedOperationException("Not yet implemented");
    }

    // ===== Object Methods =====

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ReaderPath<?, ?> other)) return false;
        return reader.equals(other.reader);
    }

    @Override
    public int hashCode() {
        return reader.hashCode();
    }

    @Override
    public String toString() {
        return "ReaderPath(" + reader + ")";
    }
}
```

**Tests**: `ReaderPathTest.java`, `ReaderPathPropertyTest.java`

### B2: StatePath<S, A>

**Purpose**: Stateful computations

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/effect/StatePath.java`

```java
package org.higherkindedj.hkt.effect;

import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.state.State;
import org.higherkindedj.hkt.tuple.Tuple2;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * A Path wrapper for {@link State} computations.
 *
 * <p>StatePath represents computations that thread state through a sequence
 * of operations. Each step can read and modify the state.
 *
 * <h2>Use Cases</h2>
 * <ul>
 *   <li>Stateful parsers</li>
 *   <li>Random number generation</li>
 *   <li>Counter or ID generation</li>
 *   <li>Building data structures</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // Counter state
 * StatePath<Integer, Integer> nextId = StatePath.modify(n -> n + 1)
 *     .then(() -> StatePath.get());
 *
 * // Generate multiple IDs
 * StatePath<Integer, List<Integer>> threeIds = nextId
 *     .via(id1 -> nextId.via(id2 -> nextId.map(id3 -> List.of(id1, id2, id3))));
 *
 * // Run starting from 0
 * Tuple2<Integer, List<Integer>> result = threeIds.run(0);
 * // result = (3, [1, 2, 3])
 * }</pre>
 *
 * @param <S> the state type
 * @param <A> the result type
 */
public final class StatePath<S, A> implements Chainable<A> {

    private final State<S, A> state;

    private StatePath(State<S, A> state) {
        this.state = Objects.requireNonNull(state, "state must not be null");
    }

    // ===== Factory Methods =====

    /**
     * Creates a StatePath from a State.
     */
    public static <S, A> StatePath<S, A> of(State<S, A> state) {
        return new StatePath<>(state);
    }

    /**
     * Creates a StatePath that returns a constant value without modifying state.
     */
    public static <S, A> StatePath<S, A> pure(A value) {
        return new StatePath<>(State.of(value));
    }

    /**
     * Creates a StatePath that returns the current state.
     */
    public static <S> StatePath<S, S> get() {
        return new StatePath<>(State.get());
    }

    /**
     * Creates a StatePath that sets the state and returns unit.
     */
    public static <S> StatePath<S, Void> set(S newState) {
        return new StatePath<>(State.set(newState).map(_ -> null));
    }

    /**
     * Creates a StatePath that modifies the state and returns unit.
     */
    public static <S> StatePath<S, Void> modify(UnaryOperator<S> f) {
        return new StatePath<>(State.modify(f).map(_ -> null));
    }

    /**
     * Creates a StatePath that extracts a value from the state.
     */
    public static <S, A> StatePath<S, A> inspect(Function<? super S, ? extends A> f) {
        return new StatePath<>(State.inspect(f));
    }

    // ===== Terminal Operations =====

    /**
     * Runs this computation with the given initial state.
     * Returns both the final state and the result.
     */
    public Tuple2<S, A> run(S initialState) {
        return state.run(initialState);
    }

    /**
     * Runs and returns only the result, discarding final state.
     */
    public A evalState(S initialState) {
        return run(initialState).second();
    }

    /**
     * Runs and returns only the final state, discarding result.
     */
    public S execState(S initialState) {
        return run(initialState).first();
    }

    /**
     * Returns the underlying State.
     */
    public State<S, A> toState() {
        return state;
    }

    // ===== Composable Implementation =====

    @Override
    public <B> StatePath<S, B> map(Function<? super A, ? extends B> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        return new StatePath<>(state.map(mapper));
    }

    @Override
    public StatePath<S, A> peek(java.util.function.Consumer<? super A> consumer) {
        Objects.requireNonNull(consumer, "consumer must not be null");
        return map(a -> {
            consumer.accept(a);
            return a;
        });
    }

    // ===== Chainable Implementation =====

    @Override
    @SuppressWarnings("unchecked")
    public <B> StatePath<S, B> via(Function<? super A, ? extends Chainable<B>> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        State<S, B> flatMapped = state.flatMap(a -> {
            Chainable<B> result = mapper.apply(a);
            if (result instanceof StatePath<?, ?> sp) {
                return ((StatePath<S, B>) sp).state;
            }
            throw new IllegalArgumentException(
                "StatePath.via must return StatePath. Got: " + result.getClass());
        });
        return new StatePath<>(flatMapped);
    }

    @Override
    public <B> StatePath<S, B> then(java.util.function.Supplier<? extends Chainable<B>> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        return via(_ -> supplier.get());
    }

    // ===== State-Specific Operations =====

    /**
     * Combines with another StatePath using a combining function.
     */
    public <B, C> StatePath<S, C> zipWith(
            StatePath<S, B> other,
            java.util.function.BiFunction<? super A, ? super B, ? extends C> combiner) {
        Objects.requireNonNull(other, "other must not be null");
        Objects.requireNonNull(combiner, "combiner must not be null");
        return via(a -> other.map(b -> combiner.apply(a, b)));
    }

    // ===== Conversions =====

    /**
     * Converts to IOPath by providing the initial state.
     * Returns only the result.
     */
    public IOPath<A> toIOPath(S initialState) {
        return IOPath.delay(() -> evalState(initialState));
    }

    // ===== Object Methods =====

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof StatePath<?, ?> other)) return false;
        return state.equals(other.state);
    }

    @Override
    public int hashCode() {
        return state.hashCode();
    }

    @Override
    public String toString() {
        return "StatePath(" + state + ")";
    }
}
```

**Tests**: `StatePathTest.java`, `StatePathPropertyTest.java`

### B3: WriterPath<W, A>

**Purpose**: Computations that accumulate output (logging, audit trails)

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/effect/WriterPath.java`

```java
package org.higherkindedj.hkt.effect;

import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.typeclass.Monoid;
import org.higherkindedj.hkt.writer.Writer;
import org.higherkindedj.hkt.tuple.Tuple2;

import java.util.Objects;
import java.util.function.Function;

/**
 * A Path wrapper for {@link Writer} computations.
 *
 * <p>WriterPath represents computations that produce a value along with
 * accumulated output. The output type must have a Monoid instance for combining.
 *
 * <h2>Use Cases</h2>
 * <ul>
 *   <li>Logging during computation</li>
 *   <li>Audit trail generation</li>
 *   <li>Collecting metrics</li>
 *   <li>Building output alongside computation</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // Using List<String> for log output
 * Monoid<List<String>> logMonoid = Monoids.list();
 *
 * WriterPath<List<String>, Integer> computation =
 *     WriterPath.tell(List.of("Starting"), logMonoid)
 *         .then(() -> WriterPath.pure(42, logMonoid))
 *         .peek(n -> {})  // value is 42
 *         .via(n -> WriterPath.tell(List.of("Got " + n), logMonoid)
 *             .map(_ -> n * 2));
 *
 * Tuple2<List<String>, Integer> result = computation.run();
 * // result = (["Starting", "Got 42"], 84)
 * }</pre>
 *
 * @param <W> the output/log type (must have Monoid)
 * @param <A> the result type
 */
public final class WriterPath<W, A> implements Chainable<A> {

    private final Writer<W, A> writer;
    private final Monoid<W> monoid;

    private WriterPath(Writer<W, A> writer, Monoid<W> monoid) {
        this.writer = Objects.requireNonNull(writer, "writer must not be null");
        this.monoid = Objects.requireNonNull(monoid, "monoid must not be null");
    }

    // ===== Factory Methods =====

    /**
     * Creates a WriterPath from a Writer.
     */
    public static <W, A> WriterPath<W, A> of(Writer<W, A> writer, Monoid<W> monoid) {
        return new WriterPath<>(writer, monoid);
    }

    /**
     * Creates a WriterPath with a value and empty output.
     */
    public static <W, A> WriterPath<W, A> pure(A value, Monoid<W> monoid) {
        return new WriterPath<>(Writer.of(value, monoid), monoid);
    }

    /**
     * Creates a WriterPath that only produces output.
     */
    public static <W> WriterPath<W, Void> tell(W output, Monoid<W> monoid) {
        return new WriterPath<>(Writer.tell(output, monoid).map(_ -> null), monoid);
    }

    /**
     * Creates a WriterPath from a value and output.
     */
    public static <W, A> WriterPath<W, A> writer(A value, W output, Monoid<W> monoid) {
        return new WriterPath<>(Writer.writer(value, output, monoid), monoid);
    }

    // ===== Terminal Operations =====

    /**
     * Runs this computation and returns both output and result.
     */
    public Tuple2<W, A> run() {
        return writer.run();
    }

    /**
     * Runs and returns only the result, discarding output.
     */
    public A value() {
        return run().second();
    }

    /**
     * Runs and returns only the output, discarding result.
     */
    public W written() {
        return run().first();
    }

    /**
     * Returns the underlying Writer.
     */
    public Writer<W, A> toWriter() {
        return writer;
    }

    /**
     * Returns the Monoid used for combining output.
     */
    public Monoid<W> monoid() {
        return monoid;
    }

    // ===== Composable Implementation =====

    @Override
    public <B> WriterPath<W, B> map(Function<? super A, ? extends B> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        return new WriterPath<>(writer.map(mapper), monoid);
    }

    @Override
    public WriterPath<W, A> peek(java.util.function.Consumer<? super A> consumer) {
        Objects.requireNonNull(consumer, "consumer must not be null");
        return map(a -> {
            consumer.accept(a);
            return a;
        });
    }

    // ===== Chainable Implementation =====

    @Override
    @SuppressWarnings("unchecked")
    public <B> WriterPath<W, B> via(Function<? super A, ? extends Chainable<B>> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        Writer<W, B> flatMapped = writer.flatMap(a -> {
            Chainable<B> result = mapper.apply(a);
            if (result instanceof WriterPath<?, ?> wp) {
                return ((WriterPath<W, B>) wp).writer;
            }
            throw new IllegalArgumentException(
                "WriterPath.via must return WriterPath. Got: " + result.getClass());
        });
        return new WriterPath<>(flatMapped, monoid);
    }

    @Override
    public <B> WriterPath<W, B> then(java.util.function.Supplier<? extends Chainable<B>> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        return via(_ -> supplier.get());
    }

    // ===== Writer-Specific Operations =====

    /**
     * Adds additional output to this computation.
     */
    public WriterPath<W, A> censor(Function<? super W, ? extends W> f) {
        return new WriterPath<>(writer.censor(f), monoid);
    }

    /**
     * Transforms both the output and value.
     */
    public <B> WriterPath<W, B> mapBoth(
            Function<? super W, ? extends W> outputMapper,
            Function<? super A, ? extends B> valueMapper) {
        return new WriterPath<>(
            writer.censor(outputMapper).map(valueMapper),
            monoid
        );
    }

    /**
     * Combines with another WriterPath using a combining function.
     */
    public <B, C> WriterPath<W, C> zipWith(
            WriterPath<W, B> other,
            java.util.function.BiFunction<? super A, ? super B, ? extends C> combiner) {
        Objects.requireNonNull(other, "other must not be null");
        Objects.requireNonNull(combiner, "combiner must not be null");
        return via(a -> other.map(b -> combiner.apply(a, b)));
    }

    // ===== Conversions =====

    /**
     * Converts to IOPath, discarding the output.
     */
    public IOPath<A> toIOPath() {
        return IOPath.delay(this::value);
    }

    /**
     * Converts to EitherPath with the output as the "right" value.
     */
    public EitherPath<W, A> toEitherPath() {
        return EitherPath.right(value());
    }

    // ===== Object Methods =====

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof WriterPath<?, ?> other)) return false;
        return writer.equals(other.writer) && monoid.equals(other.monoid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(writer, monoid);
    }

    @Override
    public String toString() {
        return "WriterPath(" + writer + ")";
    }
}
```

**Tests**: `WriterPathTest.java`, `WriterPathPropertyTest.java`

### B4: LazyPath<A>

**Purpose**: Deferred/memoised computation

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/effect/LazyPath.java`

```java
package org.higherkindedj.hkt.effect;

import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.lazy.Lazy;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A Path wrapper for {@link Lazy} computations.
 *
 * <p>LazyPath represents deferred computations that are evaluated at most once.
 * Once evaluated, the result is cached and reused.
 *
 * <h2>Use Cases</h2>
 * <ul>
 *   <li>Expensive computations that may not be needed</li>
 *   <li>Breaking circular dependencies</li>
 *   <li>Infinite data structures</li>
 *   <li>Memoisation</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // Expensive computation, deferred
 * LazyPath<BigInteger> fibonacci1000 = LazyPath.defer(() -> computeFib(1000));
 *
 * // Not computed yet
 * LazyPath<String> asString = fibonacci1000.map(BigInteger::toString);
 *
 * // Now it's computed (and cached)
 * String result = asString.get();
 *
 * // Second call returns cached value
 * String result2 = asString.get();  // No recomputation
 * }</pre>
 *
 * @param <A> the result type
 */
public final class LazyPath<A> implements Chainable<A> {

    private final Lazy<A> lazy;

    private LazyPath(Lazy<A> lazy) {
        this.lazy = Objects.requireNonNull(lazy, "lazy must not be null");
    }

    // ===== Factory Methods =====

    /**
     * Creates a LazyPath from a Lazy.
     */
    public static <A> LazyPath<A> of(Lazy<A> lazy) {
        return new LazyPath<>(lazy);
    }

    /**
     * Creates an already-evaluated LazyPath.
     */
    public static <A> LazyPath<A> now(A value) {
        return new LazyPath<>(Lazy.now(value));
    }

    /**
     * Creates a LazyPath that defers computation.
     */
    public static <A> LazyPath<A> defer(Supplier<? extends A> supplier) {
        return new LazyPath<>(Lazy.defer(supplier));
    }

    // ===== Terminal Operations =====

    /**
     * Forces evaluation and returns the result.
     * Subsequent calls return the cached value.
     */
    public A get() {
        return lazy.get();
    }

    /**
     * Returns whether this lazy value has been evaluated.
     */
    public boolean isEvaluated() {
        return lazy.isEvaluated();
    }

    /**
     * Returns the underlying Lazy.
     */
    public Lazy<A> toLazy() {
        return lazy;
    }

    // ===== Composable Implementation =====

    @Override
    public <B> LazyPath<B> map(Function<? super A, ? extends B> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        return new LazyPath<>(lazy.map(mapper));
    }

    @Override
    public LazyPath<A> peek(java.util.function.Consumer<? super A> consumer) {
        Objects.requireNonNull(consumer, "consumer must not be null");
        return map(a -> {
            consumer.accept(a);
            return a;
        });
    }

    // ===== Chainable Implementation =====

    @Override
    @SuppressWarnings("unchecked")
    public <B> LazyPath<B> via(Function<? super A, ? extends Chainable<B>> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        Lazy<B> flatMapped = lazy.flatMap(a -> {
            Chainable<B> result = mapper.apply(a);
            if (result instanceof LazyPath<?> lp) {
                return ((LazyPath<B>) lp).lazy;
            }
            throw new IllegalArgumentException(
                "LazyPath.via must return LazyPath. Got: " + result.getClass());
        });
        return new LazyPath<>(flatMapped);
    }

    @Override
    public <B> LazyPath<B> then(java.util.function.Supplier<? extends Chainable<B>> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        return via(_ -> supplier.get());
    }

    // ===== Lazy-Specific Operations =====

    /**
     * Combines with another LazyPath using a combining function.
     * Both are evaluated lazily.
     */
    public <B, C> LazyPath<C> zipWith(
            LazyPath<B> other,
            java.util.function.BiFunction<? super A, ? super B, ? extends C> combiner) {
        Objects.requireNonNull(other, "other must not be null");
        Objects.requireNonNull(combiner, "combiner must not be null");
        return via(a -> other.map(b -> combiner.apply(a, b)));
    }

    // ===== Conversions =====

    /**
     * Converts to IOPath.
     * The IO will force evaluation when run.
     */
    public IOPath<A> toIOPath() {
        return IOPath.delay(this::get);
    }

    /**
     * Converts to MaybePath.
     * Forces evaluation.
     */
    public MaybePath<A> toMaybePath() {
        return MaybePath.just(get());
    }

    /**
     * Converts to IdPath.
     * Forces evaluation.
     */
    public IdPath<A> toIdPath() {
        return IdPath.of(get());
    }

    // ===== Object Methods =====

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof LazyPath<?> other)) return false;
        return lazy.equals(other.lazy);
    }

    @Override
    public int hashCode() {
        return lazy.hashCode();
    }

    @Override
    public String toString() {
        if (isEvaluated()) {
            return "LazyPath(" + get() + ")";
        } else {
            return "LazyPath(<not evaluated>)";
        }
    }
}
```

**Tests**: `LazyPathTest.java`, `LazyPathPropertyTest.java`

---

## Phase 3c: Java Stdlib & Collections

### C1: CompletableFuturePath<A>

**Purpose**: Async computation wrapper

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/effect/CompletableFuturePath.java`

```java
package org.higherkindedj.hkt.effect;

import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.effect.capability.Effectful;
import org.higherkindedj.hkt.effect.capability.Recoverable;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * A Path wrapper for {@link CompletableFuture} async computations.
 *
 * <p>CompletableFuturePath represents asynchronous computations that may
 * complete with a value or fail with an exception. Supports error recovery
 * and timeout handling.
 *
 * <h2>Use Cases</h2>
 * <ul>
 *   <li>Async API calls</li>
 *   <li>Parallel computation</li>
 *   <li>Non-blocking I/O</li>
 *   <li>Timeout handling</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * CompletableFuturePath<User> userPath = CompletableFuturePath.fromFuture(
 *     userService.findByIdAsync(userId));
 *
 * CompletableFuturePath<Order> orderPath = userPath
 *     .via(user -> CompletableFuturePath.fromFuture(
 *         orderService.getOrdersAsync(user.id())))
 *     .map(orders -> orders.get(0))
 *     .withTimeout(Duration.ofSeconds(5))
 *     .recover(ex -> Order.empty());
 *
 * Order order = orderPath.join();
 * }</pre>
 *
 * @param <A> the result type
 */
public final class CompletableFuturePath<A>
        implements Chainable<A>, Recoverable<Throwable, A>, Effectful<A> {

    private final CompletableFuture<A> future;

    private CompletableFuturePath(CompletableFuture<A> future) {
        this.future = Objects.requireNonNull(future, "future must not be null");
    }

    // ===== Factory Methods =====

    /**
     * Creates a CompletableFuturePath from an existing future.
     */
    public static <A> CompletableFuturePath<A> fromFuture(CompletableFuture<A> future) {
        return new CompletableFuturePath<>(future);
    }

    /**
     * Creates an already-completed CompletableFuturePath.
     */
    public static <A> CompletableFuturePath<A> completed(A value) {
        return new CompletableFuturePath<>(CompletableFuture.completedFuture(value));
    }

    /**
     * Creates a failed CompletableFuturePath.
     */
    public static <A> CompletableFuturePath<A> failed(Throwable error) {
        return new CompletableFuturePath<>(CompletableFuture.failedFuture(error));
    }

    /**
     * Creates a CompletableFuturePath from a supplier, running async.
     */
    public static <A> CompletableFuturePath<A> supplyAsync(
            java.util.function.Supplier<A> supplier) {
        return new CompletableFuturePath<>(CompletableFuture.supplyAsync(supplier));
    }

    /**
     * Creates a CompletableFuturePath from a supplier, running on the given executor.
     */
    public static <A> CompletableFuturePath<A> supplyAsync(
            java.util.function.Supplier<A> supplier,
            Executor executor) {
        return new CompletableFuturePath<>(CompletableFuture.supplyAsync(supplier, executor));
    }

    // ===== Terminal Operations =====

    /**
     * Blocks and returns the result.
     * @throws CompletionException if the computation failed
     */
    public A join() {
        return future.join();
    }

    /**
     * Blocks and returns the result, with timeout.
     */
    public A join(Duration timeout) throws java.util.concurrent.TimeoutException {
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new CompletionException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompletionException(e);
        }
    }

    /**
     * Returns the underlying CompletableFuture.
     */
    @Override
    public CompletableFuture<A> run() {
        return future;
    }

    /**
     * Returns whether this future is done.
     */
    public boolean isDone() {
        return future.isDone();
    }

    /**
     * Returns whether this future completed exceptionally.
     */
    public boolean isCompletedExceptionally() {
        return future.isCompletedExceptionally();
    }

    // ===== Composable Implementation =====

    @Override
    public <B> CompletableFuturePath<B> map(Function<? super A, ? extends B> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        return new CompletableFuturePath<>(future.thenApply(mapper));
    }

    @Override
    public CompletableFuturePath<A> peek(java.util.function.Consumer<? super A> consumer) {
        Objects.requireNonNull(consumer, "consumer must not be null");
        return new CompletableFuturePath<>(future.thenApply(a -> {
            consumer.accept(a);
            return a;
        }));
    }

    // ===== Chainable Implementation =====

    @Override
    @SuppressWarnings("unchecked")
    public <B> CompletableFuturePath<B> via(Function<? super A, ? extends Chainable<B>> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        CompletableFuture<B> composed = future.thenCompose(a -> {
            Chainable<B> result = mapper.apply(a);
            if (result instanceof CompletableFuturePath<?> cfp) {
                return ((CompletableFuturePath<B>) cfp).future;
            }
            throw new IllegalArgumentException(
                "CompletableFuturePath.via must return CompletableFuturePath. Got: " +
                result.getClass());
        });
        return new CompletableFuturePath<>(composed);
    }

    @Override
    public <B> CompletableFuturePath<B> then(
            java.util.function.Supplier<? extends Chainable<B>> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        return via(_ -> supplier.get());
    }

    // ===== Recoverable Implementation =====

    @Override
    public CompletableFuturePath<A> recover(
            Function<? super Throwable, ? extends A> recovery) {
        Objects.requireNonNull(recovery, "recovery must not be null");
        return new CompletableFuturePath<>(future.exceptionally(recovery));
    }

    @Override
    public CompletableFuturePath<A> recoverWith(
            Function<? super Throwable, ? extends Chainable<A>> recovery) {
        Objects.requireNonNull(recovery, "recovery must not be null");
        CompletableFuture<A> recovered = future.exceptionallyCompose(ex -> {
            Chainable<A> result = recovery.apply(ex);
            if (result instanceof CompletableFuturePath<?> cfp) {
                return ((CompletableFuturePath<A>) cfp).future;
            }
            throw new IllegalArgumentException(
                "recoverWith must return CompletableFuturePath. Got: " + result.getClass());
        });
        return new CompletableFuturePath<>(recovered);
    }

    @Override
    public <E2> CompletableFuturePath<A> mapError(
            Function<? super Throwable, ? extends E2> mapper) {
        // Note: E2 must be Throwable subtype for this to make sense
        Objects.requireNonNull(mapper, "mapper must not be null");
        CompletableFuture<A> mapped = future.exceptionallyCompose(ex -> {
            @SuppressWarnings("unchecked")
            E2 mappedError = mapper.apply(ex);
            if (mappedError instanceof Throwable t) {
                return CompletableFuture.failedFuture(t);
            }
            throw new IllegalArgumentException("mapError must return Throwable");
        });
        return new CompletableFuturePath<>(mapped);
    }

    // ===== Async-Specific Operations =====

    /**
     * Adds a timeout to this computation.
     */
    public CompletableFuturePath<A> withTimeout(Duration timeout) {
        return new CompletableFuturePath<>(
            future.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS));
    }

    /**
     * Returns a default value if this computation times out.
     */
    public CompletableFuturePath<A> completeOnTimeout(A defaultValue, Duration timeout) {
        return new CompletableFuturePath<>(
            future.completeOnTimeout(defaultValue, timeout.toMillis(), TimeUnit.MILLISECONDS));
    }

    /**
     * Combines with another future in parallel.
     */
    public <B, C> CompletableFuturePath<C> zipWith(
            CompletableFuturePath<B> other,
            java.util.function.BiFunction<? super A, ? super B, ? extends C> combiner) {
        Objects.requireNonNull(other, "other must not be null");
        Objects.requireNonNull(combiner, "combiner must not be null");
        return new CompletableFuturePath<>(future.thenCombine(other.future, combiner));
    }

    /**
     * Runs this computation on a different executor.
     */
    public CompletableFuturePath<A> onExecutor(Executor executor) {
        return new CompletableFuturePath<>(
            future.thenApplyAsync(Function.identity(), executor));
    }

    // ===== Conversions =====

    /**
     * Converts to IOPath (blocking).
     */
    public IOPath<A> toIOPath() {
        return IOPath.delay(this::join);
    }

    /**
     * Converts to TryPath (blocking).
     */
    public TryPath<A> toTryPath() {
        return TryPath.of(() -> join());
    }

    // ===== Object Methods =====

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CompletableFuturePath<?> other)) return false;
        return future.equals(other.future);
    }

    @Override
    public int hashCode() {
        return future.hashCode();
    }

    @Override
    public String toString() {
        if (future.isDone()) {
            if (future.isCompletedExceptionally()) {
                return "CompletableFuturePath(<failed>)";
            }
            return "CompletableFuturePath(" + future.join() + ")";
        }
        return "CompletableFuturePath(<pending>)";
    }
}
```

**Tests**: `CompletableFuturePathTest.java`, `CompletableFuturePathPropertyTest.java`

### C2: ListPath<A>

**Purpose**: Non-deterministic/multi-result computations

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/effect/ListPath.java`

```java
package org.higherkindedj.hkt.effect;

import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.effect.capability.Composable;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A Path wrapper for {@link List} representing non-deterministic computations.
 *
 * <p>ListPath treats lists as computations that can produce multiple results.
 * The {@code via} operation performs flatMap, combining all possible results.
 *
 * <h2>Use Cases</h2>
 * <ul>
 *   <li>Non-deterministic algorithms</li>
 *   <li>Search problems with multiple solutions</li>
 *   <li>Generating combinations/permutations</li>
 *   <li>Parsing with ambiguous grammars</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // Generate all pairs
 * ListPath<Integer> numbers = ListPath.of(1, 2, 3);
 * ListPath<String> letters = ListPath.of("a", "b");
 *
 * ListPath<String> pairs = numbers.via(n ->
 *     letters.map(l -> n + l));
 *
 * List<String> result = pairs.run();
 * // ["1a", "1b", "2a", "2b", "3a", "3b"]
 * }</pre>
 *
 * @param <A> the element type
 */
public final class ListPath<A> implements Chainable<A> {

    private final List<A> list;

    private ListPath(List<A> list) {
        this.list = List.copyOf(Objects.requireNonNull(list, "list must not be null"));
    }

    // ===== Factory Methods =====

    /**
     * Creates a ListPath from a list.
     */
    public static <A> ListPath<A> of(List<A> list) {
        return new ListPath<>(list);
    }

    /**
     * Creates a ListPath from varargs.
     */
    @SafeVarargs
    public static <A> ListPath<A> of(A... elements) {
        return new ListPath<>(Arrays.asList(elements));
    }

    /**
     * Creates a ListPath with a single element.
     */
    public static <A> ListPath<A> pure(A value) {
        return new ListPath<>(List.of(value));
    }

    /**
     * Creates an empty ListPath.
     */
    public static <A> ListPath<A> empty() {
        return new ListPath<>(List.of());
    }

    /**
     * Creates a ListPath from a range.
     */
    public static ListPath<Integer> range(int start, int endExclusive) {
        List<Integer> list = new ArrayList<>();
        for (int i = start; i < endExclusive; i++) {
            list.add(i);
        }
        return new ListPath<>(list);
    }

    // ===== Terminal Operations =====

    /**
     * Returns the underlying list.
     */
    public List<A> run() {
        return list;
    }

    /**
     * Returns the first element, or empty if the list is empty.
     */
    public Optional<A> headOption() {
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /**
     * Returns whether this list is empty.
     */
    public boolean isEmpty() {
        return list.isEmpty();
    }

    /**
     * Returns the size of this list.
     */
    public int size() {
        return list.size();
    }

    // ===== Composable Implementation =====

    @Override
    public <B> ListPath<B> map(Function<? super A, ? extends B> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        List<B> mapped = list.stream()
            .map(mapper)
            .collect(Collectors.toList());
        return new ListPath<>(mapped);
    }

    @Override
    public ListPath<A> peek(java.util.function.Consumer<? super A> consumer) {
        Objects.requireNonNull(consumer, "consumer must not be null");
        list.forEach(consumer);
        return this;
    }

    // ===== Chainable Implementation =====

    @Override
    @SuppressWarnings("unchecked")
    public <B> ListPath<B> via(Function<? super A, ? extends Chainable<B>> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        List<B> flatMapped = list.stream()
            .flatMap(a -> {
                Chainable<B> result = mapper.apply(a);
                if (result instanceof ListPath<?> lp) {
                    return ((ListPath<B>) lp).list.stream();
                }
                throw new IllegalArgumentException(
                    "ListPath.via must return ListPath. Got: " + result.getClass());
            })
            .collect(Collectors.toList());
        return new ListPath<>(flatMapped);
    }

    @Override
    public <B> ListPath<B> then(java.util.function.Supplier<? extends Chainable<B>> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        return via(_ -> supplier.get());
    }

    // ===== List-Specific Operations =====

    /**
     * Filters elements based on a predicate.
     */
    public ListPath<A> filter(Predicate<? super A> predicate) {
        Objects.requireNonNull(predicate, "predicate must not be null");
        List<A> filtered = list.stream()
            .filter(predicate)
            .collect(Collectors.toList());
        return new ListPath<>(filtered);
    }

    /**
     * Takes the first n elements.
     */
    public ListPath<A> take(int n) {
        return new ListPath<>(list.stream().limit(n).collect(Collectors.toList()));
    }

    /**
     * Drops the first n elements.
     */
    public ListPath<A> drop(int n) {
        return new ListPath<>(list.stream().skip(n).collect(Collectors.toList()));
    }

    /**
     * Combines with another ListPath using a combining function.
     * Produces all combinations.
     */
    public <B, C> ListPath<C> zipWith(
            ListPath<B> other,
            java.util.function.BiFunction<? super A, ? super B, ? extends C> combiner) {
        Objects.requireNonNull(other, "other must not be null");
        Objects.requireNonNull(combiner, "combiner must not be null");
        return via(a -> other.map(b -> combiner.apply(a, b)));
    }

    /**
     * Folds the list from the left.
     */
    public <B> B foldLeft(B initial, java.util.function.BiFunction<B, A, B> f) {
        B result = initial;
        for (A a : list) {
            result = f.apply(result, a);
        }
        return result;
    }

    /**
     * Concatenates with another ListPath.
     */
    public ListPath<A> concat(ListPath<A> other) {
        List<A> combined = new ArrayList<>(list);
        combined.addAll(other.list);
        return new ListPath<>(combined);
    }

    /**
     * Returns distinct elements.
     */
    public ListPath<A> distinct() {
        return new ListPath<>(list.stream().distinct().collect(Collectors.toList()));
    }

    // ===== Conversions =====

    /**
     * Converts to MaybePath with the first element.
     */
    public MaybePath<A> toMaybePath() {
        return headOption()
            .map(MaybePath::just)
            .orElse(MaybePath.nothing());
    }

    /**
     * Converts to StreamPath.
     */
    public StreamPath<A> toStreamPath() {
        return StreamPath.of(list.stream());
    }

    // ===== Object Methods =====

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ListPath<?> other)) return false;
        return list.equals(other.list);
    }

    @Override
    public int hashCode() {
        return list.hashCode();
    }

    @Override
    public String toString() {
        return "ListPath(" + list + ")";
    }
}
```

**Tests**: `ListPathTest.java`, `ListPathPropertyTest.java`

### C3: StreamPath<A>

**Purpose**: Lazy sequence computations

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/effect/StreamPath.java`

```java
package org.higherkindedj.hkt.effect;

import org.higherkindedj.hkt.effect.capability.Chainable;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A Path wrapper for {@link Stream} representing lazy sequence computations.
 *
 * <p>StreamPath provides lazy evaluation of sequences. Operations are not
 * executed until a terminal operation is called.
 *
 * <h2>Important</h2>
 * <p>Streams can only be consumed once. After calling a terminal operation
 * like {@code run()}, the stream is exhausted. Use {@code toListPath()} if
 * you need to iterate multiple times.
 *
 * <h2>Use Cases</h2>
 * <ul>
 *   <li>Large data processing</li>
 *   <li>Infinite sequences (with limit)</li>
 *   <li>Pipeline transformations</li>
 *   <li>Memory-efficient processing</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * StreamPath<Integer> naturals = StreamPath.iterate(1, n -> n + 1);
 *
 * List<Integer> firstTenSquares = naturals
 *     .map(n -> n * n)
 *     .take(10)
 *     .run()
 *     .collect(Collectors.toList());
 * }</pre>
 *
 * @param <A> the element type
 */
public final class StreamPath<A> implements Chainable<A> {

    private final Supplier<Stream<A>> streamSupplier;

    private StreamPath(Supplier<Stream<A>> streamSupplier) {
        this.streamSupplier = Objects.requireNonNull(streamSupplier, "streamSupplier must not be null");
    }

    // ===== Factory Methods =====

    /**
     * Creates a StreamPath from a stream.
     * Note: The stream supplier allows re-creation for multiple terminal operations.
     */
    public static <A> StreamPath<A> of(Stream<A> stream) {
        // Materialize to allow multiple uses
        List<A> materialized = stream.collect(Collectors.toList());
        return new StreamPath<>(() -> materialized.stream());
    }

    /**
     * Creates a StreamPath from a supplier that produces streams.
     */
    public static <A> StreamPath<A> fromSupplier(Supplier<Stream<A>> supplier) {
        return new StreamPath<>(supplier);
    }

    /**
     * Creates a StreamPath from a list.
     */
    public static <A> StreamPath<A> fromList(List<A> list) {
        return new StreamPath<>(list::stream);
    }

    /**
     * Creates a StreamPath from varargs.
     */
    @SafeVarargs
    public static <A> StreamPath<A> of(A... elements) {
        List<A> list = Arrays.asList(elements);
        return new StreamPath<>(list::stream);
    }

    /**
     * Creates a StreamPath with a single element.
     */
    public static <A> StreamPath<A> pure(A value) {
        return new StreamPath<>(() -> Stream.of(value));
    }

    /**
     * Creates an empty StreamPath.
     */
    public static <A> StreamPath<A> empty() {
        return new StreamPath<>(Stream::empty);
    }

    /**
     * Creates an infinite StreamPath by iterating a function.
     */
    public static <A> StreamPath<A> iterate(A seed, java.util.function.UnaryOperator<A> f) {
        return new StreamPath<>(() -> Stream.iterate(seed, f));
    }

    /**
     * Creates an infinite StreamPath from a supplier.
     */
    public static <A> StreamPath<A> generate(Supplier<A> supplier) {
        return new StreamPath<>(() -> Stream.generate(supplier));
    }

    // ===== Terminal Operations =====

    /**
     * Returns the stream for consumption.
     * Note: Can only be called once per logical operation.
     */
    public Stream<A> run() {
        return streamSupplier.get();
    }

    /**
     * Collects to a list.
     */
    public List<A> toList() {
        return run().collect(Collectors.toList());
    }

    /**
     * Returns the first element, or empty if the stream is empty.
     */
    public Optional<A> headOption() {
        return run().findFirst();
    }

    /**
     * Counts elements.
     */
    public long count() {
        return run().count();
    }

    // ===== Composable Implementation =====

    @Override
    public <B> StreamPath<B> map(Function<? super A, ? extends B> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        return new StreamPath<>(() -> streamSupplier.get().map(mapper));
    }

    @Override
    public StreamPath<A> peek(java.util.function.Consumer<? super A> consumer) {
        Objects.requireNonNull(consumer, "consumer must not be null");
        return new StreamPath<>(() -> streamSupplier.get().peek(consumer));
    }

    // ===== Chainable Implementation =====

    @Override
    @SuppressWarnings("unchecked")
    public <B> StreamPath<B> via(Function<? super A, ? extends Chainable<B>> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        return new StreamPath<>(() -> streamSupplier.get().flatMap(a -> {
            Chainable<B> result = mapper.apply(a);
            if (result instanceof StreamPath<?> sp) {
                return ((StreamPath<B>) sp).run();
            }
            if (result instanceof ListPath<?> lp) {
                return ((ListPath<B>) lp).run().stream();
            }
            throw new IllegalArgumentException(
                "StreamPath.via must return StreamPath or ListPath. Got: " + result.getClass());
        }));
    }

    @Override
    public <B> StreamPath<B> then(java.util.function.Supplier<? extends Chainable<B>> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        return via(_ -> supplier.get());
    }

    // ===== Stream-Specific Operations =====

    /**
     * Filters elements based on a predicate.
     */
    public StreamPath<A> filter(Predicate<? super A> predicate) {
        Objects.requireNonNull(predicate, "predicate must not be null");
        return new StreamPath<>(() -> streamSupplier.get().filter(predicate));
    }

    /**
     * Takes the first n elements.
     */
    public StreamPath<A> take(long n) {
        return new StreamPath<>(() -> streamSupplier.get().limit(n));
    }

    /**
     * Drops the first n elements.
     */
    public StreamPath<A> drop(long n) {
        return new StreamPath<>(() -> streamSupplier.get().skip(n));
    }

    /**
     * Takes elements while predicate is true.
     */
    public StreamPath<A> takeWhile(Predicate<? super A> predicate) {
        Objects.requireNonNull(predicate, "predicate must not be null");
        return new StreamPath<>(() -> streamSupplier.get().takeWhile(predicate));
    }

    /**
     * Drops elements while predicate is true.
     */
    public StreamPath<A> dropWhile(Predicate<? super A> predicate) {
        Objects.requireNonNull(predicate, "predicate must not be null");
        return new StreamPath<>(() -> streamSupplier.get().dropWhile(predicate));
    }

    /**
     * Returns distinct elements.
     */
    public StreamPath<A> distinct() {
        return new StreamPath<>(() -> streamSupplier.get().distinct());
    }

    /**
     * Sorts elements.
     */
    public StreamPath<A> sorted() {
        return new StreamPath<>(() -> streamSupplier.get().sorted());
    }

    /**
     * Sorts elements using a comparator.
     */
    public StreamPath<A> sorted(Comparator<? super A> comparator) {
        return new StreamPath<>(() -> streamSupplier.get().sorted(comparator));
    }

    /**
     * Concatenates with another StreamPath.
     */
    public StreamPath<A> concat(StreamPath<A> other) {
        return new StreamPath<>(() -> Stream.concat(streamSupplier.get(), other.run()));
    }

    /**
     * Folds the stream from the left.
     */
    public <B> B foldLeft(B initial, java.util.function.BiFunction<B, A, B> f) {
        B result = initial;
        Iterator<A> iter = run().iterator();
        while (iter.hasNext()) {
            result = f.apply(result, iter.next());
        }
        return result;
    }

    // ===== Conversions =====

    /**
     * Converts to ListPath (materializes the stream).
     */
    public ListPath<A> toListPath() {
        return ListPath.of(toList());
    }

    /**
     * Converts to MaybePath with the first element.
     */
    public MaybePath<A> toMaybePath() {
        return headOption()
            .map(MaybePath::just)
            .orElse(MaybePath.nothing());
    }

    // ===== Object Methods =====

    @Override
    public String toString() {
        return "StreamPath(<stream>)";
    }

    // Note: equals and hashCode not implemented because streams are not comparable
}
```

**Tests**: `StreamPathTest.java`, `StreamPathPropertyTest.java`

### C4: Concrete Traverse Overloads

**Purpose**: Provide traverse without exposing HKT

**File**: Update `hkj-core/src/main/java/org/higherkindedj/hkt/effect/PathOps.java`

**New Methods**:
```java
// ===== Traverse Methods for MaybePath =====

/**
 * Traverses a list with a function returning MaybePath.
 */
public static <A, B> MaybePath<List<B>> traverseMaybe(
        List<A> items,
        Function<A, MaybePath<B>> f) {
    List<B> results = new ArrayList<>();
    for (A item : items) {
        MaybePath<B> result = f.apply(item);
        if (result.run().isNothing()) {
            return MaybePath.nothing();
        }
        results.add(result.run().getOrElse(null));
    }
    return MaybePath.just(results);
}

// ===== Traverse Methods for EitherPath =====

/**
 * Traverses a list with a function returning EitherPath.
 */
public static <E, A, B> EitherPath<E, List<B>> traverseEither(
        List<A> items,
        Function<A, EitherPath<E, B>> f) {
    List<B> results = new ArrayList<>();
    for (A item : items) {
        EitherPath<E, B> result = f.apply(item);
        Either<E, B> either = result.run();
        if (either.isLeft()) {
            return EitherPath.left(either.getLeft());
        }
        results.add(either.getOrElse(null));
    }
    return EitherPath.right(results);
}

// ===== Traverse Methods for ValidationPath =====

/**
 * Traverses a list with a function returning ValidationPath, accumulating all errors.
 */
public static <E, A, B> ValidationPath<E, List<B>> traverseValidated(
        List<A> items,
        Function<A, ValidationPath<E, B>> f,
        Semigroup<E> semigroup) {
    List<B> results = new ArrayList<>();
    E accumulatedErrors = null;
    boolean hasErrors = false;

    for (A item : items) {
        ValidationPath<E, B> result = f.apply(item);
        Validated<E, B> validated = result.run();

        if (validated.isInvalid()) {
            if (hasErrors) {
                accumulatedErrors = semigroup.combine(accumulatedErrors, validated.getError());
            } else {
                accumulatedErrors = validated.getError();
                hasErrors = true;
            }
        } else if (!hasErrors) {
            results.add(validated.getValue());
        }
    }

    if (hasErrors) {
        return Path.invalid(accumulatedErrors, semigroup);
    }
    return Path.valid(results, semigroup);
}

// ===== Traverse Methods for TryPath =====

/**
 * Traverses a list with a function returning TryPath.
 */
public static <A, B> TryPath<List<B>> traverseTry(
        List<A> items,
        Function<A, TryPath<B>> f) {
    List<B> results = new ArrayList<>();
    for (A item : items) {
        TryPath<B> result = f.apply(item);
        Try<B> tryResult = result.run();
        if (tryResult.isFailure()) {
            return TryPath.failure(tryResult.getCause());
        }
        results.add(tryResult.get());
    }
    return TryPath.success(results);
}

// ===== Traverse Methods for IOPath =====

/**
 * Traverses a list with a function returning IOPath.
 */
public static <A, B> IOPath<List<B>> traverseIO(
        List<A> items,
        Function<A, IOPath<B>> f) {
    return IOPath.delay(() -> {
        List<B> results = new ArrayList<>();
        for (A item : items) {
            results.add(f.apply(item).run().unsafeRun());
        }
        return results;
    });
}

// ===== Parallel Traverse for CompletableFuturePath =====

/**
 * Traverses a list in parallel with a function returning CompletableFuturePath.
 */
public static <A, B> CompletableFuturePath<List<B>> traverseFuturePar(
        List<A> items,
        Function<A, CompletableFuturePath<B>> f) {
    List<CompletableFuture<B>> futures = items.stream()
        .map(item -> f.apply(item).run())
        .collect(Collectors.toList());

    CompletableFuture<List<B>> combined = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0]))
        .thenApply(_ -> futures.stream()
            .map(CompletableFuture::join)
            .collect(Collectors.toList()));

    return CompletableFuturePath.fromFuture(combined);
}
```

**Tests**: `PathOpsTraverseTest.java`

---

## Testing Strategy

### Unit Tests

| Component | Test Class | Coverage Target |
|-----------|------------|-----------------|
| @PathSource processor | PathSourceProcessorTest | 100% line/branch |
| PathProvider SPI | PathProviderTest | 100% line/branch |
| PathRegistry | PathRegistryTest | 100% line/branch |
| NaturalTransformation | NaturalTransformationTest | 100% line/branch |
| GenericPath MonadError | GenericPathMonadErrorTest | 100% line/branch |
| ReaderPath | ReaderPathTest | 100% line/branch |
| StatePath | StatePathTest | 100% line/branch |
| WriterPath | WriterPathTest | 100% line/branch |
| LazyPath | LazyPathTest | 100% line/branch |
| CompletableFuturePath | CompletableFuturePathTest | 100% line/branch |
| ListPath | ListPathTest | 100% line/branch |
| StreamPath | StreamPathTest | 100% line/branch |
| PathOps traverse | PathOpsTraverseTest | 100% line/branch |

### Property-Based Law Tests (jqwik)

| Path Type | Laws to Verify | Test Class |
|-----------|----------------|------------|
| ReaderPath | Functor, Monad | ReaderPathPropertyTest |
| StatePath | Functor, Monad | StatePathPropertyTest |
| WriterPath | Functor, Monad | WriterPathPropertyTest |
| LazyPath | Functor, Monad | LazyPathPropertyTest |
| CompletableFuturePath | Functor, Monad, MonadError | CompletableFuturePathPropertyTest |
| ListPath | Functor, Monad, MonadZero | ListPathPropertyTest |
| StreamPath | Functor, Monad | StreamPathPropertyTest |
| NaturalTransformation | Naturality law | NaturalTransformationPropertyTest |

### Processor Tests

Using google-compile-testing:
- Valid @PathSource produces expected code
- @PathConfig affects generation
- Invalid annotations produce clear errors
- Generated code compiles and passes type checking

---

## Documentation Requirements

### Javadoc

All new public classes and methods need comprehensive Javadoc including:
- Class/method description
- @param for all parameters
- @return for return values
- Usage examples in class-level doc
- @see cross-references

### hkj-book Updates

New/updated chapters for Effects section:
- [ ] Update path_types.md with new Phase 3 types
- [ ] Add custom_effects.md chapter for @PathSource
- [ ] Update patterns.md with new path type patterns
- [ ] Add advanced_effects.md for Reader/State/Writer patterns
- [ ] Update composition.md with traverse examples

### Examples

Add to `hkj-examples`:
- ReaderPath dependency injection example
- StatePath parser example
- WriterPath logging example
- LazyPath memoisation example
- CompletableFuturePath async example
- ListPath combinations example
- @PathSource custom effect example
- Natural transformation example

---

## Success Criteria

### Functional

- [x] All new path types compile and pass tests
- [x] @PathSource annotation defined (processor implementation deferred)
- [x] PathProvider SPI discovers and creates paths
- [x] Natural transformations preserve structure
- [x] MonadError recovery works on GenericPath
- [x] All traverse methods work correctly

### Quality

- [x] High line/branch coverage for new code
- [x] All property-based law tests pass
- [x] All laws tests pass (Functor/Monad laws)
- [x] No breaking changes to Phase 1/2 API
- [x] Comprehensive Javadoc

### Process

- [x] All design decisions documented
- [x] Implementation matches design documents
- [ ] PR review completed
- [x] Documentation updated
- [ ] Examples in hkj-examples

---

## Phase 4 Forward Look

The following items are documented for Phase 4 consideration:

### Phase 4a: Resilience Patterns

| Component | Purpose |
|-----------|---------|
| **RetryPolicy** | Configurable retry strategies (exponential backoff, fixed delay) |
| **CircuitBreaker** | Fail-fast pattern for unreliable services |
| **Bulkhead** | Isolation pattern for resource protection |
| **RateLimiter** | Throttling for external API calls |

### Phase 4b: Tier 3 Types

| Path Type | Underlying Type | Notes |
|-----------|-----------------|-------|
| **TrampolinePath** | Trampoline | Stack-safe recursion (internal utility) |
| **FreePath** | Free | DSL building (advanced users) |

### Phase 4c: Monad Transformers (Tier 4)

| Path Type | Underlying Type | Notes |
|-----------|-----------------|-------|
| **MaybeTPath** | MaybeT | Requires outer monad parameter |
| **EitherTPath** | EitherT | Requires outer monad parameter |
| **OptionalTPath** | OptionalT | Requires outer monad parameter |
| **ReaderTPath** | ReaderT | Requires outer monad parameter |
| **StateTPath** | StateT | Requires outer monad parameter |

**Note**: Transformer paths are complex due to the outer monad requirement. They may be better served by @PathSource code generation rather than hand-written implementations.

### Phase 4d: FocusPath Integration

| Component | Purpose |
|-----------|---------|
| **FocusPath** | Combine optics with effect paths |
| **Path + Lens composition** | Navigate and transform nested structures with effects |
| **Prism + Path composition** | Safe access to sum type variants with effects |

### Phase 4e: Additional Features

| Component | Purpose |
|-----------|---------|
| **Reactive Streams interop** | Bridge to java.util.concurrent.Flow |
| **Resource management** | bracket/withResource for IOPath |
| **Parallel execution** | parZip, parSequence for IOPath/FuturePath |

---

## Checklist Summary

### Phase 3a: Extensibility
- [x] @PathSource annotation
- [x] @PathConfig annotation
- [x] PathProvider SPI
- [x] PathRegistry
- [x] MonadError support for GenericPath
- [x] NaturalTransformation interface
- [x] GenericPath.mapK method

### Phase 3b: Advanced Effects
- [x] ReaderPath
- [x] WithStatePath (named WithStatePath instead of StatePath)
- [x] WriterPath
- [x] LazyPath

### Phase 3c: Java Stdlib & Collections
- [x] CompletableFuturePath
- [x] ListPath
- [x] StreamPath
- [x] NonDetPath (additional - list-based non-determinism)
- [x] Concrete traverse overloads in PathOps

### Testing
- [x] Unit tests for all new components (*PathTest.java)
- [x] Property-based law tests for all path types (*PathPropertyTest.java)
- [x] Laws tests for all path types (*PathLawsTest.java)
- [x] NaturalTransformationTest
- [x] NaturalTransformationPropertyTest (property-based naturality laws)
- [x] PathRegistryTest (includes PathProvider tests)
- [x] PathOpsTest (includes traverse tests)
- [x] GenericPathTest (includes MonadError tests)
- [x] EffectPathTestingRules (ArchUnit enforcement)
- [ ] PathSourceProcessorTest (annotation processor tests - deferred)

### Documentation
- [x] Javadoc for all new public API
- [x] Updated hkj-book Effect chapter (path_types.md, composition.md, etc.)
- [x] TESTING.md exists (Effect Path section present)
- [ ] Examples in hkj-examples/effect/

### Phase 4 (Documented, Deferred)
- [ ] Document resilience patterns
- [ ] Document Tier 3 types (Trampoline, Free)
- [ ] Document Tier 4 transformers
- [ ] Document FocusPath integration

---

## Appendix: File Structure

```
hkj-core/src/main/java/org/higherkindedj/hkt/effect/
├── Path.java                          # Factory (updated)
├── PathOps.java                       # Utilities (updated with traverse)
├── NaturalTransformation.java         # NEW
├── MaybePath.java                     # Existing
├── EitherPath.java                    # Existing
├── TryPath.java                       # Existing
├── IOPath.java                        # Existing
├── ValidationPath.java                # Existing
├── IdPath.java                        # Existing
├── OptionalPath.java                  # Existing
├── GenericPath.java                   # Updated (MonadError, mapK)
├── ReaderPath.java                    # NEW
├── StatePath.java                     # NEW
├── WriterPath.java                    # NEW
├── LazyPath.java                      # NEW
├── CompletableFuturePath.java         # NEW
├── ListPath.java                      # NEW
├── StreamPath.java                    # NEW
├── capability/
│   └── (existing interfaces)
└── spi/
    ├── PathProvider.java              # NEW
    └── PathRegistry.java              # NEW

hkj-annotations/src/main/java/org/higherkindedj/hkt/effect/annotation/
├── GeneratePathBridge.java            # Existing
├── PathVia.java                       # Existing
├── PathSource.java                    # NEW
└── PathConfig.java                    # NEW

hkj-processor/src/main/java/org/higherkindedj/hkt/effect/processor/
├── PathProcessor.java                 # Updated (PathSource support)
├── PathSourceGenerator.java           # NEW
└── (existing files)

hkj-core/src/test/java/org/higherkindedj/hkt/effect/
├── ReaderPathTest.java                # NEW
├── StatePathTest.java                 # NEW
├── WriterPathTest.java                # NEW
├── LazyPathTest.java                  # NEW
├── CompletableFuturePathTest.java     # NEW
├── ListPathTest.java                  # NEW
├── StreamPathTest.java                # NEW
├── GenericPathMonadErrorTest.java     # NEW
├── NaturalTransformationTest.java     # NEW
├── PathOpsTraverseTest.java           # NEW
├── spi/
│   ├── PathProviderTest.java          # NEW
│   └── PathRegistryTest.java          # NEW
└── laws/
    ├── ReaderPathPropertyTest.java    # NEW
    ├── StatePathPropertyTest.java     # NEW
    ├── WriterPathPropertyTest.java    # NEW
    ├── LazyPathPropertyTest.java      # NEW
    ├── CompletableFuturePathPropertyTest.java  # NEW
    ├── ListPathPropertyTest.java      # NEW
    ├── StreamPathPropertyTest.java    # NEW
    └── NaturalTransformationPropertyTest.java  # NEW

hkj-examples/src/main/java/org/higherkindedj/examples/effect/
├── ReaderPathExample.java             # NEW
├── StatePathExample.java              # NEW
├── WriterPathExample.java             # NEW
├── LazyPathExample.java               # NEW
├── AsyncPathExample.java              # NEW
├── ListPathExample.java               # NEW
├── CustomEffectExample.java           # NEW
└── NaturalTransformationExample.java  # NEW
```
