// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Reachability spike (verify-before-implement): are the two {@code WitnessArity} mistakes — an
 * unbounded type parameter used in an HKT position, and a {@code Witness}-style class that
 * implements {@code Kind} but not {@code WitnessArity} — real, reachable javac bound errors a
 * companion can usefully annotate, and do the bound/implements fixes resolve them?
 */
@DisplayName("Spike: WitnessArity bound + implements reachability")
class WitnessAritySpikeTest {

  private Compilation javacOnly(String name, String body) {
    return javac()
        .withOptions("--enable-preview", "--release", "25")
        .compile(JavaFileObjects.forSourceString(name, body));
  }

  @Test
  @DisplayName("unbounded <F> used as Monad<F> -> bound error")
  void unboundedTypeParam_fails() {
    Compilation c =
        javacOnly(
            "test.A1",
            """
            package test;
            import org.higherkindedj.hkt.Monad;
            public class A1 {
                <F> void use(Monad<F> m) {}
            }
            """);
    System.out.println("A1(unbounded F as Monad<F>): " + c.status());
    c.diagnostics().forEach(d -> System.out.println("  " + d.getKind() + " " + d.getCode()));
    assertThat(c.status()).isEqualTo(Compilation.Status.FAILURE);
  }

  @Test
  @DisplayName("properly bounded <F extends WitnessArity<Unary>> as Monad<F> -> compiles")
  void boundedTypeParam_compiles() {
    Compilation c =
        javacOnly(
            "test.A2",
            """
            package test;
            import org.higherkindedj.hkt.Monad;
            import org.higherkindedj.hkt.WitnessArity;
            import org.higherkindedj.hkt.TypeArity;
            public class A2 {
                <F extends WitnessArity<TypeArity.Unary>> void use(Monad<F> m) {}
            }
            """);
    System.out.println("A2(bounded F): " + c.status());
    assertThat(c.status()).isEqualTo(Compilation.Status.SUCCESS);
  }

  @Test
  @DisplayName("Witness implements Kind but not WitnessArity, used as Kind<W,A> -> bound error")
  void witnessWithoutArity_fails() {
    Compilation c =
        javacOnly(
            "test.B1",
            """
            package test;
            import org.higherkindedj.hkt.Kind;
            public class B1 {
                static final class W {}
                Kind<W, String> k;
            }
            """);
    System.out.println("B1(Witness w/o WitnessArity as Kind<W,A>): " + c.status());
    c.diagnostics().forEach(d -> System.out.println("  " + d.getKind() + " " + d.getCode()));
    assertThat(c.status()).isEqualTo(Compilation.Status.FAILURE);
  }

  @Test
  @DisplayName("Witness implements WitnessArity<Unary>, used as Kind<W,A> -> compiles")
  void witnessWithArity_compiles() {
    Compilation c =
        javacOnly(
            "test.B2",
            """
            package test;
            import org.higherkindedj.hkt.Kind;
            import org.higherkindedj.hkt.WitnessArity;
            import org.higherkindedj.hkt.TypeArity;
            public class B2 {
                static final class W implements WitnessArity<TypeArity.Unary> {}
                Kind<W, String> k;
            }
            """);
    System.out.println("B2(Witness w/ WitnessArity): " + c.status());
    assertThat(c.status()).isEqualTo(Compilation.Status.SUCCESS);
  }
}
