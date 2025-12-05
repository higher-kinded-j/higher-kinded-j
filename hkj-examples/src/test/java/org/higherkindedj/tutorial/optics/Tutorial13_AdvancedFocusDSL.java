// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.optics;

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
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 13: Advanced Focus DSL - Type Class Integration
 *
 * <p>This tutorial covers advanced features of the Focus DSL that integrate with higher-kinded-j
 * type classes for effectful operations, monoid aggregation, and generic collection traversal.
 *
 * <p>Key concepts:
 *
 * <ul>
 *   <li><b>modifyF()</b>: Effectful modification using Applicative/Monad
 *   <li><b>foldMap()</b>: Aggregate values using a Monoid
 *   <li><b>traverseOver()</b>: Generic collection traversal via Traverse type class
 *   <li><b>modifyWhen()</b>: Conditional modification with predicates
 *   <li><b>instanceOf()</b>: Focus on specific variants of sealed interfaces
 *   <li><b>traced()</b>: Debug path navigation with observers
 * </ul>
 *
 * <p>Prerequisites: Complete Tutorial 12 (Focus DSL basics) before this one.
 */
public class Tutorial13_AdvancedFocusDSL {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // ========== Test Domain Types ==========

  /** A role with name and privilege level. */
  record Role(String name, int level) {
    Role promote() {
      return new Role(name, level + 1);
    }
  }

  /** A user with roles stored as Kind<ListKind.Witness, Role>. */
  record User(String name, Kind<ListKind.Witness, Role> roles) {}

  /** A team with members. */
  record Team(String name, List<User> members) {}

  /** An employee with salary. */
  record Employee(String name, int yearsOfService, int salary) {
    Employee withSalary(int newSalary) {
      return new Employee(name, yearsOfService, newSalary);
    }
  }

  /** A department with employees. */
  record Department(List<Employee> employees) {}

  // Sealed interface for sum type exercises
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
   * Exercise 1: Effectful Modification with modifyF()
   *
   * <p>The modifyF() method allows you to perform effectful modifications where the transformation
   * function returns a value wrapped in an effect type (like Maybe, Either, or IO).
   *
   * <p>This is useful for:
   *
   * <ul>
   *   <li>Validation that may fail
   *   <li>Async operations
   *   <li>Operations that may produce no result
   * </ul>
   *
   * <p>Task: Use modifyF to validate and transform a value
   */
  @Test
  void exercise1_effectfulModification() {
    record Config(String apiKey) {}

    Lens<Config, String> apiKeyLens = Lens.of(Config::apiKey, (c, k) -> new Config(k));

    Config config = new Config("abc123");

    FocusPath<Config, String> keyPath = FocusPath.of(apiKeyLens);

    // A validation function that returns Maybe.just if key is valid, Maybe.nothing if not
    // Valid keys must be at least 6 characters
    Function<String, Kind<MaybeKind.Witness, String>> validateAndTransform =
        key -> {
          if (key.length() >= 6) {
            return MaybeKindHelper.MAYBE.widen(Maybe.just(key.toUpperCase()));
          } else {
            return MaybeKindHelper.MAYBE.widen(Maybe.nothing());
          }
        };

    // Use modifyF with MaybeMonad to perform the effectful modification
    // TODO: Replace null with keyPath.modifyF(validateAndTransform, config, MaybeMonad.INSTANCE)
    Kind<MaybeKind.Witness, Config> result = answerRequired();

    // The result should be Just(Config("ABC123")) because the key is valid
    Maybe<Config> maybeConfig = MaybeKindHelper.MAYBE.narrow(result);
    assertThat(maybeConfig.isJust()).isTrue();
    assertThat(maybeConfig.get().apiKey()).isEqualTo("ABC123");

    // Now test with an invalid key
    Config shortKeyConfig = new Config("abc");
    // TODO: Replace null with keyPath.modifyF(validateAndTransform, shortKeyConfig,
    // MaybeMonad.INSTANCE)
    Kind<MaybeKind.Witness, Config> invalidResult = answerRequired();

    Maybe<Config> invalidMaybe = MaybeKindHelper.MAYBE.narrow(invalidResult);
    assertThat(invalidMaybe.isNothing()).isTrue();
  }

  /**
   * Exercise 2: Monoid Aggregation with foldMap()
   *
   * <p>TraversalPath supports foldMap() to aggregate values using a Monoid. This is powerful for
   * computing summaries across multiple focused elements.
   *
   * <p>Task: Use foldMap to compute aggregate values
   */
  @Test
  void exercise2_monoidAggregation() {
    Department dept =
        new Department(
            List.of(
                new Employee("Alice", 5, 60000),
                new Employee("Bob", 3, 50000),
                new Employee("Charlie", 8, 75000)));

    // Path to all employee salaries - using intermediate step for type inference
    TraversalPath<Department, Employee> employeesPath = FocusPath.of(deptEmployeesLens).each();
    TraversalPath<Department, Integer> salariesPath = employeesPath.via(employeeSalaryLens);

    // Define a Monoid for integer addition
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

    // Use foldMap to sum all salaries
    // foldMap takes a Monoid and a mapper function
    // TODO: Replace 0 with salariesPath.foldMap(intAddition, salary -> salary, dept)
    int totalSalary = answerRequired();
    assertThat(totalSalary).isEqualTo(185000);

    // Path to all years of service - reusing employeesPath from above
    TraversalPath<Department, Integer> yearsPath = employeesPath.via(employeeYearsLens);

    // Sum all years of service
    // TODO: Replace 0 with yearsPath.foldMap(intAddition, years -> years, dept)
    int totalYears = answerRequired();
    assertThat(totalYears).isEqualTo(16);

    // Use foldMap to find maximum salary (using max monoid pattern)
    // We'll map salaries to a wrapper that tracks the max
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

    // TODO: Replace 0 with salariesPath.foldMap(intMax, salary -> salary, dept)
    int maxSalary = answerRequired();
    assertThat(maxSalary).isEqualTo(75000);
  }

  /**
   * Exercise 3: Generic Collection Traversal with traverseOver()
   *
   * <p>When your data contains collections wrapped in Kind<F, A> (rather than raw List<A>), use
   * traverseOver() with a Traverse instance to navigate into the collection elements.
   *
   * <p>Task: Use traverseOver to access elements in Kind-wrapped collections
   */
  @Test
  void exercise3_traverseTypeClassIntegration() {
    User user =
        new User(
            "Alice",
            ListKindHelper.LIST.widen(
                List.of(new Role("Admin", 10), new Role("Developer", 5), new Role("Viewer", 1))));

    // The roles field is Kind<ListKind.Witness, Role>, not List<Role>
    // We need to use traverseOver() with ListTraverse to navigate into it
    FocusPath<User, Kind<ListKind.Witness, Role>> rolesKindPath = FocusPath.of(userRolesLens);

    // TODO: Replace null with rolesKindPath.<ListKind.Witness,
    // Role>traverseOver(ListTraverse.INSTANCE)
    TraversalPath<User, Role> allRolesPath = answerRequired();

    // Get all roles
    // TODO: Replace null with allRolesPath.getAll(user)
    List<Role> roles = answerRequired();
    assertThat(roles).hasSize(3);
    assertThat(roles.stream().map(Role::name).toList())
        .containsExactly("Admin", "Developer", "Viewer");

    // Modify all roles - promote everyone
    // TODO: Replace null with allRolesPath.modifyAll(Role::promote, user)
    User promoted = answerRequired();

    List<Role> promotedRoles =
        rolesKindPath.<ListKind.Witness, Role>traverseOver(ListTraverse.INSTANCE).getAll(promoted);
    assertThat(promotedRoles.stream().map(Role::level).toList()).containsExactly(11, 6, 2);
  }

  /**
   * Exercise 4: Chaining traverseOver with other operations
   *
   * <p>traverseOver() returns a TraversalPath, which can be further composed with via() to navigate
   * deeper into each element.
   *
   * <p>Task: Compose traverseOver with via to access nested properties
   */
  @Test
  void exercise4_traverseOverComposition() {
    User user =
        new User(
            "Bob",
            ListKindHelper.LIST.widen(
                List.of(new Role("Admin", 10), new Role("User", 3), new Role("Guest", 1))));

    // Build path step by step for clarity
    FocusPath<User, Kind<ListKind.Witness, Role>> step1 = FocusPath.of(userRolesLens);
    TraversalPath<User, Role> step2 =
        step1.<ListKind.Witness, Role>traverseOver(ListTraverse.INSTANCE);

    // Compose with roleLevelLens to get all levels
    // TODO: Replace null with step2.via(roleLevelLens)
    TraversalPath<User, Integer> allLevelsPath = answerRequired();

    // Get all levels
    // TODO: Replace null with allLevelsPath.getAll(user)
    List<Integer> levels = answerRequired();
    assertThat(levels).containsExactly(10, 3, 1);

    // Modify all levels - double them
    // TODO: Replace null with allLevelsPath.modifyAll(level -> level * 2, user)
    User doubled = answerRequired();

    List<Integer> doubledLevels =
        FocusPath.of(userRolesLens)
            .<ListKind.Witness, Role>traverseOver(ListTraverse.INSTANCE)
            .via(roleLevelLens)
            .getAll(doubled);
    assertThat(doubledLevels).containsExactly(20, 6, 2);
  }

  /**
   * Exercise 5: Conditional Modification with modifyWhen()
   *
   * <p>modifyWhen() allows you to modify only elements that match a predicate, leaving others
   * unchanged.
   *
   * <p>Task: Use modifyWhen to selectively update elements
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

    // Give a 10% raise only to employees with 5+ years of service
    // TODO: Replace null with:
    // employeesPath.modifyWhen(
    //     e -> e.yearsOfService() >= 5,
    //     e -> e.withSalary((int) (e.salary() * 1.10)),
    //     dept)
    Department afterRaises = answerRequired();

    List<Integer> updatedSalaries = employeesPath.via(employeeSalaryLens).getAll(afterRaises);

    // Alice (8 years): 60000 * 1.10 = 66000
    // Bob (2 years): unchanged at 45000
    // Charlie (6 years): 55000 * 1.10 = 60500
    // Diana (1 year): unchanged at 40000
    assertThat(updatedSalaries).containsExactly(66000, 45000, 60500, 40000);
  }

  /**
   * Exercise 6: Working with Sum Types using instanceOf()
   *
   * <p>AffinePath.instanceOf() creates a path that focuses on values of a specific subtype. This is
   * useful for working with sealed interfaces and other sum types.
   *
   * <p>Task: Use instanceOf to focus on specific variants
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

    // Path to all shapes
    TraversalPath<Drawing, Shape> allShapes = FocusPath.of(drawingShapesLens).each();

    // Create a path that only focuses on Circles
    // TODO: Replace null with AffinePath.instanceOf(Circle.class)
    AffinePath<Shape, Circle> circlePrism = answerRequired();

    // Compose to get a path to all circles in the drawing
    // TODO: Replace null with allShapes.via(circlePrism)
    TraversalPath<Drawing, Circle> circlesPath = answerRequired();

    // Get all circles
    // TODO: Replace null with circlesPath.getAll(drawing)
    List<Circle> circles = answerRequired();
    assertThat(circles).hasSize(3);
    assertThat(circles.stream().map(Circle::radius).toList()).containsExactly(5.0, 3.0, 7.0);

    // Double all circle radii
    // TODO: Replace null with circlesPath.via(circleRadiusLens).modifyAll(r -> r * 2, drawing)
    Drawing enlarged = answerRequired();

    List<Circle> enlargedCircles =
        FocusPath.of(drawingShapesLens)
            .each()
            .via(AffinePath.instanceOf(Circle.class))
            .getAll(enlarged);
    assertThat(enlargedCircles.stream().map(Circle::radius).toList())
        .containsExactly(10.0, 6.0, 14.0);
  }

  /**
   * Exercise 7: Path Debugging with traced()
   *
   * <p>The traced() method adds an observer that is called during getAll() operations, allowing you
   * to debug complex path navigation.
   *
   * <p>Task: Use traced to observe path navigation
   */
  @Test
  void exercise7_pathDebugging() {
    Department dept =
        new Department(List.of(new Employee("Alice", 5, 60000), new Employee("Bob", 3, 50000)));

    // Create a list to capture observed values
    List<String> observations = new ArrayList<>();

    // Path to all employee names
    TraversalPath<Department, Employee> employeesPath = FocusPath.of(deptEmployeesLens).each();

    // Create an observer that logs each employee accessed
    BiConsumer<Department, List<Employee>> observer =
        (source, employees) -> {
          for (Employee e : employees) {
            observations.add("Observed: " + e.name());
          }
        };

    // Create a traced path
    // TODO: Replace null with employeesPath.traced(observer)
    TraversalPath<Department, Employee> tracedPath = answerRequired();

    // Get all employees - this should trigger the observer
    // TODO: Replace null with tracedPath.getAll(dept)
    List<Employee> employees = answerRequired();

    assertThat(employees).hasSize(2);
    assertThat(observations).containsExactly("Observed: Alice", "Observed: Bob");

    // Note: traced() only observes during getAll(), not during modifyAll()
    observations.clear();
    tracedPath.modifyAll(e -> e.withSalary(e.salary() + 1000), dept);
    assertThat(observations).isEmpty(); // No observations during modify
  }

  /**
   * Exercise 8: Combining Multiple Advanced Features
   *
   * <p>This exercise combines traverseOver, modifyWhen, and foldMap to solve a real-world scenario.
   *
   * <p>Task: Process a team structure using multiple Focus DSL features
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

    // Build a path to all roles across all team members
    FocusPath<Team, List<User>> step1 = FocusPath.of(teamMembersLens);
    TraversalPath<Team, User> step2 = step1.each();
    TraversalPath<Team, Kind<ListKind.Witness, Role>> step3 = step2.via(userRolesLens);
    TraversalPath<Team, Role> allRoles =
        step3.<ListKind.Witness, Role>traverseOver(ListTraverse.INSTANCE);

    // Count total number of roles
    // TODO: Replace 0 with allRoles.count(team)
    int totalRoles = answerRequired();
    assertThat(totalRoles).isEqualTo(6);

    // Sum all role levels using foldMap
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

    // Get path to all role levels
    TraversalPath<Team, Integer> allLevels = allRoles.via(roleLevelLens);

    // TODO: Replace 0 with allLevels.foldMap(intSum, level -> level, team)
    int totalLevels = answerRequired();
    assertThat(totalLevels).isEqualTo(34); // 10+5+2+8+6+3

    // Promote all Admin roles (those with level >= 8)
    // TODO: Replace null with allRoles.modifyWhen(r -> r.level() >= 8, Role::promote, team)
    Team promotedTeam = answerRequired();

    // Verify only high-level admins were promoted - reuse allLevels path
    List<Integer> updatedLevels = allLevels.getAll(promotedTeam);

    // Alice's Admin (10->11), Dev (5 unchanged)
    // Bob's User (2 unchanged)
    // Charlie's Admin (8->9), Dev (6 unchanged), User (3 unchanged)
    assertThat(updatedLevels).containsExactly(11, 5, 2, 9, 6, 3);
  }

  /**
   * Congratulations! You have completed Tutorial 13: Advanced Focus DSL
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>modifyF() for effectful modifications with Applicative/Monad
   *   <li>foldMap() for aggregating values using Monoid
   *   <li>traverseOver() for generic collection traversal via Traverse
   *   <li>modifyWhen() for conditional modifications
   *   <li>instanceOf() for sum type navigation
   *   <li>traced() for debugging path navigation
   * </ul>
   *
   * <p>Key Takeaways:
   *
   * <ul>
   *   <li>Type class integration enables powerful abstractions over effects and aggregation
   *   <li>traverseOver() bridges the Traverse type class with optics traversal
   *   <li>modifyWhen() provides clean syntax for conditional updates
   *   <li>instanceOf() makes sum type handling type-safe and ergonomic
   *   <li>traced() helps debug complex optic compositions
   * </ul>
   *
   * <p>Next Steps:
   *
   * <ul>
   *   <li>Explore the TraverseTraversals utility class for more Traverse-based optics
   *   <li>Combine Focus DSL with Free Monad patterns for complex workflows
   *   <li>Create custom Traverse instances for your collection types
   * </ul>
   */
}
