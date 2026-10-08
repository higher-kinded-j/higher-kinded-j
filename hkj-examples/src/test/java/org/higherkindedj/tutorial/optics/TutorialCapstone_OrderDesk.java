// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.optics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.ORDER;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.consignment;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.line;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.order;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.higherkindedj.example.book.optics.cast.Consignment;
import org.higherkindedj.example.book.optics.cast.ConsignmentState;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.LineItemFocus;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderFocus;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.focus.TraversalPath;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Capstone Tutorial: The order desk.
 *
 * <p>Pain to Promise. An order desk takes orders in, amends them when a customer asks, and sends
 * goods out. By hand, an amendment rebuilds the customer and the order for each field the request
 * sent, and throws on the first bad one:
 *
 * <pre>
 *   if (email != null) {
 *     String clean = email.strip().toLowerCase(Locale.ROOT);
 *     if (!clean.contains("@")) throw new IllegalArgumentException("not an email");
 *     order = new Order(order.id(), new Customer(order.customer().name(), new EmailAddress(clean)),
 *         order.lines(), order.placedAt(), order.currency(), order.status());
 *   }
 *   if (status != null) { ...the same again for the status... }
 * </pre>
 *
 * <p>With named paths, the same amendment checks every field the request sent, reports every bad
 * one, and writes only when all of them pass:
 *
 * <pre>
 *   Edits.accumulate(
 *           parseIfPresent(EMAIL, email, OrderDesk::parseEmail),
 *           parseIfPresent(STATUS, status, OrderDesk::parseStatus))
 *       .apply(order);
 * </pre>
 *
 * <p>Java idiom anchor: {@code OpticOps.modifyAllValidated} plays the part of a loop over the
 * prices that collects every failure into a list instead of throwing at the first. Unlike the loop,
 * it hands back the rebuilt order when every price passes.
 *
 * <p>Key concepts:
 *
 * <ul>
 *   <li>a path is a value: name it once, as {@link #PRICES} is, and reuse it;
 *   <li>{@code modifyAllValidated} reports every bad value, and {@code map} changes only a valid
 *       result;
 *   <li>{@code filter} narrows a traversal path, so a write never reaches what it leaves out;
 *   <li>{@code Edits.accumulate} checks every field a request sent before it writes anything, and
 *       leaves alone a field it did not send;
 *   <li>moving to another variant is a check through the prism, then a write.
 * </ul>
 *
 * <p>The records are the Optics chapter's cast, in {@code
 * org.higherkindedj.example.book.optics.cast}, and the Optics chapter's Capstone page builds the
 * same desk; these exercises ask for its pieces on different requests. Prerequisites: Tutorial 00
 * (Your First Path), Tutorial 09 (Fluent Optics API), Tutorial 12 (Focus DSL) and Tutorial 24
 * (Multi-Edit and Sparse Updates).
 *
 * <p>Your answers need a few imports this file does not carry yet: {@code OpticOps} from {@code
 * org.higherkindedj.optics.fluent}, {@code Edits} and {@code Edit} from {@code
 * org.higherkindedj.optics.edit}, and the cast's generated {@code ConsignmentFocus} and {@code
 * ConsignmentStatePrisms}.
 *
 * <p>Each exercise's hints climb from a nudge to the answer; stop reading as soon as you have what
 * you need. Replace each {@code answerRequired()} placeholder with the correct code to make the
 * tests pass.
 */
@DisplayName("Capstone: The Order Desk")
public class TutorialCapstone_OrderDesk {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required - replace answerRequired() with your solution");
  }

  /** The desk's path to every line's price, named once and reused. */
  static final TraversalPath<Order, BigDecimal> PRICES =
      OrderFocus.lines().via(LineItemFocus.price());

  private static final BigDecimal FIVE_POUNDS = new BigDecimal("5.00");

  private static final Instant AT = Instant.parse("2026-10-02T08:00:00Z");

  /** An order with two negative prices, which every check must report. */
  private static final Order BAD =
      order(List.of(line("DESK", "-12.00"), line("LAMP", "40.00"), line("PEN", "-0.50")));

  static Validated<String, BigDecimal> checkPrice(BigDecimal price) {
    return price.signum() < 0
        ? Validated.invalid("Price cannot be negative: " + price)
        : Validated.valid(price);
  }

  static Validated<NonEmptyList<FieldError>, String> parseEmail(String raw) {
    String email = raw.strip().toLowerCase(Locale.ROOT);
    return email.contains("@")
        ? Validated.validNel(email)
        : Validated.invalidNel(FieldError.of("not an email"));
  }

  static Validated<NonEmptyList<FieldError>, OrderStatus> parseStatus(String raw) {
    try {
      return Validated.validNel(OrderStatus.valueOf(raw.strip().toUpperCase(Locale.ROOT)));
    } catch (IllegalArgumentException e) {
      return Validated.invalidNel(FieldError.of("not a status"));
    }
  }

  /**
   * Exercise 1: check every price, and hear about every bad one.
   *
   * <p>A check that throws stops at the first bad price, so a customer fixes one and meets the
   * next. {@code OpticOps.modifyAllValidated} runs the check on every value a traversal reaches and
   * gathers every failure.
   *
   * <p>Task: write a function that checks every price of an order with {@code checkPrice}, using
   * {@link #PRICES}.
   *
   * <pre>
   *   // Nudge:    the check can fail, so the call that runs it comes from the fallible-update API.
   *   // Strategy: OpticOps.modifyAllValidated(order, traversal, check) takes a Traversal; a path
   *   //           hands over its optic with toTraversal().
   *   // Spoiler:  order -> OpticOps.modifyAllValidated(
   *   //               order, PRICES.toTraversal(), TutorialCapstone_OrderDesk::checkPrice)
   * </pre>
   */
  @Test
  @DisplayName("Exercise 1: check every price, and hear about every bad one")
  void exercise1_checkEveryPrice() {
    // TODO: Replace answerRequired() with a function that checks every price of an order
    Function<Order, Validated<List<String>, Order>> check = answerRequired();

    assertThat(check.apply(ORDER)).isEqualTo(Validated.valid(ORDER));
    assertThat(check.apply(BAD))
        .isEqualTo(
            Validated.invalid(
                List.of("Price cannot be negative: -12.00", "Price cannot be negative: -0.50")));
  }

  /**
   * Exercise 2: name a narrower path.
   *
   * <p>{@code filter} keeps the elements that pass a test and lets a write reach only those. Unlike
   * a stream's {@code filter}, the elements it leaves out stay in the order, unchanged.
   *
   * <p>Task: name the path to the price of every line priced at £10.00 or more.
   *
   * <pre>
   *   // Nudge:    narrow the lines first, then take the price of each line that is left.
   *   // Strategy: filter(line -> ...) on OrderFocus.lines(), before .via(LineItemFocus.price());
   *   //           compare BigDecimal values with compareTo.
   *   // Spoiler:  OrderFocus.lines()
   *   //               .filter(line -> line.price().compareTo(new BigDecimal("10.00")) >= 0)
   *   //               .via(LineItemFocus.price())
   * </pre>
   */
  @Test
  @DisplayName("Exercise 2: name a narrower path")
  void exercise2_nameTheDearerPrices() {
    // TODO: Replace answerRequired() with the path to every price of £10.00 or more
    TraversalPath<Order, BigDecimal> dearer = answerRequired();

    assertThat(dearer.getAll(ORDER)).containsExactly(new BigDecimal("40.00"));
    assertThat(dearer.modifyAll(price -> price.subtract(FIVE_POUNDS), ORDER).lines())
        .extracting(LineItem::price)
        .containsExactly(new BigDecimal("35.00"), new BigDecimal("2.50"));
  }

  /**
   * Exercise 3: accept an order, then reduce its dearer lines.
   *
   * <p>A {@code Validated} carries either the checked order or every failure. Its {@code map}
   * changes only the first, so a step after a check runs only when the check passed.
   *
   * <p>Task: write a function that checks every price of an order and, only when all of them pass,
   * takes {@code FIVE_POUNDS} off every line priced at £10.00 or more.
   *
   * <pre>
   *   // Nudge:    the check of exercise 1, then a write that runs only on a valid result.
   *   // Strategy: map(...) on the Validated that modifyAllValidated returns, writing through the
   *   //           path of exercise 2 with modifyAll.
   *   // Spoiler:  order -> OpticOps.modifyAllValidated(
   *   //                   order, PRICES.toTraversal(), TutorialCapstone_OrderDesk::checkPrice)
   *   //               .map(checked -> OrderFocus.lines()
   *   //                   .filter(line -> line.price().compareTo(new BigDecimal("10.00")) >= 0)
   *   //                   .via(LineItemFocus.price())
   *   //                   .modifyAll(price -> price.subtract(FIVE_POUNDS), checked))
   * </pre>
   */
  @Test
  @DisplayName("Exercise 3: accept an order, then reduce its dearer lines")
  void exercise3_acceptThenReduce() {
    // TODO: Replace answerRequired() with a function that checks, then reduces the dearer lines
    Function<Order, Validated<List<String>, Order>> accept = answerRequired();

    Validated<List<String>, Order> accepted = accept.apply(ORDER);
    assertThatValidated(accepted).isValid();
    assertThat(accepted.get().lines())
        .extracting(LineItem::price)
        .containsExactly(new BigDecimal("35.00"), new BigDecimal("2.50"));
    assertThat(accept.apply(BAD))
        .isEqualTo(
            Validated.invalid(
                List.of("Price cannot be negative: -12.00", "Price cannot be negative: -0.50")));
  }

  /**
   * Exercise 4: amend an order from a sparse request.
   *
   * <p>A request sends only the fields a customer changed, and leaves the others {@code null}. Each
   * {@code Edit.parseIfPresent} parses its field when it was sent, and does nothing when it was
   * not; {@code Edits.accumulate} checks them all before it writes any.
   *
   * <p>Task: write a function that amends {@code ORDER} from a request's email and status, parsing
   * the email into the customer's email address with {@code parseEmail} and the status into the
   * order's status with {@code parseStatus}.
   *
   * <pre>
   *   // Nudge:    one fallible edit per field, all checked together, then applied to ORDER.
   *   // Strategy: Edits.accumulate(...).apply(ORDER), with one Edit.parseIfPresent(path, raw, parse)
   *   //           per field; the email's path ends in value().
   *   // Spoiler:  (email, status) -> Edits.accumulate(
   *   //                   Edit.parseIfPresent(OrderFocus.customer().email().value(), email,
   *   //                       TutorialCapstone_OrderDesk::parseEmail),
   *   //                   Edit.parseIfPresent(OrderFocus.status(), status,
   *   //                       TutorialCapstone_OrderDesk::parseStatus))
   *   //               .apply(ORDER)
   * </pre>
   */
  @Test
  @DisplayName("Exercise 4: amend an order from a sparse request")
  void exercise4_amendFromASparseRequest() {
    // TODO: Replace answerRequired() with a function from (email, status) to the amended ORDER
    BiFunction<@Nullable String, @Nullable String, Validated<NonEmptyList<FieldError>, Order>>
        amend = answerRequired();

    Validated<NonEmptyList<FieldError>, Order> shipped = amend.apply(null, " shipped ");
    assertThatValidated(shipped).isValid();
    assertThat(shipped.get().status()).isEqualTo(OrderStatus.SHIPPED);
    assertThat(shipped.get().customer()).isSameAs(ORDER.customer());

    assertThatValidated(amend.apply("ada.example.org", null))
        .hasFieldErrors("customer.email.value: not an email");
  }

  /**
   * Exercise 5: move a consignment to another state, from one state only.
   *
   * <p>A prism's {@code modify} changes a {@code Dispatched} into another {@code Dispatched}; it
   * cannot turn it into a {@code Returned}. Moving to another variant is a check through the prism,
   * then a write through the state's path.
   *
   * <p>Task: write a function that marks a {@code Dispatched} consignment {@code
   * Returned("damaged")}, and hands any other consignment back as it was.
   *
   * <pre>
   *   // Nudge:    ask whether the state is Dispatched before writing a new state.
   *   // Strategy: ConsignmentFocus.state().via(ConsignmentStatePrisms.dispatched()) is an
   *   //           AffinePath with matches(source); ConsignmentFocus.state().set(...) writes.
   *   // Spoiler:  consignment -> ConsignmentFocus.state()
   *   //                   .via(ConsignmentStatePrisms.dispatched()).matches(consignment)
   *   //               ? ConsignmentFocus.state()
   *   //                   .set(new ConsignmentState.Returned("damaged"), consignment)
   *   //               : consignment
   * </pre>
   */
  @Test
  @DisplayName("Exercise 5: move a consignment to another state, from one state only")
  void exercise5_markReturned() {
    // TODO: Replace answerRequired() with a function that returns only a Dispatched consignment
    UnaryOperator<Consignment> markReturned = answerRequired();

    Consignment dispatched = consignment(new ConsignmentState.Dispatched(AT));
    Consignment pending = consignment(new ConsignmentState.Pending());

    assertThat(markReturned.apply(dispatched).state())
        .isEqualTo(new ConsignmentState.Returned("damaged"));
    assertThat(markReturned.apply(pending)).isSameAs(pending);
  }

  /**
   * Congratulations! You've completed the Optics Capstone: The Order Desk
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>✓ How a named path is reused by every operation that needs it
   *   <li>✓ How {@code modifyAllValidated} reports every bad value, and {@code map} runs only on a
   *       valid result
   *   <li>✓ How {@code filter} narrows a write to the elements that pass a test
   *   <li>✓ How {@code Edits.accumulate} checks a sparse request before writing it
   *   <li>✓ Why a change of variant is a check through the prism, then a write
   * </ul>
   *
   * <p>Next: the Optics chapter's Check Your Understanding page, whose score says where to go next.
   */
}
