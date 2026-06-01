// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.laws;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.BiPredicate;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;

/**
 * Flat law-verification helpers for {@link Traverse} instances.
 *
 * <p>Each helper checks one law for a single fixture. Drive coverage with
 * {@code @ParameterizedTest @MethodSource} or jqwik {@code @Property @ForAll} over a fixture stream
 * rather than enumerating values inside the law method.
 *
 * <p>Library users can call these against their own {@code Traverse} instances to verify the laws
 * hold on their custom type-class implementations.
 *
 * <p>The full Traverse law set is identity, naturality and composition. Only <b>identity</b> is
 * checked here — it is the strongest law expressible without a second applicative or a natural
 * transformation, and it already pins down that {@code traverse} neither drops, duplicates nor
 * reorders the traversable's structure.
 */
public final class TraverseLaws {

  private TraverseLaws() {}

  /**
   * Identity: {@code traverse(Id.applicative(), Id::of, ta) == Id.of(ta)} — traversing with the
   * identity applicative is a no-op modulo the {@link Id} wrapper.
   *
   * @param traverse the instance under test
   * @param fa a sample traversable value
   * @param eq structural equality over {@code Kind<F, ?>} (e.g. {@code
   *     KindEquivalence.byEqualsAfter(HELPER::narrow)})
   * @param <F> the traversable witness type
   * @param <A> the element type
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A> void assertIdentity(
      Traverse<F> traverse, Kind<F, A> fa, BiPredicate<Kind<F, ?>, Kind<F, ?>> eq) {
    Kind<IdKind.Witness, Kind<F, A>> traversed =
        traverse.traverse(IdMonad.instance(), a -> IdKindHelper.ID.widen(Id.of(a)), fa);
    Kind<F, A> result = IdKindHelper.ID.narrow(traversed).value();
    assertThat(eq.test(result, fa))
        .as("Traverse identity: traverse(Id, Id::of, fa) == fa")
        .isTrue();
  }
}
