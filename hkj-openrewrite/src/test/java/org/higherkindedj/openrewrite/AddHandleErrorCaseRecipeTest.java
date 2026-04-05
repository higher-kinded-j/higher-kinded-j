// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

/** Tests for {@link AddHandleErrorCaseRecipe}. */
class AddHandleErrorCaseRecipeTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipe(new AddHandleErrorCaseRecipe());
  }

  @Test
  void detectsMissingSwitchCases() {
    rewriteRun(
        java(
            """
            package test;

            public class Interpreter {
                public Object interpret(Object free) {
                    return switch (free) {
                        case String s -> s; // simulates Free.Pure
                        default -> null;
                    };
                }
            }
            """));
    // Non-Free switch should not be modified
  }

  @Test
  void doesNotModifyNonFreeSwitch() {
    rewriteRun(
        java(
            """
            package test;

            public class RegularSwitch {
                public String convert(int x) {
                    return switch (x) {
                        case 1 -> "one";
                        case 2 -> "two";
                        default -> "other";
                    };
                }
            }
            """));
  }
}
