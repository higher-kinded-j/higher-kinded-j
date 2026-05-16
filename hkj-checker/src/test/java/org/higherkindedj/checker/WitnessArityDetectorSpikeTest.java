// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Detector spike: does the unifying rule — flag a {@code Kind}/type-class usage iff the witness
 * argument resolves and is not assignable to {@code WitnessArity} — fire on the two real mistakes
 * while staying silent on correct HKJ-style code (the decisive false-positive question)?
 */
@DisplayName("Detector spike: WitnessArity-bound false-positive surface")
class WitnessArityDetectorSpikeTest {

  @BeforeEach
  void reset() {
    WitnessArityDetectorSpikeProbe.reset();
  }

  private List<String> observe(String name, String body) {
    Compilation c =
        javac()
            .withOptions(
                "-Xplugin:HKJWitnessArityDetectorSpike", "--enable-preview", "--release", "25")
            .compile(JavaFileObjects.forSourceString(name, body));
    System.out.println("=== " + name + " (" + c.status() + ") ===");
    WitnessArityDetectorSpikeProbe.OBSERVATIONS.forEach(o -> System.out.println("  " + o));
    return List.copyOf(WitnessArityDetectorSpikeProbe.OBSERVATIONS);
  }

  @Test
  @DisplayName("TRUE POSITIVE: unbounded <F> used as Monad<F>")
  void unboundedTypeParam() {
    assertThat(
            observe(
                "test.A1",
                """
                package test;
                import org.higherkindedj.hkt.Monad;
                public class A1 {
                    <F> void use(Monad<F> m) {}
                }
                """))
        .anyMatch(o -> o.contains("FLAG:typevar-unbounded"));
  }

  @Test
  @DisplayName("TRUE POSITIVE: plain Witness class used as Kind<W,A>")
  void witnessWithoutArity() {
    assertThat(
            observe(
                "test.B1",
                """
                package test;
                import org.higherkindedj.hkt.Kind;
                public class B1 {
                    static final class W {}
                    Kind<W, String> k;
                }
                """))
        .anyMatch(o -> o.contains("FLAG:declared-not-WitnessArity"));
  }

  @Test
  @DisplayName("NO FALSE POSITIVE: properly bounded <F extends WitnessArity<Unary>>")
  void boundedTypeParam() {
    assertThat(
            observe(
                "test.A2",
                """
                package test;
                import org.higherkindedj.hkt.Monad;
                import org.higherkindedj.hkt.WitnessArity;
                import org.higherkindedj.hkt.TypeArity;
                public class A2 {
                    <F extends WitnessArity<TypeArity.Unary>> void use(Monad<F> m) {}
                }
                """))
        .noneMatch(o -> o.contains("FLAG"));
  }

  @Test
  @DisplayName("NO FALSE POSITIVE: correct HKJ-style witness (the decisive case)")
  void correctHkjWitness() {
    List<String> obs =
        observe(
            "test.B2",
            """
            package test;
            import org.higherkindedj.hkt.Kind;
            import org.higherkindedj.hkt.WitnessArity;
            import org.higherkindedj.hkt.TypeArity;
            public interface B2<A> extends Kind<B2.Witness, A> {
                final class Witness implements WitnessArity<TypeArity.Unary> {}
            }
            """);
    assertThat(obs).noneMatch(o -> o.contains("FLAG"));
    assertThat(obs).anyMatch(o -> o.contains("OK:declared-is-WitnessArity"));
  }

  @Test
  @DisplayName("NO FALSE POSITIVE: real library Kind usage (OptionalKind.Witness)")
  void realLibraryWitness() {
    assertThat(
            observe(
                "test.C1",
                """
                package test;
                import org.higherkindedj.hkt.Kind;
                import org.higherkindedj.hkt.optional.OptionalKind;
                public class C1 {
                    Kind<OptionalKind.Witness, String> k;
                }
                """))
        .noneMatch(o -> o.contains("FLAG"));
  }
}
