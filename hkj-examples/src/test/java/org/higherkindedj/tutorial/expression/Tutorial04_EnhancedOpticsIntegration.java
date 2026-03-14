// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 04: Enhanced Optics Integration - Traversals, Isos, and Combined Workflows
 *
 * <p>This tutorial explores the advanced optics integration features of {@code ForState} and {@code
 * For}, focusing on bulk operations via traversals, type-safe conversions via isomorphisms, and
 * real-world combined workflows.
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>{@code traverseOver}: apply effectful functions to every element in a state collection
 *   <li>{@code modifyThrough}: pure bulk modifications via traversals (with optional lens
 *       composition)
 *   <li>{@code through(Iso)}: convert values through isomorphisms in {@code For} comprehensions
 *   <li>{@code modifyVia}: modify a lens-focused field through an Iso conversion
 *   <li>{@code updateVia}: set a lens-focused field through an Iso conversion
 *   <li>Combined workflows: chaining traversals, lenses, and isos for real-world scenarios
 * </ul>
 *
 * <p>Prerequisites: Complete Tutorials 01-03 before this one.
 *
 * <p>Estimated time: ~15 minutes.
 *
 * <p>Replace each {@code answerRequired()} call with the correct code to make the tests pass.
 */
public class Tutorial04_EnhancedOpticsIntegration {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

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

  // =========================================================================
  // Exercise 1: Basic traverseOver
  // =========================================================================

  /**
   * Exercise 1: Use {@code ForState.withState} with the Id monad to give every employee in a list a
   * 1000-cent raise using {@code traverseOver}.
   *
   * <p>The state is the entire {@code List<Employee>}, and {@code traverseOver} applies an
   * effectful function to each element via a traversal.
   *
   * <p>Hint: Use {@code traverseOver(Traversals.forList(), e -> Id.of(new Employee(e.name(),
   * e.salaryInCents() + 1000)))}
   */
  @Test
  void exercise1_basicTraverseOver() {
    IdMonad idMonad = IdMonad.instance();
    List<Employee> employees = List.of(new Employee("Alice", 50000), new Employee("Bob", 60000));

    // TODO: Use ForState.withState with idMonad and Id.of(employees).
    //   Chain .traverseOver(Traversals.forList(), ...) to add 1000 to each salary.
    //   Then .yield() to get the result.
    Kind<IdKind.Witness, List<Employee>> result = answerRequired();

    List<Employee> updated = IdKindHelper.ID.unwrap(result);
    assertThat(updated).containsExactly(new Employee("Alice", 51000), new Employee("Bob", 61000));
  }

  // =========================================================================
  // Exercise 2: traverseOver with Maybe (short-circuit)
  // =========================================================================

  /**
   * Exercise 2: Use {@code ForState.withState} with the Maybe monad to validate that every
   * employee's salary exceeds 40000 cents. If all salaries are valid, return {@code
   * Just(employees)}; if any salary fails, the entire result becomes {@code Nothing}.
   *
   * <p>Hint: Use {@code traverseOver(Traversals.forList(), e -> e.salaryInCents() > 40000 ?
   * MAYBE.just(e) : MAYBE.nothing())}
   */
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

    // TODO: Build a ForState workflow that validates all salaries > 40000.
    //   For validEmployees, the result should be Just(employees).
    //   For mixedEmployees, the result should be Nothing (Charlie's salary is too low).
    Kind<MaybeKind.Witness, List<Employee>> validResult = answerRequired();
    Kind<MaybeKind.Witness, List<Employee>> mixedResult = answerRequired();

    assertThat(MAYBE.narrow(validResult)).isEqualTo(Maybe.just(validEmployees));
    assertThat(MAYBE.narrow(mixedResult)).isEqualTo(Maybe.nothing());
  }

  // =========================================================================
  // Exercise 3: Pure modifyThrough
  // =========================================================================

  /**
   * Exercise 3: Use {@code modifyThrough} with a traversal and a pure function to uppercase all
   * employee names in the list.
   *
   * <p>Unlike {@code traverseOver}, {@code modifyThrough} uses a pure (non-effectful) function.
   *
   * <p>Hint: Use {@code modifyThrough(Traversals.forList(), e -> new Employee(
   * e.name().toUpperCase(), e.salaryInCents()))}
   */
  @Test
  void exercise3_pureModifyThrough() {
    IdMonad idMonad = IdMonad.instance();
    List<Employee> employees = List.of(new Employee("Alice", 50000), new Employee("Bob", 60000));

    // TODO: Use ForState.withState with idMonad and Id.of(employees).
    //   Chain .modifyThrough(Traversals.forList(), ...) to uppercase each name.
    //   Then .yield() to get the result.
    Kind<IdKind.Witness, List<Employee>> result = answerRequired();

    List<Employee> updated = IdKindHelper.ID.unwrap(result);
    assertThat(updated).containsExactly(new Employee("ALICE", 50000), new Employee("BOB", 60000));
  }

  // =========================================================================
  // Exercise 4: modifyThrough with composed Lens
  // =========================================================================

  /**
   * Exercise 4: Use the overloaded {@code modifyThrough} that takes a traversal and a lens to
   * double all employee salaries. The lens focuses on the salary field within each employee.
   *
   * <p>Hint: Use {@code modifyThrough(Traversals.forList(), salaryLens, s -> s * 2)}
   */
  @Test
  void exercise4_modifyThroughWithLens() {
    IdMonad idMonad = IdMonad.instance();
    List<Employee> employees = List.of(new Employee("Alice", 50000), new Employee("Bob", 60000));

    // TODO: Use ForState.withState with idMonad and Id.of(employees).
    //   Chain .modifyThrough(Traversals.forList(), salaryLens, ...) to double each salary.
    //   Then .yield() to get the result.
    Kind<IdKind.Witness, List<Employee>> result = answerRequired();

    List<Employee> updated = IdKindHelper.ID.unwrap(result);
    assertThat(updated).containsExactly(new Employee("Alice", 100000), new Employee("Bob", 120000));
  }

  // =========================================================================
  // Exercise 5: through(Iso) basics
  // =========================================================================

  /**
   * Exercise 5: Use {@code For.from} with the Id monad to convert a Celsius value to Fahrenheit
   * using {@code through(Iso)}, then yield a formatted string showing both values.
   *
   * <p>The {@code through(Iso)} method on a For comprehension converts the current value and
   * accumulates both the original and the converted value in a tuple.
   *
   * <p>Hint: Use {@code For.from(idMonad, Id.of(new Celsius(100.0))).through(celsiusToFahrenheit)
   * .yield((celsius, fahrenheit) -> ...)}
   */
  @Test
  void exercise5_throughIsoBasics() {
    IdMonad idMonad = IdMonad.instance();

    // TODO: Use For.from with Id.of(new Celsius(100.0)).
    //   Chain .through(celsiusToFahrenheit) to convert to Fahrenheit.
    //   Then .yield((celsius, fahrenheit) -> ...) to produce a formatted string.
    Kind<IdKind.Witness, String> result = answerRequired();

    assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("100.0\u00B0C = 212.0\u00B0F");
  }

  // =========================================================================
  // Exercise 6: through(Iso) with FilterableSteps
  // =========================================================================

  /**
   * Exercise 6: Use {@code For.from} with the Maybe monad and {@code through(stringToWrapper)} to
   * wrap a string value. Then use {@code when()} to filter based on the wrapped value's length.
   *
   * <p>When using {@code through(Iso)} on a FilterableSteps1 (from a MonadZero), the result is a
   * FilterableSteps2 which supports {@code when()} guards. The predicate receives a {@code
   * Tuple2<A, B>} where {@code _1()} is the original and {@code _2()} is the converted value.
   *
   * <p>Hint: Use {@code For.from(maybeMonad, MAYBE.just("hello")).through(stringToWrapper) .when(t
   * -> t._2().inner().length() > 3)}
   */
  @Test
  void exercise6_throughIsoWithFilter() {
    MaybeMonad maybeMonad = MaybeMonad.INSTANCE;

    // TODO: Use For.from with MAYBE.just("hello") and through(stringToWrapper).
    //   Add a when() guard that checks if the inner string's length > 3.
    //   Yield the inner string value.
    //   "hello" has length 5, so it should pass the filter.
    Kind<MaybeKind.Witness, String> passResult = answerRequired();

    // TODO: Same workflow but with "hi" (length 2), which should fail the filter.
    Kind<MaybeKind.Witness, String> failResult = answerRequired();

    assertThat(MAYBE.narrow(passResult)).isEqualTo(Maybe.just("hello"));
    assertThat(MAYBE.narrow(failResult)).isEqualTo(Maybe.nothing());
  }

  // =========================================================================
  // Exercise 7: modifyVia with Iso
  // =========================================================================

  /**
   * Exercise 7: Use {@code modifyVia} to add $50 to an employee's salary by converting through the
   * {@code centsToDollars} Iso.
   *
   * <p>The {@code modifyVia(lens, iso, modifier)} method extracts a field via a lens, converts it
   * to the Iso's target type, applies the modifier in that type, converts back, and stores the
   * result.
   *
   * <p>Hint: Use {@code .modifyVia(salaryLens, centsToDollars, d -> d + 50.0)}
   */
  @Test
  void exercise7_modifyViaIso() {
    IdMonad idMonad = IdMonad.instance();
    Employee alice = new Employee("Alice", 50000);

    // TODO: Use ForState.withState with idMonad and Id.of(alice).
    //   Chain .modifyVia(salaryLens, centsToDollars, ...) to add $50.00 to the salary.
    //   Then .yield() to get the result.
    Kind<IdKind.Witness, Employee> result = answerRequired();

    Employee updated = IdKindHelper.ID.unwrap(result);
    assertThat(updated).isEqualTo(new Employee("Alice", 55000));
  }

  // =========================================================================
  // Exercise 8: updateVia with Iso
  // =========================================================================

  /**
   * Exercise 8: Use {@code updateVia} to set an employee's salary to exactly $750.00 by converting
   * through the {@code centsToDollars} Iso.
   *
   * <p>The {@code updateVia(lens, iso, value)} method converts the provided value from the Iso's
   * target type back to the field type using {@code reverseGet}, then stores it via the lens.
   *
   * <p>Hint: Use {@code .updateVia(salaryLens, centsToDollars, 750.0)}
   */
  @Test
  void exercise8_updateViaIso() {
    IdMonad idMonad = IdMonad.instance();
    Employee alice = new Employee("Alice", 50000);

    // TODO: Use ForState.withState with idMonad and Id.of(alice).
    //   Chain .updateVia(salaryLens, centsToDollars, 750.0) to set the salary to $750.
    //   Then .yield() to get the result.
    Kind<IdKind.Witness, Employee> result = answerRequired();

    Employee updated = IdKindHelper.ID.unwrap(result);
    assertThat(updated).isEqualTo(new Employee("Alice", 75000));
  }

  // =========================================================================
  // Exercise 9: Combined workflow
  // =========================================================================

  /**
   * Exercise 9: Build a combined workflow that:
   *
   * <ol>
   *   <li>Validates all employee salaries are positive (using Maybe + traverseOver)
   *   <li>Uppercases all employee names (using modifyThrough)
   *   <li>Adds a 5000-cent bonus to all salaries (using modifyThrough with salaryLens)
   * </ol>
   *
   * <p>Hint: Chain {@code traverseOver}, then {@code modifyThrough}, then another {@code
   * modifyThrough} with the salary lens.
   */
  @Test
  void exercise9_combinedWorkflow() {
    MaybeMonad maybeMonad = MaybeMonad.INSTANCE;
    List<Employee> employees = List.of(new Employee("alice", 50000), new Employee("bob", 60000));

    // TODO: Use ForState.withState with maybeMonad and MAYBE.just(employees).
    //   1. traverseOver to validate each salary > 0 (return Just(e) if valid, Nothing if not)
    //   2. modifyThrough to uppercase each name
    //   3. modifyThrough with salaryLens to add 5000 to each salary
    //   Then .yield() to get the result.
    Kind<MaybeKind.Witness, List<Employee>> result = answerRequired();

    assertThat(MAYBE.narrow(result))
        .isEqualTo(Maybe.just(List.of(new Employee("ALICE", 55000), new Employee("BOB", 65000))));
  }

  // =========================================================================
  // Exercise 10: Real-world department payroll
  // =========================================================================

  record Department(String name, List<Employee> staff, int budgetInCents) {}

  static final Lens<Department, List<Employee>> staffLens =
      Lens.of(Department::staff, (d, s) -> new Department(d.name(), s, d.budgetInCents()));

  static final Lens<Department, Integer> budgetLens =
      Lens.of(Department::budgetInCents, (d, b) -> new Department(d.name(), d.staff(), b));

  /**
   * Exercise 10: Build a real-world department payroll workflow that:
   *
   * <ol>
   *   <li>Validates all staff salaries are positive using {@code traverse(staffLens,
   *       Traversals.forList(), ...)} with Maybe
   *   <li>Increases the department budget by 10% using {@code modifyVia(budgetLens, centsToDollars,
   *       ...)}
   * </ol>
   *
   * <p>Hint: Use {@code traverse(staffLens, Traversals.forList(), e -> e.salaryInCents() > 0 ?
   * MAYBE.just(e) : MAYBE.nothing())} for validation, then {@code modifyVia(budgetLens,
   * centsToDollars, d -> d * 1.1)} for the budget increase.
   */
  @Test
  void exercise10_departmentPayroll() {
    MaybeMonad maybeMonad = MaybeMonad.INSTANCE;
    Department engineering =
        new Department(
            "Engineering",
            List.of(new Employee("Alice", 80000), new Employee("Bob", 90000)),
            500000);

    // TODO: Use ForState.withState with maybeMonad and MAYBE.just(engineering).
    //   1. traverse(staffLens, Traversals.forList(), ...) to validate salaries > 0
    //   2. modifyVia(budgetLens, centsToDollars, ...) to increase budget by 10%
    //   Then .yield() to get the result.
    Kind<MaybeKind.Witness, Department> result = answerRequired();

    Maybe<Department> maybeDept = MAYBE.narrow(result);
    assertThat(maybeDept.isJust()).isTrue();
    Department updated = maybeDept.get();
    assertThat(updated.name()).isEqualTo("Engineering");
    assertThat(updated.staff())
        .containsExactly(new Employee("Alice", 80000), new Employee("Bob", 90000));
    assertThat(updated.budgetInCents()).isEqualTo(550000);
  }

  // =========================================================================
  // Congratulations!
  // =========================================================================
  //
  // Well done! You have completed Tutorial 04: Enhanced Optics Integration.
  //
  // You have learnt how to:
  //   - Use traverseOver for effectful bulk operations on state collections
  //   - Use modifyThrough for pure bulk modifications (with and without lens composition)
  //   - Convert values through Isos in For comprehensions using through()
  //   - Filter converted values using when() on FilterableSteps
  //   - Modify and set state fields through Iso conversions using modifyVia and updateVia
  //   - Combine traversals, lenses, and isos in real-world workflows
  //   - Use traverse with a lens to target nested collections within state
  //
  // Next steps: explore zoom/endZoom for narrowing state scope and
  // ForPath for parallel independent computations.
  //
  // =========================================================================
}
