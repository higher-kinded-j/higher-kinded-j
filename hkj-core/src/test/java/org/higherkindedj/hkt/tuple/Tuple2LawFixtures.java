// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.tuple;

import static org.higherkindedj.hkt.tuple.Tuple2KindHelper.TUPLE2;

import java.util.function.BiPredicate;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind2;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Shared law fixtures for the {@code Tuple2} {@link Tuple2Bifunctor} tests.
 *
 * <p>Referenced from the {@code Laws} block via a fully-qualified {@code @MethodSource}. {@code
 * Tuple2<A, B>} is an eager product carrying both components, so its bifunctor equality is just
 * structural {@link Tuple2#equals(Object) Tuple2.equals} after narrowing.
 */
@SuppressWarnings("unused") // referenced reflectively via @MethodSource
final class Tuple2LawFixtures {

  private Tuple2LawFixtures() {}

  /** Two {@code Tuple2} {@code Kind2}s are equal iff their underlying {@code Tuple2}s are equal. */
  static final BiPredicate<Kind2<Tuple2Kind2.Witness, ?, ?>, Kind2<Tuple2Kind2.Witness, ?, ?>>
      BIFUNCTOR_EQ = (k1, k2) -> TUPLE2.narrow2(k1).equals(TUPLE2.narrow2(k2));

  /** Bifunctor {@code Kind2} fixtures spanning a few populated {@code (first, second)} pairs. */
  static Stream<Arguments> kind2s() {
    return Stream.of(
        Arguments.of("(\"hello\", 42)", TUPLE2.widen2(new Tuple2<>("hello", 42))),
        Arguments.of("(\"\", 0)", TUPLE2.widen2(new Tuple2<>("", 0))),
        Arguments.of("(\"world\", -1)", TUPLE2.widen2(new Tuple2<>("world", -1))));
  }
}
