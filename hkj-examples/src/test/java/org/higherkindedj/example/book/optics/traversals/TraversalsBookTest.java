// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.traversals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the Traversals page makes about its league and its price lists. */
@DisplayName("the Traversals page: a composed traversal, and partsOf")
class TraversalsBookTest {

  private static final League LEAGUE =
      new League(
          "Pro League",
          List.of(
              new Team("Team Alpha", List.of(new Player("Alice", 100), new Player("Bob", 90))),
              new Team(
                  "Team Bravo", List.of(new Player("Charlie", 110), new Player("Diana", 120)))));

  private static final List<Product> PRODUCTS =
      List.of(
          product("Widget", "100.00", "tools", 5),
          product("Gadget", "200.00", "tools", 0),
          product("Gizmo", "300.00", "toys", 2),
          product("Doohickey", "400.00", "toys", 7),
          product("Thingummy", "500.00", "spares", 0));

  private static Product product(String name, String price, String tag, int stock) {
    return new Product(name, new BigDecimal(price), tag, stock);
  }

  private static List<BigDecimal> prices(List<Product> products) {
    return products.stream().map(Product::price).toList();
  }

  private static List<BigDecimal> decimals(String... values) {
    return Arrays.stream(values).map(BigDecimal::new).toList();
  }

  @Test
  @DisplayName("modify adds 5 to every score and rebuilds the league, keeping every name")
  void modifyAddsTheBonusEverywhere() {
    League updated = TraversalsBook.addBonus(LEAGUE).updatedLeague();

    assertThat(updated.name()).isEqualTo("Pro League");
    assertThat(updated.teams()).extracting(Team::name).containsExactly("Team Alpha", "Team Bravo");
    assertThat(updated.teams().stream().flatMap(team -> team.players().stream()).toList())
        .containsExactly(
            new Player("Alice", 105),
            new Player("Bob", 95),
            new Player("Charlie", 115),
            new Player("Diana", 125));
  }

  @Test
  @DisplayName("getAll reads every score, in the order the traversal visits them")
  void getAllReadsEveryScore() {
    assertThat(TraversalsBook.addBonus(LEAGUE).allScores()).containsExactly(100, 90, 110, 120);
  }

  @Test
  @DisplayName("partsOf reads, sorts and writes back every price across both categories")
  void partsOfSortsAcrossCategories() {
    TraversalsBook.SortedPrices sorted =
        TraversalsBook.sortAllPrices(TraversalsBook.springCatalogue());

    // Scale is part of the claim: the page prints each price with two decimal places.
    assertThat(sorted.allPricesList())
        .containsExactlyElementsOf(
            decimals("999.99", "499.99", "799.99", "29.99", "49.99", "19.99"));
    assertThat(sorted.sortedPrices())
        .containsExactlyElementsOf(
            decimals("19.99", "29.99", "49.99", "499.99", "799.99", "999.99"));
    // the first product gets the lowest price, whichever category it is in
    assertThat(
            sorted.sortedCatalogue().categories().stream()
                .flatMap(category -> category.products().stream())
                .toList())
        .extracting(Product::name, Product::price)
        .containsExactly(
            tuple("Laptop", new BigDecimal("19.99")),
            tuple("Tablet", new BigDecimal("29.99")),
            tuple("Phone", new BigDecimal("49.99")),
            tuple("Case", new BigDecimal("499.99")),
            tuple("Charger", new BigDecimal("799.99")),
            tuple("Cable", new BigDecimal("999.99")));
  }

  @Test
  @DisplayName("a shorter list fills the first positions and leaves the rest as they were")
  void fewerValuesLeaveTheRest() {
    List<Product> result = TraversalsBook.fewerValuesThanPositions(PRODUCTS);

    assertThat(prices(result))
        .containsExactlyElementsOf(decimals("10.00", "20.00", "30.00", "400.00", "500.00"));
    assertThat(result)
        .extracting(Product::name)
        .containsExactly("Widget", "Gadget", "Gizmo", "Doohickey", "Thingummy");
  }

  @Test
  @DisplayName("a longer list fills every position and the extra values are never read")
  void moreValuesAreIgnored() {
    List<Product> trimmed = TraversalsBook.moreValuesThanPositions(PRODUCTS);

    assertThat(trimmed).hasSize(3);
    assertThat(prices(trimmed)).containsExactlyElementsOf(decimals("10.00", "20.00", "30.00"));
  }
}
