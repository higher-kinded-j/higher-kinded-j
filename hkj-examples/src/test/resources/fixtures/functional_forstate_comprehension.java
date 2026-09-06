// Fixture for hkj-book/src/functional/forstate_comprehension.md
//
// The page walks every ForState operation over three workflows: an order, an offer and a customer.
// The records, the lenses each operation names and the services the comprehensions call are
// declared here, so a snippet showing one operation is one operation long.
//
// The lenses are written by hand rather than generated: the page names them `userLens`,
// `addressLens` and so on, which is what a reader binds `@GenerateLenses`' output to.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.higherkindedj.hkt.instances.Witnesses.io;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.expression.ForState;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.util.Traversals;

record User(String id, String name, double loyaltyDiscount) {}

record Address(String street, String city, String zip) {}

record ShippingAddress(String street, String city) {}

record Preferences(String theme) {}

record Offer(String code) {}

record Dashboard(String user, int count, boolean ready) {}

record Employee(String name, int salary) {}

record Department(String name, int budgetCents) {}

record Customer(String name, Address address, int loyaltyPoints) {}

sealed interface OrderStatus {
  record Pending(String reason) implements OrderStatus {}

  record Confirmed(String id) implements OrderStatus {}
}

record OrderWorkflow(
    User user,
    Address address,
    int shippingCents,
    boolean inventoryOk,
    double discount,
    String confirmationId) {}

record OfferWorkflow(User user, Preferences prefs, Offer offer, String receipt) {}

record OrderContext(
    String orderId,
    OrderStatus status,
    String extractedConfirmationId,
    ShippingAddress shippingAddress,
    List<String> tags,
    int totalCents,
    boolean validated) {}

class Fixture {

  static final String userId = "u-1";

  static final Monad<IdKind.Witness> idMonad = Instances.monad(id());

  static final Monad<IOKind.Witness> ioMonad = Instances.monad(io());

  static final MonadZero<MaybeKind.Witness> maybeMonad = Instances.monadZero(maybe());

  static final MonadZero<MaybeKind.Witness> monad = maybeMonad;

  static final User user = new User("u-1", "Alice", 0.1);

  static final Address address = new Address("221B Baker Street", "London", "NW1");

  static final Customer customer = new Customer("Alice", address, 100);

  static final Department department = new Department("Sales", 70000);

  static final List<Employee> employeeList = List.of(new Employee("Alice", 50000));

  static final Dashboard dashboard = new Dashboard("Alice", 5, false);

  static final OrderWorkflow initialWorkflow0 =
      new OrderWorkflow(user, address, 0, false, 0.0, "");

  static final OfferWorkflow initialWorkflow =
      new OfferWorkflow(user, new Preferences("dark"), new Offer("SAVE10"), "");

  static final OrderContext ctx =
      new OrderContext(
          "ORD-1",
          new OrderStatus.Confirmed("CONF-123"),
          "",
          new ShippingAddress("221B Baker Street", "London"),
          List.of("priority"),
          1000,
          false);

  static final OrderContext order = ctx;

  static final int shippingCost = 500;

  static final Iso<Integer, Double> centsToDollars =
      Iso.of(c -> c / 100.0, d -> (int) (d * 100));

  // --- Lenses the page names ---

  static final Lens<OfferWorkflow, User> userLens =
      Lens.of(OfferWorkflow::user, (w, v) -> new OfferWorkflow(v, w.prefs(), w.offer(), w.receipt()));

  static final Lens<OfferWorkflow, Preferences> prefsLens =
      Lens.of(OfferWorkflow::prefs, (w, v) -> new OfferWorkflow(w.user(), v, w.offer(), w.receipt()));

  static final Lens<OfferWorkflow, Offer> offerLens =
      Lens.of(OfferWorkflow::offer, (w, v) -> new OfferWorkflow(w.user(), w.prefs(), v, w.receipt()));

  static final Lens<OfferWorkflow, String> receiptLens =
      Lens.of(OfferWorkflow::receipt, (w, v) -> new OfferWorkflow(w.user(), w.prefs(), w.offer(), v));

  static final Lens<OrderContext, OrderStatus> statusLens =
      Lens.of(
          OrderContext::status,
          (c, v) ->
              new OrderContext(
                  c.orderId(), v, c.extractedConfirmationId(), c.shippingAddress(), c.tags(),
                  c.totalCents(), c.validated()));

  static final Lens<OrderContext, String> extractedIdLens =
      Lens.of(
          OrderContext::extractedConfirmationId,
          (c, v) ->
              new OrderContext(
                  c.orderId(), c.status(), v, c.shippingAddress(), c.tags(), c.totalCents(),
                  c.validated()));

  static final Lens<OrderContext, String> confirmationLens = extractedIdLens;

  static final Lens<OrderContext, Boolean> validatedLens =
      Lens.of(
          OrderContext::validated,
          (c, v) ->
              new OrderContext(
                  c.orderId(), c.status(), c.extractedConfirmationId(), c.shippingAddress(),
                  c.tags(), c.totalCents(), v));

  static final Lens<OrderContext, ShippingAddress> addressLens =
      Lens.of(
          OrderContext::shippingAddress,
          (c, v) ->
              new OrderContext(
                  c.orderId(), c.status(), c.extractedConfirmationId(), v, c.tags(),
                  c.totalCents(), c.validated()));

  static final Lens<OrderContext, List<String>> tagsLens =
      Lens.of(
          OrderContext::tags,
          (c, v) ->
              new OrderContext(
                  c.orderId(), c.status(), c.extractedConfirmationId(), c.shippingAddress(), v,
                  c.totalCents(), c.validated()));

  static final Lens<OrderContext, Integer> totalLens =
      Lens.of(
          OrderContext::totalCents,
          (c, v) ->
              new OrderContext(
                  c.orderId(), c.status(), c.extractedConfirmationId(), c.shippingAddress(),
                  c.tags(), v, c.validated()));

  static final Lens<ShippingAddress, String> cityLens =
      Lens.of(ShippingAddress::city, (a, v) -> new ShippingAddress(a.street(), v));

  static final Lens<Address, String> streetLens =
      Lens.of(Address::street, (a, v) -> new Address(v, a.city(), a.zip()));

  static final Lens<Address, String> zipLens =
      Lens.of(Address::zip, (a, v) -> new Address(a.street(), a.city(), v));

  static final Lens<Address, String> addressCityLens =
      Lens.of(Address::city, (a, v) -> new Address(a.street(), v, a.zip()));

  static final Lens<Customer, Address> customerAddressLens =
      Lens.of(Customer::address, (c, v) -> new Customer(c.name(), v, c.loyaltyPoints()));

  static final Lens<Customer, String> nameLens =
      Lens.of(Customer::name, (c, v) -> new Customer(v, c.address(), c.loyaltyPoints()));

  static final Lens<Customer, Integer> loyaltyLens =
      Lens.of(Customer::loyaltyPoints, (c, v) -> new Customer(c.name(), c.address(), v));

  static final Lens<Dashboard, Boolean> readyLens =
      Lens.of(Dashboard::ready, (d, v) -> new Dashboard(d.user(), d.count(), v));

  static final Lens<Dashboard, Integer> countLens =
      Lens.of(Dashboard::count, (d, v) -> new Dashboard(d.user(), v, d.ready()));

  static final Lens<Employee, Integer> salaryLens =
      Lens.of(Employee::salary, (e, v) -> new Employee(e.name(), v));

  static final Lens<Department, Integer> budgetLens =
      Lens.of(Department::budgetCents, (d, v) -> new Department(d.name(), v));

  static final Prism<OrderStatus, String> confirmedIdPrism =
      Prism.of(
          s ->
              s instanceof OrderStatus.Confirmed c
                  ? Optional.of(c.id())
                  : Optional.<String>empty(),
          OrderStatus.Confirmed::new);

  // --- The services the comprehensions call ---

  static Kind<MaybeKind.Witness, User> getUser(String userId) {
    return MAYBE.just(user);
  }

  static Kind<MaybeKind.Witness, Address> lookupAddress(User user) {
    return MAYBE.just(address);
  }

  static Kind<MaybeKind.Witness, Integer> calculateShipping(Address address) {
    return MAYBE.just(500);
  }

  static Kind<MaybeKind.Witness, Boolean> checkInventory(User user, Address address) {
    return MAYBE.just(true);
  }

  static Kind<MaybeKind.Witness, String> confirmOrder(User user, Boolean inventoryOk) {
    return MAYBE.just("CONF-123");
  }

  static String buildReceipt(
      User user, Address address, Integer shipping, String confirmation, Double discount) {
    return "receipt";
  }

  static Kind<MaybeKind.Witness, User> fetchUser(String userId) {
    return MAYBE.just(user);
  }

  static Kind<MaybeKind.Witness, Preferences> fetchPreferences(User user) {
    return MAYBE.just(new Preferences("dark"));
  }

  static Kind<MaybeKind.Witness, Offer> calculateOffer(User user, Preferences prefs) {
    return MAYBE.just(new Offer("SAVE10"));
  }

  static Kind<MaybeKind.Witness, String> applyDiscount(Offer offer, User user) {
    return MAYBE.just("receipt");
  }

  static String formatReceipt(User user, String receipt) {
    return receipt;
  }

  static Kind<MaybeKind.Witness, Boolean> validateOrder(String orderId) {
    return MAYBE.just(true);
  }

  static Kind<MaybeKind.Witness, String> processPayment(OrderContext ctx) {
    return MAYBE.just("CONF-123");
  }

  static Kind<MaybeKind.Witness, Unit> logEvent(String message) {
    return MAYBE.just(Unit.INSTANCE);
  }

  static boolean isValid(String tag) {
    return !tag.isBlank();
  }
}
