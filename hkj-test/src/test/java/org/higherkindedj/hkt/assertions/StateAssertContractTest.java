// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.state.StateTuple;
import org.junit.jupiter.api.DisplayName;

/** Coverage contract for {@link StateAssert}. See {@link AssertContract}. */
@DisplayName("StateAssert contract")
class StateAssertContractTest
    extends AssertContract<StateTuple<Integer, String>, StateAssert<Integer, String>> {

  private static final StateTuple<Integer, String> T_OK = new StateTuple<>("ok", 42);
  private static final StateTuple<Integer, String> T_OTHER = new StateTuple<>("other", 99);
  private static final StateTuple<Integer, String> T_NULL_VALUE = new StateTuple<>(null, 42);

  @Override
  protected Function<StateTuple<Integer, String>, StateAssert<Integer, String>> entry() {
    return StateAssert::assertThatStateTuple;
  }

  @Override
  protected Stream<Row<StateTuple<Integer, String>, StateAssert<Integer, String>>> rows() {
    return Stream.of(
        row("hasValue match", T_OK, T_OTHER, a -> a.hasValue("ok")),
        row("hasState match", T_OK, T_OTHER, a -> a.hasState(42)),
        passOnly("hasValueSatisfying passes", T_OK, a -> a.hasValueSatisfying(v -> {})),
        failOnly(
            "hasValueSatisfying inner fails",
            T_OK,
            a ->
                a.hasValueSatisfying(
                    v -> {
                      throw new AssertionError("inner");
                    })),
        passOnly("hasStateSatisfying passes", T_OK, a -> a.hasStateSatisfying(s -> {})),
        failOnly(
            "hasStateSatisfying inner fails",
            T_OK,
            a ->
                a.hasStateSatisfying(
                    s -> {
                      throw new AssertionError("inner");
                    })),
        row("hasValueNonNull", T_OK, T_NULL_VALUE, StateAssert::hasValueNonNull),
        passOnly("hasStateNonNull", T_OK, StateAssert::hasStateNonNull),
        row("hasNullValue", T_NULL_VALUE, T_OK, StateAssert::hasNullValue));
  }
}
