// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.coretypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.coyoneda.Coyoneda;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListMonad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeFunctor;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 09: Coyoneda - The Free Functor - SOLUTIONS
 *
 * <p>This file contains the solutions for all exercises in Tutorial 09.
 */
public class Tutorial09_Coyoneda_Solution {

  @Test
  void exercise1_lift() {
    Kind<MaybeKind.Witness, Integer> maybeValue = MAYBE.widen(Maybe.just(42));

    // SOLUTION: Use Coyoneda.lift
    Coyoneda<MaybeKind.Witness, Integer> coyo = Coyoneda.lift(maybeValue);

    // Verify by lowering back
    MaybeFunctor functor = MaybeFunctor.INSTANCE;
    Kind<MaybeKind.Witness, Integer> lowered = coyo.lower(functor);

    assertThat(MAYBE.narrow(lowered)).isEqualTo(Maybe.just(42));
  }

  @Test
  void exercise2_mapWithoutFunctor() {
    Kind<MaybeKind.Witness, String> maybeString = MAYBE.widen(Maybe.just("hello"));
    Coyoneda<MaybeKind.Witness, String> coyo = Coyoneda.lift(maybeString);

    // SOLUTION: Map to uppercase
    Coyoneda<MaybeKind.Witness, String> upper = coyo.map(String::toUpperCase);

    // Lower to get result
    MaybeFunctor functor = MaybeFunctor.INSTANCE;
    Kind<MaybeKind.Witness, String> result = upper.lower(functor);

    assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.just("HELLO"));
  }

  @Test
  void exercise3_mapFusion() {
    Kind<ListKind.Witness, Integer> listValue = LIST.widen(List.of(1, 2, 3, 4, 5));
    Coyoneda<ListKind.Witness, Integer> coyo = Coyoneda.lift(listValue);

    // SOLUTION: Chain three maps
    Coyoneda<ListKind.Witness, String> chained =
        coyo.map(x -> x * 2).map(x -> x + 10).map(Object::toString);

    // Lower to execute
    ListMonad listFunctor = ListMonad.INSTANCE;
    Kind<ListKind.Witness, String> result = chained.lower(listFunctor);

    assertThat(LIST.narrow(result)).containsExactly("12", "14", "16", "18", "20");
  }

  @Test
  void exercise4_withNothing() {
    Kind<MaybeKind.Witness, Integer> nothing = MAYBE.widen(Maybe.nothing());

    // SOLUTION: Lift and map over nothing
    Coyoneda<MaybeKind.Witness, Integer> coyo = Coyoneda.lift(nothing).map(x -> x * 2);

    // Lower
    MaybeFunctor functor = MaybeFunctor.INSTANCE;
    Kind<MaybeKind.Witness, Integer> result = coyo.lower(functor);

    // Should still be Nothing
    assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.nothing());
  }

  @Test
  void exercise5_pipeline() {
    Kind<ListKind.Witness, String> names = LIST.widen(List.of("  alice  ", " bob ", "charlie"));

    // SOLUTION: Build the full pipeline
    Coyoneda<ListKind.Witness, String> pipeline =
        Coyoneda.lift(names).map(String::trim).map(String::toUpperCase).map(s -> "[" + s + "]");

    // Execute
    ListMonad listFunctor = ListMonad.INSTANCE;
    Kind<ListKind.Witness, String> result = pipeline.lower(listFunctor);

    assertThat(LIST.narrow(result)).containsExactly("[ALICE]", "[BOB]", "[CHARLIE]");
  }
}
