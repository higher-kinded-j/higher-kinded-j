// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.focus;

import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.future.CompletableFutureMonad;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;

/**
 * Demonstrates async data fetching using the Focus DSL with CompletableFuture.
 *
 * <p>This example shows how to use {@code modifyF()} with {@link CompletableFutureMonad} for
 * asynchronous operations, including:
 *
 * <ul>
 *   <li>Async data enrichment through Focus paths
 *   <li>Error handling with MonadError
 *   <li>Parallel async transformations on collections
 *   <li>Combining async operations with optic composition
 * </ul>
 *
 * <h2>Key Concepts</h2>
 *
 * <p>The Focus DSL's {@code modifyF()} method accepts any Applicative instance, including {@link
 * CompletableFutureMonad}. This enables async transformations that:
 *
 * <ul>
 *   <li>Run asynchronously without blocking
 *   <li>Propagate errors through the Future chain
 *   <li>Compose naturally with other Focus operations
 * </ul>
 */
public class AsyncFetchExample {

  // ============= Domain Model =============

  /** User profile with enrichable fields. */
  record UserProfile(String userId, String displayName, String avatarUrl, ProfileStats stats) {}

  /** Profile statistics that can be fetched asynchronously. */
  record ProfileStats(int followers, int following, int posts) {
    static ProfileStats empty() {
      return new ProfileStats(0, 0, 0);
    }
  }

  /** Product with pricing that can be updated from external service. */
  record Product(String productId, String name, PricingInfo pricing) {}

  /** Pricing information fetched from external service. */
  record PricingInfo(double basePrice, double discountPercent, String currency) {
    double effectivePrice() {
      return basePrice * (1 - discountPercent / 100);
    }

    static PricingInfo unknown() {
      return new PricingInfo(0.0, 0.0, "USD");
    }
  }

  /** Shopping cart with products to be enriched. */
  record ShoppingCart(String cartId, List<Product> products) {}

  // ============= Lenses =============

  static final Lens<UserProfile, String> displayNameLens =
      Lens.of(
          UserProfile::displayName,
          (u, name) -> new UserProfile(u.userId(), name, u.avatarUrl(), u.stats()));

  static final Lens<UserProfile, String> avatarUrlLens =
      Lens.of(
          UserProfile::avatarUrl,
          (u, url) -> new UserProfile(u.userId(), u.displayName(), url, u.stats()));

  static final Lens<UserProfile, ProfileStats> statsLens =
      Lens.of(
          UserProfile::stats,
          (u, stats) -> new UserProfile(u.userId(), u.displayName(), u.avatarUrl(), stats));

  static final Lens<Product, PricingInfo> pricingLens =
      Lens.of(Product::pricing, (p, pricing) -> new Product(p.productId(), p.name(), pricing));

  static final Lens<ShoppingCart, List<Product>> productsLens =
      Lens.of(ShoppingCart::products, (c, products) -> new ShoppingCart(c.cartId(), products));

  // ============= Simulated Async Services =============

  /** Simulates fetching display name from a remote service. */
  static CompletableFuture<String> fetchDisplayName(String userId) {
    return CompletableFuture.supplyAsync(
        () -> {
          simulateLatency(50);
          return "User " + userId.toUpperCase();
        });
  }

  /** Simulates fetching avatar URL from a CDN. */
  static CompletableFuture<String> fetchAvatarUrl(String userId) {
    return CompletableFuture.supplyAsync(
        () -> {
          simulateLatency(30);
          return "https://cdn.example.com/avatars/" + userId + ".png";
        });
  }

  /** Simulates fetching profile stats from analytics service. */
  static CompletableFuture<ProfileStats> fetchStats(String userId) {
    return CompletableFuture.supplyAsync(
        () -> {
          simulateLatency(100);
          // Simulate fetched stats
          int hash = userId.hashCode();
          return new ProfileStats(
              Math.abs(hash % 10000), Math.abs((hash >> 8) % 1000), Math.abs((hash >> 16) % 500));
        });
  }

  /** Simulates fetching pricing from external pricing service. */
  static CompletableFuture<PricingInfo> fetchPricing(String productId) {
    return CompletableFuture.supplyAsync(
        () -> {
          simulateLatency(40);
          // Simulate different pricing based on product
          double basePrice =
              switch (productId) {
                case "PROD-A" -> 29.99;
                case "PROD-B" -> 149.99;
                case "PROD-C" -> 9.99;
                default -> 19.99;
              };
          double discount = productId.hashCode() % 20;
          return new PricingInfo(basePrice, discount, "USD");
        });
  }

  /** Simulates a service that may fail. */
  static CompletableFuture<String> fetchUnreliableData(String id) {
    return CompletableFuture.supplyAsync(
        () -> {
          simulateLatency(20);
          if (id.startsWith("BAD")) {
            throw new RuntimeException("Service unavailable for ID: " + id);
          }
          return "Data for " + id;
        });
  }

  private static void simulateLatency(long millis) {
    try {
      TimeUnit.MILLISECONDS.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  // ============= Examples =============

  public static void main(String[] args) {
    System.out.println("=== Async Fetch Example with Focus DSL ===\n");

    basicAsyncEnrichment();
    multiFieldAsyncEnrichment();
    collectionAsyncEnrichment();
    errorHandlingExample();
  }

  /** Demonstrates basic async enrichment of a single field. */
  static void basicAsyncEnrichment() {
    System.out.println("--- Basic Async Enrichment ---");

    CompletableFutureMonad futureMonad = CompletableFutureMonad.INSTANCE;

    // Initial profile with placeholder values
    UserProfile profile = new UserProfile("user123", "", "", ProfileStats.empty());
    System.out.println("Initial profile: " + profile);

    // Create path to display name
    FocusPath<UserProfile, String> displayNamePath = FocusPath.of(displayNameLens);

    // Async function to fetch and update display name
    Function<String, Kind<CompletableFutureKind.Witness, String>> fetchName =
        currentName -> FUTURE.widen(fetchDisplayName(profile.userId()));

    // Use modifyF to asynchronously update the display name
    Kind<CompletableFutureKind.Witness, UserProfile> enrichedKind =
        displayNamePath.modifyF(fetchName, profile, futureMonad);

    // Wait for result and print
    UserProfile enrichedProfile = FUTURE.join(enrichedKind);
    System.out.println("Enriched profile: " + enrichedProfile);

    System.out.println();
  }

  /** Demonstrates enriching multiple fields asynchronously in sequence. */
  static void multiFieldAsyncEnrichment() {
    System.out.println("--- Multi-Field Async Enrichment ---");

    CompletableFutureMonad futureMonad = CompletableFutureMonad.INSTANCE;

    // Initial profile
    UserProfile profile = new UserProfile("alice42", "", "", ProfileStats.empty());
    System.out.println("Initial: " + profile);

    // Create paths
    FocusPath<UserProfile, String> namePath = FocusPath.of(displayNameLens);
    FocusPath<UserProfile, String> avatarPath = FocusPath.of(avatarUrlLens);
    FocusPath<UserProfile, ProfileStats> statsPath = FocusPath.of(statsLens);

    // Capture userId for closures
    String userId = profile.userId();

    // Chain async enrichments using flatMap
    Kind<CompletableFutureKind.Witness, UserProfile> result =
        futureMonad.flatMap(
            p1 -> avatarPath.modifyF(_ -> FUTURE.widen(fetchAvatarUrl(userId)), p1, futureMonad),
            namePath.modifyF(_ -> FUTURE.widen(fetchDisplayName(userId)), profile, futureMonad));

    // Continue the chain for stats
    Kind<CompletableFutureKind.Witness, UserProfile> finalResult =
        futureMonad.flatMap(
            p2 -> statsPath.modifyF(_ -> FUTURE.widen(fetchStats(userId)), p2, futureMonad),
            result);

    // Wait and print
    UserProfile enrichedProfile = FUTURE.join(finalResult);
    System.out.println("Fully enriched: " + enrichedProfile);

    System.out.println();
  }

  /** Demonstrates async enrichment of all items in a collection. */
  static void collectionAsyncEnrichment() {
    System.out.println("--- Collection Async Enrichment ---");

    CompletableFutureMonad futureMonad = CompletableFutureMonad.INSTANCE;

    // Shopping cart with products needing price updates
    ShoppingCart cart =
        new ShoppingCart(
            "CART-001",
            List.of(
                new Product("PROD-A", "Widget", PricingInfo.unknown()),
                new Product("PROD-B", "Gadget", PricingInfo.unknown()),
                new Product("PROD-C", "Gizmo", PricingInfo.unknown())));

    System.out.println("Initial cart products:");
    printCartProducts(cart);

    // Path to all products - we update products directly to get pricing
    TraversalPath<ShoppingCart, Product> allProductsPath =
        FocusPath.of(productsLens).<Product>each();

    // Async function to enrich each product with its pricing
    Function<Product, Kind<CompletableFutureKind.Witness, Product>> enrichProduct =
        product ->
            FUTURE.widen(
                fetchPricing(product.productId())
                    .thenApply(
                        pricing -> new Product(product.productId(), product.name(), pricing)));

    // Use modifyF on the traversal to update all products
    Kind<CompletableFutureKind.Witness, ShoppingCart> enrichedCartKind =
        allProductsPath.modifyF(enrichProduct, cart, futureMonad);

    // Wait and print
    ShoppingCart enrichedCart = FUTURE.join(enrichedCartKind);
    System.out.println("\nEnriched cart products:");
    printCartProducts(enrichedCart);

    // Calculate total
    double total =
        enrichedCart.products().stream().mapToDouble(p -> p.pricing().effectivePrice()).sum();
    System.out.printf("Cart total: $%.2f%n", total);

    System.out.println();
  }

  /** Demonstrates error handling with async operations. */
  static void errorHandlingExample() {
    System.out.println("--- Error Handling with Async Operations ---");

    CompletableFutureMonad futureMonad = CompletableFutureMonad.INSTANCE;

    // Test with good and bad IDs
    UserProfile goodProfile = new UserProfile("user999", "", "", ProfileStats.empty());
    UserProfile badProfile = new UserProfile("BAD_USER", "", "", ProfileStats.empty());

    FocusPath<UserProfile, String> namePath = FocusPath.of(displayNameLens);

    // Function that may fail
    Function<String, Kind<CompletableFutureKind.Witness, String>> unreliableFetch =
        _ -> FUTURE.widen(fetchUnreliableData(goodProfile.userId()));

    // Successful case
    Kind<CompletableFutureKind.Witness, UserProfile> successResult =
        namePath.modifyF(unreliableFetch, goodProfile, futureMonad);

    UserProfile successProfile = FUTURE.join(successResult);
    System.out.println("Success case: " + successProfile.displayName());

    // Error case with recovery
    Function<String, Kind<CompletableFutureKind.Witness, String>> unreliableFetchBad =
        _ -> FUTURE.widen(fetchUnreliableData(badProfile.userId()));

    Kind<CompletableFutureKind.Witness, UserProfile> errorResult =
        namePath.modifyF(unreliableFetchBad, badProfile, futureMonad);

    // Use handleErrorWith for recovery
    Kind<CompletableFutureKind.Witness, UserProfile> recoveredResult =
        futureMonad.handleErrorWith(
            errorResult,
            error -> {
              System.out.println("Caught error: " + error.getMessage());
              // Return a fallback profile
              UserProfile fallback =
                  new UserProfile(
                      badProfile.userId(),
                      "[Unavailable]",
                      badProfile.avatarUrl(),
                      badProfile.stats());
              return futureMonad.of(fallback);
            });

    UserProfile recoveredProfile = FUTURE.join(recoveredResult);
    System.out.println("Recovered profile: " + recoveredProfile.displayName());

    System.out.println();
  }

  private static void printCartProducts(ShoppingCart cart) {
    for (Product product : cart.products()) {
      PricingInfo p = product.pricing();
      System.out.printf(
          "  %s (%s): base=$%.2f, discount=%.0f%%, effective=$%.2f %s%n",
          product.productId(),
          product.name(),
          p.basePrice(),
          p.discountPercent(),
          p.effectivePrice(),
          p.currency());
    }
  }
}
