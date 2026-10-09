// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.folds;

import static org.higherkindedj.optics.extensions.FoldExtensions.findMaybe;
import static org.higherkindedj.optics.extensions.FoldExtensions.getAllMaybe;
import static org.higherkindedj.optics.extensions.FoldExtensions.previewMaybe;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.LineItemLenses;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderLenses;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateFolds;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/folds.html">Folds</a> page. The page {@code
 * {{#include}}}s the anchored regions, and {@code FoldsBookTest} holds the claims the page makes
 * about this code.
 *
 * <p>The order and its lines are the chapter's cast, imported from its package.
 */
public final class FoldsBook {

  /** The lens the page's ordering example reads a name through. */
  static final Lens<Employee, String> NAME_LENS =
      Lens.of(Employee::name, (e, name) -> new Employee(name, e.email()));

  /** The lens the page's ordering example reads an email through. */
  static final Lens<Employee, String> EMAIL_LENS =
      Lens.of(Employee::email, (e, email) -> new Employee(e.name(), email));

  private FoldsBook() {}

  /** The order the page builds in Step 2, and queries in the steps after it. */
  static Order order() {
    // ANCHOR: order
    Order order =
        new Order(
            UUID.fromString("00000000-0000-0000-0000-000000000123"),
            new Customer("Ada", new EmailAddress("ada@example.com")),
            List.of(
                new LineItem("LAPTOP", 1, new BigDecimal("999.99")),
                new LineItem("MOUSE", 2, new BigDecimal("12.50")),
                new LineItem("DESK", 1, new BigDecimal("350.00"))),
            Instant.parse("2026-10-01T09:00:00Z"),
            Currency.getInstance("GBP"),
            OrderStatus.NEW);
    // ANCHOR_END: order
    return order;
  }

  static List<LineItem> getAll(Order order) {
    // ANCHOR: get_all
    Fold<Order, LineItem> linesFold = Fold.of(Order::lines);

    List<LineItem> allLines = linesFold.getAll(order);
    // [LineItem[sku=LAPTOP, ...], LineItem[sku=MOUSE, ...], LineItem[sku=DESK, ...]]
    // ANCHOR_END: get_all
    return allLines;
  }

  static List<Optional<LineItem>> preview(Fold<Order, LineItem> linesFold, Order order) {
    // ANCHOR: preview
    Optional<LineItem> firstLine = linesFold.preview(order);
    // Optional[LineItem[sku=LAPTOP, quantity=1, price=999.99]]

    Order emptyOrder = OrderLenses.withLines(order, List.of());
    Optional<LineItem> noLine = linesFold.preview(emptyOrder);
    // Optional.empty
    // ANCHOR_END: preview
    return List.of(firstLine, noLine);
  }

  static Optional<LineItem> find(Fold<Order, LineItem> linesFold, Order order) {
    // ANCHOR: find
    Optional<LineItem> expensiveLine =
        linesFold.find(line -> line.price().compareTo(new BigDecimal("500")) > 0, order);
    // Optional[LineItem[sku=LAPTOP, quantity=1, price=999.99]]
    // ANCHOR_END: find
    return expensiveLine;
  }

  static boolean exists(Fold<Order, LineItem> linesFold, Order order) {
    // ANCHOR: exists
    boolean hasMultiUnitLine = linesFold.exists(line -> line.quantity() > 1, order);
    // true: the mouse line is for two
    // ANCHOR_END: exists
    return hasMultiUnitLine;
  }

  static boolean all(Fold<Order, LineItem> linesFold, Order order) {
    // ANCHOR: all
    boolean allSingleUnits = linesFold.all(line -> line.quantity() == 1, order);
    // false: the mouse line is for two
    // ANCHOR_END: all
    return allSingleUnits;
  }

  static boolean hasLines(Fold<Order, LineItem> linesFold, Order order) {
    // ANCHOR: is_empty
    boolean hasLines = !linesFold.isEmpty(order);
    // true
    // ANCHOR_END: is_empty
    return hasLines;
  }

  static int length(Fold<Order, LineItem> linesFold, Order order) {
    // ANCHOR: length
    int lineCount = linesFold.length(order);
    // 3
    // ANCHOR_END: length
    return lineCount;
  }

  static List<String> allSkus(Order order) {
    // ANCHOR: compose
    // Get every SKU from every order in a history
    Fold<OrderHistory, Order> historyToOrders = OrderHistoryFolds.orders();
    Fold<Order, LineItem> orderToLines = Fold.of(Order::lines);
    Lens<LineItem, String> lineToSku = LineItemLenses.sku();

    Fold<OrderHistory, String> historyToAllSkus =
        historyToOrders.andThen(orderToLines).andThen(lineToSku.asFold());

    Order secondOrder =
        OrderLenses.withId(
            OrderLenses.withLines(
                order,
                List.of(
                    new LineItem("KEYBOARD", 1, new BigDecimal("75.00")),
                    new LineItem("MONITOR", 1, new BigDecimal("450.00")))),
            UUID.fromString("00000000-0000-0000-0000-000000000124"));
    OrderHistory history = new OrderHistory(List.of(order, secondOrder));

    List<String> allSkus = historyToAllSkus.getAll(history);
    // [LAPTOP, MOUSE, DESK, KEYBOARD, MONITOR]
    // ANCHOR_END: compose
    return allSkus;
  }

  static BigDecimal orderTotal(Order order) {
    // ANCHOR: total
    Fold<Order, LineItem> lines = Fold.of(Order::lines);

    // Define how to combine amounts (addition)
    Monoid<BigDecimal> sumMonoid =
        new Monoid<>() {
          @Override
          public BigDecimal empty() {
            return BigDecimal.ZERO; // Start with zero
          }

          @Override
          public BigDecimal combine(BigDecimal a, BigDecimal b) {
            return a.add(b); // Add them
          }
        };

    // A line's total is its price times its quantity
    Function<LineItem, BigDecimal> lineTotal =
        line -> line.price().multiply(BigDecimal.valueOf(line.quantity()));

    // Work out each line's total and sum them all
    BigDecimal orderTotal = lines.foldMap(sumMonoid, lineTotal, order);
    // 1374.99, which is 999.99 + 25.00 + 350.00
    // ANCHOR_END: total
    return orderTotal;
  }

  static List<Maybe<LineItem>> previewMaybeExample(Order order) {
    // ANCHOR: preview_maybe
    Fold<Order, LineItem> linesFold = Fold.of(Order::lines);

    Maybe<LineItem> firstLine = previewMaybe(linesFold, order);
    // Just(LineItem[sku=LAPTOP, quantity=1, price=999.99])

    Order emptyOrder = OrderLenses.withLines(order, List.of());
    Maybe<LineItem> noLine = previewMaybe(linesFold, emptyOrder);
    // Nothing
    // ANCHOR_END: preview_maybe
    return List.of(firstLine, noLine);
  }

  static List<Maybe<LineItem>> findMaybeExample(Order order) {
    // ANCHOR: find_maybe
    Fold<Order, LineItem> linesFold = Fold.of(Order::lines);

    Maybe<LineItem> expensiveLine =
        findMaybe(linesFold, line -> line.price().compareTo(new BigDecimal("500")) > 0, order);
    // Just(LineItem[sku=LAPTOP, quantity=1, price=999.99])

    Maybe<LineItem> luxuryLine =
        findMaybe(linesFold, line -> line.price().compareTo(new BigDecimal("5000")) > 0, order);
    // Nothing
    // ANCHOR_END: find_maybe
    return List.of(expensiveLine, luxuryLine);
  }

  static List<Maybe<List<LineItem>>> getAllMaybeExample(Order order) {
    // ANCHOR: get_all_maybe
    Fold<Order, LineItem> linesFold = Fold.of(Order::lines);

    Maybe<List<LineItem>> allLines = getAllMaybe(linesFold, order);
    // Just([LineItem[sku=LAPTOP, ...], LineItem[sku=MOUSE, ...], LineItem[sku=DESK, ...]])

    Order emptyOrder = OrderLenses.withLines(order, List.of());
    Maybe<List<LineItem>> noLines = getAllMaybe(linesFold, emptyOrder);
    // Nothing
    // ANCHOR_END: get_all_maybe
    return List.of(allLines, noLines);
  }

  static List<Object> emptyFold(Team team) {
    // ANCHOR: empty
    Fold<Team, String> nothing = Fold.empty();

    List<String> none = nothing.getAll(team);
    // []
    int count = nothing.length(team);
    // 0
    boolean empty = nothing.isEmpty(team);
    // true
    // ANCHOR_END: empty
    return List.of(none, count, empty);
  }

  static List<List<String>> ordering(
      Lens<Employee, String> nameLens, Lens<Employee, String> emailLens, Employee employee) {
    // ANCHOR: ordering
    Fold<Employee, String> nameFirst = nameLens.asFold().plus(emailLens.asFold());
    List<String> nameThenEmail = nameFirst.getAll(employee);
    // [Alice, alice@example.com]

    Fold<Employee, String> emailFirst = emailLens.asFold().plus(nameLens.asFold());
    List<String> emailThenName = emailFirst.getAll(employee);
    // [alice@example.com, Alice]
    // ANCHOR_END: ordering
    return List.of(nameThenEmail, emailThenName);
  }
}

// ANCHOR: order_history
// A customer's past orders, beside the chapter's Order
@GenerateFolds
record OrderHistory(List<Order> orders) {}

// ANCHOR_END: order_history

record Employee(String name, String email) {}

record Team(String name, Employee lead, List<Employee> members) {}
