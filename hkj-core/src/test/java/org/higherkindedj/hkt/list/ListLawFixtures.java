// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.list;

import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Shared law fixtures for the List type-class tests.
 *
 * <p>Referenced from the per-type-class {@code Laws} blocks via a fully-qualified
 * {@code @MethodSource}, e.g.
 * {@code @MethodSource("org.higherkindedj.hkt.list.ListLawFixtures#kinds")}. Centralising the
 * fixture streams keeps every algebra's law verification driven by the same inputs and removes the
 * copy-pasted {@code fixtures()}/{@code values()}/{@code strings()} providers that previously lived
 * in each test.
 */
@SuppressWarnings("unused") // referenced reflectively via @MethodSource
final class ListLawFixtures {

  private ListLawFixtures() {}

  /**
   * Shared law equality: narrows both List kinds and compares the resulting lists with {@code
   * equals}.
   */
  static final BiPredicate<Kind<ListKind.Witness, ?>, Kind<ListKind.Witness, ?>> EQ =
      KindEquivalence.byEqualsAfter(LIST::narrow);

  /** {@code []}, {@code [42]}, {@code [1,2,3]}, {@code [-1,0,1]}. */
  static Stream<Arguments> kinds() {
    return Stream.of(
        Arguments.of("[]", LIST.<Integer>widen(List.of())),
        Arguments.of("[42]", LIST.widen(List.of(42))),
        Arguments.of("[1,2,3]", LIST.widen(List.of(1, 2, 3))),
        Arguments.of("[-1,0,1]", LIST.widen(List.of(-1, 0, 1))));
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
