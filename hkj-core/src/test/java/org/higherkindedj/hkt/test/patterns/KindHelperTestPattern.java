// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.patterns;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.builders.ValidationTestBuilder;
import org.higherkindedj.hkt.test.data.TestData;

/**
 * Complete test pattern for KindHelper implementations.
 *
 * <p>Provides a comprehensive test suite for widen/narrow operations including:
 *
 * <ul>
 *   <li>Round-trip identity preservation
 *   <li>Null parameter validation
 *   <li>Invalid type validation
 *   <li>Idempotency testing
 * </ul>
 *
 * <p>Usage:
 *
 * <pre>{@code
 * @Test
 * void testEitherKindHelper() {
 *     KindHelperTestPattern.runComplete(
 *         Either.right("test"),
 *         Either.class,
 *         EITHER::widen,
 *         EITHER::narrow
 *     );
 * }
 * }</pre>
 */
public final class KindHelperTestPattern {

  private KindHelperTestPattern() {
    throw new AssertionError("KindHelperTestPattern is a utility class");
  }

  /** Runs complete KindHelper test suite. */
  public static <T, F, A> void runComplete(
      T validInstance,
      Class<T> targetType,
      Function<T, Kind<F, A>> widenFunc,
      Function<Kind<F, A>, T> narrowFunc) {

    testRoundTrip(validInstance, widenFunc, narrowFunc);
    testNullValidations(targetType, widenFunc, narrowFunc);
    testInvalidType(targetType, widenFunc, narrowFunc);
    testIdempotency(validInstance, widenFunc, narrowFunc);
  }

  /** Tests round-trip widen/narrow preserves identity. */
  public static <T, F, A> void testRoundTrip(
      T validInstance, Function<T, Kind<F, A>> widenFunc, Function<Kind<F, A>, T> narrowFunc) {

    Kind<F, A> widened = widenFunc.apply(validInstance);
    T narrowed = narrowFunc.apply(widened);

    assertThat(narrowed)
        .as("Round-trip widen/narrow should preserve identity")
        .isSameAs(validInstance);
  }

  /** Tests null parameter validations. */
  public static <T, F, A> void testNullValidations(
      Class<T> targetType, Function<T, Kind<F, A>> widenFunc, Function<Kind<F, A>, T> narrowFunc) {

    ValidationTestBuilder.create()
        .assertWidenNull(() -> widenFunc.apply(null), targetType)
        .assertNarrowNull(() -> narrowFunc.apply(null), targetType)
        .execute();
  }

  /** Tests invalid Kind type validation. */
  public static <T, F, A> void testInvalidType(
      Class<T> targetType, Function<T, Kind<F, A>> widenFunc, Function<Kind<F, A>, T> narrowFunc) {

    Kind<F, A> invalidKind = TestData.createDummyKind("invalid_" + targetType.getSimpleName());

    ValidationTestBuilder.create()
        .assertInvalidKindType(() -> narrowFunc.apply(invalidKind), targetType, invalidKind)
        .execute();
  }

  /** Tests multiple round-trips preserve idempotency. */
  public static <T, F, A> void testIdempotency(
      T validInstance, Function<T, Kind<F, A>> widenFunc, Function<Kind<F, A>, T> narrowFunc) {

    T current = validInstance;
    for (int i = 0; i < 3; i++) {
      Kind<F, A> widened = widenFunc.apply(current);
      current = narrowFunc.apply(widened);
    }

    assertThat(current).as("Multiple round-trips should preserve identity").isSameAs(validInstance);
  }
}
