// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.higherkindedj.hkt.util.validation.Operation.*;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Bifunctor;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.NullMarked;

/**
 * {@link Bifunctor} instance for {@link Validated}.
 *
 * <p>This instance enables transformation of both the error (Invalid) and value (Valid) channels of
 * a {@link Validated} independently. Both type parameters are covariant.
 *
 * <p>Examples:
 *
 * <pre>{@code
 * Validated<String, Integer> validated = Validated.valid(42);
 * Validated<Integer, String> transformed = ValidatedBifunctor.INSTANCE.bimap(
 *     String::length,     // Transform error (not applied here)
 *     n -> "Value: " + n, // Transform value: 42 -> "Value: 42"
 *     VALIDATED.widen2(validated)
 * );
 * // result = Validated.valid("Value: 42")
 * }</pre>
 */
@NullMarked
public class ValidatedBifunctor implements Bifunctor<ValidatedKind2.Witness> {

  /** Singleton instance of the ValidatedBifunctor. */
  public static final ValidatedBifunctor INSTANCE = new ValidatedBifunctor();

  private ValidatedBifunctor() {}

  @Override
  public <A, B, C, D> Kind2<ValidatedKind2.Witness, C, D> bimap(
      Function<? super A, ? extends C> f,
      Function<? super B, ? extends D> g,
      Kind2<ValidatedKind2.Witness, A, B> fab) {

    Validation.function().requireMapper(f, "f", ValidatedBifunctor.class, BIMAP);
    Validation.function().requireMapper(g, "g", ValidatedBifunctor.class, BIMAP);
    Objects.requireNonNull(fab, "Kind for ValidatedBifunctor.bimap cannot be null");

    Validated<A, B> validated = VALIDATED.narrow2(fab);
    Validated<C, D> result = validated.bimap(f, g);
    return VALIDATED.widen2(result);
  }

  @Override
  public <A, B, C> Kind2<ValidatedKind2.Witness, C, B> first(
      Function<? super A, ? extends C> f, Kind2<ValidatedKind2.Witness, A, B> fab) {

    Validation.function().requireMapper(f, "f", ValidatedBifunctor.class, FIRST);
    Objects.requireNonNull(fab, "Kind for ValidatedBifunctor.first cannot be null");

    Validated<A, B> validated = VALIDATED.narrow2(fab);
    Validated<C, B> result = validated.mapError(f);
    return VALIDATED.widen2(result);
  }

  @Override
  public <A, B, D> Kind2<ValidatedKind2.Witness, A, D> second(
      Function<? super B, ? extends D> g, Kind2<ValidatedKind2.Witness, A, B> fab) {

    Validation.function().requireMapper(g, "g", ValidatedBifunctor.class, SECOND);
    Objects.requireNonNull(fab, "Kind for ValidatedBifunctor.second cannot be null");

    Validated<A, B> validated = VALIDATED.narrow2(fab);
    Validated<A, D> result = validated.map(g);
    return VALIDATED.widen2(result);
  }
}
