// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

/** Tests for {@link AddArityBoundsToTypeParametersRecipe}. */
class AddArityBoundsToTypeParametersRecipeTest implements RewriteTest {

  // Minimal stubs so the parser can attribute the HKJ types referenced by the test sources.
  // Kind/Monad are intentionally unbounded so pre-migration user code attributes cleanly.
  private static final String[] HKJ_STUBS = {
    "package org.higherkindedj.hkt; public interface TypeArity {"
        + " interface Unary extends TypeArity {} interface Binary extends TypeArity {} }",
    "package org.higherkindedj.hkt; public interface WitnessArity<A extends TypeArity> {}",
    "package org.higherkindedj.hkt; public interface Kind<F, A> {}",
    "package org.higherkindedj.hkt; public interface Kind2<F, A, B> {}",
    "package org.higherkindedj.hkt; public interface Monad<F> {}",
    "package org.higherkindedj.hkt; public interface Bifunctor<F> {}",
  };

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipe(new AddArityBoundsToTypeParametersRecipe())
        .parser(JavaParser.fromJavaVersion().dependsOn(HKJ_STUBS));
  }

  @DocumentExample
  @Test
  void addsBoundToMethodTypeParameterUsedWithKind() {
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.Kind;

            public class Service {
                public <F> void process(Kind<F, String> input) {}
            }
            """,
            """
            package com.example;

            import org.higherkindedj.hkt.Kind;
            import org.higherkindedj.hkt.TypeArity;
            import org.higherkindedj.hkt.WitnessArity;

            public class Service {
                public <F extends WitnessArity<TypeArity.Unary>> void process(Kind<F, String> input) {}
            }
            """));
  }

  @Test
  void addsBoundToMethodTypeParameterUsedWithTypeClass() {
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.Monad;

            public class Service {
                public <F> void run(Monad<F> monad) {}
            }
            """,
            """
            package com.example;

            import org.higherkindedj.hkt.Monad;
            import org.higherkindedj.hkt.TypeArity;
            import org.higherkindedj.hkt.WitnessArity;

            public class Service {
                public <F extends WitnessArity<TypeArity.Unary>> void run(Monad<F> monad) {}
            }
            """));
  }

  @Test
  void addsBoundToClassTypeParameterUsedInImplements() {
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.Monad;

            public abstract class MyMonad<F> implements Monad<F> {}
            """,
            """
            package com.example;

            import org.higherkindedj.hkt.Monad;
            import org.higherkindedj.hkt.TypeArity;
            import org.higherkindedj.hkt.WitnessArity;

            public abstract class MyMonad<F extends WitnessArity<TypeArity.Unary>> implements Monad<F> {}
            """));
  }

  @Test
  void appendsWitnessArityAsIntersectionWhenBoundAlreadyExists() {
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.Kind;

            public class Service {
                public <F extends Comparable<F>> void process(Kind<F, String> input) {}
            }
            """,
            """
            package com.example;

            import org.higherkindedj.hkt.Kind;
            import org.higherkindedj.hkt.TypeArity;
            import org.higherkindedj.hkt.WitnessArity;

            public class Service {
                public <F extends Comparable<F> & WitnessArity<TypeArity.Unary>> void process(Kind<F, String> input) {}
            }
            """));
  }

  @Test
  void doesNotModifyTypeParameterThatAlreadyHasWitnessArityBound() {
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.Kind;
            import org.higherkindedj.hkt.TypeArity;
            import org.higherkindedj.hkt.WitnessArity;

            public class Service {
                public <F extends WitnessArity<TypeArity.Unary>> void process(Kind<F, String> input) {}
            }
            """));
  }

  @Test
  void doesNotModifyUnrelatedTypeParameter() {
    rewriteRun(
        java(
            """
            package com.example;

            import java.util.List;

            public class Service {
                public <T> List<T> identity(List<T> input) {
                    return input;
                }
            }
            """));
  }

  @Test
  void isIdempotentWhenAlreadyMigrated() {
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.Kind;
            import org.higherkindedj.hkt.TypeArity;
            import org.higherkindedj.hkt.WitnessArity;

            public class Service {
                public <F extends WitnessArity<TypeArity.Unary>> void process(Kind<F, String> input) {}
            }
            """));
  }

  @Test
  void addsBinaryBoundForKind2TypeParameter() {
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.Kind2;

            public class Service {
                public <F> void process(Kind2<F, String, Integer> input) {}
            }
            """,
            """
            package com.example;

            import org.higherkindedj.hkt.Kind2;
            import org.higherkindedj.hkt.TypeArity;
            import org.higherkindedj.hkt.WitnessArity;

            public class Service {
                public <F extends WitnessArity<TypeArity.Binary>> void process(Kind2<F, String, Integer> input) {}
            }
            """));
  }

  @Test
  void addsBinaryBoundForBinaryTypeClass() {
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.Bifunctor;

            public abstract class MyBifunctor<F> implements Bifunctor<F> {}
            """,
            """
            package com.example;

            import org.higherkindedj.hkt.Bifunctor;
            import org.higherkindedj.hkt.TypeArity;
            import org.higherkindedj.hkt.WitnessArity;

            public abstract class MyBifunctor<F extends WitnessArity<TypeArity.Binary>> implements Bifunctor<F> {}
            """));
  }

  @Test
  void detectsTypeParameterUsedOnlyInAField() {
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.Kind;

            public class Box<F> {
                private Kind<F, String> value;
            }
            """,
            """
            package com.example;

            import org.higherkindedj.hkt.Kind;
            import org.higherkindedj.hkt.TypeArity;
            import org.higherkindedj.hkt.WitnessArity;

            public class Box<F extends WitnessArity<TypeArity.Unary>> {
                private Kind<F, String> value;
            }
            """));
  }

  @Test
  void detectsTypeParameterUsedOnlyInALocalVariable() {
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.Kind;

            public class Service<F> {
                public void run(Kind<F, String> in) {
                    Kind<F, String> local = in;
                }
            }
            """,
            """
            package com.example;

            import org.higherkindedj.hkt.Kind;
            import org.higherkindedj.hkt.TypeArity;
            import org.higherkindedj.hkt.WitnessArity;

            public class Service<F extends WitnessArity<TypeArity.Unary>> {
                public void run(Kind<F, String> in) {
                    Kind<F, String> local = in;
                }
            }
            """));
  }

  @Test
  void detectsTypeParameterInWildcardAndNestedPosition() {
    rewriteRun(
        java(
            """
            package com.example;

            import java.util.List;
            import org.higherkindedj.hkt.Kind;

            public class Service {
                public <F> void process(List<Kind<? extends F, String>> inputs) {}
            }
            """,
            """
            package com.example;

            import java.util.List;
            import org.higherkindedj.hkt.Kind;
            import org.higherkindedj.hkt.TypeArity;
            import org.higherkindedj.hkt.WitnessArity;

            public class Service {
                public <F extends WitnessArity<TypeArity.Unary>> void process(List<Kind<? extends F, String>> inputs) {}
            }
            """));
  }

  @Test
  void doesNotBindOuterTypeParameterShadowedByAnInnerWitness() {
    // The class type parameter F is never used as a witness. A method declares its
    // own <F> that IS used with Kind; the scan must not let the inner usage leak
    // onto the outer, unrelated F.
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.Kind;

            public class Holder<F> {
                private F value;

                public <F> void process(Kind<F, String> input) {}
            }
            """,
            """
            package com.example;

            import org.higherkindedj.hkt.Kind;
            import org.higherkindedj.hkt.TypeArity;
            import org.higherkindedj.hkt.WitnessArity;

            public class Holder<F> {
                private F value;

                public <F extends WitnessArity<TypeArity.Unary>> void process(Kind<F, String> input) {}
            }
            """));
  }
}
