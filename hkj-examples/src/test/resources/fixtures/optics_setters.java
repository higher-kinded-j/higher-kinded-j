// Fixture for hkj-book/src/optics/setters.md
//
// The page writes through users, products and a company without ever reading back, and builds its
// setters by hand with `Setter.fromGetSet`. The models are declared here, along with the setters
// the later sections name but do not repeat; a snippet that shows a model shadows this copy.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.optional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Setter;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateSetters;
import org.higherkindedj.optics.util.Traversals;

@GenerateSetters
record UserSettings(
    String theme, boolean notifications, int fontSize, Map<String, String> preferences) {}

@GenerateSetters
record User(String username, String email, int loginCount, UserSettings settings) {}

@GenerateSetters
record Product(String name, double price, int stock, List<String> tags) {}

@GenerateSetters
record Inventory(List<Product> products, String warehouseId) {}

record Person(String name, int age) {}

record Order(String orderId, List<Product> products) {}

record Company(String name, List<Person> employees, List<Product> products) {}

class Fixture {

  static final UserSettings settings = new UserSettings("light", true, 14, Map.of());

  static final User user = new User("JOHN_DOE", "john@example.com", 10, settings);

  static final List<User> users = List.of(user);

  static final Person person = new Person("Ada", 36);

  static final Product laptop = new Product("Laptop", 999.99, 50, List.of("electronics"));

  static final List<Product> usdProducts = List.of(laptop);

  static final Order order = new Order("ORD-1", usdProducts);

  static final Company company = new Company("Initech", List.of(person), usdProducts);

  static final List<String> rawStrings = List.of("  one  ", " two ");

  static final List<Integer> largeList = List.of(1, 2, 3);

  static final Setter<User, UserSettings> settingsSetter =
      Setter.fromGetSet(
          User::settings, (u, s) -> new User(u.username(), u.email(), u.loginCount(), s));

  static final Setter<UserSettings, String> themeSetter =
      Setter.fromGetSet(
          UserSettings::theme,
          (s, theme) -> new UserSettings(theme, s.notifications(), s.fontSize(), s.preferences()));

  static final Setter<UserSettings, Integer> fontSizeSetter =
      Setter.fromGetSet(
          UserSettings::fontSize,
          (s, size) -> new UserSettings(s.theme(), s.notifications(), size, s.preferences()));

  static final Setter<User, Integer> loginCountSetter =
      Setter.fromGetSet(
          User::loginCount, (u, count) -> new User(u.username(), u.email(), count, u.settings()));

  static final Setter<List<User>, User> usersSetter = Setter.forList();

  static final Setter<Product, Double> priceSetter =
      Setter.fromGetSet(
          Product::price, (p, price) -> new Product(p.name(), price, p.stock(), p.tags()));

  static final Setter<List<Product>, Product> productsSetter = Setter.forList();

  static final Setter<List<Product>, Product> productSetter = productsSetter;

  static final Setter<List<Person>, Person> employeesSetter = Setter.forList();

  static final Setter<Person, String> personNameSetter =
      Setter.fromGetSet(Person::name, (p, name) -> new Person(name, p.age()));

  static final Setter<Company, List<Person>> companySetter =
      Setter.fromGetSet(
          Company::employees, (c, e) -> new Company(c.name(), e, c.products()));

  static final Setter<Company, List<Product>> companyProductsSetter =
      Setter.fromGetSet(
          Company::products, (c, p) -> new Company(c.name(), c.employees(), p));

  static final Traversal<Order, Product> productTraversal =
      Lens.<Order, List<Product>>of(Order::products, (o, p) -> new Order(o.orderId(), p))
          .asTraversal()
          .andThen(Traversals.forList());

  static final Applicative<OptionalKind.Witness> applicative = Instances.monadError(optional());

  static final Function<String, Kind<OptionalKind.Witness, String>> validateFn =
      name -> OptionalKindHelper.OPTIONAL.widen(Optional.of(name));

  static final Function<Integer, Kind<OptionalKind.Witness, Integer>> validateAndTransform =
      n -> OptionalKindHelper.OPTIONAL.widen(Optional.of(n * 2));
}
