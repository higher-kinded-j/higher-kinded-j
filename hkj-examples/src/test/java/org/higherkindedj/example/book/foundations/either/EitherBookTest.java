// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.either;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.example.book.foundations.either.EitherBook.ORDERS;
import static org.higherkindedj.example.book.foundations.either.EitherBook.PLACED;
import static org.higherkindedj.example.book.foundations.either.EitherBook.SHIPPED;
import static org.higherkindedj.example.book.foundations.either.EitherBook.UNKNOWN;
import static org.higherkindedj.example.book.foundations.either.EitherBook.describe;
import static org.higherkindedj.example.book.foundations.either.EitherBook.find;
import static org.higherkindedj.example.book.foundations.either.EitherBook.markPaid;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.higherkindedj.example.book.foundations.OrderError;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Holds the claims the Either page makes about its steps, its chain and its methods. */
@DisplayName("Either: a typed error as a value")
class EitherBookTest {

  @Nested
  @DisplayName("the chain")
  class TheChain {

    @Test
    @DisplayName("marks an open order paid")
    void marksAnOpenOrderPaid() {
      assertThat(markPaid(ORDERS, PLACED.id()).getRight().status()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("ends on the first step's error when the order is missing")
    void endsOnTheFirstStepsError() {
      assertThat(markPaid(ORDERS, UNKNOWN)).isEqualTo(Either.left(OrderError.notFound(UNKNOWN)));
    }

    @Test
    @DisplayName("ends on the second step's error when the order has shipped")
    void endsOnTheSecondStepsError() {
      assertThat(markPaid(ORDERS, SHIPPED.id()))
          .isEqualTo(Either.left(OrderError.alreadyShipped(SHIPPED.id())));
    }

    @Test
    @DisplayName("never runs a later step once a step gives a Left")
    void neverRunsALaterStep() {
      List<String> ran = new ArrayList<>();
      Either<OrderError, Order> result =
          find(ORDERS, UNKNOWN)
              .flatMap(
                  order -> {
                    ran.add("requireOpen");
                    return Either.right(order);
                  })
              .map(
                  order -> {
                    ran.add("map");
                    return order;
                  });

      assertThat(result.isLeft()).isTrue();
      assertThat(ran).isEmpty();
    }
  }

  @Nested
  @DisplayName("reading the result")
  class ReadingTheResult {

    @Test
    @DisplayName("the switch names each outcome")
    void theSwitchNamesEachOutcome() {
      assertThat(describe(markPaid(ORDERS, PLACED.id()))).isEqualTo("order is PAID");
      assertThat(describe(markPaid(ORDERS, UNKNOWN))).isEqualTo("no order " + UNKNOWN);
      assertThat(describe(markPaid(ORDERS, SHIPPED.id())))
          .isEqualTo("order " + SHIPPED.id() + " has shipped");
    }

    @Test
    @DisplayName("fold applies the function for the side it holds")
    void foldAppliesTheFunctionForItsSide() {
      Either<String, Integer> left = Either.left("missing");
      Either<String, Integer> right = Either.right(2);

      String fromLeft = left.fold(error -> "error: " + error, value -> "value: " + value);
      String fromRight = right.fold(error -> "error: " + error, value -> "value: " + value);

      assertThat(fromLeft).isEqualTo("error: missing");
      assertThat(fromRight).isEqualTo("value: 2");
    }

    @Test
    @DisplayName("getRight on a Left, and getLeft on a Right, throw")
    void theGettersThrowOnTheWrongSide() {
      assertThatThrownBy(() -> Either.left("missing").getRight())
          .isInstanceOf(NoSuchElementException.class);
      assertThatThrownBy(() -> Either.right(2).getLeft())
          .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("ifLeft and ifRight run only for the side the value holds")
    void ifLeftAndIfRightRunForTheirSide() {
      List<String> ran = new ArrayList<>();
      Either<String, Integer> left = Either.left("missing");

      Either<String, Integer> right = Either.right(2);

      left.ifLeft(error -> ran.add("left " + error));
      left.ifRight(value -> ran.add("right " + value));
      right.ifLeft(error -> ran.add("left " + error));
      right.ifRight(value -> ran.add("right " + value));

      assertThat(ran).containsExactly("left missing", "right 2");
    }
  }

  @Nested
  @DisplayName("changing one side")
  class ChangingOneSide {

    @Test
    @DisplayName("map changes a Right and leaves a Left as it is")
    void mapChangesOnlyARight() {
      assertThat(Either.<String, Integer>right(2).map(n -> n * 10)).isEqualTo(Either.right(20));
      assertThat(Either.<String, Integer>right(2).mapRight(n -> n * 10))
          .isEqualTo(Either.right(20));
      assertThat(Either.<String, Integer>left("missing").map(n -> n * 10))
          .isEqualTo(Either.left("missing"));
    }

    @Test
    @DisplayName("mapLeft changes a Left and leaves a Right as it is")
    void mapLeftChangesOnlyALeft() {
      assertThat(Either.<String, Integer>left("missing").mapLeft(String::length))
          .isEqualTo(Either.left(7));
      assertThat(Either.<String, Integer>right(2).mapLeft(String::length))
          .isEqualTo(Either.right(2));
    }

    @Test
    @DisplayName("bimap changes whichever side the value holds")
    void bimapChangesEitherSide() {
      assertThat(Either.<String, Integer>left("missing").bimap(String::length, n -> n * 10))
          .isEqualTo(Either.left(7));
      assertThat(Either.<String, Integer>right(2).bimap(String::length, n -> n * 10))
          .isEqualTo(Either.right(20));
    }
  }

  @Test
  @DisplayName("widening allocates nothing: it returns the same object")
  void wideningReturnsTheSameObject() {
    Either<OrderError, Order> found = find(ORDERS, PLACED.id());

    assertThat(EITHER.widen(found)).isSameAs(found);
  }

  @Test
  @DisplayName("a Maybe and a Validated become an Either")
  void aMaybeAndAValidatedBecomeAnEither() {
    assertThat(Maybe.<Integer>nothing().toEither("missing")).isEqualTo(Either.left("missing"));
    assertThat(Maybe.just(2).toEither("missing")).isEqualTo(Either.right(2));
    assertThat(Validated.<String, Integer>invalid("too long").toEither())
        .isEqualTo(Either.left("too long"));
  }

  @Nested
  @DisplayName("values, never null")
  class ValuesNeverNull {

    @Test
    @DisplayName("right and left refuse null")
    void rightAndLeftRefuseNull() {
      assertThatThrownBy(() -> Either.right(null)).isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> Either.left(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("a map whose function returns null throws")
    void aMapReturningNullThrows() {
      assertThatThrownBy(() -> Either.<String, Integer>right(2).map(n -> null))
          .isInstanceOf(NullPointerException.class);
    }
  }
}
