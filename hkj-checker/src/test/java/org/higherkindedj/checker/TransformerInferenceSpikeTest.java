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
 * Docs-staleness confirming spike for transformers §4 (`EitherT.fromEither` "cannot infer `L`").
 *
 * <p>This is the same inference family as effect §1 (proven stale by {@code
 * PathRightInferenceSpikeTest}): {@code Either.right(value)} has no Left, so {@code L} is
 * unconstrained. The doc claims a {@code cannot infer type-variable(s)} error; the hypothesis, from
 * the §1 finding, is that modern javac instead resolves {@code L} to {@code Object} and compiles.
 * This test pins whichever behaviour javac actually has, so the docs rewrite rests on evidence.
 *
 * <p>(Transformers §2 is deliberately not spiked: there {@code L} is carried in {@code
 * EitherTKind.Witness<F,L>}, so a different-{@code L} step is a genuinely different {@code Kind}
 * type and {@code .from(fn)} still fails to type-check — §2 is accurate, not stale, by the type
 * system. Optics {@code traverseOver} is the same inference family as §1 but needs heavy generated-
 * optics scaffolding; it is treated as suspected-stale-by-family in the docs note rather than
 * over-invested in here.)
 */
@DisplayName("Spike: transformers §4 EitherT.fromEither inference")
class TransformerInferenceSpikeTest {

  @Test
  @DisplayName("EitherT.fromEither(monad, Either.right(..)) with no L target compiles (L=Object)")
  void fromEither_unconstrainedL_compiles() {
    Compilation c =
        javac()
            .withOptions("--enable-preview", "--release", "25")
            .compile(
                JavaFileObjects.forSourceString(
                    "test.T4",
                    """
                    package test;
                    import org.higherkindedj.hkt.either.Either;
                    import org.higherkindedj.hkt.either_t.EitherT;
                    import org.higherkindedj.hkt.optional.OptionalMonad;
                    public class T4 {
                        void m() {
                            var step = EitherT.fromEither(OptionalMonad.INSTANCE, Either.right("ok"));
                        }
                    }
                    """));
    assertThat(c.status())
        .as("doc transformers §4 claims a 'cannot infer L' error here; like §1, javac resolves it")
        .isEqualTo(Compilation.Status.SUCCESS);
  }
}
