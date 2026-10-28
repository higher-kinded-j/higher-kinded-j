// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

/** Tests for {@link AddPathFamilyToCapabilityTypesRecipe}. */
class AddPathFamilyToCapabilityTypesRecipeTest implements RewriteTest {

  /** The capability interfaces as 0.5.x declares them, without the family. */
  private static final String[] BEFORE_THE_FAMILY = {
    "package org.higherkindedj.hkt.effect.capability;" + " public interface Combinable<A> {}",
    "package org.higherkindedj.hkt.effect.capability;"
        + " public interface Chainable<A> extends Combinable<A> {}",
    "package org.higherkindedj.hkt.effect.capability;"
        + " public interface Effectful<A> extends Chainable<A> {}",
    "package org.higherkindedj.hkt.effect.capability;"
        + " public interface Recoverable<E, A> extends Chainable<A> {}",
    "package org.higherkindedj.hkt.effect.capability;" + " public interface Accumulating<E, A> {}",
    "package org.higherkindedj.hkt.effect.spi;"
        + " import org.higherkindedj.hkt.effect.capability.Chainable;"
        + " public interface PathProvider<F> { <A> Chainable<A> createPath(Object kind); }",
  };

  /** The capability interfaces as 0.6.0 declares them, with the family first. */
  private static final String[] WITH_THE_FAMILY = {
    "package org.higherkindedj.hkt.effect.capability;" + " public interface Combinable<P, A> {}",
    "package org.higherkindedj.hkt.effect.capability;"
        + " public interface Chainable<P, A> extends Combinable<P, A> {}",
    "package org.higherkindedj.hkt.effect.capability;"
        + " public interface Effectful<P, A> extends Chainable<P, A> {}",
    "package org.higherkindedj.hkt.effect.capability;"
        + " public interface Recoverable<P, E, A> extends Chainable<P, A> {}",
  };

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipe(new AddPathFamilyToCapabilityTypesRecipe())
        .parser(JavaParser.fromJavaVersion().dependsOn(BEFORE_THE_FAMILY));
  }

  @Test
  void opensTheFamilyInAHelpersSignature() {
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.effect.capability.Effectful;

            class Retry {
                static <A> Effectful<A> once(Effectful<A> effect) {
                    return effect;
                }
            }
            """,
            """
            package com.example;

            import org.higherkindedj.hkt.effect.capability.Effectful;

            class Retry {
                static <A> Effectful<?, A> once(Effectful<?, A> effect) {
                    return effect;
                }
            }
            """));
  }

  @Test
  void opensTheFamilyOfEveryCapabilityInFieldsLocalsAndTypeArguments() {
    rewriteRun(
        java(
            """
            package com.example;

            import java.util.List;
            import java.util.Optional;
            import org.higherkindedj.hkt.effect.capability.Accumulating;
            import org.higherkindedj.hkt.effect.capability.Chainable;
            import org.higherkindedj.hkt.effect.capability.Combinable;
            import org.higherkindedj.hkt.effect.capability.Recoverable;

            class Holder {
                Optional<Chainable<String>> found;
                Recoverable<String, Integer> recoverable;
                Accumulating<List<String>, Integer> accumulating;

                void run(Combinable<?> any) {
                    List<Chainable<Integer>> all = List.of();
                }
            }
            """,
            """
            package com.example;

            import java.util.List;
            import java.util.Optional;
            import org.higherkindedj.hkt.effect.capability.Accumulating;
            import org.higherkindedj.hkt.effect.capability.Chainable;
            import org.higherkindedj.hkt.effect.capability.Combinable;
            import org.higherkindedj.hkt.effect.capability.Recoverable;

            class Holder {
                Optional<Chainable<?, String>> found;
                Recoverable<?, String, Integer> recoverable;
                Accumulating<?, List<String>, Integer> accumulating;

                void run(Combinable<?, ?> any) {
                    List<Chainable<?, Integer>> all = List.of();
                }
            }
            """));
  }

  @Test
  void opensTheFamilyOfAProvidersCreatePath() {
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.effect.capability.Chainable;
            import org.higherkindedj.hkt.effect.spi.PathProvider;

            class ApiResultPathProvider implements PathProvider<Object> {
                @Override
                public <A> Chainable<A> createPath(Object kind) {
                    return null;
                }
            }
            """,
            """
            package com.example;

            import org.higherkindedj.hkt.effect.capability.Chainable;
            import org.higherkindedj.hkt.effect.spi.PathProvider;

            class ApiResultPathProvider implements PathProvider<Object> {
                @Override
                public <A> Chainable<?, A> createPath(Object kind) {
                    return null;
                }
            }
            """));
  }

  @Test
  void opensTheFamilyInAnInstanceofPattern() {
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.effect.capability.Chainable;

            class Patterns {
                boolean chains(Object value) {
                    return value instanceof Chainable<?> c;
                }
            }
            """,
            """
            package com.example;

            import org.higherkindedj.hkt.effect.capability.Chainable;

            class Patterns {
                boolean chains(Object value) {
                    return value instanceof Chainable<?, ?> c;
                }
            }
            """));
  }

  @Test
  void leavesARawTypeAndAnotherPackagesChainableAlone() {
    rewriteRun(
        java(
            """
            package com.example.other;

            public interface Chainable<A> {}
            """),
        java(
            """
            package com.example;

            class Untouched {
                @SuppressWarnings("rawtypes")
                org.higherkindedj.hkt.effect.capability.Chainable raw;
                com.example.other.Chainable<String> unrelated;
            }
            """));
  }

  @Test
  void leavesAnImplementationsFamilyToItsAuthorAndRewritesItsOtherMembers() {
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.effect.capability.Chainable;
            import org.higherkindedj.hkt.effect.capability.Combinable;

            class OwnPath<A> implements Combinable<A> {
                Chainable<A> pending;
                Object anonymous = new Combinable<String>() {};

                <B> OwnPath<B> zipWith(Combinable<B> other) {
                    Chainable<B> local = null;
                    return null;
                }

                static class Helper {
                    Combinable<String> held;
                }
            }
            """,
            """
            package com.example;

            import org.higherkindedj.hkt.effect.capability.Chainable;
            import org.higherkindedj.hkt.effect.capability.Combinable;

            class OwnPath<A> implements Combinable<A> {
                Chainable<?, A> pending;
                Object anonymous = new Combinable<String>() {};

                <B> OwnPath<B> zipWith(Combinable<B> other) {
                    Chainable<?, B> local = null;
                    return null;
                }

                static class Helper {
                    Combinable<?, String> held;
                }
            }
            """));
  }

  /** The same source after the project has moved to 0.6.0. */
  @Nested
  class AgainstTheFamily {

    @Test
    void opensTheFamilyOfAnOldSignature() {
      rewriteRun(
          spec ->
              spec.parser(JavaParser.fromJavaVersion().dependsOn(WITH_THE_FAMILY))
                  .typeValidationOptions(TypeValidation.none()),
          java(
              """
              package com.example;

              import org.higherkindedj.hkt.effect.capability.Effectful;
              import org.higherkindedj.hkt.effect.capability.Recoverable;

              class Retry {
                  static <A> Effectful<A> once(Effectful<A> effect) {
                      return effect;
                  }

                  static <E, A> Recoverable<E, A> recovered(Recoverable<E, A> path) {
                      return path;
                  }
              }
              """,
              """
              package com.example;

              import org.higherkindedj.hkt.effect.capability.Effectful;
              import org.higherkindedj.hkt.effect.capability.Recoverable;

              class Retry {
                  static <A> Effectful<?, A> once(Effectful<?, A> effect) {
                      return effect;
                  }

                  static <E, A> Recoverable<?, E, A> recovered(Recoverable<?, E, A> path) {
                      return path;
                  }
              }
              """));
    }

    @Test
    void leavesAMigratedSignatureAlone() {
      rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(WITH_THE_FAMILY)),
          java(
              """
              package com.example;

              import org.higherkindedj.hkt.effect.capability.Chainable;

              class Generic {
                  static <P, A> Chainable<P, A> same(Chainable<P, A> path) {
                      return path;
                  }

                  static <A> Chainable<?, A> open(Chainable<?, A> path) {
                      return path;
                  }
              }
              """));
    }
  }
}
