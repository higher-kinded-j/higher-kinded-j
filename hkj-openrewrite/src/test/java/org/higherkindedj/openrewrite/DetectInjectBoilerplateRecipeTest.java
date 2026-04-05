// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

/** Tests for {@link DetectInjectBoilerplateRecipe}. */
class DetectInjectBoilerplateRecipeTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipe(new DetectInjectBoilerplateRecipe());
  }

  @Test
  void doesNotModifyNonInjectCode() {
    rewriteRun(
        java(
            """
            package test;

            public class NoInjectUsage {
                public String doSomething() {
                    return "hello";
                }
            }
            """));
  }

  @Test
  void doesNotFlagNonInjectInstancesMethods() {
    rewriteRun(
        java(
            """
            package test;

            public class SomeClass {
                public static Object injectLeft() { return null; }

                public static void test() {
                    SomeClass.injectLeft();
                }
            }
            """));
  }
}
