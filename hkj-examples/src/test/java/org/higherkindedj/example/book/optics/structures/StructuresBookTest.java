// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.structures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.higherkindedj.hkt.tuple.Tuple2;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Holds the claims the Common Data Structure Traversals page makes about its Optional, Map and
 * Tuple2 examples.
 */
@DisplayName("the Common Data Structure Traversals page: Optional, Map values and Tuple2")
class StructuresBookTest {

  @Test
  @DisplayName("forOptional moves a present value, leaves an empty one alone, and reads 0 or 1")
  void optionalTraversal() {
    StructuresBook.OptionalResults results = StructuresBook.optionalTraversal();

    assertThat(results.offsetPort()).contains(9080);
    assertThat(results.offsetPort()).hasToString("Optional[9080]");
    assertThat(results.stillEmpty()).isEmpty();
    assertThat(results.stillEmpty()).hasToString("Optional.empty");
    assertThat(results.values()).containsExactly(8080);
    assertThat(Traversals.getAll(Traversals.<Integer>forOptional(), Optional.empty())).isEmpty();
  }

  @Test
  @DisplayName("the stream version raises every price by 10% under the same keys")
  void streamIncrease() {
    StructuresBook.StreamIncrease increase = StructuresBook.streamIncrease();

    assertThat(increase.inflated()).containsOnlyKeys("widget", "gadget", "gizmo");
    // to whole pence: the scale is 2, not the 3 that multiplying by 1.1 leaves
    assertThat(increase.inflated().get("widget")).isEqualTo(new BigDecimal("11.00"));
    assertThat(increase.inflated().get("gadget")).isEqualTo(new BigDecimal("27.50"));
    assertThat(increase.inflated().get("gizmo")).isEqualTo(new BigDecimal("16.50"));
  }

  @Test
  @DisplayName("forMapValues changes every value, keeps the keys, and keeps the source's order")
  void mapValues() {
    Map<String, BigDecimal> prices = StructuresBook.streamIncrease().prices();

    StructuresBook.MapResults results = StructuresBook.mapValues(prices);

    // Scale is part of these claims: BigDecimal equality compares it, and the page prints it.
    assertThat(results.inflated())
        .containsExactly(
            entry("gadget", new BigDecimal("26.50")),
            entry("gizmo", new BigDecimal("16.50")),
            entry("widget", new BigDecimal("11.50")));
    assertThat(results.inflated()).hasToString("{gadget=26.50, gizmo=16.50, widget=11.50}");
    assertThat(results.allPrices())
        .containsExactly(new BigDecimal("25.00"), new BigDecimal("15.00"), new BigDecimal("10.00"));
    assertThat(results.discounted())
        .containsExactly(
            entry("gadget", new BigDecimal("22.50")),
            entry("gizmo", new BigDecimal("15.00")),
            entry("widget", new BigDecimal("10.00")));
  }

  @Test
  @DisplayName("TupleTraversals.both changes and reads both elements of a same-typed pair")
  void bothElements() {
    StructuresBook.TupleResults results = StructuresBook.bothElements();

    assertThat(results.doubled()).isEqualTo(new Tuple2<>(20, 40));
    assertThat(results.doubled()).hasToString("Tuple2[_1=20, _2=40]");
    assertThat(results.values()).containsExactly(10, 20);
    assertThat(results.capitalised()).isEqualTo(new Tuple2<>("Alice", "Bob"));
    assertThat(results.capitalised()).hasToString("Tuple2[_1=Alice, _2=Bob]");
  }

  @Test
  @DisplayName("the page's price list iterates in key order, the order its results print in")
  void pricesAreThePagesThree() {
    assertThat(StructuresBook.streamIncrease().prices())
        .containsExactly(
            entry("gadget", new BigDecimal("25.00")),
            entry("gizmo", new BigDecimal("15.00")),
            entry("widget", new BigDecimal("10.00")));
  }
}
