// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.nonemptylist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.nonemptylist.NonEmptyListKindHelper.NON_EMPTY_LIST;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.instances.Witnesses;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.validated.ValidatedMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("NonEmptyList HKT instances")
class NonEmptyListInstancesTest {

  @Nested
  @DisplayName("NON_EMPTY_LIST helper")
  class KindHelper {

    @Test
    @DisplayName("widen then narrow round-trips")
    void roundTrip() {
      NonEmptyList<Integer> nel = NonEmptyList.of(1, 2, 3);
      Kind<NonEmptyListKind.Witness, Integer> kind = NON_EMPTY_LIST.widen(nel);
      assertThat(NON_EMPTY_LIST.narrow(kind)).isEqualTo(nel);
    }

    @Test
    @DisplayName("widen rejects null")
    void widenNull() {
      assertThatThrownBy(() -> NON_EMPTY_LIST.widen(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("narrow rejects null")
    void narrowNull() {
      assertThatThrownBy(() -> NON_EMPTY_LIST.narrow(null)).isInstanceOf(KindUnwrapException.class);
    }
  }

  @Nested
  @DisplayName("Functor")
  class Functor {

    @Test
    @DisplayName("map applies to every element")
    void map() {
      Kind<NonEmptyListKind.Witness, Integer> fa = NON_EMPTY_LIST.widen(NonEmptyList.of(1, 2, 3));
      Kind<NonEmptyListKind.Witness, Integer> mapped =
          NonEmptyListFunctor.INSTANCE.map(n -> n * 10, fa);
      assertThat(NON_EMPTY_LIST.narrow(mapped).toJavaList()).containsExactly(10, 20, 30);
    }
  }

  @Nested
  @DisplayName("Monad")
  class MonadInstance {

    private final NonEmptyListMonad monad = NonEmptyListMonad.INSTANCE;

    @Test
    @DisplayName("of lifts a value into a singleton")
    void of() {
      assertThat(NON_EMPTY_LIST.narrow(monad.of(7))).isEqualTo(NonEmptyList.single(7));
    }

    @Test
    @DisplayName("ap applies every function to every value (Cartesian)")
    void ap() {
      NonEmptyList<Function<Integer, Integer>> fns = NonEmptyList.of(n -> n + 1, n -> n * 10);
      Kind<NonEmptyListKind.Witness, Function<Integer, Integer>> ff = NON_EMPTY_LIST.widen(fns);
      Kind<NonEmptyListKind.Witness, Integer> fa = NON_EMPTY_LIST.widen(NonEmptyList.of(1, 2));
      assertThat(NON_EMPTY_LIST.narrow(monad.ap(ff, fa)).toJavaList())
          .containsExactly(2, 3, 10, 20);
    }

    @Test
    @DisplayName("map delegates to the Functor")
    void map() {
      Kind<NonEmptyListKind.Witness, Integer> fa = NON_EMPTY_LIST.widen(NonEmptyList.of(1, 2));
      assertThat(NON_EMPTY_LIST.narrow(monad.map(n -> n + 100, fa)).toJavaList())
          .containsExactly(101, 102);
    }

    @Test
    @DisplayName("flatMap flattens, staying non-empty")
    void flatMap() {
      Kind<NonEmptyListKind.Witness, Integer> ma = NON_EMPTY_LIST.widen(NonEmptyList.of(1, 2));
      Kind<NonEmptyListKind.Witness, Integer> result =
          monad.flatMap(n -> NON_EMPTY_LIST.widen(NonEmptyList.of(n, n * 10)), ma);
      assertThat(NON_EMPTY_LIST.narrow(result).toJavaList()).containsExactly(1, 10, 2, 20);
    }

    @Test
    @DisplayName("flatMap rejects a null result")
    void flatMapNullResult() {
      Kind<NonEmptyListKind.Witness, Integer> ma = NON_EMPTY_LIST.widen(NonEmptyList.of(1));
      assertThatThrownBy(() -> monad.flatMap(n -> null, ma))
          .isInstanceOf(KindUnwrapException.class);
    }
  }

  @Nested
  @DisplayName("Traverse / Foldable")
  class TraverseInstance {

    private final NonEmptyListTraverse traverse = NonEmptyListTraverse.INSTANCE;
    private final MaybeMonad applicative = MaybeMonad.INSTANCE;

    @Test
    @DisplayName("map delegates to the Functor")
    void map() {
      Kind<NonEmptyListKind.Witness, Integer> fa = NON_EMPTY_LIST.widen(NonEmptyList.of(1, 2, 3));
      assertThat(NON_EMPTY_LIST.narrow(traverse.map(n -> n * 2, fa)).toJavaList())
          .containsExactly(2, 4, 6);
    }

    @Test
    @DisplayName("traverse over a single element collects the effect")
    void traverseSingle() {
      Kind<NonEmptyListKind.Witness, Integer> ta = NON_EMPTY_LIST.widen(NonEmptyList.single(5));
      Kind<MaybeKind.Witness, Kind<NonEmptyListKind.Witness, Integer>> result =
          traverse.traverse(applicative, n -> MAYBE.just(n * 10), ta);
      Maybe<Kind<NonEmptyListKind.Witness, Integer>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(NON_EMPTY_LIST.narrow(maybe.get()).toJavaList()).containsExactly(50);
    }

    @Test
    @DisplayName("traverse collects effects of every element, head first")
    void traverseAll() {
      Kind<NonEmptyListKind.Witness, Integer> ta = NON_EMPTY_LIST.widen(NonEmptyList.of(1, 2, 3));
      Kind<MaybeKind.Witness, Kind<NonEmptyListKind.Witness, Integer>> result =
          traverse.traverse(applicative, n -> MAYBE.just(n * 10), ta);
      Maybe<Kind<NonEmptyListKind.Witness, Integer>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(NON_EMPTY_LIST.narrow(maybe.get()).toJavaList()).containsExactly(10, 20, 30);
    }

    @Test
    @DisplayName("traverse short-circuits when any element fails")
    void traverseShortCircuits() {
      Kind<NonEmptyListKind.Witness, Integer> ta = NON_EMPTY_LIST.widen(NonEmptyList.of(1, 2, 3));
      Kind<MaybeKind.Witness, Kind<NonEmptyListKind.Witness, Integer>> result =
          traverse.traverse(
              applicative, n -> n == 2 ? MAYBE.<Integer>nothing() : MAYBE.just(n), ta);
      assertThat(MAYBE.narrow(result).isNothing()).isTrue();
    }

    @Test
    @DisplayName("traverse accumulates effects head-first with an accumulating applicative")
    void traverseAccumulatesInOrder() {
      // Validated accumulates its errors via the Semigroup, joining with a comma.
      Applicative<ValidatedKind.Witness<String>> validatedApp =
          ValidatedMonad.instance(Semigroups.string(","));
      Kind<NonEmptyListKind.Witness, Integer> ta = NON_EMPTY_LIST.widen(NonEmptyList.of(1, 2, 3));

      // The head (1) and a tail element (3) fail; 2 is valid.
      Function<Integer, Kind<ValidatedKind.Witness<String>, Integer>> validate =
          n ->
              n == 2
                  ? VALIDATED.widen(Validated.<String, Integer>valid(n))
                  : VALIDATED.widen(Validated.<String, Integer>invalid("e" + n));

      Kind<ValidatedKind.Witness<String>, Kind<NonEmptyListKind.Witness, Integer>> result =
          traverse.traverse(validatedApp, validate, ta);

      Validated<String, Kind<NonEmptyListKind.Witness, Integer>> validated =
          VALIDATED.narrow(result);
      assertThat(validated.isInvalid()).isTrue();
      // head error first, then the tail error — element order is preserved end-to-end
      assertThat(validated.getError()).isEqualTo("e1,e3");
    }

    @Test
    @DisplayName("foldMap combines all elements from the head")
    void foldMap() {
      Monoid<Integer> sum =
          new Monoid<>() {
            @Override
            public Integer empty() {
              return 0;
            }

            @Override
            public Integer combine(Integer a, Integer b) {
              return a + b;
            }
          };
      Kind<NonEmptyListKind.Witness, Integer> fa =
          NON_EMPTY_LIST.widen(NonEmptyList.of(1, 2, 3, 4));
      assertThat(traverse.foldMap(sum, n -> n, fa)).isEqualTo(10);
    }
  }

  @Nested
  @DisplayName("registry")
  class Registry {

    @Test
    @DisplayName("Witnesses.nonEmptyList() yields a working Monad via Instances")
    void registered() {
      Monad<NonEmptyListKind.Witness> monad = Instances.monad(Witnesses.nonEmptyList());
      Kind<NonEmptyListKind.Witness, Integer> result =
          monad.flatMap(n -> monad.of(n + 1), monad.of(41));
      assertThat(NON_EMPTY_LIST.narrow(result)).isEqualTo(NonEmptyList.single(42));
    }
  }
}
