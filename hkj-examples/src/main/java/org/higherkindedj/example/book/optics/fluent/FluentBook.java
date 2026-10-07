// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.fluent;

import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.instances.Witnesses.completableFuture;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.CustomerFocus;
import org.higherkindedj.example.book.optics.cast.CustomerLenses;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.LineItemFocus;
import org.higherkindedj.example.book.optics.cast.LineItemLenses;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderFocus;
import org.higherkindedj.example.book.optics.cast.OrderLenses;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.example.book.optics.cast.OrderTraversals;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.fluent.OpticOps;
import org.higherkindedj.optics.focus.TraversalPath;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/fluent_api.html">Updates That Can Fail</a>
 * page. The page {@code {{#include}}}s the anchored regions, and {@code FluentBookTest} holds the
 * claims the page makes about this code. The records are the chapter's cast.
 */
public final class FluentBook {

  private FluentBook() {}

  // ANCHOR: check_price
  static final BigDecimal MAXIMUM = new BigDecimal("10000");

  static Validated<String, BigDecimal> checkPrice(BigDecimal price) {
    if (price.signum() < 0) {
      return Validated.invalid("Price cannot be negative: " + price);
    }
    return price.compareTo(MAXIMUM) > 0
        ? Validated.invalid("Price exceeds maximum: " + price)
        : Validated.valid(price);
  }

  // ANCHOR_END: check_price

  // ANCHOR: other_checks
  static Either<String, BigDecimal> checkPriceEither(BigDecimal price) {
    return checkPrice(price).toEither();
  }

  static Either<String, String> checkEmail(String email) {
    return email.contains("@") ? Either.right(email) : Either.left("Invalid email: " + email);
  }

  static Maybe<String> normaliseName(String name) {
    String trimmed = name.strip();
    return trimmed.length() >= 2 && trimmed.length() <= 40 ? Maybe.just(trimmed) : Maybe.nothing();
  }

  // ANCHOR_END: other_checks

  static List<String> priceErrorsByHand(Order order) {
    // ANCHOR: by_hand
    List<String> errors = new ArrayList<>();
    for (LineItem line : order.lines()) {
      if (line.price().signum() < 0) {
        errors.add("Price cannot be negative: " + line.price());
      } else if (line.price().compareTo(MAXIMUM) > 0) {
        errors.add("Price exceeds maximum: " + line.price());
      }
    }
    // ANCHOR_END: by_hand
    return errors;
  }

  /** What the page's every-error block computes. */
  record EveryError(Validated<List<String>, Order> checked, String report) {}

  static EveryError everyError(Order order) {
    // ANCHOR: every_error
    Traversal<Order, BigDecimal> prices =
        OrderFocus.lines().via(LineItemFocus.price()).toTraversal();

    Validated<List<String>, Order> checked =
        OpticOps.modifyAllValidated(order, prices, FluentBook::checkPrice);

    String report =
        checked.fold(
            errors -> errors.size() + " invalid prices: " + String.join("; ", errors),
            _ -> "all prices accepted");
    // ANCHOR_END: every_error
    return new EveryError(checked, report);
  }

  /** What the page's fail-fast block computes. */
  record FailFast(Either<String, Customer> result, String message) {}

  static FailFast failFast(Customer customer) {
    // ANCHOR: fail_fast
    Lens<Customer, String> email = CustomerFocus.email().value().toLens();

    Either<String, Customer> result =
        OpticOps.modifyEither(customer, email, FluentBook::checkEmail);

    String message =
        result.fold(error -> "rejected: " + error, c -> "accepted: " + c.email().value());
    // ANCHOR_END: fail_fast
    return new FailFast(result, message);
  }

  /** What the page's no-detail block computes. */
  record NoDetail(Maybe<Customer> normalised, Customer safe) {}

  static NoDetail noDetail(Customer customer) {
    // ANCHOR: no_detail
    Maybe<Customer> normalised =
        OpticOps.modifyMaybe(customer, CustomerFocus.name().toLens(), FluentBook::normaliseName);

    Customer safe = normalised.orElse(customer);
    // ANCHOR_END: no_detail
    return new NoDetail(normalised, safe);
  }

  static Either<String, Order> firstError(Order order) {
    Traversal<Order, BigDecimal> prices =
        OrderFocus.lines().via(LineItemFocus.price()).toTraversal();
    // ANCHOR: first_error
    Either<String, Order> firstFailure =
        OpticOps.modifyAllEither(order, prices, FluentBook::checkPriceEither);
    // ANCHOR_END: first_error
    return firstFailure;
  }

  static Either<String, Customer> register(Customer customer) {
    // ANCHOR: sequential
    Either<String, Customer> registered =
        OpticOps.modifyEither(
                customer, CustomerFocus.email().value().toLens(), FluentBook::checkEmail)
            .flatMap(
                checked ->
                    OpticOps.modifyEither(
                        checked,
                        CustomerFocus.name().toLens(),
                        name ->
                            name.length() >= 2
                                ? Either.right(name)
                                : Either.left("Name must be at least 2 characters")));
    // ANCHOR_END: sequential
    return registered;
  }

  /** What the page's builder block computes. */
  record Builders(Either<String, Customer> email, Validated<List<String>, Order> prices) {}

  static Builders builders(Customer customer, Order order) {
    Lens<Customer, String> email = CustomerFocus.email().value().toLens();
    Traversal<Order, BigDecimal> prices =
        OrderFocus.lines().via(LineItemFocus.price()).toTraversal();
    // ANCHOR: builders
    Either<String, Customer> checkedEmail =
        OpticOps.modifyingWithValidation(customer).throughEither(email, FluentBook::checkEmail);

    Validated<List<String>, Order> checkedPrices =
        OpticOps.modifyingWithValidation(order).allThroughValidated(prices, FluentBook::checkPrice);
    // ANCHOR_END: builders
    return new Builders(checkedEmail, checkedPrices);
  }

  // ANCHOR: current_price
  static CompletableFuture<BigDecimal> currentPrice(BigDecimal listed) {
    return CompletableFuture.completedFuture(listed.add(BigDecimal.ONE));
  }

  // ANCHOR_END: current_price

  static CompletableFuture<Order> repriced(Order order) {
    // ANCHOR: modify_f
    Applicative<CompletableFutureKind.Witness> futures = Instances.applicative(completableFuture());

    TraversalPath<Order, BigDecimal> prices = OrderFocus.lines().via(LineItemFocus.price());

    Kind<CompletableFutureKind.Witness, Order> pending =
        prices.modifyF(price -> FUTURE.widen(currentPrice(price)), order, futures);

    CompletableFuture<Order> repriced = FUTURE.narrow(pending);
    // ANCHOR_END: modify_f
    return repriced;
  }

  /** What the page's OpticOps reads, writes and queries compute. */
  record ReadsAndWrites(
      String name,
      List<Integer> quantities,
      Optional<Integer> firstQuantity,
      Order paid,
      Order doubled,
      boolean anyBulk,
      boolean allOrdered,
      int lineCount,
      boolean noLines,
      Optional<LineItem> overTen) {}

  static ReadsAndWrites readsAndWrites(Customer customer, Order order) {
    // ANCHOR: reads_and_writes
    Traversal<Order, Integer> quantities =
        OrderTraversals.lines().andThen(LineItemLenses.quantity());

    // Read
    String name = OpticOps.get(customer, CustomerLenses.name());
    List<Integer> allQuantities = OpticOps.getAll(order, quantities);
    Optional<Integer> firstQuantity = OpticOps.preview(order, quantities);

    // Write
    Order paid = OpticOps.set(order, OrderLenses.status(), OrderStatus.PAID);
    Order doubled = OpticOps.modifyAll(order, quantities, quantity -> quantity * 2);

    // Query, without modifying anything
    boolean anyBulk = OpticOps.exists(order, quantities, quantity -> quantity >= 4);
    boolean allOrdered = OpticOps.all(order, quantities, quantity -> quantity >= 1);
    int lineCount = OpticOps.count(order, OrderTraversals.lines());
    boolean noLines = OpticOps.isEmpty(order, OrderTraversals.lines());
    Optional<LineItem> overTen =
        OpticOps.find(
            order, OrderTraversals.lines(), line -> line.price().compareTo(BigDecimal.TEN) > 0);
    // ANCHOR_END: reads_and_writes
    return new ReadsAndWrites(
        name,
        allQuantities,
        firstQuantity,
        paid,
        doubled,
        anyBulk,
        allOrdered,
        lineCount,
        noLines,
        overTen);
  }

  /** What the page's two-styles block computes. */
  record Styles(int quantity, LineItem more, int sameQuantity, LineItem alsoMore) {}

  static Styles styles(LineItem lamp) {
    // ANCHOR: styles
    // Static style
    int quantity = OpticOps.get(lamp, LineItemLenses.quantity());
    LineItem more = OpticOps.modify(lamp, LineItemLenses.quantity(), q -> q + 1);

    // Builder style
    int sameQuantity = OpticOps.getting(lamp).through(LineItemLenses.quantity());
    LineItem alsoMore = OpticOps.modifying(lamp).through(LineItemLenses.quantity(), q -> q + 1);
    // ANCHOR_END: styles
    return new Styles(quantity, more, sameQuantity, alsoMore);
  }

  /** What the page's builder-verbs block computes. */
  record Verbs(List<Integer> all, Order reset, Order bumped, boolean any) {}

  static Verbs builderVerbs(Order order) {
    Traversal<Order, Integer> quantities =
        OrderTraversals.lines().andThen(LineItemLenses.quantity());
    // ANCHOR: builder_verbs
    List<Integer> all = OpticOps.getting(order).allThrough(quantities);
    Order reset = OpticOps.setting(order).allThrough(quantities, 1);
    Order bumped = OpticOps.modifying(order).allThrough(quantities, quantity -> quantity + 1);
    boolean any = OpticOps.querying(order).anyMatch(quantities, quantity -> quantity >= 4);
    // ANCHOR_END: builder_verbs
    return new Verbs(all, reset, bumped, any);
  }

  static Order stampIfNew(Order order, Instant now) {
    // ANCHOR: conditional
    Order stamped =
        OpticOps.get(order, OrderLenses.status()) == OrderStatus.NEW
            ? OpticOps.set(order, OrderLenses.placedAt(), now)
            : order;
    // ANCHOR_END: conditional
    return stamped;
  }

  /** What the page's filtered block computes. */
  record BulkDiscount(Order discounted, List<LineItem> bulkLines) {}

  static BulkDiscount discountBulkLines(Order order) {
    // ANCHOR: filtered
    Traversal<Order, LineItem> bulk =
        OrderTraversals.lines().filtered(line -> line.quantity() >= 4);

    Order discounted =
        OpticOps.modifyAll(
            order,
            bulk.andThen(LineItemLenses.price()),
            price -> price.multiply(new BigDecimal("0.9")));

    List<LineItem> bulkLines = OpticOps.getAll(discounted, bulk);
    // ANCHOR_END: filtered
    return new BulkDiscount(discounted, bulkLines);
  }

  static int totalQuantity(Order order) {
    // ANCHOR: aggregate
    int total =
        OrderTraversals.lines()
            .andThen(LineItemLenses.quantity())
            .asFold()
            .foldMap(Monoids.integerAddition(), quantity -> quantity, order);
    // ANCHOR_END: aggregate
    return total;
  }

  static List<String> dearSkus(Order order) {
    // ANCHOR: streams
    List<String> dearSkus =
        OpticOps.getting(order).allThrough(OrderTraversals.lines()).stream()
            .filter(line -> line.price().compareTo(BigDecimal.TEN) > 0)
            .map(LineItem::sku)
            .toList();
    // ANCHOR_END: streams
    return dearSkus;
  }

  static List<List<Integer>> composeOnce(List<Order> orders) {
    // ANCHOR: compose_once
    // Compose once, before the loop
    Traversal<Order, Integer> quantities =
        OrderTraversals.lines().andThen(LineItemLenses.quantity());

    List<List<Integer>> allQuantities = new ArrayList<>();
    for (Order order : orders) {
      allQuantities.add(OpticOps.getAll(order, quantities));
    }
    // ANCHOR_END: compose_once
    return allQuantities;
  }
}
