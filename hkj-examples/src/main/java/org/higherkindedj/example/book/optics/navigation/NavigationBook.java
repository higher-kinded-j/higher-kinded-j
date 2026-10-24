// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.navigation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.higherkindedj.example.book.optics.cast.Address;
import org.higherkindedj.example.book.optics.cast.AddressFocus;
import org.higherkindedj.example.book.optics.cast.AddressLenses;
import org.higherkindedj.example.book.optics.cast.Card;
import org.higherkindedj.example.book.optics.cast.CardFocus;
import org.higherkindedj.example.book.optics.cast.Consignment;
import org.higherkindedj.example.book.optics.cast.ConsignmentFocus;
import org.higherkindedj.example.book.optics.cast.ConsignmentLenses;
import org.higherkindedj.example.book.optics.cast.LineItem;
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
import org.higherkindedj.optics.focus.FocusPaths;
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
    // Generated: FocusPath.of(lens).each(EachInstances.listEach())
    TraversalPath<Order, LineItem> allLines = OrderFocus.lines();
    List<LineItem> lines = allLines.getAll(order);

    // Applying the Each yourself, starting from the lens to the whole list
    TraversalPath<Order, LineItem> sameThing =
        FocusPath.of(OrderLenses.lines()).each(EachInstances.listEach());
    // ANCHOR_END: each_generated
    return List.of(lines, sameThing.getAll(order));
  }

  static List<Object> eachCustom(PriceList priceList, SavedForLater saved) {
    // ANCHOR: each_custom
    // A Map field: traverse the values
    TraversalPath<PriceList, BigDecimal> allPrices =
        PriceListFocus.prices().each(EachInstances.mapValuesEach());

    // An HKJ container held behind a hand-written lens
    Lens<SavedForLater, Maybe<LineItem>> itemLens =
        Lens.of(SavedForLater::item, (_, item) -> new SavedForLater(item));
    TraversalPath<SavedForLater, LineItem> savedItem =
        FocusPath.of(itemLens).each(EachExtensions.maybeEach());
    // ANCHOR_END: each_custom
    List<Object> found = new ArrayList<>(allPrices.getAll(priceList));
    found.addAll(savedItem.getAll(saved));
    return found;
  }

  /** What the page's indexing block reads. */
  record Indexed(
      Optional<LineItem> first,
      AffinePath<Order, LineItem> alsoFirst,
      Optional<BigDecimal> lampPrice) {}

  static Indexed byIndex(Order order, PriceList priceList) {
    // ANCHOR: by_index
    // A List field: start from the lens, because the generated path is element-level
    AffinePath<Order, LineItem> firstLine =
        FocusPath.of(OrderLenses.lines()).via(FocusPaths.listAt(0));
    Optional<LineItem> first = firstLine.getOptional(order); // empty if out of bounds

    // Or narrow the generated traversal to its first element. Mind the asymmetry:
    // headOption reads the first element but writes to all of them
    AffinePath<Order, LineItem> alsoFirst = OrderFocus.lines().headOption();

    // A Map field: the generated path still focuses the whole map, so mapAt applies
    AffinePath<PriceList, BigDecimal> lamp = PriceListFocus.prices().via(FocusPaths.mapAt("LAMP"));
    Optional<BigDecimal> lampPrice = lamp.getOptional(priceList);
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
  record SealedVariants(
      List<String> pans, PaymentHistory masked, TraversalPath<PaymentHistory, String> samePans) {}

  static SealedVariants sealedVariants(PaymentHistory history) {
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
    return new SealedVariants(pans, masked, samePans);
  }

  /** The three paths the page's composition block builds. */
  record ComposedPaths(
      FocusPath<Consignment, String> street,
      AffinePath<Order, LineItem> firstLine,
      TraversalPath<Order, LineItem> allLines) {}

  static ComposedPaths composeExisting() {
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
    return new ComposedPaths(street, firstLine, allLines);
  }

  static List<String> navigatorAsPath(Consignment consignment) {
    // ANCHOR: navigator_use
    // The navigator chains the next hop as a method call
    String city = ConsignmentFocus.to().city().get(consignment);

    // It is the path itself, so it goes wherever a path goes
    FocusPath<Consignment, Address> to = ConsignmentFocus.to();
    String same = to.via(AddressFocus.city()).get(consignment);
    // ANCHOR_END: navigator_use
    return List.of(city, same);
  }

  /** What the page's navigator-hops block reads. */
  record NavigatorHops(String email, List<String> skus) {}

  static NavigatorHops navigatorHops(Order order) {
    // ANCHOR: navigator_hops
    // customer is a navigable record: a navigator, so .email() chains
    String email = OrderFocus.customer().email().value().get(order);

    // lines is a List of them: a navigator over every line, so .sku() chains too
    TraversalPath<Order, String> skuPath = OrderFocus.lines().sku();
    List<String> skus = skuPath.getAll(order);
    // ANCHOR_END: navigator_hops
    return new NavigatorHops(email, skus);
  }

  /** What the page's wider-hop block reads. */
  record WiderHop(List<BigDecimal> prices, List<String> emails) {}

  static WiderHop widerHop(OrderHistory history) {
    // ANCHOR: wider_hop
    // A hop into each order's list keeps the traversal, so lines() hands on its navigator
    List<BigDecimal> prices = OrderHistoryFocus.orders().lines().price().getAll(history);

    // A hop into one customer lands on a traversal too, so customer() hands on the traversal
    // form of the order's customer navigator, and the chain goes on
    OrderFocus.CustomerNavigator.TraversalForm<OrderHistory> customers =
        OrderHistoryFocus.orders().customer();
    List<String> emails = customers.email().value().getAll(history);
    // ANCHOR_END: wider_hop
    return new WiderHop(prices, emails);
  }

  /** What the page's path-methods block computes, and the postcodes its trace saw. */
  record Normalised(Consignment consignment, List<String> seen) {}

  static Normalised normalisePostcode(Consignment consignment) {
    // ANCHOR: path_methods
    List<String> seen = new ArrayList<>();
    Consignment normalised =
        ConsignmentFocus.to()
            .traced((_, address) -> seen.add(address.postcode()))
            .modify(
                address -> AddressLenses.postcode().modify(String::toUpperCase, address),
                consignment);
    // ANCHOR_END: path_methods
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

    // Map is ZERO_OR_MORE via the SPI, but a Focus method widens it only under
    // widenCollections or widenFields; otherwise the path still focuses the whole map
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

  static Nested nested(NestedContainers containers, WidenedConfig widenedConfig) {
    // ANCHOR: nested
    TraversalPath<NestedContainers, String> allTags = NestedContainersFocus.tags();
    List<String> tagValues = allTags.getAll(containers);

    AffinePath<NestedContainers, String> nestedOpt = NestedContainersFocus.nested();
    Optional<String> innerValue = nestedOpt.getOptional(containers);

    // Either<String, List<Integer>>: a nested List is stepped into unconditionally
    TraversalPath<NestedContainers, Integer> data = NestedContainersFocus.data();

    // Either<String, Map<String, Integer>>: an SPI ZERO_OR_MORE stops at the Map...
    AffinePath<NestedContainers, Map<String, Integer>> meta = NestedContainersFocus.meta();

    // ...unless widenCollections is on
    TraversalPath<WidenedConfig, Integer> hits = WidenedConfigFocus.meta();
    // ANCHOR_END: nested
    return new Nested(
        tagValues,
        innerValue,
        data.getAll(containers),
        meta.getOptional(containers),
        hits.getAll(widenedConfig));
  }
}

// Supporting types, each named for its role beside the cast.

// ANCHOR: catalogue
@GenerateLenses
@GenerateFocus
record PriceList(String name, Map<String, BigDecimal> prices) {}

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
@GenerateFocus
record Stockroom(String name, Map<String, Integer> stock, Either<String, String> verifiedName) {}

// ANCHOR_END: stockroom

// ANCHOR: order_history
// A customer's past orders: a list of orders, each holding a list of lines
@GenerateFocus
record OrderHistory(List<Order> orders) {}

// ANCHOR_END: order_history

// Width proofs for the nested-container table: the components are placeholders, with no role.

@GenerateLenses
@GenerateFocus
record NestedContainers(
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
