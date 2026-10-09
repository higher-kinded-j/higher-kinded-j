// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.indexed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.order;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.optics.indexed.Pair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Holds the claims the Indexed Optics: Advanced Patterns page makes: the paired index paths a
 * composition yields, what its layered filters focus, and what its examples print.
 */
@DisplayName("the Indexed Optics: Advanced Patterns page: indices paired through a composition")
class IndexedAdvancedBookTest {

  /** The lines an example prints to standard output while it runs. */
  private static List<String> printed(Runnable example) {
    PrintStream original = System.out;
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
    try {
      example.run();
    } finally {
      System.setOut(original);
    }
    return buffer.toString(StandardCharsets.UTF_8).lines().toList();
  }

  private static LineItem line(String sku, int quantity, String price) {
    return new LineItem(sku, quantity, new BigDecimal(price));
  }

  @Test
  @DisplayName("iandThen pairs the order index with the item index, outer first")
  void pairedIndices() {
    List<Order> orders =
        List.of(
            order(List.of(line("LAPTOP", 1, "999.99"), line("MOUSE", 1, "24.99"))),
            order(
                List.of(
                    line("KEYBOARD", 1, "79.99"),
                    line("MONITOR", 1, "299.99"),
                    line("CABLE", 2, "9.99"))));

    assertThat(printed(() -> IndexedAdvancedBook.pairedIndices(orders)))
        .containsExactly(
            "Order 0, Item 0: LAPTOP",
            "Order 0, Item 1: MOUSE",
            "Order 1, Item 0: KEYBOARD",
            "Order 1, Item 1: MONITOR",
            "Order 1, Item 2: CABLE");
  }

  @Test
  @DisplayName("imodify turns the zero-based index into a one-based display number")
  void oneBased() {
    List<LineItem> items =
        List.of(
            line("LAPTOP", 1, "999.99"), line("MOUSE", 1, "24.99"), line("KEYBOARD", 1, "79.99"));

    assertThat(IndexedAdvancedBook.oneBased(items))
        .containsExactly("Item 1: LAPTOP", "Item 2: MOUSE", "Item 3: KEYBOARD");
  }

  @Test
  @DisplayName("filterIndex and filtered layer: even positions that are also expensive")
  void layeredFilters() {
    assertThat(
            IndexedAdvancedBook.layeredFilters().stream()
                .map(p -> p.first() + " " + p.second().sku()))
        .containsExactly("0 LAPTOP", "2 KEYBOARD", "4 MONITOR");
  }

  @Test
  @DisplayName("the audit trail records the field's name and old value, and prints each change")
  void auditTrail() {
    List<String> lines = printed(IndexedAdvancedBook::auditTrail);
    assertThat(lines).hasSize(1);
    assertThat(lines.getFirst()).matches("Field 'name' changed from Ada to Ada Lovelace at \\S+Z");

    IndexedAdvancedBook.Audited audited = IndexedAdvancedBook.auditTrail();
    assertThat(audited.updated())
        .isEqualTo(new Customer("Ada Lovelace", new EmailAddress("ada@example.com")));
    assertThat(audited.audit())
        .singleElement()
        .satisfies(
            change -> {
              assertThat(change.fieldName()).isEqualTo("name");
              assertThat(change.oldValue()).isEqualTo("Ada");
              assertThat(change.newValue()).isEqualTo("Ada Lovelace");
            });
  }

  @Test
  @DisplayName("the full indexed path prints customer, order and item for every price it raises")
  void pathTracking() {
    List<Order> adasOrders =
        List.of(
            order(List.of(line("LAPTOP", 1, "999.99"), line("MOUSE", 1, "24.99"))),
            order(List.of(line("KEYBOARD", 1, "79.99"))));

    assertThat(printed(() -> IndexedAdvancedBook.pathTracking(adasOrders)))
        .containsExactly(
            "Updating price at [history=0, order=0, item=0]: 999.99 -> 1099.99",
            "Updating price at [history=0, order=0, item=1]: 24.99 -> 27.49",
            "Updating price at [history=0, order=1, item=0]: 79.99 -> 87.99");
  }

  @Test
  @DisplayName("Pair reads, replaces and swaps its components")
  void pairUtilities() {
    IndexedAdvancedBook.PairUtilities pairs = IndexedAdvancedBook.pairUtilities();

    assertThat(pairs.first()).isEqualTo(1);
    assertThat(pairs.second()).isEqualTo("Hello");
    assertThat(pairs.modified())
        .isEqualTo(Pair.of(1, "World"))
        .hasToString("Pair[first=1, second=World]");
    assertThat(pairs.transformed())
        .isEqualTo(Pair.of("One", "Hello"))
        .hasToString("Pair[first=One, second=Hello]");
    assertThat(pairs.swapped())
        .isEqualTo(Pair.of("Hello", 1))
        .hasToString("Pair[first=Hello, second=1]");
  }
}
