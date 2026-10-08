// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.cookbook;

import static org.higherkindedj.optics.edit.Edit.parseIfPresent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.higherkindedj.example.book.optics.cast.Card;
import org.higherkindedj.example.book.optics.cast.CardFocus;
import org.higherkindedj.example.book.optics.cast.Consignment;
import org.higherkindedj.example.book.optics.cast.ConsignmentFocus;
import org.higherkindedj.example.book.optics.cast.ConsignmentLenses;
import org.higherkindedj.example.book.optics.cast.ConsignmentState;
import org.higherkindedj.example.book.optics.cast.ConsignmentStatePrisms;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.CustomerFocus;
import org.higherkindedj.example.book.optics.cast.CustomerLenses;
import org.higherkindedj.example.book.optics.cast.CustomerProfile;
import org.higherkindedj.example.book.optics.cast.CustomerProfileFocus;
import org.higherkindedj.example.book.optics.cast.CustomerProfileLenses;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.EmailAddressFocus;
import org.higherkindedj.example.book.optics.cast.EmailAddressLenses;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.LineItemFocus;
import org.higherkindedj.example.book.optics.cast.LineItemLenses;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderFocus;
import org.higherkindedj.example.book.optics.cast.OrderLenses;
import org.higherkindedj.example.book.optics.cast.OrderTraversals;
import org.higherkindedj.example.book.optics.cast.Payment;
import org.higherkindedj.example.book.optics.cast.PaymentPrisms;
import org.higherkindedj.example.book.optics.cast.ReturnedFocus;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.edit.Edits;
import org.higherkindedj.optics.fluent.OpticOps;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.FocusPaths;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.util.Prisms;
import org.higherkindedj.optics.util.Traversals;
import org.jspecify.annotations.Nullable;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/cookbook.html">Optics Cookbook</a>. The
 * page {@code {{#include}}}s the anchored regions, and {@code CookbookBookTest} holds the claims
 * the page makes about this code. The records are the chapter's cast, with supporting types below
 * named for their roles in the order service.
 *
 * <p>Each recipe has a Focus DSL region and, where the page shows one, a {@code _raw} region that
 * reaches the same result with the generated lenses, prisms and traversals.
 */
public final class CookbookBook {

  private CookbookBook() {}

  // ----- Recipe 1: a field several records deep -----

  static Order changeEmail(Order order) {
    // ANCHOR: deep_field
    Order updated = OrderFocus.customer().email().value().set("ada@example.org", order);
    // ANCHOR_END: deep_field
    return updated;
  }

  static Order changeEmailRaw(Order order) {
    // ANCHOR: deep_field_raw
    Lens<Order, String> email =
        OrderLenses.customer().andThen(CustomerLenses.email()).andThen(EmailAddressLenses.value());

    Order updated = email.set("ada@example.org", order);
    // ANCHOR_END: deep_field_raw
    return updated;
  }

  // ----- Recipe 2: a field inside an Optional -----

  /** What the optional-field recipe computes, so the test can read each value. */
  record OptionalField(CustomerProfile lowered, CustomerProfile untouched) {}

  static OptionalField lowerAltEmail(CustomerProfile profile, CustomerProfile withoutAltEmail) {
    // ANCHOR: optional_field
    AffinePath<CustomerProfile, String> altEmail =
        CustomerProfileFocus.altEmail().via(EmailAddressFocus.value());

    CustomerProfile lowered = altEmail.modify(String::toLowerCase, profile);

    // With no alternative email, the profile comes back unchanged
    CustomerProfile untouched = altEmail.modify(String::toLowerCase, withoutAltEmail);
    // ANCHOR_END: optional_field
    return new OptionalField(lowered, untouched);
  }

  static CustomerProfile lowerAltEmailRaw(CustomerProfile profile) {
    // ANCHOR: optional_field_raw
    Affine<CustomerProfile, String> altEmail =
        CustomerProfileLenses.altEmail()
            .andThen(Prisms.<EmailAddress>some())
            .andThen(EmailAddressLenses.value());

    CustomerProfile lowered = altEmail.modify(String::toLowerCase, profile);
    // ANCHOR_END: optional_field_raw
    return lowered;
  }

  // ----- Recipe 3: every element of a list inside a list -----

  static OrderHistory roundEveryPrice(OrderHistory history) {
    // ANCHOR: every_element
    TraversalPath<OrderHistory, BigDecimal> prices =
        OrderHistoryFocus.orders().via(OrderFocus.lines()).via(LineItemFocus.price());

    OrderHistory rounded =
        prices.modifyAll(price -> price.setScale(2, RoundingMode.HALF_EVEN), history);
    // ANCHOR_END: every_element
    return rounded;
  }

  static OrderHistory roundEveryPriceRaw(OrderHistory history) {
    // ANCHOR: every_element_raw
    Traversal<OrderHistory, BigDecimal> prices =
        OrderHistoryTraversals.orders()
            .andThen(OrderTraversals.lines())
            .andThen(LineItemLenses.price());

    OrderHistory rounded =
        Traversals.modify(prices, price -> price.setScale(2, RoundingMode.HALF_EVEN), history);
    // ANCHOR_END: every_element_raw
    return rounded;
  }

  // ----- Recipe 4: only the elements that match -----

  /** What the matching-elements recipe computes. */
  record Matching(Order discounted, Order doubled) {}

  static Matching bulkLines(Order order) {
    // ANCHOR: matching
    // Ten per cent off every line of four or more
    Order discounted =
        OrderFocus.lines()
            .filter(line -> line.quantity() >= 4)
            .via(LineItemFocus.price())
            .modifyAll(price -> price.multiply(new BigDecimal("0.90")), order);

    // The same test, with a change to the whole line
    Order doubled =
        OrderFocus.lines()
            .modifyWhen(
                line -> line.quantity() >= 4,
                line -> LineItemFocus.quantity().modify(quantity -> quantity * 2, line),
                order);
    // ANCHOR_END: matching
    return new Matching(discounted, doubled);
  }

  static Order bulkLinesRaw(Order order) {
    // ANCHOR: matching_raw
    Traversal<Order, BigDecimal> bulkPrices =
        OrderTraversals.lines()
            .filtered(line -> line.quantity() >= 4)
            .andThen(LineItemLenses.price());

    Order discounted =
        Traversals.modify(bulkPrices, price -> price.multiply(new BigDecimal("0.90")), order);
    // ANCHOR_END: matching_raw
    return discounted;
  }

  // ----- Recipe 5: one variant of a sealed type -----

  /** What the one-variant recipe computes. */
  record OneVariant(Consignment tidied, Consignment untouched) {}

  static OneVariant tidyReturnReason(Consignment returned, Consignment pending) {
    // ANCHOR: one_variant
    AffinePath<Consignment, String> returnReason =
        ConsignmentFocus.state().via(ConsignmentStatePrisms.returned()).via(ReturnedFocus.reason());

    Consignment tidied = returnReason.modify(String::strip, returned);

    // A consignment in any other state comes back unchanged
    Consignment untouched = returnReason.modify(String::strip, pending);
    // ANCHOR_END: one_variant
    return new OneVariant(tidied, untouched);
  }

  static Consignment tidyReturnReasonRaw(Consignment consignment) {
    // ANCHOR: one_variant_raw
    Affine<Consignment, ConsignmentState.Returned> returnedState =
        ConsignmentLenses.state().andThen(ConsignmentStatePrisms.returned());

    // Returned has no generated lenses, so the function rebuilds the variant
    Consignment tidied =
        returnedState.modify(
            state -> new ConsignmentState.Returned(state.reason().strip()), consignment);
    // ANCHOR_END: one_variant_raw
    return tidied;
  }

  // ----- Recipe 6: one variant in a list of mixed types -----

  /** What the variants-in-a-list recipe computes. */
  record Variants(List<String> pans, PaymentHistory masked) {}

  static Variants cardNumbers(PaymentHistory history) {
    // ANCHOR: variants_in_list
    TraversalPath<PaymentHistory, String> cardNumbers =
        PaymentHistoryFocus.payments().via(PaymentPrisms.card()).via(CardFocus.pan());

    // The bank payments are skipped
    List<String> pans = cardNumbers.getAll(history);

    PaymentHistory masked =
        cardNumbers.modifyAll(pan -> "**** " + pan.substring(pan.length() - 4), history);
    // ANCHOR_END: variants_in_list
    return new Variants(pans, masked);
  }

  static List<String> cardNumbersRaw(PaymentHistory history) {
    // ANCHOR: variants_in_list_raw
    // Card carries no @GenerateLenses, so its lens is written by hand
    Lens<Card, String> pan = Lens.of(Card::pan, (_, newPan) -> new Card(newPan));

    Traversal<PaymentHistory, String> cardNumbers =
        PaymentHistoryTraversals.payments().andThen(PaymentPrisms.card()).andThen(pan);

    List<String> pans = Traversals.getAll(cardNumbers, history);
    // ANCHOR_END: variants_in_list_raw
    return pans;
  }

  // ----- Recipe 7: a map entry, with a fallback -----

  /** What the map-entry recipe computes. */
  record MapEntry(int desksInStock, Stockroom picked, Stockroom stocked) {}

  static MapEntry stockLevels(Stockroom stockroom) {
    // ANCHOR: map_entry
    AffinePath<Stockroom, Integer> desks = StockroomFocus.stock().atKey("DESK");
    AffinePath<Stockroom, Integer> lamps = StockroomFocus.stock().atKey("LAMP");

    // A missing key reads as the fallback
    int desksInStock = desks.getOrElse(0, stockroom);

    // modify changes a key that is there, and leaves a missing one alone
    Stockroom picked = lamps.modify(count -> count - 1, stockroom);

    // set adds a missing key
    Stockroom stocked = desks.set(5, stockroom);
    // ANCHOR_END: map_entry
    return new MapEntry(desksInStock, picked, stocked);
  }

  static int desksInStockRaw(Stockroom stockroom) {
    // ANCHOR: map_entry_raw
    Affine<Stockroom, Integer> desks = StockroomLenses.stock().andThen(FocusPaths.mapAt("DESK"));

    int desksInStock = desks.getOptional(stockroom).orElse(0);
    // ANCHOR_END: map_entry_raw
    return desksInStock;
  }

  // ----- Recipe 8: the value inside an Either field -----

  /** What the Either-field recipe computes. */
  record EitherField(Stockroom tidied, Stockroom untouched) {}

  static EitherField tidyVerifiedName(Stockroom verified, Stockroom unverified) {
    // ANCHOR: either_field
    AffinePath<Stockroom, String> verifiedName = StockroomFocus.verifiedName();

    // A Right is changed
    Stockroom tidied = verifiedName.modify(String::strip, verified);

    // A Left comes back unchanged
    Stockroom untouched = verifiedName.modify(String::strip, unverified);
    // ANCHOR_END: either_field
    return new EitherField(tidied, untouched);
  }

  static Stockroom tidyVerifiedNameRaw(Stockroom verified) {
    // ANCHOR: either_field_raw
    Affine<Stockroom, String> verifiedName =
        StockroomLenses.verifiedName().andThen(Prisms.<String, String>right());

    Stockroom tidied = verifiedName.modify(String::strip, verified);
    // ANCHOR_END: either_field_raw
    return tidied;
  }

  // ----- Recipe 9: a PATCH that reports every bad field -----

  static Validated<NonEmptyList<FieldError>, Customer> applyPatch(
      Customer customer, CustomerPatch patch) {
    // ANCHOR: patch
    Validated<NonEmptyList<FieldError>, Customer> patched =
        Edits.accumulate(
                parseIfPresent(CustomerFocus.name(), patch.name(), Names::parse),
                parseIfPresent(CustomerFocus.email().toPath(), patch.email(), Emails::parse))
            .apply(customer);
    // ANCHOR_END: patch
    return patched;
  }

  static Validated<NonEmptyList<FieldError>, Customer> applyPatchRaw(
      Customer customer, CustomerPatch patch) {
    // ANCHOR: patch_raw
    // A path built from a lens carries no label, so at(...) names each field
    Validated<NonEmptyList<FieldError>, Customer> patched =
        Edits.accumulate(
                parseIfPresent(FocusPath.of(CustomerLenses.name()), patch.name(), Names::parse)
                    .at("name"),
                parseIfPresent(FocusPath.of(CustomerLenses.email()), patch.email(), Emails::parse)
                    .at("email"))
            .apply(customer);
    // ANCHOR_END: patch_raw
    return patched;
  }

  // ----- Recipe 10: check the values a path reaches -----

  /** What the check-values recipe computes. */
  record Checked(Validated<List<String>, Order> every, Either<String, Order> first) {}

  static Checked checkQuantities(Order order) {
    // ANCHOR: check_values
    Traversal<Order, Integer> quantities =
        OrderFocus.lines().via(LineItemFocus.quantity()).toTraversal();

    // Every bad quantity, reported together
    Validated<List<String>, Order> checked =
        OpticOps.modifyAllValidated(order, quantities, Quantities::check);

    // Or keep only the first
    Either<String, Order> firstFailure =
        OpticOps.modifyAllEither(
            order, quantities, quantity -> Quantities.check(quantity).toEither());
    // ANCHOR_END: check_values
    return new Checked(checked, firstFailure);
  }

  static Validated<List<String>, Order> checkQuantitiesRaw(Order order) {
    // ANCHOR: check_values_raw
    Traversal<Order, Integer> quantities =
        OrderTraversals.lines().andThen(LineItemLenses.quantity());

    Validated<List<String>, Order> checked =
        OpticOps.modifyAllValidated(order, quantities, Quantities::check);
    // ANCHOR_END: check_values_raw
    return checked;
  }

  // ----- Recipe 11: total, count or test the values a path reaches -----

  /** What the totals recipe computes. */
  record Totals(
      int items, boolean anyBulk, boolean noEmptyLines, int lineCount, BigDecimal total) {}

  static Totals totals(Order order) {
    // ANCHOR: totals
    TraversalPath<Order, Integer> quantities = OrderFocus.lines().via(LineItemFocus.quantity());

    int items = quantities.foldMap(Monoids.integerAddition(), quantity -> quantity, order);
    boolean anyBulk = quantities.exists(quantity -> quantity >= 4, order);
    boolean noEmptyLines = quantities.all(quantity -> quantity >= 1, order);
    int lineCount = OrderFocus.lines().count(order);

    // A money total over one list field is plain Java: Monoids offers no BigDecimal sum
    BigDecimal total =
        order.lines().stream()
            .map(line -> line.price().multiply(BigDecimal.valueOf(line.quantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    // ANCHOR_END: totals
    return new Totals(items, anyBulk, noEmptyLines, lineCount, total);
  }

  /** What the raw totals compute. */
  record RawTotals(int items, boolean anyBulk, int lineCount) {}

  static RawTotals totalsRaw(Order order) {
    // ANCHOR: totals_raw
    Fold<Order, Integer> quantities =
        OrderTraversals.lines().andThen(LineItemLenses.quantity()).asFold();

    int items = quantities.foldMap(Monoids.integerAddition(), quantity -> quantity, order);
    boolean anyBulk = quantities.exists(quantity -> quantity >= 4, order);
    int lineCount = quantities.length(order);
    // ANCHOR_END: totals_raw
    return new RawTotals(items, anyBulk, lineCount);
  }

  // ----- Recipe 12: values from several places -----

  /** What the several-places recipe computes. */
  record SeveralPlaces(List<String> names, boolean known, List<String> searchTerms) {}

  static SeveralPlaces names(CustomerProfile profile) {
    // ANCHOR: several_places
    // The names a profile answers to: always a name, sometimes a nickname
    Fold<CustomerProfile, String> names =
        CustomerProfileFocus.name().asFold().plus(CustomerProfileFocus.nickname().asFold());

    List<String> all = names.getAll(profile);
    boolean known = names.exists(name -> name.equalsIgnoreCase("countess"), profile);

    // Three or more at once
    Fold<CustomerProfile, String> searchTerms =
        Fold.sum(
            CustomerProfileFocus.name().asFold(),
            CustomerProfileFocus.nickname().asFold(),
            CustomerProfileFocus.altEmail().via(EmailAddressFocus.value()).asFold());
    // ANCHOR_END: several_places
    return new SeveralPlaces(all, known, searchTerms.getAll(profile));
  }

  static List<String> namesRaw(CustomerProfile profile) {
    // ANCHOR: several_places_raw
    Fold<CustomerProfile, String> names =
        CustomerProfileLenses.name()
            .asFold()
            .plus(CustomerProfileLenses.nickname().andThen(Prisms.<String>some()).asFold());
    // ANCHOR_END: several_places_raw
    return names.getAll(profile);
  }

  // ----- Recipe 13: sort or reverse what a traversal reaches -----

  /** What the sort recipe computes. */
  record Sorted(Order byPrice, Order reversed) {}

  static Sorted sortLines(Order order) {
    // ANCHOR: sort
    Order byPrice =
        Traversals.sorted(OrderTraversals.lines(), Comparator.comparing(LineItem::price), order);

    Order reversed = Traversals.reversed(OrderTraversals.lines(), order);
    // ANCHOR_END: sort
    return new Sorted(byPrice, reversed);
  }

  // ----- Recipe 14: see what a path reads -----

  /** What the trace recipe reads, the path it traced, and what the observer saw. */
  record Traced(List<String> read, TraversalPath<Order, String> skus, List<String> log) {}

  static Traced traceSkus(Order order) {
    // ANCHOR: trace
    // In a service, the observer would call your logger
    List<String> log = new ArrayList<>();
    TraversalPath<Order, String> skus =
        OrderFocus.lines()
            .via(LineItemFocus.sku())
            .traced((_, found) -> log.add("read " + found.size() + " SKUs: " + found));

    List<String> read = skus.getAll(order);
    // the log now holds "read 2 SKUs: [LAMP, BULB]"
    // ANCHOR_END: trace
    return new Traced(read, skus, log);
  }
}

// Supporting types, each named for its role beside the cast.

// ANCHOR: order_history
// A customer's past orders: a list of orders, each holding a list of lines
@GenerateFocus
@GenerateTraversals
record OrderHistory(List<Order> orders) {}

// ANCHOR_END: order_history

// ANCHOR: payment_history
// The Collections, Optionals and Sealed Types page's payment history
@GenerateFocus
@GenerateTraversals
record PaymentHistory(UUID customerId, List<Payment> payments) {}

// ANCHOR_END: payment_history

// ANCHOR: stockroom
// The stockroom an order is picked from: stock by SKU, and a name verified by a check
@GenerateLenses
@GenerateFocus(generateNavigators = true)
record Stockroom(String name, Map<String, Integer> stock, Either<String, String> verifiedName) {}

// ANCHOR_END: stockroom

// ANCHOR: customer_patch
/** A sparse PATCH of a customer: a null component means "not supplied", not "set to null". */
record CustomerPatch(@Nullable String name, @Nullable String email) {}

/** The boundary parsers the recipe hands to {@code parseIfPresent}. */
final class Names {
  static Validated<NonEmptyList<FieldError>, String> parse(String raw) {
    String name = raw.strip();
    return name.isEmpty()
        ? Validated.invalidNel(FieldError.of("must not be blank"))
        : Validated.validNel(name);
  }

  private Names() {}
}

final class Emails {
  static Validated<NonEmptyList<FieldError>, EmailAddress> parse(String raw) {
    String email = raw.strip();
    return email.contains("@")
        ? Validated.validNel(new EmailAddress(email))
        : Validated.invalidNel(FieldError.of("not an address"));
  }

  private Emails() {}
}

// ANCHOR_END: customer_patch

// ANCHOR: check_quantity
/** The check the check-values recipe runs on every quantity. */
final class Quantities {
  static Validated<String, Integer> check(Integer quantity) {
    return quantity >= 1 ? Validated.valid(quantity) : Validated.invalid("No items: " + quantity);
  }

  private Quantities() {}
}

// ANCHOR_END: check_quantity
