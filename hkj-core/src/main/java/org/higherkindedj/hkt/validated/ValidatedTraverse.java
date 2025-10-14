// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.higherkindedj.hkt.util.validation.Operation.*;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.util.validation.FunctionValidator;
import org.higherkindedj.hkt.util.validation.KindValidator;

/**
 * Implements the {@link Traverse} and {@link Foldable} typeclasses for {@link Validated}.
 *
 * <p>Traversal and folding operations are right-biased, meaning they operate on the value inside a
 * {@link Valid} and pass through an {@link Invalid} unchanged.
 *
 * @param <E> The type of the error value.
 */
public final class ValidatedTraverse<E> implements Traverse<ValidatedKind.Witness<E>> {

  private static final ValidatedTraverse<?> INSTANCE = new ValidatedTraverse<>();
  private static final Class<ValidatedTraverse> VALIDATED_TRAVERSE_CLASS = ValidatedTraverse.class;

  private ValidatedTraverse() {}

  @SuppressWarnings("unchecked")
  public static <E> ValidatedTraverse<E> instance() {
    return (ValidatedTraverse<E>) INSTANCE;
  }

  @Override
  public <A, B> Kind<ValidatedKind.Witness<E>, B> map(
      Function<? super A, ? extends B> f, Kind<ValidatedKind.Witness<E>, A> fa) {

    FunctionValidator.requireMapper(f, "f", VALIDATED_TRAVERSE_CLASS, MAP);
    KindValidator.requireNonNull(fa, VALIDATED_TRAVERSE_CLASS, MAP);

    return VALIDATED.widen(VALIDATED.narrow(fa).map(f));
  }

  @Override
  public <G, A, B> Kind<G, Kind<ValidatedKind.Witness<E>, B>> traverse(
      Applicative<G> applicative,
      Function<? super A, ? extends Kind<G, ? extends B>> f,
      Kind<ValidatedKind.Witness<E>, A> ta) {

    FunctionValidator.requireApplicative(applicative, "applicative", VALIDATED_TRAVERSE_CLASS, TRAVERSE);
    FunctionValidator.requireMapper(f, "f", VALIDATED_TRAVERSE_CLASS, TRAVERSE);
    KindValidator.requireNonNull(ta, VALIDATED_TRAVERSE_CLASS, TRAVERSE);

    return VALIDATED
        .narrow(ta)
        .fold(
            // Invalid case: Lift the Invalid instance directly into the applicative context.
            error -> applicative.of(VALIDATED.widen(Validated.invalid(error))),

            // Valid case: Apply the effectful function and map the result back into a Valid.
            value -> applicative.map(b -> VALIDATED.widen(Validated.valid(b)), f.apply(value)));
  }

  @Override
  public <A, M> M foldMap(
      Monoid<M> monoid, Function<? super A, ? extends M> f, Kind<ValidatedKind.Witness<E>, A> fa) {

    FunctionValidator.requireMonoid(monoid, "monoid", VALIDATED_TRAVERSE_CLASS, FOLD_MAP);
    FunctionValidator.requireMapper(f, "f", VALIDATED_TRAVERSE_CLASS, FOLD_MAP);
    KindValidator.requireNonNull(fa, VALIDATED_TRAVERSE_CLASS, FOLD_MAP);

    // If Valid, map the value. If Invalid, return the monoid's empty value.
    return VALIDATED.narrow(fa).fold(error -> monoid.empty(), f);
  }
}
