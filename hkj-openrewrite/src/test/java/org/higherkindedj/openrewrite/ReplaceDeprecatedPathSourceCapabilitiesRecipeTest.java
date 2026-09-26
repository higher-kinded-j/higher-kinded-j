// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

/** Tests for {@link ReplaceDeprecatedPathSourceCapabilitiesRecipe}. */
class ReplaceDeprecatedPathSourceCapabilitiesRecipeTest implements RewriteTest {

  // A stub of the annotation and its capability levels, enough for the recipe to bind the
  // constants' owning type without the hkj-annotations jar.
  private static final String[] HKJ_STUBS = {
    "package org.higherkindedj.hkt.effect.annotation;"
        + " import java.lang.annotation.*;"
        + " @Target(ElementType.TYPE) @Retention(RetentionPolicy.SOURCE)"
        + " public @interface PathSource {"
        + "   Class<?> witness();"
        + "   Class<?> errorType() default Void.class;"
        + "   Capability capability() default Capability.CHAINABLE;"
        + "   enum Capability {"
        + "     COMPOSABLE, COMBINABLE, CHAINABLE, RECOVERABLE, EFFECTFUL, ACCUMULATING } }",
  };

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipe(new ReplaceDeprecatedPathSourceCapabilitiesRecipe())
        .parser(JavaParser.fromJavaVersion().dependsOn(HKJ_STUBS));
  }

  @Test
  void replacesAQualifiedCapabilityKeepingItsQualifier() {
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.effect.annotation.PathSource;

            @PathSource(witness = Object.class, capability = PathSource.Capability.EFFECTFUL)
            interface Effect<A> {}
            """,
            """
            package com.example;

            import org.higherkindedj.hkt.effect.annotation.PathSource;

            @PathSource(witness = Object.class, capability = PathSource.Capability.CHAINABLE)
            interface Effect<A> {}
            """));
  }

  @Test
  void replacesACapabilityThroughAnImportedCapabilityType() {
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.effect.annotation.PathSource;
            import org.higherkindedj.hkt.effect.annotation.PathSource.Capability;

            @PathSource(witness = Object.class, errorType = String.class, capability = Capability.ACCUMULATING)
            interface Result<A> {}
            """,
            """
            package com.example;

            import org.higherkindedj.hkt.effect.annotation.PathSource;
            import org.higherkindedj.hkt.effect.annotation.PathSource.Capability;

            @PathSource(witness = Object.class, errorType = String.class, capability = Capability.RECOVERABLE)
            interface Result<A> {}
            """));
  }

  @Test
  void replacesAStaticallyImportedCapability() {
    rewriteRun(
        java(
            """
            package com.example;

            import static org.higherkindedj.hkt.effect.annotation.PathSource.Capability.EFFECTFUL;

            import org.higherkindedj.hkt.effect.annotation.PathSource;

            @PathSource(witness = Object.class, capability = EFFECTFUL)
            interface Effect<A> {}
            """,
            """
            package com.example;

            import static org.higherkindedj.hkt.effect.annotation.PathSource.Capability.CHAINABLE;

            import org.higherkindedj.hkt.effect.annotation.PathSource;

            @PathSource(witness = Object.class, capability = CHAINABLE)
            interface Effect<A> {}
            """));
  }

  @Test
  void replacesAFullyQualifiedCapability() {
    rewriteRun(
        java(
            """
            package com.example;

            @org.higherkindedj.hkt.effect.annotation.PathSource(
                witness = Object.class,
                capability = org.higherkindedj.hkt.effect.annotation.PathSource.Capability.ACCUMULATING)
            interface Result<A> {}
            """,
            """
            package com.example;

            @org.higherkindedj.hkt.effect.annotation.PathSource(
                witness = Object.class,
                capability = org.higherkindedj.hkt.effect.annotation.PathSource.Capability.RECOVERABLE)
            interface Result<A> {}
            """));
  }

  @Test
  void keepsTheCapabilitiesThatStay() {
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.effect.annotation.PathSource;

            @PathSource(witness = Object.class, capability = PathSource.Capability.RECOVERABLE)
            interface Result<A> {}
            """));
  }

  @Test
  void leavesAUserConstantOfTheSameNameAlone() {
    // A constant named EFFECTFUL on the user's own enum, and a local of that name, stay.
    rewriteRun(
        java(
            """
            package com.example;

            public class Usage {
                enum Mode { EFFECTFUL, ACCUMULATING }

                Mode pick(boolean effectful) {
                    Mode EFFECTFUL = Mode.EFFECTFUL;
                    return effectful ? EFFECTFUL : Mode.ACCUMULATING;
                }
            }
            """));
  }
}
