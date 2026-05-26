// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

/** Tests for {@link SwapTryFoldToFoldFailureFirstRecipe}. */
class SwapTryFoldToFoldFailureFirstRecipeTest implements RewriteTest {

  // Minimal stubs that present the deprecated success-first `fold` plus the
  // error-first `foldFailureFirst` replacement on both Try and TryPath. This
  // mirrors the real API surface enough for the MethodMatcher to bind without
  // pulling in the full hkj-core jar.
  private static final String[] HKJ_STUBS = {
    "package org.higherkindedj.hkt.trymonad;"
        + " import java.util.function.Function;"
        + " public interface Try<T> {"
        + "   <U> U fold(Function<? super T, ? extends U> successMapper,"
        + "              Function<? super Throwable, ? extends U> failureMapper);"
        + "   <U> U foldFailureFirst(Function<? super Throwable, ? extends U> failureMapper,"
        + "                          Function<? super T, ? extends U> successMapper);"
        + "   static <T> Try<T> success(T value) { return null; }"
        + "   static <T> Try<T> failure(Throwable t) { return null; } }",
    "package org.higherkindedj.hkt.effect;"
        + " import java.util.function.Function;"
        + " public final class TryPath<A> {"
        + "   public <B> B fold(Function<? super A, ? extends B> successMapper,"
        + "                     Function<? super Throwable, ? extends B> failureMapper) {"
        + "     return null;"
        + "   }"
        + "   public <B> B foldFailureFirst(Function<? super Throwable, ? extends B> failureMapper,"
        + "                                  Function<? super A, ? extends B> successMapper) {"
        + "     return null;"
        + "   } }",
    // Either has its own fold with a different (error-first) order; the recipe
    // must leave Either.fold alone.
    "package org.higherkindedj.hkt.either;"
        + " import java.util.function.Function;"
        + " public interface Either<L, R> {"
        + "   <T> T fold(Function<? super L, ? extends T> leftMapper,"
        + "              Function<? super R, ? extends T> rightMapper); }",
  };

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipe(new SwapTryFoldToFoldFailureFirstRecipe())
        .parser(JavaParser.fromJavaVersion().dependsOn(HKJ_STUBS));
  }

  @Test
  void rewritesTryFoldSwappingArguments() {
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.trymonad.Try;

            public class Usage {
                String describe(Try<Integer> t) {
                    return t.fold(
                        value -> "ok: " + value,
                        ex -> "err: " + ex.getMessage());
                }
            }
            """,
            """
            package com.example;

            import org.higherkindedj.hkt.trymonad.Try;

            public class Usage {
                String describe(Try<Integer> t) {
                    return t.foldFailureFirst(
                        ex -> "err: " + ex.getMessage(),
                        value -> "ok: " + value);
                }
            }
            """));
  }

  @Test
  void rewritesTryPathFoldSwappingArguments() {
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.effect.TryPath;

            public class Usage {
                String describe(TryPath<Integer> p) {
                    return p.fold(
                        value -> "ok: " + value,
                        ex -> "err: " + ex.getMessage());
                }
            }
            """,
            """
            package com.example;

            import org.higherkindedj.hkt.effect.TryPath;

            public class Usage {
                String describe(TryPath<Integer> p) {
                    return p.foldFailureFirst(
                        ex -> "err: " + ex.getMessage(),
                        value -> "ok: " + value);
                }
            }
            """));
  }

  @Test
  void rewritesMethodReferenceArguments() {
    // Method references are a sub-case worth covering explicitly because
    // OpenRewrite distinguishes them from lambda Expression nodes in the AST.
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.trymonad.Try;

            public class Usage {
                Try<String> coerce(Try<String> t) {
                    return t.fold(Try::success, Try::failure);
                }
            }
            """,
            """
            package com.example;

            import org.higherkindedj.hkt.trymonad.Try;

            public class Usage {
                Try<String> coerce(Try<String> t) {
                    return t.foldFailureFirst(Try::failure, Try::success);
                }
            }
            """));
  }

  @Test
  void rewritesNestedTryFolds() {
    // Nested folds (Try inside Try) — verifies the visitor descends through
    // a rewritten subtree without re-rewriting itself or skipping the inner call.
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.trymonad.Try;

            public class Usage {
                String describe(Try<Try<Integer>> outer) {
                    return outer.fold(
                        inner -> inner.fold(
                            v -> "inner ok: " + v,
                            ex -> "inner err: " + ex.getMessage()),
                        ex -> "outer err: " + ex.getMessage());
                }
            }
            """,
            """
            package com.example;

            import org.higherkindedj.hkt.trymonad.Try;

            public class Usage {
                String describe(Try<Try<Integer>> outer) {
                    return outer.foldFailureFirst(
                        ex -> "outer err: " + ex.getMessage(),
                        inner -> inner.foldFailureFirst(
                            ex -> "inner err: " + ex.getMessage(),
                            v -> "inner ok: " + v));
                }
            }
            """));
  }

  @Test
  void preservesArgumentCommentsThroughTheSwap() {
    // Comments are stored inside the leading Space prefix of each argument in
    // OpenRewrite. A naive prefix swap would move the success-arm comment onto
    // the failure-arm position, which is the exact misattribution this rename
    // was supposed to prevent. This test pins the correct behaviour: comments
    // stay attached to the mapper they describe.
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.trymonad.Try;

            public class Usage {
                String describe(Try<Integer> t) {
                    return t.fold(
                        /* success */ value -> "ok: " + value,
                        /* failure */ ex -> "err: " + ex.getMessage());
                }
            }
            """,
            """
            package com.example;

            import org.higherkindedj.hkt.trymonad.Try;

            public class Usage {
                String describe(Try<Integer> t) {
                    return t.foldFailureFirst(
                        /* failure */ ex -> "err: " + ex.getMessage(),
                        /* success */ value -> "ok: " + value);
                }
            }
            """));
  }

  @Test
  void leavesEitherFoldAlone() {
    // Either.fold is already error-first; the recipe must not touch it.
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.either.Either;

            public class Usage {
                String describe(Either<String, Integer> e) {
                    return e.fold(
                        err -> "err: " + err,
                        value -> "ok: " + value);
                }
            }
            """));
  }

  @Test
  void leavesFoldFailureFirstAlone() {
    // Idempotence: a call site that already uses the new API must not be rewritten
    // (and must not somehow be matched by the recipe).
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.trymonad.Try;

            public class Usage {
                String describe(Try<Integer> t) {
                    return t.foldFailureFirst(
                        ex -> "err: " + ex.getMessage(),
                        value -> "ok: " + value);
                }
            }
            """));
  }

  @Test
  void leavesUnrelatedFoldOnUserTypeAlone() {
    // A user type with an unrelated `fold` method must not be matched.
    rewriteRun(
        java(
            """
            package com.example;

            import java.util.function.Function;

            public class Usage {
                <A, B, T> T fold(A a, B b, Function<A, T> fa, Function<B, T> fb) {
                    return null;
                }

                String run() {
                    return fold(1, "x", i -> "i:" + i, s -> "s:" + s);
                }
            }
            """));
  }
}
