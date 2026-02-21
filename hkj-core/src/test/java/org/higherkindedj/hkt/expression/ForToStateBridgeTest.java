// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.optics.Lens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@code toState()} bridge method that transitions from {@link For} comprehensions to
 * {@link ForState} builders.
 */
@DisplayName("For → ForState Bridge (toState) Tests")
class ForToStateBridgeTest {

  // --- Test records ---

  record Dashboard(String user, int count, boolean ready) {}

  record Summary(String label, int total) {}

  // --- Lenses ---

  static final Lens<Dashboard, Boolean> readyLens =
      Lens.of(Dashboard::ready, (d, v) -> new Dashboard(d.user(), d.count(), v));

  static final Lens<Dashboard, Integer> countLens =
      Lens.of(Dashboard::count, (d, v) -> new Dashboard(d.user(), v, d.ready()));

  static final Lens<Summary, String> labelLens =
      Lens.of(Summary::label, (s, v) -> new Summary(v, s.total()));

  // --- Monad instances ---

  private final IdMonad idMonad = IdMonad.instance();
  private final MaybeMonad maybeMonad = MaybeMonad.INSTANCE;

  // =========================================================================
  // MonadicSteps1.toState()
  // =========================================================================

  @Nested
  @DisplayName("MonadicSteps1.toState()")
  class MonadicSteps1ToState {

    @Test
    @DisplayName("should construct state from single value and apply lens operations")
    void toState_fromStep1() {
      Kind<IdKind.Witness, Dashboard> result =
          For.from(idMonad, Id.of("Alice"))
              .toState(name -> new Dashboard(name, 0, false))
              .update(readyLens, true)
              .modify(countLens, c -> c + 1)
              .yield();

      Dashboard dashboard = IdKindHelper.ID.unwrap(result);
      assertThat(dashboard.user()).isEqualTo("Alice");
      assertThat(dashboard.count()).isEqualTo(1);
      assertThat(dashboard.ready()).isTrue();
    }

    @Test
    @DisplayName("should support yield with projection after toState")
    void toState_yieldProjection() {
      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of(42))
              .toState(n -> new Summary("Total", n))
              .modify(labelLens, l -> l + "!")
              .yield(s -> s.label() + "=" + s.total());

      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("Total!=42");
    }

    @Test
    @DisplayName("should reject null constructor")
    void toState_nullConstructor() {
      assertThatNullPointerException()
          .isThrownBy(() -> For.from(idMonad, Id.of(1)).toState(null))
          .withMessageContaining("constructor must not be null");
    }
  }

  // =========================================================================
  // MonadicSteps2.toState() (generated)
  // =========================================================================

  @Nested
  @DisplayName("MonadicSteps2.toState()")
  class MonadicSteps2ToState {

    @Test
    @DisplayName("should construct state from two accumulated values (spread-style)")
    void toState_spread() {
      Kind<IdKind.Witness, Dashboard> result =
          For.from(idMonad, Id.of("Bob"))
              .from(name -> Id.of(5))
              .toState((name, count) -> new Dashboard(name, count, false))
              .update(readyLens, true)
              .yield();

      Dashboard dashboard = IdKindHelper.ID.unwrap(result);
      assertThat(dashboard.user()).isEqualTo("Bob");
      assertThat(dashboard.count()).isEqualTo(5);
      assertThat(dashboard.ready()).isTrue();
    }

    @Test
    @DisplayName("should construct state from two accumulated values (tuple-style)")
    void toState_tuple() {
      Kind<IdKind.Witness, Dashboard> result =
          For.from(idMonad, Id.of("Carol"))
              .from(name -> Id.of(10))
              .toState(t -> new Dashboard(t._1(), t._2(), false))
              .update(readyLens, true)
              .yield();

      Dashboard dashboard = IdKindHelper.ID.unwrap(result);
      assertThat(dashboard.user()).isEqualTo("Carol");
      assertThat(dashboard.count()).isEqualTo(10);
      assertThat(dashboard.ready()).isTrue();
    }

    @Test
    @DisplayName("should reject null constructor (spread)")
    void toState_nullSpread() {
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  For.from(idMonad, Id.of("x"))
                      .from(x -> Id.of(1))
                      .toState((java.util.function.BiFunction<String, Integer, Dashboard>) null))
          .withMessageContaining("constructor must not be null");
    }
  }

  // =========================================================================
  // MonadicSteps3.toState() (generated, arity 3)
  // =========================================================================

  @Nested
  @DisplayName("MonadicSteps3.toState()")
  class MonadicSteps3ToState {

    @Test
    @DisplayName("should construct state from three accumulated values (spread-style)")
    void toState_spread() {
      Kind<IdKind.Witness, Dashboard> result =
          For.from(idMonad, Id.of("Dave"))
              .from(name -> Id.of(3))
              .let(t -> t._1().length() > 3)
              .toState((name, count, ready) -> new Dashboard(name, count, ready))
              .yield();

      Dashboard dashboard = IdKindHelper.ID.unwrap(result);
      assertThat(dashboard.user()).isEqualTo("Dave");
      assertThat(dashboard.count()).isEqualTo(3);
      assertThat(dashboard.ready()).isTrue();
    }

    @Test
    @DisplayName("should construct state from three accumulated values (tuple-style)")
    void toState_tuple() {
      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of("Eve"))
              .from(name -> Id.of(7))
              .let(t -> t._2() * 2)
              .toState(t -> new Summary(t._1(), t._2() + t._3()))
              .yield(s -> s.label() + ":" + s.total());

      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("Eve:21");
    }
  }

  // =========================================================================
  // FilterableSteps1.toState() (with MonadZero)
  // =========================================================================

  @Nested
  @DisplayName("FilterableSteps1.toState()")
  class FilterableSteps1ToState {

    @Test
    @DisplayName("should return FilterableSteps from MonadZero and preserve filtering")
    void toState_preservesFiltering() {
      Kind<MaybeKind.Witness, Dashboard> result =
          For.from(maybeMonad, MAYBE.just("Alice"))
              .toState(name -> new Dashboard(name, 0, false))
              .when(d -> d.user().length() > 3)
              .update(readyLens, true)
              .yield();

      Maybe<Dashboard> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get().ready()).isTrue();
    }

    @Test
    @DisplayName("should short-circuit with Nothing when filter fails")
    void toState_shortCircuitsOnFilterFailure() {
      Kind<MaybeKind.Witness, Dashboard> result =
          For.from(maybeMonad, MAYBE.just("Al"))
              .toState(name -> new Dashboard(name, 0, false))
              .when(d -> d.user().length() > 3)
              .update(readyLens, true)
              .yield();

      Maybe<Dashboard> maybe = MAYBE.narrow(result);
      assertThat(maybe.isNothing()).isTrue();
    }

    @Test
    @DisplayName("should reject null constructor")
    void toState_nullConstructor() {
      assertThatNullPointerException()
          .isThrownBy(() -> For.from(maybeMonad, MAYBE.just(1)).toState(null))
          .withMessageContaining("constructor must not be null");
    }
  }

  // =========================================================================
  // FilterableSteps2.toState() (generated, with MonadZero)
  // =========================================================================

  @Nested
  @DisplayName("FilterableSteps2.toState()")
  class FilterableSteps2ToState {

    @Test
    @DisplayName("should construct filterable state from two values (spread-style)")
    void toState_spread() {
      Kind<MaybeKind.Witness, Dashboard> result =
          For.from(maybeMonad, MAYBE.just("Grace"))
              .from(name -> MAYBE.just(8))
              .toState((name, count) -> new Dashboard(name, count, false))
              .when(d -> d.count() > 5)
              .update(readyLens, true)
              .yield();

      Maybe<Dashboard> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get().user()).isEqualTo("Grace");
      assertThat(maybe.get().ready()).isTrue();
    }

    @Test
    @DisplayName("should construct filterable state from two values (tuple-style)")
    void toState_tuple() {
      Kind<MaybeKind.Witness, Dashboard> result =
          For.from(maybeMonad, MAYBE.just("Hank"))
              .from(name -> MAYBE.just(2))
              .toState(t -> new Dashboard(t._1(), t._2(), false))
              .when(d -> d.count() > 5)
              .yield();

      Maybe<Dashboard> maybe = MAYBE.narrow(result);
      assertThat(maybe.isNothing()).isTrue();
    }
  }

  // =========================================================================
  // FilterableSteps with when() before toState()
  // =========================================================================

  @Nested
  @DisplayName("Filter then toState()")
  class FilterThenToState {

    @Test
    @DisplayName("should filter before transitioning to ForState")
    void when_thenToState() {
      Kind<MaybeKind.Witness, Dashboard> result =
          For.from(maybeMonad, MAYBE.just("Iris"))
              .when(name -> name.startsWith("I"))
              .from(name -> MAYBE.just(name.length()))
              .toState((name, len) -> new Dashboard(name, len, false))
              .update(readyLens, true)
              .yield();

      Maybe<Dashboard> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo(new Dashboard("Iris", 4, true));
    }

    @Test
    @DisplayName("should short-circuit when filter fails before toState")
    void when_failsThenToState() {
      Kind<MaybeKind.Witness, Dashboard> result =
          For.from(maybeMonad, MAYBE.just("Jack"))
              .when(name -> name.startsWith("I"))
              .from(name -> MAYBE.just(name.length()))
              .toState((name, len) -> new Dashboard(name, len, false))
              .update(readyLens, true)
              .yield();

      Maybe<Dashboard> maybe = MAYBE.narrow(result);
      assertThat(maybe.isNothing()).isTrue();
    }
  }

  // =========================================================================
  // End-to-end: For → ForState → yield with lens operations
  // =========================================================================

  @Nested
  @DisplayName("End-to-end bridge workflows")
  class EndToEnd {

    @Test
    @DisplayName("should chain For accumulation into ForState lens pipeline")
    void fullPipeline() {
      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of("data"))
              .from(d -> Id.of(d.length()))
              .let(t -> t._1() + ":" + t._2())
              .toState((data, len, label) -> new Dashboard(label, len, false))
              .modify(countLens, c -> c * 10)
              .update(readyLens, true)
              .yield(d -> d.user() + "|" + d.count() + "|" + d.ready());

      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("data:4|40|true");
    }

    @Test
    @DisplayName("should handle Nothing propagation through entire pipeline")
    void nothingPropagation() {
      Kind<MaybeKind.Witness, Dashboard> result =
          For.from(maybeMonad, MAYBE.<String>nothing())
              .from(name -> MAYBE.just(name.length()))
              .toState((name, len) -> new Dashboard(name, len, false))
              .update(readyLens, true)
              .yield();

      assertThat(MAYBE.narrow(result).isNothing()).isTrue();
    }
  }
}
