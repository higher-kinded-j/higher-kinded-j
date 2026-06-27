// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.nonemptylist;

import static org.higherkindedj.hkt.nonemptylist.NonEmptyListKindHelper.NON_EMPTY_LIST;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.fixtures.TestFunctions;
import org.higherkindedj.hkt.test.fixtures.TypeClassTestBase;

/**
 * Base class for {@link NonEmptyList} type class tests.
 *
 * <p>Provides common fixture creation, standardised test constants, and helper methods for all
 * NonEmptyList type class tests, eliminating duplication across the Monad and Traverse tests.
 *
 * <p>Because a {@code NonEmptyList} can never be empty, every fixture holds at least one element.
 */
abstract class NonEmptyListTestBase
    extends TypeClassTestBase<NonEmptyListKind.Witness, Integer, String> {

  /** Default value for NonEmptyList instances in tests. */
  protected static final Integer DEFAULT_VALUE = 42;

  /** Alternative value for NonEmptyList instances when testing with multiple values. */
  protected static final Integer ALTERNATIVE_VALUE = 24;

  /** Third value for tests requiring more than two values. */
  protected static final Integer THIRD_VALUE = 100;

  /**
   * Creates a {@code NonEmptyList} Kind from a guaranteed head plus zero or more further elements.
   */
  @SafeVarargs
  protected final <A> Kind<NonEmptyListKind.Witness, A> nelOf(A head, A... rest) {
    return NON_EMPTY_LIST.widen(NonEmptyList.of(head, rest));
  }

  /** Creates a single-element {@code NonEmptyList} Kind. */
  protected <A> Kind<NonEmptyListKind.Witness, A> singleNel(A element) {
    return NON_EMPTY_LIST.widen(NonEmptyList.single(element));
  }

  // ============================================================================
  // Integer-based Fixtures (from parent class requirements)
  // ============================================================================

  @Override
  protected Kind<NonEmptyListKind.Witness, Integer> createValidKind() {
    return nelOf(DEFAULT_VALUE, ALTERNATIVE_VALUE);
  }

  @Override
  protected Kind<NonEmptyListKind.Witness, Integer> createValidKind2() {
    return nelOf(THIRD_VALUE, 200);
  }

  @Override
  protected Function<Integer, String> createValidMapper() {
    return TestFunctions.INT_TO_STRING;
  }

  @Override
  protected Function<Integer, Kind<NonEmptyListKind.Witness, String>> createValidFlatMapper() {
    return i -> nelOf("flat:" + i, "mapped:" + i);
  }

  @Override
  protected Kind<NonEmptyListKind.Witness, Function<Integer, String>> createValidFunctionKind() {
    return nelOf(TestFunctions.INT_TO_STRING, i -> "Alt:" + i);
  }

  @Override
  protected BiFunction<Integer, Integer, String> createValidCombiningFunction() {
    return (a, b) -> "Result:" + a + "," + b;
  }

  @Override
  protected Integer createTestValue() {
    return DEFAULT_VALUE;
  }

  @Override
  protected Function<String, String> createSecondMapper() {
    return s -> "Transformed:" + s;
  }

  @Override
  protected Function<Integer, Kind<NonEmptyListKind.Witness, String>> createTestFunction() {
    return i -> nelOf("test:" + i, "value:" + i);
  }

  @Override
  protected Function<String, Kind<NonEmptyListKind.Witness, String>> createChainFunction() {
    return s -> nelOf(s + "!", s + "?");
  }

  @Override
  protected BiPredicate<Kind<NonEmptyListKind.Witness, ?>, Kind<NonEmptyListKind.Witness, ?>>
      createEqualityChecker() {
    return NonEmptyListLawFixtures.EQ;
  }
}
