// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.eitherorboth;

import static org.higherkindedj.hkt.eitherorboth.EitherOrBothKindHelper.EITHER_OR_BOTH;
import static org.higherkindedj.hkt.util.validation.Operation.BIMAP;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Bifunctor;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.NullMarked;

/**
 * {@link Bifunctor} instance for {@link EitherOrBoth}.
 *
 * <p>Transforms the left (warning) and right (success) channels independently. For a {@link
 * EitherOrBoth.Both}, both functions are applied; the {@code first}/{@code second} convenience
 * methods (inherited as {@link Bifunctor} defaults) therefore reach exactly one channel of a {@code
 * Both} while leaving the other intact.
 */
@NullMarked
public class EitherOrBothBifunctor implements Bifunctor<EitherOrBothKind2.Witness> {

  /** Singleton instance of the {@code EitherOrBothBifunctor}. */
  public static final EitherOrBothBifunctor INSTANCE = new EitherOrBothBifunctor();

  private EitherOrBothBifunctor() {}

  @Override
  public <A, B, C, D> Kind2<EitherOrBothKind2.Witness, C, D> bimap(
      Function<? super A, ? extends C> f,
      Function<? super B, ? extends D> g,
      Kind2<EitherOrBothKind2.Witness, A, B> fab) {

    Validation.function().require(f, "f", BIMAP);
    Validation.function().require(g, "g", BIMAP);
    Objects.requireNonNull(fab, "Kind2 for bimap cannot be null");

    EitherOrBoth<A, B> eob = EITHER_OR_BOTH.narrow2(fab);
    EitherOrBoth<C, D> result = eob.bimap(f, g);
    return EITHER_OR_BOTH.widen2(result);
  }
}
