// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListKindHelper;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.junit.jupiter.api.Test;

/**
 * Solutions for Tutorial 13: Advanced Focus DSL - Type Class Integration
 *
 * <p>These are the completed solutions for all exercises in Tutorial13_AdvancedFocusDSL.java
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
        keyPath.modifyF(validateAndTransform, config, MaybeMonad.INSTANCE);

    Maybe<Config> maybeConfig = MaybeKindHelper.MAYBE.narrow(result);
    assertThat(maybeConfig.isJust()).isTrue();
    assertThat(maybeConfig.get().apiKey()).isEqualTo("ABC123");

    Config shortKeyConfig = new Config("abc");
    // SOLUTION: Invalid key returns Nothing
    Kind<MaybeKind.Witness, Config> invalidResult =
        keyPath.modifyF(validateAndTransform, shortKeyConfig, MaybeMonad.INSTANCE);

    Maybe<Config> invalidMaybe = MaybeKindHelper.MAYBE.narrow(invalidResult);
    assertThat(invalidMaybe.isNothing()).isTrue();
  }

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
