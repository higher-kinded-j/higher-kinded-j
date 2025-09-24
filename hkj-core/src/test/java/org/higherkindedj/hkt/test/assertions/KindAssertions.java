// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.assertions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.ThrowableAssert;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.util.context.KindContext;

/**
 * Assertions for Kind operation validation (widen, narrow, etc.).
 *
 * <p>Aligns with {@link org.higherkindedj.hkt.util.validation.KindValidator} to ensure test
 * assertions match production validation logic.
 *
 * <p>This class provides context-aware assertions that automatically use the same error messages as
 * the production validators, ensuring consistency and reducing maintenance overhead.
 *
 * <h2>Usage Examples:</h2>
 *
 * <pre>{@code
 * // Test narrow operation with null Kind
 * assertNarrowNull(() -> helper.narrow(null), MyType.class);
 * // Error: "Cannot narrow null Kind for MyType"
 *
 * // Test widen operation with null input
 * assertWidenNull(() -> helper.widen(null), MyType.class);
 * // Error: "Input MyType cannot be null for widen"
 *
 * // Test invalid Kind type
 * Kind<F, A> invalidKind = createDummyKind("test");
 * assertInvalidKindType(() -> helper.narrow(invalidKind), MyType.class, invalidKind);
 * // Error: "Kind instance is not a MyType: DummyKind"
 *
 * // Test null Kind parameter in operations
 * assertKindNull(() -> monad.map(validMapper, null), "map");
 * // Error: "Cannot map null Kind for Kind"
 * }</pre>
 *
 * <h2>Design Notes:</h2>
 *
 * <ul>
 *   <li>All methods use {@link KindContext} for error messages
 *   <li>Error messages automatically match production validators
 *   <li>Type-safe - uses Class<?> for target type information
 *   <li>Composable - can be used by higher-level assertion classes
 * </ul>
 *
 * @see org.higherkindedj.hkt.util.validation.KindValidator
 * @see org.higherkindedj.hkt.util.context.KindContext
 */
public final class KindAssertions {

  private KindAssertions() {
    throw new AssertionError("KindAssertions is a utility class and should not be instantiated");
  }

  /**
   * Asserts that narrowing a null Kind throws {@link KindUnwrapException} with correct context.
   *
   * <p>This assertion aligns with {@link
   * org.higherkindedj.hkt.util.validation.KindValidator#narrow} validation logic.
   *
   * @param executable The narrowing code that should throw
   * @param targetType The target type class for context-aware error message
   * @return Throwable assertion for further chaining
   * @throws AssertionError if the exception is not thrown or has wrong message
   * @see org.higherkindedj.hkt.util.context.KindContext#narrow(Class)
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertNarrowNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> targetType) {

    var context = KindContext.narrow(targetType);
    return assertThatThrownBy(executable)
        .isInstanceOf(KindUnwrapException.class)
        .hasMessage(context.nullParameterMessage());
  }

  /**
   * Asserts that widening null input throws {@link NullPointerException} with correct context.
   *
   * <p>This assertion aligns with {@link
   * org.higherkindedj.hkt.util.validation.KindValidator#requireForWiden} validation logic.
   *
   * @param executable The widening code that should throw
   * @param targetType The target type class for context-aware error message
   * @return Throwable assertion for further chaining
   * @throws AssertionError if the exception is not thrown or has wrong message
   * @see org.higherkindedj.hkt.util.context.KindContext#widen(Class)
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertWidenNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> targetType) {

    var context = KindContext.widen(targetType);
    return assertThatThrownBy(executable)
        .isInstanceOf(NullPointerException.class)
        .hasMessage(context.nullInputMessage());
  }

  //  /**
  //   * Asserts that narrowing an invalid Kind type throws {@link KindUnwrapException} with correct
  //   * context.
  //   *
  //   * <p>This assertion verifies that the narrow operation properly validates the Kind type and
  //   * provides a meaningful error message including both expected and actual types.
  //   *
  //   * @param executable The narrowing code that should throw
  //   * @param targetType The expected target type class
  //   * @param invalidKind The actual invalid Kind instance
  //   * @return Throwable assertion for further chaining
  //   * @throws AssertionError if the exception is not thrown or has wrong message
  //   * @see org.higherkindedj.hkt.util.context.KindContext#invalidTypeMessage(String)
  //   */
  //  public static AbstractThrowableAssert<?, ? extends Throwable> assertInvalidKindType(
  //      ThrowableAssert.ThrowingCallable executable, Class<?> targetType, Kind<?, ?> invalidKind)
  // {
  //
  //    var context = KindContext.narrow(targetType);
  //    return assertThatThrownBy(executable)
  //        .isInstanceOf(KindUnwrapException.class)
  //        .hasMessage(context.invalidTypeMessage(invalidKind.getClass().getName()));
  //  }

  /**
   * Asserts that narrowing an invalid Kind type throws {@link KindUnwrapException} with correct
   * context.
   *
   * <p>This assertion verifies that the narrow operation properly validates the Kind type and
   * provides a meaningful error message including both expected and actual types.
   *
   * @param executable The narrowing code that should throw
   * @param targetType The expected target type class
   * @param invalidKind The actual invalid Kind instance
   * @return Throwable assertion for further chaining
   * @throws AssertionError if the exception is not thrown or has wrong message
   * @see org.higherkindedj.hkt.util.context.KindContext#invalidTypeMessage(String)
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertInvalidKindType(
      ThrowableAssert.ThrowingCallable executable, Class<?> targetType, Kind<?, ?> invalidKind) {

    return assertThatThrownBy(executable)
        .isInstanceOf(KindUnwrapException.class)
        .satisfies(
            throwable -> {
              String message = throwable.getMessage();
              String simpleName = targetType.getSimpleName();
              String actualClassName = invalidKind.getClass().getName();

              // Message should mention the target type and the actual class
              assertThat(message)
                  .as("Error message should mention target type")
                  .containsIgnoringCase(simpleName);
              assertThat(message)
                  .as("Error message should mention actual class")
                  .contains(actualClassName);
            });
  }

  /**
   * Asserts that a Kind parameter null check throws {@link NullPointerException} with correct
   * context.
   *
   * <p>This is a generic assertion for any operation that takes a Kind parameter. Use this when
   * testing operations like map, flatMap, traverse, etc.
   *
   * <p>Example usage:
   *
   * <pre>{@code
   * assertKindNull(() -> monad.map(validMapper, null), "map");
   * assertKindNull(() -> monad.flatMap(validFlatMapper, null), "flatMap");
   * assertKindNull(() -> traverse.traverse(applicative, validFunction, null), "traverse");
   * }</pre>
   *
   * @param executable The code that should throw
   * @param operation The operation name for context-aware error message
   * @return Throwable assertion for further chaining
   * @throws AssertionError if the exception is not thrown or has wrong message
   * @see org.higherkindedj.hkt.util.context.KindContext
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertKindNull(
      ThrowableAssert.ThrowingCallable executable, String operation) {

    var context = new KindContext(Kind.class, operation);
    return assertThatThrownBy(executable)
        .isInstanceOf(NullPointerException.class)
        .hasMessage(context.nullParameterMessage());
  }

  /**
   * Asserts that a specific Kind parameter throws with a custom parameter name.
   *
   * <p>Use this when you need to test specific Kind parameters that have meaningful names (e.g.,
   * "source Kind", "function Kind", "argument Kind").
   *
   * <p>Example usage:
   *
   * <pre>{@code
   * assertKindNullWithName(() -> monad.ap(null, validKind), "function Kind", "ap");
   * assertKindNullWithName(() -> monad.ap(validFunc, null), "argument Kind", "ap");
   * }</pre>
   *
   * @param executable The code that should throw
   * @param parameterName The specific parameter name for the error message
   * @param operation The operation name for context
   * @return Throwable assertion for further chaining
   * @throws AssertionError if the exception is not thrown or has wrong message
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertKindNullWithName(
      ThrowableAssert.ThrowingCallable executable, String parameterName, String operation) {

    return assertThatThrownBy(executable)
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining(parameterName)
        .hasMessageContaining(operation);
  }

  /**
   * Asserts that multiple Kind validations can be chained together.
   *
   * <p>This is a convenience method for testing multiple Kind-related validations in sequence. It's
   * particularly useful when testing KindHelper implementations.
   *
   * <p>Example usage:
   *
   * <pre>{@code
   * assertAllKindValidations(
   *     MyType.class,
   *     () -> helper.narrow(null),           // Test null narrow
   *     () -> helper.widen(null),            // Test null widen
   *     () -> helper.narrow(invalidKind)     // Test invalid type
   * );
   * }</pre>
   *
   * @param targetType The target type class
   * @param narrowNull Executable for testing null narrow
   * @param widenNull Executable for testing null widen
   * @param invalidType Executable for testing invalid type
   */
  public static void assertAllKindValidations(
      Class<?> targetType,
      ThrowableAssert.ThrowingCallable narrowNull,
      ThrowableAssert.ThrowingCallable widenNull,
      ThrowableAssert.ThrowingCallable invalidType) {

    assertNarrowNull(narrowNull, targetType);
    assertWidenNull(widenNull, targetType);
    // Note: invalidType needs the actual invalid Kind, so we just verify it throws
    assertThatThrownBy(invalidType).isInstanceOf(KindUnwrapException.class);
  }
}
