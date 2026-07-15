// Fixture for .claude/skills/hkj-optics/reference/cookbook.md
//
// Each recipe is an independent illustration, so each is compiled independently. A recipe that
// declares its own records shadows the ones here; a recipe that does not gets them from here. The
// domain types below therefore mirror the shapes the recipes show, and the generated-style
// companions (`OrderLenses`, `SuccessLenses`, ...) are hand-written rather than generated: the
// processor cannot generate a companion for a record a snippet has re-declared for itself, and the
// same class must compile against every recipe on the page.
//
// Where two recipes give one name two different shapes (Recipe 6's `Employee` has a salary, Recipe
// 9's has an email; Recipe 7's `Product` has a `Money` price, Recipe 13's a `BigDecimal`), the one
// optic that cannot bridge both is a typed `stub()`. Only its type is load-bearing for a compile
// gate, and a stub says so honestly rather than faking a getter the record does not have.
//
// NOTE: the imports below look unused *here*. They are for the snippet this file is spliced into.
// That is why spotless excludes src/test/resources/fixtures (see build.gradle.kts).

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.util.Prisms;
import org.higherkindedj.optics.util.Traversals;

// ===== Recipe 1: nested Optional fields =====

record Settings(boolean darkMode, int fontSize) {}

record Profile(String bio, Optional<Settings> settings) {}

record User(String name, Optional<Profile> profile) {}

final class SettingsLenses {
  static Lens<Settings, Integer> fontSize() {
    return Lens.of(Settings::fontSize, (s, size) -> new Settings(s.darkMode(), size));
  }
}

final class ProfileLenses {
  static Lens<Profile, Optional<Settings>> settings() {
    return Lens.of(Profile::settings, (p, s) -> new Profile(p.bio(), s));
  }
}

final class UserLenses {
  static Lens<User, String> name() {
    return Lens.of(User::name, (u, name) -> new User(name, u.profile()));
  }

  static Lens<User, Optional<Profile>> profile() {
    return Lens.of(User::profile, (u, p) -> new User(u.name(), p));
  }
}

// ===== Recipe 2: a sum type whose variants carry fields =====

record Data(String value) {}

sealed interface ApiResponse permits Success, Error, Loading {}

record Success(Data data, String timestamp) implements ApiResponse {}

record Error(String message, int code) implements ApiResponse {}

record Loading() implements ApiResponse {}

final class SuccessLenses {
  static Lens<Success, Data> data() {
    return Lens.of(Success::data, (s, d) -> new Success(d, s.timestamp()));
  }
}

// ===== Recipe 3 / 10 / 14: orders and their line items =====

record Money(BigDecimal amount) {
  Money multiply(double factor) {
    return new Money(amount.multiply(BigDecimal.valueOf(factor)));
  }
}

record LineItem(String productId, int quantity, Money price) {}

record Order(String id, List<LineItem> items) {}

final class OrderLenses {
  static Lens<Order, List<LineItem>> items() {
    return Lens.of(Order::items, (o, items) -> new Order(o.id(), items));
  }
}

final class LineItemLenses {
  static Lens<LineItem, Money> price() {
    return Lens.of(
        LineItem::price, (li, p) -> new LineItem(li.productId(), li.quantity(), p));
  }
}

// ===== Recipe 4: polymorphic events =====

sealed interface Event permits UserEvent, SystemEvent {}

record UserEvent(String userId, String action) implements Event {}

record SystemEvent(String level, String message) implements Event {}

final class UserEventLenses {
  static Lens<UserEvent, String> action() {
    return Lens.of(UserEvent::action, (e, a) -> new UserEvent(e.userId(), a));
  }
}

// ===== Recipe 5 / 15: configuration =====

record Config(Map<String, String> settings) {}

final class ConfigLenses {
  static Lens<Config, Map<String, String>> settings() {
    return Lens.of(Config::settings, (c, s) -> new Config(s));
  }
}

// ===== Recipe 6 / 9 / 11: the org chart =====

@GenerateFocus
record Employee(String name, int salary) {}

@GenerateFocus
record Department(String name, List<Employee> employees) {}

@GenerateFocus
record Company(List<Department> departments) {}

record Team(String name, Employee lead, List<Employee> members) {}

final class EmployeeLenses {
  static Lens<Employee, Integer> salary() {
    return Lens.of(Employee::salary, (e, salary) -> new Employee(e.name(), salary));
  }
}

final class DepartmentLenses {
  static Lens<Department, List<Employee>> employees() {
    return Lens.of(Department::employees, (d, es) -> new Department(d.name(), es));
  }
}

final class CompanyLenses {
  static Lens<Company, List<Department>> departments() {
    return Lens.of(Company::departments, (c, ds) -> new Company(ds));
  }
}

// ===== Recipe 7 / 13: the catalogue =====

/**
 * Recipe 7's product. Recipe 13 declares its own, with a stock level and a BigDecimal price; each
 * recipe's declaration shadows this one in its own compilation unit.
 */
record Product(String name, Money price, boolean onSale) {}

record Inventory(List<Product> products) {}

enum Category {
  ELECTRONICS,
  GROCERY
}

final class ProductLenses {
  /**
   * A typed stand-in: Recipe 7's {@code Product} has a {@code Money} price and Recipe 13's a {@code
   * BigDecimal} one, so no single body can read the field on both.
   */
  static Lens<Product, Money> price() {
    return Fixture.stub();
  }
}

// ===== Recipe 8: Either =====

record ValidationError(String message) {}

record UserData(String name, String email) {}

// ===== Recipe 12: notifications =====

sealed interface Notification permits Email, SMS, Push {}

record Email(String address, String subject, String body) implements Notification {}

record SMS(String number, String text) implements Notification {}

record Push(String token, String text) implements Notification {}

class Fixture {

  // --- the structures the recipes operate on ---

  static final User user = new User("Alice", Optional.empty());

  static final ApiResponse response = new Success(new Data("ok"), "2026-01-01T00:00:00Z");

  static final Order order = new Order("o-1", List.of());

  static final List<Event> events = List.of();

  static final Config config = new Config(Map.of("api.key", "0123456789"));

  static final Company company = new Company(List.of());

  static final Team team = new Team("Platform", new Employee("Alice", 100), List.of());

  static final List<Product> products = List.of();

  static final Inventory inventory = new Inventory(List.of());

  static final Either<ValidationError, UserData> result =
      Either.right(new UserData("alice", "alice@example.com"));

  // --- Recipe 9: the folds and lenses the page names but never builds ---

  static final Lens<Team, Employee> teamLeadLens =
      Lens.of(Team::lead, (t, lead) -> new Team(t.name(), lead, t.members()));

  static final Lens<Employee, String> employeeNameLens =
      Lens.of(Employee::name, (e, name) -> new Employee(name, e.salary()));

  /** Recipe 9's Employee has an email; Recipe 6's, which shadows it, has a salary. */
  static final Lens<Employee, String> employeeEmailLens = stub();

  // --- Recipe 11 ---

  static final Lens<Company, List<Department>> companyDeptsLens = CompanyLenses.departments();

  static final Lens<Department, List<Employee>> deptEmployeesLens = DepartmentLenses.employees();

  static final Lens<Employee, Integer> employeeSalaryLens = EmployeeLenses.salary();

  // --- Recipe 12 ---

  /** Recipe 12's User carries notifications; Recipe 1's, which shadows it, carries a profile. */
  static final Lens<User, List<Notification>> userNotificationsLens = stub();

  static final Lens<Email, String> emailAddressLens =
      Lens.of(Email::address, (e, a) -> new Email(a, e.subject(), e.body()));

  static final Lens<Email, String> emailSubjectLens =
      Lens.of(Email::subject, (e, s) -> new Email(e.address(), s, e.body()));

  // --- Recipe 13 ---

  static final Lens<Inventory, List<Product>> inventoryProductsLens =
      Lens.of(Inventory::products, (i, ps) -> new Inventory(ps));

  // --- Recipe 14 ---

  static final Lens<Order, List<LineItem>> orderItemsLens = OrderLenses.items();

  static final Lens<LineItem, Integer> lineItemQuantityLens =
      Lens.of(LineItem::quantity, (li, q) -> new LineItem(li.productId(), q, li.price()));

  static final Lens<LineItem, BigDecimal> lineItemPriceLens =
      Lens.of(
          li -> li.price().amount(),
          (li, amount) -> new LineItem(li.productId(), li.quantity(), new Money(amount)));

  static final Monoid<Integer> intSumMonoid = Monoids.integerAddition();

  static final Monoid<BigDecimal> decimalSumMonoid =
      new Monoid<>() {
        @Override
        public BigDecimal empty() {
          return BigDecimal.ZERO;
        }

        @Override
        public BigDecimal combine(BigDecimal a, BigDecimal b) {
          return a.add(b);
        }
      };

  // --- Recipe 15 ---

  static final Lens<Config, String> configApiKeyLens =
      Lens.of(
          c -> c.settings().getOrDefault("api.key", ""),
          (c, key) -> {
            Map<String, String> updated = new HashMap<>(c.settings());
            updated.put("api.key", key);
            return new Config(Map.copyOf(updated));
          });

  // --- Best practice 2 ---

  /** An optional nested record: the field the page composes a Prisms.some() onto. */
  static final Lens<Config, Optional<Settings>> configLens = stub();

  static final Prism<Optional<Settings>, Settings> settingsPrism = Prisms.some();

  /**
   * The reader's own optics, which the page names without ever building. Only their types are
   * load-bearing for a compile gate, so they stand in unimplemented rather than pretending to a
   * behaviour the page never claims.
   */
  static <T> T stub() {
    throw new UnsupportedOperationException("the reader supplies this");
  }
}
