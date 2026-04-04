// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.eitherf;

import java.util.Objects;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Custom AssertJ assertions for {@link EitherF} instances.
 *
 * <p>Provides fluent assertion methods specifically designed for testing {@code EitherF} instances,
 * making test code more readable and providing better error messages.
 *
 * <h2>Usage:</h2>
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.eitherf.EitherFAssert.assertThatEitherF;
 *
 * EitherF<F, G, String> left = EitherF.left(someOp);
 * assertThatEitherF(left).isLeft();
 *
 * EitherF<F, G, String> right = EitherF.right(otherOp);
 * assertThatEitherF(right).isRight();
 * }</pre>
 *
 * @param <F> The witness type for the left effect algebra
 * @param <G> The witness type for the right effect algebra
 * @param <A> The result type
 */
public class EitherFAssert<F extends WitnessArity<?>, G extends WitnessArity<?>, A>
    extends AbstractAssert<EitherFAssert<F, G, A>, EitherF<F, G, A>> {

  /**
   * Entry point for EitherF assertions.
   *
   * @param actual The EitherF instance to assert on
   * @param <F> The witness type for the left effect algebra
   * @param <G> The witness type for the right effect algebra
   * @param <A> The result type
   * @return A new EitherFAssert instance
   */
  public static <F extends WitnessArity<?>, G extends WitnessArity<?>, A>
      EitherFAssert<F, G, A> assertThatEitherF(EitherF<F, G, A> actual) {
    return new EitherFAssert<>(actual);
  }

  protected EitherFAssert(EitherF<F, G, A> actual) {
    super(actual, EitherFAssert.class);
  }

  /**
   * Verifies that the actual {@code EitherF} is a {@link EitherF.Left}.
   *
   * @return This assertion object for method chaining
   */
  public EitherFAssert<F, G, A> isLeft() {
    isNotNull();
    if (!(actual instanceof EitherF.Left<?, ?, ?>)) {
      failWithMessage("Expected EitherF to be Left but was Right");
    }
    return this;
  }

  /**
   * Verifies that the actual {@code EitherF} is a {@link EitherF.Right}.
   *
   * @return This assertion object for method chaining
   */
  public EitherFAssert<F, G, A> isRight() {
    isNotNull();
    if (!(actual instanceof EitherF.Right<?, ?, ?>)) {
      failWithMessage("Expected EitherF to be Right but was Left");
    }
    return this;
  }

  /**
   * Verifies that the actual {@code EitherF} is a Left and the contained value satisfies the given
   * requirements.
   *
   * @param requirements Assertions to apply to the left Kind value
   * @return This assertion object for method chaining
   */
  public EitherFAssert<F, G, A> hasLeftSatisfying(Consumer<Kind<F, A>> requirements) {
    Objects.requireNonNull(requirements, "requirements must not be null");
    isLeft();
    // Safe: isLeft() verified above; sealed interface guarantees Left type
    @SuppressWarnings("unchecked")
    EitherF.Left<F, G, A> left = (EitherF.Left<F, G, A>) actual;
    requirements.accept(left.value());
    return this;
  }

  /**
   * Verifies that the actual {@code EitherF} is a Right and the contained value satisfies the given
   * requirements.
   *
   * @param requirements Assertions to apply to the right Kind value
   * @return This assertion object for method chaining
   */
  public EitherFAssert<F, G, A> hasRightSatisfying(Consumer<Kind<G, A>> requirements) {
    Objects.requireNonNull(requirements, "requirements must not be null");
    isRight();
    // Safe: isRight() verified above; sealed interface guarantees Right type
    @SuppressWarnings("unchecked")
    EitherF.Right<F, G, A> right = (EitherF.Right<F, G, A>) actual;
    requirements.accept(right.value());
    return this;
  }
}
