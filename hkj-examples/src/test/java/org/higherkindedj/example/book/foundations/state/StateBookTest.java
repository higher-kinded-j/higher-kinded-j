// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.example.book.foundations.state.StateBook.BULBS;
import static org.higherkindedj.example.book.foundations.state.StateBook.LAMP;
import static org.higherkindedj.example.book.foundations.state.StateBook.SOFA;
import static org.higherkindedj.example.book.foundations.state.StateBook.add;
import static org.higherkindedj.example.book.foundations.state.StateBook.addAll;
import static org.higherkindedj.example.book.foundations.state.StateBook.checkout;
import static org.higherkindedj.hkt.instances.Witnesses.state;
import static org.higherkindedj.hkt.state.StateKindHelper.STATE;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.higherkindedj.example.book.foundations.Basket;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.WithStatePath;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.state.State;
import org.higherkindedj.hkt.state.StateKind;
import org.higherkindedj.hkt.state.StateTuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Holds the claims the State page makes about a basket each step is handed and hands on. */
@DisplayName("State: steps that are each handed a basket and hand on the next")
class StateBookTest {

  @Nested
  @DisplayName("adding to a basket")
  class Adding {

    @Test
    @DisplayName("the lamp is added, the sofa refused, and the basket keeps only the lamp")
    void lampThenSofa() {
      StateTuple<Basket, Boolean> afterLamp = add(LAMP).run(Basket.EMPTY);
      assertThat(afterLamp.value()).isTrue();
      assertThat(afterLamp.state().lines()).containsExactly(LAMP);

      StateTuple<Basket, Boolean> after = add(LAMP).flatMap(_ -> add(SOFA)).run(Basket.EMPTY);
      assertThat(after.value()).isFalse();
      assertThat(after.state().lines()).containsExactly(LAMP);
      assertThat(after.state().total()).isEqualByComparingTo("40.00");
    }

    @Test
    @DisplayName("a refused line leaves the basket it was handed")
    void refusalKeepsTheBasket() {
      Basket withLamp = new Basket(List.of(LAMP));

      StateTuple<Basket, Boolean> after = add(SOFA).run(withLamp);

      assertThat(after.value()).isFalse();
      assertThat(after.state()).isSameAs(withLamp);
    }

    @Test
    @DisplayName("a basket that reaches the limit exactly is within it")
    void theLimitItselfIsAllowed() {
      Basket at60 = new Basket(List.of(LAMP, BULBS, BULBS));

      assertThat(add(LAMP).run(at60).value()).isTrue();
      assertThat(add(LAMP).run(at60).state().total()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("the same steps run from a basket of bulbs come to 50.00")
    void theSameStepsFromAnotherBasket() {
      State<Basket, Boolean> lampThenSofa = add(LAMP).flatMap(_ -> add(SOFA));

      assertThat(lampThenSofa.run(new Basket(List.of(BULBS))).state().total())
          .isEqualByComparingTo("50.00");
      assertThat(lampThenSofa.run(Basket.EMPTY).state().total()).isEqualByComparingTo("40.00");
    }

    @Test
    @DisplayName("a State holds no basket: building it runs nothing, and each run starts again")
    void nothingRunsUntilRun() {
      List<Basket> handed = new ArrayList<>();
      State<Basket, Unit> spy =
          State.modify(
              basket -> {
                handed.add(basket);
                return basket;
              });
      State<Basket, Boolean> steps = spy.flatMap(_ -> add(LAMP));

      assertThat(handed).isEmpty();
      steps.run(Basket.EMPTY);
      steps.run(Basket.EMPTY);
      assertThat(handed).containsExactly(Basket.EMPTY, Basket.EMPTY);
    }
  }

  @Nested
  @DisplayName("a step for each line")
  class AddAll {

    @Test
    @DisplayName("the trip refuses the sofa and comes to 50.00")
    void theTrip() {
      StateTuple<Basket, List<String>> trip = addAll(List.of(LAMP, SOFA, BULBS)).run(Basket.EMPTY);

      assertThat(trip.value()).containsExactly("SOFA");
      assertThat(trip.state().lines()).containsExactly(LAMP, BULBS);
      assertThat(trip.state().total()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("an empty list adds nothing and refuses nothing")
    void noLines() {
      StateTuple<Basket, List<String>> trip = addAll(List.of()).run(Basket.EMPTY);

      assertThat(trip.value()).isEmpty();
      assertThat(trip.state()).isEqualTo(Basket.EMPTY);
    }
  }

  @Nested
  @DisplayName("on a WithStatePath")
  class OnAPath {

    @Test
    @DisplayName("checkout reads the total from the basket the trip left")
    void checkoutReadsTheTripsBasket() {
      assertThat(checkout(List.of(LAMP, SOFA, BULBS)).evalState(Basket.EMPTY))
          .isEqualTo("pay 50.00, refused [SOFA]");
      assertThat(checkout(List.of(LAMP)).evalState(Basket.EMPTY)).isEqualTo("pay 40.00");
    }

    @Test
    @DisplayName("execState gives the basket, and run both")
    void theRunners() {
      WithStatePath<Basket, String> path = checkout(List.of(LAMP, SOFA));

      assertThat(path.execState(Basket.EMPTY).lines()).containsExactly(LAMP);
      assertThat(path.run(Basket.EMPTY).value()).isEqualTo("pay 40.00, refused [SOFA]");
      assertThat(path.run(Basket.EMPTY).state().lines()).containsExactly(LAMP);
      assertThat(path.toState().run(Basket.EMPTY).state().lines()).containsExactly(LAMP);
    }

    @Test
    @DisplayName("Path.getState, setState and modifyState read and replace the basket")
    void theFactories() {
      WithStatePath<Basket, BigDecimal> steps =
          Path.<Basket>setState(new Basket(List.of(LAMP)))
              .then(() -> Path.<Basket>modifyState(basket -> basket.add(BULBS)))
              .then(Path::<Basket>getState)
              .map(Basket::total);

      assertThat(steps.evalState(Basket.EMPTY)).isEqualByComparingTo("50.00");
    }
  }

  @Nested
  @DisplayName("long chains")
  class LongChains {

    private static final int STEPS = 100_000;

    @Test
    @DisplayName("a chain of 100,000 map, flatMap and zipWith steps runs without overflowing")
    void aLongChainIsStackSafe() {
      State<Integer, Integer> counted = State.pure(0);
      WithStatePath<Integer, Integer> zipped = Path.statePure(0);
      WithStatePath<Integer, Unit> tick = Path.modifyState(n -> n + 1);
      for (int i = 0; i < STEPS; i++) {
        counted =
            i % 2 == 0
                ? counted.map(n -> n + 1)
                : counted.flatMap(n -> State.<Integer>modify(s -> s + 1).map(_ -> n + 1));
        zipped = zipped.zipWith(tick, (n, _) -> n + 1);
      }

      assertThat(counted.run(0).value()).isEqualTo(STEPS);
      assertThat(counted.run(0).state()).isEqualTo(STEPS / 2);
      assertThat(zipped.run(0).value()).isEqualTo(STEPS);
      assertThat(zipped.run(0).state()).isEqualTo(STEPS);
    }
  }

  @Nested
  @DisplayName("creating instances")
  class Creating {

    @Test
    @DisplayName("new StateTuple takes the result first, and StateTuple.of the state first")
    void tupleArgumentOrder() {
      assertThat(new StateTuple<>(true, Basket.EMPTY)).isEqualTo(StateTuple.of(Basket.EMPTY, true));
    }

    @Test
    @DisplayName("get, inspect, set and modify read, replace and change the basket")
    void theFactories() {
      Basket withLamp = new Basket(List.of(LAMP));

      assertThat(State.<Basket>get().run(withLamp).value()).isSameAs(withLamp);
      assertThat(State.inspect(Basket::total).run(withLamp).value()).isEqualByComparingTo("40.00");
      assertThat(State.set(withLamp).run(Basket.EMPTY).state()).isSameAs(withLamp);
      assertThat(State.set(withLamp).run(Basket.EMPTY).value()).isEqualTo(Unit.INSTANCE);
      assertThat(State.<Basket>modify(b -> b.add(BULBS)).run(withLamp).state().total())
          .isEqualByComparingTo("50.00");
      assertThat(State.<Basket, String>pure("kept").run(withLamp).state()).isSameAs(withLamp);
    }
  }

  @Nested
  @DisplayName("a State always gives a value")
  class AlwaysAValue {

    @Test
    @DisplayName("pure, set and a StateTuple refuse null at the call")
    void refusedAtTheCall() {
      assertThatThrownBy(() -> State.pure(null)).isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> State.set(null)).isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> new StateTuple<>(null, Basket.EMPTY))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> new StateTuple<>(true, null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("a function given to map, inspect or modify that returns null fails on run")
    void refusedOnRun() {
      State<Basket, String> mapped = add(LAMP).map(_ -> null);
      State<Basket, String> inspected = State.inspect(_ -> null);
      State<Basket, Unit> modified = State.modify(_ -> null);

      assertThatThrownBy(() -> mapped.run(Basket.EMPTY))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("f must not return null");
      assertThatThrownBy(() -> inspected.run(Basket.EMPTY))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("f must not return null");
      assertThatThrownBy(() -> modified.run(Basket.EMPTY))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("StateTuple.construction state cannot be null");
    }
  }

  @Nested
  @DisplayName("working with Kind")
  class WorkingWithKind {

    @Test
    @DisplayName("the monad maps the widened step, and evalState runs it")
    void theMonad() {
      Monad<StateKind.Witness<Basket>> baskets = Instances.monad(state());
      Kind<StateKind.Witness<Basket>, Boolean> lamp = STATE.widen(add(LAMP));

      Kind<StateKind.Witness<Basket>, String> label =
          baskets.map(added -> added ? "added" : "refused", lamp);

      assertThat(STATE.evalState(label, Basket.EMPTY)).isEqualTo("added");
    }

    @Test
    @DisplayName("widening allocates nothing: narrow gives back the same State")
    void widenIsTheSameObject() {
      State<Basket, Boolean> lamp = add(LAMP);

      assertThat(STATE.widen(lamp)).isSameAs(lamp);
      assertThat(STATE.narrow(STATE.widen(lamp))).isSameAs(lamp);
    }

    @Test
    @DisplayName("runState gives both, execState the basket")
    void theRunners() {
      Kind<StateKind.Witness<Basket>, Boolean> lamp = STATE.widen(add(LAMP));

      assertThat(STATE.runState(lamp, Basket.EMPTY).value()).isTrue();
      assertThat(STATE.execState(lamp, Basket.EMPTY).lines()).containsExactly(LAMP);
    }
  }
}
