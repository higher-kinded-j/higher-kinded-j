// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

/** Tests for {@link DetectInjectBoilerplateRecipe}. */
class DetectInjectBoilerplateRecipeTest implements RewriteTest {

  // Stub the real InjectInstances type so the type-attributed MethodMatcher resolves calls.
  private static final String[] INJECT_STUB = {
    "package org.higherkindedj.hkt.inject;"
        + " public final class InjectInstances {"
        + " public static Object injectLeft() { return null; }"
        + " public static Object injectRight() { return null; }"
        + " public static Object injectRightThen(Object next) { return null; } }",
  };

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipe(new DetectInjectBoilerplateRecipe())
        .parser(JavaParser.fromJavaVersion().dependsOn(INJECT_STUB));
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

  @DocumentExample
  @Test
  void flagsInjectInstancesCalls() {
    rewriteRun(
        java(
            """
            package test;

            import org.higherkindedj.hkt.inject.InjectInstances;

            public class Wiring {
                public void wire() {
                    Object a = InjectInstances.injectLeft();
                    Object b = InjectInstances.injectRightThen(a);
                }
            }
            """,
            """
            package test;

            import org.higherkindedj.hkt.inject.InjectInstances;

            public class Wiring {
                public void wire() {
                    Object a = /*~~(Consider the @ComposeEffects generated Support class instead of manual InjectInstances)~~>*/InjectInstances.injectLeft();
                    Object b = /*~~(Consider the @ComposeEffects generated Support class instead of manual InjectInstances)~~>*/InjectInstances.injectRightThen(a);
                }
            }
            """));
  }

  @Test
  void doesNotFlagInjectLeftOnAnUnrelatedType() {
    // Same method name, different declaring type: the type-attributed matcher must not fire.
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
