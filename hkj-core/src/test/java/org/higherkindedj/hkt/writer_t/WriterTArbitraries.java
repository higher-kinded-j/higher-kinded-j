// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer_t;

import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.higherkindedj.hkt.writer_t.WriterTKindHelper.WRITER_T;

import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.instances.Instances;

/**
 * Shared jqwik arbitraries for the WriterT property tests (inner monad = {@code Id}, String log).
 *
 * <p>The per-test {@code @Provide} methods delegate here so the {@code WriterT} generator and the
 * function pools are defined once rather than copy-pasted into {@code WriterTMonadPropertyTest}.
 */
final class WriterTArbitraries {

  private WriterTArbitraries() {}

  private static final Monad<IdKind.Witness> ID = Instances.monad(id());

  /**
   * {@code WriterT<Id, String, Integer>} kinds: alpha logs (max length 5) over ints in [-100,100].
   */
  @SuppressWarnings("DataFlowIssue") // jqwik samples are non-null despite the nullable typing
  static Arbitrary<Kind<WriterTKind.Witness<IdKind.Witness, String>, Integer>> writerTKinds() {
    Arbitrary<Integer> values = Arbitraries.integers().between(-100, 100);
    Arbitrary<String> logs = Arbitraries.strings().alpha().ofMaxLength(5);
    return Combinators.combine(logs, values)
        .as((log, v) -> WRITER_T.widen(WriterT.writer(ID, v, log)));
  }

  /** A small pool of total {@code Integer -> String} functions. */
  static Arbitrary<Function<Integer, String>> intToString() {
    return Arbitraries.of(i -> "v:" + i, i -> String.valueOf(i * 2), Object::toString);
  }

  /** A small pool of total {@code String -> Integer} functions. */
  static Arbitrary<Function<String, Integer>> stringToInt() {
    return Arbitraries.of(String::length, s -> s.isEmpty() ? 0 : 1, String::hashCode);
  }

  /** A small pool of {@code Integer -> WriterT<Id, String, String>} kleisli arrows. */
  static Arbitrary<Function<Integer, Kind<WriterTKind.Witness<IdKind.Witness, String>, String>>>
      intToTKindString() {
    return Arbitraries.of(
        i -> WRITER_T.widen(WriterT.writer(ID, "v:" + i, "f1;")),
        i -> WRITER_T.widen(WriterT.writer(ID, String.valueOf(i * 2), "f2;")),
        i -> WRITER_T.widen(WriterT.writer(ID, Integer.toBinaryString(i), "")));
  }

  /** A small pool of {@code String -> WriterT<Id, String, String>} kleisli arrows. */
  static Arbitrary<Function<String, Kind<WriterTKind.Witness<IdKind.Witness, String>, String>>>
      stringToTKindString() {
    return Arbitraries.of(
        s -> WRITER_T.widen(WriterT.writer(ID, s + "!", "g1;")),
        s -> WRITER_T.widen(WriterT.writer(ID, s.toUpperCase(), "g2;")),
        s -> WRITER_T.widen(WriterT.writer(ID, "x:" + s, "")));
  }
}
