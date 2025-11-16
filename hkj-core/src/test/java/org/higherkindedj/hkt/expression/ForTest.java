// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.Arrays;
import java.util.Collections;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListMonad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.hkt.tuple.Tuple;
import org.higherkindedj.hkt.tuple.Tuple2;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("For Comprehension Tests")
class ForTest {

  @Nested
  @DisplayName("With Identity Monad (Non-Filterable)")
  class ForIdTest {
    private final IdMonad idMonad = IdMonad.instance();

    @Test
    @DisplayName("Arity 1: should yield value")
    void arity1_yield() {
      Kind<IdKind.Witness, String> result = For.from(idMonad, Id.of(10)).yield(i -> "v" + i);
      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("v10");
    }

    @Test
    @DisplayName("Arity 2: should chain from and let and yield")
    void arity2_fromLetYield() {
      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of(10)).let(i -> i * 2).yield((a, b) -> a + ":" + b);
      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("10:20");
    }

    @Test
    @DisplayName("Arity 2: should chain from and from and yield")
    void arity2_fromFromYield() {
      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of(5)).from(a -> Id.of("x")).yield((a, b) -> a + b);
      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("5x");
    }

    @Test
    @DisplayName("Arity 2: should chain let on arity 2")
    void arity2_let() {
      Kind<IdKind.Witness, Integer> result =
          For.from(idMonad, Id.of(5))
              .from(a -> Id.of(a * 2)) // b = 10
              .let(t -> t._1() + t._2()) // c = 15
              .yield((a, b, c) -> a + b + c); // 5 + 10 + 15
      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo(30);
    }

    @Test
    @DisplayName("Arity 2: should yield with tuple function")
    void arity2_yieldTuple() {
      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of(5)).from(a -> Id.of("x")).yield(t -> t._1() + t._2());
      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("5x");
    }

    @Test
    @DisplayName("Arity 3: should chain from, from, from and yield")
    void arity3_fromFromFromYield() {
      Kind<IdKind.Witness, Integer> result =
          For.from(idMonad, Id.of(1))
              .from(a -> Id.of(2))
              .from(ab -> Id.of(3))
              .yield((a, b, c) -> a + b + c);
      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo(6);
    }

    @Test
    @DisplayName("Arity 3: should chain and yield")
    void arity3_yield() {
      Kind<IdKind.Witness, Integer> result =
          For.from(idMonad, Id.of(5))
              .let(i -> i * 2) // b = 10
              .from(t -> Id.of(t._1() + t._2())) // c = 15
              .yield((a, b, c) -> a + b + c); // 5 + 10 + 15
      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo(30);
    }

    @Test
    @DisplayName("Arity 3: should yield with tuple function")
    void arity3_yieldTuple() {
      Kind<IdKind.Witness, Integer> result =
          For.from(idMonad, Id.of(1))
              .from(a -> Id.of(2))
              .from(ab -> Id.of(3))
              .yield(t -> t._1() + t._2() + t._3());
      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo(6);
    }

    @Test
    @DisplayName("Arity 4: should chain and yield")
    void arity4_yield() {
      Kind<IdKind.Witness, Integer> result =
          For.from(idMonad, Id.of(1))
              .let(a -> a + 1) // b = 2
              .from(t -> Id.of(t._2() * 2)) // c = 4
              .let(t -> t._3() * 2) // d = 8
              .yield((a, b, c, d) -> a + b + c + d); // 1 + 2 + 4 + 8
      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo(15);
    }

    @Test
    @DisplayName("Arity 4: should yield with tuple function")
    void arity4_yieldTuple() {
      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of(1))
              .from(a -> Id.of("b"))
              .from(ab -> Id.of(3.0))
              .from(abc -> Id.of(true))
              .yield(t -> "" + t._1() + t._2() + t._3() + t._4());
      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("1b3.0true");
    }

    @Test
    @DisplayName("Arity 4: should chain let and yield")
    void arity4_let() {
      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of(1)) // a
              .from(a -> Id.of("b")) // b
              .from(t -> Id.of(3.0)) // c
              .from(t -> Id.of(true)) // d
              .let(t -> t._1() + t._2() + t._3() + t._4().toString()) // e
              .yield((a, b, c, d, e) -> "" + a + ":" + b + ":" + c + ":" + d + ":" + e);
      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("1:b:3.0:true:1b3.0true");
    }

    @Test
    @DisplayName("Arity 5: should chain and yield")
    void arity5_yield() {
      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of(1)) // a = 1
              .let(a -> a + 1) // b = 2
              .from(t -> Id.of(t._2() * 2)) // c = 4
              .let(t -> t._3() * 2) // d = 8
              .from(t -> Id.of("e" + t._4())) // e = "e8"
              .yield((a, b, c, d, e) -> a + ":" + b + ":" + c + ":" + d + ":" + e);
      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("1:2:4:8:e8");
    }

    @Test
    @DisplayName("Arity 5: should yield with tuple function")
    void arity5_yieldTuple() {
      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of(1))
              .from(a -> Id.of("b"))
              .from(t -> Id.of(3.0))
              .from(t -> Id.of(true))
              .from(t -> Id.of('e'))
              .yield(t -> "" + t._1() + t._2() + t._3() + t._4() + t._5());
      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("1b3.0truee");
    }
  }

  @Nested
  @DisplayName("With List Monad (Filterable)")
  class ForListTest {
    private final ListMonad listMonad = ListMonad.INSTANCE;

    @Test
    @DisplayName("should filter values with 'when'")
    void singleGeneratorWithFilter() {
      Kind<ListKind.Witness, Integer> list = LIST.widen(Arrays.asList(1, 2, 3, 4));
      Kind<ListKind.Witness, Integer> result =
          For.from(listMonad, list).when(i -> i % 2 == 0).yield(i -> i * 10);
      assertThat(LIST.narrow(result)).containsExactly(20, 40);
    }

    @Test
    @DisplayName("should support let bindings")
    void withLetBinding() {
      Kind<ListKind.Witness, Integer> list1 = LIST.widen(Arrays.asList(1, 2));
      Kind<ListKind.Witness, String> list3 = LIST.widen(Arrays.asList("x", "y"));

      Kind<ListKind.Witness, String> result =
          For.from(listMonad, list1)
              .let(i -> "i" + i) // b is "i1", "i2"
              .from(t -> list3) // c is "x", "y"
              .yield((a, b, c) -> a + ":" + b + ":" + c);

      assertThat(LIST.narrow(result)).containsExactly("1:i1:x", "1:i1:y", "2:i2:x", "2:i2:y");
    }

    @Test
    @DisplayName("should short-circuit when a 'from' generator returns an empty list")
    void fromReturnsEmptyList() {
      Kind<ListKind.Witness, Integer> list1 = LIST.widen(Arrays.asList(1, 2, 3));
      Kind<ListKind.Witness, String> list3 = LIST.widen(Arrays.asList("a", "b"));

      Kind<ListKind.Witness, String> result =
          For.from(listMonad, list1)
              .from(
                  i -> {
                    if (i == 2) return listMonad.zero(); // Return empty list for i=2
                    return LIST.widen(Collections.singletonList("x" + i));
                  })
              .from(t -> list3)
              .yield((a, b, c) -> a + ":" + b + ":" + c);

      assertThat(LIST.narrow(result)).containsExactly("1:x1:a", "1:x1:b", "3:x3:a", "3:x3:b");
    }

    @Test
    @DisplayName("should short-circuit when the first generator is empty")
    void firstGeneratorIsEmpty() {
      Kind<ListKind.Witness, Integer> list1 = LIST.widen(Collections.emptyList());
      Kind<ListKind.Witness, String> list2 = LIST.widen(Arrays.asList("a", "b"));
      Kind<ListKind.Witness, String> result =
          For.from(listMonad, list1).from(i -> list2).yield((a, b) -> a + b);
      assertThat(LIST.narrow(result)).isEmpty();
    }

    @Test
    @DisplayName("should chain multiple 'when' filters")
    void multipleWhenFilters() {
      Kind<ListKind.Witness, Integer> list = LIST.widen(Arrays.asList(1, 2, 3, 4, 5, 6));

      Kind<ListKind.Witness, Integer> result =
          For.from(listMonad, list).when(i -> i > 2).when(i -> i % 2 == 0).yield(i -> i);

      assertThat(LIST.narrow(result)).containsExactly(4, 6);
    }

    @Test
    @DisplayName("should filter results after a 'let' binding")
    void letFollowedByWhen() {
      Kind<ListKind.Witness, Integer> list1 = LIST.widen(Arrays.asList(1, 2));

      Kind<ListKind.Witness, Integer> result =
          For.from(listMonad, list1).let(i -> i * 10).when(t -> t._2() > 15).yield((a, b) -> a + b);

      assertThat(LIST.narrow(result)).containsExactly(22);
    }

    @Test
    @DisplayName("should yield the tuple itself")
    void yieldTuple() {
      Kind<ListKind.Witness, Integer> list1 = LIST.widen(Arrays.asList(1));
      Kind<ListKind.Witness, String> list2 = LIST.widen(Arrays.asList("a"));

      Kind<ListKind.Witness, Tuple2<Integer, String>> result =
          For.from(listMonad, list1).from(i -> list2).yield(t -> t);

      assertThat(LIST.narrow(result)).containsExactly(Tuple.of(1, "a"));
    }

    @Test
    @DisplayName("Arity 2: should chain let on filterable steps")
    void arity2_let() {
      Kind<ListKind.Witness, String> result =
          For.from(listMonad, LIST.widen(Arrays.asList(1, 2)))
              .from(a -> LIST.widen(Arrays.asList("x")))
              .let(t -> t._1() + t._2())
              .yield((a, b, c) -> a + ":" + b + ":" + c);
      assertThat(LIST.narrow(result)).containsExactly("1:x:1x", "2:x:2x");
    }

    @Test
    @DisplayName("Arity 3: should filter values with 'when'")
    void arity3_withFilter() {
      Kind<ListKind.Witness, Integer> result =
          For.from(listMonad, LIST.widen(Arrays.asList(1, 2)))
              .from(a -> LIST.widen(Arrays.asList(10, 20)))
              .from(t -> LIST.widen(Arrays.asList(100, 200)))
              .when(t -> (t._1() + t._2() + t._3()) > 221)
              .yield(t -> t._1() + t._2() + t._3());
      // The only combination with a sum > 221 is 2 + 20 + 200 = 222.
      assertThat(LIST.narrow(result)).containsExactly(222);
    }

    @Test
    @DisplayName("Arity 3: should chain let on filterable steps")
    void arity3_let() {
      Kind<ListKind.Witness, String> result =
          For.from(listMonad, LIST.widen(Collections.singletonList(1)))
              .from(a -> LIST.widen(Collections.singletonList("b")))
              .from(t -> LIST.widen(Collections.singletonList(true)))
              .let(t -> t._1() + t._2() + t._3())
              .yield((a, b, c, d) -> a + ":" + b + ":" + c + ":" + d);
      assertThat(LIST.narrow(result)).containsExactly("1:b:true:1btrue");
    }

    @Test
    @DisplayName("Arity 4: should filter values with 'when'")
    void arity4_withFilter() {
      Kind<ListKind.Witness, Integer> result =
          For.from(listMonad, LIST.widen(Arrays.asList(1, 2))) // a
              .from(a -> LIST.widen(Arrays.asList(10, 20))) // b
              .from(t -> LIST.widen(Arrays.asList(100, 200))) // c
              .from(t -> LIST.widen(Arrays.asList(1000, 2000))) // d
              .when(t -> (t._1() + t._2() + t._3() + t._4()) > 3231) // Filter
              .yield(t -> t._1() + t._2() + t._3() + t._4());
      // The maximum possible sum is 2+20+200+2000 = 2222, which is not > 3231.
      // Therefore, the result should be empty.
      assertThat(LIST.narrow(result)).isEmpty();
    }

    @Test
    @DisplayName("Arity 4: should chain let on filterable steps")
    void arity4_let() {
      Kind<ListKind.Witness, String> result =
          For.from(listMonad, LIST.widen(Arrays.asList(1, 10))) // a
              .from(a -> LIST.widen(Arrays.asList("b"))) // b
              .from(t -> LIST.widen(Arrays.asList(true))) // c
              .from(t -> LIST.widen(Arrays.asList(4.0))) // d
              .when(
                  t -> t._1() < 5) // filter on (a, b, c, d) -> keeps only the tuple starting with 1
              .let(t -> "e-is-" + t._4()) // e
              .yield((a, b, c, d, e) -> a + "," + b + "," + c + "," + d + "," + e);

      assertThat(LIST.narrow(result)).containsExactly("1,b,true,4.0,e-is-4.0");
    }

    @Test
    @DisplayName("Arity 5: should yield values")
    void arity5_yield() {
      Kind<ListKind.Witness, String> result =
          For.from(listMonad, LIST.widen(Arrays.asList(1)))
              .from(a -> LIST.widen(Arrays.asList("b")))
              .from(t -> LIST.widen(Arrays.asList(true)))
              .from(t -> LIST.widen(Arrays.asList(4.0)))
              .from(t -> LIST.widen(Arrays.asList('e')))
              .yield((a, b, c, d, e) -> "" + a + b + c + d + e);
      assertThat(LIST.narrow(result)).containsExactly("1btrue4.0e");
    }

    @Test
    @DisplayName("Arity 5: should filter with 'when'")
    void arity5_withFilter() {
      Kind<ListKind.Witness, Integer> result =
          For.from(listMonad, LIST.widen(Arrays.asList(1, 2)))
              .from(a -> LIST.widen(Arrays.asList(10)))
              .from(t -> LIST.widen(Arrays.asList(100)))
              .from(t -> LIST.widen(Arrays.asList(1000)))
              .from(t -> LIST.widen(Arrays.asList(10000)))
              .when(t -> t._1() == 2)
              .yield(t -> t._1() + t._2() + t._3() + t._4() + t._5());
      assertThat(LIST.narrow(result)).containsExactly(11112);
    }
  }

  @Nested
  @DisplayName("With Maybe Monad (Filterable)")
  class ForMaybeTest {
    private final MaybeMonad maybeMonad = MaybeMonad.INSTANCE;

    @Test
    @DisplayName("should chain let bindings with Maybe")
    void withLetBinding() {
      Kind<MaybeKind.Witness, Integer> just1 = MAYBE.just(10);

      Kind<MaybeKind.Witness, String> result =
          For.from(maybeMonad, just1)
              .let(i -> i * 2) // b = 20
              .let(t -> t._1() + t._2()) // c = 10 + 20 = 30
              .yield((a, b, c) -> a + ":" + b + ":" + c);

      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.just("10:20:30"));
    }

    @Test
    @DisplayName("should short-circuit to Nothing if a generator is Nothing")
    void shortCircuitOnNothing() {
      Kind<MaybeKind.Witness, Integer> just1 = MAYBE.just(10);
      Kind<MaybeKind.Witness, String> nothing = MAYBE.nothing();
      Kind<MaybeKind.Witness, Double> just3 = MAYBE.just(1.5);

      Kind<MaybeKind.Witness, String> result =
          For.from(maybeMonad, just1)
              .from(i -> nothing)
              .from(t -> just3)
              .yield((i, s, d) -> "should not be reached");

      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("should apply filter after a 'let' binding on a Just value")
    void letFollowedByWhenOnJust() {
      Kind<MaybeKind.Witness, String> maybe = MAYBE.just("hello");

      Kind<MaybeKind.Witness, String> resultPass =
          For.from(maybeMonad, maybe)
              .let(String::length)
              .when(t -> t._2() == 5)
              .yield((s, len) -> s + " has length " + len);

      assertThat(MAYBE.narrow(resultPass)).isEqualTo(Maybe.just("hello has length 5"));

      Kind<MaybeKind.Witness, String> resultFail =
          For.from(maybeMonad, maybe)
              .let(String::length)
              .when(t -> t._2() > 10)
              .yield((s, len) -> "should not be reached");

      assertThat(MAYBE.narrow(resultFail)).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("should not apply 'let' or 'when' if initial value is Nothing")
    void letAndWhenOnNothing() {
      Kind<MaybeKind.Witness, String> maybe = MAYBE.nothing();

      Kind<MaybeKind.Witness, String> result =
          For.from(maybeMonad, maybe)
              .let(
                  s -> {
                    throw new AssertionError("let should not be called");
                  })
              .when(
                  t -> {
                    throw new AssertionError("when should not be called");
                  })
              .yield((a, b) -> "should not be reached");

      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("should short-circuit if from after let is Nothing")
    void letFollowedByFromNothing() {
      Kind<MaybeKind.Witness, Integer> just1 = MAYBE.just(10);
      Kind<MaybeKind.Witness, String> nothing = MAYBE.nothing();

      Kind<MaybeKind.Witness, String> result =
          For.from(MaybeMonad.INSTANCE, just1)
              .let(i -> "let-val")
              .from(t -> nothing)
              .yield((a, b, c) -> "should not be reached");

      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("Arity 3: should filter with 'when'")
    void arity3_withFilter() {
      Kind<MaybeKind.Witness, Integer> result =
          For.from(maybeMonad, MAYBE.just(1))
              .from(a -> MAYBE.just(10))
              .from(t -> MAYBE.just(100))
              .when(t -> t._3() > 50)
              .yield(t -> t._1() + t._2() + t._3());
      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.just(111));

      Kind<MaybeKind.Witness, Integer> resultFiltered =
          For.from(maybeMonad, MAYBE.just(1))
              .from(a -> MAYBE.just(10))
              .from(t -> MAYBE.just(100))
              .when(t -> t._3() < 50)
              .yield(t -> t._1() + t._2() + t._3());
      assertThat(MAYBE.narrow(resultFiltered)).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("Arity 3: should yield with tuple function")
    void arity3_yieldTuple() {
      Kind<MaybeKind.Witness, Integer> result =
          For.from(maybeMonad, MAYBE.just(1))
              .from(a -> MAYBE.just(10))
              .from(t -> MAYBE.just(100))
              .yield(t -> t._1() + t._2() + t._3());
      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.just(111));
    }

    @Test
    @DisplayName("Arity 4: should filter with 'when'")
    void arity4_withFilter() {
      Kind<MaybeKind.Witness, Integer> result =
          For.from(maybeMonad, MAYBE.just(1))
              .from(a -> MAYBE.just(10))
              .from(t -> MAYBE.just(100))
              .from(t -> MAYBE.just(1000))
              .when(t -> t._4() > 500)
              .yield(t -> t._1() + t._2() + t._3() + t._4());
      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.just(1111));

      Kind<MaybeKind.Witness, Integer> resultFiltered =
          For.from(maybeMonad, MAYBE.just(1))
              .from(a -> MAYBE.just(10))
              .from(t -> MAYBE.just(100))
              .from(t -> MAYBE.just(1000))
              .when(t -> t._4() < 500)
              .yield(t -> t._1() + t._2() + t._3() + t._4());
      assertThat(MAYBE.narrow(resultFiltered)).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("Arity 4: should yield with tuple function")
    void arity4_yieldTuple() {
      Kind<MaybeKind.Witness, Integer> result =
          For.from(maybeMonad, MAYBE.just(1))
              .from(a -> MAYBE.just(10))
              .from(t -> MAYBE.just(100))
              .from(t -> MAYBE.just(1000))
              .yield(t -> t._1() + t._2() + t._3() + t._4());
      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.just(1111));
    }
  }
}
