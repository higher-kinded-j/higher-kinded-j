// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.indexed;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.optics.indexed.IndexedTraversal;
import org.higherkindedj.optics.indexed.Pair;
import org.higherkindedj.optics.util.IndexedTraversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Holds the claims the Indexed Optics page makes: the pairs each indexed optic yields, what its
 * {@code imodify} returns, and what the page's examples print.
 */
@DisplayName("the Indexed Optics page: every value with its index")
class IndexedOpticsBookTest {

  private static final IndexedTraversal<Integer, List<LineItem>, LineItem> ITEMS_WITH_INDEX =
      IndexedTraversals.forList();

  private static final List<LineItem> ITEMS =
      quietly(() -> IndexedOpticsBook.positions(ITEMS_WITH_INDEX)).items();

  /** Runs an example with what it prints discarded, and returns what it returns. */
  private static <T> T quietly(Supplier<T> example) {
    PrintStream original = System.out;
    System.setOut(new PrintStream(OutputStream.nullOutputStream(), true, StandardCharsets.UTF_8));
    try {
      return example.get();
    } finally {
      System.setOut(original);
    }
  }

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

  private static List<String> skusAt(List<Pair<Integer, LineItem>> pairs) {
    return pairs.stream().map(p -> p.first() + " " + p.second().sku()).toList();
  }

  @Test
  @DisplayName("toIndexedList pairs each item with its zero-based position")
  void positions() {
    assertThat(printed(() -> IndexedOpticsBook.positions(ITEMS_WITH_INDEX)))
        .containsExactly("Position 0: LAPTOP", "Position 1: MOUSE", "Position 2: KEYBOARD");
  }

  @Test
  @DisplayName("the indexed fold finds the item at index 1, and an expensive even-positioned one")
  void foldQueries() {
    assertThat(printed(() -> IndexedOpticsBook.foldQueries(ITEMS_WITH_INDEX, ITEMS)))
        .containsExactly("Item at index 1: MOUSE");
    IndexedOpticsBook.FoldQueries queries = IndexedOpticsBook.foldQueries(ITEMS_WITH_INDEX, ITEMS);
    assertThat(queries.found().second().sku()).isEqualTo("MOUSE");
    assertThat(queries.hasExpensiveEven()).as("the laptop, at index 0, costs over 500").isTrue();
  }

  @Test
  @DisplayName("imodify numbers each item from its index")
  void numbering() {
    assertThat(printed(() -> IndexedOpticsBook.numbered(ITEMS)))
        .containsExactly("Item 1: LAPTOP", "Item 2: MOUSE", "Item 3: KEYBOARD");
  }

  @Test
  @DisplayName("imodify on a map hands each value its key")
  void keyAware() {
    assertThat(IndexedOpticsBook.keyAware())
        .isEqualTo(
            Map.of(
                "priority", "[priority] express",
                "gift-wrap", "[gift-wrap] true",
                "delivery-note", "[delivery-note] Leave at door"));
  }

  @Test
  @DisplayName("filterIndex focuses the even positions, and modify changes only those")
  void evenPositions() {
    IndexedOpticsBook.Filtered even = IndexedOpticsBook.evenPositions(ITEMS_WITH_INDEX, ITEMS);

    assertThat(skusAt(even.focused())).containsExactly("0 LAPTOP", "2 KEYBOARD");
    assertThat(even.result().stream().map(line -> line.sku() + " " + line.price()))
        .containsExactly("LAPTOP 989.99", "MOUSE 24.99", "KEYBOARD 69.99");
  }

  @Test
  @DisplayName("filteredWithIndex keeps the original indices rather than renumbering them")
  void expensive() {
    assertThat(skusAt(IndexedOpticsBook.expensive(ITEMS_WITH_INDEX, ITEMS)))
        .containsExactly("0 LAPTOP", "2 KEYBOARD");
  }

  @Test
  @DisplayName("filterIndex on a map focuses the keys that match")
  void deliveryEntries() {
    Map<String, String> metadata =
        Map.of("priority", "express", "gift-wrap", "true", "delivery-note", "Leave at door");

    List<Pair<String, String>> entries =
        IndexedOpticsBook.deliveryEntries(IndexedTraversals.forMap(), metadata);

    assertThat(entries).containsExactly(Pair.of("delivery-note", "Leave at door"));
    assertThat(entries).hasToString("[Pair[first=delivery-note, second=Leave at door]]");
  }

  @Test
  @DisplayName("an indexed lens reads its field's name with the value, and hands both to imodify")
  void fieldTracking() {
    assertThat(printed(IndexedOpticsBook::fieldTracking))
        .containsExactly(
            "Field: email",
            "Value: ada@example.com",
            "Updating field 'email' from ada@example.com");
    IndexedOpticsBook.LensRead read = IndexedOpticsBook.fieldTracking();
    assertThat(read.fieldInfo()).isEqualTo(Pair.of("email", new EmailAddress("ada@example.com")));
    assertThat(read.updated())
        .isEqualTo(new Customer("Ada", new EmailAddress("ada.lovelace@example.com")));
  }

  @Test
  @DisplayName("the patterns number tasks, show a sorted map's entries, and take odd positions")
  void patterns() {
    assertThat(IndexedOpticsBook.sequenceNumbers())
        .hasToString("[1. Review PR, 2. Update docs, 3. Run tests]");
    assertThat(IndexedOpticsBook.keyValueDisplay())
        .hasToString("[alice scored 100, bob scored 85, charlie scored 92]");
    assertThat(IndexedOpticsBook.oddPositions()).containsExactly("b", "d", "f");
  }
}
