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
 * Spike result, frozen as a characterization test.
 *
 * <p><b>Question:</b> is a wrong-{@code KindHelper} {@code narrow} mismatch ({@code
 * EITHER.narrow(anOptionalKind)}) a <em>silent</em> runtime {@code KindUnwrapException}, or does
 * javac already reject it?
 *
 * <p><b>Finding (JDK 25):</b> {@code EitherKindHelper.EITHER.narrow} is typed {@code
 * Kind<EitherKind.Witness<L>, R>}, so a concretely-typed wrong-witness {@code Kind} is a
 * <em>compile error</em> (`compiler.err.cant.apply.symbol`). The mismatch only compiles via a
 * <em>raw</em> {@code Kind} — which already requires suppressing {@code rawtypes}/{@code unchecked}
 * and which a witness-comparison detector cannot resolve (so it would skip, by the
 * no-false-positives policy).
 *
 * <p><b>Verdict:</b> this case is <b>closed</b> — javac already catches the realistic case and the
 * only reachable sliver is an already-linted raw-type anti-pattern a detector would skip. Contrast
 * {@code .value()} on a correctly-typed {@code Kind} ({@link KindValueNarrowChecker}), which
 * <em>is</em> a real "cannot find symbol" error a companion can usefully annotate.
 */
@DisplayName("Spike: wrong-KindHelper narrow reachability")
class WrongNarrowSpikeTest {

  private Compilation javacOnly(String name, String body) {
    return javac()
        .withOptions("--enable-preview", "--release", "25")
        .compile(JavaFileObjects.forSourceString(name, body));
  }

  @Test
  @DisplayName("typed wrong-witness Kind into EITHER.narrow")
  void typedWrongWitness() {
    Compilation c =
        javacOnly(
            "test.W1",
            """
            package test;
            import java.util.Optional;
            import org.higherkindedj.hkt.Kind;
            import org.higherkindedj.hkt.optional.OptionalKind;
            import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
            import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
            public class W1 {
                void m() {
                    Kind<OptionalKind.Witness, String> opt = OPTIONAL.widen(Optional.of("x"));
                    var e = EITHER.narrow(opt);
                }
            }
            """);
    assertThat(c.status())
        .as("a typed wrong-witness Kind into narrow is rejected by javac, not silent")
        .isEqualTo(Compilation.Status.FAILURE);
  }

  @Test
  @DisplayName("raw Kind into EITHER.narrow")
  void rawKind() {
    Compilation c =
        javacOnly(
            "test.W2",
            """
            package test;
            import java.util.Optional;
            import org.higherkindedj.hkt.Kind;
            import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
            import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
            public class W2 {
                @SuppressWarnings({"rawtypes", "unchecked"})
                void m() {
                    Kind raw = OPTIONAL.widen(Optional.of("x"));
                    var e = EITHER.narrow(raw);
                }
            }
            """);
    assertThat(c.status())
        .as("only the raw-Kind anti-pattern compiles (the sole silent path; detector would skip)")
        .isEqualTo(Compilation.Status.SUCCESS);
  }
}
