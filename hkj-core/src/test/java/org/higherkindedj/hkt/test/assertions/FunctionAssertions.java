// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.assertions;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.ThrowableAssert;
import org.higherkindedj.hkt.util.context.FunctionContext;

/**
 * Assertions for function parameter validation in monad operations.
 *
 * <p>Aligns with {@link org.higherkindedj.hkt.util.validation.FunctionValidator} to ensure test
 * assertions match production validation logic.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * // Instead of generic null function assertion
 * assertNullFunctionThrows(() -> monad.map(null, validKind), "function f");
 *
 * // Use context-aware assertion
 * assertMapperNull(() -> monad.map(null, validKind), "map");
 * }</pre>
 */
public final class FunctionAssertions {

  private FunctionAssertions() {
    throw new AssertionError("FunctionAssertions is a utility class");
  }

  /**
   * Asserts that a mapper function null check throws with correct context.
   *
   * @param executable The code that should throw
   * @param operation The operation name (map, foldMap, etc.)
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMapperNull(
      ThrowableAssert.ThrowingCallable executable, String operation) {

    var context = FunctionContext.mapper(operation);
    return assertThatThrownBy(executable)
        .isInstanceOf(NullPointerException.class)
        .hasMessage(context.nullParameterMessage());
  }

  /**
   * Asserts that a flatMapper function null check throws with correct context.
   *
   * @param executable The code that should throw
   * @param operation The operation name (flatMap, bind, etc.)
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertFlatMapperNull(
      ThrowableAssert.ThrowingCallable executable, String operation) {

    var context = FunctionContext.flatMapper(operation);
    return assertThatThrownBy(executable)
        .isInstanceOf(NullPointerException.class)
        .hasMessage(context.nullParameterMessage());
  }

  /**
   * Asserts that an applicative instance null check throws with correct context.
   *
   * @param executable The code that should throw
   * @param operation The operation name (traverse, sequence, etc.)
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertApplicativeNull(
      ThrowableAssert.ThrowingCallable executable, String operation) {

    var context = FunctionContext.applicative(operation);
    return assertThatThrownBy(executable)
        .isInstanceOf(NullPointerException.class)
        .hasMessage(context.nullParameterMessage());
  }

  /**
   * Asserts that a monoid instance null check throws with correct context.
   *
   * @param executable The code that should throw
   * @param operation The operation name (foldMap, etc.)
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMonoidNull(
      ThrowableAssert.ThrowingCallable executable, String operation) {

    var context = new FunctionContext("monoid", operation);
    return assertThatThrownBy(executable)
        .isInstanceOf(NullPointerException.class)
        .hasMessage(context.nullParameterMessage());
  }

  /**
   * Generic function parameter null assertion with custom context.
   *
   * @param executable The code that should throw
   * @param functionName The name of the function parameter
   * @param operation The operation name
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertFunctionNull(
      ThrowableAssert.ThrowingCallable executable, String functionName, String operation) {

    var context = new FunctionContext(functionName, operation);
    return assertThatThrownBy(executable)
        .isInstanceOf(NullPointerException.class)
        .hasMessage(context.nullParameterMessage());
  }
}
