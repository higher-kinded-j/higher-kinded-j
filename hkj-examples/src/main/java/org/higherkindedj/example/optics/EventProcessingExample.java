// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;

/**
 * A runnable example demonstrating event-driven architecture and event routing using prisms.
 *
 * <p>This example showcases:
 *
 * <ul>
 *   <li>Type-safe event variant handling with sealed interfaces
 *   <li>Event routing and dispatch using {@code matches()} and {@code getOptional()}
 *   <li>Event filtering and transformation with {@code modifyWhen()}
 *   <li>Event enrichment pipelines using {@code modify()}
 *   <li>Event handler composition with {@code orElse()}
 * </ul>
 */
public class EventProcessingExample {

  // Event type hierarchy
  @GeneratePrisms
  public sealed interface PlatformEvent permits AccountEvent, PurchaseEvent, SystemEvent {}

  @GeneratePrisms
  public sealed interface AccountEvent extends PlatformEvent
      permits UserRegistered, UserLoggedIn, UserUpdated {}

  @GenerateLenses
  public record UserRegistered(String userId, String email, Instant timestamp)
      implements AccountEvent {}

  @GenerateLenses
  public record UserLoggedIn(String userId, String ipAddress, Instant timestamp)
      implements AccountEvent {}

  public record UserUpdated(String userId, Map<String, String> changes, Instant timestamp)
      implements AccountEvent {}

  @GeneratePrisms
  public sealed interface PurchaseEvent extends PlatformEvent
      permits OrderSubmitted, OrderShipped, OrderCancelled {}

  public record OrderSubmitted(String orderId, String userId, double amount, Instant timestamp)
      implements PurchaseEvent {}

  public record OrderShipped(String orderId, String trackingNumber, Instant timestamp)
      implements PurchaseEvent {}

  public record OrderCancelled(String orderId, String reason, Instant timestamp)
      implements PurchaseEvent {}

  @GeneratePrisms
  public sealed interface SystemEvent extends PlatformEvent
      permits ServiceStarted, ServiceStopped, HealthCheck {}

  public record ServiceStarted(String serviceName, String version, Instant timestamp)
      implements SystemEvent {}

  public record ServiceStopped(String serviceName, Instant timestamp) implements SystemEvent {}

  public record HealthCheck(String serviceName, String status, Instant timestamp)
      implements SystemEvent {}

  // Prisms for event type access
  private static final Prism<PlatformEvent, AccountEvent> ACCOUNT_EVENT =
      PlatformEventPrisms.accountEvent();
  private static final Prism<PlatformEvent, PurchaseEvent> PURCHASE_EVENT =
      PlatformEventPrisms.purchaseEvent();
  private static final Prism<PlatformEvent, SystemEvent> SYSTEM_EVENT =
      PlatformEventPrisms.systemEvent();

  private static final Prism<AccountEvent, UserRegistered> USER_REGISTERED =
      AccountEventPrisms.userRegistered();
  private static final Prism<AccountEvent, UserLoggedIn> USER_LOGGED_IN =
      AccountEventPrisms.userLoggedIn();

  private static final Prism<PurchaseEvent, OrderSubmitted> ORDER_SUBMITTED =
      PurchaseEventPrisms.orderSubmitted();
  private static final Prism<PurchaseEvent, OrderShipped> ORDER_SHIPPED =
      PurchaseEventPrisms.orderShipped();
  private static final Prism<PurchaseEvent, OrderCancelled> ORDER_CANCELLED =
      PurchaseEventPrisms.orderCancelled();

  public static void main(String[] args) {
    System.out.println("=== Event Processing with Prisms ===\n");

    demonstrateEventRouting();
    demonstrateEventFiltering();
    demonstrateEventEnrichment();
    demonstrateCompositeHandlers();
    demonstrateEventAggregation();
    demonstrateForComprehensionWithMatch();
  }

  private static void demonstrateEventRouting() {
    System.out.println("--- Event Routing ---");

    List<PlatformEvent> events =
        List.of(
            new UserRegistered("U001", "alice@example.com", Instant.now()),
            new OrderSubmitted("O001", "U001", 99.99, Instant.now()),
            new ServiceStarted("payment-service", "1.0.0", Instant.now()),
            new UserLoggedIn("U002", "192.168.1.1", Instant.now()));

    // Route events to appropriate handlers
    for (PlatformEvent event : events) {
      if (ACCOUNT_EVENT.matches(event)) {
        System.out.println("  Routing to user event handler: " + event.getClass().getSimpleName());
      } else if (PURCHASE_EVENT.matches(event)) {
        System.out.println("  Routing to order event handler: " + event.getClass().getSimpleName());
      } else if (SYSTEM_EVENT.matches(event)) {
        System.out.println(
            "  Routing to system event handler: " + event.getClass().getSimpleName());
      }
    }

    System.out.println();
  }

  private static void demonstrateEventFiltering() {
    System.out.println("--- Event Filtering ---");

    List<PlatformEvent> events =
        List.of(
            new UserRegistered("U001", "alice@example.com", Instant.now()),
            new OrderSubmitted("O001", "U001", 199.99, Instant.now()),
            new OrderSubmitted("O002", "U002", 49.99, Instant.now()),
            new OrderShipped("O001", "TRACK123", Instant.now()),
            new UserLoggedIn("U001", "192.168.1.1", Instant.now()));

    // Extract high-value orders (> £100)
    List<OrderSubmitted> highValue =
        events.stream()
            .flatMap(
                event ->
                    PURCHASE_EVENT
                        .andThen(ORDER_SUBMITTED)
                        .getOptional(event)
                        .filter(order -> order.amount() > 100.0)
                        .stream())
            .toList();

    System.out.println("High-value orders (> £100):");
    highValue.forEach(
        order ->
            System.out.println(
                "  Order " + order.orderId() + ": £" + String.format("%.2f", order.amount())));

    // Extract login events for a specific user
    String targetUserId = "U001";
    List<UserLoggedIn> userLogins =
        events.stream()
            .flatMap(
                event ->
                    ACCOUNT_EVENT
                        .andThen(USER_LOGGED_IN)
                        .getOptional(event)
                        .filter(login -> login.userId().equals(targetUserId))
                        .stream())
            .toList();

    System.out.println("Login events for user " + targetUserId + ": " + userLogins.size());

    System.out.println();
  }

  private static void demonstrateEventEnrichment() {
    System.out.println("--- Event Enrichment ---");

    PlatformEvent orderEvent =
        new OrderSubmitted("O001", "U123", 99.99, Instant.now().minusSeconds(60));

    // Enrich event with current timestamp if it's too old
    Instant cutoff = Instant.now().minusSeconds(30);

    PlatformEvent enriched =
        PURCHASE_EVENT.modifyWhen(
            event ->
                ORDER_SUBMITTED
                    .getOptional(event)
                    .map(order -> order.timestamp().isBefore(cutoff))
                    .orElse(false),
            event ->
                Optional.ofNullable(
                        ORDER_SUBMITTED.modify(
                            order ->
                                new OrderSubmitted(
                                    order.orderId(), order.userId(), order.amount(), Instant.now()),
                            event))
                    .orElse(event),
            orderEvent);

    System.out.println("Original event: " + orderEvent);
    System.out.println("Enriched event: " + enriched);

    // Sanitise sensitive data in user events
    PlatformEvent userEvent = new UserRegistered("U001", "alice@example.com", Instant.now());

    PlatformEvent sanitised =
        ACCOUNT_EVENT.modify(
            event ->
                Optional.ofNullable(
                        USER_REGISTERED.modify(
                            registered ->
                                new UserRegistered(
                                    registered.userId(),
                                    maskEmail(registered.email()),
                                    registered.timestamp()),
                            event))
                    .orElse(event),
            userEvent);

    System.out.println("Original: " + userEvent);
    System.out.println("Sanitised: " + sanitised);

    System.out.println();
  }

  private static void demonstrateCompositeHandlers() {
    System.out.println("--- Composite Event Handlers ---");

    List<PlatformEvent> events =
        List.of(
            new UserRegistered("U001", "alice@example.com", Instant.now()),
            new OrderSubmitted("O001", "U001", 99.99, Instant.now()),
            new UserLoggedIn("U002", "192.168.1.1", Instant.now()),
            new UserRegistered("U003", "charlie@example.com", Instant.now()));

    System.out.println("User activity events (registrations + logins):");
    events.stream()
        .flatMap(
            event ->
                ACCOUNT_EVENT
                    .andThen(USER_REGISTERED)
                    .mapOptional(UserRegistered::userId, event)
                    .or(
                        () ->
                            ACCOUNT_EVENT
                                .andThen(USER_LOGGED_IN)
                                .mapOptional(UserLoggedIn::userId, event))
                    .stream())
        .forEach(userId -> System.out.println("  User activity for: " + userId));

    System.out.println();
  }

  private static void demonstrateEventAggregation() {
    System.out.println("--- Event Aggregation Statistics ---");

    List<PlatformEvent> eventStream =
        List.of(
            new UserRegistered("U001", "alice@example.com", Instant.now()),
            new UserRegistered("U002", "bob@example.com", Instant.now()),
            new OrderSubmitted("O001", "U001", 199.99, Instant.now()),
            new OrderSubmitted("O002", "U002", 49.99, Instant.now()),
            new OrderSubmitted("O003", "U001", 299.99, Instant.now()),
            new OrderShipped("O001", "TRACK123", Instant.now()),
            new OrderCancelled("O002", "Out of stock", Instant.now()),
            new UserLoggedIn("U001", "192.168.1.1", Instant.now()),
            new ServiceStarted("order-service", "2.0.0", Instant.now()));

    // Count events by type
    long userEvents = eventStream.stream().filter(ACCOUNT_EVENT::matches).count();
    long orderEvents = eventStream.stream().filter(PURCHASE_EVENT::matches).count();
    long systemEvents = eventStream.stream().filter(SYSTEM_EVENT::matches).count();

    System.out.println("Event counts by category:");
    System.out.println("  User events:   " + userEvents);
    System.out.println("  Order events:  " + orderEvents);
    System.out.println("  System events: " + systemEvents);

    // Calculate total order value
    double totalOrderValue =
        eventStream.stream()
            .flatMap(event -> PURCHASE_EVENT.andThen(ORDER_SUBMITTED).getOptional(event).stream())
            .mapToDouble(OrderSubmitted::amount)
            .sum();

    System.out.println("Total order value: £" + String.format("%.2f", totalOrderValue));

    // Count unique users from registration and login events
    long uniqueUsers =
        eventStream.stream()
            .flatMap(
                event ->
                    ACCOUNT_EVENT
                        .andThen(USER_REGISTERED)
                        .mapOptional(UserRegistered::userId, event)
                        .or(
                            () ->
                                ACCOUNT_EVENT
                                    .andThen(USER_LOGGED_IN)
                                    .mapOptional(UserLoggedIn::userId, event))
                        .stream())
            .distinct()
            .count();

    System.out.println("Unique active users: " + uniqueUsers);

    System.out.println();
  }

  /**
   * Demonstrates For comprehension with match() for declarative event filtering.
   *
   * <p>The match() operation provides prism-based pattern matching within For comprehensions. When
   * the prism match fails, the computation short-circuits using the monad's zero value (empty list
   * for List, Nothing for Maybe).
   */
  private static void demonstrateForComprehensionWithMatch() {
    System.out.println("--- For Comprehension with match() ---");

    MonadZero<ListKind.Witness> listMonad = Instances.monadZero(list());

    List<PlatformEvent> events =
        List.of(
            new UserRegistered("U001", "alice@example.com", Instant.now()),
            new OrderSubmitted("O001", "U001", 199.99, Instant.now()),
            new OrderSubmitted("O002", "U002", 49.99, Instant.now()),
            new UserLoggedIn("U001", "192.168.1.1", Instant.now()),
            new OrderShipped("O001", "TRACK123", Instant.now()),
            new ServiceStarted("payment-service", "1.0.0", Instant.now()),
            new OrderSubmitted("O003", "U003", 350.00, Instant.now()));

    // Example 1: Extract high-value orders using For + match()
    // Compare with the traditional flatMap approach in demonstrateEventFiltering()
    Prism<PlatformEvent, OrderSubmitted> orderSubmittedPrism =
        PURCHASE_EVENT.andThen(ORDER_SUBMITTED);

    Kind<ListKind.Witness, OrderSubmitted> highValueOrders =
        For.from(listMonad, LIST.widen(events))
            .match(orderSubmittedPrism)
            .when(t -> t._2().amount() > 100.0)
            .yield((event, order) -> order);

    List<OrderSubmitted> highValueList = LIST.narrow(highValueOrders);

    System.out.println("High-value orders (> £100) using For + match():");
    for (OrderSubmitted order : highValueList) {
      System.out.printf("  Order %s: £%.2f%n", order.orderId(), order.amount());
    }

    // Example 2: Extract user registration emails
    Prism<PlatformEvent, UserRegistered> userRegisteredPrism =
        ACCOUNT_EVENT.andThen(USER_REGISTERED);

    Kind<ListKind.Witness, String> registrationEmails =
        For.from(listMonad, LIST.widen(events))
            .match(userRegisteredPrism)
            .yield((event, registration) -> registration.email());

    List<String> emailList = LIST.narrow(registrationEmails);

    System.out.println("\nRegistration emails using For + match():");
    for (String email : emailList) {
      System.out.println("  " + email);
    }

    // Example 3: Combine match() with focus() for complex extraction
    // Extract order IDs along with amounts for orders from specific users
    Kind<ListKind.Witness, String> orderSummaries =
        For.from(listMonad, LIST.widen(events))
            .match(orderSubmittedPrism)
            .focus(t -> t._2().userId())
            .when(t -> t._3().startsWith("U00"))
            .yield(
                (event, order, userId) ->
                    String.format(
                        "Order %s by %s: £%.2f", order.orderId(), userId, order.amount()));

    List<String> summaryList = LIST.narrow(orderSummaries);

    System.out.println("\nOrder summaries (users U00*) using For + match() + focus():");
    for (String summary : summaryList) {
      System.out.println("  " + summary);
    }

    // Example 4: Count events by type using match() short-circuit behaviour
    // Only AccountEvents pass through this comprehension
    Kind<ListKind.Witness, String> userEventTypes =
        For.from(listMonad, LIST.widen(events))
            .match(ACCOUNT_EVENT)
            .yield((event, userEvent) -> userEvent.getClass().getSimpleName());

    List<String> userEventTypeList = LIST.narrow(userEventTypes);

    System.out.println("\nUser event types (others filtered out):");
    System.out.println("  Types: " + userEventTypeList);
    System.out.println("  Count: " + userEventTypeList.size() + " user events");

    System.out.println("\n=== For + match() provides declarative, type-safe event filtering ===\n");
  }

  // Helper methods

  private static String maskEmail(String email) {
    int atIndex = email.indexOf('@');
    if (atIndex <= 1) {
      return "***" + email.substring(atIndex);
    }
    return email.charAt(0) + "***" + email.substring(atIndex);
  }

  private static Prism<AccountEvent, AccountEvent> filter(Predicate<AccountEvent> predicate) {
    return Prism.of(
        event -> predicate.test(event) ? Optional.of(event) : Optional.empty(), event -> event);
  }
}
