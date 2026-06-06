// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.fixtures.TypeClassTestBase;

/**
 * Base class for Try type class tests.
 *
 * <p>Provides common fixture creation and standardised test constants for all Try type class tests,
 * eliminating duplication across Functor, Applicative, Monad, MonadError, Foldable, and Traverse
 * tests.
 *
 * <h2>Test Constants</h2>
 *
 * <ul>
 *   <li>{@link #DEFAULT_SUCCESS_VALUE} - The primary test value ("test value")
 *   <li>{@link #ALTERNATIVE_SUCCESS_VALUE} - A secondary test value ("second value")
 *   <li>{@link #DEFAULT_TEST_EXCEPTION} - The primary test exception
 * </ul>
 *
 * <p>Try uses {@code String} as its primary value type ({@code A}) and {@code Integer} as the
 * secondary ({@code B}), so {@link #createValidMapper()} is {@code String::length}.
 */
abstract class TryTestBase extends TypeClassTestBase<TryKind.Witness, String, Integer> {

  /** Default value for Success instances in tests. */
  protected static final String DEFAULT_SUCCESS_VALUE = "test value";

  /** Alternative value for Success instances when testing with multiple values. */
  protected static final String ALTERNATIVE_SUCCESS_VALUE = "second value";

  /** Default exception for Failure instances in tests. */
  protected static final RuntimeException DEFAULT_TEST_EXCEPTION =
      new RuntimeException("Test exception");

  /**
   * Creates a Failure Kind with the specified exception.
   *
   * @param throwable The exception to wrap in a Failure
   * @return A Failure Kind containing the specified exception
   */
  protected <A> Kind<TryKind.Witness, A> failureKind(Throwable throwable) {
    return TRY.widen(Try.failure(throwable));
  }

  @Override
  protected Kind<TryKind.Witness, String> createValidKind() {
    return TRY.widen(Try.success(DEFAULT_SUCCESS_VALUE));
  }

  @Override
  protected Kind<TryKind.Witness, String> createValidKind2() {
    return TRY.widen(Try.success(ALTERNATIVE_SUCCESS_VALUE));
  }

  @Override
  protected Function<String, Integer> createValidMapper() {
    return String::length;
  }

  @Override
  protected Function<String, Kind<TryKind.Witness, Integer>> createValidFlatMapper() {
    return s -> TRY.widen(Try.success(s.length()));
  }

  @Override
  protected Kind<TryKind.Witness, Function<String, Integer>> createValidFunctionKind() {
    return TRY.widen(Try.success(String::length));
  }

  @Override
  protected BiFunction<String, String, Integer> createValidCombiningFunction() {
    return (s1, s2) -> s1.length() + s2.length();
  }

  @Override
  protected String createTestValue() {
    return DEFAULT_SUCCESS_VALUE;
  }

  @Override
  protected Function<Integer, String> createSecondMapper() {
    return i -> "Transformed:" + i;
  }

  @Override
  protected Function<String, Kind<TryKind.Witness, Integer>> createTestFunction() {
    return s -> TRY.widen(Try.success(s.length()));
  }

  @Override
  protected Function<Integer, Kind<TryKind.Witness, Integer>> createChainFunction() {
    return i -> TRY.widen(Try.success(i * 2));
  }

  @Override
  protected BiPredicate<Kind<TryKind.Witness, ?>, Kind<TryKind.Witness, ?>>
      createEqualityChecker() {
    return TryLawFixtures.EQ;
  }
}
