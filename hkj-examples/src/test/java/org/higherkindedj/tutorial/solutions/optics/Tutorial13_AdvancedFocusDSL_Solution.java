// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.optics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.MaybeAssert.assertThatMaybe;
import static org.higherkindedj.hkt.instances.Witnesses.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListKindHelper;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.junit.jupiter.api.Test;

/**
 * Solution for Tutorial13 AdvancedFocusDSL — teaching-solution format.
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
public class Tutorial13_AdvancedFocusDSL_Solution {

  // ========== Test Domain Types ==========

  record Role(String name, int level) {
    Role promote() {
      return new Role(name, level + 1);
    }
  }

  record User(String name, Kind<ListKind.Witness, Role> roles) {}

  record Team(String name, List<User> members) {}

  record Employee(String name, int yearsOfService, int salary) {
    Employee withSalary(int newSalary) {
      return new Employee(name, yearsOfService, newSalary);
    }
  }

  record Department(List<Employee> employees) {}

  sealed interface Shape permits Circle, Rectangle {}

  record Circle(double radius) implements Shape {}

  record Rectangle(double width, double height) implements Shape {}

  record Drawing(List<Shape> shapes) {}

  // ========== Lenses ==========

  static final Lens<User, String> userNameLens =
      Lens.of(User::name, (u, n) -> new User(n, u.roles()));

  static final Lens<User, Kind<ListKind.Witness, Role>> userRolesLens =
      Lens.of(User::roles, (u, r) -> new User(u.name(), r));

  static final Lens<Role, Integer> roleLevelLens =
      Lens.of(Role::level, (r, l) -> new Role(r.name(), l));

  static final Lens<Team, List<User>> teamMembersLens =
      Lens.of(Team::members, (t, m) -> new Team(t.name(), m));

  static final Lens<Department, List<Employee>> deptEmployeesLens =
      Lens.of(Department::employees, (d, e) -> new Department(e));

  static final Lens<Employee, Integer> employeeSalaryLens =
      Lens.of(Employee::salary, (e, s) -> new Employee(e.name(), e.yearsOfService(), s));

  static final Lens<Employee, Integer> employeeYearsLens =
      Lens.of(Employee::yearsOfService, (e, y) -> new Employee(e.name(), y, e.salary()));

  static final Lens<Drawing, List<Shape>> drawingShapesLens =
      Lens.of(Drawing::shapes, (d, s) -> new Drawing(s));

  static final Lens<Circle, Double> circleRadiusLens =
      Lens.of(Circle::radius, (c, r) -> new Circle(r));

  /**
   * Why this is idiomatic: {@code modifyF} on a path takes a function that returns an effect (here
   * a {@code Maybe}), threads the effect through the navigation, and rebuilds the source.
   * Validation that may fail stays in the type.
   *
   * <p>Alternative: read with the lens, run the validator, branch on the result, write back.
   * Equivalent for one effect; {@code modifyF} composes for free with deeper paths.
   *
   * <p>Common wrong attempt: throw an exception from the validator. The effect channel is what the
   * path knows about; throwing leaks the failure outside the optic and skips any follow-up steps
   * the caller wired up.
   */
  @Test
  void exercise1_effectfulModification() {
    record Config(String apiKey) {}

    Lens<Config, String> apiKeyLens = Lens.of(Config::apiKey, (c, k) -> new Config(k));

    Config config = new Config("abc123");

    FocusPath<Config, String> keyPath = FocusPath.of(apiKeyLens);

    Function<String, Kind<MaybeKind.Witness, String>> validateAndTransform =
        key -> {
          if (key.length() >= 6) {
            return MaybeKindHelper.MAYBE.widen(Maybe.just(key.toUpperCase()));
          } else {
            return MaybeKindHelper.MAYBE.widen(Maybe.nothing());
          }
        };

    // SOLUTION: Use modifyF with MaybeMonad
    Kind<MaybeKind.Witness, Config> result =
        keyPath.modifyF(validateAndTransform, config, Instances.monadError(maybe()));

    Maybe<Config> maybeConfig = MaybeKindHelper.MAYBE.narrow(result);
    assertThatMaybe(maybeConfig)
        .isJust()
        .hasValueSatisfying(cfg -> assertThat(cfg.apiKey()).isEqualTo("ABC123"));

    Config shortKeyConfig = new Config("abc");
    // SOLUTION: Invalid key returns Nothing
    Kind<MaybeKind.Witness, Config> invalidResult =
        keyPath.modifyF(validateAndTransform, shortKeyConfig, Instances.monadError(maybe()));

    Maybe<Config> invalidMaybe = MaybeKindHelper.MAYBE.narrow(invalidResult);
    assertThatMaybe(invalidMaybe).isNothing();
  }

  /**
   * Why this is idiomatic: {@code foldMap(monoid, fn, source)} reduces every focused element
   * through the monoid — sum salaries with addition, find max with the max-monoid. Same traversal,
   * different question.
   *
   * <p>Alternative: {@code getAll(...).stream().mapToInt(...).sum()}. Equivalent for sums; the
   * monoid version generalises to any commutative reduction without changing shape.
   *
   * <p>Common wrong attempt: pick the wrong identity for the monoid (e.g. zero for max). The fold
   * over an empty set returns the identity, so the answer is wrong; double-check the identity
   * matches the intent.
   */
  @Test
  void exercise2_monoidAggregation() {
    Department dept =
        new Department(
            List.of(
                new Employee("Alice", 5, 60000),
                new Employee("Bob", 3, 50000),
                new Employee("Charlie", 8, 75000)));

    // Using intermediate step for type inference
    TraversalPath<Department, Employee> employeesPath = FocusPath.of(deptEmployeesLens).each();
    TraversalPath<Department, Integer> salariesPath = employeesPath.via(employeeSalaryLens);

    Monoid<Integer> intAddition =
        new Monoid<>() {
          @Override
          public Integer empty() {
            return 0;
          }

          @Override
          public Integer combine(Integer a, Integer b) {
            return a + b;
          }
        };

    // SOLUTION: Use foldMap to sum salaries
    int totalSalary = salariesPath.foldMap(intAddition, salary -> salary, dept);
    assertThat(totalSalary).isEqualTo(185000);

    TraversalPath<Department, Integer> yearsPath = employeesPath.via(employeeYearsLens);

    // SOLUTION: Sum years of service
    int totalYears = yearsPath.foldMap(intAddition, years -> years, dept);
    assertThat(totalYears).isEqualTo(16);

    Monoid<Integer> intMax =
        new Monoid<>() {
          @Override
          public Integer empty() {
            return Integer.MIN_VALUE;
          }

          @Override
          public Integer combine(Integer a, Integer b) {
            return Math.max(a, b);
          }
        };

    // SOLUTION: Find max salary using max monoid
    int maxSalary = salariesPath.foldMap(intMax, salary -> salary, dept);
    assertThat(maxSalary).isEqualTo(75000);
  }

  /**
   * Why this is idiomatic: {@code traverseOver(traverse)} lifts any {@code Traverse} type class
   * instance into a path. {@code ListTraverse.INSTANCE} is the bridge between the higher-kinded
   * list and the path DSL.
   *
   * <p>Alternative: build a custom traversal that knows about {@code Kind<ListKind.Witness, A>}.
   * Same answer; using the canonical {@code ListTraverse} instance keeps the integration stable
   * across versions.
   *
   * <p>Common wrong attempt: confuse {@code traverseOver} with {@code each()}. {@code each()} works
   * on plain {@code List<A>} fields; {@code traverseOver} is for fields already wrapped in {@code
   * Kind<F, A>}.
   */
  @Test
  void exercise3_traverseTypeClassIntegration() {
    User user =
        new User(
            "Alice",
            ListKindHelper.LIST.widen(
                List.of(new Role("Admin", 10), new Role("Developer", 5), new Role("Viewer", 1))));

    FocusPath<User, Kind<ListKind.Witness, Role>> rolesKindPath = FocusPath.of(userRolesLens);

    // SOLUTION: Use traverseOver with ListTraverse
    TraversalPath<User, Role> allRolesPath =
        rolesKindPath.<ListKind.Witness, Role>traverseOver(ListTraverse.INSTANCE);

    // SOLUTION: Get all roles
    List<Role> roles = allRolesPath.getAll(user);
    assertThat(roles).hasSize(3);
    assertThat(roles.stream().map(Role::name).toList())
        .containsExactly("Admin", "Developer", "Viewer");

    // SOLUTION: Modify all roles
    User promoted = allRolesPath.modifyAll(Role::promote, user);

    List<Role> promotedRoles =
        rolesKindPath.<ListKind.Witness, Role>traverseOver(ListTraverse.INSTANCE).getAll(promoted);
    assertThat(promotedRoles.stream().map(Role::level).toList()).containsExactly(11, 6, 2);
  }

  /**
   * Why this is idiomatic: {@code traverseOver(...).via(lens)} chains an HKT traversal with a lens
   * — User → roles → level for every role. The composed path is one named optic with all the usual
   * operations.
   *
   * <p>Alternative: extract the levels into a list, transform, and rewrite the user. Same runtime;
   * the composed path stays in one expression and survives schema growth.
   *
   * <p>Common wrong attempt: forget the {@code traverseOver} step and try to {@code via} directly
   * into a {@code Kind<ListKind.Witness, Role>}. The lens does not know how to iterate a
   * higher-kinded container; the {@code Traverse} instance is what supplies the iteration.
   */
  @Test
  void exercise4_traverseOverComposition() {
    User user =
        new User(
            "Bob",
            ListKindHelper.LIST.widen(
                List.of(new Role("Admin", 10), new Role("User", 3), new Role("Guest", 1))));

    FocusPath<User, Kind<ListKind.Witness, Role>> step1 = FocusPath.of(userRolesLens);
    TraversalPath<User, Role> step2 =
        step1.<ListKind.Witness, Role>traverseOver(ListTraverse.INSTANCE);

    // SOLUTION: Compose with lens
    TraversalPath<User, Integer> allLevelsPath = step2.via(roleLevelLens);

    // SOLUTION: Get all levels
    List<Integer> levels = allLevelsPath.getAll(user);
    assertThat(levels).containsExactly(10, 3, 1);

    // SOLUTION: Modify all levels
    User doubled = allLevelsPath.modifyAll(level -> level * 2, user);

    List<Integer> doubledLevels =
        FocusPath.of(userRolesLens)
            .<ListKind.Witness, Role>traverseOver(ListTraverse.INSTANCE)
            .via(roleLevelLens)
            .getAll(doubled);
    assertThat(doubledLevels).containsExactly(20, 6, 2);
  }

  /**
   * Why this is idiomatic: {@code modifyWhen(predicate, fn, source)} narrows the focus to elements
   * that satisfy a predicate and applies the function only to them. The other elements pass through
   * untouched.
   *
   * <p>Alternative: {@code modifyAll(e -> matches(e) ? fn(e) : e, source)}. Equivalent runtime; the
   * named version advertises the predicate at the optic level so other reads inherit the same
   * scope.
   *
   * <p>Common wrong attempt: assume non-matching elements are removed. They are kept as-is; if
   * removal is wanted, filter the source list before the path navigates it.
   */
  @Test
  void exercise5_conditionalModification() {
    Department dept =
        new Department(
            List.of(
                new Employee("Alice", 8, 60000),
                new Employee("Bob", 2, 45000),
                new Employee("Charlie", 6, 55000),
                new Employee("Diana", 1, 40000)));

    TraversalPath<Department, Employee> employeesPath = FocusPath.of(deptEmployeesLens).each();

    // SOLUTION: Use modifyWhen for conditional modification
    Department afterRaises =
        employeesPath.modifyWhen(
            e -> e.yearsOfService() >= 5, e -> e.withSalary((int) (e.salary() * 1.10)), dept);

    List<Integer> updatedSalaries = employeesPath.via(employeeSalaryLens).getAll(afterRaises);

    assertThat(updatedSalaries).containsExactly(66000, 45000, 60500, 40000);
  }

  /**
   * Why this is idiomatic: {@code AffinePath.instanceOf(Class)} is the canonical "narrow to this
   * variant" affine. Composed with the shapes traversal, it produces a {@code
   * TraversalPath<Drawing, Circle>} that touches only circles.
   *
   * <p>Alternative: a hand-rolled prism per variant. {@code instanceOf} is the named shorthand for
   * the common case where the class is the discriminator.
   *
   * <p>Common wrong attempt: cast the shapes inside {@code modifyAll} ({@code (Circle) s}).
   * Non-circles throw at runtime; the affine refuses cleanly with no exception.
   */
  @Test
  void exercise6_sumTypeNavigation() {
    Drawing drawing =
        new Drawing(
            List.of(
                new Circle(5.0),
                new Rectangle(10.0, 20.0),
                new Circle(3.0),
                new Rectangle(5.0, 5.0),
                new Circle(7.0)));

    TraversalPath<Drawing, Shape> allShapes = FocusPath.of(drawingShapesLens).each();

    // SOLUTION: Create instanceOf for Circles
    AffinePath<Shape, Circle> circlePrism = AffinePath.instanceOf(Circle.class);

    // SOLUTION: Compose to get circles path
    TraversalPath<Drawing, Circle> circlesPath = allShapes.via(circlePrism);

    // SOLUTION: Get all circles
    List<Circle> circles = circlesPath.getAll(drawing);
    assertThat(circles).hasSize(3);
    assertThat(circles.stream().map(Circle::radius).toList()).containsExactly(5.0, 3.0, 7.0);

    // SOLUTION: Modify all circle radii
    Drawing enlarged = circlesPath.via(circleRadiusLens).modifyAll(r -> r * 2, drawing);

    List<Circle> enlargedCircles =
        FocusPath.of(drawingShapesLens)
            .each()
            .via(AffinePath.instanceOf(Circle.class))
            .getAll(enlarged);
    assertThat(enlargedCircles.stream().map(Circle::radius).toList())
        .containsExactly(10.0, 6.0, 14.0);
  }

  /**
   * Why this is idiomatic: {@code path.traced(observer)} returns a path that fires the observer on
   * read. The behaviour is unchanged; the trace becomes diagnostic data — handy during development
   * without polluting production code.
   *
   * <p>Alternative: log inside a wrapping function. Works but is brittle when the path is built up
   * gradually; the {@code traced} variant attaches the observer to one point in the chain.
   *
   * <p>Common wrong attempt: assume the observer fires on writes too. The trace is deliberately
   * read-only; if write-tracking is needed, wrap the traversal's modify call in an observation step
   * at the call site.
   */
  @Test
  void exercise7_pathDebugging() {
    Department dept =
        new Department(List.of(new Employee("Alice", 5, 60000), new Employee("Bob", 3, 50000)));

    List<String> observations = new ArrayList<>();

    TraversalPath<Department, Employee> employeesPath = FocusPath.of(deptEmployeesLens).each();

    BiConsumer<Department, List<Employee>> observer =
        (source, employees) -> {
          for (Employee e : employees) {
            observations.add("Observed: " + e.name());
          }
        };

    // SOLUTION: Create traced path
    TraversalPath<Department, Employee> tracedPath = employeesPath.traced(observer);

    // SOLUTION: Get triggers observer
    List<Employee> employees = tracedPath.getAll(dept);

    assertThat(employees).hasSize(2);
    assertThat(observations).containsExactly("Observed: Alice", "Observed: Bob");

    observations.clear();
    tracedPath.modifyAll(e -> e.withSalary(e.salary() + 1000), dept);
    assertThat(observations).isEmpty();
  }

  /**
   * Why this is idiomatic: combine {@code each()}, {@code traverseOver}, and {@code via} to
   * navigate a team of users with role lists. One named path traverses every level and the caller
   * asks the questions through {@code getAll}, {@code modifyAll}, or {@code foldMap}.
   *
   * <p>Alternative: nested {@code stream().flatMap(...).flatMap(...)} chains. Same reads; the
   * optic-driven version makes the corresponding writes a single call.
   *
   * <p>Common wrong attempt: build several paths and merge their results with hand-rolled zips. The
   * composed path is the single source; the merge is what the path's {@code via} provides natively.
   */
  @Test
  void exercise8_combiningFeatures() {
    Team team =
        new Team(
            "Platform",
            List.of(
                new User(
                    "Alice",
                    ListKindHelper.LIST.widen(List.of(new Role("Admin", 10), new Role("Dev", 5)))),
                new User("Bob", ListKindHelper.LIST.widen(List.of(new Role("User", 2)))),
                new User(
                    "Charlie",
                    ListKindHelper.LIST.widen(
                        List.of(new Role("Admin", 8), new Role("Dev", 6), new Role("User", 3))))));

    FocusPath<Team, List<User>> step1 = FocusPath.of(teamMembersLens);
    TraversalPath<Team, User> step2 = step1.each();
    TraversalPath<Team, Kind<ListKind.Witness, Role>> step3 = step2.via(userRolesLens);
    TraversalPath<Team, Role> allRoles =
        step3.<ListKind.Witness, Role>traverseOver(ListTraverse.INSTANCE);

    // SOLUTION: Count total roles
    int totalRoles = allRoles.count(team);
    assertThat(totalRoles).isEqualTo(6);

    Monoid<Integer> intSum =
        new Monoid<>() {
          @Override
          public Integer empty() {
            return 0;
          }

          @Override
          public Integer combine(Integer a, Integer b) {
            return a + b;
          }
        };

    TraversalPath<Team, Integer> allLevels = allRoles.via(roleLevelLens);

    // SOLUTION: Sum all levels
    int totalLevels = allLevels.foldMap(intSum, level -> level, team);
    assertThat(totalLevels).isEqualTo(34);

    // SOLUTION: Promote high-level admins
    Team promotedTeam = allRoles.modifyWhen(r -> r.level() >= 8, Role::promote, team);

    // Reuse allLevels path from above
    List<Integer> updatedLevels = allLevels.getAll(promotedTeam);

    assertThat(updatedLevels).containsExactly(11, 5, 2, 9, 6, 3);
  }
}
