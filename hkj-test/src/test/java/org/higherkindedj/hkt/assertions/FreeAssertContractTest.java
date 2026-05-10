// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.util.function.Function;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.free_ap.FreeAp;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Coverage contract for {@link FreeAssert}. See {@link AssertContract}. */
@DisplayName("FreeAssert contract")
class FreeAssertContractTest
    extends AssertContract<
        Free<MaybeKind.Witness, Integer>, FreeAssert<MaybeKind.Witness, Integer>> {

  private static final Free<MaybeKind.Witness, Integer> PURE_42 = Free.pure(42);
  private static final Free<MaybeKind.Witness, Integer> PURE_99 = Free.pure(99);
  private static final Free<MaybeKind.Witness, Integer> SUSPEND =
      Free.<MaybeKind.Witness, Integer>pure(0).flatMap(x -> Free.pure(x + 1));
  private static final Free<MaybeKind.Witness, Integer> FLAT_MAPPED =
      Free.<MaybeKind.Witness, Integer>pure(1).flatMap(i -> Free.pure(i + 1));
  private static final Free<MaybeKind.Witness, Integer> HANDLE_ERROR =
      Free.<MaybeKind.Witness, Integer>pure(1)
          .handleError(RuntimeException.class, t -> Free.pure(0));
  private static final Free<MaybeKind.Witness, Integer> AP = new Free.Ap<>(FreeAp.pure(42));

  @Override
  protected Function<Free<MaybeKind.Witness, Integer>, FreeAssert<MaybeKind.Witness, Integer>>
      entry() {
    return FreeAssert::assertThatFree;
  }

  @Override
  protected Stream<Row<Free<MaybeKind.Witness, Integer>, FreeAssert<MaybeKind.Witness, Integer>>>
      rows() {
    return Stream.of(
        row("isPure", PURE_42, FLAT_MAPPED, FreeAssert::isPure),
        row("hasPureValue match", PURE_42, PURE_99, a -> a.hasPureValue(42)),
        row("hasPureValue wrong state", PURE_42, FLAT_MAPPED, a -> a.hasPureValue(42)),
        row("isFlatMapped", FLAT_MAPPED, PURE_42, FreeAssert::isFlatMapped),
        row("isHandleError", HANDLE_ERROR, PURE_42, FreeAssert::isHandleError),
        row("isAp", AP, PURE_42, FreeAssert::isAp),
        // Suspend creation requires Kind<F, Free<F, A>>; building such an instance for
        // arbitrary F is awkward in a generic contract. We simulate it via passOnly with a
        // synthetic Suspend created directly as a record.
        passOnly(
            "isSuspend",
            new Free.Suspend<MaybeKind.Witness, Integer>(
                MaybeKindHelper.MAYBE.widen(
                    Maybe.<Free<MaybeKind.Witness, Integer>>just(Free.pure(7)))),
            FreeAssert::isSuspend),
        failOnly("isSuspend wrong state", PURE_42, FreeAssert::isSuspend));
  }

  @Test
  void interpretedWith_runs_program_and_returns_kind() {
    Natural<MaybeKind.Witness, MaybeKind.Witness> identity =
        new Natural<>() {
          @Override
          public <A> Kind<MaybeKind.Witness, A> apply(Kind<MaybeKind.Witness, A> fa) {
            return fa;
          }
        };
    Kind<MaybeKind.Witness, Integer> result =
        FreeAssert.assertThatFree(PURE_42).interpretedWith(identity, MaybeMonad.INSTANCE);
    Assertions.assertThat(MaybeKindHelper.MAYBE.narrow(result).get()).isEqualTo(42);
  }
}
