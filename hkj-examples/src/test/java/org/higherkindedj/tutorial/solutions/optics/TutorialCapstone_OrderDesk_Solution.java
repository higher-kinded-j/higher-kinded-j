// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.optics;

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
import org.higherkindedj.example.book.optics.cast.ConsignmentFocus;
import org.higherkindedj.example.book.optics.cast.ConsignmentState;
import org.higherkindedj.example.book.optics.cast.ConsignmentStatePrisms;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.LineItemFocus;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderFocus;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.edit.Edit;
import org.higherkindedj.optics.edit.Edits;
import org.higherkindedj.optics.fluent.OpticOps;
import org.higherkindedj.optics.focus.TraversalPath;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Solutions for the Optics Capstone: The order desk.
 *
 * <p>The pattern throughout: name each path once, hand its optic to {@code OpticOps} or {@code
 * Edits} when an update can fail, and check a variant through its prism before writing another.
 */
@DisplayName("Capstone: The Order Desk (Solutions)")
public class TutorialCapstone_OrderDesk_Solution {

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
   * Why this is idiomatic: the path names where the prices are, and {@code modifyAllValidated} says
   * what a failure means. Every bad price comes back, in order, and a good order comes back rebuilt
   * and equal to the one that went in.
   *
   * <p>Alternative: {@code OpticOps.modifyAllEither} with the same check, returning {@code Either}.
   * It keeps only the first error, which suits a check whose later failures add nothing.
   *
   * <p>Common wrong attempt: passing {@code PRICES} itself. {@code OpticOps} takes an optic, not a
   * path, so the call does not compile until the path hands over its traversal with {@code
   * toTraversal()}.
   */
  @Test
  @DisplayName("Exercise 1: check every price, and hear about every bad one")
  void exercise1_checkEveryPrice() {
    Function<Order, Validated<List<String>, Order>> check =
        order ->
            OpticOps.modifyAllValidated(
                order, PRICES.toTraversal(), TutorialCapstone_OrderDesk_Solution::checkPrice);

    assertThat(check.apply(ORDER)).isEqualTo(Validated.valid(ORDER));
    assertThat(check.apply(BAD))
        .isEqualTo(
            Validated.invalid(
                List.of("Price cannot be negative: -12.00", "Price cannot be negative: -0.50")));
  }

  /**
   * Why this is idiomatic: the filter sits on the lines, before the hop to the price, so the test
   * can read the whole line. The narrowed path is a value too, and a write through it leaves the
   * bulb's price alone.
   *
   * <p>Alternative: {@code OrderFocus.lines().via(LineItemFocus.price()).filter(price -> ...)},
   * filtering the prices themselves. It reaches the same prices here, but cannot test any other
   * field of the line.
   *
   * <p>Common wrong attempt: {@code line.price().compareTo(...) > 0}. It leaves out a line priced
   * at exactly £10.00, which the task includes; {@code >= 0} keeps it.
   */
  @Test
  @DisplayName("Exercise 2: name a narrower path")
  void exercise2_nameTheDearerPrices() {
    TraversalPath<Order, BigDecimal> dearer =
        OrderFocus.lines()
            .filter(line -> line.price().compareTo(new BigDecimal("10.00")) >= 0)
            .via(LineItemFocus.price());

    assertThat(dearer.getAll(ORDER)).containsExactly(new BigDecimal("40.00"));
    assertThat(dearer.modifyAll(price -> price.subtract(FIVE_POUNDS), ORDER).lines())
        .extracting(LineItem::price)
        .containsExactly(new BigDecimal("35.00"), new BigDecimal("2.50"));
  }

  /**
   * Why this is idiomatic: {@code map} runs the reduction only on a valid result, so an order with
   * a bad price is never reduced, and the failures reach the caller untouched.
   *
   * <p>Alternative: reduce first and check afterwards. For these prices the answers agree, but a
   * reduction that took a price below zero would then be reported as the customer's mistake.
   *
   * <p>Common wrong attempt: {@code flatMap} in place of {@code map}. {@code flatMap} wants a
   * function that returns a {@code Validated}, and the reduction returns a plain {@code Order}, so
   * the call does not compile.
   */
  @Test
  @DisplayName("Exercise 3: accept an order, then reduce its dearer lines")
  void exercise3_acceptThenReduce() {
    Function<Order, Validated<List<String>, Order>> accept =
        order ->
            OpticOps.modifyAllValidated(
                    order, PRICES.toTraversal(), TutorialCapstone_OrderDesk_Solution::checkPrice)
                .map(
                    checked ->
                        OrderFocus.lines()
                            .filter(line -> line.price().compareTo(new BigDecimal("10.00")) >= 0)
                            .via(LineItemFocus.price())
                            .modifyAll(price -> price.subtract(FIVE_POUNDS), checked));

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
   * Why this is idiomatic: each {@code parseIfPresent} owns one field, so a field the request did
   * not send costs no {@code if}, and a bad field is reported at its path. Nothing is written
   * unless every sent field parsed.
   *
   * <p>Alternative: the amendment in the tutorial's header, written by hand: an {@code if} and a
   * rebuild per field, and a throw at the first bad one, so a request with two bad fields reports
   * only one.
   *
   * <p>Common wrong attempt: {@code Edits.combine} with the parsing edits. {@code combine} takes
   * only edits that cannot fail, so it rejects a parse at compile time: "no suitable method found
   * for combine".
   */
  @Test
  @DisplayName("Exercise 4: amend an order from a sparse request")
  void exercise4_amendFromASparseRequest() {
    BiFunction<@Nullable String, @Nullable String, Validated<NonEmptyList<FieldError>, Order>>
        amend =
            (email, status) ->
                Edits.accumulate(
                        Edit.parseIfPresent(
                            OrderFocus.customer().email().value(),
                            email,
                            TutorialCapstone_OrderDesk_Solution::parseEmail),
                        Edit.parseIfPresent(
                            OrderFocus.status(),
                            status,
                            TutorialCapstone_OrderDesk_Solution::parseStatus))
                    .apply(ORDER);

    Validated<NonEmptyList<FieldError>, Order> shipped = amend.apply(null, " shipped ");
    assertThatValidated(shipped).isValid();
    assertThat(shipped.get().status()).isEqualTo(OrderStatus.SHIPPED);
    assertThat(shipped.get().customer()).isSameAs(ORDER.customer());

    assertThatValidated(amend.apply("ada.example.org", null))
        .hasFieldErrors("customer.email.value: not an email");
  }

  /**
   * Why this is idiomatic: the prism answers "is it dispatched?", and the state's path writes the
   * new variant. A consignment in any other state is handed back as the same object.
   *
   * <p>Alternative: {@code ConsignmentStatePrisms.dispatched().matches(consignment.state())},
   * asking the prism directly. Same answer; the path version reads from the consignment itself.
   *
   * <p>Common wrong attempt: {@code
   * ConsignmentFocus.state().via(ConsignmentStatePrisms.dispatched()) .modify(...)}. Its function
   * maps a {@code Dispatched} to a {@code Dispatched}, so it cannot return a {@code Returned}, and
   * the call does not compile.
   */
  @Test
  @DisplayName("Exercise 5: move a consignment to another state, from one state only")
  void exercise5_markReturned() {
    UnaryOperator<Consignment> markReturned =
        consignment ->
            ConsignmentFocus.state().via(ConsignmentStatePrisms.dispatched()).matches(consignment)
                ? ConsignmentFocus.state()
                    .set(new ConsignmentState.Returned("damaged"), consignment)
                : consignment;

    Consignment dispatched = consignment(new ConsignmentState.Dispatched(AT));
    Consignment pending = consignment(new ConsignmentState.Pending());

    assertThat(markReturned.apply(dispatched).state())
        .isEqualTo(new ConsignmentState.Returned("damaged"));
    assertThat(markReturned.apply(pending)).isSameAs(pending);
  }
}
