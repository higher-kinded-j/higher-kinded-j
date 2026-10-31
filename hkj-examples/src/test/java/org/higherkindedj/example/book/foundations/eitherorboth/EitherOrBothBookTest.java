// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.eitherorboth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.example.book.foundations.eitherorboth.EitherOrBothBook.describe;
import static org.higherkindedj.example.book.foundations.eitherorboth.EitherOrBothBook.line;
import static org.higherkindedj.example.book.foundations.eitherorboth.EitherOrBothBook.reviewedLine;
import static org.higherkindedj.example.book.foundations.eitherorboth.EitherOrBothBook.reviewedLinePath;
import static org.higherkindedj.hkt.eitherorboth.EitherOrBothKindHelper.EITHER_OR_BOTH;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.eitherorboth.EitherOrBoth;
import org.higherkindedj.hkt.eitherorboth.EitherOrBothKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Holds the claims the EitherOrBoth page makes about warnings, flatMap and the conversions. */
@DisplayName("EitherOrBoth: a value, an error, or a value with warnings")
class EitherOrBothBookTest {

  private static final LineItem LAMP = new LineItem("LAMP", 2, new BigDecimal("40.01"));

  private static NonEmptyList<String> nel(String first, String... rest) {
    return NonEmptyList.of(first, rest);
  }

  @Nested
  @DisplayName("importing a line")
  class ImportingALine {

    @Test
    @DisplayName("a clean line is a Right")
    void aCleanLineIsARight() {
      assertThat(line("LAMP", 2, new BigDecimal("40.00")))
          .isEqualTo(EitherOrBoth.right(new LineItem("LAMP", 2, new BigDecimal("40.00"))));
    }

    @Test
    @DisplayName("a corrected line is a Both, with every warning in the order of the checks")
    void aCorrectedLineIsABoth() {
      assertThat(line("lamp", 2, new BigDecimal("40.005")))
          .isEqualTo(
              EitherOrBoth.both(
                  nel("sku lamp upper-cased", "price 40.005 rounded to 40.01"), LAMP));
    }

    @Test
    @DisplayName("a fatal check makes a Left that still keeps every warning")
    void aFatalCheckMakesALeftKeepingWarnings() {
      assertThat(line("lamp", 0, new BigDecimal("40.005")))
          .isEqualTo(
              EitherOrBoth.left(
                  nel(
                      "sku lamp upper-cased",
                      "quantity 0 is not positive",
                      "price 40.005 rounded to 40.01")));
    }

    @Test
    @DisplayName("the switch names each case")
    void theSwitchNamesEachCase() {
      assertThat(describe(line("LAMP", 2, new BigDecimal("40.00")))).isEqualTo("imported LAMP");
      assertThat(describe(line("lamp", 2, new BigDecimal("40.005"))))
          .isEqualTo("imported LAMP with 2 warning(s)");
      assertThat(describe(line("", 2, new BigDecimal("40.00"))))
          .isEqualTo("refused: sku is missing");
      assertThat(describe(line("lamp", 0, new BigDecimal("40.005"))))
          .isEqualTo(
              "refused: sku lamp upper-cased; quantity 0 is not positive;"
                  + " price 40.005 rounded to 40.01");
    }
  }

  @Nested
  @DisplayName("the flatMap contract")
  class TheFlatMapContract {

    private final EitherOrBoth<NonEmptyList<String>, Integer> both = EitherOrBoth.both(nel("w"), 1);

    @Test
    @DisplayName("a review warning joins the import's warnings")
    void aReviewWarningJoinsTheImportsWarnings() {
      assertThat(reviewedLine("lamp", 3, new BigDecimal("40.005")))
          .isEqualTo(
              EitherOrBoth.both(
                  nel(
                      "sku lamp upper-cased",
                      "price 40.005 rounded to 40.01",
                      "line total 120.03 flagged"),
                  new LineItem("LAMP", 3, new BigDecimal("40.01"))));
    }

    @Test
    @DisplayName("Both then Left gives a Left with both warnings")
    void bothThenLeft() {
      assertThat(both.flatMap(NonEmptyList.semigroup(), n -> EitherOrBoth.left(nel("e"))))
          .isEqualTo(EitherOrBoth.left(nel("w", "e")));
    }

    @Test
    @DisplayName("Both then Right gives a Both with the first warnings")
    void bothThenRight() {
      assertThat(both.flatMap(NonEmptyList.semigroup(), n -> EitherOrBoth.right(n + 1)))
          .isEqualTo(EitherOrBoth.both(nel("w"), 2));
    }

    @Test
    @DisplayName("Both then Both gives a Both with every warning")
    void bothThenBoth() {
      assertThat(both.flatMap(NonEmptyList.semigroup(), n -> EitherOrBoth.both(nel("w2"), n + 1)))
          .isEqualTo(EitherOrBoth.both(nel("w", "w2"), 2));
    }

    @Test
    @DisplayName("a Left never runs the step, and a Right gives the step's result")
    void aLeftNeverRunsTheStep() {
      List<String> ran = new ArrayList<>();
      EitherOrBoth<NonEmptyList<String>, Integer> left = EitherOrBoth.left(nel("e"));

      EitherOrBoth<NonEmptyList<String>, Integer> afterLeft =
          left.flatMap(
              NonEmptyList.semigroup(),
              n -> {
                ran.add("step");
                return EitherOrBoth.right(n);
              });
      EitherOrBoth<NonEmptyList<String>, Integer> afterRight =
          EitherOrBoth.<NonEmptyList<String>, Integer>right(1)
              .flatMap(NonEmptyList.semigroup(), n -> EitherOrBoth.both(nel("w"), n));

      assertThat(afterLeft).isEqualTo(left);
      assertThat(ran).isEmpty();
      assertThat(afterRight).isEqualTo(EitherOrBoth.both(nel("w"), 1));
    }

    @Test
    @DisplayName("the instance's ap drops the argument's warnings when the function side is a Left")
    void apShortCircuits() {
      Monad<EitherOrBothKind.Witness<NonEmptyList<String>>> monad =
          Instances.eitherOrBoth(NonEmptyList.semigroup());
      Kind<EitherOrBothKind.Witness<NonEmptyList<String>>, Function<Integer, Integer>> noFunction =
          EITHER_OR_BOTH.widen(EitherOrBoth.left(nel("no function")));
      Kind<EitherOrBothKind.Witness<NonEmptyList<String>>, Integer> warnedArgument =
          EITHER_OR_BOTH.widen(EitherOrBoth.both(nel("argument warning"), 1));

      assertThat(EITHER_OR_BOTH.narrow(monad.ap(noFunction, warnedArgument)))
          .isEqualTo(EitherOrBoth.left(nel("no function")));
    }

    @Test
    @DisplayName("on a Path, zipWithAccum keeps both sides' warnings, even across a Left")
    void pathZipWithAccumKeepsBothSides() {
      assertThat(
              Path.<String, Integer>leftNel("e")
                  .zipWithAccum(Path.<String, Integer>bothNel("w", 1), Integer::sum)
                  .run())
          .isEqualTo(EitherOrBoth.left(nel("e", "w")));
    }

    @Test
    @DisplayName("zipWithAccum keeps both sides' warnings, even across a Left")
    void zipWithAccumKeepsBothSides() {
      EitherOrBoth<NonEmptyList<String>, Integer> left = EitherOrBoth.left(nel("e"));

      assertThat(left.zipWithAccum(both, NonEmptyList.semigroup(), Integer::sum))
          .isEqualTo(EitherOrBoth.left(nel("e", "w")));
    }
  }

  @Nested
  @DisplayName("the conversions")
  class TheConversions {

    private final EitherOrBoth<NonEmptyList<String>, LineItem> corrected =
        line("lamp", 2, new BigDecimal("40.005"));
    private final NonEmptyList<String> warnings =
        nel("sku lamp upper-cased", "price 40.005 rounded to 40.01");

    @Test
    @DisplayName("each conversion decides what happens to a Both's warnings")
    void eachConversionDecides() {
      assertThat(corrected.toEitherDroppingWarnings()).isEqualTo(Either.right(LAMP));
      assertThat(corrected.toEitherFailingOnWarnings()).isEqualTo(Either.left(warnings));
      assertThat(corrected.toValidated()).isEqualTo(Validated.valid(LAMP));
      assertThat(corrected.toMaybe()).isEqualTo(Maybe.just(LAMP));
    }

    @Test
    @DisplayName("a Left converts to the failing case, and a Right to the success")
    void leftAndRightConvert() {
      EitherOrBoth<String, Integer> left = EitherOrBoth.left("e");
      EitherOrBoth<String, Integer> right = EitherOrBoth.right(1);

      assertThat(left.toEitherDroppingWarnings()).isEqualTo(Either.left("e"));
      assertThat(left.toValidated()).isEqualTo(Validated.invalid("e"));
      assertThat(left.toMaybe()).isEqualTo(Maybe.nothing());
      assertThat(right.toEitherFailingOnWarnings()).isEqualTo(Either.right(1));
    }

    @Test
    @DisplayName("fromEither and fromValidated lift the exclusive types in")
    void fromEitherAndFromValidatedLiftIn() {
      assertThat(EitherOrBoth.fromEither(Either.left("e"))).isEqualTo(EitherOrBoth.left("e"));
      assertThat(EitherOrBoth.fromValidated(Validated.valid(1))).isEqualTo(EitherOrBoth.right(1));
    }
  }

  @Nested
  @DisplayName("the methods")
  class TheMethods {

    private final EitherOrBoth<String, Integer> left = EitherOrBoth.left("e");
    private final EitherOrBoth<String, Integer> right = EitherOrBoth.right(1);
    private final EitherOrBoth<String, Integer> both = EitherOrBoth.both("w", 1);

    @Test
    @DisplayName("getLeft and getRight return a Maybe and never throw")
    void theAccessorsAreTotal() {
      assertThat(left.getLeft()).isEqualTo(Maybe.just("e"));
      assertThat(left.getRight()).isEqualTo(Maybe.nothing());
      assertThat(right.getLeft()).isEqualTo(Maybe.nothing());
      assertThat(both.getLeft()).isEqualTo(Maybe.just("w"));
      assertThat(both.getRight()).isEqualTo(Maybe.just(1));
    }

    @Test
    @DisplayName("isLeft, isRight and isBoth name the case")
    void theTestsNameTheCase() {
      assertThat(List.of(left.isLeft(), left.isRight(), left.isBoth()))
          .containsExactly(true, false, false);
      assertThat(List.of(right.isLeft(), right.isRight(), right.isBoth()))
          .containsExactly(false, true, false);
      assertThat(List.of(both.isLeft(), both.isRight(), both.isBoth()))
          .containsExactly(false, false, true);
    }

    @Test
    @DisplayName("map, mapLeft and bimap change only their side, a Both on both sides")
    void theMapsChangeTheirSide() {
      assertThat(both.map(n -> n + 1)).isEqualTo(EitherOrBoth.both("w", 2));
      assertThat(left.map(n -> n + 1)).isEqualTo(left);
      assertThat(both.mapLeft(String::toUpperCase)).isEqualTo(EitherOrBoth.both("W", 1));
      assertThat(right.mapLeft(String::toUpperCase)).isEqualTo(right);
      assertThat(both.bimap(String::toUpperCase, n -> n + 1)).isEqualTo(EitherOrBoth.both("W", 2));
    }

    @Test
    @DisplayName("fold takes one function for each case")
    void foldTakesOneFunctionPerCase() {
      String fromBoth = both.fold(l -> "left " + l, r -> "right " + r, (l, r) -> "both " + l + r);
      String fromLeft = left.fold(l -> "left " + l, r -> "right " + r, (l, r) -> "both");

      assertThat(fromBoth).isEqualTo("both w1");
      assertThat(fromLeft).isEqualTo("left e");
    }

    @Test
    @DisplayName("left, right and both refuse null")
    void theFactoriesRefuseNull() {
      assertThatThrownBy(() -> EitherOrBoth.right(null)).isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> EitherOrBoth.left(null)).isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> EitherOrBoth.both("w", null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Test
  @DisplayName("on a Path, via adds the review's warning")
  void onAPathViaAddsTheWarning() {
    assertThat(reviewedLinePath("LAMP", 3, new BigDecimal("40.00")).run())
        .isEqualTo(
            EitherOrBoth.both(
                nel("line total 120.00 flagged"),
                new LineItem("LAMP", 3, new BigDecimal("40.00"))));
    assertThat(reviewedLinePath("LAMP", 3, new BigDecimal("40.00")).warnings())
        .isEqualTo(Maybe.just(nel("line total 120.00 flagged")));
    assertThat(reviewedLinePath("lamp", 3, new BigDecimal("40.00")).run())
        .isEqualTo(
            EitherOrBoth.both(
                nel("sku lamp upper-cased", "line total 120.00 flagged"),
                new LineItem("LAMP", 3, new BigDecimal("40.00"))));
  }
}
