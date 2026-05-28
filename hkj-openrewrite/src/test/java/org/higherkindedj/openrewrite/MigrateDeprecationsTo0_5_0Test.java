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
  };

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipeFromResources("org.higherkindedj.openrewrite.MigrateDeprecationsTo0_5_0")
        .parser(JavaParser.fromJavaVersion().dependsOn(HKJ_STUBS));
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
}
