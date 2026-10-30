// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.selective;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.example.book.foundations.selective.SelectiveBook.bookCourier;
import static org.higherkindedj.example.book.foundations.selective.SelectiveBook.reviewIfLarge;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOSelective;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeSelective;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the Selective page makes about which effects run. */
@DisplayName("Selective: only the effect the condition picks runs")
class SelectiveBookTest {

  private static final Selective<MaybeKind.Witness> MAYBE_SELECTIVE = MaybeSelective.INSTANCE;

  @Test
  @DisplayName("select applies the function to a Left, and never needs it for a Right")
  void selectNeedsTheFunctionOnlyForALeft() {
    Kind<MaybeKind.Witness, Function<String, BigDecimal>> parse = MAYBE.just(BigDecimal::new);
    Kind<MaybeKind.Witness, Function<String, BigDecimal>> noFunction = MAYBE.nothing();

    assertThat(
            MAYBE.narrow(
                MAYBE_SELECTIVE.select(
                    MAYBE.just(Selective.<String, BigDecimal>left("40.00")), parse)))
        .isEqualTo(Maybe.just(new BigDecimal("40.00")));

    assertThat(
            MAYBE.narrow(
                MAYBE_SELECTIVE.select(
                    MAYBE.just(Selective.<String, BigDecimal>left("40.00")), noFunction)))
        .isEqualTo(Maybe.nothing());
    assertThat(
            MAYBE.narrow(
                MAYBE_SELECTIVE.select(
                    MAYBE.just(Selective.<String, BigDecimal>right(new BigDecimal("2.50"))),
                    noFunction)))
        .isEqualTo(Maybe.just(new BigDecimal("2.50")));
  }

  @Test
  @DisplayName("whenS runs the flag only for a large order, and nothing until the IO runs")
  void whenSRunsTheFlagOnlyWhenLarge() {
    List<String> flagged = new ArrayList<>();

    var small = reviewIfLarge(new BigDecimal("50.00"), flagged);
    var large = reviewIfLarge(new BigDecimal("120.00"), flagged);
    assertThat(flagged).isEmpty();

    IO_OP.narrow(small).unsafeRunSync();
    assertThat(flagged).isEmpty();
    IO_OP.narrow(large).unsafeRunSync();
    assertThat(flagged).containsExactly("review 120.00");
  }

  @Test
  @DisplayName("ifS runs only the booking the order's size picks")
  void ifSRunsOnlyThePickedBooking() {
    List<String> booked = new ArrayList<>();

    assertThat(IO_OP.narrow(bookCourier(new BigDecimal("50.00"), booked)).unsafeRunSync())
        .isEqualTo("post");
    assertThat(booked).containsExactly("post");

    assertThat(IO_OP.narrow(bookCourier(new BigDecimal("120.00"), booked)).unsafeRunSync())
        .isEqualTo("freight");
    assertThat(booked).containsExactly("post", "freight");
  }

  @Test
  @DisplayName("orElse gives the first Right, or the last Left when every alternative fails")
  void orElseGivesTheFirstRight() {
    Choice<String, String> firstRight =
        MAYBE
            .narrow(
                MAYBE_SELECTIVE.orElse(
                    List.of(
                        MAYBE.just(Selective.<String, String>left("no courier")),
                        MAYBE.just(Selective.<String, String>right("post")),
                        MAYBE.just(Selective.<String, String>right("freight")))))
            .get();
    Choice<String, String> lastLeft =
        MAYBE
            .narrow(
                MAYBE_SELECTIVE.orElse(
                    List.of(
                        MAYBE.just(Selective.<String, String>left("no post")),
                        MAYBE.just(Selective.<String, String>left("no freight")))))
            .get();

    assertThat(firstRight.isRight()).isTrue();
    assertThat(firstRight.getRight()).isEqualTo("post");
    assertThat(lastLeft.isLeft()).isTrue();
    assertThat(lastLeft.getLeft()).isEqualTo("no freight");
  }

  @Test
  @DisplayName("over IO, branch runs only the handler for the side the choice is on")
  void branchRunsOnlyOneHandler() {
    List<String> ran = new ArrayList<>();
    Kind<IOKind.Witness, Choice<String, BigDecimal>> needsParsing =
        IO_OP.widen(IO.delay(() -> Selective.<String, BigDecimal>left("40.00")));
    Kind<IOKind.Witness, Function<String, String>> onLeft =
        IO_OP.widen(
            IO.delay(
                () -> {
                  ran.add("left");
                  return text -> "parsed " + text;
                }));
    Kind<IOKind.Witness, Function<BigDecimal, String>> onRight =
        IO_OP.widen(
            IO.delay(
                () -> {
                  ran.add("right");
                  return price -> "ready " + price;
                }));

    String result =
        IO_OP.narrow(IOSelective.INSTANCE.branch(needsParsing, onLeft, onRight)).unsafeRunSync();

    assertThat(result).isEqualTo("parsed 40.00");
    assertThat(ran).containsExactly("left");
  }

  @Test
  @DisplayName("apS runs each step while the value is a Right, and stops at the first Left")
  void apSStopsAtTheFirstLeft() {
    List<String> ran = new ArrayList<>();
    Function<String, Kind<MaybeKind.Witness, Function<Integer, Choice<String, Integer>>>> step =
        name ->
            MAYBE.just(
                quantity -> {
                  ran.add(name);
                  return quantity > 1
                      ? Selective.left(name + " stopped it")
                      : Selective.right(quantity + 1);
                });

    Choice<String, Integer> result =
        MAYBE
            .narrow(
                MAYBE_SELECTIVE.apS(
                    MAYBE.just(Selective.<String, Integer>right(1)),
                    List.of(step.apply("first"), step.apply("second"), step.apply("third"))))
            .get();

    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isEqualTo("second stopped it");
    assertThat(ran).containsExactly("first", "second");
  }

  @Test
  @DisplayName("whenS_ runs an effect that gives a value only when the condition holds")
  void whenS_RunsOnlyWhenTheConditionHolds() {
    List<String> ran = new ArrayList<>();
    Kind<IOKind.Witness, Integer> write =
        IO_OP.widen(
            IO.delay(
                () -> {
                  ran.add("write");
                  return 3;
                }));

    IO_OP
        .narrow(IOSelective.INSTANCE.whenS_(IO_OP.widen(IO.delay(() -> false)), write))
        .unsafeRunSync();
    assertThat(ran).isEmpty();
    IO_OP
        .narrow(IOSelective.INSTANCE.whenS_(IO_OP.widen(IO.delay(() -> true)), write))
        .unsafeRunSync();
    assertThat(ran).containsExactly("write");
  }
}
