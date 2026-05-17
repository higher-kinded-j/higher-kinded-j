// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

/** Tests for {@link ConvertRawFreeToFreePathRecipe}. */
class ConvertRawFreeToFreePathRecipeTest implements RewriteTest {

  // Stub the real Free type so the type-attributed MethodMatcher resolves calls.
  private static final String[] FREE_STUB = {
    "package org.higherkindedj.hkt.free;"
        + " public final class Free {"
        + " public static Object liftF(Object op, Object functor) { return op; }"
        + " public static Object suspend(Object fa) { return fa; } }",
  };

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipe(new ConvertRawFreeToFreePathRecipe())
        .parser(JavaParser.fromJavaVersion().dependsOn(FREE_STUB));
  }

  @Test
  void doesNotModifyNonFreeCode() {
    rewriteRun(
        java(
            """
            package test;

            public class NoFreeUsage {
                public String doSomething() {
                    return "hello";
                }
            }
            """));
  }

  @DocumentExample
  @Test
  void flagsRawFreeLiftFAndSuspend() {
    rewriteRun(
        java(
            """
            package test;

            import org.higherkindedj.hkt.free.Free;

            public class Program {
                public Object build() {
                    Object a = Free.liftF("op", "functor");
                    Object b = Free.suspend("fa");
                    return a;
                }
            }
            """,
            """
            package test;

            import org.higherkindedj.hkt.free.Free;

            public class Program {
                public Object build() {
                    Object a = /*~~(Consider generated *Ops methods or the FreePath API instead of raw Free)~~>*/Free.liftF("op", "functor");
                    Object b = /*~~(Consider generated *Ops methods or the FreePath API instead of raw Free)~~>*/Free.suspend("fa");
                    return a;
                }
            }
            """));
  }

  @Test
  void doesNotModifyLiftFOnAnUnrelatedType() {
    // Same method name, different declaring type: the type-attributed matcher must not fire.
    rewriteRun(
        java(
            """
            package test;

            public class NotFree {
                public static Object liftF(Object a, Object b) { return a; }

                public static void test() {
                    NotFree.liftF("a", "b");
                }
            }
            """));
  }
}
