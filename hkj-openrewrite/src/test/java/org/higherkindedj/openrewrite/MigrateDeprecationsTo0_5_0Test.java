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
