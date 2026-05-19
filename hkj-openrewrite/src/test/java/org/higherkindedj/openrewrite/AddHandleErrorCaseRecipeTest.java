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

  @Test
  void flagsFreeSwitchMissingHandleErrorAndAp() {
    rewriteRun(
        java(
            """
            package test;

            sealed interface Free permits Free.Pure, Free.Suspend, Free.FlatMapped {
                record Pure() implements Free {}
                record Suspend() implements Free {}
                record FlatMapped() implements Free {}
            }
            """),
        java(
            """
            package test;

            public class Interpreter {
                public int interpret(Free free) {
                    return switch (free) {
                        case Free.Pure p -> 1;
                        case Free.Suspend s -> 2;
                        case Free.FlatMapped f -> 3;
                    };
                }
            }
            """,
            """
            package test;

            public class Interpreter {
                public int interpret(Free free) {
                    return /*~~(Free switch is missing HandleError and/or Ap case(s))~~>*/switch (free) {
                        case Free.Pure p -> 1;
                        case Free.Suspend s -> 2;
                        case Free.FlatMapped f -> 3;
                    };
                }
            }
            """));
  }

  @Test
  void doesNotFlagCompleteFreeSwitch() {
    rewriteRun(
        java(
            """
            package test;

            sealed interface Free
                permits Free.Pure, Free.Suspend, Free.FlatMapped, Free.HandleError, Free.Ap {
                record Pure() implements Free {}
                record Suspend() implements Free {}
                record FlatMapped() implements Free {}
                record HandleError() implements Free {}
                record Ap() implements Free {}
            }
            """),
        java(
            """
            package test;

            public class Interpreter {
                public int interpret(Free free) {
                    return switch (free) {
                        case Free.Pure p -> 1;
                        case Free.Suspend s -> 2;
                        case Free.FlatMapped f -> 3;
                        case Free.HandleError h -> 4;
                        case Free.Ap a -> 5;
                    };
                }
            }
            """));
  }

  @Test
  void apMatchingUsesWordBoundaryAndDoesNotFalseMatchApply() {
    // The switch has Pure/Suspend/FlatMapped/HandleError plus an "Apply" case but no
    // real "Ap" case. A naive substring check on "Ap" would wrongly treat this as
    // complete; word-boundary matching keeps it correctly flagged as missing Ap.
    rewriteRun(
        java(
            """
            package test;

            sealed interface Free
                permits Free.Pure, Free.Suspend, Free.FlatMapped, Free.HandleError, Free.Apply {
                record Pure() implements Free {}
                record Suspend() implements Free {}
                record FlatMapped() implements Free {}
                record HandleError() implements Free {}
                record Apply() implements Free {}
            }
            """),
        java(
            """
            package test;

            public class Interpreter {
                public int interpret(Free free) {
                    return switch (free) {
                        case Free.Pure p -> 1;
                        case Free.Suspend s -> 2;
                        case Free.FlatMapped f -> 3;
                        case Free.HandleError h -> 4;
                        case Free.Apply a -> 5;
                    };
                }
            }
            """,
            """
            package test;

            public class Interpreter {
                public int interpret(Free free) {
                    return /*~~(Free switch is missing HandleError and/or Ap case(s))~~>*/switch (free) {
                        case Free.Pure p -> 1;
                        case Free.Suspend s -> 2;
                        case Free.FlatMapped f -> 3;
                        case Free.HandleError h -> 4;
                        case Free.Apply a -> 5;
                    };
                }
            }
            """));
  }
}
