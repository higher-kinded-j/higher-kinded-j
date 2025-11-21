// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 07: Real World Optics - Practical Applications
 *
 * <p>In this final tutorial, we'll apply optics to solve real-world problems: 1. Updating user
 * profiles in a database 2. Processing API responses with nested structures 3. Batch operations on
 * complex data
 */
public class Tutorial07_RealWorldOptics {

  /**
   * Exercise 1: User profile updates
   *
   * <p>Manage user profile updates with nested settings and preferences.
   *
   * <p>Task: Build a system to update user preferences immutably
   */
  @Test
  void exercise1_userProfileUpdates() {
    @GenerateLenses
    record NotificationPrefs(boolean email, boolean sms, boolean push) {}

    @GenerateLenses
    record PrivacySettings(boolean publicProfile, boolean showEmail) {}

    @GenerateLenses
    record UserSettings(NotificationPrefs notifications, PrivacySettings privacy) {}

    @GenerateLenses
    record UserProfile(String id, String name, String email, UserSettings settings) {}

    UserProfile user =
        new UserProfile(
            "user123",
            "Alice",
            "alice@example.com",
            new UserSettings(
                new NotificationPrefs(true, false, true), new PrivacySettings(false, false)));

    // TODO: Replace null with a lens that accesses the email notification preference
    // Hint: Compose lenses through settings -> notifications -> email
    Lens<UserProfile, Boolean> emailNotifLens = null;

    // Enable email notifications
    UserProfile updated = emailNotifLens.set(true, user);
    assertThat(updated.settings().notifications().email()).isTrue();

    // TODO: Replace null with code that toggles the publicProfile setting
    // Hint: Use the privacy lens and modify with boolean negation
    Lens<UserProfile, Boolean> publicProfileLens =
        UserProfileLenses.settings()
            .andThen(UserSettingsLenses.privacy())
            .andThen(PrivacySettingsLenses.publicProfile());

    UserProfile toggled = publicProfileLens.modify(p -> !p, updated);
    assertThat(toggled.settings().privacy().publicProfile()).isTrue();
  }

  /**
   * Exercise 2: API response processing
   *
   * <p>Process nested API responses and extract/transform data.
   *
   * <p>Task: Navigate a complex API response structure
   */
  @Test
  void exercise2_apiResponseProcessing() {
    @GenerateLenses
    record Coordinates(double lat, double lon) {}

    @GenerateLenses
    record Location(String city, String country, Coordinates coords) {}

    @GeneratePrisms
    sealed interface ApiResponse {}

    @GenerateLenses
    @GenerateTraversals
    record SuccessResponse(List<Location> locations, int count) implements ApiResponse {}

    @GenerateLenses
    record ErrorResponse(String message, int code) implements ApiResponse {}

    ApiResponse response =
        new SuccessResponse(
            List.of(
                new Location("New York", "USA", new Coordinates(40.7128, -74.0060)),
                new Location("London", "UK", new Coordinates(51.5074, -0.1278))),
            2);

    Prism<ApiResponse, SuccessResponse> successPrism = ApiResponsePrisms.successResponse();
    Traversal<SuccessResponse, Location> locationsTraversal = SuccessResponseTraversals.locations();

    // TODO: Replace null with a composed optic that extracts all city names
    // Hint: Compose success prism -> locations traversal -> city lens
    Traversal<ApiResponse, String> citiesTraversal = null;

    List<String> cities = Traversals.getAll(citiesTraversal, response);
    assertThat(cities).containsExactly("New York", "London");

    // TODO: Replace null with code that adjusts all latitudes by +1.0
    Traversal<ApiResponse, Double> latitudesTraversal =
        successPrism
            .asTraversal()
            .andThen(locationsTraversal)
            .andThen(null)
            .andThen(CoordinatesLenses.lat().asTraversal());

    ApiResponse adjusted = Traversals.modify(latitudesTraversal, lat -> lat + 1.0, response);
    var successData = (SuccessResponse) adjusted;
    assertThat(successData.locations().get(0).coords().lat()).isCloseTo(41.7128, within(0.001));
  }

  /**
   * Exercise 3: E-commerce order processing
   *
   * <p>Handle complex order structures with line items, pricing, and status.
   *
   * <p>Task: Build order processing logic with optics
   */
  @Test
  void exercise3_ecommerceOrderProcessing() {
    @GenerateLenses
    record LineItem(String productId, String name, int quantity, double price) {}

    @GeneratePrisms
    sealed interface OrderStatus {}

    record Pending() implements OrderStatus {}

    record Processing() implements OrderStatus {}

    @GenerateLenses
    record Shipped(String trackingNumber) implements OrderStatus {}

    @GenerateLenses
    @GenerateTraversals
    record Order(String orderId, List<LineItem> items, OrderStatus status, double discount) {}

    Order order =
        new Order(
            "ORD-001",
            List.of(
                new LineItem("PROD-1", "Widget", 2, 10.0),
                new LineItem("PROD-2", "Gadget", 1, 25.0)),
            new Pending(),
            0.0);

    // TODO: Replace null with a traversal that accesses all item prices
    Traversal<Order, Double> pricesTraversal = null;

    // Calculate total before discount
    List<Double> prices = Traversals.getAll(pricesTraversal, order);
    double total = prices.stream().mapToDouble(p -> p).sum();
    assertThat(total).isEqualTo(45.0); // 2*10 + 1*25

    // TODO: Replace null with code that applies a 10% discount to all items
    Order discounted = null;

    List<Double> newPrices = Traversals.getAll(pricesTraversal, discounted);
    double newTotal = newPrices.stream().mapToDouble(p -> p).sum();
    assertThat(newTotal).isCloseTo(40.5, within(0.01)); // 45 * 0.9

    // Update order status to shipped
    Lens<Order, OrderStatus> statusLens = OrderLenses.status();
    Order shipped = statusLens.set(new Shipped("TRACK-123"), discounted);
    assertThat(shipped.status()).isInstanceOf(Shipped.class);
  }

  /**
   * Exercise 4: Data validation and transformation
   *
   * <p>Validate and transform data structures while preserving immutability.
   *
   * <p>Task: Clean and normalize customer data
   */
  @Test
  void exercise4_dataValidationAndTransformation() {
    @GenerateLenses
    record Address(String street, String city, String state, String zip) {}

    @GenerateLenses
    @GenerateTraversals
    record Customer(String id, String name, String email, List<Address> addresses) {}

    @GenerateTraversals
    record CustomerDatabase(List<Customer> customers) {}

    CustomerDatabase db =
        new CustomerDatabase(
            List.of(
                new Customer(
                    "C001",
                    "  Alice Smith  ",
                    "ALICE@EXAMPLE.COM",
                    List.of(new Address("123 main st", "springfield", "il", "62701"))),
                new Customer(
                    "C002",
                    "bob jones",
                    "bob@example.com",
                    List.of(new Address("456 oak ave", "shelbyville", "il", "62702")))));

    // TODO: Replace null with a traversal that accesses all customer names
    Traversal<CustomerDatabase, String> namesTraversal = null;

    // Trim and normalize names
    CustomerDatabase normalized =
        Traversals.modify(namesTraversal, name -> name.trim(), db);

    List<String> names = Traversals.getAll(namesTraversal, normalized);
    assertThat(names.get(0)).isEqualTo("Alice Smith"); // Trimmed

    // TODO: Replace null with a traversal that accesses all city names in all addresses
    Traversal<CustomerDatabase, String> citiesTraversal =
        CustomerDatabaseTraversals.customers()
            .andThen(null)
            .andThen(AddressLenses.city().asTraversal());

    // Capitalize all cities
    CustomerDatabase capitalized =
        Traversals.modify(
            citiesTraversal,
            city -> city.substring(0, 1).toUpperCase() + city.substring(1),
            normalized);

    List<String> cities = Traversals.getAll(citiesTraversal, capitalized);
    assertThat(cities).contains("Springfield", "Shelbyville");
  }

  /**
   * Exercise 5: Configuration management
   *
   * <p>Manage application configuration with environment-specific overrides.
   *
   * <p>Task: Build a configuration system with optics
   */
  @Test
  void exercise5_configurationManagement() {
    @GenerateLenses
    record DatabaseConfig(String host, int port, String username, boolean ssl) {}

    @GenerateLenses
    record CacheConfig(String host, int port, int ttl) {}

    @GenerateLenses
    record AppConfig(String environment, DatabaseConfig database, CacheConfig cache) {}

    AppConfig devConfig =
        new AppConfig(
            "development",
            new DatabaseConfig("localhost", 5432, "dev_user", false),
            new CacheConfig("localhost", 6379, 3600));

    // TODO: Replace null with a lens to access the database SSL setting
    Lens<AppConfig, Boolean> dbSslLens = null;

    // Enable SSL for production
    AppConfig prodConfig =
        AppConfigLenses.environment()
            .set("production", devConfig);
    prodConfig = dbSslLens.set(true, prodConfig);

    assertThat(prodConfig.environment()).isEqualTo("production");
    assertThat(prodConfig.database().ssl()).isTrue();

    // TODO: Replace null with code that updates both database and cache hosts to
    // "prod.example.com"
    Lens<AppConfig, String> dbHostLens =
        AppConfigLenses.database().andThen(DatabaseConfigLenses.host());
    Lens<AppConfig, String> cacheHostLens =
        AppConfigLenses.cache().andThen(CacheConfigLenses.host());

    AppConfig updated = dbHostLens.set("prod.example.com", prodConfig);
    updated = null;

    assertThat(updated.database().host()).isEqualTo("prod.example.com");
    assertThat(updated.cache().host()).isEqualTo("prod.example.com");
  }

  /**
   * Exercise 6: Event processing pipeline
   *
   * <p>Process events with different types and extract metrics.
   *
   * <p>Task: Build an event processing system
   */
  @Test
  void exercise6_eventProcessingPipeline() {
    @GeneratePrisms
    sealed interface Event {}

    @GenerateLenses
    record UserLogin(String userId, long timestamp) implements Event {}

    @GenerateLenses
    record PageView(String userId, String page, long timestamp) implements Event {}

    @GenerateLenses
    record Purchase(String userId, double amount, long timestamp) implements Event {}

    @GenerateTraversals
    record EventStream(List<Event> events) {}

    EventStream stream =
        new EventStream(
            List.of(
                new UserLogin("user1", 1000L),
                new PageView("user1", "/home", 1001L),
                new Purchase("user1", 99.99, 1002L),
                new Purchase("user2", 149.99, 1003L),
                new PageView("user2", "/products", 1004L)));

    Prism<Event, Purchase> purchasePrism = EventPrisms.purchase();

    // TODO: Replace null with a traversal that gets all purchase amounts
    Traversal<EventStream, Double> purchaseAmounts =
        EventStreamTraversals.events()
            .andThen(null.asTraversal())
            .andThen(PurchaseLenses.amount().asTraversal());

    List<Double> amounts = Traversals.getAll(purchaseAmounts, stream);
    assertThat(amounts).containsExactly(99.99, 149.99);

    double totalRevenue = amounts.stream().mapToDouble(a -> a).sum();
    assertThat(totalRevenue).isCloseTo(249.98, within(0.01));
  }

  /**
   * Congratulations! You've completed Tutorial 07: Real World Optics
   *
   * <p>You now understand: ✓ How to manage nested user profiles and settings ✓ How to process
   * complex API responses ✓ How to handle e-commerce orders with optics ✓ How to validate and
   * transform data immutably ✓ How to build configuration management systems ✓ How to process
   * event streams and extract metrics
   *
   * <p>You've completed the Optics tutorial series! 🎉
   *
   * <p>You now have all the tools to build type-safe, composable data transformations in Java
   * using higher-kinded-j!
   */
}
