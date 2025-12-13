# EffectPath API Implementation Guide

> **Status**: Phase 1 Complete, Living Document (v2.1)
> **Last Updated**: 2025-12-13
> **Scope**: Implementation patterns, package structure, and code organization

## Overview

This document provides implementation details for the EffectPath API, including package structure, code organization principles, and complete implementation patterns.

## Package Structure

```
hkj-core/src/main/java/org/higherkindedj/hkt/
├── effect/                                  # Main effect path package
│   ├── Path.java                           # Primary factory (entry point)
│   ├── MaybePath.java                      # Maybe wrapper
│   ├── EitherPath.java                     # Either wrapper
│   ├── TryPath.java                        # Try wrapper
│   ├── IOPath.java                         # IO wrapper
│   ├── ValidatedPath.java                  # Validated wrapper (Phase 2)
│   ├── GenericPath.java                    # Escape hatch for custom monads
│   │
│   ├── capability/                         # Capability interfaces
│   │   ├── Composable.java                 # Base: map, peek
│   │   ├── Combinable.java                 # Applicative: zipWith
│   │   ├── Chainable.java                  # Monadic: via, then, flatMap
│   │   ├── Recoverable.java                # Error: recover, mapError
│   │   └── Effectful.java                  # Side effects: run semantics
│   │
│   ├── spi/                                # Service Provider Interface (Future)
│   │   ├── PathProvider.java               # Plugin interface
│   │   ├── PathRegistry.java               # Provider discovery
│   │   └── TypeMapping.java                # Type to path mapping
│   │
│   ├── annotation/                         # Annotation definitions (Future)
│   │   ├── PathSource.java
│   │   ├── GeneratePathBridge.java
│   │   ├── PathVia.java
│   │   └── PathConfig.java
│   │
│   └── processor/                          # Annotation processor (Future)
│       ├── PathProcessor.java
│       ├── PathSourceGenerator.java
│       ├── PathBridgeGenerator.java
│       ├── PathCodeModel.java
│       └── PathValidations.java
│
├── maybe/                                   # EXISTING
├── either/                                  # EXISTING
├── trymonad/                                # EXISTING
└── io/                                      # EXISTING
```

## Code Organization Principles

### Single Responsibility

Each class should have ONE clear purpose:

| Class | Responsibility | Max Lines |
|-------|----------------|-----------|
| `MaybePath` | Wrap Maybe with fluent composition | 400 |
| `Path` | Factory methods only | 200 |
| `Chainable` | Define monadic composition contract | 80 |
| `PathProvider` | SPI contract definition | 50 |

### Delegation Over Reimplementation

Path types DELEGATE to underlying types:

```java
// GOOD: Delegate to underlying type
public <B> MaybePath<B> map(Function<? super A, ? extends B> f) {
    return new MaybePath<>(value.map(f));
}

// BAD: Reimplement logic
public <B> MaybePath<B> map(Function<? super A, ? extends B> f) {
    if (value.isJust()) {
        return new MaybePath<>(Maybe.just(f.apply(value.get())));
    }
    return nothing();
}
```

### Immutability

All path types are immutable:

```java
public final class MaybePath<A> {
    private final Maybe<A> value;  // Final, never mutated

    // All operations return NEW instances
    public <B> MaybePath<B> map(Function<A, B> f) {
        return new MaybePath<>(value.map(f));  // New instance
    }
}
```

### Null Safety

Use JSpecify annotations consistently:

```java
@NullMarked
public final class MaybePath<A> {

    private MaybePath(Maybe<A> value) {
        this.value = Objects.requireNonNull(value, "value must not be null");
    }

    public <B> MaybePath<B> map(Function<? super A, ? extends B> f) {
        Objects.requireNonNull(f, "mapper function must not be null");
        return new MaybePath<>(value.map(f));
    }
}
```

## Implementation: Primary Factory

### Path.java

```java
// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License.
package org.higherkindedj.hkt.effect;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.effect.support.ThrowingSupplier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Primary factory for creating Path instances.
 *
 * <p>This is the main entry point for the EffectPath API. All common path
 * creation scenarios are covered by methods in this class.
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * // Maybe paths
 * Path.maybe(value)
 * Path.maybeNullable(nullableValue)
 * Path.nothing()
 *
 * // Either paths
 * Path.right(value)
 * Path.left(error)
 *
 * // Try paths
 * Path.attempt(() -> riskyCode())
 *
 * // From existing types
 * Path.from(maybe)
 * Path.from(either)
 * }</pre>
 */
@NullMarked
public final class Path {

    private Path() {
        throw new UnsupportedOperationException("Factory class");
    }

    // ==================== Maybe Paths ====================

    /**
     * Creates a MaybePath containing Just with the given value.
     *
     * @param value the value (must not be null)
     * @param <A> the value type
     * @return a MaybePath containing Just
     * @throws NullPointerException if value is null
     */
    public static <A> MaybePath<A> maybe(A value) {
        return MaybePath.just(value);
    }

    /**
     * Creates a MaybePath from a nullable value.
     *
     * @param value the possibly-null value
     * @param <A> the value type
     * @return Just if non-null, Nothing if null
     */
    public static <A> MaybePath<A> maybeNullable(@Nullable A value) {
        return MaybePath.fromNullable(value);
    }

    /**
     * Creates an empty MaybePath (Nothing).
     *
     * @param <A> the value type
     * @return a MaybePath containing Nothing
     */
    public static <A> MaybePath<A> nothing() {
        return MaybePath.nothing();
    }

    // ==================== Either Paths ====================

    /**
     * Creates an EitherPath containing Right (success).
     *
     * @param value the success value
     * @param <E> the error type
     * @param <A> the value type
     * @return an EitherPath containing Right
     */
    public static <E, A> EitherPath<E, A> right(A value) {
        return EitherPath.right(value);
    }

    /**
     * Creates an EitherPath containing Left (error).
     *
     * @param error the error value
     * @param <E> the error type
     * @param <A> the value type
     * @return an EitherPath containing Left
     */
    public static <E, A> EitherPath<E, A> left(E error) {
        return EitherPath.left(error);
    }

    // ==================== Try Paths ====================

    /**
     * Creates a successful TryPath.
     *
     * @param value the success value
     * @param <A> the value type
     * @return a TryPath containing Success
     */
    public static <A> TryPath<A> success(A value) {
        return TryPath.success(value);
    }

    /**
     * Creates a failed TryPath.
     *
     * @param error the error
     * @param <A> the value type
     * @return a TryPath containing Failure
     */
    public static <A> TryPath<A> failure(Throwable error) {
        return TryPath.failure(error);
    }

    /**
     * Creates a TryPath by attempting a potentially throwing operation.
     *
     * @param supplier the operation that may throw
     * @param <A> the result type
     * @return Success if operation succeeds, Failure if it throws
     */
    public static <A> TryPath<A> attempt(ThrowingSupplier<A> supplier) {
        return TryPath.attempt(supplier);
    }

    // ==================== IO Paths ====================

    /**
     * Creates an IOPath wrapping a side-effecting computation.
     *
     * @param effect the side effect
     * @param <A> the result type
     * @return an IOPath wrapping the effect
     */
    public static <A> IOPath<A> io(Supplier<A> effect) {
        return IOPath.of(effect);
    }

    /**
     * Creates an IOPath with a deferred computation.
     *
     * @param computation the deferred computation
     * @param <A> the result type
     * @return an IOPath wrapping the deferred computation
     */
    public static <A> IOPath<A> delay(Supplier<A> computation) {
        return IOPath.delay(computation);
    }

    // ==================== From Existing Types ====================

    /**
     * Creates a MaybePath from an existing Maybe.
     *
     * @param maybe the Maybe to wrap
     * @param <A> the value type
     * @return a MaybePath wrapping the Maybe
     */
    public static <A> MaybePath<A> from(Maybe<A> maybe) {
        return MaybePath.of(maybe);
    }

    /**
     * Creates an EitherPath from an existing Either.
     *
     * @param either the Either to wrap
     * @param <E> the error type
     * @param <A> the value type
     * @return an EitherPath wrapping the Either
     */
    public static <E, A> EitherPath<E, A> from(Either<E, A> either) {
        return EitherPath.of(either);
    }

    /**
     * Creates a TryPath from an existing Try.
     *
     * @param tryValue the Try to wrap
     * @param <A> the value type
     * @return a TryPath wrapping the Try
     */
    public static <A> TryPath<A> from(Try<A> tryValue) {
        return TryPath.of(tryValue);
    }

    /**
     * Creates an IOPath from an existing IO.
     *
     * @param io the IO to wrap
     * @param <A> the value type
     * @return an IOPath wrapping the IO
     */
    public static <A> IOPath<A> from(IO<A> io) {
        return IOPath.of(io);
    }

    // ==================== From Java Standard Library ====================

    /**
     * Creates a MaybePath from a Java Optional.
     *
     * @param optional the Optional to convert
     * @param <A> the value type
     * @return a MaybePath (Just if present, Nothing if empty)
     */
    public static <A> MaybePath<A> fromOptional(Optional<A> optional) {
        return MaybePath.fromOptional(optional);
    }

    /**
     * Creates a TryPath from a CompletableFuture (blocking).
     *
     * @param future the future to convert
     * @param <A> the value type
     * @return Success if future completes, Failure if it fails
     */
    public static <A> TryPath<A> fromCompletableFuture(CompletableFuture<A> future) {
        return attempt(() -> future.get());
    }

    // ==================== Parallel Composition ====================

    /**
     * Combines two paths using a combining function.
     *
     * @param path1 first path
     * @param path2 second path
     * @param <A> first path value type
     * @param <B> second path value type
     * @return a tuple path that can be mapped
     */
    public static <A, B> MaybePath<Tuple2<A, B>> zip(
            MaybePath<A> path1,
            MaybePath<B> path2) {
        return path1.map2(path2, Tuple2::of);
    }

    /**
     * Combines three paths.
     */
    public static <A, B, C> MaybePath<Tuple3<A, B, C>> zip(
            MaybePath<A> path1,
            MaybePath<B> path2,
            MaybePath<C> path3) {
        return path1.map3(path2, path3, Tuple3::of);
    }
}
```

## Implementation: Capability Interfaces

### Composable.java

```java
package org.higherkindedj.hkt.effect.capability;

import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.annotations.NullMarked;

/**
 * Base capability for all path types: mapping and observation.
 *
 * @param <A> the value type
 */
@NullMarked
public sealed interface Composable<A>
    permits Chainable, org.higherkindedj.hkt.effect.GenericPath {

    /**
     * Transforms the contained value.
     *
     * @param f the transformation function
     * @param <B> the result type
     * @return a new path with the transformed value
     */
    <B> Composable<B> map(Function<? super A, ? extends B> f);

    /**
     * Observes the value without modification.
     *
     * @param observer receives the value if present
     * @return this path unchanged
     */
    Composable<A> peek(Consumer<? super A> observer);
}
```

### Chainable.java

```java
package org.higherkindedj.hkt.effect.capability;

import java.util.function.Function;
import org.jspecify.annotations.NullMarked;

/**
 * Monadic composition capability: chaining effectful operations.
 *
 * @param <A> the value type
 */
@NullMarked
public sealed interface Chainable<A> extends Composable<A>
    permits Recoverable, Effectful, org.higherkindedj.hkt.effect.GenericPath {

    /**
     * Chains with an effect-returning function (FocusPath-style).
     *
     * @param f function returning the next effect
     * @param <B> the result type
     * @return a new path with the chained result
     */
    <B> Chainable<B> via(Function<? super A, ? extends Chainable<B>> f);

    /**
     * Alias for {@link #via(Function)}.
     */
    default <B> Chainable<B> then(Function<? super A, ? extends Chainable<B>> f) {
        return via(f);
    }

    /**
     * Sequences this path with another, discarding this value.
     *
     * @param next the next path
     * @param <B> the result type
     * @return the next path if this succeeds
     */
    default <B> Chainable<B> andThen(Chainable<B> next) {
        return via(_ -> next);
    }

    /**
     * Chains with a function returning the same path type (flatMap).
     *
     * @param f the chaining function
     * @param <B> the result type
     * @return a new path with the chained result
     */
    <B> Chainable<B> flatMap(Function<? super A, ? extends Chainable<B>> f);
}
```

### Recoverable.java

```java
package org.higherkindedj.hkt.effect.capability;

import java.util.function.Function;
import org.jspecify.annotations.NullMarked;

/**
 * Error recovery capability for fallible effects.
 *
 * @param <E> the error type
 * @param <A> the value type
 */
@NullMarked
public sealed interface Recoverable<E, A> extends Chainable<A>
    permits org.higherkindedj.hkt.effect.MaybePath,
            org.higherkindedj.hkt.effect.EitherPath,
            org.higherkindedj.hkt.effect.TryPath,
            org.higherkindedj.hkt.effect.ValidatedPath {

    /**
     * Recovers from an error by providing a replacement value.
     *
     * @param handler converts error to success value
     * @return a path that always succeeds
     */
    Recoverable<E, A> recover(Function<? super E, ? extends A> handler);

    /**
     * Recovers from an error with another path.
     *
     * @param handler converts error to another path
     * @return this path if success, handler result if error
     */
    Recoverable<E, A> recoverWith(Function<? super E, ? extends Recoverable<E, A>> handler);

    /**
     * Transforms the error type.
     *
     * @param f error transformation
     * @param <E2> the new error type
     * @return a path with transformed error type
     */
    <E2> Recoverable<E2, A> mapError(Function<? super E, ? extends E2> f);

    /**
     * Returns this if success, otherwise the fallback.
     *
     * @param fallback the fallback path
     * @return this or fallback
     */
    Recoverable<E, A> orElse(Recoverable<E, A> fallback);
}
```

## Implementation: Concrete Path Type

### MaybePath.java (Updated)

```java
// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License.
package org.higherkindedj.hkt.effect;

import java.util.Objects;
import java.util.Optional;
import java.util.function.*;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.effect.capability.Recoverable;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A fluent composition wrapper for {@link Maybe}.
 *
 * <p>MaybePath provides FocusPath-style composition for optional values,
 * enabling fluent navigation through potentially absent data.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * String name = Path.maybe(userId)
 *     .via(id -> userRepo.findById(id))
 *     .map(User::getName)
 *     .getOrElse("Anonymous");
 * }</pre>
 *
 * @param <A> the value type
 */
@NullMarked
public final class MaybePath<A> implements Recoverable<Unit, A> {

    private final Maybe<A> value;

    private MaybePath(Maybe<A> value) {
        this.value = Objects.requireNonNull(value, "value must not be null");
    }

    // ==================== Core Operations ====================

    /**
     * Extracts the underlying Maybe.
     */
    public Maybe<A> run() {
        return value;
    }

    /**
     * Converts to Java Optional.
     */
    public Optional<A> toOptional() {
        return value.toOptional();
    }

    /**
     * Converts to Stream (0 or 1 elements).
     */
    public Stream<A> stream() {
        return value.isJust() ? Stream.of(value.get()) : Stream.empty();
    }

    // ==================== Extraction ====================

    public A getOrElse(A defaultValue) {
        return value.getOrElse(defaultValue);
    }

    public A getOrElse(Supplier<? extends A> defaultSupplier) {
        return value.getOrElse(defaultSupplier);
    }

    public A getOrThrow() {
        return value.get();
    }

    public <X extends Throwable> A getOrThrow(Supplier<? extends X> exceptionSupplier) throws X {
        if (value.isNothing()) {
            throw exceptionSupplier.get();
        }
        return value.get();
    }

    // ==================== Predicates ====================

    public boolean isPresent() {
        return value.isJust();
    }

    public boolean isEmpty() {
        return value.isNothing();
    }

    public boolean contains(A testValue) {
        return value.isJust() && Objects.equals(value.get(), testValue);
    }

    // ==================== Functor (Composable) ====================

    @Override
    public <B> MaybePath<B> map(Function<? super A, ? extends B> f) {
        Objects.requireNonNull(f, "mapper must not be null");
        return new MaybePath<>(value.map(f));
    }

    @Override
    public MaybePath<A> peek(Consumer<? super A> observer) {
        Objects.requireNonNull(observer, "observer must not be null");
        value.ifJust(observer);
        return this;
    }

    // ==================== Monad (Chainable) ====================

    @Override
    public <B> MaybePath<B> flatMap(Function<? super A, ? extends Recoverable<Unit, B>> f) {
        Objects.requireNonNull(f, "flatMap function must not be null");
        Maybe<B> result = value.flatMap(a -> {
            Recoverable<Unit, B> path = f.apply(a);
            if (path instanceof MaybePath<B> mp) {
                return mp.value;
            }
            throw new IllegalArgumentException("Expected MaybePath");
        });
        return new MaybePath<>(result);
    }

    @Override
    public <B> MaybePath<B> via(Function<? super A, ? extends Recoverable<Unit, B>> f) {
        return flatMap(f);
    }

    /**
     * FocusPath-style composition with Maybe-returning function.
     */
    public <B> MaybePath<B> via(Function<? super A, ? extends Maybe<B>> f) {
        Objects.requireNonNull(f, "via function must not be null");
        return new MaybePath<>(value.flatMap(f));
    }

    /**
     * Alias for via.
     */
    public <B> MaybePath<B> then(Function<? super A, ? extends Maybe<B>> f) {
        return via(f);
    }

    /**
     * Composes with a function that may return null.
     */
    public <B> MaybePath<B> viaNullable(Function<? super A, ? extends @Nullable B> f) {
        Objects.requireNonNull(f, "function must not be null");
        return map(f).filter(Objects::nonNull);
    }

    // ==================== Applicative ====================

    public <B, C> MaybePath<C> map2(MaybePath<B> other, BiFunction<A, B, C> f) {
        Objects.requireNonNull(other, "other must not be null");
        Objects.requireNonNull(f, "combiner must not be null");
        return flatMap(a -> other.map(b -> f.apply(a, b)));
    }

    public <B, C, D> MaybePath<D> map3(
            MaybePath<B> pb,
            MaybePath<C> pc,
            Function3<A, B, C, D> f) {
        return flatMap(a -> pb.flatMap(b -> pc.map(c -> f.apply(a, b, c))));
    }

    // ==================== Filtering ====================

    public MaybePath<A> filter(Predicate<? super A> predicate) {
        Objects.requireNonNull(predicate, "predicate must not be null");
        return new MaybePath<>(value.filter(predicate));
    }

    // ==================== Recovery (Recoverable) ====================

    @Override
    public MaybePath<A> recover(Function<? super Unit, ? extends A> handler) {
        Objects.requireNonNull(handler, "handler must not be null");
        if (value.isNothing()) {
            return just(handler.apply(Unit.INSTANCE));
        }
        return this;
    }

    /**
     * Recovers with a supplier (more natural for Maybe).
     */
    public MaybePath<A> recover(Supplier<? extends A> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        if (value.isNothing()) {
            return just(supplier.get());
        }
        return this;
    }

    @Override
    public MaybePath<A> recoverWith(Function<? super Unit, ? extends Recoverable<Unit, A>> handler) {
        Objects.requireNonNull(handler, "handler must not be null");
        if (value.isNothing()) {
            Recoverable<Unit, A> result = handler.apply(Unit.INSTANCE);
            if (result instanceof MaybePath<A> mp) {
                return mp;
            }
            throw new IllegalArgumentException("Expected MaybePath");
        }
        return this;
    }

    @Override
    public <E2> MaybePath<A> mapError(Function<? super Unit, ? extends E2> f) {
        return this;  // MaybePath has no error to map
    }

    @Override
    public MaybePath<A> orElse(Recoverable<Unit, A> fallback) {
        Objects.requireNonNull(fallback, "fallback must not be null");
        if (value.isNothing() && fallback instanceof MaybePath<A> mp) {
            return mp;
        }
        return this;
    }

    /**
     * Returns this if Just, otherwise the fallback.
     */
    public MaybePath<A> orElse(MaybePath<A> fallback) {
        Objects.requireNonNull(fallback, "fallback must not be null");
        return value.isNothing() ? fallback : this;
    }

    // ==================== Cross-Type Conversion ====================

    /**
     * Converts to EitherPath, using provided error if Nothing.
     */
    public <E> EitherPath<E, A> toEitherPath(E errorIfNothing) {
        return EitherPath.of(value.toEither(errorIfNothing));
    }

    /**
     * Converts to EitherPath with lazy error.
     */
    public <E> EitherPath<E, A> toEitherPath(Supplier<E> errorSupplier) {
        if (value.isNothing()) {
            return EitherPath.left(errorSupplier.get());
        }
        return EitherPath.right(value.get());
    }

    // ==================== Folding ====================

    public <B> B fold(Supplier<B> onNothing, Function<A, B> onJust) {
        return value.fold(onNothing, onJust);
    }

    // ==================== Conditional Execution ====================

    public void ifPresent(Consumer<? super A> action) {
        value.ifJust(action);
    }

    public void ifPresentOrElse(Consumer<? super A> action, Runnable emptyAction) {
        if (value.isJust()) {
            action.accept(value.get());
        } else {
            emptyAction.run();
        }
    }

    // ==================== Debugging ====================

    public MaybePath<A> traced(Consumer<Maybe<A>> observer) {
        Objects.requireNonNull(observer, "observer must not be null");
        observer.accept(value);
        return this;
    }

    // ==================== Factory Methods ====================

    public static <A> MaybePath<A> of(Maybe<A> maybe) {
        return new MaybePath<>(maybe);
    }

    public static <A> MaybePath<A> just(A value) {
        return new MaybePath<>(Maybe.just(value));
    }

    public static <A> MaybePath<A> nothing() {
        return new MaybePath<>(Maybe.nothing());
    }

    public static <A> MaybePath<A> fromNullable(@Nullable A value) {
        return new MaybePath<>(Maybe.fromNullable(value));
    }

    public static <A> MaybePath<A> fromOptional(Optional<A> optional) {
        return new MaybePath<>(Maybe.fromOptional(optional));
    }

    // ==================== Object Methods ====================

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof MaybePath<?> other)) return false;
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "MaybePath(" + value + ")";
    }
}
```

## Implementation: Supporting Types

### ThrowingSupplier.java

```java
package org.higherkindedj.hkt.effect.support;

/**
 * A supplier that may throw checked exceptions.
 */
@FunctionalInterface
public interface ThrowingSupplier<A> {
    A get() throws Throwable;
}
```

### Function3.java

Already exists in `org.higherkindedj.hkt.function.Function3` - reuse it.

## Modifications to Existing Types

### Adding asPath() Default Methods

```java
// In Maybe.java
default MaybePath<T> asPath() {
    return MaybePath.of(this);
}

// In Either.java
default EitherPath<L, R> asPath() {
    return EitherPath.of(this);
}

// In Try.java
default TryPath<T> asPath() {
    return TryPath.of(this);
}

// In IO.java
default IOPath<A> asPath() {
    return IOPath.of(this);
}
```

## Module Configuration

### module-info.java

```java
module org.higherkindedj.hkt {
    // Existing exports...

    // New exports
    exports org.higherkindedj.hkt.effect;
    exports org.higherkindedj.hkt.effect.capability;
    exports org.higherkindedj.hkt.effect.spi;        // Future
    exports org.higherkindedj.hkt.effect.annotation;  // Future

    // SPI (Future)
    uses org.higherkindedj.hkt.effect.spi.PathProvider;
    provides org.higherkindedj.hkt.effect.spi.PathProvider with
        org.higherkindedj.hkt.effect.MaybePathProvider,
        org.higherkindedj.hkt.effect.EitherPathProvider,
        org.higherkindedj.hkt.effect.TryPathProvider,
        org.higherkindedj.hkt.effect.IOPathProvider;
}
```

## Implementation Checklist

### Phase 1: Core Types
- [x] Create `org.higherkindedj.hkt.effect` package
- [x] Create `org.higherkindedj.hkt.effect.capability` package
- [x] Implement `Composable.java`
- [x] Implement `Combinable.java`
- [x] Implement `Chainable.java`
- [x] Implement `Recoverable.java`
- [x] Implement `Effectful.java`
- [x] Implement `Path.java` factory
- [x] Implement `MaybePath.java`
- [x] Implement `EitherPath.java`
- [x] Implement `TryPath.java`
- [x] Implement `IOPath.java`
- [x] Unit tests with 100% coverage
- [x] Law verification tests (Functor/Monad laws)
- [x] Property-based tests with jQwik
- [x] Book chapter documentation (7 chapters in hkj-book/src/effect/)
- [x] Runnable examples in hkj-examples module (5 examples)
- [x] Update EXAMPLES_GUIDE.md

### Phase 2: Extensions
- [ ] Implement `ValidatedPath.java`
- [ ] Implement `GenericPath.java`
- [ ] Implement `Accumulating.java`

### Phase 3: SPI
- [ ] Create `org.higherkindedj.hkt.effect.spi` package
- [ ] Implement `PathProvider.java`
- [ ] Implement `PathRegistry.java`
- [ ] Implement provider implementations
- [ ] Configure ServiceLoader

### Phase 4: Annotations
- [ ] Create `org.higherkindedj.hkt.effect.annotation` package
- [ ] Implement annotation types
- [ ] Create `org.higherkindedj.hkt.effect.processor` package
- [ ] Implement `PathProcessor.java`
- [ ] Implement generators
- [ ] Configure annotation processor

### Phase 5: Module
- [ ] Update `module-info.java` with SPI
- [ ] Add Javadoc to all public APIs
- [ ] Create additional usage examples
