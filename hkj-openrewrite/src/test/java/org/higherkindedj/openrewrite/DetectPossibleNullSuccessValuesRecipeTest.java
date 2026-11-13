// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

/**
 * Tests for {@link DetectPossibleNullSuccessValuesRecipe}, over the shapes an adopter's 0.4.x code
 * base held after the 0.5.0 migration had run (#1349).
 */
@DisplayName("DetectPossibleNullSuccessValuesRecipe: values that may reach a success as null")
class DetectPossibleNullSuccessValuesRecipeTest implements RewriteTest {

  /**
   * The HKJ types, and libraries that may return null: one annotated with JSpecify's type-use
   * {@code @Nullable}, which the type model drops, and two with a declaration annotation, which it
   * keeps: Spring Framework 6's {@code @Nullable} and JSR-305's {@code @CheckForNull}. Each stub
   * stands for a jar, since only the sources a test runs over are scanned.
   */
  private static final String[] STUBS = {
    "package org.jspecify.annotations; import java.lang.annotation.*;"
        + " @Target(ElementType.TYPE_USE) @Retention(RetentionPolicy.RUNTIME)"
        + " public @interface Nullable {}",
    "package org.springframework.lang; import java.lang.annotation.*;"
        + " @Target({ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD})"
        + " @Retention(RetentionPolicy.RUNTIME) public @interface Nullable {}",
    "package javax.annotation; import java.lang.annotation.*;"
        + " @Target({ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD})"
        + " @Retention(RetentionPolicy.RUNTIME) public @interface CheckForNull {}",
    "package org.higherkindedj.hkt; public enum Unit { INSTANCE }",
    "package org.higherkindedj.hkt.maybe; public interface Maybe<T> {"
        + " static <T> Maybe<T> fromNullable(T value) { return null; }"
        + " boolean isNothing(); }",
    "package org.higherkindedj.hkt.either; import java.util.function.Function;"
        + " public interface Either<L, R> {"
        + " static <L, R> Either<L, R> right(R value) { return null; }"
        + " static <L, R> Either<L, R> left(L value) { return null; }"
        + " <R2> Either<L, R2> map(Function<? super R, ? extends R2> f);"
        + " <R2> Either<L, R2> flatMap(Function<? super R, ? extends Either<L, R2>> f);"
        + " void ifRight(java.util.function.Consumer<? super R> action); }",
    "package org.higherkindedj.hkt.trymonad; import java.util.function.Function;"
        + " public interface Try<T> {"
        + " static <T> Try<T> of(java.util.function.Supplier<? extends T> s) { return null; }"
        + " static <T> Try<T> attempt(java.util.concurrent.Callable<? extends T> s) {"
        + " return null; }"
        + " <U> Try<U> map(Function<? super T, ? extends U> f);"
        + " <L> org.higherkindedj.hkt.either.Either<L, T> toEither("
        + "Function<? super Throwable, ? extends L> f);"
        + " <U> U fold(Function<? super Throwable, ? extends U> failure,"
        + " Function<? super T, ? extends U> success);"
        + " <U> U foldFailureFirst(Function<? super Throwable, ? extends U> failure,"
        + " Function<? super T, ? extends U> success); }",
    "package org.higherkindedj.hkt.vtask; import java.util.function.Function;"
        + " public interface VTask<A> {"
        + " A execute() throws Throwable;"
        + " static <A> VTask<A> of(java.util.concurrent.Callable<A> c) { return null; }"
        + " default <B> VTask<B> map(Function<? super A, ? extends B> f) { return null; }"
        + " default <B> VTask<B> via(Function<? super A, ? extends VTask<B>> f) {"
        + " return null; }"
        + " default VTask<A> peek(java.util.function.Consumer<? super A> f) { return null; }"
        + " default A join() { return null; } }",
    "package org.example.store; import org.jspecify.annotations.Nullable;"
        + " public class Store {"
        + " public <T> @Nullable T findAndReplace(String query, T document) { return null; } }",
    "package org.example.store; public class LegacyStore {"
        + " @org.springframework.lang.Nullable"
        + " public <T> T findAndReplace(String query, T document) { return null; } }",
    "package com.google.common.cache; public interface Cache<K, V> {"
        + " @javax.annotation.CheckForNull V getIfPresent(Object key); }",
  };

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipe(new DetectPossibleNullSuccessValuesRecipe())
        .parser(JavaParser.fromJavaVersion().dependsOn(STUBS));
  }

  @DocumentExample
  @Test
  @DisplayName("marks a wrapper's @Nullable parameter, and each null passed to a wrapper")
  void marksAWrapperAndTheNullsPassedToIt() {
    rewriteRun(
        java(
            """
            package app;

            import java.util.Objects;
            import org.higherkindedj.hkt.either.Either;
            import org.jspecify.annotations.Nullable;

            public final class Results {
                private Results() {}

                public static <T> Either<String, T> right(@Nullable T value) {
                    return Either.right(value);
                }

                public static <T> Either<String, T> ok(T value) {
                    return Either.right(value);
                }

                public static <T> Either<String, T> logged(@Nullable T value) {
                    System.out.println(value);
                    return Either.left("logged");
                }

                public static <T> Either<String, T> guarded(@Nullable T value) {
                    return value == null ? Either.left("missing") : Either.right(value);
                }

                public static <T> Either<String, T> checked(@Nullable T value) {
                    Objects.requireNonNull(value);
                    return Either.right(value);
                }

                public static Either<String, Object[]> all(Object... values) {
                    return Either.right(values);
                }
            }
            """,
            """
            package app;

            import java.util.Objects;
            import org.higherkindedj.hkt.either.Either;
            import org.jspecify.annotations.Nullable;

            public final class Results {
                private Results() {}

                public static <T> Either<String, T> right(/*~~(May be null, which Either.right now refuses: drop @Nullable so callers pass a value, such as Unit.INSTANCE or a Maybe, or return an error here when it is null)~~>*/@Nullable T value) {
                    return Either.right(value);
                }

                public static <T> Either<String, T> ok(T value) {
                    return Either.right(value);
                }

                public static <T> Either<String, T> logged(@Nullable T value) {
                    System.out.println(value);
                    return Either.left("logged");
                }

                public static <T> Either<String, T> guarded(@Nullable T value) {
                    return value == null ? Either.left("missing") : Either.right(value);
                }

                public static <T> Either<String, T> checked(@Nullable T value) {
                    Objects.requireNonNull(value);
                    return Either.right(value);
                }

                public static Either<String, Object[]> all(Object... values) {
                    return Either.right(values);
                }
            }
            """),
        java(
            """
            package app;

            import static app.Results.ok;
            import static app.Results.right;

            import org.higherkindedj.hkt.either.Either;

            class Orders {
                Either<String, Void> purge() {
                    System.out.println("purged");
                    return right(null);
                }

                Either<String, String> find(String id) {
                    return right(null);
                }

                Either<String, String> name() {
                    return ok("order");
                }

                Either<String, String> blank() {
                    return ok(null);
                }

                Either<String, String> missing() {
                    return Results.guarded(null);
                }

                Either<String, String> required() {
                    return Results.checked(null);
                }

                Either<String, Object[]> listed() {
                    return Results.all(null, "a");
                }
            }
            """,
            """
            package app;

            import static app.Results.ok;
            import static app.Results.right;

            import org.higherkindedj.hkt.either.Either;

            class Orders {
                Either<String, Void> purge() {
                    System.out.println("purged");
                    return /*~~(Passes null on to Either.right, which now throws: pass Unit.INSTANCE for a step with nothing to return)~~>*/right(null);
                }

                Either<String, String> find(String id) {
                    return /*~~(Passes null on to Either.right, which now throws: use a Maybe for a value that may be missing, or an error that says why)~~>*/right(null);
                }

                Either<String, String> name() {
                    return ok("order");
                }

                Either<String, String> blank() {
                    return /*~~(Passes null on to Either.right, which now throws: use a Maybe for a value that may be missing, or an error that says why)~~>*/ok(null);
                }

                Either<String, String> missing() {
                    return Results.guarded(null);
                }

                Either<String, String> required() {
                    return Results.checked(null);
                }

                Either<String, Object[]> listed() {
                    return Results.all(null, "a");
                }
            }
            """));
  }

  @Test
  @DisplayName("marks a null check in a success step that a lazy factory starts")
  void marksANullCheckDownstreamOfALazyFactory() {
    rewriteRun(
        java(
            """
            package app;

            import java.util.List;
            import java.util.Map;
            import java.util.Objects;
            import java.util.Optional;
            import org.example.store.Store;
            import org.higherkindedj.hkt.either.Either;
            import org.higherkindedj.hkt.trymonad.Try;
            import org.higherkindedj.hkt.vtask.VTask;

            class Accounts {
                Either<String, String> save(Store store, String query, String document) {
                    return Try.of(() -> store.findAndReplace(query, document))
                        .toEither(e -> "storage failed")
                        .flatMap(found -> found == null
                            ? Either.<String, String>left("not found")
                            : Either.<String, String>right(document));
                }

                Optional<List<String>> roles(Map<String, List<String>> claims) {
                    return Try.attempt(() -> claims.get("roles"))
                        .foldFailureFirst(_ -> Optional.empty(), roles -> Optional.of(roles == null ? List.of() : roles));
                }

                String label(Map<String, String> names) {
                    return Try.of(() -> names.get("label")).fold(e -> "failed", label -> label != null ? label : "none");
                }

                VTask<String> greeting(Map<String, String> names) {
                    return VTask.of(() -> names.get("greeting")).map(value -> Objects.isNull(value) ? "hello" : value);
                }

                VTask<String> traced(Map<String, String> names) {
                    return VTask.of(() -> names.get("trace"))
                        .peek(value -> System.out.println(value != null))
                        .via(value -> VTask.of(() -> Objects.nonNull(value) ? value : "none"));
                }

                void shown(Map<String, String> names) {
                    Try.of(() -> names.get("shown")).toEither(e -> "failed").ifRight(value -> System.out.println(value == null));
                }
            }
            """,
            """
            package app;

            import java.util.List;
            import java.util.Map;
            import java.util.Objects;
            import java.util.Optional;
            import org.example.store.Store;
            import org.higherkindedj.hkt.either.Either;
            import org.higherkindedj.hkt.trymonad.Try;
            import org.higherkindedj.hkt.vtask.VTask;

            class Accounts {
                Either<String, String> save(Store store, String query, String document) {
                    return Try.of(() -> store.findAndReplace(query, document))
                        .toEither(e -> "storage failed")
                        .flatMap(found -> /*~~(Never null here now, so a value that used to arrive as null may fail the chain before this check: carry it as a Maybe from the call that returns it)~~>*/found == null
                            ? Either.<String, String>left("not found")
                            : Either.<String, String>right(document));
                }

                Optional<List<String>> roles(Map<String, List<String>> claims) {
                    return Try.attempt(() -> claims.get("roles"))
                        .foldFailureFirst(_ -> Optional.empty(), roles -> Optional.of(/*~~(Never null here now, so a value that used to arrive as null may fail the chain before this check: carry it as a Maybe from the call that returns it)~~>*/roles == null ? List.of() : roles));
                }

                String label(Map<String, String> names) {
                    return Try.of(() -> names.get("label")).fold(e -> "failed", label -> /*~~(Never null here now, so a value that used to arrive as null may fail the chain before this check: carry it as a Maybe from the call that returns it)~~>*/label != null ? label : "none");
                }

                VTask<String> greeting(Map<String, String> names) {
                    return VTask.of(() -> names.get("greeting")).map(value -> /*~~(Never null here now, so a value that used to arrive as null may fail the chain before this check: carry it as a Maybe from the call that returns it)~~>*/Objects.isNull(value) ? "hello" : value);
                }

                VTask<String> traced(Map<String, String> names) {
                    return VTask.of(() -> names.get("trace"))
                        .peek(value -> System.out.println(/*~~(Never null here now, so a value that used to arrive as null may fail the chain before this check: carry it as a Maybe from the call that returns it)~~>*/value != null))
                        .via(value -> VTask.of(() -> /*~~(Never null here now, so a value that used to arrive as null may fail the chain before this check: carry it as a Maybe from the call that returns it)~~>*/Objects.nonNull(value) ? value : "none"));
                }

                void shown(Map<String, String> names) {
                    Try.of(() -> names.get("shown")).toEither(e -> "failed").ifRight(value -> System.out.println(/*~~(Never null here now, so a value that used to arrive as null may fail the chain before this check: carry it as a Maybe from the call that returns it)~~>*/value == null));
                }
            }
            """));
  }

  @Test
  @DisplayName(
      "leaves a null check alone where no lazy factory made the value, or a Maybe holds it")
  void leavesANullCheckNoLazyFactoryFeedsAlone() {
    rewriteRun(
        java(
            """
            package app;

            import java.util.function.Predicate;
            import org.example.store.LegacyStore;
            import org.higherkindedj.hkt.either.Either;
            import org.higherkindedj.hkt.maybe.Maybe;
            import org.higherkindedj.hkt.trymonad.Try;
            import org.higherkindedj.hkt.vtask.VTask;

            class Checks {
                Either<String, String> given(Either<String, String> found) {
                    return found.map(value -> value == null ? "none" : value);
                }

                Either<String, String> built() {
                    return Either.<String, String>right("x").map(value -> value == null ? "none" : value);
                }

                Try<String> held(LegacyStore store, String query) {
                    return Try.of(() -> Maybe.fromNullable(store.findAndReplace(query, "doc")))
                        .map(found -> found.isNothing() ? "none" : "found");
                }

                Try<String> checked(Try<String> lookup) {
                    return lookup.map(value -> value == null ? "none" : value);
                }

                Either<String, String> joined(Either<String, String> found) {
                    return VTask.of(() -> found).join().map(value -> value == null ? "none" : value);
                }

                Try<Boolean> shadowed() {
                    return Try.of(() -> "x").map(value -> new Predicate<String>() {
                        @Override
                        public boolean test(String value) {
                            return value == null;
                        }
                    }.test("y"));
                }
            }
            """));
  }

  @Test
  @DisplayName(
      "marks a method that may return null where a lazy factory reads it, and a step that accepts"
          + " null")
  void marksASupplierThatMayReturnNull() {
    rewriteRun(
        java(
            """
            package app;

            import com.google.common.cache.Cache;
            import org.example.store.LegacyStore;
            import org.example.store.Store;
            import org.higherkindedj.hkt.either.Either;
            import org.higherkindedj.hkt.trymonad.Try;
            import org.higherkindedj.hkt.vtask.VTask;
            import org.jspecify.annotations.Nullable;

            class Gateway {
                @Nullable String exchange() {
                    return null;
                }

                public @Nullable String header(String name) {
                    return null;
                }

                public <T> @Nullable T attribute(String name) {
                    return null;
                }

                Either<String, String> accept(@Nullable String body) {
                    return body == null ? Either.left("empty") : Either.right(body);
                }

                Either<String, String> call() {
                    return Try.of(this::exchange).toEither(e -> "failed").flatMap(this::accept);
                }

                VTask<String> later() {
                    return VTask.of(() -> {
                        System.out.println("calling");
                        return exchange();
                    });
                }

                Try<String> headers() {
                    return Try.of(() -> header("accept"));
                }

                Try<String> branched(boolean fresh) {
                    return Try.of(() -> {
                        if (fresh) {
                            return header("accept");
                        }
                        return "cached";
                    });
                }

                VTask<String> attributes() {
                    return VTask.of(() -> this.<String>attribute("user"));
                }

                Try<String> legacy(LegacyStore store, String query) {
                    return Try.of(() -> store.findAndReplace(query, "doc"));
                }

                Try<String> cached(Cache<String, String> cache) {
                    return Try.of(() -> cache.getIfPresent("key"));
                }

                Try<String> current(Store store, String query) {
                    return Try.of(() -> store.findAndReplace(query, "doc"));
                }

                Try<String> plain(String query) {
                    return Try.of(() -> query.trim());
                }
            }
            """,
            """
            package app;

            import com.google.common.cache.Cache;
            import org.example.store.LegacyStore;
            import org.example.store.Store;
            import org.higherkindedj.hkt.either.Either;
            import org.higherkindedj.hkt.trymonad.Try;
            import org.higherkindedj.hkt.vtask.VTask;
            import org.jspecify.annotations.Nullable;

            class Gateway {
                @Nullable String exchange() {
                    return null;
                }

                public @Nullable String header(String name) {
                    return null;
                }

                public <T> @Nullable T attribute(String name) {
                    return null;
                }

                Either<String, String> accept(@Nullable String body) {
                    return body == null ? Either.left("empty") : Either.right(body);
                }

                Either<String, String> call() {
                    return Try.of(/*~~(May return null, which now gives a Failure: carry a missing value as a Maybe, or fail with an error that says why)~~>*/this::exchange).toEither(e -> "failed").flatMap(/*~~(Accepts null, but is never handed one now, so a value that used to arrive as null may fail the chain before this step: carry it as a Maybe from the call that returns it)~~>*/this::accept);
                }

                VTask<String> later() {
                    return VTask.of(() -> {
                        System.out.println("calling");
                        return /*~~(May return null, which now fails when it runs: carry a missing value as a Maybe, or fail with an error that says why)~~>*/exchange();
                    });
                }

                Try<String> headers() {
                    return Try.of(() -> /*~~(May return null, which now gives a Failure: carry a missing value as a Maybe, or fail with an error that says why)~~>*/header("accept"));
                }

                Try<String> branched(boolean fresh) {
                    return Try.of(() -> {
                        if (fresh) {
                            return /*~~(May return null, which now gives a Failure: carry a missing value as a Maybe, or fail with an error that says why)~~>*/header("accept");
                        }
                        return "cached";
                    });
                }

                VTask<String> attributes() {
                    return VTask.of(() -> /*~~(May return null, which now fails when it runs: carry a missing value as a Maybe, or fail with an error that says why)~~>*/this.<String>attribute("user"));
                }

                Try<String> legacy(LegacyStore store, String query) {
                    return Try.of(() -> /*~~(May return null, which now gives a Failure: carry a missing value as a Maybe, or fail with an error that says why)~~>*/store.findAndReplace(query, "doc"));
                }

                Try<String> cached(Cache<String, String> cache) {
                    return Try.of(() -> /*~~(May return null, which now gives a Failure: carry a missing value as a Maybe, or fail with an error that says why)~~>*/cache.getIfPresent("key"));
                }

                Try<String> current(Store store, String query) {
                    return Try.of(() -> store.findAndReplace(query, "doc"));
                }

                Try<String> plain(String query) {
                    return Try.of(() -> query.trim());
                }
            }
            """));
  }

  @Test
  @DisplayName("marks a method that may return null where an eager map reads it")
  void marksAnEagerMapThatMayReturnNull() {
    rewriteRun(
        java(
            """
            package app;

            import org.higherkindedj.hkt.either.Either;
            import org.jspecify.annotations.Nullable;

            class Names {
                @Nullable String nickname(String name) {
                    return null;
                }

                Either<String, String> shorten(Either<String, String> name) {
                    return name.map(this::nickname);
                }
            }
            """,
            """
            package app;

            import org.higherkindedj.hkt.either.Either;
            import org.jspecify.annotations.Nullable;

            class Names {
                @Nullable String nickname(String name) {
                    return null;
                }

                Either<String, String> shorten(Either<String, String> name) {
                    return name.map(/*~~(May return null, which now throws: carry a missing value as a Maybe)~~>*/this::nickname);
                }
            }
            """));
  }

  @Test
  @DisplayName("reads the success function of 0.4.x's success-first Try.fold")
  void readsTheSuccessFunctionOfASuccessFirstFold() {
    rewriteRun(
        spec ->
            spec.parser(
                JavaParser.fromJavaVersion()
                    .dependsOn(
                        "package org.higherkindedj.hkt.trymonad; import java.util.function.Function;"
                            + " public interface Try<T> {"
                            + " static <T> Try<T> of(java.util.function.Supplier<? extends T> s) {"
                            + " return null; }"
                            + " <U> U fold(Function<? super T, ? extends U> success,"
                            + " Function<? super Throwable, ? extends U> failure); }")),
        java(
            """
            package app;

            import java.util.Map;
            import org.higherkindedj.hkt.trymonad.Try;

            class Labels {
                String label(Map<String, String> names) {
                    return Try.of(() -> names.get("label")).fold(label -> label == null ? "none" : label, e -> "failed");
                }
            }
            """,
            """
            package app;

            import java.util.Map;
            import org.higherkindedj.hkt.trymonad.Try;

            class Labels {
                String label(Map<String, String> names) {
                    return Try.of(() -> names.get("label")).fold(label -> /*~~(Never null here now, so a value that used to arrive as null may fail the chain before this check: carry it as a Maybe from the call that returns it)~~>*/label == null ? "none" : label, e -> "failed");
                }
            }
            """));
  }

  @Test
  @DisplayName(
      "reads an overload by its parameter types, and a generic one only for what all its"
          + " overloads say")
  void readsAnOverloadByItsParameterTypes() {
    rewriteRun(
        java(
            """
            package app;

            import org.higherkindedj.hkt.either.Either;
            import org.jspecify.annotations.Nullable;

            final class Shortcuts {
                private Shortcuts() {}

                static Either<String, String> wrap(@Nullable String value) {
                    return Either.right(value);
                }

                static Either<String, Integer> wrap(Integer value) {
                    return Either.left("not a number");
                }

                static <T> Either<String, T> box(@Nullable T value) {
                    return Either.right(value);
                }

                static Either<String, Integer> box(Integer value) {
                    return Either.left("not a number");
                }

                static Either<String, String> text() {
                    return wrap((String) null);
                }

                static Either<String, Integer> number() {
                    return wrap((Integer) null);
                }

                static Either<String, String> boxed() {
                    return box((String) null);
                }
            }
            """,
            """
            package app;

            import org.higherkindedj.hkt.either.Either;
            import org.jspecify.annotations.Nullable;

            final class Shortcuts {
                private Shortcuts() {}

                static Either<String, String> wrap(/*~~(May be null, which Either.right now refuses: drop @Nullable so callers pass a value, such as Unit.INSTANCE or a Maybe, or return an error here when it is null)~~>*/@Nullable String value) {
                    return Either.right(value);
                }

                static Either<String, Integer> wrap(Integer value) {
                    return Either.left("not a number");
                }

                static <T> Either<String, T> box(/*~~(May be null, which Either.right now refuses: drop @Nullable so callers pass a value, such as Unit.INSTANCE or a Maybe, or return an error here when it is null)~~>*/@Nullable T value) {
                    return Either.right(value);
                }

                static Either<String, Integer> box(Integer value) {
                    return Either.left("not a number");
                }

                static Either<String, String> text() {
                    return /*~~(Passes null on to Either.right, which now throws: use a Maybe for a value that may be missing, or an error that says why)~~>*/wrap((String) null);
                }

                static Either<String, Integer> number() {
                    return wrap((Integer) null);
                }

                static Either<String, String> boxed() {
                    return box((String) null);
                }
            }
            """));
  }
}
