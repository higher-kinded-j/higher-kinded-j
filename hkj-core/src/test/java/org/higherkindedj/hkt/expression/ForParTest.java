// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("For Comprehension par() Tests")
class ForParTest {

  // ===== Static par() entry points =====

  @Nested
  @DisplayName("Static par() with Identity Monad (Non-Filterable)")
  class StaticParIdTests {
    private final IdMonad idMonad = IdMonad.instance();

    @Test
    @DisplayName("par(2): should combine two independent computations")
    void par2_combineTwo() {
      Kind<IdKind.Witness, String> result =
          For.par(idMonad, Id.of(1), Id.of("hello")).yield((a, b) -> a + ":" + b);
      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("1:hello");
    }

    @Test
    @DisplayName("par(3): should combine three independent computations")
    void par3_combineThree() {
      Kind<IdKind.Witness, String> result =
          For.par(idMonad, Id.of(1), Id.of("hello"), Id.of(3.14))
              .yield((a, b, c) -> a + ":" + b + ":" + c);
      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("1:hello:3.14");
    }

    @Test
    @DisplayName("par(4): should combine four independent computations")
    void par4_combineFour() {
      Kind<IdKind.Witness, String> result =
          For.par(idMonad, Id.of(1), Id.of("b"), Id.of(3.0), Id.of(true))
              .yield((a, b, c, d) -> "" + a + b + c + d);
      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("1b3.0true");
    }

    @Test
    @DisplayName("par(5): should combine five independent computations")
    void par5_combineFive() {
      Kind<IdKind.Witness, String> result =
          For.par(idMonad, Id.of(1), Id.of("b"), Id.of(3.0), Id.of(true), Id.of('e'))
              .yield((a, b, c, d, e) -> "" + a + b + c + d + e);
      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("1b3.0truee");
    }

    @Test
    @DisplayName("par(2) then from: should allow sequential chaining after parallel")
    void par2_thenFrom() {
      Kind<IdKind.Witness, String> result =
          For.par(idMonad, Id.of(1), Id.of(2))
              .from(t -> Id.of(t._1() + t._2()))
              .yield((a, b, c) -> a + "+" + b + "=" + c);
      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("1+2=3");
    }

    @Test
    @DisplayName("par(2) then let: should allow let after parallel")
    void par2_thenLet() {
      Kind<IdKind.Witness, String> result =
          For.par(idMonad, Id.of(10), Id.of(20))
              .let(t -> t._1() + t._2())
              .yield((a, b, c) -> a + "+" + b + "=" + c);
      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("10+20=30");
    }
  }

  @Nested
  @DisplayName("Static par() with Maybe Monad (Filterable)")
  class StaticParMaybeTests {
    private final MaybeMonad maybeMonad = MaybeMonad.INSTANCE;

    @Test
    @DisplayName("par(2): should combine two Just values")
    void par2_bothJust() {
      Kind<MaybeKind.Witness, String> result =
          For.par(maybeMonad, MAYBE.widen(Maybe.just(1)), MAYBE.widen(Maybe.just("x")))
              .yield((a, b) -> a + b);
      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.just("1x"));
    }

    @Test
    @DisplayName("par(2): should short-circuit on Nothing")
    void par2_oneNothing() {
      Kind<MaybeKind.Witness, String> result =
          For.par(maybeMonad, MAYBE.widen(Maybe.just(1)), MAYBE.widen(Maybe.<String>nothing()))
              .yield((a, b) -> a + b);
      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("par(3): should combine three Just values")
    void par3_allJust() {
      Kind<MaybeKind.Witness, Integer> result =
          For.par(
                  maybeMonad,
                  MAYBE.widen(Maybe.just(1)),
                  MAYBE.widen(Maybe.just(2)),
                  MAYBE.widen(Maybe.just(3)))
              .yield((a, b, c) -> a + b + c);
      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.just(6));
    }

    @Test
    @DisplayName("par(2) then when: should support filtering after parallel")
    void par2_thenWhen() {
      Kind<MaybeKind.Witness, Integer> result =
          For.par(maybeMonad, MAYBE.widen(Maybe.just(3)), MAYBE.widen(Maybe.just(4)))
              .when(t -> t._1() + t._2() > 10)
              .yield((a, b) -> a + b);
      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("par(4): should combine four Just values")
    void par4_allJust() {
      Kind<MaybeKind.Witness, Integer> result =
          For.par(
                  maybeMonad,
                  MAYBE.widen(Maybe.just(1)),
                  MAYBE.widen(Maybe.just(2)),
                  MAYBE.widen(Maybe.just(3)),
                  MAYBE.widen(Maybe.just(4)))
              .yield((a, b, c, d) -> a + b + c + d);
      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.just(10));
    }

    @Test
    @DisplayName("par(5): should combine five Just values")
    void par5_allJust() {
      Kind<MaybeKind.Witness, Integer> result =
          For.par(
                  maybeMonad,
                  MAYBE.widen(Maybe.just(1)),
                  MAYBE.widen(Maybe.just(2)),
                  MAYBE.widen(Maybe.just(3)),
                  MAYBE.widen(Maybe.just(4)),
                  MAYBE.widen(Maybe.just(5)))
              .yield((a, b, c, d, e) -> a + b + c + d + e);
      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.just(15));
    }
  }

  // ===== Instance par() on MonadicSteps1 =====

  @Nested
  @DisplayName("Instance par() on MonadicSteps1")
  class InstanceParMonadicSteps1Tests {
    private final IdMonad idMonad = IdMonad.instance();

    @Test
    @DisplayName("par(2 fns): should combine two dependent computations in parallel")
    void par2_fromSteps1() {
      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of(10))
              .par(a -> Id.of(a * 2), a -> Id.of(a + "!"))
              .yield((a, b, c) -> a + ":" + b + ":" + c);
      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("10:20:10!");
    }

    @Test
    @DisplayName("par(3 fns): should combine three dependent computations in parallel")
    void par3_fromSteps1() {
      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of(5))
              .par(a -> Id.of(a + 1), a -> Id.of(a + 2), a -> Id.of(a + 3))
              .yield((a, b, c, d) -> a + ":" + b + ":" + c + ":" + d);
      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("5:6:7:8");
    }

    @Test
    @DisplayName("par(4 fns): should combine four dependent computations in parallel")
    void par4_fromSteps1() {
      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of(1))
              .par(
                  a -> Id.of(a * 10),
                  a -> Id.of(a * 100),
                  a -> Id.of(a * 1000),
                  a -> Id.of(a * 10000))
              .yield((a, b, c, d, e) -> "" + a + ":" + b + ":" + c + ":" + d + ":" + e);
      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("1:10:100:1000:10000");
    }
  }

  // ===== Instance par() on FilterableSteps1 =====

  @Nested
  @DisplayName("Instance par() on FilterableSteps1")
  class InstanceParFilterableSteps1Tests {
    private final MaybeMonad maybeMonad = MaybeMonad.INSTANCE;

    @Test
    @DisplayName("par(2 fns): should combine two dependent computations")
    void par2_fromFilterableSteps1() {
      Kind<MaybeKind.Witness, String> result =
          For.from(maybeMonad, MAYBE.widen(Maybe.just(10)))
              .par(a -> MAYBE.widen(Maybe.just(a * 2)), a -> MAYBE.widen(Maybe.just(a + "!")))
              .yield((a, b, c) -> a + ":" + b + ":" + c);
      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.just("10:20:10!"));
    }

    @Test
    @DisplayName("par(2 fns): should short-circuit on Nothing")
    void par2_shortCircuit() {
      Kind<MaybeKind.Witness, String> result =
          For.from(maybeMonad, MAYBE.widen(Maybe.just(10)))
              .par(
                  a -> MAYBE.widen(Maybe.<Integer>nothing()), a -> MAYBE.widen(Maybe.just(a + "!")))
              .yield((a, b, c) -> a + ":" + b + ":" + c);
      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("par(3 fns): should combine three dependent computations")
    void par3_fromFilterableSteps1() {
      Kind<MaybeKind.Witness, String> result =
          For.from(maybeMonad, MAYBE.widen(Maybe.just(5)))
              .par(
                  a -> MAYBE.widen(Maybe.just(a + 1)),
                  a -> MAYBE.widen(Maybe.just(a + 2)),
                  a -> MAYBE.widen(Maybe.just(a + 3)))
              .yield((a, b, c, d) -> a + ":" + b + ":" + c + ":" + d);
      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.just("5:6:7:8"));
    }

    @Test
    @DisplayName("par(4 fns): should combine four dependent computations")
    void par4_fromFilterableSteps1() {
      Kind<MaybeKind.Witness, String> result =
          For.from(maybeMonad, MAYBE.widen(Maybe.just(1)))
              .par(
                  a -> MAYBE.widen(Maybe.just(a * 10)),
                  a -> MAYBE.widen(Maybe.just(a * 100)),
                  a -> MAYBE.widen(Maybe.just(a * 1000)),
                  a -> MAYBE.widen(Maybe.just(a * 10000)))
              .yield((a, b, c, d, e) -> "" + a + ":" + b + ":" + c + ":" + d + ":" + e);
      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.just("1:10:100:1000:10000"));
    }
  }

  // ===== Mixed sequential/parallel chains =====

  @Nested
  @DisplayName("Mixed sequential and parallel chains")
  class MixedChainTests {
    private final IdMonad idMonad = IdMonad.instance();

    @Test
    @DisplayName("from then par then from: should support interleaving")
    void from_par_from() {
      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of(1))
              .from(a -> Id.of(a * 10)) // b = 10
              .par( // c, d in parallel (both depend on (a,b))
                  t -> Id.of(t._1() + t._2()), // c = 11
                  t -> Id.of(t._1() * t._2())) // d = 10
              .yield((a, b, c, d) -> a + ":" + b + ":" + c + ":" + d);
      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("1:10:11:10");
    }

    @Test
    @DisplayName("par then sequential chain should produce correct results")
    void par_then_sequential() {
      Kind<IdKind.Witness, String> result =
          For.par(idMonad, Id.of("hello"), Id.of("world"))
              .from(t -> Id.of(t._1().length() + t._2().length()))
              .let(t -> t._3() > 5)
              .yield(
                  (greeting, name, totalLen, isLong) ->
                      greeting + " " + name + " (" + totalLen + ", long=" + isLong + ")");
      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("hello world (10, long=true)");
    }
  }
}
