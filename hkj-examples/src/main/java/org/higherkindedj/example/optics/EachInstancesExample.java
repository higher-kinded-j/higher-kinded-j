// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.util.*;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Each;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.each.EachInstances;
import org.higherkindedj.optics.extensions.EachExtensions;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.indexed.IndexedTraversal;
import org.higherkindedj.optics.util.IndexedTraversals;
import org.higherkindedj.optics.util.Traversals;

/**
 * A runnable example demonstrating the Each typeclass for canonical element-wise traversal.
 *
 * <p>This example showcases:
 *
 * <ul>
 *   <li><b>EachInstances</b> for Java standard types (List, Set, Map, Optional, arrays, Stream,
 *       String)
 *   <li><b>EachExtensions</b> for HKT types (Maybe, Either, Try, Validated)
 *   <li><b>IndexedTraversal</b> via {@code eachWithIndex()} for position-aware operations
 *   <li><b>Focus DSL integration</b> using {@code .each(Each)} method
 *   <li>Creating custom Each instances
 * </ul>
 *
 * <p>Key concepts demonstrated:
 *
 * <ul>
 *   <li>Getting canonical traversals with {@code Each.each()}
 *   <li>Checking for indexed support with {@code Each.supportsIndexed()}
 *   <li>Using {@code Each.eachWithIndex()} for containers with meaningful indices
 *   <li>Wrapping existing traversals with {@code Each.fromTraversal()}
 *   <li>Integrating with Focus DSL paths
 * </ul>
 */
public class EachInstancesExample {

  // Domain models
  public record Product(String name, double price) {}

  public record Order(String id, List<Product> products) {}

  public record User(String name, Map<String, Order> orders) {}

  public static void main(String[] args) {
    System.out.println("=== Each Typeclass Examples ===\n");

    demonstrateListEach();
    demonstrateSetEach();
    demonstrateMapValuesEach();
    demonstrateOptionalEach();
    demonstrateArrayEach();
    demonstrateStringCharsEach();
    demonstrateMaybeEach();
    demonstrateEitherEach();
    demonstrateTryEach();
    demonstrateValidatedEach();
    demonstrateIndexedTraversal();
    demonstrateFocusDslIntegration();
    demonstrateCustomEach();
  }

  /** Demonstrates Each for List types with indexed traversal support. */
  private static void demonstrateListEach() {
    System.out.println("--- List Each ---");

    Each<List<String>, String> listEach = EachInstances.listEach();
    Traversal<List<String>, String> traversal = listEach.each();

    List<String> names = List.of("alice", "bob", "charlie");

    // Get all elements
    List<String> all = Traversals.getAll(traversal, names);
    System.out.println("All names: " + all);

    // Modify all elements
    List<String> upper = Traversals.modify(traversal, String::toUpperCase, names);
    System.out.println("Uppercased: " + upper);

    // Check indexed support
    System.out.println("Supports indexed: " + listEach.supportsIndexed());

    System.out.println();
  }

  /** Demonstrates Each for Set types (no indexed support). */
  private static void demonstrateSetEach() {
    System.out.println("--- Set Each ---");

    Each<Set<Integer>, Integer> setEach = EachInstances.setEach();
    Traversal<Set<Integer>, Integer> traversal = setEach.each();

    Set<Integer> numbers = Set.of(1, 2, 3, 4, 5);

    // Get all elements
    List<Integer> all = Traversals.getAll(traversal, numbers);
    System.out.println("All numbers: " + all);

    // Modify all elements (double them)
    Set<Integer> doubled = Traversals.modify(traversal, n -> n * 2, numbers);
    System.out.println("Doubled: " + doubled);

    // Check indexed support (Set doesn't have meaningful indices)
    System.out.println("Supports indexed: " + setEach.supportsIndexed());

    System.out.println();
  }

  /** Demonstrates Each for Map values with key-based indexed traversal. */
  private static void demonstrateMapValuesEach() {
    System.out.println("--- Map Values Each ---");

    Each<Map<String, Integer>, Integer> mapEach = EachInstances.mapValuesEach();
    Traversal<Map<String, Integer>, Integer> traversal = mapEach.each();

    Map<String, Integer> scores = new LinkedHashMap<>();
    scores.put("alice", 100);
    scores.put("bob", 85);
    scores.put("charlie", 92);

    // Get all values
    List<Integer> values = Traversals.getAll(traversal, scores);
    System.out.println("All scores: " + values);

    // Add 10 to all scores
    Map<String, Integer> updated = Traversals.modify(traversal, s -> s + 10, scores);
    System.out.println("After bonus: " + updated);

    // Check indexed support (Map uses key as index)
    System.out.println("Supports indexed: " + mapEach.supportsIndexed());

    System.out.println();
  }

  /** Demonstrates Each for Optional (0 or 1 element). */
  private static void demonstrateOptionalEach() {
    System.out.println("--- Optional Each ---");

    Each<Optional<String>, String> optEach = EachInstances.optionalEach();
    Traversal<Optional<String>, String> traversal = optEach.each();

    Optional<String> present = Optional.of("hello");
    Optional<String> empty = Optional.empty();

    // Get all from present
    List<String> fromPresent = Traversals.getAll(traversal, present);
    System.out.println("From present: " + fromPresent);

    // Get all from empty
    List<String> fromEmpty = Traversals.getAll(traversal, empty);
    System.out.println("From empty: " + fromEmpty);

    // Modify present value
    Optional<String> modified = Traversals.modify(traversal, String::toUpperCase, present);
    System.out.println("Modified: " + modified);

    System.out.println();
  }

  /** Demonstrates Each for arrays with indexed traversal support. */
  private static void demonstrateArrayEach() {
    System.out.println("--- Array Each ---");

    Each<Integer[], Integer> arrayEach = EachInstances.arrayEach();
    Traversal<Integer[], Integer> traversal = arrayEach.each();

    Integer[] numbers = {1, 2, 3, 4, 5};

    // Get all elements
    List<Integer> all = Traversals.getAll(traversal, numbers);
    System.out.println("All: " + all);

    // Triple all elements
    Integer[] tripled = Traversals.modify(traversal, n -> n * 3, numbers);
    System.out.println("Tripled: " + Arrays.toString(tripled));

    // Check indexed support
    System.out.println("Supports indexed: " + arrayEach.supportsIndexed());

    System.out.println();
  }

  /** Demonstrates Each for String characters with indexed traversal support. */
  private static void demonstrateStringCharsEach() {
    System.out.println("--- String Chars Each ---");

    Each<String, Character> stringEach = EachInstances.stringCharsEach();
    Traversal<String, Character> traversal = stringEach.each();

    String text = "hello";

    // Get all characters
    List<Character> chars = Traversals.getAll(traversal, text);
    System.out.println("Characters: " + chars);

    // Uppercase all characters
    String upper = Traversals.modify(traversal, Character::toUpperCase, text);
    System.out.println("Uppercased: " + upper);

    // Check indexed support
    System.out.println("Supports indexed: " + stringEach.supportsIndexed());

    System.out.println();
  }

  /** Demonstrates Each for Maybe (HKT type). */
  private static void demonstrateMaybeEach() {
    System.out.println("--- Maybe Each (via EachExtensions) ---");

    Each<Maybe<String>, String> maybeEach = EachExtensions.maybeEach();
    Traversal<Maybe<String>, String> traversal = maybeEach.each();

    Maybe<String> just = Maybe.just("world");
    Maybe<String> nothing = Maybe.nothing();

    // Get all from just
    List<String> fromJust = Traversals.getAll(traversal, just);
    System.out.println("From just: " + fromJust);

    // Get all from nothing
    List<String> fromNothing = Traversals.getAll(traversal, nothing);
    System.out.println("From nothing: " + fromNothing);

    // Modify
    Maybe<String> modified = Traversals.modify(traversal, s -> "Hello, " + s + "!", just);
    System.out.println("Modified: " + modified);

    System.out.println();
  }

  /** Demonstrates Each for Either right values. */
  private static void demonstrateEitherEach() {
    System.out.println("--- Either Right Each (via EachExtensions) ---");

    Each<Either<String, Integer>, Integer> eitherEach = EachExtensions.eitherRightEach();
    Traversal<Either<String, Integer>, Integer> traversal = eitherEach.each();

    Either<String, Integer> right = Either.right(42);
    Either<String, Integer> left = Either.left("error");

    // Get from right
    List<Integer> fromRight = Traversals.getAll(traversal, right);
    System.out.println("From right: " + fromRight);

    // Get from left (empty - no right value)
    List<Integer> fromLeft = Traversals.getAll(traversal, left);
    System.out.println("From left: " + fromLeft);

    // Modify right value
    Either<String, Integer> modified = Traversals.modify(traversal, n -> n * 2, right);
    System.out.println("Modified right: " + modified);

    // Modify left (no change - no right value to modify)
    Either<String, Integer> leftUnchanged = Traversals.modify(traversal, n -> n * 2, left);
    System.out.println("Left unchanged: " + leftUnchanged);

    System.out.println();
  }

  /** Demonstrates Each for Try success values. */
  private static void demonstrateTryEach() {
    System.out.println("--- Try Success Each (via EachExtensions) ---");

    Each<Try<Integer>, Integer> tryEach = EachExtensions.trySuccessEach();
    Traversal<Try<Integer>, Integer> traversal = tryEach.each();

    Try<Integer> success = Try.success(100);
    Try<Integer> failure = Try.failure(new RuntimeException("oops"));

    // Get from success
    List<Integer> fromSuccess = Traversals.getAll(traversal, success);
    System.out.println("From success: " + fromSuccess);

    // Get from failure (empty)
    List<Integer> fromFailure = Traversals.getAll(traversal, failure);
    System.out.println("From failure: " + fromFailure);

    // Modify success
    Try<Integer> modified = Traversals.modify(traversal, n -> n + 50, success);
    System.out.println("Modified success: " + modified);

    System.out.println();
  }

  /** Demonstrates Each for Validated valid values. */
  private static void demonstrateValidatedEach() {
    System.out.println("--- Validated Each (via EachExtensions) ---");

    Each<Validated<List<String>, Integer>, Integer> validatedEach = EachExtensions.validatedEach();
    Traversal<Validated<List<String>, Integer>, Integer> traversal = validatedEach.each();

    Validated<List<String>, Integer> valid = Validated.valid(50);
    Validated<List<String>, Integer> invalid = Validated.invalid(List.of("error1", "error2"));

    // Get from valid
    List<Integer> fromValid = Traversals.getAll(traversal, valid);
    System.out.println("From valid: " + fromValid);

    // Get from invalid (empty)
    List<Integer> fromInvalid = Traversals.getAll(traversal, invalid);
    System.out.println("From invalid: " + fromInvalid);

    // Modify valid
    Validated<List<String>, Integer> modified = Traversals.modify(traversal, n -> n * 3, valid);
    System.out.println("Modified valid: " + modified);

    System.out.println();
  }

  /** Demonstrates indexed traversal via eachWithIndex(). */
  private static void demonstrateIndexedTraversal() {
    System.out.println("--- Indexed Traversal via eachWithIndex() ---");

    Each<List<String>, String> listEach = EachInstances.listEach();

    List<String> items = List.of("apple", "banana", "cherry");

    // Use eachWithIndex for position-aware operations
    listEach
        .<Integer>eachWithIndex()
        .ifPresent(
            indexed -> {
              // Number each element (1-based)
              List<String> numbered =
                  IndexedTraversals.imodify(
                      indexed, (index, value) -> (index + 1) + ". " + value, items);

              System.out.println("Numbered items:");
              for (String item : numbered) {
                System.out.println("  " + item);
              }
            });

    // Map with key as index
    Each<Map<String, Double>, Double> mapEach = EachInstances.mapValuesEach();

    Map<String, Double> prices = new LinkedHashMap<>();
    prices.put("laptop", 999.99);
    prices.put("mouse", 29.99);
    prices.put("keyboard", 79.99);

    mapEach
        .<String>eachWithIndex()
        .ifPresent(
            indexed -> {
              // Format prices with product name
              System.out.println("\nPrice list:");
              var pairs = IndexedTraversals.toIndexedList(indexed, prices);
              for (var pair : pairs) {
                System.out.printf("  %s: £%.2f%n", pair.first(), pair.second());
              }
            });

    System.out.println();
  }

  /** Demonstrates integration with Focus DSL using .each(Each). */
  private static void demonstrateFocusDslIntegration() {
    System.out.println("--- Focus DSL Integration ---");

    // Create lenses for User -> Map<String, Order>
    Lens<User, Map<String, Order>> ordersLens =
        Lens.of(User::orders, (u, o) -> new User(u.name(), o));

    // Create lens for Order -> List<Product>
    Lens<Order, List<Product>> productsLens =
        Lens.of(Order::products, (o, p) -> new Order(o.id(), p));

    // Sample data
    User user =
        new User(
            "Alice",
            Map.of(
                "ORD-1",
                new Order("ORD-1", List.of(new Product("Laptop", 999.99))),
                "ORD-2",
                new Order(
                    "ORD-2",
                    List.of(new Product("Mouse", 29.99), new Product("Keyboard", 79.99)))));

    // Navigate using Each instances with Focus DSL
    // User -> Map<String, Order> -> Order (values) -> List<Product> -> Product
    TraversalPath<User, Order> allOrders =
        FocusPath.of(ordersLens).each(EachInstances.mapValuesEach());

    System.out.println("All orders:");
    for (Order order : allOrders.getAll(user)) {
      System.out.println("  " + order.id() + ": " + order.products().size() + " product(s)");
    }

    // Navigate deeper: User -> all Orders -> all Products
    TraversalPath<User, Product> allProducts =
        allOrders.via(productsLens).each(EachInstances.listEach());

    System.out.println("\nAll products across all orders:");
    for (Product product : allProducts.getAll(user)) {
      System.out.printf("  %s: £%.2f%n", product.name(), product.price());
    }

    // Apply 10% discount to all products
    User discounted = allProducts.modifyAll(p -> new Product(p.name(), p.price() * 0.9), user);

    System.out.println("\nAfter 10% discount:");
    for (Product product : allProducts.getAll(discounted)) {
      System.out.printf("  %s: £%.2f%n", product.name(), product.price());
    }

    System.out.println();
  }

  /** Demonstrates creating custom Each instances. */
  private static void demonstrateCustomEach() {
    System.out.println("--- Custom Each Instances ---");

    // Create Each from existing Traversal
    Traversal<List<String>, String> existingTraversal = Traversals.forList();
    Each<List<String>, String> fromTraversal = Each.fromTraversal(existingTraversal);

    List<String> items = List.of("one", "two", "three");
    List<String> result = Traversals.modify(fromTraversal.each(), String::toUpperCase, items);
    System.out.println("From existing traversal: " + result);

    // Create Each from IndexedTraversal (supports both each() and eachWithIndex())
    IndexedTraversal<Integer, List<String>, String> indexedTraversal = IndexedTraversals.forList();
    Each<List<String>, String> fromIndexed = Each.fromIndexedTraversal(indexedTraversal);

    System.out.println("From indexed traversal:");
    System.out.println("  Supports indexed: " + fromIndexed.supportsIndexed());

    // Use the indexed capability
    fromIndexed
        .<Integer>eachWithIndex()
        .ifPresent(
            indexed -> {
              List<String> numbered =
                  IndexedTraversals.imodify(
                      indexed, (i, s) -> "[" + i + "] " + s, List.of("a", "b", "c"));
              System.out.println("  Numbered: " + numbered);
            });

    System.out.println();
    System.out.println("=== Each provides a uniform interface for element-wise traversal ===");
  }
}
