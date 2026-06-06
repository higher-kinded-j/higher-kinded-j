// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer_t;

import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.higherkindedj.hkt.writer_t.WriterTKindHelper.WRITER_T;

import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadWriter;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.typeclass.StringMonoid;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Shared law fixtures for the {@code WriterT} type-class tests (inner monad = {@code Id}, log = a
 * {@code String} monoid).
 *
 * <p>Referenced from the {@code *LawTests} blocks via a fully-qualified {@code @MethodSource}. The
 * inner {@code Id} is total, so there is no empty/absent state — the kinds are plain writers with
 * empty and non-empty logs.
 */
@SuppressWarnings("unused") // referenced reflectively via @MethodSource
final class WriterTLawFixtures {

  private WriterTLawFixtures() {}

  private static final Monad<IdKind.Witness> ID = Instances.monad(id());
  private static final MonadWriter<WriterTKind.Witness<IdKind.Witness, String>, String> WRITER =
      Instances.writerT(ID, new StringMonoid());

  private static Kind<WriterTKind.Witness<IdKind.Witness, String>, Integer> of(int value) {
    return WRITER.of(value);
  }

  @SuppressWarnings("SameParameterValue") // one logged fixture today; kept parameterised
  private static Kind<WriterTKind.Witness<IdKind.Witness, String>, Integer> logged(int value) {
    return WRITER_T.widen(WriterT.writer(ID, value, "log"));
  }

  /** Writers {@code of(0/42/-1)} (empty log) and one writer carrying a non-empty log. */
  static Stream<Arguments> kinds() {
    return Stream.of(
        Arguments.of("of(0)", of(0)),
        Arguments.of("of(42)", of(42)),
        Arguments.of("of(-1)", of(-1)),
        Arguments.of("logged(7)", logged(7)));
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
