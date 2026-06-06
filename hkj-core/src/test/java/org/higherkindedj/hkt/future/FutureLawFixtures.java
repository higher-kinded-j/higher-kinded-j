// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.future;

import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Shared law fixtures for the CompletableFuture type-class tests.
 *
 * <p>Referenced from the {@code *LawTests} blocks via a fully-qualified {@code @MethodSource}, e.g.
 * {@code @MethodSource("org.higherkindedj.hkt.future.FutureLawFixtures#kinds")}. Centralising the
 * fixture streams keeps every algebra's law verification driven by the same inputs rather than the
 * copy-pasted single fixtures that previously lived in each block. The fixtures are
 * <em>completed</em> futures so the law equality checker can {@code join()} both sides
 * synchronously.
 */
@SuppressWarnings("unused") // referenced reflectively via @MethodSource
final class FutureLawFixtures {

  private FutureLawFixtures() {}

  /**
   * Equality on {@code Kind<CF, ?>} by joining both completed futures and comparing the values.
   * Shared by the unit law block and the property test.
   */
  static final BiPredicate<
          Kind<CompletableFutureKind.Witness, ?>, Kind<CompletableFutureKind.Witness, ?>>
      EQ = (k1, k2) -> Objects.equals(FUTURE.join(k1), FUTURE.join(k2));

  /** Completed {@code CompletableFuture}s wrapping {@code 0}, {@code 42}, {@code -1}. */
  static Stream<Arguments> kinds() {
    return Stream.of(
        Arguments.of("future(0)", FUTURE.widen(CompletableFuture.completedFuture(0))),
        Arguments.of("future(42)", FUTURE.widen(CompletableFuture.completedFuture(42))),
        Arguments.of("future(-1)", FUTURE.widen(CompletableFuture.completedFuture(-1))));
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
