// Fixture for hkj-book/src/optics/setters.md
//
// The page writes through users, products and an intake of new hires without ever reading back,
// and builds its setters by hand with `Setter.fromGetSet`. The models are declared here, along with
// the setters the later sections name but do not repeat; a snippet that shows a model shadows this
// copy.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.optional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
record Product(String sku, String name, BigDecimal price, int stock) {}

@GenerateSetters
record Inventory(List<Product> products, String warehouseId) {}

record NewHire(String name, int age) {}

record Intake(String name, List<NewHire> hires) {}

class Fixture {

  static final UserSettings settings = new UserSettings("light", true, 14, Map.of());

  static final User user = new User("JOHN_DOE", "john@example.com", 10, settings);

  static final List<User> users = List.of(user);

  static final NewHire hire = new NewHire("Ada", 36);

  static final Product laptop = new Product("LAPTOP", "Laptop", new BigDecimal("999.99"), 50);

  static final List<Product> usdProducts = List.of(laptop);

  static final Inventory inventory = new Inventory(usdProducts, "WH-1");

  static final Intake intake = new Intake("September intake", List.of(hire));

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

  static final Setter<Product, BigDecimal> priceSetter =
      Setter.fromGetSet(
          Product::price, (p, price) -> new Product(p.sku(), p.name(), price, p.stock()));

  static final Setter<List<Product>, Product> productsSetter = Setter.forList();

  static final Setter<List<Product>, Product> productSetter = productsSetter;

  static final Setter<List<NewHire>, NewHire> hiresSetter = Setter.forList();

  static final Setter<NewHire, String> hireNameSetter =
      Setter.fromGetSet(NewHire::name, (h, name) -> new NewHire(name, h.age()));

  static final Setter<Intake, List<NewHire>> intakeSetter =
      Setter.fromGetSet(Intake::hires, (i, h) -> new Intake(i.name(), h));

  static final Traversal<Inventory, Product> productTraversal =
      Lens.<Inventory, List<Product>>of(
              Inventory::products, (i, p) -> new Inventory(p, i.warehouseId()))
          .asTraversal()
          .andThen(Traversals.forList());

  static final Applicative<OptionalKind.Witness> applicative = Instances.monadError(optional());

  static final Function<String, Kind<OptionalKind.Witness, String>> validateFn =
      name -> OptionalKindHelper.OPTIONAL.widen(Optional.of(name));

  static final Function<Integer, Kind<OptionalKind.Witness, Integer>> validateAndTransform =
      n -> OptionalKindHelper.OPTIONAL.widen(Optional.of(n * 2));
}
