// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.optics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.ORDER;

import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.LineItemFocus;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderFocus;
import org.higherkindedj.optics.focus.FocusPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Solutions for Tutorial 00: Your first path.
 *
 * <p>The pattern throughout: start from the generated {@code OrderFocus}, chain each record hop as
 * a method call, and step from a list into its elements with {@code .via(...)}.
 */
@DisplayName("Tutorial 00: Your First Path (Solutions)")
public class Tutorial00_FirstPath_Solution {

  /**
   * Why this is idiomatic: the path is a value of its own, built once from the generated {@code
   * OrderFocus}, and its type says what it starts from and what it reaches. The same {@code email}
   * reads this order, writes it, or works on any other order.
   *
   * <p>Alternative: {@code ORDER.customer().email().value()} reads the same text. It is shorter for
   * one read, but it is not a value you can write through.
   *
   * <p>Common wrong attempt: stopping at {@code OrderFocus.customer().email()}. That reaches the
   * {@code EmailAddress} record, not its text, so it does not compile as a {@code FocusPath<Order,
   * String>}; {@code value()} is the last hop.
   */
  @Test
  @DisplayName("Exercise 1: name the path to a field three records down")
  void exercise1_nameTheEmailPath() {
    FocusPath<Order, String> email = OrderFocus.customer().email().value();

    assertThat(email.get(ORDER)).isEqualTo("ada@example.com");
  }

  /**
   * Why this is idiomatic: one {@code set} rebuilds the customer and the order, and nothing else.
   * The email and the lines in the new order are the very objects the old one held.
   *
   * <p>Alternative: {@code new Order(...)} around {@code new Customer("Ada Lovelace",
   * order.customer().email())}, copying the other five components by hand. Same result, and every
   * new component of {@code Order} means editing it.
   *
   * <p>Common wrong attempt: {@code set(ORDER, "Ada Lovelace")}. A path's methods take the value
   * first and the source last, so the arguments do not compile the other way round.
   */
  @Test
  @DisplayName("Exercise 2: write through a path, and get a new order back")
  void exercise2_renameTheCustomer() {
    Order renamed = OrderFocus.customer().name().set("Ada Lovelace", ORDER);

    assertThat(renamed.customer().name()).isEqualTo("Ada Lovelace");
    assertThat(renamed.customer().email()).isSameAs(ORDER.customer().email());
    assertThat(renamed.lines()).isSameAs(ORDER.lines());
    assertThat(ORDER.customer().name()).isEqualTo("Ada");
  }

  /**
   * Why this is idiomatic: {@code OrderFocus.lines()} already reaches every line, so one {@code
   * .via(...)} names every quantity, and {@code modifyAll} changes them all and rebuilds the order.
   *
   * <p>Alternative: stream the lines, map each one to a new {@code LineItem}, collect the list and
   * build a new {@code Order} around it. Same result, with the rebuild written out twice.
   *
   * <p>Common wrong attempt: {@code OrderFocus.lines().each()}. The generated method already ends
   * in {@code .each()}, so a second one tries to read a {@code LineItem} as a {@code List}: it
   * compiles and then throws {@code ClassCastException} at run time.
   */
  @Test
  @DisplayName("Exercise 3: update every element of a list")
  void exercise3_doubleEveryQuantity() {
    Order doubled = OrderFocus.lines().via(LineItemFocus.quantity()).modifyAll(q -> q * 2, ORDER);

    assertThat(doubled.lines()).extracting(LineItem::quantity).containsExactly(2, 8);
  }
}
