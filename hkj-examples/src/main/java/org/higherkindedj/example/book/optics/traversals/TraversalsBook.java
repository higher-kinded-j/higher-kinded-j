// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.traversals;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.util.Traversals;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/traversals.html">Traversals</a> page. The
 * page {@code {{#include}}}s the anchored regions, and {@code TraversalsBookTest} holds the claims
 * the page makes about this code.
 */
public final class TraversalsBook {

  private TraversalsBook() {}

  /** The league after the bonus, and every score read from the league before it. */
  record Bonus(League updatedLeague, List<Integer> allScores) {}

  static Bonus addBonus(League league) {
    // ANCHOR: compose
    // Get generated optics
    Traversal<League, Team> leagueToTeams = LeagueTraversals.teams();
    Traversal<Team, Player> teamToPlayers = TeamTraversals.players();
    Lens<Player, Integer> playerToScore = PlayerLenses.score();

    // Compose them to create a single, deep traversal.
    Traversal<League, Integer> leagueToAllPlayerScores =
        leagueToTeams
            .andThen(teamToPlayers)
            .andThen(playerToScore); // a Lens after a Traversal: still a Traversal
    // ANCHOR_END: compose

    // ANCHOR: modify
    // Use the composed traversal to add 5 bonus points to every score.
    League updatedLeague = Traversals.modify(leagueToAllPlayerScores, score -> score + 5, league);
    // ANCHOR_END: modify

    // ANCHOR: get_all
    // Get a flat list of all player scores in the league.
    List<Integer> allScores = Traversals.getAll(leagueToAllPlayerScores, league);
    // Result: [100, 90, 110, 120]
    // ANCHOR_END: get_all
    return new Bonus(updatedLeague, allScores);
  }

  /** The prices read as one list, that list sorted, and the catalogue with it written back. */
  record SortedPrices(
      List<BigDecimal> allPricesList, List<BigDecimal> sortedPrices, Catalogue sortedCatalogue) {}

  /** The two-category catalogue the page sorts with {@code partsOf}. */
  static Catalogue springCatalogue() {
    // ANCHOR: spring_catalogue
    // Two categories of three products, priced in no particular order
    Catalogue spring =
        new Catalogue(
            "Spring",
            List.of(
                new Category(
                    "Electronics",
                    List.of(
                        new Product("Laptop", new BigDecimal("999.99"), "computers", 3),
                        new Product("Tablet", new BigDecimal("499.99"), "computers", 8),
                        new Product("Phone", new BigDecimal("799.99"), "phones", 5))),
                new Category(
                    "Accessories",
                    List.of(
                        new Product("Case", new BigDecimal("29.99"), "phones", 40),
                        new Product("Charger", new BigDecimal("49.99"), "phones", 25),
                        new Product("Cable", new BigDecimal("19.99"), "computers", 60)))));
    // ANCHOR_END: spring_catalogue
    return spring;
  }

  static SortedPrices sortAllPrices(Catalogue spring) {
    Traversal<Catalogue, BigDecimal> allPrices =
        CatalogueTraversals.categories()
            .andThen(CategoryTraversals.products())
            .andThen(ProductLenses.price());

    // ANCHOR: parts_of
    // Convert traversal to a lens on the list of all prices
    Lens<Catalogue, List<BigDecimal>> pricesLens = Traversals.partsOf(allPrices);

    // Get all prices as a list
    List<BigDecimal> allPricesList = pricesLens.get(spring);
    // Result: [999.99, 499.99, 799.99, 29.99, 49.99, 19.99]

    // Sort the list
    List<BigDecimal> sortedPrices = new ArrayList<>(allPricesList);
    Collections.sort(sortedPrices);
    // Result: [19.99, 29.99, 49.99, 499.99, 799.99, 999.99]

    // Set the sorted prices back
    Catalogue sortedCatalogue = pricesLens.set(sortedPrices, spring);
    // ANCHOR_END: parts_of
    return new SortedPrices(allPricesList, sortedPrices, sortedCatalogue);
  }

  /** Three new prices written over five products. */
  static List<Product> fewerValuesThanPositions(List<Product> products) {
    Traversal<List<Product>, BigDecimal> priceTraversal =
        Traversals.<Product>forList().andThen(ProductLenses.price());

    // ANCHOR: fewer_values
    Lens<List<Product>, List<BigDecimal>> productPrices = Traversals.partsOf(priceTraversal);

    // Original: 5 products with prices [100.00, 200.00, 300.00, 400.00, 500.00]
    List<BigDecimal> partialPrices =
        List.of(new BigDecimal("10.00"), new BigDecimal("20.00"), new BigDecimal("30.00"));

    List<Product> result = productPrices.set(partialPrices, products);
    // Result prices: [10.00, 20.00, 30.00, 400.00, 500.00]
    // First 3 updated, last 2 unchanged
    // ANCHOR_END: fewer_values
    return result;
  }

  /** Five new prices written over the first three products. */
  static List<Product> moreValuesThanPositions(List<Product> products) {
    Traversal<List<Product>, BigDecimal> priceTraversal =
        Traversals.<Product>forList().andThen(ProductLenses.price());
    Lens<List<Product>, List<BigDecimal>> productPrices = Traversals.partsOf(priceTraversal);

    // ANCHOR: more_values
    // A three-product source, prices [100.00, 200.00, 300.00]
    List<Product> threeProducts = products.subList(0, 3);
    List<BigDecimal> extraPrices =
        List.of(
            new BigDecimal("10.00"),
            new BigDecimal("20.00"),
            new BigDecimal("30.00"),
            new BigDecimal("40.00"),
            new BigDecimal("50.00"));

    List<Product> trimmed = productPrices.set(extraPrices, threeProducts);
    // Result prices: [10.00, 20.00, 30.00]
    // Only the first 3 values are consumed; 40.00 and 50.00 are never read
    // ANCHOR_END: more_values
    return trimmed;
  }
}

@GenerateLenses
record Player(String name, int score) {}

@GenerateLenses
@GenerateTraversals
record Team(String name, List<Player> players) {}

@GenerateLenses
@GenerateTraversals
record League(String name, List<Team> teams) {}

@GenerateLenses
record Product(String name, BigDecimal price, String tag, int stockLevel) {}

@GenerateLenses
@GenerateTraversals
record Category(String name, List<Product> products) {}

@GenerateLenses
@GenerateTraversals
record Catalogue(String name, List<Category> categories) {}
