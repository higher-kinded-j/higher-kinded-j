// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state_op;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.state.StateKindHelper.STATE;

import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.state.StateKind;
import org.higherkindedj.hkt.state.StateMonad;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.optics.Lens;

/**
 * Property-based tests for StateOp using jQwik.
 *
 * <p>These tests verify the fundamental optic laws hold when StateOp is interpreted through the
 * State monad:
 *
 * <ul>
 *   <li>GetPut: view then assign is identity
 *   <li>PutGet: assign then view returns the assigned value
 *   <li>PutPut: last assign wins
 *   <li>Over with identity is identity
 * </ul>
 */
class StateOpPropertyTest {

  record Point(int x, int y) {}

  private static final Lens<Point, Integer> X_LENS =
      Lens.of(Point::x, (point, x) -> new Point(x, point.y()));

  private static final Lens<Point, Integer> Y_LENS =
      Lens.of(Point::y, (point, y) -> new Point(point.x(), y));

  private final StateOpInterpreter<Point> interpreter = new StateOpInterpreter<>();
  private final StateMonad<Point> monad = new StateMonad<>();

  @Provide
  Arbitrary<Point> points() {
    return Arbitraries.integers()
        .between(-1000, 1000)
        .flatMap(x -> Arbitraries.integers().between(-1000, 1000).map(y -> new Point(x, y)));
  }

  // ===== GetPut Law: assign(view(s), s) == s =====

  @Property
  @Label("GetPut: view then assign returns original state")
  void getPutLaw(@ForAll("points") Point point) {
    // Read x, then assign it back — state should be unchanged
    Free<StateOpKind.Witness<Point>, Integer> program =
        StateOps.<Point, Integer>view(X_LENS).flatMap(x -> StateOps.assign(X_LENS, x));

    Kind<StateKind.Witness<Point>, Integer> result = program.foldMap(interpreter, monad);
    StateTuple<Point, Integer> tuple = STATE.runState(result, point);

    assertThat(tuple.state()).isEqualTo(point);
  }

  // ===== PutGet Law: assign(a, s) then view == a =====

  @Property
  @Label("PutGet: assign then view returns the assigned value")
  void putGetLaw(@ForAll("points") Point point, @ForAll int newX) {
    Free<StateOpKind.Witness<Point>, Integer> program =
        StateOps.<Point, Integer>assign(X_LENS, newX)
            .flatMap(_ -> StateOps.<Point, Integer>view(X_LENS));

    Kind<StateKind.Witness<Point>, Integer> result = program.foldMap(interpreter, monad);
    StateTuple<Point, Integer> tuple = STATE.runState(result, point);

    assertThat(tuple.value()).isEqualTo(newX);
  }

  // ===== PutPut Law: assign(a2) after assign(a1) == assign(a2) =====

  @Property
  @Label("PutPut: last assign wins")
  void putPutLaw(@ForAll("points") Point point, @ForAll int first, @ForAll int second) {
    // Assign first, then assign second, then view
    Free<StateOpKind.Witness<Point>, Integer> twoAssigns =
        StateOps.<Point, Integer>assign(X_LENS, first)
            .flatMap(_ -> StateOps.<Point, Integer>assign(X_LENS, second))
            .flatMap(_ -> StateOps.<Point, Integer>view(X_LENS));

    // Just assign second, then view
    Free<StateOpKind.Witness<Point>, Integer> oneAssign =
        StateOps.<Point, Integer>assign(X_LENS, second)
            .flatMap(_ -> StateOps.<Point, Integer>view(X_LENS));

    Kind<StateKind.Witness<Point>, Integer> result1 = twoAssigns.foldMap(interpreter, monad);
    Kind<StateKind.Witness<Point>, Integer> result2 = oneAssign.foldMap(interpreter, monad);

    assertThat(STATE.runState(result1, point).value())
        .isEqualTo(STATE.runState(result2, point).value());
  }

  // ===== Over with identity is identity =====

  @Property
  @Label("Over with identity function does not change state")
  void overIdentity(@ForAll("points") Point point) {
    Free<StateOpKind.Witness<Point>, Integer> program = StateOps.over(X_LENS, Function.identity());

    Kind<StateKind.Witness<Point>, Integer> result = program.foldMap(interpreter, monad);
    StateTuple<Point, Integer> tuple = STATE.runState(result, point);

    assertThat(tuple.state()).isEqualTo(point);
    assertThat(tuple.value()).isEqualTo(point.x());
  }

  // ===== Independent lenses do not interfere =====

  @Property
  @Label("Modifying x does not affect y")
  void independentLenses(@ForAll("points") Point point, @ForAll int newX) {
    Free<StateOpKind.Witness<Point>, Integer> program =
        StateOps.<Point, Integer>assign(X_LENS, newX)
            .flatMap(_ -> StateOps.<Point, Integer>view(Y_LENS));

    Kind<StateKind.Witness<Point>, Integer> result = program.foldMap(interpreter, monad);
    StateTuple<Point, Integer> tuple = STATE.runState(result, point);

    assertThat(tuple.value()).isEqualTo(point.y()); // y unchanged
    assertThat(tuple.state().x()).isEqualTo(newX);
  }
}
