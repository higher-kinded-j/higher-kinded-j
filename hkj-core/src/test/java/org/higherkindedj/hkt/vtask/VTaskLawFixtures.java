// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vtask;

import static org.higherkindedj.hkt.vtask.VTaskKindHelper.VTASK;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.trymonad.Try;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Shared law fixtures for the VTask type-class tests.
 *
 * <p>Referenced from the per-type-class {@code Laws} blocks via a fully-qualified
 * {@code @MethodSource}, e.g.
 * {@code @MethodSource("org.higherkindedj.hkt.vtask.VTaskLawFixtures#kinds")}. Centralising the
 * fixture streams keeps every algebra's law verification driven by the same inputs rather than the
 * copy-pasted single fixtures that previously lived in each block. The fixtures are
 * <em>successful</em> tasks so the law equality checker can {@code run()} both sides.
 */
@SuppressWarnings("unused") // referenced reflectively via @MethodSource
final class VTaskLawFixtures {

  private VTaskLawFixtures() {}

  /**
   * Equality for VTask kinds: runs both sides via {@link VTask#runSafe()} and compares outcomes.
   *
   * <p>Two tasks are equal when they share the same success/failure disposition and, on success,
   * equal values; on failure, the same exception type and message.
   */
  static final BiPredicate<Kind<VTaskKind.Witness, ?>, Kind<VTaskKind.Witness, ?>> EQ =
      (k1, k2) -> {
        Try<?> resultA = VTASK.narrow(k1).runSafe();
        Try<?> resultB = VTASK.narrow(k2).runSafe();

        if (resultA.isSuccess() != resultB.isSuccess()) {
          return false;
        }

        if (resultA.isSuccess()) {
          return Objects.equals(resultA.orElse(null), resultB.orElse(null));
        } else {
          // Both failures - compare exception types and messages
          Throwable causeA = ((Try.Failure<?>) resultA).cause();
          Throwable causeB = ((Try.Failure<?>) resultB).cause();
          return causeA.getClass().equals(causeB.getClass())
              && Objects.equals(causeA.getMessage(), causeB.getMessage());
        }
      };

  /** Successful {@code VTask}s producing {@code 0}, {@code 42}, {@code -1}. */
  static Stream<Arguments> kinds() {
    return Stream.of(
        Arguments.of("succeed(0)", VTASK.widen(VTask.succeed(0))),
        Arguments.of("succeed(42)", VTASK.widen(VTask.succeed(42))),
        Arguments.of("succeed(-1)", VTASK.widen(VTask.succeed(-1))));
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
