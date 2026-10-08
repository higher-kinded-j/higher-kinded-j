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

  /** The profile most of the page's lens examples read and write. */
  static final UserProfile PROFILE =
      new UserProfile("u1", "Alice", "alice@example.com", 30, "Software Engineer");

  /** The order lines the page's traversal examples read and write. */
  static final List<OrderItem> ITEMS =
      List.of(
          new OrderItem("SKU-1", new BigDecimal("999.99"), 1, "pending"),
          new OrderItem("SKU-2", new BigDecimal("29.99"), 2, "shipped"));

  /** Every line's price, as the page's traversal examples compose it. */
  static final Traversal<List<OrderItem>, BigDecimal> ALL_PRICES =
      Traversals.<OrderItem>forList().andThen(OrderItemLenses.price());

  private ExtensionsBook() {}

  static List<Object> getMaybeExample() {
    // ANCHOR: get_maybe
    Lens<UserProfile, String> bioLens = UserProfileLenses.bio();

    UserProfile withBio =
        new UserProfile("u1", "Alice", "alice@example.com", 30, "Software Engineer");
    Maybe<String> bio = getMaybe(bioLens, withBio);
    // Just(Software Engineer)

    UserProfile withoutBio = new UserProfile("u2", "Bob", "bob@example.com", 25, null);
    Maybe<String> noBio = getMaybe(bioLens, withoutBio);
    // Nothing

    // Use with default
    String displayBio = bio.orElse("No bio provided");
    // ANCHOR_END: get_maybe
    return List.of(bio, noBio, displayBio);
  }

  static List<Object> getEitherExample(UserProfile profile) {
    // ANCHOR: get_either
    Lens<UserProfile, Integer> ageLens = UserProfileLenses.age();

    Either<String, Integer> age = getEither(ageLens, "Age not provided", profile);
    // Right(30)

    UserProfile ageUnknown = new UserProfile("u3", "Carol", "carol@example.com", null, null);
    Either<String, Integer> noAge = getEither(ageLens, "Age not provided", ageUnknown);
    // Left(Age not provided)

    String message = age.fold(error -> "AppError: " + error, a -> "Age: " + a);
    // ANCHOR_END: get_either
    return List.of(age, noAge, message);
  }

  static List<Validated<String, String>> getValidatedExample(UserProfile profile) {
    // ANCHOR: get_validated
    Lens<UserProfile, String> emailLens = UserProfileLenses.email();

    Validated<String, String> email = getValidated(emailLens, "Email is required", profile);
    // Valid(alice@example.com)

    UserProfile noEmail = new UserProfile("u4", "Dan", null, 41, null);
    Validated<String, String> missing = getValidated(emailLens, "Email is required", noEmail);
    // Invalid(Email is required)
    // ANCHOR_END: get_validated
    return List.of(email, missing);
  }

  static Maybe<UserProfile> modifyMaybeExample(UserProfile profile) {
    // ANCHOR: modify_maybe
    Lens<UserProfile, String> nameLens = UserProfileLenses.name();

    Maybe<UserProfile> updated =
        modifyMaybe(
            nameLens,
            name -> name.length() >= 2 ? Maybe.just(name.toUpperCase()) : Maybe.nothing(),
            profile);
    // Just(UserProfile[id=u1, name=ALICE, ...]); a name shorter than two letters gives Nothing
    // ANCHOR_END: modify_maybe
    return updated;
  }

  static List<Maybe<List<BigDecimal>>> getAllMaybeExample(List<OrderItem> items) {
    // ANCHOR: get_all_maybe
    Lens<OrderItem, BigDecimal> priceLens = OrderItemLenses.price();
    Traversal<List<OrderItem>, BigDecimal> allPrices =
        Traversals.<OrderItem>forList().andThen(priceLens);

    Maybe<List<BigDecimal>> prices = getAllMaybe(allPrices, items);
    // Just([999.99, 29.99])

    Maybe<List<BigDecimal>> noPrices = getAllMaybe(allPrices, List.of());
    // Nothing
    // ANCHOR_END: get_all_maybe
    return List.of(prices, noPrices);
  }

  static List<Maybe<List<OrderItem>>> modifyAllMaybeExample(
      Traversal<List<OrderItem>, BigDecimal> allPrices, List<OrderItem> items) {
    // ANCHOR: modify_all_maybe
    Function<BigDecimal, Maybe<BigDecimal>> raiseTenPercent =
        price ->
            price.compareTo(new BigDecimal("10")) >= 0
                ? Maybe.just(
                    price.multiply(new BigDecimal("1.1")).setScale(2, RoundingMode.HALF_EVEN))
                : Maybe.nothing();

    Maybe<List<OrderItem>> updated = modifyAllMaybe(allPrices, raiseTenPercent, items);
    // Just([OrderItem[sku=SKU-1, price=1099.99, ...], OrderItem[sku=SKU-2, price=32.99, ...]])

    List<OrderItem> withACheapItem =
        List.of(items.get(0), new OrderItem("SKU-3", new BigDecimal("4.99"), 3, "pending"));
    Maybe<List<OrderItem>> refused = modifyAllMaybe(allPrices, raiseTenPercent, withACheapItem);
    // Nothing: 4.99 is under 10, so no price changes
    // ANCHOR_END: modify_all_maybe
    return List.of(updated, refused);
  }

  static Either<String, List<OrderItem>> modifyAllEitherExample(
      Traversal<List<OrderItem>, BigDecimal> allPrices) {
    // ANCHOR: modify_all_either
    List<OrderItem> withRefunds =
        List.of(
            new OrderItem("SKU-1", new BigDecimal("999.99"), 1, "pending"),
            new OrderItem("SKU-4", new BigDecimal("-5.00"), 1, "refund"),
            new OrderItem("SKU-5", new BigDecimal("-1.50"), 1, "refund"));

    Either<String, List<OrderItem>> result =
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

@GenerateLenses
record UserProfile(String id, String name, String email, Integer age, String bio) {}

@GenerateLenses
record OrderItem(String sku, BigDecimal price, int quantity, String status) {}
