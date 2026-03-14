// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.expression.ForState;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.Test;

/**
 * Solutions for Tutorial 04: Enhanced Optics Integration.
 *
 * <p>Each exercise solution demonstrates the correct usage of Phase 6 ForState and For
 * comprehension operations: traverseOver, modifyThrough, through(Iso), modifyVia, and updateVia.
 */
public class Tutorial04_EnhancedOpticsIntegration_Solution {

  // --- Shared Domain Model ---

  record Employee(String name, int salaryInCents) {}

  record Celsius(double value) {}

  record Fahrenheit(double value) {}

  record Wrapper(String inner) {}

  // --- Shared Optics ---

  static final Lens<Employee, Integer> salaryLens =
      Lens.of(Employee::salaryInCents, (e, s) -> new Employee(e.name(), s));

  static final Lens<Employee, String> empNameLens =
      Lens.of(Employee::name, (e, n) -> new Employee(n, e.salaryInCents()));

  static final Iso<Integer, Double> centsToDollars =
      Iso.of(cents -> cents / 100.0, dollars -> (int) (dollars * 100));

  static final Iso<Celsius, Fahrenheit> celsiusToFahrenheit =
      Iso.of(
          c -> new Fahrenheit(c.value() * 9.0 / 5.0 + 32),
          f -> new Celsius((f.value() - 32) * 5.0 / 9.0));

  static final Iso<String, Wrapper> stringToWrapper = Iso.of(Wrapper::new, Wrapper::inner);

  // --- Exercise 1 Solution ---
  // SOLUTION: Use traverseOver with Id monad to add 1000 cents to each salary.

  @Test
  void exercise1_basicTraverseOver() {
    IdMonad idMonad = IdMonad.instance();
    List<Employee> employees = List.of(new Employee("Alice", 50000), new Employee("Bob", 60000));

    Kind<IdKind.Witness, List<Employee>> result =
        ForState.withState(idMonad, Id.of(employees))
            .traverseOver(
                Traversals.forList(), e -> Id.of(new Employee(e.name(), e.salaryInCents() + 1000)))
            .yield();

    List<Employee> updated = IdKindHelper.ID.unwrap(result);
    assertThat(updated).containsExactly(new Employee("Alice", 51000), new Employee("Bob", 61000));
  }

  // --- Exercise 2 Solution ---
  // SOLUTION: Use traverseOver with Maybe monad to validate salaries > 40000.

  @Test
  void exercise2_traverseOverWithMaybeShortCircuit() {
    MaybeMonad maybeMonad = MaybeMonad.INSTANCE;
    List<Employee> validEmployees =
        List.of(new Employee("Alice", 50000), new Employee("Bob", 60000));
    List<Employee> mixedEmployees =
        List.of(
            new Employee("Alice", 50000),
            new Employee("Charlie", 30000),
            new Employee("Bob", 60000));

    Kind<MaybeKind.Witness, List<Employee>> validResult =
        ForState.withState(maybeMonad, MAYBE.just(validEmployees))
            .traverseOver(
                Traversals.forList(),
                e -> e.salaryInCents() > 40000 ? MAYBE.just(e) : MAYBE.nothing())
            .yield();

    Kind<MaybeKind.Witness, List<Employee>> mixedResult =
        ForState.withState(maybeMonad, MAYBE.just(mixedEmployees))
            .traverseOver(
                Traversals.forList(),
                e -> e.salaryInCents() > 40000 ? MAYBE.just(e) : MAYBE.nothing())
            .yield();

    assertThat(MAYBE.narrow(validResult)).isEqualTo(Maybe.just(validEmployees));
    assertThat(MAYBE.narrow(mixedResult)).isEqualTo(Maybe.nothing());
  }

  // --- Exercise 3 Solution ---
  // SOLUTION: Use modifyThrough with a pure function to uppercase names.

  @Test
  void exercise3_pureModifyThrough() {
    IdMonad idMonad = IdMonad.instance();
    List<Employee> employees = List.of(new Employee("Alice", 50000), new Employee("Bob", 60000));

    Kind<IdKind.Witness, List<Employee>> result =
        ForState.withState(idMonad, Id.of(employees))
            .modifyThrough(
                Traversals.forList(), e -> new Employee(e.name().toUpperCase(), e.salaryInCents()))
            .yield();

    List<Employee> updated = IdKindHelper.ID.unwrap(result);
    assertThat(updated).containsExactly(new Employee("ALICE", 50000), new Employee("BOB", 60000));
  }

  // --- Exercise 4 Solution ---
  // SOLUTION: Use three-argument modifyThrough with traversal + lens to double salaries.

  @Test
  void exercise4_modifyThroughWithLens() {
    IdMonad idMonad = IdMonad.instance();
    List<Employee> employees = List.of(new Employee("Alice", 50000), new Employee("Bob", 60000));

    Kind<IdKind.Witness, List<Employee>> result =
        ForState.withState(idMonad, Id.of(employees))
            .modifyThrough(Traversals.forList(), salaryLens, s -> s * 2)
            .yield();

    List<Employee> updated = IdKindHelper.ID.unwrap(result);
    assertThat(updated).containsExactly(new Employee("Alice", 100000), new Employee("Bob", 120000));
  }

  // --- Exercise 5 Solution ---
  // SOLUTION: Use For.from(...).through(celsiusToFahrenheit) to yield formatted string.

  @Test
  void exercise5_throughIsoBasics() {
    IdMonad idMonad = IdMonad.instance();

    Kind<IdKind.Witness, String> result =
        For.from(idMonad, Id.of(new Celsius(100.0)))
            .through(celsiusToFahrenheit)
            .yield(
                (celsius, fahrenheit) ->
                    celsius.value() + "\u00B0C = " + fahrenheit.value() + "\u00B0F");

    assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("100.0\u00B0C = 212.0\u00B0F");
  }

  // --- Exercise 6 Solution ---
  // SOLUTION: Use For.from with MonadZero, through(stringToWrapper), then when() guard.

  @Test
  void exercise6_throughIsoWithFilter() {
    MaybeMonad maybeMonad = MaybeMonad.INSTANCE;

    Kind<MaybeKind.Witness, String> passResult =
        For.from((MonadZero<MaybeKind.Witness>) maybeMonad, MAYBE.just("hello"))
            .through(stringToWrapper)
            .when(t -> t._2().inner().length() > 3)
            .yield((original, wrapped) -> original);

    Kind<MaybeKind.Witness, String> failResult =
        For.from((MonadZero<MaybeKind.Witness>) maybeMonad, MAYBE.just("hi"))
            .through(stringToWrapper)
            .when(t -> t._2().inner().length() > 3)
            .yield((original, wrapped) -> original);

    assertThat(MAYBE.narrow(passResult)).isEqualTo(Maybe.just("hello"));
    assertThat(MAYBE.narrow(failResult)).isEqualTo(Maybe.nothing());
  }

  // --- Exercise 7 Solution ---
  // SOLUTION: Use modifyVia to add $50 to salary via centsToDollars Iso.

  @Test
  void exercise7_modifyViaIso() {
    IdMonad idMonad = IdMonad.instance();
    Employee alice = new Employee("Alice", 50000);

    Kind<IdKind.Witness, Employee> result =
        ForState.withState(idMonad, Id.of(alice))
            .modifyVia(salaryLens, centsToDollars, d -> d + 50.0)
            .yield();

    Employee updated = IdKindHelper.ID.unwrap(result);
    assertThat(updated).isEqualTo(new Employee("Alice", 55000));
  }

  // --- Exercise 8 Solution ---
  // SOLUTION: Use updateVia to set salary to $750 via centsToDollars Iso.

  @Test
  void exercise8_updateViaIso() {
    IdMonad idMonad = IdMonad.instance();
    Employee alice = new Employee("Alice", 50000);

    Kind<IdKind.Witness, Employee> result =
        ForState.withState(idMonad, Id.of(alice))
            .updateVia(salaryLens, centsToDollars, 750.0)
            .yield();

    Employee updated = IdKindHelper.ID.unwrap(result);
    assertThat(updated).isEqualTo(new Employee("Alice", 75000));
  }

  // --- Exercise 9 Solution ---
  // SOLUTION: Chain traverseOver (validate), modifyThrough (uppercase), modifyThrough+lens (bonus).

  @Test
  void exercise9_combinedWorkflow() {
    MaybeMonad maybeMonad = MaybeMonad.INSTANCE;
    List<Employee> employees = List.of(new Employee("alice", 50000), new Employee("bob", 60000));

    Kind<MaybeKind.Witness, List<Employee>> result =
        ForState.withState(maybeMonad, MAYBE.just(employees))
            .traverseOver(
                Traversals.forList(), e -> e.salaryInCents() > 0 ? MAYBE.just(e) : MAYBE.nothing())
            .modifyThrough(
                Traversals.forList(), e -> new Employee(e.name().toUpperCase(), e.salaryInCents()))
            .modifyThrough(Traversals.forList(), salaryLens, s -> s + 5000)
            .yield();

    assertThat(MAYBE.narrow(result))
        .isEqualTo(Maybe.just(List.of(new Employee("ALICE", 55000), new Employee("BOB", 65000))));
  }

  // --- Exercise 10 Solution ---
  // SOLUTION: Use traverse with staffLens to validate, then modifyVia for budget increase.

  record Department(String name, List<Employee> staff, int budgetInCents) {}

  static final Lens<Department, List<Employee>> staffLens =
      Lens.of(Department::staff, (d, s) -> new Department(d.name(), s, d.budgetInCents()));

  static final Lens<Department, Integer> budgetLens =
      Lens.of(Department::budgetInCents, (d, b) -> new Department(d.name(), d.staff(), b));

  @Test
  void exercise10_departmentPayroll() {
    MaybeMonad maybeMonad = MaybeMonad.INSTANCE;
    Department engineering =
        new Department(
            "Engineering",
            List.of(new Employee("Alice", 80000), new Employee("Bob", 90000)),
            500000);

    Kind<MaybeKind.Witness, Department> result =
        ForState.withState(maybeMonad, MAYBE.just(engineering))
            .traverse(
                staffLens,
                Traversals.forList(),
                e -> e.salaryInCents() > 0 ? MAYBE.just(e) : MAYBE.nothing())
            .modifyVia(budgetLens, centsToDollars, d -> d * 1.1)
            .yield();

    Maybe<Department> maybeDept = MAYBE.narrow(result);
    assertThat(maybeDept.isJust()).isTrue();
    Department updated = maybeDept.get();
    assertThat(updated.name()).isEqualTo("Engineering");
    assertThat(updated.staff())
        .containsExactly(new Employee("Alice", 80000), new Employee("Bob", 90000));
    assertThat(updated.budgetInCents()).isEqualTo(550000);
  }
}
