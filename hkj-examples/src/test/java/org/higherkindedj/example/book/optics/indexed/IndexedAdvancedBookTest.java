// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.indexed;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.higherkindedj.example.book.optics.indexed.IndexedAdvancedBook.Customer;
import org.higherkindedj.example.book.optics.indexed.IndexedAdvancedBook.LineItem;
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

  @Test
  @DisplayName("iandThen pairs the order index with the item index, outer first")
  void pairedIndices() {
    assertThat(printed(IndexedAdvancedBook::pairedIndices))
        .containsExactly(
            "Order 0, Item 0: Laptop",
            "Order 0, Item 1: Mouse",
            "Order 1, Item 0: Keyboard",
            "Order 1, Item 1: Monitor",
            "Order 1, Item 2: Cable");
  }

  @Test
  @DisplayName("imodify turns the zero-based index into a one-based display number")
  void oneBased() {
    List<LineItem> items =
        List.of(
            new LineItem("Laptop", 1, new BigDecimal("999.99")),
            new LineItem("Mouse", 1, new BigDecimal("24.99")),
            new LineItem("Keyboard", 1, new BigDecimal("79.99")));

    assertThat(IndexedAdvancedBook.oneBased(items).stream().map(LineItem::productName))
        .containsExactly("Item 1: Laptop", "Item 2: Mouse", "Item 3: Keyboard");
  }

  @Test
  @DisplayName("filterIndex and filtered layer: even positions that are also expensive")
  void layeredFilters() {
    assertThat(
            IndexedAdvancedBook.layeredFilters().stream()
                .map(p -> p.first() + " " + p.second().productName()))
        .containsExactly("0 Laptop", "2 Keyboard", "4 Monitor");
  }

  @Test
  @DisplayName("the audit trail records the field's name and old value, and prints each change")
  void auditTrail() {
    List<String> lines = printed(IndexedAdvancedBook::auditTrail);
    assertThat(lines).hasSize(1);
    assertThat(lines.getFirst())
        .matches("Field 'email' changed from alice@old\\.com to alice@new\\.com at \\S+Z");

    IndexedAdvancedBook.Audited audited = IndexedAdvancedBook.auditTrail();
    assertThat(audited.updated()).isEqualTo(new Customer("Alice", "alice@new.com"));
    assertThat(audited.audit())
        .singleElement()
        .satisfies(
            change -> {
              assertThat(change.fieldName()).isEqualTo("email");
              assertThat(change.oldValue()).isEqualTo("alice@old.com");
              assertThat(change.newValue()).isEqualTo("alice@new.com");
            });
  }

  @Test
  @DisplayName("the full indexed path prints buyer, order and item for every price it raises")
  void pathTracking() {
    assertThat(printed(IndexedAdvancedBook::pathTracking))
        .containsExactly(
            "Updating price at [buyer=0, order=0, item=0]: 999.99 -> 1099.99",
            "Updating price at [buyer=0, order=0, item=1]: 24.99 -> 27.49",
            "Updating price at [buyer=0, order=1, item=0]: 79.99 -> 87.99");
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
