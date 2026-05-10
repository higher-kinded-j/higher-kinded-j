// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.reader.Reader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Coverage contract for {@link ReaderAssert}. See {@link AssertContract}. */
@DisplayName("ReaderAssert contract")
class ReaderAssertContractTest
    extends AssertContract<Reader<String, String>, ReaderAssert<String, String>> {

  // Pure: returns env directly
  private static final Reader<String, String> ECHO = Reader.of(env -> env);
  // Constant 'X' regardless of env
  private static final Reader<String, String> CONST_X = Reader.of(env -> "X");
  // Constant 'Y' (different from CONST_X)
  private static final Reader<String, String> CONST_Y = Reader.of(env -> "Y");
  // Returns null
  private static final Reader<String, String> NULL_R = Reader.of(env -> null);
  // Stateful (impure) — returns different value on second call
  private static final AtomicInteger N = new AtomicInteger();

  private static final Reader<String, String> IMPURE_FRESH() {
    AtomicInteger counter = new AtomicInteger();
    return Reader.of(env -> "v" + counter.incrementAndGet());
  }

  @Override
  protected Function<Reader<String, String>, ReaderAssert<String, String>> entry() {
    return ReaderAssert::assertThatReader;
  }

  @Override
  protected Stream<Row<Reader<String, String>, ReaderAssert<String, String>>> rows() {
    return Stream.of(
        row(
            "whenRunWith.produces match",
            ECHO,
            CONST_X,
            a -> a.whenRunWith("hello").produces("hello")),
        row("whenRunWith.producesNull", NULL_R, ECHO, a -> a.whenRunWith("hello").producesNull()),
        row(
            "whenRunWith.producesNonNull",
            ECHO,
            NULL_R,
            a -> a.whenRunWith("hello").producesNonNull()),
        passOnly(
            "whenRunWith.satisfies passes", ECHO, a -> a.whenRunWith("hello").satisfies(r -> {})),
        failOnly(
            "whenRunWith.satisfies inner fails",
            ECHO,
            a ->
                a.whenRunWith("hello")
                    .satisfies(
                        r -> {
                          throw new AssertionError("inner");
                        })),
        row(
            "whenRunWith.matches predicate",
            ECHO,
            CONST_X,
            a -> a.whenRunWith("hello").matches("hello"::equals)),
        row(
            "whenRunWith.matches predicate with description",
            ECHO,
            CONST_X,
            a -> a.whenRunWith("hello").matches("hello"::equals, "result should be hello")),
        passOnly("isPureWhenRunWith on stable reader", ECHO, a -> a.isPureWhenRunWith("hello")),
        row("isConstantFor", CONST_X, ECHO, a -> a.isConstantFor("a", "b")));
  }

  // Override to inject impure reader for the failing isPureWhenRunWith case at runtime
  // (static fields would share the AtomicInteger across tests).
  @Test
  void isPureWhenRunWith_fails_for_impure_reader() {
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> ReaderAssert.assertThatReader(IMPURE_FRESH()).isPureWhenRunWith("env"));
  }

  @Test
  void getResult_returns_underlying_value() {
    ReaderAssert.ReaderResultAssert<String, String> r =
        ReaderAssert.assertThatReader(ECHO).whenRunWith("hello");
    Assertions.assertThat(r.getResult()).isEqualTo("hello");
  }
}
