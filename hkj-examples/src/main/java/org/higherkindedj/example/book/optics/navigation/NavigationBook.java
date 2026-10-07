// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.navigation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.higherkindedj.example.book.optics.cast.AddressFocus;
import org.higherkindedj.example.book.optics.cast.AddressLenses;
import org.higherkindedj.example.book.optics.cast.Card;
import org.higherkindedj.example.book.optics.cast.CardFocus;
import org.higherkindedj.example.book.optics.cast.Consignment;
import org.higherkindedj.example.book.optics.cast.ConsignmentFocus;
import org.higherkindedj.example.book.optics.cast.ConsignmentLenses;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.LineItemFocus;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderFocus;
import org.higherkindedj.example.book.optics.cast.OrderLenses;
import org.higherkindedj.example.book.optics.cast.Payment;
import org.higherkindedj.example.book.optics.cast.PaymentPrisms;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.each.EachInstances;
import org.higherkindedj.optics.extensions.EachExtensions;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.indexed.Pair;
import org.higherkindedj.optics.util.ListPrisms;
import org.higherkindedj.optics.util.Traversals;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/focus_navigation.html">Collections,
 * Optionals and Sealed Types</a> page. The page {@code {{#include}}}s the anchored regions, and
 * {@code NavigationBookTest} holds the claims the page makes about this code. The records are the
 * chapter's cast, with supporting types below named for their roles in the order service.
 */
public final class NavigationBook {

  private NavigationBook() {}

  static List<List<LineItem>> eachGenerated(Order order) {
    // ANCHOR: each_generated
    // Generated: FocusPath.of(lens).each()
    TraversalPath<Order, LineItem> allLines = OrderFocus.lines();
    List<LineItem> lines = allLines.getAll(order);

    // Applying .each() yourself, starting from the lens to the whole list
    TraversalPath<Order, LineItem> sameThing = FocusPath.of(OrderLenses.lines()).each();
    // ANCHOR_END: each_generated
    return List.of(lines, sameThing.getAll(order));
  }

  static List<Object> eachCustom(Catalogue catalogue, SavedForLater saved) {
    // ANCHOR: each_custom
    // A Map field: traverse the values
    TraversalPath<Catalogue, BigDecimal> allPrices =
        CatalogueFocus.prices().each(EachInstances.mapValuesEach());

    // An HKJ container held behind a hand-written lens
    Lens<SavedForLater, Maybe<LineItem>> itemLens =
        Lens.of(SavedForLater::item, (_, item) -> new SavedForLater(item));
    TraversalPath<SavedForLater, LineItem> savedItem =
        FocusPath.of(itemLens).each(EachExtensions.maybeEach());
    // ANCHOR_END: each_custom
    List<Object> found = new ArrayList<>(allPrices.getAll(catalogue));
    found.addAll(savedItem.getAll(saved));
    return found;
  }

  /** What the page's indexing block reads. */
  record Indexed(
      Optional<LineItem> first,
      AffinePath<Order, LineItem> alsoFirst,
      Optional<BigDecimal> lampPrice) {}

  static Indexed byIndex(Order order, Catalogue catalogue) {
    // ANCHOR: by_index
    // A List field: start from the lens, because the generated path is element-level
    AffinePath<Order, LineItem> firstLine = FocusPath.of(OrderLenses.lines()).at(0);
    Optional<LineItem> first = firstLine.getOptional(order); // empty if out of bounds

    // Or narrow the generated traversal to its first element. Mind the asymmetry:
    // headOption reads the first element but writes to all of them
    AffinePath<Order, LineItem> alsoFirst = OrderFocus.lines().headOption();

    // A Map field: the generated path still focuses the whole map, so .atKey() applies
    AffinePath<Catalogue, BigDecimal> lamp = CatalogueFocus.prices().atKey("LAMP");
    Optional<BigDecimal> lampPrice = lamp.getOptional(catalogue);
    // ANCHOR_END: by_index
    return new Indexed(first, alsoFirst, lampPrice);
  }

  static List<Optional<String>> nullable() {
    // ANCHOR: nullable
    FocusPath<LegacyContact, String> rawPath = LegacyContactFocus.nickname();
    AffinePath<LegacyContact, String> safePath = rawPath.nullable();

    Optional<String> missing = safePath.getOptional(new LegacyContact("Charles", null));
    Optional<String> present = safePath.getOptional(new LegacyContact("Grace", "Amazing Grace"));
    // ANCHOR_END: nullable
    return List.of(missing, present);
  }

  /** What the page's sealed-type block reads and writes. */
  record Variants(
      List<String> pans, PaymentHistory masked, TraversalPath<PaymentHistory, String> samePans) {}

  static Variants sealedVariants(PaymentHistory history) {
    // ANCHOR: sealed
    // A sealed type you own: @GeneratePrisms names each variant
    TraversalPath<PaymentHistory, String> cardPans =
        PaymentHistoryFocus.payments().via(PaymentPrisms.card()).via(CardFocus.pan());

    List<String> pans = cardPans.getAll(history); // the bank payments are skipped
    PaymentHistory masked =
        cardPans.modifyAll(pan -> "**** " + pan.substring(pan.length() - 4), history);

    // A sealed type you do not own: AffinePath.instanceOf matches by runtime type
    TraversalPath<PaymentHistory, String> samePans =
        PaymentHistoryFocus.payments().via(AffinePath.instanceOf(Card.class)).via(CardFocus.pan());
    // ANCHOR_END: sealed
    return new Variants(pans, masked, samePans);
  }

  /** The three paths the page's composition block builds. */
  record Composed(
      FocusPath<Consignment, String> street,
      AffinePath<Order, LineItem> firstLine,
      TraversalPath<Order, LineItem> allLines) {}

  static Composed composeExisting() {
    // ANCHOR: compose_existing
    // Path + Lens = Path
    FocusPath<Consignment, String> street =
        FocusPath.of(ConsignmentLenses.to()).via(AddressLenses.street());

    // Path + Prism or Affine = AffinePath
    AffinePath<Order, LineItem> firstLine =
        FocusPath.of(OrderLenses.lines()).via(ListPrisms.head());

    // Path + Traversal = TraversalPath
    TraversalPath<Order, LineItem> allLines =
        FocusPath.of(OrderLenses.lines()).via(Traversals.forList());
    // ANCHOR_END: compose_existing
    return new Composed(street, firstLine, allLines);
  }

  static List<String> withAndWithoutNavigators(Consignment consignment) {
    // ANCHOR: navigator_use
    // With navigators
    String city = ConsignmentFocus.to().city().get(consignment);

    // Without them, the same path, spelled out
    String same = FocusPath.of(ConsignmentLenses.to()).via(AddressFocus.city()).get(consignment);
    // ANCHOR_END: navigator_use
    return List.of(city, same);
  }

  /** What the page's navigator-or-via block reads. */
  record NavigatorOrVia(String email, List<String> skus) {}

  static NavigatorOrVia navigatorOrVia(Order order) {
    // ANCHOR: navigator_or_via
    // customer is a plain navigable field: navigator, so .email() chains
    String email = OrderFocus.customer().email().value().get(order);

    // lines is a List: a TraversalPath, so the next hop is .via()
    List<String> skus = OrderFocus.lines().via(LineItemFocus.sku()).getAll(order);
    // ANCHOR_END: navigator_or_via
    return new NavigatorOrVia(email, skus);
  }

  /** What the page's toPath block computes, and the postcodes its trace saw. */
  record Normalised(Consignment consignment, List<String> seen) {}

  static Normalised normalisePostcode(Consignment consignment) {
    // ANCHOR: to_path
    List<String> seen = new ArrayList<>();
    Consignment normalised =
        ConsignmentFocus.to()
            .toPath()
            .traced((_, address) -> seen.add(address.postcode()))
            .modify(
                address -> AddressLenses.postcode().modify(String::toUpperCase, address),
                consignment);
    // ANCHOR_END: to_path
    return new Normalised(normalised, seen);
  }

  /** What the page's SPI block reads and writes. */
  record Verified(Optional<String> name, Stockroom renamed, Stockroom untouched) {}

  static Verified verified(Stockroom stockroom) {
    // ANCHOR: spi_either
    // Either<String, String> field: the generated method already applies
    // .some(Affines.eitherRight()), focusing the Right value
    AffinePath<Stockroom, String> verified = StockroomFocus.verifiedName();

    Optional<String> name = verified.getOptional(stockroom); // empty for a Left
    Stockroom renamed =
        verified.set("Northern", stockroom); // replaces a Left with Right("Northern")
    Stockroom untouched = verified.modify(String::toUpperCase, stockroom); // a no-op on a Left
    // ANCHOR_END: spi_either
    return new Verified(name, renamed, untouched);
  }

  /** What the page's list-decomposition block reads. */
  record Decomposed(
      Optional<LineItem> first, Optional<LineItem> last, Optional<List<LineItem>> tail) {}

  static Decomposed decompose(Order order) {
    // ANCHOR: list_prisms
    FocusPath<Order, List<LineItem>> lines = FocusPath.of(OrderLenses.lines());

    AffinePath<Order, LineItem> firstLine = lines.via(ListPrisms.head());
    Optional<LineItem> first = firstLine.getOptional(order);

    AffinePath<Order, LineItem> lastLine = lines.via(ListPrisms.last());

    // Pattern match with cons (head, tail)
    AffinePath<Order, Pair<LineItem, List<LineItem>>> consPath = lines.via(ListPrisms.cons());
    Optional<List<LineItem>> tail = consPath.getOptional(order).map(Pair::second);
    // ANCHOR_END: list_prisms
    return new Decomposed(first, lastLine.getOptional(order), tail);
  }

  static List<Integer> spiWidening(Stockroom stockroom) {
    // ANCHOR: spi_widening
    // Either is ZERO_OR_ONE via the SPI: AffinePath
    AffinePath<Stockroom, String> verified = StockroomFocus.verifiedName();

    // Map is ZERO_OR_MORE via the SPI, but a static Focus method widens it only
    // under widenCollections; otherwise the path still focuses the whole map
    FocusPath<Stockroom, Map<String, Integer>> stock = StockroomFocus.stock();
    TraversalPath<Stockroom, Integer> quantities = stock.each(EachInstances.mapValuesEach());
    // ANCHOR_END: spi_widening
    return quantities.getAll(stockroom);
  }

  /** What the page's nested-container block reads. */
  record Nested(
      List<String> tagValues,
      Optional<String> innerValue,
      List<Integer> data,
      Optional<Map<String, Integer>> meta,
      List<Integer> hits) {}

  static Nested nested(NestedConfig nestedConfig, WidenedConfig widenedConfig) {
    // ANCHOR: nested
    TraversalPath<NestedConfig, String> allTags = NestedConfigFocus.tags();
    List<String> tagValues = allTags.getAll(nestedConfig);

    AffinePath<NestedConfig, String> nestedOpt = NestedConfigFocus.nested();
    Optional<String> innerValue = nestedOpt.getOptional(nestedConfig);

    // Either<String, List<Integer>>: a nested List is stepped into unconditionally
    TraversalPath<NestedConfig, Integer> data = NestedConfigFocus.data();

    // Either<String, Map<String, Integer>>: an SPI ZERO_OR_MORE stops at the Map...
    AffinePath<NestedConfig, Map<String, Integer>> meta = NestedConfigFocus.meta();

    // ...unless widenCollections is on
    TraversalPath<WidenedConfig, Integer> hits = WidenedConfigFocus.meta();
    // ANCHOR_END: nested
    return new Nested(
        tagValues,
        innerValue,
        data.getAll(nestedConfig),
        meta.getOptional(nestedConfig),
        hits.getAll(widenedConfig));
  }
}

// Supporting types, each named for its role beside the cast.

// ANCHOR: catalogue
@GenerateLenses
@GenerateFocus
record Catalogue(String name, Map<String, BigDecimal> prices) {}

// ANCHOR_END: catalogue

/**
 * A line a customer saved for later, as an HKJ Maybe. Unannotated, so a hand-written lens reaches
 * it.
 */
record SavedForLater(Maybe<LineItem> item) {}

// ANCHOR: legacy_contact
// From an older system: its nickname may be null, and nothing says so
@GenerateLenses
@GenerateFocus
record LegacyContact(String name, String nickname) {}

// ANCHOR_END: legacy_contact

// ANCHOR: payment_history
@GenerateFocus
record PaymentHistory(UUID customerId, List<Payment> payments) {}

// ANCHOR_END: payment_history

// ANCHOR: stockroom
// The stockroom an order is picked from: stock by SKU, and a name verified by a check
@GenerateLenses
@GenerateFocus(generateNavigators = true)
record Stockroom(String name, Map<String, Integer> stock, Either<String, String> verifiedName) {}

// ANCHOR_END: stockroom

// Width proofs for the nested-container table: the components are placeholders, with no role.

@GenerateLenses
@GenerateFocus
record NestedConfig(
    Optional<List<String>> tags,
    List<Optional<String>> items,
    Optional<Optional<String>> nested,
    List<List<String>> grid,
    Optional<Either<String, String>> approval,
    Either<String, List<Integer>> data,
    Either<String, Map<String, Integer>> meta) {}

@GenerateLenses
@GenerateFocus(widenCollections = true)
record WidenedConfig(Either<String, Map<String, Integer>> meta) {}
