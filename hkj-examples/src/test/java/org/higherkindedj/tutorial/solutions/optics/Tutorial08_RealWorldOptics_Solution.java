// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.optics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.Update;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.Test;

/**
 * Solution for Tutorial08 RealWorldOptics — teaching-solution format.
 *
 * <p>This solution file follows the chapter's <em>teaching solution</em> conventions established by
 * the Foundations journey: read the working code first, then the commentary on <em>why</em> the
 * chosen form is idiomatic. The complete-with-commentary template (Why this is idiomatic /
 * Alternative / Common wrong attempt on every exercise) lives in the Foundations solutions
 * coretypes/Tutorial01_KindBasics_Solution.java as the canonical reference.
 *
 * <p>The exercise bodies below are correct working code. Per-exercise teaching commentary is being
 * rolled out across the chapter; if this file does not yet have it, treat the reference code as the
 * answer and consult the pilot solution for the format guide.
 *
 * <p>For the chapter-level guidance on how to learn from a solution, see the <a
 * href="../../../../../../../../../hkj-book/src/tutorials/solutions_guide.md">Solutions Guide</a>
 * in the book.
 */
public class Tutorial08_RealWorldOptics_Solution {

  // Sealed interfaces must be at class level
  @GeneratePrisms
  sealed interface ApiResponse2 {}

  record SuccessResponse2(List<Location2> locations, int count) implements ApiResponse2 {}

  record ErrorResponse2(String message, int code) implements ApiResponse2 {}

  @GenerateLenses
  record Location2(String city, String country, Coordinates2 coords) {}

  @GenerateLenses
  record Coordinates2(double lat, double lon) {}

  @GeneratePrisms
  sealed interface OrderStatus3 {}

  record Pending3() implements OrderStatus3 {}

  record Processing3() implements OrderStatus3 {}

  @GenerateLenses
  record Shipped3(String trackingNumber) implements OrderStatus3 {}

  @GeneratePrisms
  sealed interface Event6 {}

  @GenerateLenses
  record UserLogin6(String userId, long timestamp) implements Event6 {}

  @GenerateLenses
  record PageView6(String userId, String page, long timestamp) implements Event6 {}

  @GenerateLenses
  record Purchase6(String userId, double amount, long timestamp) implements Event6 {}

  // Manual traversal/lens/prism implementations (annotation processor generates these in real
  // projects)

  /** Helper to create a Traversal for List fields */
  static <S, A> Traversal<S, A> listTraversal(
      Function<S, List<A>> getter, BiFunction<S, List<A>, S> setter) {
    return new Traversal<S, A>() {
      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, S> modifyF(
          Function<A, Kind<F, A>> f, S s, Applicative<F> applicative) {
        List<A> list = getter.apply(s);
        var listKind = Traversals.traverseList(list, f, applicative);
        return applicative.map(newList -> setter.apply(s, newList), listKind);
      }
    };
  }

  /**
   * Why this is idiomatic: a real user profile is records-of-records-of-records. Each level gets
   * one lens; the composed path reads as a sentence and the update touches only the leaf field
   * while every other field is rebuilt automatically.
   *
   * <p>Alternative: rebuild the profile by hand at every level. Works once; the next field added to
   * {@code User} silently drifts to its default unless every site is updated.
   *
   * <p>Common wrong attempt: try to "patch" the inner record with reflection or a setter. Records
   * are immutable; the lens-driven rebuild is the only safe path.
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

    // Manual implementations (annotation processor would generate these)
    class NotificationPrefsLenses {
      public static Lens<NotificationPrefs, Boolean> email() {
        return Lens.of(
            NotificationPrefs::email,
            (np, newEmail) -> new NotificationPrefs(newEmail, np.sms(), np.push()));
      }

      public static Lens<NotificationPrefs, Boolean> sms() {
        return Lens.of(
            NotificationPrefs::sms,
            (np, newSms) -> new NotificationPrefs(np.email(), newSms, np.push()));
      }

      public static Lens<NotificationPrefs, Boolean> push() {
        return Lens.of(
            NotificationPrefs::push,
            (np, newPush) -> new NotificationPrefs(np.email(), np.sms(), newPush));
      }
    }

    class PrivacySettingsLenses {
      public static Lens<PrivacySettings, Boolean> publicProfile() {
        return Lens.of(
            PrivacySettings::publicProfile,
            (ps, newPublic) -> new PrivacySettings(newPublic, ps.showEmail()));
      }

      public static Lens<PrivacySettings, Boolean> showEmail() {
        return Lens.of(
            PrivacySettings::showEmail,
            (ps, newShow) -> new PrivacySettings(ps.publicProfile(), newShow));
      }
    }

    class UserSettingsLenses {
      public static Lens<UserSettings, NotificationPrefs> notifications() {
        return Lens.of(
            UserSettings::notifications,
            (us, newNotif) -> new UserSettings(newNotif, us.privacy()));
      }

      public static Lens<UserSettings, PrivacySettings> privacy() {
        return Lens.of(
            UserSettings::privacy,
            (us, newPrivacy) -> new UserSettings(us.notifications(), newPrivacy));
      }
    }

    class UserProfileLenses {
      public static Lens<UserProfile, String> id() {
        return Lens.of(
            UserProfile::id,
            (up, newId) -> new UserProfile(newId, up.name(), up.email(), up.settings()));
      }

      public static Lens<UserProfile, String> name() {
        return Lens.of(
            UserProfile::name,
            (up, newName) -> new UserProfile(up.id(), newName, up.email(), up.settings()));
      }

      public static Lens<UserProfile, String> email() {
        return Lens.of(
            UserProfile::email,
            (up, newEmail) -> new UserProfile(up.id(), up.name(), newEmail, up.settings()));
      }

      public static Lens<UserProfile, UserSettings> settings() {
        return Lens.of(
            UserProfile::settings,
            (up, newSettings) -> new UserProfile(up.id(), up.name(), up.email(), newSettings));
      }
    }

    UserProfile user =
        new UserProfile(
            "user123",
            "Alice",
            "alice@example.com",
            new UserSettings(
                new NotificationPrefs(true, false, true), new PrivacySettings(false, false)));

    // Compose lenses: UserProfile -> UserSettings -> NotificationPrefs -> email
    Lens<UserProfile, Boolean> emailNotifLens =
        UserProfileLenses.settings()
            .andThen(UserSettingsLenses.notifications())
            .andThen(NotificationPrefsLenses.email());

    // Enable email notifications
    UserProfile updated = emailNotifLens.set(true, user);
    assertThat(updated.settings().notifications().email()).isTrue();

    // Compose lens to access publicProfile setting
    Lens<UserProfile, Boolean> publicProfileLens =
        UserProfileLenses.settings()
            .andThen(UserSettingsLenses.privacy())
            .andThen(PrivacySettingsLenses.publicProfile());

    UserProfile toggled = publicProfileLens.modify(p -> !p, updated);
    assertThat(toggled.settings().privacy().publicProfile()).isTrue();
  }

  /**
   * Why this is idiomatic: API payloads are deeply nested records (response → data → coordinates →
   * lat). Composed lenses traverse the JSON-shaped graph without ever unwrapping intermediate
   * records by hand.
   *
   * <p>Alternative: pretty-print the payload, find the field, edit, re-serialise. Works for one-off
   * changes; loses the type-safety the records provide.
   *
   * <p>Common wrong attempt: extract the leaf with chained accessors and ignore the rebuild ({@code
   * response.data().coordinates().lat()}). That reads, but the corresponding write is the entire
   * reason the lens exists.
   */
  @Test
  void exercise2_apiResponseProcessing() {
    // Manual implementations (annotation processor would generate these)
    class CoordinatesLenses {
      public static Lens<Coordinates2, Double> lat() {
        return Lens.of(Coordinates2::lat, (c, newLat) -> new Coordinates2(newLat, c.lon()));
      }

      public static Lens<Coordinates2, Double> lon() {
        return Lens.of(Coordinates2::lon, (c, newLon) -> new Coordinates2(c.lat(), newLon));
      }
    }

    class LocationLenses {
      public static Lens<Location2, String> city() {
        return Lens.of(
            Location2::city, (l, newCity) -> new Location2(newCity, l.country(), l.coords()));
      }

      public static Lens<Location2, String> country() {
        return Lens.of(
            Location2::country, (l, newCountry) -> new Location2(l.city(), newCountry, l.coords()));
      }

      public static Lens<Location2, Coordinates2> coords() {
        return Lens.of(
            Location2::coords, (l, newCoords) -> new Location2(l.city(), l.country(), newCoords));
      }
    }

    class ApiResponsePrisms {
      public static Prism<ApiResponse2, SuccessResponse2> successResponse() {
        return Prism.of(
            ar -> ar instanceof SuccessResponse2 sr ? Optional.of(sr) : Optional.empty(), sr -> sr);
      }

      public static Prism<ApiResponse2, ErrorResponse2> errorResponse() {
        return Prism.of(
            ar -> ar instanceof ErrorResponse2 er ? Optional.of(er) : Optional.empty(), er -> er);
      }
    }

    class SuccessResponseTraversals {
      public static Traversal<SuccessResponse2, Location2> locations() {
        return listTraversal(
            SuccessResponse2::locations, (sr, locs) -> new SuccessResponse2(locs, sr.count()));
      }
    }

    ApiResponse2 response =
        new SuccessResponse2(
            List.of(
                new Location2("New York", "USA", new Coordinates2(40.7128, -74.0060)),
                new Location2("London", "UK", new Coordinates2(51.5074, -0.1278))),
            2);

    Prism<ApiResponse2, SuccessResponse2> successPrism = ApiResponsePrisms.successResponse();
    Traversal<SuccessResponse2, Location2> locationsTraversal =
        SuccessResponseTraversals.locations();

    // Compose: success prism -> locations traversal -> city lens
    Traversal<ApiResponse2, String> citiesTraversal =
        successPrism
            .asTraversal()
            .andThen(locationsTraversal)
            .andThen(LocationLenses.city().asTraversal());

    List<String> cities = Traversals.getAll(citiesTraversal, response);
    assertThat(cities).containsExactly("New York", "London");

    // Compose traversal to adjust all latitudes
    Traversal<ApiResponse2, Double> latitudesTraversal =
        successPrism
            .asTraversal()
            .andThen(locationsTraversal)
            .andThen(LocationLenses.coords().asTraversal())
            .andThen(CoordinatesLenses.lat().asTraversal());

    ApiResponse2 adjusted = Traversals.modify(latitudesTraversal, lat -> lat + 1.0, response);
    var successData = (SuccessResponse2) adjusted;
    assertThat(successData.locations().get(0).coords().lat()).isCloseTo(41.7128, within(0.001));
  }

  /**
   * Why this is idiomatic: orders mix lenses (status, customer), traversals (line items), and
   * prisms (payment methods). One named optic per concern keeps the discount logic, the status
   * update, and the per-item adjustment separate.
   *
   * <p>Alternative: a single sprawling {@code processOrder} method. Works for a tutorial;
   * production code grows the method past a thousand lines and unit-testing it becomes difficult.
   *
   * <p>Common wrong attempt: mutate the order in place with a {@code setStatus} method and a
   * mutable line-item list. The data flow is now hidden; testing a discount means resetting the
   * order state between cases.
   */
  @Test
  void exercise3_ecommerceOrderProcessing() {
    @GenerateLenses
    record LineItem(String productId, String name, int quantity, double price) {}

    @GenerateLenses
    @GenerateTraversals
    record Order(String orderId, List<LineItem> items, OrderStatus3 status, double discount) {}

    // Manual implementations (annotation processor would generate these)
    class LineItemLenses {
      public static Lens<LineItem, String> productId() {
        return Lens.of(
            LineItem::productId,
            (li, newId) -> new LineItem(newId, li.name(), li.quantity(), li.price()));
      }

      public static Lens<LineItem, String> name() {
        return Lens.of(
            LineItem::name,
            (li, newName) -> new LineItem(li.productId(), newName, li.quantity(), li.price()));
      }

      public static Lens<LineItem, Integer> quantity() {
        return Lens.of(
            LineItem::quantity,
            (li, newQty) -> new LineItem(li.productId(), li.name(), newQty, li.price()));
      }

      public static Lens<LineItem, Double> price() {
        return Lens.of(
            LineItem::price,
            (li, newPrice) -> new LineItem(li.productId(), li.name(), li.quantity(), newPrice));
      }
    }

    class OrderLenses {
      public static Lens<Order, String> orderId() {
        return Lens.of(
            Order::orderId, (o, newId) -> new Order(newId, o.items(), o.status(), o.discount()));
      }

      public static Lens<Order, List<LineItem>> items() {
        return Lens.of(
            Order::items,
            (o, newItems) -> new Order(o.orderId(), newItems, o.status(), o.discount()));
      }

      public static Lens<Order, OrderStatus3> status() {
        return Lens.of(
            Order::status,
            (o, newStatus) -> new Order(o.orderId(), o.items(), newStatus, o.discount()));
      }

      public static Lens<Order, Double> discount() {
        return Lens.of(
            Order::discount,
            (o, newDiscount) -> new Order(o.orderId(), o.items(), o.status(), newDiscount));
      }
    }

    class OrderTraversals {
      public static Traversal<Order, LineItem> items() {
        return listTraversal(
            Order::items, (o, items) -> new Order(o.orderId(), items, o.status(), o.discount()));
      }
    }

    Order order =
        new Order(
            "ORD-001",
            List.of(
                new LineItem("PROD-1", "Widget", 2, 10.0),
                new LineItem("PROD-2", "Gadget", 1, 25.0)),
            new Pending3(),
            0.0);

    // Compose traversal: items -> price
    Traversal<Order, Double> pricesTraversal =
        OrderTraversals.items().andThen(LineItemLenses.price().asTraversal());

    // Calculate total before discount (sum of unit prices)
    List<Double> prices = Traversals.getAll(pricesTraversal, order);
    double total = prices.stream().mapToDouble(p -> p).sum();
    assertThat(total).isEqualTo(35.0); // 10.0 + 25.0 (unit prices only)

    // Apply 10% discount to all items (multiply by 0.9)
    Order discounted = Traversals.modify(pricesTraversal, price -> price * 0.9, order);

    List<Double> newPrices = Traversals.getAll(pricesTraversal, discounted);
    double newTotal = newPrices.stream().mapToDouble(p -> p).sum();
    assertThat(newTotal).isCloseTo(31.5, within(0.01)); // 35.0 * 0.9

    // Update order status to shipped
    Lens<Order, OrderStatus3> statusLens = OrderLenses.status();
    Order shipped = statusLens.set(new Shipped3("TRACK-123"), discounted);
    assertThat(shipped.status()).isInstanceOf(Shipped3.class);
  }

  /**
   * Why this is idiomatic: each cleansing step is one named transform — trim names, normalise post
   * codes, lowercase emails. Composing them with lens-driven {@code modify} gives a single rebuilt
   * customer with every field at its canonical value.
   *
   * <p>Alternative: a single {@code normaliseCustomer} method that copies every field with its
   * cleansed value. Works once; loses the per-field naming, so the diff in code review shows "the
   * whole customer changed" instead of "the email was normalised".
   *
   * <p>Common wrong attempt: validate against the cleansed value but write back the original. The
   * cleansing pipeline must produce the value the rest of the system reads; keep the lens write at
   * the end, not somewhere in the middle.
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

    // Manual implementations (annotation processor would generate these)
    class AddressLenses {
      public static Lens<Address, String> street() {
        return Lens.of(
            Address::street,
            (a, newStreet) -> new Address(newStreet, a.city(), a.state(), a.zip()));
      }

      public static Lens<Address, String> city() {
        return Lens.of(
            Address::city, (a, newCity) -> new Address(a.street(), newCity, a.state(), a.zip()));
      }

      public static Lens<Address, String> state() {
        return Lens.of(
            Address::state, (a, newState) -> new Address(a.street(), a.city(), newState, a.zip()));
      }

      public static Lens<Address, String> zip() {
        return Lens.of(
            Address::zip, (a, newZip) -> new Address(a.street(), a.city(), a.state(), newZip));
      }
    }

    class CustomerLenses {
      public static Lens<Customer, String> id() {
        return Lens.of(
            Customer::id, (c, newId) -> new Customer(newId, c.name(), c.email(), c.addresses()));
      }

      public static Lens<Customer, String> name() {
        return Lens.of(
            Customer::name,
            (c, newName) -> new Customer(c.id(), newName, c.email(), c.addresses()));
      }

      public static Lens<Customer, String> email() {
        return Lens.of(
            Customer::email,
            (c, newEmail) -> new Customer(c.id(), c.name(), newEmail, c.addresses()));
      }

      public static Lens<Customer, List<Address>> addresses() {
        return Lens.of(
            Customer::addresses,
            (c, newAddrs) -> new Customer(c.id(), c.name(), c.email(), newAddrs));
      }
    }

    class CustomerTraversals {
      public static Traversal<Customer, Address> addresses() {
        return listTraversal(
            Customer::addresses, (c, addrs) -> new Customer(c.id(), c.name(), c.email(), addrs));
      }
    }

    class CustomerDatabaseTraversals {
      public static Traversal<CustomerDatabase, Customer> customers() {
        return listTraversal(
            CustomerDatabase::customers, (db, custs) -> new CustomerDatabase(custs));
      }
    }

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

    // Compose traversal: customers -> name
    Traversal<CustomerDatabase, String> namesTraversal =
        CustomerDatabaseTraversals.customers().andThen(CustomerLenses.name().asTraversal());

    // Trim and normalise names
    CustomerDatabase normalised = Traversals.modify(namesTraversal, String::trim, db);

    List<String> names = Traversals.getAll(namesTraversal, normalised);
    assertThat(names.get(0)).isEqualTo("Alice Smith"); // Trimmed

    // Compose traversal: customers -> addresses -> city
    Traversal<CustomerDatabase, String> citiesTraversal =
        CustomerDatabaseTraversals.customers()
            .andThen(CustomerTraversals.addresses())
            .andThen(AddressLenses.city().asTraversal());

    // Capitalise all cities
    CustomerDatabase capitalised =
        Traversals.modify(
            citiesTraversal,
            city -> city.substring(0, 1).toUpperCase() + city.substring(1),
            normalised);

    List<String> cities = Traversals.getAll(citiesTraversal, capitalised);
    assertThat(cities).contains("Springfield", "Shelbyville");
  }

  /**
   * Why this is idiomatic: configuration is a layered tree — base, environment overrides,
   * per-instance tweaks. Lenses through each layer let the merge stay declarative; the environment
   * override is a {@code modify} on the matching field, nothing more.
   *
   * <p>Alternative: a property-merging library that walks the trees by key. Works for string-keyed
   * maps; loses the type-safety records provide.
   *
   * <p>Common wrong attempt: encode every override as a string-keyed map and reach into fields by
   * name. A typo in a key compiles fine and silently keeps the default value. Records + lenses make
   * the field misnames a compile error.
   */
  @Test
  void exercise5_configurationManagement() {
    @GenerateLenses
    record DatabaseConfig(String host, int port, String username, boolean ssl) {}

    @GenerateLenses
    record CacheConfig(String host, int port, int ttl) {}

    @GenerateLenses
    record AppConfig(String environment, DatabaseConfig database, CacheConfig cache) {}

    // Manual implementations (annotation processor would generate these)
    class DatabaseConfigLenses {
      public static Lens<DatabaseConfig, String> host() {
        return Lens.of(
            DatabaseConfig::host,
            (dc, newHost) -> new DatabaseConfig(newHost, dc.port(), dc.username(), dc.ssl()));
      }

      public static Lens<DatabaseConfig, Integer> port() {
        return Lens.of(
            DatabaseConfig::port,
            (dc, newPort) -> new DatabaseConfig(dc.host(), newPort, dc.username(), dc.ssl()));
      }

      public static Lens<DatabaseConfig, String> username() {
        return Lens.of(
            DatabaseConfig::username,
            (dc, newUser) -> new DatabaseConfig(dc.host(), dc.port(), newUser, dc.ssl()));
      }

      public static Lens<DatabaseConfig, Boolean> ssl() {
        return Lens.of(
            DatabaseConfig::ssl,
            (dc, newSsl) -> new DatabaseConfig(dc.host(), dc.port(), dc.username(), newSsl));
      }
    }

    class CacheConfigLenses {
      public static Lens<CacheConfig, String> host() {
        return Lens.of(
            CacheConfig::host, (cc, newHost) -> new CacheConfig(newHost, cc.port(), cc.ttl()));
      }

      public static Lens<CacheConfig, Integer> port() {
        return Lens.of(
            CacheConfig::port, (cc, newPort) -> new CacheConfig(cc.host(), newPort, cc.ttl()));
      }

      public static Lens<CacheConfig, Integer> ttl() {
        return Lens.of(
            CacheConfig::ttl, (cc, newTtl) -> new CacheConfig(cc.host(), cc.port(), newTtl));
      }
    }

    class AppConfigLenses {
      public static Lens<AppConfig, String> environment() {
        return Lens.of(
            AppConfig::environment,
            (ac, newEnv) -> new AppConfig(newEnv, ac.database(), ac.cache()));
      }

      public static Lens<AppConfig, DatabaseConfig> database() {
        return Lens.of(
            AppConfig::database, (ac, newDb) -> new AppConfig(ac.environment(), newDb, ac.cache()));
      }

      public static Lens<AppConfig, CacheConfig> cache() {
        return Lens.of(
            AppConfig::cache,
            (ac, newCache) -> new AppConfig(ac.environment(), ac.database(), newCache));
      }
    }

    AppConfig devConfig =
        new AppConfig(
            "development",
            new DatabaseConfig("localhost", 5432, "dev_user", false),
            new CacheConfig("localhost", 6379, 3600));

    // Compose lens: AppConfig -> DatabaseConfig -> ssl
    Lens<AppConfig, Boolean> dbSslLens =
        AppConfigLenses.database().andThen(DatabaseConfigLenses.ssl());

    // Enable SSL for production
    AppConfig prodConfig = AppConfigLenses.environment().set("production", devConfig);
    prodConfig = dbSslLens.set(true, prodConfig);

    assertThat(prodConfig.environment()).isEqualTo("production");
    assertThat(prodConfig.database().ssl()).isTrue();

    // Update both database and cache hosts: each lens write is an Update<AppConfig>,
    // folded into one named migration step applied in a single pass
    Lens<AppConfig, String> dbHostLens =
        AppConfigLenses.database().andThen(DatabaseConfigLenses.host());
    Lens<AppConfig, String> cacheHostLens =
        AppConfigLenses.cache().andThen(CacheConfigLenses.host());

    Update<AppConfig> pointHostsAtProd =
        Monoids.<AppConfig>update()
            .combineAll(
                List.of(
                    c -> dbHostLens.set("prod.example.com", c),
                    c -> cacheHostLens.set("prod.example.com", c)));

    AppConfig updated = pointHostsAtProd.apply(prodConfig);

    assertThat(updated.database().host()).isEqualTo("prod.example.com");
    assertThat(updated.cache().host()).isEqualTo("prod.example.com");
  }

  /**
   * Why this is idiomatic: events are a sealed interface; a prism per variant lets the pipeline
   * classify each event without {@code instanceof} ladders. Metrics are folds over the matching
   * variants — write the prism once, fold many ways.
   *
   * <p>Alternative: visitor-pattern dispatch with one method per variant. More verbose, no extra
   * power; the prism + fold pair stays compact and composes with traversals when the event stream
   * is itself nested.
   *
   * <p>Common wrong attempt: branch on the variant inside the metric collector. Now the collector
   * knows about every event type and adding a new variant means touching every collector; the prism
   * + fold split keeps the concerns separate.
   */
  @Test
  void exercise6_eventProcessingPipeline() {
    @GenerateTraversals
    record EventStream(List<Event6> events) {}

    // Manual implementations (annotation processor would generate these)
    class UserLoginLenses {
      public static Lens<UserLogin6, String> userId() {
        return Lens.of(UserLogin6::userId, (ul, newId) -> new UserLogin6(newId, ul.timestamp()));
      }

      public static Lens<UserLogin6, Long> timestamp() {
        return Lens.of(UserLogin6::timestamp, (ul, newTs) -> new UserLogin6(ul.userId(), newTs));
      }
    }

    class PageViewLenses {
      public static Lens<PageView6, String> userId() {
        return Lens.of(
            PageView6::userId, (pv, newId) -> new PageView6(newId, pv.page(), pv.timestamp()));
      }

      public static Lens<PageView6, String> page() {
        return Lens.of(
            PageView6::page, (pv, newPage) -> new PageView6(pv.userId(), newPage, pv.timestamp()));
      }

      public static Lens<PageView6, Long> timestamp() {
        return Lens.of(
            PageView6::timestamp, (pv, newTs) -> new PageView6(pv.userId(), pv.page(), newTs));
      }
    }

    class PurchaseLenses {
      public static Lens<Purchase6, String> userId() {
        return Lens.of(
            Purchase6::userId, (p, newId) -> new Purchase6(newId, p.amount(), p.timestamp()));
      }

      public static Lens<Purchase6, Double> amount() {
        return Lens.of(
            Purchase6::amount, (p, newAmt) -> new Purchase6(p.userId(), newAmt, p.timestamp()));
      }

      public static Lens<Purchase6, Long> timestamp() {
        return Lens.of(
            Purchase6::timestamp, (p, newTs) -> new Purchase6(p.userId(), p.amount(), newTs));
      }
    }

    class EventPrisms {
      public static Prism<Event6, UserLogin6> userLogin() {
        return Prism.of(
            e -> e instanceof UserLogin6 ul ? Optional.of(ul) : Optional.empty(), ul -> ul);
      }

      public static Prism<Event6, PageView6> pageView() {
        return Prism.of(
            e -> e instanceof PageView6 pv ? Optional.of(pv) : Optional.empty(), pv -> pv);
      }

      public static Prism<Event6, Purchase6> purchase() {
        return Prism.of(e -> e instanceof Purchase6 p ? Optional.of(p) : Optional.empty(), p -> p);
      }
    }

    class EventStreamTraversals {
      public static Traversal<EventStream, Event6> events() {
        return listTraversal(EventStream::events, (es, events) -> new EventStream(events));
      }
    }

    EventStream stream =
        new EventStream(
            List.of(
                new UserLogin6("user1", 1000L),
                new PageView6("user1", "/home", 1001L),
                new Purchase6("user1", 99.99, 1002L),
                new Purchase6("user2", 149.99, 1003L),
                new PageView6("user2", "/products", 1004L)));

    Prism<Event6, Purchase6> purchasePrism = EventPrisms.purchase();

    // Compose traversal: events -> purchases -> amount
    Traversal<EventStream, Double> purchaseAmounts =
        EventStreamTraversals.events()
            .andThen(purchasePrism.asTraversal())
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
   * transform data immutably ✓ How to build configuration management systems ✓ How to process event
   * streams and extract metrics
   *
   * <p>You've completed the Optics tutorial series! 🎉
   *
   * <p>You now have all the tools to build type-safe, composable data transformations in Java using
   * higher-kinded-j!
   */
}
