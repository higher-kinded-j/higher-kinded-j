// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.io;

import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Shared law fixtures for the IO type-class tests.
 *
 * <p>Referenced from the per-type-class {@code Laws} blocks via a fully-qualified
 * {@code @MethodSource}, e.g.
 * {@code @MethodSource("org.higherkindedj.hkt.io.IOLawFixtures#kinds")}. Centralising the fixture
 * streams keeps every algebra's law verification driven by the same inputs and removes the
 * copy-pasted {@code fixtures()}/{@code values()}/{@code strings()} providers that previously lived
 * in each test. IO is lazy, so the law equality checker runs both sides via {@code
 * unsafeRunSync()}.
 */
@SuppressWarnings("unused") // referenced reflectively via @MethodSource
final class IOLawFixtures {

  private IOLawFixtures() {}

  /**
   * IO equality: run both via {@code unsafeRunSync()} and compare results. Shared by the unit law
   * block and the property tests. Fixtures must be pure for this to make sense.
   */
  static final BiPredicate<Kind<IOKind.Witness, ?>, Kind<IOKind.Witness, ?>> EQ =
      (k1, k2) ->
          Objects.equals(
              IO_OP.narrow(cast(k1)).unsafeRunSync(), IO_OP.narrow(cast(k2)).unsafeRunSync());

  @SuppressWarnings("unchecked")
  private static <A> Kind<IOKind.Witness, A> cast(Kind<IOKind.Witness, ?> k) {
    return (Kind<IOKind.Witness, A>) k;
  }

  /** {@code IO(0)}, {@code IO(42)}, {@code IO(-1)}. */
  static Stream<Arguments> kinds() {
    return Stream.of(
        Arguments.of("IO(0)", IO_OP.widen(IO.delay(() -> 0))),
        Arguments.of("IO(42)", IO_OP.widen(IO.delay(() -> 42))),
        Arguments.of("IO(-1)", IO_OP.widen(IO.delay(() -> -1))));
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
