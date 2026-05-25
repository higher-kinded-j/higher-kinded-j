// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Constructors for {@link BiPredicate}-shaped equivalences over {@link Kind} values.
 *
 * <p>Type-class law verification helpers (e.g. {@code FunctorLaws}, {@code MonadLaws}) take a
 * {@code BiPredicate<Kind<F,?>, Kind<F,?>>} equality checker. Almost every caller writes the same
 * boilerplate inline: narrow both sides through a {@code KindHelper}, then compare with {@code
 * equals}. This class lifts that pattern.
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * private static final BiPredicate<Kind<MaybeKind.Witness, ?>, Kind<MaybeKind.Witness, ?>> EQ =
 *     KindEquivalence.byEqualsAfter(MAYBE::narrow);
 * }</pre>
 */
public final class KindEquivalence {

  private KindEquivalence() {}

  /**
   * Equivalence that narrows both sides through {@code narrow} and compares the unwrapped values
   * with {@link Objects#equals}.
   */
  public static <F extends WitnessArity<TypeArity.Unary>>
      BiPredicate<Kind<F, ?>, Kind<F, ?>> byEqualsAfter(Function<? super Kind<F, ?>, ?> narrow) {
    return (k1, k2) -> Objects.equals(narrow.apply(k1), narrow.apply(k2));
  }
}
