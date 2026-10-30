// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.bifunctor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.example.book.foundations.bifunctor.BifunctorBook.ORDER;
import static org.higherkindedj.example.book.foundations.bifunctor.BifunctorBook.forCaller;
import static org.higherkindedj.hkt.constant.ConstKindHelper.CONST;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.eitherorboth.EitherOrBothKindHelper.EITHER_OR_BOTH;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.tuple.Tuple2KindHelper.TUPLE2;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;
import static org.higherkindedj.hkt.writer.WriterKindHelper.WRITER;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.example.book.foundations.OrderError;
import org.higherkindedj.example.book.foundations.TypeClassInstances;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Bifunctor;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.Profunctor;
import org.higherkindedj.hkt.constant.Const;
import org.higherkindedj.hkt.constant.ConstBifunctor;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherBifunctor;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.either.EitherKind2;
import org.higherkindedj.hkt.eitherorboth.EitherOrBoth;
import org.higherkindedj.hkt.eitherorboth.EitherOrBothBifunctor;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.tuple.Tuple2;
import org.higherkindedj.hkt.tuple.Tuple2Bifunctor;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedBifunctor;
import org.higherkindedj.hkt.writer.Writer;
import org.higherkindedj.hkt.writer.WriterBifunctor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Holds the claims the Bifunctor page makes about mapping both sides of a two-sided type. */
@DisplayName("Bifunctor: mapping one side or both of a type with two")
class BifunctorBookTest {

  @Test
  @DisplayName("forCaller turns an Either's order into its status and its error into a message")
  void forCallerOnEither() {
    assertThat(EITHER.narrow2(forCaller(EitherBifunctor.INSTANCE, EITHER.widen2(right()))))
        .isEqualTo(Either.right(OrderStatus.NEW));
    assertThat(
            EITHER.narrow2(
                forCaller(
                    EitherBifunctor.INSTANCE,
                    EITHER.widen2(
                        Either.<OrderError, Order>left(OrderError.alreadyShipped(ORDER.id()))))))
        .isEqualTo(Either.left("the order has shipped"));
  }

  @Test
  @DisplayName("the same forCaller runs on Validated, unchanged")
  void forCallerOnValidated() {
    assertThat(
            VALIDATED.narrow2(
                forCaller(
                    ValidatedBifunctor.INSTANCE,
                    VALIDATED.widen2(Validated.<OrderError, Order>valid(ORDER)))))
        .isEqualTo(Validated.valid(OrderStatus.NEW));
    assertThat(
            VALIDATED.narrow2(
                forCaller(
                    ValidatedBifunctor.INSTANCE,
                    VALIDATED.widen2(
                        Validated.<OrderError, Order>invalid(OrderError.notFound(ORDER.id()))))))
        .isEqualTo(Validated.invalid("no such order"));
  }

  /** Records which of bimap's two functions ran, for the page's table. */
  static final class Calls {
    final List<String> ran = new ArrayList<>();

    <A> Function<A, A> first() {
      return a -> {
        ran.add("first");
        return a;
      };
    }

    <B> Function<B, B> second() {
      return b -> {
        ran.add("second");
        return b;
      };
    }
  }

  @Nested
  @DisplayName("what bimap runs, for each instance")
  class WhatBimapRuns {

    @Test
    @DisplayName("Either and Validated run only the function for the side they hold")
    void sumTypesRunOne() {
      Calls left = new Calls();
      EitherBifunctor.INSTANCE.bimap(
          left.first(), left.second(), EITHER.widen2(Either.<String, Integer>left("error")));
      Calls right = new Calls();
      EitherBifunctor.INSTANCE.bimap(
          right.first(), right.second(), EITHER.widen2(Either.<String, Integer>right(1)));
      Calls invalid = new Calls();
      ValidatedBifunctor.INSTANCE.bimap(
          invalid.first(),
          invalid.second(),
          VALIDATED.widen2(Validated.<String, Integer>invalid("error")));
      Calls valid = new Calls();
      ValidatedBifunctor.INSTANCE.bimap(
          valid.first(), valid.second(), VALIDATED.widen2(Validated.<String, Integer>valid(1)));

      assertThat(left.ran).containsExactly("first");
      assertThat(right.ran).containsExactly("second");
      assertThat(invalid.ran).containsExactly("first");
      assertThat(valid.ran).containsExactly("second");
    }

    @Test
    @DisplayName("EitherOrBoth runs one function on a Left or a Right, and both on a Both")
    void eitherOrBothRunsOneOrBoth() {
      Calls left = new Calls();
      EitherOrBothBifunctor.INSTANCE.bimap(
          left.first(),
          left.second(),
          EITHER_OR_BOTH.widen2(EitherOrBoth.<String, Integer>left("warning")));
      Calls right = new Calls();
      EitherOrBothBifunctor.INSTANCE.bimap(
          right.first(),
          right.second(),
          EITHER_OR_BOTH.widen2(EitherOrBoth.<String, Integer>right(1)));
      Calls both = new Calls();
      EitherOrBothBifunctor.INSTANCE.bimap(
          both.first(),
          both.second(),
          EITHER_OR_BOTH.widen2(EitherOrBoth.<String, Integer>both("warning", 1)));

      assertThat(left.ran).containsExactly("first");
      assertThat(right.ran).containsExactly("second");
      assertThat(both.ran).containsExactlyInAnyOrder("first", "second");
    }

    @Test
    @DisplayName("Tuple2 and Writer always run both")
    void productTypesRunBoth() {
      Calls tuple = new Calls();
      Tuple2Bifunctor.INSTANCE.bimap(
          tuple.first(), tuple.second(), TUPLE2.widen2(new Tuple2<>("note", 1)));
      Calls writer = new Calls();
      Writer<String, Integer> priced = Writer.of("priced; ", 1);
      Writer<String, Integer> mapped =
          WRITER.narrow2(
              WriterBifunctor.INSTANCE.bimap(
                  log -> log.toUpperCase(), value -> value + 1, WRITER.widen2(priced)));
      WriterBifunctor.INSTANCE.bimap(writer.first(), writer.second(), WRITER.widen2(priced));

      assertThat(tuple.ran).containsExactlyInAnyOrder("first", "second");
      assertThat(writer.ran).containsExactlyInAnyOrder("first", "second");
      assertThat(mapped).isEqualTo(Writer.of("PRICED; ", 2));
    }

    @Test
    @DisplayName("Const runs only the first: its second side is a type it holds no value of")
    void constRunsOnlyTheFirst() {
      Calls viaBimap = new Calls();
      Calls viaSecond = new Calls();
      Const<Integer, String> count = new Const<>(3);
      ConstBifunctor.INSTANCE.bimap(viaBimap.first(), viaBimap.second(), CONST.widen2(count));
      Const<Integer, String> retyped =
          CONST.narrow2(ConstBifunctor.INSTANCE.second(viaSecond.second(), CONST.widen2(count)));

      assertThat(viaBimap.ran).containsExactly("first");
      assertThat(viaSecond.ran).isEmpty();
      assertThat(retyped.value()).isEqualTo(3);
    }
  }

  @Test
  @DisplayName("bimap maps each side in place: a Left stays a Left")
  void bimapKeepsTheSide() {
    Either<Integer, Integer> failed =
        EITHER.narrow2(
            EitherBifunctor.INSTANCE.bimap(
                String::length,
                n -> n + 1,
                EITHER.widen2(Either.<String, Integer>left("no such order"))));

    assertThat(failed).isEqualTo(Either.left(13));
  }

  @Test
  @DisplayName("Either's Functor maps the Right, as the Bifunctor's second does")
  void functorIsSecond() {
    Either<String, Integer> result = Either.right(41);
    Functor<EitherKind.Witness<String>> functor = Instances.functor(either());
    Either<String, Integer> viaFunctor =
        EITHER.narrow(functor.map(n -> n + 1, EITHER.widen(result)));
    Kind2<EitherKind2.Witness, String, Integer> viaSecond =
        EitherBifunctor.INSTANCE.second(n -> n + 1, EITHER.widen2(result));

    assertThat(viaFunctor).isEqualTo(EITHER.narrow2(viaSecond));
  }

  @Test
  @DisplayName("Instances has no method that gives a Bifunctor or a Profunctor")
  void instancesHasNoBinaryEntries() {
    List<Method> binary =
        Arrays.stream(Instances.class.getMethods())
            .filter(
                method ->
                    Bifunctor.class.isAssignableFrom(method.getReturnType())
                        || Profunctor.class.isAssignableFrom(method.getReturnType()))
            .toList();

    assertThat(binary).isEmpty();
  }

  @Test
  @DisplayName("hkj-core ships exactly the six Bifunctor instances the page's table lists")
  void tableIsComplete() {
    assertThat(TypeClassInstances.implementingDirectly(Bifunctor.class, EitherBifunctor.class))
        .containsExactlyInAnyOrder(
            "ConstBifunctor",
            "EitherBifunctor",
            "EitherOrBothBifunctor",
            "Tuple2Bifunctor",
            "ValidatedBifunctor",
            "WriterBifunctor");
  }

  private static Either<OrderError, Order> right() {
    return Either.right(ORDER);
  }
}
