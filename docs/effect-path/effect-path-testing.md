# EffectPath API Testing Guide

> **Status**: Living Document (v2.0)
> **Last Updated**: 2025-01-15
> **Scope**: Test strategies, patterns, and quality requirements

## Overview

This document describes the comprehensive testing strategy for the EffectPath API, including functional tests, property-based tests, law verification, annotation processor tests, and non-functional quality requirements.

## Test Directory Structure

```
hkj-core/src/test/java/org/higherkindedj/hkt/path/
├── MaybePathTest.java              # Core functionality tests
├── MaybePathPropertyTest.java      # Property-based tests (jQwik)
├── MaybePathLawsTest.java          # Functor/Monad law verification
├── EitherPathTest.java
├── EitherPathPropertyTest.java
├── EitherPathLawsTest.java
├── TryPathTest.java
├── TryPathPropertyTest.java
├── IOPathTest.java
├── PathFactoryTest.java            # Path factory tests
├── PathIntegrationTest.java        # Cross-type conversion tests
├── FocusPathIntegrationTest.java   # Optics integration tests
│
├── capability/                     # Capability interface tests
│   ├── ComposableContractTest.java
│   ├── ChainableContractTest.java
│   └── RecoverableContractTest.java
│
├── spi/                            # SPI tests
│   ├── PathProviderTest.java
│   ├── PathRegistryTest.java
│   └── ServiceLoaderIntegrationTest.java
│
├── processor/                      # Annotation processor tests
│   ├── PathProcessorTest.java
│   ├── PathSourceGeneratorTest.java
│   ├── PathBridgeGeneratorTest.java
│   └── ProcessorValidationTest.java
│
├── assertions/                     # Custom AssertJ assertions
│   ├── MaybePathAssert.java
│   ├── EitherPathAssert.java
│   ├── TryPathAssert.java
│   └── PathAssertions.java         # Static import facade
│
└── fixtures/                       # Test fixtures and helpers
    ├── TestDomain.java             # Domain objects for testing
    ├── ArbitraryProviders.java     # jQwik arbitrary providers
    └── PathTestHelpers.java        # Common test utilities
```

## Testing Principles

### 1. Mirror Existing Patterns

Follow the same patterns used for `Maybe`, `Either`, `Try`, and `FocusPath` tests:

- `@Nested` test classes for organization
- `@DisplayName` for readable test names
- Custom assertions extending AssertJ
- Property-based tests with jQwik
- Law verification with `@TestFactory`

### 2. Test the Wrapper, Not the Wrapped

Path types delegate to underlying types. Focus tests on:

- Wrapper creation and unwrapping
- Delegation correctness
- `via`/`then` vocabulary
- Cross-type conversions
- Error handling specific to paths

### 3. Verify Law Compliance

Since paths wrap lawful types, verify that laws still hold through the wrapper layer.

### 4. Test Non-Functional Requirements

- Performance benchmarks for wrapper overhead
- Memory usage verification
- Thread safety (immutability)
- Null safety compliance

## Custom Assertions

### PathAssertions.java (Facade)

```java
package org.higherkindedj.hkt.path.assertions;

import org.higherkindedj.hkt.path.*;

/**
 * Static import facade for all Path assertions.
 *
 * <p>Usage:
 * <pre>{@code
 * import static org.higherkindedj.hkt.path.assertions.PathAssertions.*;
 *
 * assertThatPath(maybePath).isJust().hasValue("expected");
 * assertThatPath(eitherPath).isRight().hasRightValue(42);
 * }</pre>
 */
public final class PathAssertions {

    private PathAssertions() {}

    public static <A> MaybePathAssert<A> assertThatPath(MaybePath<A> actual) {
        return MaybePathAssert.assertThatPath(actual);
    }

    public static <E, A> EitherPathAssert<E, A> assertThatPath(EitherPath<E, A> actual) {
        return EitherPathAssert.assertThatPath(actual);
    }

    public static <A> TryPathAssert<A> assertThatPath(TryPath<A> actual) {
        return TryPathAssert.assertThatPath(actual);
    }
}
```

### MaybePathAssert.java

```java
package org.higherkindedj.hkt.path.assertions;

import java.util.Objects;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.path.MaybePath;

public class MaybePathAssert<A> extends AbstractAssert<MaybePathAssert<A>, MaybePath<A>> {

    private MaybePathAssert(MaybePath<A> actual) {
        super(actual, MaybePathAssert.class);
    }

    public static <A> MaybePathAssert<A> assertThatPath(MaybePath<A> actual) {
        return new MaybePathAssert<>(actual);
    }

    public MaybePathAssert<A> isJust() {
        isNotNull();
        if (!actual.run().isJust()) {
            failWithMessage("Expected path to contain Just but was Nothing");
        }
        return this;
    }

    public MaybePathAssert<A> isNothing() {
        isNotNull();
        if (!actual.run().isNothing()) {
            failWithMessage("Expected path to contain Nothing but was Just<%s>",
                actual.run().get());
        }
        return this;
    }

    public MaybePathAssert<A> hasValue(A expected) {
        isJust();
        A actualValue = actual.run().get();
        if (!Objects.equals(actualValue, expected)) {
            failWithMessage("Expected path to have value <%s> but was <%s>",
                expected, actualValue);
        }
        return this;
    }

    public MaybePathAssert<A> hasValueSatisfying(Consumer<? super A> requirements) {
        isJust();
        requirements.accept(actual.run().get());
        return this;
    }

    public MaybePathAssert<A> runsSameAs(Maybe<A> expected) {
        isNotNull();
        Maybe<A> actualMaybe = actual.run();
        if (!actualMaybe.equals(expected)) {
            failWithMessage("Expected path.run() to equal <%s> but was <%s>",
                expected, actualMaybe);
        }
        return this;
    }

    public MaybePathAssert<A> runsIdenticalTo(Maybe<A> expected) {
        isNotNull();
        if (actual.run() != expected) {
            failWithMessage("Expected path.run() to be identical to <%s>", expected);
        }
        return this;
    }

    public MaybePathAssert<A> isPresent() {
        return isJust();
    }

    public MaybePathAssert<A> isEmpty() {
        return isNothing();
    }
}
```

### EitherPathAssert.java

```java
package org.higherkindedj.hkt.path.assertions;

import java.util.Objects;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;
import org.higherkindedj.hkt.path.EitherPath;

public class EitherPathAssert<E, A>
        extends AbstractAssert<EitherPathAssert<E, A>, EitherPath<E, A>> {

    private EitherPathAssert(EitherPath<E, A> actual) {
        super(actual, EitherPathAssert.class);
    }

    public static <E, A> EitherPathAssert<E, A> assertThatPath(EitherPath<E, A> actual) {
        return new EitherPathAssert<>(actual);
    }

    public EitherPathAssert<E, A> isRight() {
        isNotNull();
        if (!actual.run().isRight()) {
            failWithMessage("Expected path to be Right but was Left<%s>",
                actual.run().getLeft());
        }
        return this;
    }

    public EitherPathAssert<E, A> isLeft() {
        isNotNull();
        if (!actual.run().isLeft()) {
            failWithMessage("Expected path to be Left but was Right<%s>",
                actual.run().getRight());
        }
        return this;
    }

    public EitherPathAssert<E, A> hasRightValue(A expected) {
        isRight();
        A actualValue = actual.run().getRight();
        if (!Objects.equals(actualValue, expected)) {
            failWithMessage("Expected Right value <%s> but was <%s>",
                expected, actualValue);
        }
        return this;
    }

    public EitherPathAssert<E, A> hasLeftValue(E expected) {
        isLeft();
        E actualError = actual.run().getLeft();
        if (!Objects.equals(actualError, expected)) {
            failWithMessage("Expected Left value <%s> but was <%s>",
                expected, actualError);
        }
        return this;
    }

    public EitherPathAssert<E, A> hasRightValueSatisfying(Consumer<? super A> requirements) {
        isRight();
        requirements.accept(actual.run().getRight());
        return this;
    }

    public EitherPathAssert<E, A> hasLeftValueSatisfying(Consumer<? super E> requirements) {
        isLeft();
        requirements.accept(actual.run().getLeft());
        return this;
    }
}
```

## Property-Based Tests

### ArbitraryProviders.java

```java
package org.higherkindedj.hkt.path.fixtures;

import net.jqwik.api.*;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.path.*;
import java.util.function.Function;

/**
 * Reusable jQwik arbitrary providers for Path types.
 */
public class ArbitraryProviders {

    @Provide
    public static <A> Arbitrary<MaybePath<A>> maybePaths(Arbitrary<A> values) {
        return Arbitraries.oneOf(
            values.map(MaybePath::just),
            Arbitraries.just(MaybePath.nothing())
        );
    }

    @Provide
    public static Arbitrary<MaybePath<Integer>> maybeIntPaths() {
        return Arbitraries.integers().between(-1000, 1000)
            .injectNull(0.2)
            .map(i -> i == null ? MaybePath.nothing() : MaybePath.just(i));
    }

    @Provide
    public static <E, A> Arbitrary<EitherPath<E, A>> eitherPaths(
            Arbitrary<E> errors,
            Arbitrary<A> values) {
        return Arbitraries.oneOf(
            values.map(EitherPath::right),
            errors.map(EitherPath::left)
        );
    }

    @Provide
    public static Arbitrary<EitherPath<String, Integer>> eitherStringIntPaths() {
        return Arbitraries.oneOf(
            Arbitraries.integers().between(-100, 100).map(EitherPath::right),
            Arbitraries.strings().alpha().ofLength(5).map(EitherPath::left)
        );
    }

    @Provide
    public static Arbitrary<Function<Integer, Integer>> intFunctions() {
        return Arbitraries.of(
            x -> x + 1,
            x -> x * 2,
            x -> Math.abs(x),
            x -> -x,
            Function.identity()
        );
    }

    @Provide
    public static Arbitrary<Function<Integer, MaybePath<Integer>>> intToMaybePathFunctions() {
        return Arbitraries.of(
            x -> x > 0 ? MaybePath.just(x) : MaybePath.nothing(),
            x -> MaybePath.just(x * 2),
            x -> MaybePath.just(Math.abs(x)),
            _ -> MaybePath.nothing()
        );
    }
}
```

### MaybePathPropertyTest.java

```java
package org.higherkindedj.hkt.path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.path.fixtures.ArbitraryProviders.*;

import java.util.function.Function;
import net.jqwik.api.*;
import org.higherkindedj.hkt.maybe.Maybe;

@DisplayName("MaybePath Property Tests")
class MaybePathPropertyTest {

    // ==================== Functor Laws ====================

    @Property
    @Label("Functor Identity: path.map(id) == path")
    void functorIdentity(@ForAll("maybeIntPaths") MaybePath<Integer> path) {
        MaybePath<Integer> mapped = path.map(Function.identity());
        assertThat(mapped.run()).isEqualTo(path.run());
    }

    @Property
    @Label("Functor Composition: path.map(f).map(g) == path.map(g.compose(f))")
    void functorComposition(
            @ForAll("maybeIntPaths") MaybePath<Integer> path,
            @ForAll("intFunctions") Function<Integer, Integer> f,
            @ForAll("intFunctions") Function<Integer, Integer> g) {

        MaybePath<Integer> leftSide = path.map(f).map(g);
        MaybePath<Integer> rightSide = path.map(f.andThen(g));

        assertThat(leftSide.run()).isEqualTo(rightSide.run());
    }

    // ==================== Monad Laws ====================

    @Property
    @Label("Monad Left Identity: MaybePath.just(a).flatMap(f) == f(a)")
    void monadLeftIdentity(
            @ForAll @IntRange(min = -100, max = 100) int a,
            @ForAll("intToMaybePathFunctions") Function<Integer, MaybePath<Integer>> f) {

        MaybePath<Integer> leftSide = MaybePath.just(a).flatMap(f);
        MaybePath<Integer> rightSide = f.apply(a);

        assertThat(leftSide.run()).isEqualTo(rightSide.run());
    }

    @Property
    @Label("Monad Right Identity: path.flatMap(MaybePath::just) == path")
    void monadRightIdentity(@ForAll("maybeIntPaths") MaybePath<Integer> path) {
        MaybePath<Integer> result = path.flatMap(MaybePath::just);
        assertThat(result.run()).isEqualTo(path.run());
    }

    @Property
    @Label("Monad Associativity: (m >>= f) >>= g == m >>= (x -> f(x) >>= g)")
    void monadAssociativity(
            @ForAll("maybeIntPaths") MaybePath<Integer> path,
            @ForAll("intToMaybePathFunctions") Function<Integer, MaybePath<Integer>> f,
            @ForAll("intToMaybePathFunctions") Function<Integer, MaybePath<Integer>> g) {

        MaybePath<Integer> leftSide = path.flatMap(f).flatMap(g);
        MaybePath<Integer> rightSide = path.flatMap(a -> f.apply(a).flatMap(g));

        assertThat(leftSide.run()).isEqualTo(rightSide.run());
    }

    // ==================== Via/Then Equivalence ====================

    @Property
    @Label("via() and then() are equivalent")
    void viaAndThenEquivalent(@ForAll("maybeIntPaths") MaybePath<Integer> path) {
        Function<Integer, Maybe<Integer>> f = x -> Maybe.just(x * 2);

        MaybePath<Integer> viaResult = path.via(f);
        MaybePath<Integer> thenResult = path.then(f);

        assertThat(viaResult.run()).isEqualTo(thenResult.run());
    }

    // ==================== Wrapper Integrity ====================

    @Property
    @Label("of(maybe).run() returns the same Maybe instance")
    void ofRunPreservesInstance(@ForAll("maybeIntPaths") MaybePath<Integer> path) {
        Maybe<Integer> maybe = path.run();
        MaybePath<Integer> rewrapped = MaybePath.of(maybe);

        assertThat(rewrapped.run()).isSameAs(maybe);
    }

    @Property
    @Label("Equality is based on underlying Maybe")
    void equalityBasedOnMaybe(@ForAll("maybeIntPaths") MaybePath<Integer> path) {
        MaybePath<Integer> copy = MaybePath.of(path.run());
        assertThat(path).isEqualTo(copy);
    }
}
```

## Annotation Processor Tests

### PathProcessorTest.java

```java
package org.higherkindedj.hkt.path.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.*;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

@DisplayName("PathProcessor Annotation Processor Tests")
class PathProcessorTest {

    @Nested
    @DisplayName("@PathSource Processing")
    class PathSourceProcessing {

        @Test
        @DisplayName("generates valid Path wrapper for interface")
        void generatesWrapperForInterface() {
            Compilation compilation = javac()
                .withProcessors(new PathProcessor())
                .compile(JavaFileObjects.forSourceString("test.MyEffect",
                    """
                    package test;
                    import org.higherkindedj.hkt.path.annotation.PathSource;
                    import java.util.function.Function;

                    @PathSource(witness = MyEffectKind.Witness.class)
                    public interface MyEffect<A> {
                        <B> MyEffect<B> map(Function<? super A, ? extends B> f);
                        <B> MyEffect<B> flatMap(Function<? super A, ? extends MyEffect<B>> f);
                    }

                    interface MyEffectKind {
                        interface Witness {}
                    }
                    """
                ));

            assertThat(compilation).succeeded();
            assertThat(compilation)
                .generatedSourceFile("test.MyEffectPath")
                .contentsAsUtf8String()
                .contains("public final class MyEffectPath<A>")
                .contains("public MyEffect<A> run()")
                .contains("public <B> MyEffectPath<B> map(")
                .contains("public <B> MyEffectPath<B> via(");
        }

        @Test
        @DisplayName("fails with clear error when map() is missing")
        void failsWhenMapMissing() {
            Compilation compilation = javac()
                .withProcessors(new PathProcessor())
                .compile(JavaFileObjects.forSourceString("test.BadEffect",
                    """
                    package test;
                    import org.higherkindedj.hkt.path.annotation.PathSource;
                    import java.util.function.Function;

                    @PathSource(witness = Object.class)
                    public interface BadEffect<A> {
                        <B> BadEffect<B> flatMap(Function<? super A, ? extends BadEffect<B>> f);
                        // Missing map()
                    }
                    """
                ));

            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("must have a map() method");
        }

        @Test
        @DisplayName("uses custom class name when specified")
        void usesCustomClassName() {
            Compilation compilation = javac()
                .withProcessors(new PathProcessor())
                .compile(JavaFileObjects.forSourceString("test.MyEffect",
                    """
                    package test;
                    import org.higherkindedj.hkt.path.annotation.PathSource;
                    import java.util.function.Function;

                    @PathSource(witness = MyEffectKind.Witness.class, pathClassName = "CustomPath")
                    public interface MyEffect<A> {
                        <B> MyEffect<B> map(Function<? super A, ? extends B> f);
                        <B> MyEffect<B> flatMap(Function<? super A, ? extends MyEffect<B>> f);
                    }

                    interface MyEffectKind {
                        interface Witness {}
                    }
                    """
                ));

            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.CustomPath");
        }
    }

    @Nested
    @DisplayName("@GeneratePathBridge Processing")
    class PathBridgeProcessing {

        @Test
        @DisplayName("generates bridge methods for @PathVia methods")
        void generatesBridgeMethods() {
            Compilation compilation = javac()
                .withProcessors(new PathProcessor())
                .compile(JavaFileObjects.forSourceString("test.MyService",
                    """
                    package test;
                    import org.higherkindedj.hkt.path.annotation.*;
                    import org.higherkindedj.hkt.maybe.Maybe;
                    import org.higherkindedj.hkt.either.Either;

                    @GeneratePathBridge
                    public interface MyService {
                        @PathVia Maybe<String> findName(Long id);
                        @PathVia Either<String, Integer> compute(String input);
                    }
                    """
                ));

            assertThat(compilation).succeeded();
            assertThat(compilation)
                .generatedSourceFile("test.MyServicePaths")
                .contentsAsUtf8String()
                .contains("public static MaybePath<String> findName(MyService service, Long id)")
                .contains("public static EitherPath<String, Integer> compute(MyService service, String input)");
        }

        @Test
        @DisplayName("respects custom method names in @PathVia")
        void respectsCustomMethodNames() {
            Compilation compilation = javac()
                .withProcessors(new PathProcessor())
                .compile(JavaFileObjects.forSourceString("test.MyService",
                    """
                    package test;
                    import org.higherkindedj.hkt.path.annotation.*;
                    import org.higherkindedj.hkt.maybe.Maybe;

                    @GeneratePathBridge
                    public interface MyService {
                        @PathVia("find") Maybe<String> findNameById(Long id);
                    }
                    """
                ));

            assertThat(compilation).succeeded();
            assertThat(compilation)
                .generatedSourceFile("test.MyServicePaths")
                .contentsAsUtf8String()
                .contains("public static MaybePath<String> find(");
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("reports error for unsupported return type")
        void reportsUnsupportedReturnType() {
            Compilation compilation = javac()
                .withProcessors(new PathProcessor())
                .compile(JavaFileObjects.forSourceString("test.BadService",
                    """
                    package test;
                    import org.higherkindedj.hkt.path.annotation.*;

                    @GeneratePathBridge
                    public interface BadService {
                        @PathVia String notAnEffect(Long id);  // String is not an effect type
                    }
                    """
                ));

            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("must return a supported effect type");
        }

        @Test
        @DisplayName("reports error when @GeneratePathBridge is on a class")
        void reportsErrorOnClass() {
            Compilation compilation = javac()
                .withProcessors(new PathProcessor())
                .compile(JavaFileObjects.forSourceString("test.BadService",
                    """
                    package test;
                    import org.higherkindedj.hkt.path.annotation.*;
                    import org.higherkindedj.hkt.maybe.Maybe;

                    @GeneratePathBridge
                    public class BadService {  // Class, not interface
                        @PathVia public Maybe<String> find(Long id) { return null; }
                    }
                    """
                ));

            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("can only be applied to interfaces");
        }
    }
}
```

## SPI Tests

### PathRegistryTest.java

```java
package org.higherkindedj.hkt.path.spi;

import static org.assertj.core.api.Assertions.*;

import org.higherkindedj.hkt.maybe.*;
import org.higherkindedj.hkt.path.*;
import org.higherkindedj.hkt.path.capability.Chainable;
import org.junit.jupiter.api.*;

@DisplayName("PathRegistry SPI Tests")
class PathRegistryTest {

    @Test
    @DisplayName("discovers built-in providers via ServiceLoader")
    void discoversBuiltInProviders() {
        var maybeWitness = MaybeKind.Witness.class;

        var path = PathRegistry.createPath(
            MaybeKindHelper.MAYBE.widen(Maybe.just(42)),
            maybeWitness
        );

        assertThat(path).isPresent();
        assertThat(path.get()).isInstanceOf(MaybePath.class);
    }

    @Test
    @DisplayName("returns empty for unknown witness type")
    void returnsEmptyForUnknown() {
        var path = PathRegistry.createPath(null, UnknownWitness.class);
        assertThat(path).isEmpty();
    }

    @Test
    @DisplayName("created paths are functional")
    void createdPathsAreFunctional() {
        var maybe = Maybe.just("hello");
        var path = PathRegistry.createPath(
            MaybeKindHelper.MAYBE.widen(maybe),
            MaybeKind.Witness.class
        ).orElseThrow();

        Chainable<Integer> mapped = path.map(String::length);

        assertThat(mapped).isInstanceOf(MaybePath.class);
        assertThat(((MaybePath<Integer>) mapped).run().get()).isEqualTo(5);
    }

    interface UnknownWitness {}
}
```

## Non-Functional Tests

### PathPerformanceTest.java

```java
package org.higherkindedj.hkt.path;

import org.junit.jupiter.api.*;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Path Performance Tests")
class PathPerformanceTest {

    private static final int ITERATIONS = 100_000;

    @Test
    @DisplayName("MaybePath wrapper overhead is minimal")
    void maybePathOverhead() {
        // Warm up
        for (int i = 0; i < 1000; i++) {
            MaybePath.just(i).map(x -> x * 2).run();
        }

        // Direct Maybe operations
        long directStart = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            Maybe.just(i).map(x -> x * 2);
        }
        long directTime = System.nanoTime() - directStart;

        // Path-wrapped operations
        long pathStart = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            MaybePath.just(i).map(x -> x * 2).run();
        }
        long pathTime = System.nanoTime() - pathStart;

        // Path overhead should be less than 2x
        double ratio = (double) pathTime / directTime;
        assertThat(ratio)
            .as("Path overhead ratio (path/direct)")
            .isLessThan(2.0);
    }

    @Test
    @DisplayName("long path chains complete in reasonable time")
    void longChainPerformance() {
        assertTimeout(Duration.ofSeconds(1), () -> {
            MaybePath<Integer> path = MaybePath.just(0);
            for (int i = 0; i < 10_000; i++) {
                path = path.map(x -> x + 1);
            }
            assertThat(path.run().get()).isEqualTo(10_000);
        });
    }
}
```

### PathThreadSafetyTest.java

```java
package org.higherkindedj.hkt.path;

import org.junit.jupiter.api.*;
import java.util.concurrent.*;
import java.util.stream.*;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Path Thread Safety Tests")
class PathThreadSafetyTest {

    @Test
    @DisplayName("MaybePath is safely shareable across threads")
    void maybePathThreadSafe() throws Exception {
        MaybePath<Integer> sharedPath = MaybePath.just(42);

        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            var futures = IntStream.range(0, 100)
                .mapToObj(i -> executor.submit(() ->
                    sharedPath.map(x -> x * 2).run().get()
                ))
                .toList();

            for (Future<Integer> future : futures) {
                assertThat(future.get()).isEqualTo(84);
            }
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("path operations don't modify original")
    void pathOperationsDontModifyOriginal() throws Exception {
        MaybePath<Integer> original = MaybePath.just(1);

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            // Multiple threads modify "the same" path
            var futures = IntStream.range(0, 100)
                .mapToObj(i -> executor.submit(() -> {
                    MaybePath<Integer> modified = original.map(x -> x + i);
                    return original.run().get();  // Original should be unchanged
                }))
                .toList();

            for (Future<Integer> future : futures) {
                assertThat(future.get()).isEqualTo(1);
            }
        } finally {
            executor.shutdown();
        }
    }
}
```

## Integration Test: FocusPath

### FocusPathIntegrationTest.java

```java
package org.higherkindedj.hkt.path;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.path.assertions.PathAssertions.*;

import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.optics.focus.*;
import org.junit.jupiter.api.*;

@DisplayName("FocusPath + EffectPath Integration")
class FocusPathIntegrationTest {

    record User(String name, Maybe<Address> address) {}
    record Address(String city, String country) {}

    interface UserFocus {
        static FocusPath<User, String> name() {
            return FocusPath.of(
                User::name,
                (user, name) -> new User(name, user.address())
            );
        }

        static AffinePath<User, Address> address() {
            return AffinePath.ofNullable(
                user -> user.address().orElse(null),
                (user, addr) -> new User(user.name(), Maybe.just(addr))
            );
        }
    }

    @Test
    @DisplayName("FocusPath get result can be lifted into MaybePath")
    void focusPathToMaybePath() {
        User user = new User("Alice", Maybe.just(new Address("NYC", "USA")));

        MaybePath<String> namePath = Path.maybe(UserFocus.name().get(user));

        assertThatPath(namePath).isJust().hasValue("Alice");
    }

    @Test
    @DisplayName("AffinePath naturally aligns with MaybePath")
    void affinePathToMaybePath() {
        User userWithAddress = new User("Bob", Maybe.just(new Address("London", "UK")));
        User userWithoutAddress = new User("Carol", Maybe.nothing());

        MaybePath<String> withCity = UserFocus.address()
            .getMaybe(userWithAddress)
            .map(Address::city);

        MaybePath<String> withoutCity = UserFocus.address()
            .getMaybe(userWithoutAddress)
            .map(Address::city);

        assertThatPath(withCity).isJust().hasValue("London");
        assertThatPath(withoutCity).isNothing();
    }

    @Test
    @DisplayName("Can chain optic navigation with effect composition")
    void chainedOpticAndEffect() {
        User user = new User("Dave", Maybe.just(new Address("Paris", "France")));

        EitherPath<String, String> result = Path.maybe(user)
            .via(u -> UserFocus.address().getMaybe(u).run())
            .map(Address::city)
            .toEitherPath("No address found")
            .via(city -> city.length() > 3
                ? Either.right(city.toUpperCase())
                : Either.left("City name too short"));

        assertThatPath(result).isRight().hasRightValue("PARIS");
    }
}
```

## Test Configuration

### build.gradle.kts additions

```kotlin
dependencies {
    // Existing test dependencies...

    // Compile-testing for annotation processor
    testImplementation("com.google.testing.compile:compile-testing:0.21.0")
}

tasks.test {
    // Enable jQwik
    useJUnitPlatform {
        includeEngines("junit-jupiter", "jqwik")
    }
}
```

## Test Checklist

### Per Path Type
- [ ] Factory methods (of, just/right/success, nothing/left/failure, from*)
- [ ] Core operations (run, getOrElse, getOrThrow)
- [ ] Functor operations (map)
- [ ] Applicative operations (map2, map3)
- [ ] Monad operations (flatMap, andThen)
- [ ] FocusPath-style (via, then)
- [ ] Filtering (filter) if applicable
- [ ] Recovery (orElse, recover, recoverWith)
- [ ] Cross-type conversion (toEitherPath, toMaybePath, etc.)
- [ ] Debugging (traced, peek)
- [ ] Object methods (equals, hashCode, toString)
- [ ] Functor laws (property tests)
- [ ] Monad laws (property tests)

### Annotation Processor
- [ ] @PathSource generates valid Path wrapper
- [ ] @PathSource validates required methods
- [ ] @PathSource respects custom names/packages
- [ ] @GeneratePathBridge generates bridge methods
- [ ] @PathVia handles custom names
- [ ] Error messages are clear and actionable
- [ ] Incremental compilation works correctly

### SPI
- [ ] PathProvider contract tests
- [ ] PathRegistry discovers providers
- [ ] ServiceLoader integration works
- [ ] Custom providers can be registered

### Non-Functional
- [ ] Wrapper overhead is minimal
- [ ] Thread safety (immutability verified)
- [ ] Null safety (no null leaks)
- [ ] Memory usage is reasonable
