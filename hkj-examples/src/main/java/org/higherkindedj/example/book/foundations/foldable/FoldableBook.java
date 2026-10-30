// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.foldable;

import static org.higherkindedj.example.book.foundations.monoid.MonoidBook.LINES;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.math.BigDecimal;
import java.util.List;
import org.higherkindedj.example.book.foundations.monoid.MonoidBook;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.effect.ListPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeTraverse;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/functional/foldable_and_traverse.html">Foldable
 * and Traverse</a> page: an order's line items folded into a total, a check and a list of SKUs, and
 * traversed with a check that one instance stops at and another reports in full. The line items and
 * {@code lineTotal} are the Semigroup and Monoid page's. The page {@code {{#include}}}s the
 * anchored regions, {@code FoldableBookTest} holds the claims the page makes, and the book's output
 * gate runs {@link #main} against the comments in the regions.
 */
public final class FoldableBook {

  private FoldableBook() {}

  // ANCHOR: zero
  /** An order whose chair and desk lines ask for nothing. */
  static final List<LineItem> ZERO_QUANTITIES =
      List.of(
          new LineItem("LAMP", 1, new BigDecimal("40.00")),
          new LineItem("CHAIR", 0, new BigDecimal("60.00")),
          new LineItem("DESK", 0, new BigDecimal("120.00")));

  // ANCHOR_END: zero

  // ANCHOR: generic
  // One method for any container F: the Foldable passed in walks F, and the monoid adds up
  public static <F extends WitnessArity<TypeArity.Unary>> BigDecimal total(
      Foldable<F> foldable, Kind<F, LineItem> lines) {
    return foldable.foldMap(Monoids.bigDecimalAddition(), MonoidBook::lineTotal, lines);
  }

  // ANCHOR_END: generic

  // ANCHOR: check
  // A check on one line, which fails a line that asks for nothing
  static Validated<List<String>, LineItem> checkLine(LineItem line) {
    return line.quantity() > 0
        ? Validated.valid(line)
        : Validated.invalid(List.of(line.sku() + ": quantity must be positive"));
  }

  // ANCHOR_END: check

  public static void main(String[] args) {
    // ANCHOR: path
    BigDecimal pathTotal =
        ListPath.of(LINES).foldMap(Monoids.bigDecimalAddition(), MonoidBook::lineTotal);
    // 50.00
    // ANCHOR_END: path
    System.out.println(pathTotal);

    // ANCHOR: summaries
    Foldable<ListKind.Witness> foldable = ListTraverse.INSTANCE;
    Kind<ListKind.Witness, LineItem> lines = LIST.widen(LINES);

    BigDecimal sum = foldable.foldMap(Monoids.bigDecimalAddition(), MonoidBook::lineTotal, lines);
    // 50.00

    Boolean everyQuantityPositive =
        foldable.foldMap(Monoids.booleanAnd(), line -> line.quantity() > 0, lines);
    // true

    List<String> skus = foldable.foldMap(Monoids.list(), line -> List.of(line.sku()), lines);
    // [LAMP, BULB]
    // ANCHOR_END: summaries
    System.out.println(sum);
    System.out.println(everyQuantityPositive);
    System.out.println(skus);

    // ANCHOR: instances
    BigDecimal orderTotal = total(ListTraverse.INSTANCE, LIST.widen(LINES));
    // 50.00

    BigDecimal oneLine = total(MaybeTraverse.INSTANCE, MAYBE.just(LINES.getFirst()));
    // 40.00

    BigDecimal noLine = total(MaybeTraverse.INSTANCE, MAYBE.nothing());
    // 0
    // ANCHOR_END: instances
    System.out.println(orderTotal);
    System.out.println(oneLine);
    System.out.println(noLine);

    // ANCHOR: traverse
    Applicative<ValidatedKind.Witness<List<String>>> validatedApplicative =
        Instances.validated(Semigroups.list());
    Validated<List<String>, List<LineItem>> everyError =
        VALIDATED
            .narrow(
                ListTraverse.INSTANCE.traverse(
                    validatedApplicative,
                    line -> VALIDATED.widen(checkLine(line)),
                    LIST.widen(ZERO_QUANTITIES)))
            .map(LIST::narrow);
    // Invalid([CHAIR: quantity must be positive, DESK: quantity must be positive])

    Applicative<EitherKind.Witness<List<String>>> eitherApplicative =
        Instances.applicative(either());
    Either<List<String>, List<LineItem>> firstError =
        EITHER
            .narrow(
                ListTraverse.INSTANCE.traverse(
                    eitherApplicative,
                    line -> EITHER.widen(checkLine(line).toEither()),
                    LIST.widen(ZERO_QUANTITIES)))
            .map(LIST::narrow);
    // Left([CHAIR: quantity must be positive])
    // ANCHOR_END: traverse
    System.out.println(everyError);
    System.out.println(firstError);

    // ANCHOR: sequence
    // Each SKU's price, looked up: the second SKU has none
    List<Kind<MaybeKind.Witness, BigDecimal>> prices =
        List.of(MAYBE.just(new BigDecimal("40.00")), MAYBE.nothing());
    Maybe<List<BigDecimal>> everyPrice =
        MAYBE
            .narrow(
                ListTraverse.INSTANCE.sequenceA(Instances.applicative(maybe()), LIST.widen(prices)))
            .map(LIST::narrow);
    // Nothing
    // ANCHOR_END: sequence
    System.out.println(everyPrice);
  }
}
