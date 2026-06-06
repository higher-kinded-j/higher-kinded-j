// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader_t;

import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.reader_t.ReaderTKindHelper.READER_T;

import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Shared law fixtures for the {@code ReaderT} type-class tests (inner monad = {@code Optional},
 * environment = {@code String}).
 *
 * <p>Referenced from the {@code *LawTests} blocks via a fully-qualified {@code @MethodSource}.
 * Readers are functions, so the kinds span constant readers, an env-dependent reader, and an empty
 * outer {@code Optional} — each law is verified over all of them. Equality runs both sides against
 * a fixed environment.
 */
@SuppressWarnings("unused") // referenced reflectively via @MethodSource
final class ReaderTLawFixtures {

  private ReaderTLawFixtures() {}

  private static final Monad<OptionalKind.Witness> OUTER = Instances.monadError(optional());

  private static final String ENV = "test-env";

  private static <A> Optional<A> unwrapKind(
      Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, A> kind) {
    var readerT = READER_T.narrow(kind);
    Kind<OptionalKind.Witness, A> outerKind = readerT.run().apply(ENV);
    return OPTIONAL.narrow(outerKind);
  }

  /**
   * Shared law equality: run/unwrap to {@code Optional<·>} against a fixed environment and compare.
   */
  static final BiPredicate<
          Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, ?>,
          Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, ?>>
      EQ = KindEquivalence.byEqualsAfter(ReaderTLawFixtures::unwrapKind);

  private static Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> readerT(
      int value) {
    return READER_T.widen(ReaderT.reader(OUTER, _ -> value));
  }

  private static Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> fromEnvT() {
    return READER_T.widen(ReaderT.reader(OUTER, String::length));
  }

  private static Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> emptyT() {
    Kind<OptionalKind.Witness, Integer> emptyOuter = OPTIONAL.widen(Optional.empty());
    return READER_T.widen(ReaderT.liftF(OUTER, emptyOuter));
  }

  /**
   * constant readers {@code 0/42/-1}, an env-dependent reader, and an empty outer {@code Optional}.
   */
  static Stream<Arguments> kinds() {
    return Stream.of(
        Arguments.of("readerT(0)", readerT(0)),
        Arguments.of("readerT(42)", readerT(42)),
        Arguments.of("readerT(-1)", readerT(-1)),
        Arguments.of("fromEnvT", fromEnvT()),
        Arguments.of("emptyT", emptyT()));
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
