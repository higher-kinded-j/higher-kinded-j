// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free;

import java.util.Objects;
import org.assertj.core.api.AbstractAssert;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Custom AssertJ assertions for {@link Free} instances.
 *
 * <h2>Usage:</h2>
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.free.FreeAssert.assertThatFree;
 *
 * Free<F, String> program = Free.pure("hello");
 * assertThatFree(program).isPure().hasPureValue("hello");
 * }</pre>
 *
 * @param <F> The functor type
 * @param <A> The result type
 */
public class FreeAssert<F extends WitnessArity<TypeArity.Unary>, A>
    extends AbstractAssert<FreeAssert<F, A>, Free<F, A>> {

  public static <F extends WitnessArity<TypeArity.Unary>, A> FreeAssert<F, A> assertThatFree(
      Free<F, A> actual) {
    return new FreeAssert<>(actual);
  }

  protected FreeAssert(Free<F, A> actual) {
    super(actual, FreeAssert.class);
  }

  /** Asserts the Free value is a Pure. */
  public FreeAssert<F, A> isPure() {
    isNotNull();
    if (!(actual instanceof Free.Pure<?, ?>)) {
      failWithMessage("Expected Free to be Pure but was %s", actual.getClass().getSimpleName());
    }
    return this;
  }

  /** Asserts the Free value is a Pure containing the expected value. */
  @SuppressWarnings("unchecked")
  public FreeAssert<F, A> hasPureValue(A expected) {
    isPure();
    A value = ((Free.Pure<F, A>) actual).value();
    if (!Objects.equals(value, expected)) {
      failWithMessage("Expected Pure value <%s> but was <%s>", expected, value);
    }
    return this;
  }

  /** Asserts the Free value is a Suspend. */
  public FreeAssert<F, A> isSuspend() {
    isNotNull();
    if (!(actual instanceof Free.Suspend<?, ?>)) {
      failWithMessage("Expected Free to be Suspend but was %s", actual.getClass().getSimpleName());
    }
    return this;
  }

  /** Asserts the Free value is a FlatMapped. */
  public FreeAssert<F, A> isFlatMapped() {
    isNotNull();
    if (!(actual instanceof Free.FlatMapped<?, ?, ?>)) {
      failWithMessage(
          "Expected Free to be FlatMapped but was %s", actual.getClass().getSimpleName());
    }
    return this;
  }

  /** Asserts the Free value is a HandleError. */
  public FreeAssert<F, A> isHandleError() {
    isNotNull();
    if (!(actual instanceof Free.HandleError<?, ?, ?>)) {
      failWithMessage(
          "Expected Free to be HandleError but was %s", actual.getClass().getSimpleName());
    }
    return this;
  }

  /** Asserts the Free value is an Ap. */
  public FreeAssert<F, A> isAp() {
    isNotNull();
    if (!(actual instanceof Free.Ap<?, ?>)) {
      failWithMessage("Expected Free to be Ap but was %s", actual.getClass().getSimpleName());
    }
    return this;
  }

  /**
   * Interprets the Free program and returns the result for further assertions.
   *
   * @param interpreter The natural transformation
   * @param monad The target monad
   * @param <M> The target monad type
   * @return The interpreted result as a Kind
   */
  public <M extends WitnessArity<TypeArity.Unary>> Kind<M, A> interpretedWith(
      Natural<F, M> interpreter, Monad<M> monad) {
    isNotNull();
    return actual.foldMap(interpreter, monad);
  }
}
