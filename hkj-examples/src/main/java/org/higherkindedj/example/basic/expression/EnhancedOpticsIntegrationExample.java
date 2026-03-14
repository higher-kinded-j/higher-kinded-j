// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.expression;

import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.List;
import org.higherkindedj.hkt.Kind;
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
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;

/**
 * Demonstrates Phase 6 Enhanced Optics Integration features in a realistic Department Management
 * System scenario.
 *
 * <p>This example showcases the following ForState and For comprehension capabilities introduced in
 * Phase 6:
 *
 * <ul>
 *   <li>{@code traverseOver} - Effectful traversal directly over state elements
 *   <li>{@code modifyThrough} - Pure modification via traversal (with and without nested lens)
 *   <li>{@code modifyVia} - Modify a field through an Iso conversion
 *   <li>{@code updateVia} - Set a field through an Iso conversion
 *   <li>{@code through(Iso)} - For comprehension type conversion via Iso
 *   <li>Combined workflows using multiple Phase 6 features together
 * </ul>
 */
public class EnhancedOpticsIntegrationExample {

  // --- Domain Model ---

  record Employee(String name, int salaryInCents, String department) {}

  record Department(String name, List<Employee> employees, int budgetInCents) {}

  // --- Lenses ---

  static final Lens<Department, List<Employee>> employeesLens =
      Lens.of(
          Department::employees,
          (dept, emps) -> new Department(dept.name(), emps, dept.budgetInCents()));

  static final Lens<Department, Integer> budgetLens =
      Lens.of(
          Department::budgetInCents,
          (dept, budget) -> new Department(dept.name(), dept.employees(), budget));

  static final Lens<Department, String> deptNameLens =
      Lens.of(
          Department::name,
          (dept, name) -> new Department(name, dept.employees(), dept.budgetInCents()));

  static final Lens<Employee, Integer> salaryLens =
      Lens.of(
          Employee::salaryInCents,
          (emp, salary) -> new Employee(emp.name(), salary, emp.department()));

  static final Lens<Employee, String> empNameLens =
      Lens.of(
          Employee::name, (emp, name) -> new Employee(name, emp.salaryInCents(), emp.department()));

  // --- Traversal ---

  static final Traversal<List<Employee>, Employee> empListTraversal = Traversals.forList();

  // --- Iso: cents <-> dollars ---

  static final Iso<Integer, Double> centsToDollars =
      Iso.of(cents -> cents / 100.0, dollars -> (int) (dollars * 100));

  // --- Sample Data ---

  static Department sampleDepartment() {
    return new Department(
        "Engineering",
        List.of(
            new Employee("Alice", 850000, "Engineering"),
            new Employee("Bob", 720000, "Engineering"),
            new Employee("Charlie", 950000, "Engineering")),
        5000000);
  }

  // --- Main ---

  public static void main(String[] args) {
    System.out.println("=== Phase 6: Enhanced Optics Integration ===");
    System.out.println("=== Department Management System ===\n");

    demonstrateTraverseOver();
    demonstrateModifyThrough();
    demonstrateModifyThroughWithLens();
    demonstrateModifyVia();
    demonstrateUpdateVia();
    demonstrateThrough();
    demonstrateCombinedWorkflow();
  }

  /**
   * Example 1: traverseOver - Effectful traversal over all employees in a list.
   *
   * <p>Uses Maybe monad to validate each employee. Employees with salary below a threshold cause
   * the entire operation to short-circuit to Nothing.
   */
  private static void demonstrateTraverseOver() {
    System.out.println("--- 1. traverseOver: Validate Employees (Maybe Monad) ---");
    MaybeMonad maybeMonad = MaybeMonad.INSTANCE;

    List<Employee> validTeam = sampleDepartment().employees();
    List<Employee> invalidTeam =
        List.of(
            new Employee("Alice", 850000, "Engineering"),
            new Employee("Intern", 0, "Engineering"), // zero salary fails validation
            new Employee("Bob", 720000, "Engineering"));

    // Validation: each employee must have a positive salary
    // If all pass, the list is returned with a "[validated]" prefix on names
    Kind<MaybeKind.Witness, List<Employee>> validResult =
        ForState.withState(maybeMonad, MAYBE.just(validTeam))
            .traverseOver(
                empListTraversal,
                emp -> {
                  if (emp.salaryInCents() <= 0) {
                    return MAYBE.widen(Maybe.nothing());
                  }
                  return MAYBE.just(
                      new Employee(
                          "[validated] " + emp.name(), emp.salaryInCents(), emp.department()));
                })
            .yield();

    Kind<MaybeKind.Witness, List<Employee>> invalidResult =
        ForState.withState(maybeMonad, MAYBE.just(invalidTeam))
            .traverseOver(
                empListTraversal,
                emp -> {
                  if (emp.salaryInCents() <= 0) {
                    return MAYBE.widen(Maybe.nothing());
                  }
                  return MAYBE.just(
                      new Employee(
                          "[validated] " + emp.name(), emp.salaryInCents(), emp.department()));
                })
            .yield();

    Maybe<List<Employee>> valid = MAYBE.narrow(validResult);
    Maybe<List<Employee>> invalid = MAYBE.narrow(invalidResult);

    System.out.println("  Valid team result: " + (valid.isJust() ? "Just" : "Nothing"));
    if (valid.isJust()) {
      valid
          .get()
          .forEach(
              e ->
                  System.out.println(
                      "    - " + e.name() + " ($" + e.salaryInCents() / 100.0 + ")"));
    }
    System.out.println("  Invalid team result: " + (invalid.isJust() ? "Just" : "Nothing"));
    System.out.println();
  }

  /**
   * Example 2: modifyThrough - Pure modification of all employees via traversal.
   *
   * <p>Uses Id monad to give every employee a 10% raise without any effectful computation.
   */
  private static void demonstrateModifyThrough() {
    System.out.println("--- 2. modifyThrough: Give All Employees a 10% Raise ---");
    IdMonad idMonad = IdMonad.instance();

    List<Employee> team = sampleDepartment().employees();

    Kind<IdKind.Witness, List<Employee>> result =
        ForState.withState(idMonad, Id.of(team))
            .modifyThrough(
                empListTraversal,
                emp ->
                    new Employee(emp.name(), (int) (emp.salaryInCents() * 1.10), emp.department()))
            .yield();

    List<Employee> updatedTeam = IdKindHelper.ID.unwrap(result);
    System.out.println("  Before:");
    team.forEach(e -> System.out.println("    - " + e.name() + ": $" + e.salaryInCents() / 100.0));
    System.out.println("  After 10% raise:");
    updatedTeam.forEach(
        e -> System.out.println("    - " + e.name() + ": $" + e.salaryInCents() / 100.0));
    System.out.println();
  }

  /**
   * Example 3: modifyThrough with Lens - Modify a nested field within each traversed element.
   *
   * <p>Uses the three-argument modifyThrough(traversal, lens, modifier) to uppercase all employee
   * names without touching other fields.
   */
  private static void demonstrateModifyThroughWithLens() {
    System.out.println("--- 3. modifyThrough(traversal, lens, modifier): Uppercase Names ---");
    IdMonad idMonad = IdMonad.instance();

    List<Employee> team = sampleDepartment().employees();

    Kind<IdKind.Witness, List<Employee>> result =
        ForState.withState(idMonad, Id.of(team))
            .modifyThrough(empListTraversal, empNameLens, String::toUpperCase)
            .yield();

    List<Employee> updatedTeam = IdKindHelper.ID.unwrap(result);
    System.out.println("  Original names:");
    team.forEach(e -> System.out.println("    - " + e.name()));
    System.out.println("  Uppercased names:");
    updatedTeam.forEach(e -> System.out.println("    - " + e.name()));
    System.out.println();
  }

  /**
   * Example 4: modifyVia - Modify budget through the cents-to-dollars Iso.
   *
   * <p>Applies a 10% budget increase by working in dollar amounts rather than cents, using the Iso
   * to handle conversion transparently.
   */
  private static void demonstrateModifyVia() {
    System.out.println("--- 4. modifyVia: Increase Budget by 10% (via Dollars) ---");
    IdMonad idMonad = IdMonad.instance();

    Department dept = sampleDepartment();

    Kind<IdKind.Witness, Department> result =
        ForState.withState(idMonad, Id.of(dept))
            .modifyVia(budgetLens, centsToDollars, dollars -> dollars * 1.10)
            .yield();

    Department updated = IdKindHelper.ID.unwrap(result);
    System.out.println("  Budget before: $" + centsToDollars.get(dept.budgetInCents()));
    System.out.println("  Budget after (+10%): $" + centsToDollars.get(updated.budgetInCents()));
    System.out.println();
  }

  /**
   * Example 5: updateVia - Set budget to a specific dollar amount through the Iso.
   *
   * <p>Sets the budget to exactly $75,000.00 by providing the dollar value and letting the Iso
   * convert it to cents for storage.
   */
  private static void demonstrateUpdateVia() {
    System.out.println("--- 5. updateVia: Set Budget to $75,000 (via Dollars) ---");
    IdMonad idMonad = IdMonad.instance();

    Department dept = sampleDepartment();

    Kind<IdKind.Witness, Department> result =
        ForState.withState(idMonad, Id.of(dept))
            .updateVia(budgetLens, centsToDollars, 75000.0)
            .yield();

    Department updated = IdKindHelper.ID.unwrap(result);
    System.out.println("  Budget before: $" + centsToDollars.get(dept.budgetInCents()));
    System.out.println("  Budget after: $" + centsToDollars.get(updated.budgetInCents()));
    System.out.println("  Stored as cents: " + updated.budgetInCents());
    System.out.println();
  }

  /**
   * Example 6: For.through(Iso) - Type conversion in a For comprehension.
   *
   * <p>Uses the For comprehension's through() method to convert a budget in cents to dollars,
   * keeping both representations available for the yield.
   */
  private static void demonstrateThrough() {
    System.out.println("--- 6. For.through(Iso): Budget Conversion in Comprehension ---");
    IdMonad idMonad = IdMonad.instance();

    int budgetInCents = sampleDepartment().budgetInCents();

    Kind<IdKind.Witness, String> result =
        For.from(idMonad, Id.of(budgetInCents))
            .through(centsToDollars)
            .yield((cents, dollars) -> String.format("Budget: %d cents = $%.2f", cents, dollars));

    String output = IdKindHelper.ID.unwrap(result);
    System.out.println("  " + output);
    System.out.println();
  }

  /**
   * Example 7: Combined workflow using multiple Phase 6 features.
   *
   * <p>Demonstrates a realistic scenario: rename the department, give employees a raise using
   * modifyThrough, increase the budget via modifyVia, and produce a summary report.
   */
  private static void demonstrateCombinedWorkflow() {
    System.out.println("--- 7. Combined Workflow: Full Department Update ---");
    IdMonad idMonad = IdMonad.instance();

    Department dept = sampleDepartment();

    Kind<IdKind.Witness, String> result =
        ForState.withState(idMonad, Id.of(dept))
            // Step 1: Rename department
            .update(deptNameLens, "Platform Engineering")
            // Step 2: Give all employees a 15% raise (traverse + lens + modifier)
            .traverse(
                employeesLens,
                empListTraversal,
                emp ->
                    Id.of(
                        new Employee(
                            emp.name(),
                            (int) (emp.salaryInCents() * 1.15),
                            "Platform Engineering")))
            // Step 3: Increase budget by 20% working in dollars via Iso
            .modifyVia(budgetLens, centsToDollars, dollars -> dollars * 1.20)
            // Step 4: Produce summary
            .yield(
                d -> {
                  double totalSalaries =
                      d.employees().stream().mapToInt(Employee::salaryInCents).sum() / 100.0;
                  double budget = centsToDollars.get(d.budgetInCents());
                  StringBuilder sb = new StringBuilder();
                  sb.append("Department: ").append(d.name()).append("\n");
                  sb.append("  Employees:\n");
                  d.employees()
                      .forEach(
                          e ->
                              sb.append("    - ")
                                  .append(e.name())
                                  .append(" ($")
                                  .append(String.format("%.2f", e.salaryInCents() / 100.0))
                                  .append(", ")
                                  .append(e.department())
                                  .append(")\n"));
                  sb.append(String.format("  Total salaries: $%.2f\n", totalSalaries));
                  sb.append(String.format("  Budget: $%.2f", budget));
                  return sb.toString();
                });

    String report = IdKindHelper.ID.unwrap(result);
    System.out.println(
        "  Original: " + dept.name() + " with budget $" + centsToDollars.get(dept.budgetInCents()));
    System.out.println("  Updated:");
    System.out.println("  " + report);
    System.out.println();
  }
}
