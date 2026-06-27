// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.nonemptylist;

import static org.higherkindedj.hkt.nonemptylist.NonEmptyListKindHelper.NON_EMPTY_LIST;

import java.util.function.BiPredicate;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Shared law fixtures for the {@link NonEmptyList} type-class tests.
 *
 * <p>Referenced from the per-type-class {@code Laws} blocks via a fully-qualified
 * {@code @MethodSource}, e.g.
 * {@code @MethodSource("org.higherkindedj.hkt.nonemptylist.NonEmptyListLawFixtures#kinds")}. Every
 * fixture is non-empty by construction.
 */
@SuppressWarnings("unused") // referenced reflectively via @MethodSource
final class NonEmptyListLawFixtures {

  private NonEmptyListLawFixtures() {}

  /**
   * Shared law equality: narrows both NonEmptyList kinds and compares the results with {@code
   * equals}.
   */
  static final BiPredicate<Kind<NonEmptyListKind.Witness, ?>, Kind<NonEmptyListKind.Witness, ?>>
      EQ = KindEquivalence.byEqualsAfter(NON_EMPTY_LIST::narrow);

  /** {@code [42]}, {@code [1,2,3]}, {@code [-1,0,1]}, {@code [7]}. */
  static Stream<Arguments> kinds() {
    return Stream.of(
        Arguments.of("[42]", NON_EMPTY_LIST.widen(NonEmptyList.single(42))),
        Arguments.of("[1,2,3]", NON_EMPTY_LIST.widen(NonEmptyList.of(1, 2, 3))),
        Arguments.of("[-1,0,1]", NON_EMPTY_LIST.widen(NonEmptyList.of(-1, 0, 1))),
        Arguments.of("[7]", NON_EMPTY_LIST.widen(NonEmptyList.single(7))));
  }

  /** Scalar law values {@code {0, 42, -1}}. */
  static Stream<Arguments> values() {
    return Stream.of(Arguments.of(0), Arguments.of(42), Arguments.of(-1));
  }

  /** Scalar law strings {@code {"a", "hello"}}. */
  static Stream<Arguments> strings() {
    return Stream.of(Arguments.of("a"), Arguments.of("hello"));
  }
}
