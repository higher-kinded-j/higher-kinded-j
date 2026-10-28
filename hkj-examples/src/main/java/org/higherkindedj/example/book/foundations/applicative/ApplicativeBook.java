// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.applicative;

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.math.BigDecimal;
import java.util.List;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/functional/applicative.html">Applicative</a> page:
 * a line item built from three values checked on their own, by one method written once over any
 * container, run with two instances that treat failure differently. The page {@code {{#include}}}s
 * the anchored regions, {@code ApplicativeBookTest} holds the claims the page makes, and the book's
 * output gate runs {@link #main} against the comments in the regions.
 */
public final class ApplicativeBook {

  private ApplicativeBook() {}

  // ANCHOR: checks
  // Three checks, each on one field, and none needs another's result
  static Validated<List<String>, String> checkSku(String sku) {
    return sku.isBlank() ? Validated.invalid(List.of("SKU is blank")) : Validated.valid(sku);
  }

  static Validated<List<String>, Integer> checkQuantity(int quantity) {
    return quantity > 0
        ? Validated.valid(quantity)
        : Validated.invalid(List.of("quantity must be positive"));
  }

  static Validated<List<String>, BigDecimal> checkPrice(BigDecimal price) {
    return price.signum() >= 0
        ? Validated.valid(price)
        : Validated.invalid(List.of("price is negative"));
  }

  // ANCHOR_END: checks

  // ANCHOR: generic
  // One method for any container F: the Applicative passed in decides what a failure does
  public static <F extends WitnessArity<TypeArity.Unary>> Kind<F, LineItem> lineItem(
      Applicative<F> applicative,
      Kind<F, String> sku,
      Kind<F, Integer> quantity,
      Kind<F, BigDecimal> price) {
    return applicative.map3(sku, quantity, price, LineItem::new);
  }

  // ANCHOR_END: generic

  public static void main(String[] args) {
    // ANCHOR: instances
    Applicative<ValidatedKind.Witness<List<String>>> validatedApplicative =
        Instances.validated(Semigroups.list());
    Validated<List<String>, LineItem> everyError =
        VALIDATED.narrow(
            lineItem(
                validatedApplicative,
                VALIDATED.widen(checkSku(" ")),
                VALIDATED.widen(checkQuantity(0)),
                VALIDATED.widen(checkPrice(new BigDecimal("40.00")))));
    // Invalid([SKU is blank, quantity must be positive])

    Applicative<EitherKind.Witness<List<String>>> eitherApplicative =
        Instances.applicative(either());
    Either<List<String>, LineItem> firstError =
        EITHER.narrow(
            lineItem(
                eitherApplicative,
                EITHER.widen(checkSku(" ").toEither()),
                EITHER.widen(checkQuantity(0).toEither()),
                EITHER.widen(checkPrice(new BigDecimal("40.00")).toEither())));
    // Left([SKU is blank])
    // ANCHOR_END: instances
    System.out.println(everyError);
    System.out.println(firstError);
  }
}
