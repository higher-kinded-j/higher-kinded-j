// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.optics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.ORDER;

import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.optics.focus.FocusPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 00: Your first path.
 *
 * <p>Pain to Promise. Changing a customer's email inside an order means rebuilding the email, the
 * customer and the order by hand, copying every component you did not change:
 *
 * <pre>
 *   Order moved = new Order(order.id(),
 *       new Customer(order.customer().name(), new EmailAddress("ada@example.org")),
 *       order.lines(), order.placedAt(), order.currency(), order.status());
 * </pre>
 *
 * <p>A generated Focus path names the field once, and rebuilds everything on the way:
 *
 * <pre>
 *   Order moved = OrderFocus.customer().email().value().set("ada@example.org", order);
 * </pre>
 *
 * <p>Java idiom anchor: a Focus path is like a JPA metamodel or QueryDSL path, such as {@code
 * QOrder.order.customer.email}, except that it writes as well as reads, returning a new record.
 *
 * <p>Key concepts:
 *
 * <ul>
 *   <li>{@code @GenerateFocus} on a record generates {@code XFocus}, one method per component;
 *   <li>{@code generateNavigators = true} lets the next hop chain on as a method call;
 *   <li>a {@code List} field's path is already on the elements, so the next hop is {@code
 *       .via(...)}.
 * </ul>
 *
 * <p>The records are the Optics chapter's cast, in {@code
 * org.higherkindedj.example.book.optics.cast}: an {@code Order} placed by a {@code Customer}, with
 * {@code LineItem}s. {@code OrderFocus} and {@code LineItemFocus} are generated into that package
 * when hkj-examples compiles; if your IDE cannot find them, build the module once, then import
 * them. Read the Optics Quickstart first; no other tutorial is needed.
 *
 * <p>This file is also the track's setup check: if it compiles and its tests fail only with "Answer
 * required", your build is ready for the rest of the optics tutorials.
 *
 * <p>Each exercise's hints climb from a nudge to the answer; stop reading as soon as you have what
 * you need. Replace each {@code answerRequired()} placeholder with the correct code to make the
 * tests pass.
 */
@DisplayName("Tutorial 00: Your First Path")
public class Tutorial00_FirstPath {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required - replace answerRequired() with your solution");
  }

  /**
   * Exercise 1: name the path to a field three records down.
   *
   * <p>With navigators, each record's components chain on as method calls, so the path reads like
   * the accessor chain {@code order.customer().email().value()}. The result is a value you can keep
   * and use on any order.
   *
   * <p>Task: name the path from an order to the text of its customer's email address.
   *
   * <pre>
   *   // Nudge:    one call per record the path passes through, ending at the text itself.
   *   // Strategy: start from OrderFocus, and end with the EmailAddress record's value().
   *   // Spoiler:  OrderFocus.customer().email().value()
   * </pre>
   */
  @Test
  @DisplayName("Exercise 1: name the path to a field three records down")
  void exercise1_nameTheEmailPath() {
    // TODO: Replace answerRequired() with the path from an order to its customer's email text
    FocusPath<Order, String> email = answerRequired();

    assertThat(email.get(ORDER)).isEqualTo("ada@example.com");
  }

  /**
   * Exercise 2: write through a path, and get a new order back.
   *
   * <p>A write through a path returns a new order. Everything the path does not pass through is the
   * same object as before, so a write costs only the records on the way down.
   *
   * <p>Task: rename the customer of {@code ORDER} to "Ada Lovelace".
   *
   * <pre>
   *   // Nudge:    the name sits one record down, beside the email.
   *   // Strategy: a FocusPath writes with set(value, source).
   *   // Spoiler:  OrderFocus.customer().name().set("Ada Lovelace", ORDER)
   * </pre>
   */
  @Test
  @DisplayName("Exercise 2: write through a path, and get a new order back")
  void exercise2_renameTheCustomer() {
    // TODO: Replace answerRequired() with ORDER, its customer renamed to "Ada Lovelace"
    Order renamed = answerRequired();

    assertThat(renamed.customer().name()).isEqualTo("Ada Lovelace");
    assertThat(renamed.customer().email()).isSameAs(ORDER.customer().email());
    assertThat(renamed.lines()).isSameAs(ORDER.lines());
    assertThat(ORDER.customer().name()).isEqualTo("Ada");
  }

  /**
   * Exercise 3: update every element of a list.
   *
   * <p>A {@code List} field's path is already on its elements: {@code OrderFocus.lines()} reaches
   * every line, and {@code .via(...)} takes the next hop from each one.
   *
   * <p>Task: double the quantity of every line of {@code ORDER}, with one path.
   *
   * <pre>
   *   // Nudge:    reach every line, then one field of each, then change them all at once.
   *   // Strategy: OrderFocus.lines().via(LineItemFocus.quantity()) reaches every quantity, and
   *   //           modifyAll(f, source) changes each one.
   *   // Spoiler:  OrderFocus.lines().via(LineItemFocus.quantity()).modifyAll(q -> q * 2, ORDER)
   * </pre>
   */
  @Test
  @DisplayName("Exercise 3: update every element of a list")
  void exercise3_doubleEveryQuantity() {
    // TODO: Replace answerRequired() with ORDER, every line's quantity doubled
    Order doubled = answerRequired();

    assertThat(doubled.lines()).extracting(LineItem::quantity).containsExactly(2, 8);
  }

  /**
   * Congratulations! You've completed Tutorial 00: Your First Path
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>✓ How a generated Focus path names a field several records down
   *   <li>✓ That a write returns a new order and reuses everything off the path
   *   <li>✓ Why the hop after a {@code List} field goes through {@code .via(...)}
   * </ul>
   *
   * <p>Next: Tutorial 01 - Lens Basics, which opens up the lens each step of a path is made of.
   */
}
