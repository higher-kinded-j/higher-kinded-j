// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.laws.KindHelperLaws;
import org.higherkindedj.hkt.test.assertions.ValidationTestBuilder;

/**
 * Fluent configuration for verifying a {@code KindHelper} (widen/narrow) round-trip.
 *
 * <p>Created via the {@link KindHelperTests} factory entries; each check can be skipped with the
 * {@code skip*} methods before calling {@link #test()}. The round-trip / idempotency / edge-case
 * laws delegate to the shipped {@code hkj-test} {@link KindHelperLaws}; the defensive null and
 * foreign-kind checks — coupled to the in-core validation machinery — are run here.
 *
 * @param <T> the concrete type (e.g. {@code Either<L, R>})
 * @param <F> the witness type
 * @param <A> the value type
 */
public final class KindHelperTestConfig<T, F extends WitnessArity<TypeArity.Unary>, A> {

  private final T instance;
  private final Class<T> targetClass;
  private final Function<T, Kind<F, A>> widen;
  private final Function<Kind<F, A>, T> narrow;

  private boolean roundTrip = true;
  private boolean idempotency = true;
  private boolean edgeCases = true;
  private boolean validations = true;
  private boolean invalidType = true;

  KindHelperTestConfig(
      T instance,
      Class<T> targetClass,
      Function<T, Kind<F, A>> widen,
      Function<Kind<F, A>, T> narrow) {
    this.instance = instance;
    this.targetClass = targetClass;
    this.widen = widen;
    this.narrow = narrow;
  }

  /** Skips the {@code narrow(widen(t)) == t} round-trip check. */
  public KindHelperTestConfig<T, F, A> skipRoundTrip() {
    this.roundTrip = false;
    return this;
  }

  /** Skips the repeated-round-trip idempotency check. */
  public KindHelperTestConfig<T, F, A> skipIdempotency() {
    this.idempotency = false;
    return this;
  }

  /** Skips the non-null / instance-of-target edge-case checks. */
  public KindHelperTestConfig<T, F, A> skipEdgeCases() {
    this.edgeCases = false;
    return this;
  }

  /** Skips the {@code widen(null)}/{@code narrow(null)} rejection checks. */
  public KindHelperTestConfig<T, F, A> skipValidations() {
    this.validations = false;
    return this;
  }

  /** Skips the foreign-kind rejection check. */
  public KindHelperTestConfig<T, F, A> skipInvalidType() {
    this.invalidType = false;
    return this;
  }

  /** Runs the configured checks. */
  public void test() {
    if (roundTrip) {
      KindHelperLaws.assertRoundTrip(instance, widen, narrow);
    }
    if (idempotency) {
      KindHelperLaws.assertIdempotency(instance, widen, narrow);
    }
    if (edgeCases) {
      KindHelperLaws.assertEdgeCases(instance, targetClass, widen, narrow);
    }
    if (validations) {
      ValidationTestBuilder.create()
          .assertWidenNull(() -> widen.apply(null), targetClass)
          .assertNarrowNull(() -> narrow.apply(null), targetClass)
          .execute();
    }
    if (invalidType) {
      Kind<F, A> foreign = createDummyKind("invalid_" + targetClass.getSimpleName());
      assertThatThrownBy(() -> narrow.apply(foreign))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(
              "Kind instance cannot be narrowed to " + targetClass.getSimpleName());
    }
  }

  private static <F extends WitnessArity<TypeArity.Unary>, A> Kind<F, A> createDummyKind(
      String identifier) {
    return new Kind<>() {
      @Override
      public String toString() {
        return "DummyKind{" + identifier + "}";
      }
    };
  }
}
