// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

/** Tests for the declarative {@code MigrateDeprecationsTo0_5_0} recipe. */
class MigrateDeprecationsTo0_5_0Test implements RewriteTest {

  private static final String[] HKJ_STUBS = {
    "package org.higherkindedj.hkt.state_t;"
        + " public final class StateTKind {"
        + " public static Object narrowK(Object kind) { return kind; }"
        + " public static Object narrow(Object kind) { return kind; } }",
    "package org.higherkindedj.hkt.util.validation;"
        + " public final class KindValidator {"
        + " public Object narrowWithPattern(Object kind) { return kind; }"
        + " public Object narrowHolder(Object kind) { return kind; } }",
    "package org.higherkindedj.hkt.trymonad;"
        + " import java.util.function.Function;"
        + " public interface Try<T> {"
        + "   static <T> Try<T> of(java.util.function.Supplier<? extends T> s) { return null; }"
        + "   <U> U fold(Function<? super T, ? extends U> successMapper,"
        + "              Function<? super Throwable, ? extends U> failureMapper);"
        + "   <U> U foldFailureFirst(Function<? super Throwable, ? extends U> failureMapper,"
        + "                          Function<? super T, ? extends U> successMapper); }",
    "package org.higherkindedj.hkt.effect.annotation;"
        + " import java.lang.annotation.*;"
        + " @Target(ElementType.PACKAGE) @Retention(RetentionPolicy.SOURCE)"
        + " public @interface PathConfig {"
        + "   String pathSuffix() default \"Path\";"
        + "   boolean makeFinal() default true; }",
    "package org.higherkindedj.hkt.effect.annotation;"
        + " import java.lang.annotation.*;"
        + " @Target(ElementType.TYPE) @Retention(RetentionPolicy.SOURCE)"
        + " public @interface PathSource {"
        + "   Class<?> witness();"
        + "   Class<?> errorType() default Void.class;"
        + "   Capability capability() default Capability.CHAINABLE;"
        + "   enum Capability {"
        + "     COMPOSABLE, COMBINABLE, CHAINABLE, RECOVERABLE, EFFECTFUL, ACCUMULATING } }",
    "package org.higherkindedj.hkt; public interface Monad<F> {}",
    "package org.higherkindedj.hkt.id;"
        + " public final class IdMonad implements org.higherkindedj.hkt.Monad<Object> {"
        + "   public static final IdMonad INSTANCE = new IdMonad(); }",
    // StateT as 0.4.x declares it, with the Monad the 0.5.0 shape drops
    "package org.higherkindedj.hkt.state_t;"
        + " import java.util.function.Function;"
        + " import org.higherkindedj.hkt.Monad;"
        + " public final class StateT<S, F, A> {"
        + "   public StateT(Function<S, Object> runStateTFn, Monad<F> monadF) {}"
        + "   public static <S, F, A> StateT<S, F, A> create("
        + "       Function<S, Object> runStateTFn, Monad<F> monadF) { return null; }"
        + "   public <G> StateT<S, G, A> mapT(Monad<G> monadG, Function<Object, Object> f) {"
        + "     return null; } }",
    "package org.higherkindedj.hkt.state_t;"
        + " import java.util.function.Function;"
        + " import org.higherkindedj.hkt.Monad;"
        + " public enum StateTKindHelper { STATE_T;"
        + "   public <S, F, A> StateT<S, F, A> stateT("
        + "       Function<S, Object> runStateTFn, Monad<F> monadF) { return null; } }",
    "package org.higherkindedj.hkt.either; public interface Either<L, R> {"
        + " static <L, R> Either<L, R> right(R value) { return null; }"
        + " static <L, R> Either<L, R> left(L value) { return null; } }",
    // @GenerateFocus as 0.4.x declares it, with no widenFields
    "package org.higherkindedj.optics.annotations;"
        + " import java.lang.annotation.*;"
        + " @Target(ElementType.TYPE) @Retention(RetentionPolicy.CLASS)"
        + " public @interface GenerateFocus {"
        + "   boolean generateNavigators() default false;"
        + "   boolean widenCollections() default false;"
        + "   String[] includeFields() default {};"
        + "   String[] excludeFields() default {}; }",
    "package org.eclipse.collections.api.list; public interface ImmutableList<T> {}",
  };

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipeFromResources("org.higherkindedj.openrewrite.MigrateDeprecationsTo0_5_0")
        .parser(JavaParser.fromJavaVersion().dependsOn(HKJ_STUBS));
  }

  @Test
  void marksANullSuccessWithoutRewritingIt() {
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.either.Either;

            class Cancel {
                Either<String, Void> cancel() {
                    return Either.right(null);
                }
            }
            """,
            """
            package com.example;

            import org.higherkindedj.hkt.either.Either;

            class Cancel {
                /*~~(Void has no value for a success to hold: use Unit)~~>*/Either<String, Void> cancel() {
                    return /*~~(A success always holds a value: pass Unit.INSTANCE for a step with nothing to return)~~>*/Either.right(null);
                }
            }
            """));
  }

  @Test
  void marksAValueThatMayReachASuccessAsNull() {
    rewriteRun(
        java(
            """
            package com.example;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Target;

            @Target(ElementType.TYPE_USE)
            @interface Nullable {}
            """),
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.either.Either;

            class Results {
                static <T> Either<String, T> right(@Nullable T value) {
                    return Either.right(value);
                }

                Either<String, Void> purge() {
                    return right(null);
                }
            }
            """,
            """
            package com.example;

            import org.higherkindedj.hkt.either.Either;

            class Results {
                static <T> Either<String, T> right(/*~~(May be null, which Either.right now refuses: drop @Nullable so callers pass a value, such as Unit.INSTANCE or a Maybe, or return an error here when it is null)~~>*/@Nullable T value) {
                    return Either.right(value);
                }

                /*~~(Void has no value for a success to hold: use Unit)~~>*/Either<String, Void> purge() {
                    return /*~~(Passes null on to Either.right, which now throws: pass Unit.INSTANCE for a step with nothing to return)~~>*/right(null);
                }
            }
            """));
  }

  @Test
  void readsTheSuccessFunctionOfA04FoldAfterSwappingIt() {
    rewriteRun(
        java(
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.hkt.trymonad.Try;

            class Labels {
                String label(Map<String, String> names) {
                    return Try.of(() -> names.get("label")).fold(label -> label == null ? "none" : label, e -> e == null ? "?" : "failed");
                }
            }
            """,
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.hkt.trymonad.Try;

            class Labels {
                String label(Map<String, String> names) {
                    return Try.of(() -> names.get("label")).foldFailureFirst(e -> e == null ? "?" : "failed", label -> /*~~(Never null here now, so a value that used to arrive as null may fail the chain before this check: carry it as a Maybe from the call that returns it)~~>*/label == null ? "none" : label);
                }
            }
            """));
  }

  @Test
  void marksANullErrorWithoutRewritingIt() {
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.either.Either;

            class Find {
                Either<String, Integer> find() {
                    return Either.left(null);
                }
            }
            """,
            """
            package com.example;

            import org.higherkindedj.hkt.either.Either;

            class Find {
                Either<String, Integer> find() {
                    return /*~~(A Left always holds an error: pass one that says what went wrong, or Unit.INSTANCE where the error type is Unit)~~>*/Either.left(null);
                }
            }
            """));
  }

  @Test
  void addsWidenFieldsWhereANavigatorSteppedIntoAContainer() {
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus
            public record Position(String ticker) {}
            """),
        java(
            """
            package com.example;

            import java.util.Map;
            import org.eclipse.collections.api.list.ImmutableList;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true)
            public record AssetClass(String name, ImmutableList<Position> positions, Map<String, Position> byTicker) {}
            """,
            """
            package com.example;

            import java.util.Map;
            import org.eclipse.collections.api.list.ImmutableList;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, widenFields = {"positions", "byTicker"})
            public record AssetClass(String name, ImmutableList<Position> positions, Map<String, Position> byTicker) {}
            """));
  }

  @Test
  void renamesStateTKindNarrowKToNarrow() {
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.state_t.StateTKind;

            public class Usage {
                Object run(Object kind) {
                    return StateTKind.narrowK(kind);
                }
            }
            """,
            """
            package com.example;

            import org.higherkindedj.hkt.state_t.StateTKind;

            public class Usage {
                Object run(Object kind) {
                    return StateTKind.narrow(kind);
                }
            }
            """));
  }

  @Test
  void renamesKindValidatorNarrowWithPatternToNarrowHolder() {
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.util.validation.KindValidator;

            public class Usage {
                Object run(KindValidator v, Object kind) {
                    return v.narrowWithPattern(kind);
                }
            }
            """,
            """
            package com.example;

            import org.higherkindedj.hkt.util.validation.KindValidator;

            public class Usage {
                Object run(KindValidator v, Object kind) {
                    return v.narrowHolder(kind);
                }
            }
            """));
  }

  @Test
  void swapsTryFoldToFoldFailureFirst() {
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.trymonad.Try;

            public class Usage {
                String describe(Try<Integer> t) {
                    return t.fold(value -> "ok: " + value, ex -> "err: " + ex.getMessage());
                }
            }
            """,
            """
            package com.example;

            import org.higherkindedj.hkt.trymonad.Try;

            public class Usage {
                String describe(Try<Integer> t) {
                    return t.foldFailureFirst(ex -> "err: " + ex.getMessage(), value -> "ok: " + value);
                }
            }
            """));
  }

  @Test
  void leavesTheFailureFirstFoldOfLaterReleasesAlone() {
    // From 0.6.0, fold takes the failure function first; swapping it would run the two reversed
    rewriteRun(
        spec ->
            spec.parser(JavaParser.fromJavaVersion().classpath("hkj-core", "hkj-api", "jspecify")),
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.effect.TryPath;
            import org.higherkindedj.hkt.trymonad.Try;

            public class Usage {
                String describe(Try<Integer> t) {
                    return t.fold(ex -> "err: " + ex.getMessage(), value -> "ok: " + value);
                }

                String describe(TryPath<Integer> p) {
                    return p.fold(ex -> "err: " + ex.getMessage(), value -> "ok: " + value);
                }
            }
            """));
  }

  @Test
  void leavesUnrelatedMethodsUnchanged() {
    rewriteRun(
        java(
            """
            package com.example;

            public class Usage {
                static Object narrowK(Object x) { return x; }

                Object run(Object kind) {
                    return narrowK(kind);
                }
            }
            """));
  }

  @Test
  void removesPathConfigFromAPackageInfo() {
    rewriteRun(
        java(
            """
            /** Effects. */
            @PathConfig(pathSuffix = "Effect", makeFinal = false)
            package com.example;

            import org.higherkindedj.hkt.effect.annotation.PathConfig;
            """,
            """
            /** Effects. */

            package com.example;
            """,
            spec -> spec.path("com/example/package-info.java")));
  }

  @Test
  void keepsAnotherPackageAnnotation() {
    rewriteRun(
        java(
            """
            @Deprecated
            @PathConfig
            package com.example;

            import org.higherkindedj.hkt.effect.annotation.PathConfig;
            """,
            """
            @Deprecated
            package com.example;
            """,
            spec -> spec.path("com/example/package-info.java")));
  }

  @Test
  void replacesDeprecatedPathSourceCapabilities() {
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.effect.annotation.PathSource;

            public class Usage {
                @PathSource(witness = Object.class, capability = PathSource.Capability.EFFECTFUL)
                interface Effect<A> {}

                @PathSource(
                    witness = Object.class,
                    errorType = String.class,
                    capability = PathSource.Capability.ACCUMULATING)
                interface Result<A> {}
            }
            """,
            """
            package com.example;

            import org.higherkindedj.hkt.effect.annotation.PathSource;

            public class Usage {
                @PathSource(witness = Object.class, capability = PathSource.Capability.CHAINABLE)
                interface Effect<A> {}

                @PathSource(
                    witness = Object.class,
                    errorType = String.class,
                    capability = PathSource.Capability.RECOVERABLE)
                interface Result<A> {}
            }
            """));
  }

  @Test
  void dropsTheMonadArgumentFromStateT() {
    rewriteRun(
        java(
            """
            package com.example;

            import static org.higherkindedj.hkt.state_t.StateTKindHelper.STATE_T;

            import org.higherkindedj.hkt.Monad;
            import org.higherkindedj.hkt.state_t.StateT;

            public class Usage {
                void build(Monad<Object> monad, Monad<String> target) {
                    StateT<Integer, Object, String> a = new StateT<>(s -> s, monad);
                    StateT<Integer, Object, String> b = StateT.create(s -> s, monad);
                    StateT<Integer, Object, String> c = STATE_T.stateT(s -> s, monad);
                    StateT<Integer, String, String> d = a.mapT(target, k -> k);
                }
            }
            """,
            """
            package com.example;

            import static org.higherkindedj.hkt.state_t.StateTKindHelper.STATE_T;

            import org.higherkindedj.hkt.Monad;
            import org.higherkindedj.hkt.state_t.StateT;

            public class Usage {
                void build(Monad<Object> monad, Monad<String> target) {
                    StateT<Integer, Object, String> a = new StateT<>(s -> s);
                    StateT<Integer, Object, String> b = StateT.create(s -> s);
                    StateT<Integer, Object, String> c = STATE_T.stateT(s -> s);
                    StateT<Integer, String, String> d = a.mapT(k -> k);
                }
            }
            """));
  }

  @Test
  void removesAnImportOnlyTheMonadArgumentUsed() {
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.id.IdMonad;
            import org.higherkindedj.hkt.state_t.StateT;

            public class Usage {
                StateT<Integer, Object, String> counter() {
                    return StateT.create(
                        s -> s,
                        IdMonad.INSTANCE);
                }
            }
            """,
            """
            package com.example;

            import org.higherkindedj.hkt.state_t.StateT;

            public class Usage {
                StateT<Integer, Object, String> counter() {
                    return StateT.create(
                        s -> s);
                }
            }
            """));
  }

  @Test
  void leavesAnotherTypesTwoArgumentCreateAlone() {
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.Monad;

            public class Usage {
                static Object create(Object fn, Monad<Object> monad) { return fn; }

                Object run(Monad<Object> monad) {
                    return create("fn", monad);
                }
            }
            """));
  }
}
