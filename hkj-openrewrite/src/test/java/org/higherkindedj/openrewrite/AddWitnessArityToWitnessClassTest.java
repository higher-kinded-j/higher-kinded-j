// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

/** Tests for {@link AddWitnessArityToWitnessClass}. */
class AddWitnessArityToWitnessClassTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipe(new AddWitnessArityToWitnessClass());
  }

  @Test
  void addsWitnessArityToWitnessClass() {
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.Kind;

            public interface MyKind<A> extends Kind<MyKind.Witness, A> {
                final class Witness {}
            }
            """,
            """
            package com.example;

            import org.higherkindedj.hkt.Kind;
            import org.higherkindedj.hkt.TypeArity;
            import org.higherkindedj.hkt.WitnessArity;

            public interface MyKind<A> extends Kind<MyKind.Witness, A> {
                final class Witness implements WitnessArity<TypeArity.Unary> {}
            }
            """));
  }

  @Test
  void doesNotModifyNonWitnessClass() {
    rewriteRun(
        java(
            """
            package com.example;

            public class RegularClass {
                public void doSomething() {}
            }
            """));
  }

  @Test
  void doesNotModifyAlreadyAnnotatedWitness() {
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.Kind;
            import org.higherkindedj.hkt.TypeArity;
            import org.higherkindedj.hkt.WitnessArity;

            public interface MyKind<A> extends Kind<MyKind.Witness, A> {
                final class Witness implements WitnessArity<TypeArity.Unary> {}
            }
            """));
  }
}
