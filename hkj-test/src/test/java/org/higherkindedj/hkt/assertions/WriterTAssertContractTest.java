// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.assertions.WriterTAssert.WriterTOptionalAssert;
import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.writer_t.WriterTKindHelper.WRITER_T;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Pair;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.writer_t.WriterT;
import org.higherkindedj.hkt.writer_t.WriterTKind;
import org.junit.jupiter.api.DisplayName;

/** Coverage contract for {@link WriterTAssert}. See {@link AssertContract}. */
@DisplayName("WriterTAssert contract")
class WriterTAssertContractTest
    extends AssertContract<
        Kind<WriterTKind.Witness<OptionalKind.Witness, String>, Integer>,
        WriterTOptionalAssert<OptionalKind.Witness, String, Integer>> {

  private static final MonadError<OptionalKind.Witness, Unit> MONAD =
      Instances.monadError(optional());

  private static <T> Optional<T> unwrap(Kind<OptionalKind.Witness, T> k) {
    return OPTIONAL.narrow(k);
  }

  // (value=42, output="")
  private static Kind<WriterTKind.Witness<OptionalKind.Witness, String>, Integer> mkOk() {
    return WRITER_T.widen(WriterT.of(MONAD, Monoids.string(), 42));
  }

  // (value=99, output="")
  private static Kind<WriterTKind.Witness<OptionalKind.Witness, String>, Integer> mkOther() {
    return WRITER_T.widen(WriterT.of(MONAD, Monoids.string(), 99));
  }

  // Output "log" with value 42
  private static Kind<WriterTKind.Witness<OptionalKind.Witness, String>, Integer> mkOkWithLog() {
    return WRITER_T.widen(WriterT.fromKind(OPTIONAL.widen(Optional.of(Pair.of(42, "log")))));
  }

  // Always returns empty Optional
  private static Kind<WriterTKind.Witness<OptionalKind.Witness, String>, Integer> mkEmpty() {
    return WRITER_T.widen(
        WriterT.fromKind(OPTIONAL.widen(Optional.<Pair<Integer, String>>empty())));
  }

  private static final Kind<WriterTKind.Witness<OptionalKind.Witness, String>, Integer> OK = mkOk();
  private static final Kind<WriterTKind.Witness<OptionalKind.Witness, String>, Integer> OTHER =
      mkOther();
  private static final Kind<WriterTKind.Witness<OptionalKind.Witness, String>, Integer> OK_LOG =
      mkOkWithLog();
  private static final Kind<WriterTKind.Witness<OptionalKind.Witness, String>, Integer> EMPTY =
      mkEmpty();

  @Override
  protected Function<
          Kind<WriterTKind.Witness<OptionalKind.Witness, String>, Integer>,
          WriterTOptionalAssert<OptionalKind.Witness, String, Integer>>
      entry() {
    return k -> WriterTAssert.assertThatWriterT(k, WriterTAssertContractTest::unwrap);
  }

  @Override
  protected Stream<
          Row<
              Kind<WriterTKind.Witness<OptionalKind.Witness, String>, Integer>,
              WriterTOptionalAssert<OptionalKind.Witness, String, Integer>>>
      rows() {
    return Stream.of(
        passOnly(
            "private constructor accessible via reflection",
            OK,
            a -> {
              try {
                var c = WriterTAssert.class.getDeclaredConstructor();
                c.setAccessible(true);
                c.newInstance();
              } catch (Exception e) {
                throw new AssertionError(e);
              }
            }),
        row("isEmpty", EMPTY, OK, WriterTOptionalAssert::isEmpty),
        row("isPresent", OK, EMPTY, WriterTOptionalAssert::isPresent),
        row("hasValue match", OK, OTHER, a -> a.hasValue(42)),
        row("hasValue wrong outer state", OK, EMPTY, a -> a.hasValue(42)),
        row("hasOutput match", OK_LOG, OK, a -> a.hasOutput("log")),
        row("hasOutput wrong outer state", OK_LOG, EMPTY, a -> a.hasOutput("log")),
        row("hasPair match", OK_LOG, OK, a -> a.hasPair(42, "log")),
        row("hasPair wrong value", OK_LOG, OTHER, a -> a.hasPair(42, "log")),
        row("hasPair wrong outer state", OK_LOG, EMPTY, a -> a.hasPair(42, "log")),
        passOnly("satisfiesValue passes", OK, a -> a.satisfiesValue(v -> {})),
        failOnly(
            "satisfiesValue inner fails",
            OK,
            a ->
                a.satisfiesValue(
                    v -> {
                      throw new AssertionError("inner");
                    })),
        passOnly("satisfiesOutput passes", OK, a -> a.satisfiesOutput(o -> {})),
        failOnly(
            "satisfiesOutput inner fails",
            OK,
            a ->
                a.satisfiesOutput(
                    o -> {
                      throw new AssertionError("inner");
                    })),
        row("outputMatches predicate", OK_LOG, OK, a -> a.outputMatches("log"::equals)),
        row("isEqualToWriterT match", OK, OTHER, a -> a.isEqualToWriterT(OK)),
        failOnly("isEqualToWriterT null other", OK, a -> a.isEqualToWriterT(null)));
  }
}
