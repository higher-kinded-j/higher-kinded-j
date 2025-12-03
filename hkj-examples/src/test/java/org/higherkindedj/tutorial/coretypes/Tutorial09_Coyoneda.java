// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.coretypes;

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
 * Tutorial 09: Coyoneda - The Free Functor
 *
 * <p>Coyoneda provides two key benefits:
 *
 * <ol>
 *   <li>Automatic Functor instances for any type constructor
 *   <li>Map fusion: multiple map operations become one function composition
 * </ol>
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>lift: Wrap a value in Coyoneda
 *   <li>map: Compose functions without needing a Functor
 *   <li>lower: Apply the composed function using a Functor
 * </ul>
 *
 * <p>Links to documentation: <a
 * href="https://higher-kinded-j.github.io/latest/monads/coyoneda.html">Coyoneda Guide</a>
 */
public class Tutorial09_Coyoneda {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  /**
   * Exercise 1: Lifting into Coyoneda
   *
   * <p>Coyoneda.lift wraps a value, storing it with an identity transformation.
   *
   * <p>Task: Lift a Maybe into Coyoneda
   */
  @Test
  void exercise1_lift() {
    Kind<MaybeKind.Witness, Integer> maybeValue = MAYBE.widen(Maybe.just(42));

    // TODO: Lift maybeValue into Coyoneda
    // Hint: Use Coyoneda.lift(maybeValue)
    Coyoneda<MaybeKind.Witness, Integer> coyo = answerRequired();

    // Verify by lowering back
    MaybeFunctor functor = MaybeFunctor.INSTANCE;
    Kind<MaybeKind.Witness, Integer> lowered = coyo.lower(functor);

    assertThat(MAYBE.narrow(lowered)).isEqualTo(Maybe.just(42));
  }

  /**
   * Exercise 2: Mapping without a Functor
   *
   * <p>Coyoneda allows mapping without needing a Functor instance. The Functor is only needed when
   * lowering.
   *
   * <p>Task: Map over a Coyoneda
   */
  @Test
  void exercise2_mapWithoutFunctor() {
    Kind<MaybeKind.Witness, String> maybeString = MAYBE.widen(Maybe.just("hello"));
    Coyoneda<MaybeKind.Witness, String> coyo = Coyoneda.lift(maybeString);

    // TODO: Map to uppercase. No Functor needed!
    // Hint: Use coyo.map(String::toUpperCase)
    Coyoneda<MaybeKind.Witness, String> upper = answerRequired();

    // Lower to get result
    MaybeFunctor functor = MaybeFunctor.INSTANCE;
    Kind<MaybeKind.Witness, String> result = upper.lower(functor);

    assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.just("HELLO"));
  }

  /**
   * Exercise 3: Map fusion
   *
   * <p>Multiple map operations are fused into one function composition. The list is only traversed
   * once during lower().
   *
   * <p>Task: Chain multiple maps
   */
  @Test
  void exercise3_mapFusion() {
    Kind<ListKind.Witness, Integer> listValue = LIST.widen(List.of(1, 2, 3, 4, 5));
    Coyoneda<ListKind.Witness, Integer> coyo = Coyoneda.lift(listValue);

    // TODO: Chain three maps: multiply by 2, add 10, convert to string
    // Hint: coyo.map(x -> x * 2).map(x -> x + 10).map(Object::toString)
    Coyoneda<ListKind.Witness, String> chained = answerRequired();

    // Lower to execute
    ListMonad listFunctor = ListMonad.INSTANCE;
    Kind<ListKind.Witness, String> result = chained.lower(listFunctor);

    // Results: (1*2)+10=12, (2*2)+10=14, (3*2)+10=16, (4*2)+10=18, (5*2)+10=20
    assertThat(LIST.narrow(result)).containsExactly("12", "14", "16", "18", "20");
  }

  /**
   * Exercise 4: Coyoneda with Nothing
   *
   * <p>Coyoneda works with empty containers too. Maps still compose, but produce nothing when
   * lowered.
   *
   * <p>Task: Work with Maybe.nothing()
   */
  @Test
  void exercise4_withNothing() {
    Kind<MaybeKind.Witness, Integer> nothing = MAYBE.widen(Maybe.nothing());

    // TODO: Lift nothing into Coyoneda and map over it
    // Hint: Coyoneda.lift(nothing).map(x -> x * 2)
    Coyoneda<MaybeKind.Witness, Integer> coyo = answerRequired();

    // Lower
    MaybeFunctor functor = MaybeFunctor.INSTANCE;
    Kind<MaybeKind.Witness, Integer> result = coyo.lower(functor);

    // Should still be Nothing
    assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.nothing());
  }

  /**
   * Exercise 5: Complex transformation pipeline
   *
   * <p>Build a transformation pipeline that processes strings.
   *
   * <p>Task: Create a pipeline that trims, uppercases, and wraps strings
   */
  @Test
  void exercise5_pipeline() {
    Kind<ListKind.Witness, String> names = LIST.widen(List.of("  alice  ", " bob ", "charlie"));

    // TODO: Build a pipeline that:
    // 1. Trims whitespace (String::trim)
    // 2. Converts to uppercase (String::toUpperCase)
    // 3. Wraps in brackets (s -> "[" + s + "]")
    // Hint: Coyoneda.lift(names).map(...).map(...).map(...)
    Coyoneda<ListKind.Witness, String> pipeline = answerRequired();

    // Execute
    ListMonad listFunctor = ListMonad.INSTANCE;
    Kind<ListKind.Witness, String> result = pipeline.lower(listFunctor);

    assertThat(LIST.narrow(result)).containsExactly("[ALICE]", "[BOB]", "[CHARLIE]");
  }

  /**
   * Congratulations! You've completed Tutorial 09: Coyoneda
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>Coyoneda provides automatic Functor instances
   *   <li>You can map without a Functor (only needed at lower)
   *   <li>Multiple maps are fused into one function
   *   <li>The underlying structure is only traversed once
   *   <li>Coyoneda is useful for Free monads and optimisation
   * </ul>
   *
   * <p>Next: Tutorial 10 - Free Applicative
   */
}
