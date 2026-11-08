// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

/**
 * Tests for the declarative {@code MigrateDeprecationsTo0_7_0} recipe, against the real hkj-core
 * and hkj-api. {@link ReplaceParCollectWithToListRecipeTest} covers its {@code parCollect} recipe.
 */
@DisplayName("MigrateDeprecationsTo0_7_0")
class MigrateDeprecationsTo0_7_0Test implements RewriteTest {

  // Try as 0.4.6 to 0.4.11 declare it: fold, success first, is the deprecated one
  private static final String TRY_AS_0_4_X =
      """
      package org.higherkindedj.hkt.trymonad;
      import java.util.function.Function;
      public interface Try<T> {
        @Deprecated
        <U> U fold(Function<? super T, ? extends U> successMapper,
                   Function<? super Throwable, ? extends U> failureMapper);
        <U> U foldFailureFirst(Function<? super Throwable, ? extends U> failureMapper,
                               Function<? super T, ? extends U> successMapper);
      }
      """;

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipeFromResources("org.higherkindedj.openrewrite.MigrateDeprecationsTo0_7_0")
        .parser(JavaParser.fromJavaVersion().classpath("hkj-core", "hkj-api", "jspecify"));
  }

  @Test
  @DisplayName("Try.foldFailureFirst and TryPath.foldFailureFirst become fold, arguments in place")
  void renamesFoldFailureFirstToFold() {
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.effect.Path;
            import org.higherkindedj.hkt.effect.TryPath;
            import org.higherkindedj.hkt.trymonad.Try;

            class Usage {
                String describe(Try<Integer> t) {
                    return t.foldFailureFirst(ex -> "err: " + ex.getMessage(), value -> "ok: " + value);
                }

                String describe(TryPath<Integer> p) {
                    return p.foldFailureFirst(ex -> "err: " + ex.getMessage(), value -> "ok: " + value);
                }

                int parse(String s) {
                    return Path.tryOf(() -> Integer.parseInt(s)).run().foldFailureFirst(ex -> -1, n -> n);
                }
            }
            """,
            """
            package com.example;

            import org.higherkindedj.hkt.effect.Path;
            import org.higherkindedj.hkt.effect.TryPath;
            import org.higherkindedj.hkt.trymonad.Try;

            class Usage {
                String describe(Try<Integer> t) {
                    return t.fold(ex -> "err: " + ex.getMessage(), value -> "ok: " + value);
                }

                String describe(TryPath<Integer> p) {
                    return p.fold(ex -> "err: " + ex.getMessage(), value -> "ok: " + value);
                }

                int parse(String s) {
                    return Path.tryOf(() -> Integer.parseInt(s)).run().fold(ex -> -1, n -> n);
                }
            }
            """));
  }

  @Test
  @DisplayName("On 0.4.x, where fold takes the success function first, foldFailureFirst is kept")
  void leavesTheUndeprecatedFoldFailureFirstOf04Alone() {
    rewriteRun(
        spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(TRY_AS_0_4_X)),
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.trymonad.Try;

            class Usage {
                String describe(Try<Integer> t) {
                    return t.foldFailureFirst(ex -> "err: " + ex.getMessage(), value -> "ok: " + value);
                }
            }
            """));
  }

  @Test
  @DisplayName(
      "Run in one pass with the 0.5.0 group on 0.4.x, the swapped call stays foldFailureFirst")
  void keepsTheSwappedCallWhenRunWithThe050GroupOn04() {
    rewriteRun(
        spec ->
            spec.recipeFromResources(
                    "org.higherkindedj.openrewrite.MigrateDeprecationsTo0_5_0",
                    "org.higherkindedj.openrewrite.MigrateDeprecationsTo0_7_0")
                .parser(JavaParser.fromJavaVersion().dependsOn(TRY_AS_0_4_X)),
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.trymonad.Try;

            class Usage {
                String describe(Try<Integer> t) {
                    return t.fold(value -> "ok: " + value, ex -> "err: " + ex.getMessage());
                }
            }
            """,
            """
            package com.example;

            import org.higherkindedj.hkt.trymonad.Try;

            class Usage {
                String describe(Try<Integer> t) {
                    return t.foldFailureFirst(ex -> "err: " + ex.getMessage(), value -> "ok: " + value);
                }
            }
            """));
  }

  @Test
  @DisplayName("A foldFailureFirst of another type is left alone, beside a Try's that is renamed")
  void leavesAnotherTypesFoldFailureFirstAlone() {
    rewriteRun(
        java(
            """
            package com.example;

            import java.util.function.Function;
            import org.higherkindedj.hkt.trymonad.Try;

            class Outcome {
                <U> U foldFailureFirst(Function<Throwable, U> onFailure, Function<String, U> onSuccess) {
                    return onSuccess.apply("ok");
                }

                String read(Try<String> t) {
                    return foldFailureFirst(Throwable::getMessage, s -> s)
                        + t.foldFailureFirst(Throwable::getMessage, s -> s);
                }
            }
            """,
            """
            package com.example;

            import java.util.function.Function;
            import org.higherkindedj.hkt.trymonad.Try;

            class Outcome {
                <U> U foldFailureFirst(Function<Throwable, U> onFailure, Function<String, U> onSuccess) {
                    return onSuccess.apply("ok");
                }

                String read(Try<String> t) {
                    return foldFailureFirst(Throwable::getMessage, s -> s)
                        + t.fold(Throwable::getMessage, s -> s);
                }
            }
            """));
  }
}
