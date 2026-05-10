// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.io.IO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Coverage contract for {@link IOAssert}. See {@link AssertContract}. */
@DisplayName("IOAssert contract")
class IOAssertContractTest extends AssertContract<Supplier<IO<Integer>>, IOAssert<Integer>> {

  // Each subject is a Supplier<IO<Integer>> so every test gets a fresh IO,
  // since IOAssert caches execution state internally.
  private static final Supplier<IO<Integer>> OK_42 = () -> IO.delay(() -> 42);
  private static final Supplier<IO<Integer>> OK_99 = () -> IO.delay(() -> 99);
  private static final Supplier<IO<Integer>> OK_NULL = () -> IO.delay(() -> null);
  private static final Supplier<IO<Integer>> FAIL_ISE =
      () ->
          IO.delay(
              () -> {
                throw new IllegalStateException("ise");
              });
  private static final Supplier<IO<Integer>> FAIL_IAE =
      () ->
          IO.delay(
              () -> {
                throw new IllegalArgumentException("iae");
              });
  private static final Supplier<IO<Integer>> FAIL_NULL_MSG =
      () ->
          IO.delay(
              () -> {
                throw new IllegalStateException();
              });

  @Override
  protected Function<Supplier<IO<Integer>>, IOAssert<Integer>> entry() {
    return s -> IOAssert.assertThatIO(s.get());
  }

  @Override
  protected Stream<Row<Supplier<IO<Integer>>, IOAssert<Integer>>> rows() {
    return Stream.of(
        row("hasValue match", OK_42, OK_99, a -> a.hasValue(42)),
        row("hasValue wrong (throws)", OK_42, FAIL_ISE, a -> a.hasValue(42)),
        passOnly("hasValueSatisfying passes", OK_42, a -> a.hasValueSatisfying(v -> {})),
        failOnly(
            "hasValueSatisfying inner fails",
            OK_42,
            a ->
                a.hasValueSatisfying(
                    v -> {
                      throw new AssertionError("inner");
                    })),
        failOnly("hasValueSatisfying when IO threw", FAIL_ISE, a -> a.hasValueSatisfying(v -> {})),
        row("hasValueNonNull", OK_42, OK_NULL, IOAssert::hasValueNonNull),
        failOnly("hasValueNonNull when IO threw", FAIL_ISE, IOAssert::hasValueNonNull),
        row("hasValueNull", OK_NULL, OK_42, IOAssert::hasValueNull),
        failOnly("hasValueNull when IO threw", FAIL_ISE, IOAssert::hasValueNull),
        row(
            "throwsException match",
            FAIL_ISE,
            OK_42,
            a -> a.throwsException(IllegalStateException.class)),
        row(
            "throwsException wrong type",
            FAIL_ISE,
            FAIL_IAE,
            a -> a.throwsException(IllegalStateException.class)),
        row("withMessage match", FAIL_ISE, FAIL_IAE, a -> a.whenExecuted().withMessage("ise")),
        failOnly("withMessage when no exception", OK_42, a -> a.whenExecuted().withMessage("x")),
        row(
            "withMessageContaining match",
            FAIL_ISE,
            FAIL_IAE,
            a -> a.whenExecuted().withMessageContaining("is")),
        failOnly(
            "withMessageContaining when no exception",
            OK_42,
            a -> a.whenExecuted().withMessageContaining("x")),
        failOnly(
            "withMessageContaining null message",
            FAIL_NULL_MSG,
            a -> a.whenExecuted().withMessageContaining("anything")),
        row("completesSuccessfully", OK_42, FAIL_ISE, IOAssert::completesSuccessfully),
        passOnly("isRepeatable for pure delay", OK_42, IOAssert::isRepeatable),
        passOnly("isNotExecutedYet before run", OK_42, IOAssert::isNotExecutedYet));
  }

  @Test
  void isNotExecutedYet_fails_after_whenExecuted() {
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> IOAssert.assertThatIO(OK_42.get()).whenExecuted().isNotExecutedYet());
  }

  @Test
  void isRepeatable_fails_for_nondeterministic_io() {
    AtomicInteger counter = new AtomicInteger();
    IO<Integer> nonDet = IO.delay(counter::incrementAndGet);
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> IOAssert.assertThatIO(nonDet).isRepeatable());
  }

  @Test
  void isRepeatable_fails_when_first_run_succeeds_and_second_throws() {
    AtomicInteger n = new AtomicInteger();
    IO<Integer> firstOkSecondFails =
        IO.delay(
            () -> {
              if (n.incrementAndGet() == 1) {
                return 42;
              }
              throw new IllegalStateException();
            });
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> IOAssert.assertThatIO(firstOkSecondFails).isRepeatable());
  }

  @Test
  void isRepeatable_fails_when_first_run_throws_and_second_succeeds() {
    AtomicInteger n = new AtomicInteger();
    IO<Integer> sometimesFails =
        IO.delay(
            () -> {
              if (n.incrementAndGet() == 1) {
                throw new IllegalStateException();
              }
              return 42;
            });
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> IOAssert.assertThatIO(sometimesFails).isRepeatable());
  }

  @Test
  void isRepeatable_passes_for_consistently_failing_io() {
    // IO whose `unsafeRunSync` always throws the same exception class with the same message
    // is considered repeatable (deterministic failure).
    IO<Integer> alwaysFailsSame =
        IO.delay(
            () -> {
              throw new IllegalStateException("same-msg");
            });
    IOAssert.assertThatIO(alwaysFailsSame).isRepeatable();
  }

  @Test
  void isRepeatable_fails_when_runs_throw_same_type_different_messages() {
    AtomicInteger n = new AtomicInteger();
    IO<Integer> alwaysFails =
        IO.delay(
            () -> {
              if (n.incrementAndGet() == 1) {
                throw new IllegalStateException("first-msg");
              }
              throw new IllegalStateException("second-msg");
            });
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> IOAssert.assertThatIO(alwaysFails).isRepeatable());
  }

  @Test
  void isRepeatable_fails_when_runs_throw_different_exception_types() {
    AtomicInteger n = new AtomicInteger();
    IO<Integer> alwaysFails =
        IO.delay(
            () -> {
              if (n.incrementAndGet() == 1) {
                throw new IllegalStateException("first");
              }
              throw new IllegalArgumentException("second");
            });
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> IOAssert.assertThatIO(alwaysFails).isRepeatable());
  }

  @Test
  void getValue_and_getException_after_run() {
    IOAssert<Integer> ok = IOAssert.assertThatIO(OK_42.get());
    Assertions.assertThat(ok.getValue()).isEqualTo(42);
    Assertions.assertThat(ok.getException()).isNull();

    IOAssert<Integer> bad = IOAssert.assertThatIO(FAIL_ISE.get());
    Assertions.assertThat(bad.getException()).isInstanceOf(IllegalStateException.class);
    Assertions.assertThat(bad.getValue()).isNull();
  }

  @Test
  void io_specific_checked_exception() {
    IO<Integer> failsChecked =
        IO.delay(
            () -> {
              try {
                throw new IOException("io-fail");
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            });
    IOAssert.assertThatIO(failsChecked)
        .throwsException(RuntimeException.class)
        .withMessageContaining("io-fail");
  }
}
