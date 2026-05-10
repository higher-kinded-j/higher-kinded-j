// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.writer.Writer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Coverage contract for {@link WriterAssert}. See {@link AssertContract}. */
@DisplayName("WriterAssert contract")
class WriterAssertContractTest
    extends AssertContract<Writer<String, Integer>, WriterAssert<String, Integer>> {

  private static final Writer<String, Integer> W_LOG_42 = new Writer<>("log", 42);
  private static final Writer<String, Integer> W_LOG_99 = new Writer<>("log", 99);
  private static final Writer<String, Integer> W_OTHER_42 = new Writer<>("other", 42);
  private static final Writer<String, Integer> W_EMPTY_LOG = new Writer<>("", 42);
  private static final Writer<String, Integer> W_NULL_VAL = new Writer<>("log", null);

  @Override
  protected Function<Writer<String, Integer>, WriterAssert<String, Integer>> entry() {
    return WriterAssert::assertThatWriter;
  }

  @Override
  protected Stream<Row<Writer<String, Integer>, WriterAssert<String, Integer>>> rows() {
    return Stream.of(
        row("hasLog match", W_LOG_42, W_OTHER_42, a -> a.hasLog("log")),
        row("hasValue match", W_LOG_42, W_LOG_99, a -> a.hasValue(42)),
        row("hasNullValue", W_NULL_VAL, W_LOG_42, WriterAssert::hasNullValue),
        row("hasNonNullValue", W_LOG_42, W_NULL_VAL, WriterAssert::hasNonNullValue),
        row("hasEmptyLog", W_EMPTY_LOG, W_LOG_42, WriterAssert::hasEmptyLog),
        passOnly("satisfiesLog passes", W_LOG_42, a -> a.satisfiesLog(l -> {})),
        failOnly(
            "satisfiesLog inner fails",
            W_LOG_42,
            a ->
                a.satisfiesLog(
                    l -> {
                      throw new AssertionError("inner");
                    })),
        passOnly("satisfiesValue passes", W_LOG_42, a -> a.satisfiesValue(v -> {})),
        failOnly(
            "satisfiesValue inner fails",
            W_LOG_42,
            a ->
                a.satisfiesValue(
                    v -> {
                      throw new AssertionError("inner");
                    })),
        row("logMatches predicate", W_LOG_42, W_OTHER_42, a -> a.logMatches("log"::equals)),
        row(
            "logMatches predicate with description",
            W_LOG_42,
            W_OTHER_42,
            a -> a.logMatches("log"::equals, "log should be 'log'")),
        row("valueMatches predicate", W_LOG_42, W_LOG_99, a -> a.valueMatches(v -> v == 42)),
        row(
            "valueMatches predicate with description",
            W_LOG_42,
            W_LOG_99,
            a -> a.valueMatches(v -> v == 42, "value should be 42")),
        passOnly("isPure on stable values", W_LOG_42, WriterAssert::isPure),
        row("isEqualTo match", W_LOG_42, W_LOG_99, a -> a.isEqualTo(W_LOG_42)));
  }

  @Test
  void getters_return_underlying_log_and_value() {
    WriterAssert<String, Integer> a = WriterAssert.assertThatWriter(W_LOG_42);
    Assertions.assertThat(a.getLog()).isEqualTo("log");
    Assertions.assertThat(a.getValue()).isEqualTo(42);
  }

  @Test
  void hasEmptyLog_works_for_collection_log() {
    Writer<List<String>, Integer> emptyCollLog = new Writer<>(List.of(), 42);
    Writer<List<String>, Integer> nonEmptyCollLog = new Writer<>(List.of("entry"), 42);
    WriterAssert.assertThatWriter(emptyCollLog).hasEmptyLog();
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> WriterAssert.assertThatWriter(nonEmptyCollLog).hasEmptyLog());
  }

  @Test
  void hasEmptyLog_throws_for_unsupported_log_type() {
    Writer<Integer, String> intLog = new Writer<>(0, "v");
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> WriterAssert.assertThatWriter(intLog).hasEmptyLog());
  }
}
