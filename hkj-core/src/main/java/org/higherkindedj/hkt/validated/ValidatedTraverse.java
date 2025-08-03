// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Traverse;
import org.jspecify.annotations.NonNull;

public final class ValidatedTraverse<E> implements Traverse<ValidatedKind.Witness<E>> {

  private static final ValidatedTraverse<?> INSTANCE = new ValidatedTraverse<>();

  private ValidatedTraverse() {}

  @SuppressWarnings("unchecked")
  public static <E> ValidatedTraverse<E> instance() {
    return (ValidatedTraverse<E>) INSTANCE;
  }

  @Override
  public <A, B> @NonNull Kind<ValidatedKind.Witness<E>, B> map(
      @NonNull Function<? super A, ? extends B> f, @NonNull Kind<ValidatedKind.Witness<E>, A> fa) {
    return VALIDATED.widen(VALIDATED.narrow(fa).map(f));
  }

  @Override
  public <G, A, B> Kind<G, Kind<ValidatedKind.Witness<E>, B>> traverse(
      @NonNull Applicative<G> applicative,
      @NonNull Kind<ValidatedKind.Witness<E>, A> ta,
      @NonNull Function<? super A, ? extends Kind<G, ? extends B>> f) {

    final Validated<E, A> validated = VALIDATED.narrow(ta);

    if (validated instanceof Invalid<E, A> invalid) {
      // Reconstruct the Invalid case with the correct type signature.
      final Validated<E, B> result = Validated.invalid(invalid.getError());
      return applicative.of(VALIDATED.widen(result));
    } else {
      final Valid<E, A> valid = (Valid<E, A>) validated;
      final Kind<G, ? extends B> g_of_b = f.apply(valid.get());

      @SuppressWarnings("unchecked")
      final Kind<G, B> g_of_b_casted = (Kind<G, B>) g_of_b;

      return applicative.map(b -> VALIDATED.widen(Validated.<E, B>valid(b)), g_of_b_casted);
    }
  }
}
