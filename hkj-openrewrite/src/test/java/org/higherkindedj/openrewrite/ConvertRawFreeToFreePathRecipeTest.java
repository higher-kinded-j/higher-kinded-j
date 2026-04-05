// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

/** Tests for {@link ConvertRawFreeToFreePathRecipe}. */
class ConvertRawFreeToFreePathRecipeTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipe(new ConvertRawFreeToFreePathRecipe());
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

  @Test
  void doesNotModifyNonFreeLiftF() {
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
