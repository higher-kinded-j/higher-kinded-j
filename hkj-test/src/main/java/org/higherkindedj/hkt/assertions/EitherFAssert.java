// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.util.Objects;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.eitherf.EitherF;

/** Custom AssertJ assertions for {@link EitherF} instances. */
public class EitherFAssert<F extends WitnessArity<?>, G extends WitnessArity<?>, A>
    extends AbstractAssert<EitherFAssert<F, G, A>, EitherF<F, G, A>> {

  /** Entry point for EitherF assertions. */
  public static <F extends WitnessArity<?>, G extends WitnessArity<?>, A>
      EitherFAssert<F, G, A> assertThatEitherF(EitherF<F, G, A> actual) {
    return new EitherFAssert<>(actual);
  }

  protected EitherFAssert(EitherF<F, G, A> actual) {
    super(actual, EitherFAssert.class);
  }

  /** Verifies that the actual EitherF is a Left. */
  public EitherFAssert<F, G, A> isLeft() {
    isNotNull();
    Assertions.assertThat(actual)
        .withFailMessage("Expected EitherF to be Left but was Right")
        .isInstanceOf(EitherF.Left.class);
    return this;
  }

  /** Verifies that the actual EitherF is a Right. */
  public EitherFAssert<F, G, A> isRight() {
    isNotNull();
    Assertions.assertThat(actual)
        .withFailMessage("Expected EitherF to be Right but was Left")
        .isInstanceOf(EitherF.Right.class);
    return this;
  }

  /** Verifies that the actual EitherF is a Left and the contained value satisfies requirements. */
  public EitherFAssert<F, G, A> hasLeftSatisfying(Consumer<Kind<F, A>> requirements) {
    Objects.requireNonNull(requirements, "requirements must not be null");
    isLeft();
    @SuppressWarnings("unchecked")
    EitherF.Left<F, G, A> left = (EitherF.Left<F, G, A>) actual;
    requirements.accept(left.value());
    return this;
  }

  /** Verifies that the actual EitherF is a Right and the contained value satisfies requirements. */
  public EitherFAssert<F, G, A> hasRightSatisfying(Consumer<Kind<G, A>> requirements) {
    Objects.requireNonNull(requirements, "requirements must not be null");
    isRight();
    @SuppressWarnings("unchecked")
    EitherF.Right<F, G, A> right = (EitherF.Right<F, G, A>) actual;
    requirements.accept(right.value());
    return this;
  }
}
