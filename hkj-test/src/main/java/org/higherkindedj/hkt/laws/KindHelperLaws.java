// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.laws;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Reusable round-trip verification for {@code KindHelper} (widen/narrow) implementations.
 *
 * <p>A {@code KindHelper} witnesses the isomorphism between a concrete type {@code T} (e.g. {@code
 * Id<A>}) and its defunctionalised {@code Kind<F, A>}. These helpers verify the laws that the
 * isomorphism must satisfy:
 *
 * <ul>
 *   <li><b>round-trip</b>: {@code narrow(widen(t)) == t} (reference identity)
 *   <li><b>idempotency</b>: repeated round-trips keep returning the same instance
 *   <li><b>edge cases</b>: {@code widen}/{@code narrow} never return null and {@code narrow} yields
 *       the target type
 * </ul>
 *
 * <p>Library users building their own HKT simulation can call these against their own helper to
 * confirm it round-trips cleanly. The complementary <em>defensive</em> guarantees — that {@code
 * widen(null)}/{@code narrow(null)} and narrowing a foreign {@code Kind} fail like the production
 * validators — are exercised by hkj-core's internal {@code KindAssertions}, since asserting "this
 * always throws" cannot be expressed as 100%-coverable code (a line that always throws never
 * reaches its JaCoCo end-probe) and those checks are tightly coupled to the in-core {@code
 * Validation} machinery in any case.
 */
public final class KindHelperLaws {

  private KindHelperLaws() {}

  /** Round-trip: {@code narrow(widen(instance))} is the very same instance. */
  public static <T, F extends WitnessArity<TypeArity.Unary>, A> void assertRoundTrip(
      T instance, Function<T, Kind<F, A>> widen, Function<Kind<F, A>, T> narrow) {
    Kind<F, A> widened = widen.apply(instance);
    T narrowed = narrow.apply(widened);
    assertThat(narrowed).as("KindHelper round-trip: narrow(widen(t)) == t").isSameAs(instance);
  }

  /** Idempotency: three successive round-trips still return the original instance. */
  public static <T, F extends WitnessArity<TypeArity.Unary>, A> void assertIdempotency(
      T instance, Function<T, Kind<F, A>> widen, Function<Kind<F, A>, T> narrow) {
    T current = instance;
    for (int i = 0; i < 3; i++) {
      current = narrow.apply(widen.apply(current));
    }
    assertThat(current)
        .as("KindHelper idempotency: repeated round-trips preserve identity")
        .isSameAs(instance);
  }

  /** Edge cases: {@code widen}/{@code narrow} are non-null and {@code narrow} yields the target. */
  public static <T, F extends WitnessArity<TypeArity.Unary>, A> void assertEdgeCases(
      T instance,
      Class<T> targetType,
      Function<T, Kind<F, A>> widen,
      Function<Kind<F, A>, T> narrow) {
    Kind<F, A> widened = widen.apply(instance);
    assertThat(widened).as("widen returns a non-null Kind").isNotNull();
    T narrowed = narrow.apply(widened);
    assertThat(narrowed).as("narrow returns non-null for a valid Kind").isNotNull();
    assertThat(narrowed)
        .as("narrowed result is an instance of the target type")
        .isInstanceOf(targetType);
  }
}
