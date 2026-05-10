// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.free.Free;

/** Custom AssertJ assertions for {@link Free} instances. */
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
    Assertions.assertThat(actual)
        .withFailMessage("Expected Free to be Pure but was %s", actual.getClass().getSimpleName())
        .isInstanceOf(Free.Pure.class);
    return this;
  }

  /** Asserts the Free value is a Pure containing the expected value. */
  @SuppressWarnings("unchecked")
  public FreeAssert<F, A> hasPureValue(A expected) {
    isPure();
    A value = ((Free.Pure<F, A>) actual).value();
    Assertions.assertThat(value)
        .withFailMessage("Expected Pure value <%s> but was <%s>", expected, value)
        .isEqualTo(expected);
    return this;
  }

  /** Asserts the Free value is a Suspend. */
  public FreeAssert<F, A> isSuspend() {
    isNotNull();
    Assertions.assertThat(actual)
        .withFailMessage(
            "Expected Free to be Suspend but was %s", actual.getClass().getSimpleName())
        .isInstanceOf(Free.Suspend.class);
    return this;
  }

  /** Asserts the Free value is a FlatMapped. */
  public FreeAssert<F, A> isFlatMapped() {
    isNotNull();
    Assertions.assertThat(actual)
        .withFailMessage(
            "Expected Free to be FlatMapped but was %s", actual.getClass().getSimpleName())
        .isInstanceOf(Free.FlatMapped.class);
    return this;
  }

  /** Asserts the Free value is a HandleError. */
  public FreeAssert<F, A> isHandleError() {
    isNotNull();
    Assertions.assertThat(actual)
        .withFailMessage(
            "Expected Free to be HandleError but was %s", actual.getClass().getSimpleName())
        .isInstanceOf(Free.HandleError.class);
    return this;
  }

  /** Asserts the Free value is an Ap. */
  public FreeAssert<F, A> isAp() {
    isNotNull();
    Assertions.assertThat(actual)
        .withFailMessage("Expected Free to be Ap but was %s", actual.getClass().getSimpleName())
        .isInstanceOf(Free.Ap.class);
    return this;
  }

  /** Interprets the Free program and returns the result for further assertions. */
  public <M extends WitnessArity<TypeArity.Unary>> Kind<M, A> interpretedWith(
      Natural<F, M> interpreter, Monad<M> monad) {
    isNotNull();
    return actual.foldMap(interpreter, monad);
  }
}
