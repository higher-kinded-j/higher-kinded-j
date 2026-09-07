// Fixture for hkj-book/src/functional/for_optics.md
//
// The page runs one payroll through every optic-aware comprehension operation. The domain, the
// optics each operation names and the monads they run under are declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.optics.indexed.Pair;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.expression.ForIndexed;
import org.higherkindedj.hkt.expression.ForState;
import org.higherkindedj.hkt.expression.ForTraversal;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.optics.indexed.IndexedTraversal;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.IndexedTraversals;
import org.higherkindedj.optics.util.Traversals;

record Department(String name, int budgetInCents, List<Employee> staff) {}

record Employee(String name, int salaryInCents, Department department) {}

record Player(String name, int score) {}

record Temperature(double value) {}

sealed interface Category {
  record Premium(double discount) implements Category {}

  record Standard() implements Category {}
}

record Item(String name, Category category) {}

sealed interface PayrollResult permits Paid, Skipped {}

record Paid(String employeeName, int amount) implements PayrollResult {}

record Skipped(String reason) implements PayrollResult {}

class Fixture {

  static final Monad<IdKind.Witness> idMonad = Instances.monad(id());

  static final Applicative<IdKind.Witness> idApplicative = idMonad;

  static final MonadZero<ListKind.Witness> listMonad = Instances.monadZero(list());

  static final MonadZero<MaybeKind.Witness> maybeMonad = Instances.monadZero(maybe());

  static final Department department =
      new Department("Engineering", 500000, List.of());

  static final List<Employee> employees =
      List.of(
          new Employee("Alice", 80000, department), new Employee("Bob", 90000, department));

  static final List<Player> players =
      List.of(new Player("Alice", 100), new Player("Bob", 200), new Player("Charlie", 150));

  static final List<Item> items =
      List.of(new Item("Widget", new Category.Premium(0.2)), new Item("Nut", new Category.Standard()));

  static final List<Temperature> temperatures = List.of(new Temperature(20.0));

  static final Iso<Temperature, Temperature> celsiusToFahrenheitIso =
      Iso.of(c -> new Temperature(c.value() * 9 / 5 + 32), f -> new Temperature((f.value() - 32) * 5 / 9));

  static final Prism<Category, Category.Premium> premiumCategoryPrism =
      Prism.of(
          c -> c instanceof Category.Premium p ? Optional.of(p) : Optional.<Category.Premium>empty(),
          p -> p);

  static final Traversal<List<Employee>, Employee> empTraversal = Traversals.forList();

  static final Traversal<List<Player>, Player> playersTraversal = Traversals.forList();

  static final IndexedTraversal<Integer, List<Player>, Player> indexedPlayers =
      IndexedTraversals.forList();

  static final Lens<Department, Integer> budgetLens =
      Lens.of(Department::budgetInCents, (d, v) -> new Department(d.name(), v, d.staff()));

  static final Lens<Department, List<Employee>> staffLens =
      Lens.of(Department::staff, (d, v) -> new Department(d.name(), d.budgetInCents(), v));

  static final Lens<Employee, String> empNameLens =
      Lens.of(Employee::name, (e, v) -> new Employee(v, e.salaryInCents(), e.department()));

  static final Lens<Employee, Integer> salaryLens =
      Lens.of(Employee::salaryInCents, (e, v) -> new Employee(e.name(), v, e.department()));

  static final Iso<Integer, Double> centsToDollars =
      Iso.of(cents -> cents / 100.0, dollars -> (int) (dollars * 100));

  static final Prism<PayrollResult, Paid> paidPrism =
      Prism.of(
          r -> r instanceof Paid paid ? Optional.of(paid) : Optional.<Paid>empty(), p -> p);

  // `focus` takes an optic, so the page's "reach into the department name" is a composed lens.
  static final Lens<Employee, Department> employeeDepartmentLens =
      Lens.of(Employee::department, (e, v) -> new Employee(e.name(), e.salaryInCents(), v));

  static final Lens<Department, String> departmentNameLens =
      Lens.of(Department::name, (d, v) -> new Department(v, d.budgetInCents(), d.staff()));

  static final Lens<Employee, String> departmentNameOfEmployee =
      employeeDepartmentLens.andThen(departmentNameLens);

  static final Lens<Item, Category> itemCategoryLens =
      Lens.of(Item::category, (i, v) -> new Item(i.name(), v));

  static final Lens<Player, Integer> scoreLens =
      Lens.of(Player::score, (p, s) -> new Player(p.name(), s));
}
