// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.assertions.ReaderTAssert.ReaderTOptionalAssert;
import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.reader_t.ReaderTKindHelper.READER_T;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.reader_t.ReaderT;
import org.higherkindedj.hkt.reader_t.ReaderTKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Coverage contract for {@link ReaderTAssert}. See {@link AssertContract}. */
@DisplayName("ReaderTAssert contract")
class ReaderTAssertContractTest
    extends AssertContract<
        Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer>,
        ReaderTOptionalAssert<OptionalKind.Witness, String, Integer>> {

  private static final MonadError<OptionalKind.Witness, Unit> MONAD =
      Instances.monadError(optional());

  private static <T> Optional<T> unwrap(Kind<OptionalKind.Witness, T> k) {
    return OPTIONAL.narrow(k);
  }

  // Returns env.length() lifted into Optional
  private static Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> echoLength() {
    return READER_T.widen(ReaderT.reader(MONAD, String::length));
  }

  // Returns 42 regardless of env
  private static Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> const42() {
    return READER_T.widen(ReaderT.reader(MONAD, env -> 42));
  }

  // Always returns empty Optional
  private static Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> alwaysEmpty() {
    return READER_T.widen(ReaderT.of(env -> OPTIONAL.widen(Optional.<Integer>empty())));
  }

  private static final Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> ECHO =
      echoLength();
  private static final Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> CONST_42 =
      const42();
  private static final Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> EMPTY =
      alwaysEmpty();

  @Override
  protected Function<
          Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer>,
          ReaderTOptionalAssert<OptionalKind.Witness, String, Integer>>
      entry() {
    return k -> ReaderTAssert.assertThatReaderT(k, ReaderTAssertContractTest::unwrap);
  }

  @Override
  protected Stream<
          Row<
              Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer>,
              ReaderTOptionalAssert<OptionalKind.Witness, String, Integer>>>
      rows() {
    return Stream.of(
        passOnly(
            "private constructor accessible via reflection",
            ECHO,
            a -> {
              try {
                var c = ReaderTAssert.class.getDeclaredConstructor();
                c.setAccessible(true);
                c.newInstance();
              } catch (Exception e) {
                throw new AssertionError(e);
              }
            }),
        // whenRunWith.isEmpty: ECHO("hello") = present(5); EMPTY("hello") = empty
        row("whenRunWith.isEmpty", EMPTY, ECHO, a -> a.whenRunWith("hello").isEmpty()),
        row("whenRunWith.isPresent", ECHO, EMPTY, a -> a.whenRunWith("hello").isPresent()),
        row("whenRunWith.hasValue match", ECHO, CONST_42, a -> a.whenRunWith("hello").hasValue(5)),
        row(
            "whenRunWith.hasValue wrong outer state",
            ECHO,
            EMPTY,
            a -> a.whenRunWith("hello").hasValue(5)),
        passOnly(
            "whenRunWith.satisfiesValue passes",
            ECHO,
            a -> a.whenRunWith("hello").satisfiesValue(v -> {})),
        failOnly(
            "whenRunWith.satisfiesValue inner fails",
            ECHO,
            a ->
                a.whenRunWith("hello")
                    .satisfiesValue(
                        v -> {
                          throw new AssertionError("inner");
                        })),
        row(
            "whenRunWith.valueMatches predicate",
            ECHO,
            CONST_42,
            a -> a.whenRunWith("hello").valueMatches(v -> v == 5)),
        row("isEqualToReaderT match", ECHO, CONST_42, a -> a.isEqualToReaderT(ECHO, "hello")),
        failOnly("isEqualToReaderT null other", ECHO, a -> a.isEqualToReaderT(null, "hello")));
  }

  @Test
  void valueMatches_fails_when_outer_empty() {
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(
            () ->
                ReaderTAssert.assertThatReaderT(EMPTY, ReaderTAssertContractTest::unwrap)
                    .whenRunWith("hi")
                    .valueMatches(v -> true));
  }

  @Test
  void satisfiesValue_fails_when_outer_empty() {
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(
            () ->
                ReaderTAssert.assertThatReaderT(EMPTY, ReaderTAssertContractTest::unwrap)
                    .whenRunWith("hi")
                    .satisfiesValue(v -> {}));
  }
}
