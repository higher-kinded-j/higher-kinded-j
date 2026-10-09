// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.extensions;

import static org.higherkindedj.optics.extensions.LensExtensions.getEither;
import static org.higherkindedj.optics.extensions.LensExtensions.getMaybe;
import static org.higherkindedj.optics.extensions.LensExtensions.getValidated;
import static org.higherkindedj.optics.extensions.LensExtensions.modifyMaybe;
import static org.higherkindedj.optics.extensions.TraversalExtensions.getAllMaybe;
import static org.higherkindedj.optics.extensions.TraversalExtensions.modifyAllEither;
import static org.higherkindedj.optics.extensions.TraversalExtensions.modifyAllMaybe;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.LineItemLenses;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.util.Traversals;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/optics_extensions.html">Optics
 * Extensions</a> page. The page {@code {{#include}}}s the anchored regions, and {@code
 * ExtensionsBookTest} holds the claims the page makes about this code.
 */
public final class ExtensionsBook {

  /** The affiliate most of the page's lens examples read and write. */
  static final Affiliate AFFILIATE =
      new Affiliate("a1", "Alice", "alice@example.com", 10, "Lighting blogger");

  /** The order lines the page's traversal examples read and write: the chapter cast's. */
  static final List<LineItem> ITEMS =
      List.of(
          new LineItem("SKU-1", 1, new BigDecimal("999.99")),
          new LineItem("SKU-2", 2, new BigDecimal("29.99")));

  /** Every line's price, as the page's traversal examples compose it. */
  static final Traversal<List<LineItem>, BigDecimal> ALL_PRICES =
      Traversals.<LineItem>forList().andThen(LineItemLenses.price());

  private ExtensionsBook() {}

  static List<Object> getMaybeExample() {
    // ANCHOR: get_maybe
    Lens<Affiliate, String> bioLens = AffiliateLenses.bio();

    Affiliate withBio = new Affiliate("a1", "Alice", "alice@example.com", 10, "Lighting blogger");
    Maybe<String> bio = getMaybe(bioLens, withBio);
    // Just(Lighting blogger)

    Affiliate withoutBio = new Affiliate("a2", "Bob", "bob@example.com", 5, null);
    Maybe<String> noBio = getMaybe(bioLens, withoutBio);
    // Nothing

    // Use with default
    String displayBio = bio.orElse("No bio provided");
    // ANCHOR_END: get_maybe
    return List.of(bio, noBio, displayBio);
  }

  static List<Object> getEitherExample(Affiliate affiliate) {
    // ANCHOR: get_either
    Lens<Affiliate, Integer> commissionLens = AffiliateLenses.commission();

    Either<String, Integer> commission =
        getEither(commissionLens, "Commission not agreed", affiliate);
    // Right(10)

    Affiliate newcomer = new Affiliate("a3", "Carol", "carol@example.com", null, null);
    Either<String, Integer> noCommission =
        getEither(commissionLens, "Commission not agreed", newcomer);
    // Left(Commission not agreed)

    String message = commission.fold(error -> "AppError: " + error, c -> "Commission: " + c + "%");
    // ANCHOR_END: get_either
    return List.of(commission, noCommission, message);
  }

  static List<Validated<String, String>> getValidatedExample(Affiliate affiliate) {
    // ANCHOR: get_validated
    Lens<Affiliate, String> emailLens = AffiliateLenses.email();

    Validated<String, String> email = getValidated(emailLens, "Email is required", affiliate);
    // Valid(alice@example.com)

    Affiliate noEmail = new Affiliate("a4", "Dan", null, 8, null);
    Validated<String, String> missing = getValidated(emailLens, "Email is required", noEmail);
    // Invalid(Email is required)
    // ANCHOR_END: get_validated
    return List.of(email, missing);
  }

  static Maybe<Affiliate> modifyMaybeExample(Affiliate affiliate) {
    // ANCHOR: modify_maybe
    Lens<Affiliate, String> nameLens = AffiliateLenses.name();

    Maybe<Affiliate> updated =
        modifyMaybe(
            nameLens,
            name -> name.length() >= 2 ? Maybe.just(name.toUpperCase()) : Maybe.nothing(),
            affiliate);
    // Just(Affiliate[id=a1, name=ALICE, ...]); a name shorter than two letters gives Nothing
    // ANCHOR_END: modify_maybe
    return updated;
  }

  static List<Maybe<List<BigDecimal>>> getAllMaybeExample(List<LineItem> items) {
    // ANCHOR: get_all_maybe
    Lens<LineItem, BigDecimal> priceLens = LineItemLenses.price();
    Traversal<List<LineItem>, BigDecimal> allPrices =
        Traversals.<LineItem>forList().andThen(priceLens);

    Maybe<List<BigDecimal>> prices = getAllMaybe(allPrices, items);
    // Just([999.99, 29.99])

    Maybe<List<BigDecimal>> noPrices = getAllMaybe(allPrices, List.of());
    // Nothing
    // ANCHOR_END: get_all_maybe
    return List.of(prices, noPrices);
  }

  static List<Maybe<List<LineItem>>> modifyAllMaybeExample(
      Traversal<List<LineItem>, BigDecimal> allPrices, List<LineItem> items) {
    // ANCHOR: modify_all_maybe
    Function<BigDecimal, Maybe<BigDecimal>> raiseTenPercent =
        price ->
            price.compareTo(new BigDecimal("10")) >= 0
                ? Maybe.just(
                    price.multiply(new BigDecimal("1.1")).setScale(2, RoundingMode.HALF_EVEN))
                : Maybe.nothing();

    Maybe<List<LineItem>> updated = modifyAllMaybe(allPrices, raiseTenPercent, items);
    // Just([LineItem[sku=SKU-1, quantity=1, price=1099.99],
    //       LineItem[sku=SKU-2, quantity=2, price=32.99]])

    List<LineItem> withACheapItem =
        List.of(items.get(0), new LineItem("SKU-3", 3, new BigDecimal("4.99")));
    Maybe<List<LineItem>> refused = modifyAllMaybe(allPrices, raiseTenPercent, withACheapItem);
    // Nothing: 4.99 is under 10, so no price changes
    // ANCHOR_END: modify_all_maybe
    return List.of(updated, refused);
  }

  static Either<String, List<LineItem>> modifyAllEitherExample(
      Traversal<List<LineItem>, BigDecimal> allPrices) {
    // ANCHOR: modify_all_either
    List<LineItem> withRefunds =
        List.of(
            new LineItem("SKU-1", 1, new BigDecimal("999.99")),
            new LineItem("SKU-4", 1, new BigDecimal("-5.00")),
            new LineItem("SKU-5", 1, new BigDecimal("-1.50")));

    Either<String, List<LineItem>> result =
        modifyAllEither(
            allPrices,
            price -> {
              if (price.compareTo(BigDecimal.ZERO) < 0) {
                return Either.left("Price cannot be negative: " + price);
              }
              return Either.right(price);
            },
            withRefunds);
    // Left(Price cannot be negative: -5.00): the first failure wins,
    // though every price is checked
    // ANCHOR_END: modify_all_either
    return result;
  }
}

// ANCHOR: affiliate
// A partner paid a commission, in percent, on the orders they refer. A newcomer may not yet have
// given an email, agreed a commission or written a bio, so those fields may be null.
@GenerateLenses
record Affiliate(String id, String name, String email, Integer commission, String bio) {}

// ANCHOR_END: affiliate
