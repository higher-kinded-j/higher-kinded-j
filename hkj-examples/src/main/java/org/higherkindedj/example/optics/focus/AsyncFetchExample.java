// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.focus;

import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.instances.Witnesses.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.CustomerProfile;
import org.higherkindedj.example.book.optics.cast.CustomerProfileLenses;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderLenses;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.future.CompletableFutureMonad;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.optics.each.EachInstances;
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
 *
 * <p>The data is the chapter's own: a {@link CustomerProfile} whose optional fields a CRM fills in,
 * and an {@link Order} whose lines a pricing service prices.
 */
public class AsyncFetchExample {

  // ============= Simulated Async Services =============

  /** The nicknames the simulated CRM holds, by customer name. */
  private static final Map<String, String> CRM_NICKNAMES =
      Map.of("Ada", "ada_l", "Grace", "amazing_grace");

  /** Simulates fetching a customer's nickname from the CRM. */
  static CompletableFuture<Optional<String>> fetchNickname(String name) {
    return CompletableFuture.supplyAsync(
        () -> {
          simulateLatency(50);
          return Optional.ofNullable(CRM_NICKNAMES.get(name));
        });
  }

  /** Simulates fetching a customer's alternative email address from the CRM. */
  static CompletableFuture<Optional<EmailAddress>> fetchAltEmail(String name) {
    return CompletableFuture.supplyAsync(
        () -> {
          simulateLatency(30);
          return Optional.of(new EmailAddress(name.toLowerCase() + "@work.example.com"));
        });
  }

  /** Simulates fetching the current price of a sku from an external pricing service. */
  static CompletableFuture<BigDecimal> fetchPrice(String sku) {
    return CompletableFuture.supplyAsync(
        () -> {
          simulateLatency(40);
          return switch (sku) {
            case "LAMP" -> new BigDecimal("40.00");
            case "BULB" -> new BigDecimal("2.50");
            case "SHADE" -> new BigDecimal("15.99");
            default -> new BigDecimal("19.99");
          };
        });
  }

  /** Simulates a CRM lookup that fails for a customer it has no record of. */
  static CompletableFuture<Optional<String>> fetchNicknameStrictly(String name) {
    return CompletableFuture.supplyAsync(
        () -> {
          simulateLatency(20);
          if (!CRM_NICKNAMES.containsKey(name)) {
            throw new RuntimeException("CRM has no record of customer: " + name);
          }
          return Optional.of(CRM_NICKNAMES.get(name));
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

    MonadError<CompletableFutureKind.Witness, Throwable> futureMonad =
        Instances.monadError(completableFuture());

    // Initial profile, with nothing yet fetched
    CustomerProfile profile = new CustomerProfile("Ada", Optional.empty(), Optional.empty());
    System.out.println("Initial profile: " + profile);

    // Create path to the nickname
    FocusPath<CustomerProfile, Optional<String>> nicknamePath =
        FocusPath.of(CustomerProfileLenses.nickname());

    // Async function to fetch and update the nickname
    Function<Optional<String>, Kind<CompletableFutureKind.Witness, Optional<String>>> fetch =
        currentNickname -> FUTURE.widen(fetchNickname(profile.name()));

    // Use modifyF to asynchronously update the nickname
    Kind<CompletableFutureKind.Witness, CustomerProfile> enrichedKind =
        nicknamePath.modifyF(fetch, profile, futureMonad);

    // Wait for result and print
    CustomerProfile enrichedProfile = FUTURE.join(enrichedKind);
    System.out.println("Enriched profile: " + enrichedProfile);

    System.out.println();
  }

  /** Demonstrates enriching multiple fields asynchronously in sequence. */
  static void multiFieldAsyncEnrichment() {
    System.out.println("--- Multi-Field Async Enrichment ---");

    MonadError<CompletableFutureKind.Witness, Throwable> futureMonad =
        Instances.monadError(completableFuture());

    // Initial profile
    CustomerProfile profile = new CustomerProfile("Grace", Optional.empty(), Optional.empty());
    System.out.println("Initial: " + profile);

    // Create paths
    FocusPath<CustomerProfile, Optional<String>> nicknamePath =
        FocusPath.of(CustomerProfileLenses.nickname());
    FocusPath<CustomerProfile, Optional<EmailAddress>> altEmailPath =
        FocusPath.of(CustomerProfileLenses.altEmail());

    // Capture the name for closures
    String name = profile.name();

    // Chain async enrichments using flatMap
    Kind<CompletableFutureKind.Witness, CustomerProfile> result =
        futureMonad.flatMap(
            p1 -> altEmailPath.modifyF(_ -> FUTURE.widen(fetchAltEmail(name)), p1, futureMonad),
            nicknamePath.modifyF(_ -> FUTURE.widen(fetchNickname(name)), profile, futureMonad));

    // Wait and print
    CustomerProfile enrichedProfile = FUTURE.join(result);
    System.out.println("Fully enriched: " + enrichedProfile);

    System.out.println();
  }

  /** Demonstrates async enrichment of all items in a collection. */
  static void collectionAsyncEnrichment() {
    System.out.println("--- Collection Async Enrichment ---");

    MonadError<CompletableFutureKind.Witness, Throwable> futureMonad =
        Instances.monadError(completableFuture());

    // An order whose lines are not priced yet
    Order order =
        new Order(
            new UUID(0, 1),
            new Customer("Ada", new EmailAddress("ada@example.com")),
            List.of(
                new LineItem("LAMP", 1, BigDecimal.ZERO),
                new LineItem("BULB", 4, BigDecimal.ZERO),
                new LineItem("SHADE", 1, BigDecimal.ZERO)),
            Instant.parse("2026-10-01T09:00:00Z"),
            Currency.getInstance("GBP"),
            OrderStatus.NEW);

    System.out.println("Initial order lines:");
    printLines(order);

    // Path to every line of the order
    TraversalPath<Order, LineItem> allLinesPath =
        FocusPath.of(OrderLenses.lines()).each(EachInstances.listEach());

    // Async function to price each line from the pricing service
    Function<LineItem, Kind<CompletableFutureKind.Witness, LineItem>> priceLine =
        line ->
            FUTURE.widen(
                fetchPrice(line.sku())
                    .thenApply(price -> new LineItem(line.sku(), line.quantity(), price)));

    // Use modifyF on the traversal to update every line
    Kind<CompletableFutureKind.Witness, Order> pricedOrderKind =
        allLinesPath.modifyF(priceLine, order, futureMonad);

    // Wait and print
    Order pricedOrder = FUTURE.join(pricedOrderKind);
    System.out.println("\nPriced order lines:");
    printLines(pricedOrder);

    // Calculate total: each line's price times its quantity
    BigDecimal total =
        pricedOrder.lines().stream()
            .map(line -> line.price().multiply(BigDecimal.valueOf(line.quantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    System.out.printf("Order total: £%.2f%n", total);

    System.out.println();
  }

  /** Demonstrates error handling with async operations. */
  static void errorHandlingExample() {
    System.out.println("--- Error Handling with Async Operations ---");

    MonadError<CompletableFutureKind.Witness, Throwable> futureMonad =
        Instances.monadError(completableFuture());

    // One customer the CRM knows, and one it does not
    CustomerProfile knownProfile = new CustomerProfile("Ada", Optional.empty(), Optional.empty());
    CustomerProfile unknownProfile =
        new CustomerProfile("Linus", Optional.empty(), Optional.empty());

    FocusPath<CustomerProfile, Optional<String>> nicknamePath =
        FocusPath.of(CustomerProfileLenses.nickname());

    // Function that may fail
    Function<Optional<String>, Kind<CompletableFutureKind.Witness, Optional<String>>> strictFetch =
        _ -> FUTURE.widen(fetchNicknameStrictly(knownProfile.name()));

    // Successful case
    Kind<CompletableFutureKind.Witness, CustomerProfile> successResult =
        nicknamePath.modifyF(strictFetch, knownProfile, futureMonad);

    CustomerProfile successProfile = FUTURE.join(successResult);
    System.out.println("Success case: " + successProfile.nickname().orElse("none"));

    // Error case with recovery
    Function<Optional<String>, Kind<CompletableFutureKind.Witness, Optional<String>>>
        strictFetchUnknown = _ -> FUTURE.widen(fetchNicknameStrictly(unknownProfile.name()));

    Kind<CompletableFutureKind.Witness, CustomerProfile> errorResult =
        nicknamePath.modifyF(strictFetchUnknown, unknownProfile, futureMonad);

    // Use handleErrorWith for recovery
    Kind<CompletableFutureKind.Witness, CustomerProfile> recoveredResult =
        futureMonad.handleErrorWith(
            errorResult,
            error -> {
              System.out.println("Caught error: " + error.getMessage());
              // Fall back to the profile as it was, without a nickname
              return futureMonad.of(unknownProfile);
            });

    CustomerProfile recoveredProfile = FUTURE.join(recoveredResult);
    System.out.println("Recovered profile: " + recoveredProfile);

    System.out.println();
  }

  private static void printLines(Order order) {
    for (LineItem line : order.lines()) {
      System.out.printf("  %s x%d at £%.2f%n", line.sku(), line.quantity(), line.price());
    }
  }
}
