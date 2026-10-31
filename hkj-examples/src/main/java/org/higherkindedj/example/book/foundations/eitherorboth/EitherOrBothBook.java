// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.eitherorboth;

import static org.higherkindedj.hkt.eitherorboth.EitherOrBothKindHelper.EITHER_OR_BOTH;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.effect.EitherOrBothPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.eitherorboth.EitherOrBoth;
import org.higherkindedj.hkt.eitherorboth.EitherOrBothKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.Validated;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/monads/either_or_both_monad.html">EitherOrBoth</a>
 * page: an order line imported from a wholesaler's feed, whose checks can pass, fail, or pass with
 * a warning; the line assembled with every warning kept; a review step chained with {@code
 * flatMap}; the conversions out; the same steps on an {@code EitherOrBothPath}; and the instance
 * over {@code Kind}. The page {@code {{#include}}}s the anchored regions, {@code
 * EitherOrBothBookTest} holds the claims the page makes, and the book's output gate runs {@link
 * #main} against the comments in the regions.
 */
public final class EitherOrBothBook {

  /** A line costing more than this in total is flagged for a person to check. */
  static final BigDecimal REVIEW_LIMIT = new BigDecimal("100.00");

  private EitherOrBothBook() {}

  // ANCHOR: checks
  // A check can pass, fail, or pass with something to say
  static EitherOrBoth<NonEmptyList<String>, String> sku(String sku) {
    if (sku.isBlank()) {
      return EitherOrBoth.left(NonEmptyList.single("sku is missing"));
    }
    String upper = sku.toUpperCase(Locale.ROOT);
    return upper.equals(sku)
        ? EitherOrBoth.right(sku)
        : EitherOrBoth.both(NonEmptyList.single("sku " + sku + " upper-cased"), upper);
  }

  static EitherOrBoth<NonEmptyList<String>, Integer> quantity(int quantity) {
    return quantity > 0
        ? EitherOrBoth.right(quantity)
        : EitherOrBoth.left(NonEmptyList.single("quantity " + quantity + " is not positive"));
  }

  static EitherOrBoth<NonEmptyList<String>, BigDecimal> price(BigDecimal price) {
    if (price.scale() <= 2) {
      return EitherOrBoth.right(price);
    }
    BigDecimal rounded = price.setScale(2, RoundingMode.HALF_UP);
    return EitherOrBoth.both(
        NonEmptyList.single("price " + price + " rounded to " + rounded), rounded);
  }

  // ANCHOR_END: checks

  // ANCHOR: describe
  static String describe(EitherOrBoth<NonEmptyList<String>, LineItem> result) {
    return switch (result) {
      case EitherOrBoth.Right(LineItem line) -> "imported " + line.sku();
      case EitherOrBoth.Both(NonEmptyList<String> warnings, LineItem line) ->
          "imported " + line.sku() + " with " + warnings.size() + " warning(s)";
      case EitherOrBoth.Left(NonEmptyList<String> problems) ->
          "refused: " + String.join("; ", problems.toJavaList());
    };
  }

  // ANCHOR_END: describe

  // ANCHOR: assemble
  static EitherOrBoth<NonEmptyList<String>, LineItem> line(
      String sku, int quantity, BigDecimal price) {
    return EitherOrBoth.accumulate()
        .and(sku(sku))
        .and(quantity(quantity))
        .and(price(price))
        .apply(LineItem::new);
  }

  // ANCHOR_END: assemble

  // ANCHOR: review
  // This step needs the whole line, so it can only follow the checks
  static EitherOrBoth<NonEmptyList<String>, LineItem> review(LineItem line) {
    BigDecimal total = line.price().multiply(BigDecimal.valueOf(line.quantity()));
    return total.compareTo(REVIEW_LIMIT) > 0
        ? EitherOrBoth.both(NonEmptyList.single("line total " + total + " flagged"), line)
        : EitherOrBoth.right(line);
  }

  static EitherOrBoth<NonEmptyList<String>, LineItem> reviewedLine(
      String sku, int quantity, BigDecimal price) {
    return line(sku, quantity, price).flatMap(NonEmptyList.semigroup(), EitherOrBothBook::review);
  }

  // ANCHOR_END: review

  // ANCHOR: path
  static EitherOrBothPath<NonEmptyList<String>, LineItem> reviewedLinePath(
      String sku, int quantity, BigDecimal price) {
    return Path.eitherOrBoth(line(sku, quantity, price), NonEmptyList.<String>semigroup())
        .via(line -> Path.eitherOrBoth(review(line), NonEmptyList.<String>semigroup()));
  }

  // ANCHOR_END: path

  public static void main(String[] args) {
    // ANCHOR: line_outcomes
    EitherOrBoth<NonEmptyList<String>, LineItem> clean = line("LAMP", 2, new BigDecimal("40.00"));
    // Right(LineItem[sku=LAMP, quantity=2, price=40.00])
    EitherOrBoth<NonEmptyList<String>, LineItem> corrected =
        line("lamp", 2, new BigDecimal("40.005"));
    // Both(NonEmptyList[sku lamp upper-cased, price 40.005 rounded to 40.01],
    //   LineItem[sku=LAMP, quantity=2, price=40.01])
    EitherOrBoth<NonEmptyList<String>, LineItem> refused =
        line("lamp", 0, new BigDecimal("40.005"));
    // Left(NonEmptyList[sku lamp upper-cased, quantity 0 is not positive,
    //   price 40.005 rounded to 40.01])
    // ANCHOR_END: line_outcomes
    System.out.println(clean);
    System.out.println(corrected);
    System.out.println(refused);

    // ANCHOR: review_outcomes
    EitherOrBoth<NonEmptyList<String>, LineItem> flagged =
        reviewedLine("lamp", 3, new BigDecimal("40.005"));
    // Both(NonEmptyList[sku lamp upper-cased, price 40.005 rounded to 40.01,
    //   line total 120.03 flagged], LineItem[sku=LAMP, quantity=3, price=40.01])
    // ANCHOR_END: review_outcomes
    System.out.println(flagged);

    // ANCHOR: conversions
    Either<NonEmptyList<String>, LineItem> dropping = corrected.toEitherDroppingWarnings();
    // Right(LineItem[sku=LAMP, quantity=2, price=40.01])
    Either<NonEmptyList<String>, LineItem> failing = corrected.toEitherFailingOnWarnings();
    // Left(NonEmptyList[sku lamp upper-cased, price 40.005 rounded to 40.01])
    Validated<NonEmptyList<String>, LineItem> validated = corrected.toValidated();
    // Valid(LineItem[sku=LAMP, quantity=2, price=40.01])
    Maybe<LineItem> maybe = corrected.toMaybe();
    // Just(LineItem[sku=LAMP, quantity=2, price=40.01])
    Maybe<NonEmptyList<String>> warnings = corrected.getLeft();
    // Just(NonEmptyList[sku lamp upper-cased, price 40.005 rounded to 40.01])
    // ANCHOR_END: conversions
    System.out.println(dropping);
    System.out.println(failing);
    System.out.println(validated);
    System.out.println(maybe);
    System.out.println(warnings);

    // ANCHOR: path_outcome
    EitherOrBothPath<NonEmptyList<String>, LineItem> onThePath =
        reviewedLinePath("LAMP", 3, new BigDecimal("40.00"));
    // EitherOrBothPath(Both(NonEmptyList[line total 120.00 flagged],
    //   LineItem[sku=LAMP, quantity=3, price=40.00]))
    Maybe<NonEmptyList<String>> flaggedOnPath = onThePath.warnings();
    // Just(NonEmptyList[line total 120.00 flagged])
    // ANCHOR_END: path_outcome
    System.out.println(onThePath);
    System.out.println(flaggedOnPath);

    // ANCHOR: kind
    Monad<EitherOrBothKind.Witness<NonEmptyList<String>>> importing =
        Instances.eitherOrBoth(NonEmptyList.semigroup());

    Kind<EitherOrBothKind.Witness<NonEmptyList<String>>, LineItem> imported =
        EITHER_OR_BOTH.widen(line("lamp", 3, new BigDecimal("40.00")));
    Kind<EitherOrBothKind.Witness<NonEmptyList<String>>, LineItem> checked =
        importing.flatMap(item -> EITHER_OR_BOTH.widen(review(item)), imported);

    EitherOrBoth<NonEmptyList<String>, LineItem> bothWarnings = EITHER_OR_BOTH.narrow(checked);
    // Both(NonEmptyList[sku lamp upper-cased, line total 120.00 flagged],
    //   LineItem[sku=LAMP, quantity=3, price=40.00])
    // ANCHOR_END: kind
    System.out.println(bothWarnings);
  }
}
