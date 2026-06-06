// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader;

import static org.higherkindedj.hkt.reader.ReaderKindHelper.READER;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.reader.ReaderTestBase.TestConfig;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Shared law fixtures for the Reader type-class tests (environment = {@link TestConfig}).
 *
 * <p>Referenced from the per-type-class {@code Laws} blocks via a fully-qualified
 * {@code @MethodSource}, e.g.
 * {@code @MethodSource("org.higherkindedj.hkt.reader.ReaderLawFixtures#kinds")}. Centralising the
 * fixture streams keeps every algebra's law verification driven by the same inputs and removes the
 * copy-pasted {@code fixtures()}/{@code values()}/{@code strings()} providers that previously lived
 * in each test. Readers are functions, so the law equality checker runs both sides against a fixed
 * environment.
 */
@SuppressWarnings("unused") // referenced reflectively via @MethodSource
final class ReaderLawFixtures {

  private ReaderLawFixtures() {}

  /** Fixed environment the law equality checker runs both sides against. */
  static final TestConfig ENV = new TestConfig("jdbc:test", 10);

  /**
   * Reader equality: run both sides against the fixed {@link #ENV} and compare. Shared by the unit
   * law blocks and the property tests.
   */
  static final BiPredicate<
          Kind<ReaderKind.Witness<TestConfig>, ?>, Kind<ReaderKind.Witness<TestConfig>, ?>>
      EQ = (k1, k2) -> Objects.equals(READER.runReader(k1, ENV), READER.runReader(k2, ENV));

  /** {@code reader(url length)}, {@code pure(42)}, {@code reader(maxConnections)}. */
  static Stream<Arguments> kinds() {
    return Stream.of(
        Arguments.of("reader(url length)", READER.reader((TestConfig cfg) -> cfg.url().length())),
        Arguments.of("pure(42)", READER.reader((TestConfig cfg) -> 42)),
        Arguments.of("reader(maxConnections)", READER.reader(TestConfig::maxConnections)));
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
