// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.eitherorboth;

import static org.higherkindedj.hkt.eitherorboth.EitherOrBothKindHelper.EITHER_OR_BOTH;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.fixtures.TestFunctions;
import org.higherkindedj.hkt.test.fixtures.TypeClassTestBase;

/**
 * Base class for {@link EitherOrBoth} type class tests, fixing the left (warning) type to {@code
 * String}.
 *
 * <p>Provides common fixture creation and {@code Kind} builders for the {@link
 * EitherOrBoth.Left}/{@link EitherOrBoth.Right}/{@link EitherOrBoth.Both} cases, eliminating
 * duplication across the Functor, Monad, Bifunctor and Traverse tests. Because an {@code
 * EitherOrBoth} never holds {@code null}, every fixture is non-null.
 */
abstract class EitherOrBothTestBase
    extends TypeClassTestBase<EitherOrBothKind.Witness<String>, Integer, String> {

  /** Default right value used in tests. */
  protected static final Integer DEFAULT_RIGHT = 42;

  /** Alternative right value used in tests. */
  protected static final Integer ALTERNATIVE_RIGHT = 24;

  /** Default left (warning) value used in tests. */
  protected static final String DEFAULT_LEFT = "w0";

  /** Wraps a value in a {@link EitherOrBoth.Right} {@code Kind}. */
  protected <R> Kind<EitherOrBothKind.Witness<String>, R> rightK(R value) {
    return EITHER_OR_BOTH.widen(EitherOrBoth.right(value));
  }

  /** Wraps a warning in a {@link EitherOrBoth.Left} {@code Kind}. */
  protected <R> Kind<EitherOrBothKind.Witness<String>, R> leftK(String left) {
    return EITHER_OR_BOTH.widen(EitherOrBoth.left(left));
  }

  /** Wraps a warning and a value in a {@link EitherOrBoth.Both} {@code Kind}. */
  protected <R> Kind<EitherOrBothKind.Witness<String>, R> bothK(String left, R value) {
    return EITHER_OR_BOTH.widen(EitherOrBoth.both(left, value));
  }

  @Override
  protected Kind<EitherOrBothKind.Witness<String>, Integer> createValidKind() {
    return rightK(DEFAULT_RIGHT);
  }

  @Override
  protected Kind<EitherOrBothKind.Witness<String>, Integer> createValidKind2() {
    return rightK(ALTERNATIVE_RIGHT);
  }

  @Override
  protected Function<Integer, String> createValidMapper() {
    return TestFunctions.INT_TO_STRING;
  }

  @Override
  protected Function<Integer, Kind<EitherOrBothKind.Witness<String>, String>>
      createValidFlatMapper() {
    return i -> rightK("flat:" + i);
  }

  @Override
  protected Kind<EitherOrBothKind.Witness<String>, Function<Integer, String>>
      createValidFunctionKind() {
    return rightK(TestFunctions.INT_TO_STRING);
  }

  @Override
  protected BiFunction<Integer, Integer, String> createValidCombiningFunction() {
    return (a, b) -> "Result:" + a + "," + b;
  }

  @Override
  protected Integer createTestValue() {
    return DEFAULT_RIGHT;
  }

  @Override
  protected Function<String, String> createSecondMapper() {
    return s -> "Transformed:" + s;
  }

  @Override
  protected Function<Integer, Kind<EitherOrBothKind.Witness<String>, String>> createTestFunction() {
    return i -> rightK("test:" + i);
  }

  @Override
  protected Function<String, Kind<EitherOrBothKind.Witness<String>, String>> createChainFunction() {
    return s -> rightK(s + "!");
  }

  @Override
  protected BiPredicate<
          Kind<EitherOrBothKind.Witness<String>, ?>, Kind<EitherOrBothKind.Witness<String>, ?>>
      createEqualityChecker() {
    return EitherOrBothLawFixtures.EQ;
  }
}
