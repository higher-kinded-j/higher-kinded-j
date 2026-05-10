// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.io.IOException;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.trymonad.Try;
import org.junit.jupiter.api.DisplayName;

/** Coverage contract for {@link TryAssert}. See {@link AssertContract}. */
@DisplayName("TryAssert contract")
class TryAssertContractTest extends AssertContract<Try<Integer>, TryAssert<Integer>> {

  private static final Try<Integer> SUCCESS_42 = Try.success(42);
  private static final Try<Integer> SUCCESS_99 = Try.success(99);
  private static final IllegalStateException ISE = new IllegalStateException("isefail");
  private static final IOException IOE = new IOException("iofail");
  private static final Try<Integer> FAILURE_ISE = Try.failure(ISE);
  private static final Try<Integer> FAILURE_IOE = Try.failure(IOE);

  @Override
  protected Function<Try<Integer>, TryAssert<Integer>> entry() {
    return TryAssert::assertThatTry;
  }

  @Override
  protected Stream<Row<Try<Integer>, TryAssert<Integer>>> rows() {
    return Stream.of(
        row("isSuccess", SUCCESS_42, FAILURE_ISE, TryAssert::isSuccess),
        row("isFailure", FAILURE_ISE, SUCCESS_42, TryAssert::isFailure),
        row("hasValue match", SUCCESS_42, SUCCESS_99, a -> a.hasValue(42)),
        row("hasValue wrong state", SUCCESS_42, FAILURE_ISE, a -> a.hasValue(42)),
        row(
            "hasValueSatisfying wrong state",
            SUCCESS_42,
            FAILURE_ISE,
            a -> a.hasValueSatisfying(v -> {})),
        failOnly(
            "hasValueSatisfying inner fails",
            SUCCESS_42,
            a ->
                a.hasValueSatisfying(
                    v -> {
                      throw new AssertionError("inner");
                    })),
        row("hasValueNonNull", SUCCESS_42, FAILURE_ISE, TryAssert::hasValueNonNull),
        row("hasException match", FAILURE_ISE, FAILURE_IOE, a -> a.hasException(ISE)),
        row("hasException wrong state", FAILURE_ISE, SUCCESS_42, a -> a.hasException(ISE)),
        row(
            "hasExceptionOfType match",
            FAILURE_IOE,
            FAILURE_ISE,
            a -> a.hasExceptionOfType(IOException.class)),
        row(
            "hasExceptionOfType wrong state",
            FAILURE_IOE,
            SUCCESS_42,
            a -> a.hasExceptionOfType(IOException.class)),
        row(
            "hasExceptionSatisfying wrong state",
            FAILURE_ISE,
            SUCCESS_42,
            a -> a.hasExceptionSatisfying(t -> {})),
        failOnly(
            "hasExceptionSatisfying inner fails",
            FAILURE_ISE,
            a ->
                a.hasExceptionSatisfying(
                    t -> {
                      throw new AssertionError("inner");
                    })));
  }
}
