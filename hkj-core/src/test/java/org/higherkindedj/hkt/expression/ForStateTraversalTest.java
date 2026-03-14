// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.List;
import org.higherkindedj.hkt.Kind;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the Phase 6 traversal and optic-composition features of {@link ForState}: {@code
 * traverseOver}, {@code modifyThrough} (both overloads), {@code modifyVia}, and {@code updateVia}.
 */
@DisplayName("ForState Traversal and Optic Composition Tests")
class ForStateTraversalTest {

  // --- Test Data Classes ---

  record Employee(String name, int salary) {}

  record Department(String name, List<Employee> staff, int budget) {}

  // --- Common Test Fixtures ---

  private IdMonad idMonad;
  private MaybeMonad maybeMonad;

  private Lens<Department, List<Employee>> staffLens;
  private Lens<Department, Integer> budgetLens;
  private Lens<Department, String> deptNameLens;
  private Lens<Employee, Integer> salaryLens;
  private Lens<Employee, String> empNameLens;

  private Traversal<List<Employee>, Employee> employeesTraversal;

  // Budget stored in cents, viewed as dollars
  private Iso<Integer, Double> centsToDollarsIso;

  private Department engineering;

  @BeforeEach
  void setUp() {
    idMonad = IdMonad.instance();
    maybeMonad = MaybeMonad.INSTANCE;

    staffLens = Lens.of(Department::staff, (d, s) -> new Department(d.name(), s, d.budget()));
    budgetLens = Lens.of(Department::budget, (d, b) -> new Department(d.name(), d.staff(), b));
    deptNameLens = Lens.of(Department::name, (d, n) -> new Department(n, d.staff(), d.budget()));
    salaryLens = Lens.of(Employee::salary, (e, s) -> new Employee(e.name(), s));
    empNameLens = Lens.of(Employee::name, (e, n) -> new Employee(n, e.salary()));

    employeesTraversal = Traversals.forList();

    centsToDollarsIso = Iso.of(c -> c / 100.0, d -> (int) (d * 100));

    engineering =
        new Department(
            "Engineering", List.of(new Employee("Alice", 5000), new Employee("Bob", 6000)), 100000);
  }

  // --- traverseOver Tests ---

  @Nested
  @DisplayName("traverseOver Tests")
  class TraverseOverTests {

    @Test
    @DisplayName("traverseOver with Id monad applies effectful function to each element")
    void traverseOverWithIdMonad() {
      // Give each employee a 10% raise via the identity effect
      Kind<IdKind.Witness, List<Employee>> result =
          ForState.withState(
                  idMonad, Id.of(List.of(new Employee("Alice", 1000), new Employee("Bob", 2000))))
              .traverseOver(
                  employeesTraversal, e -> Id.of(new Employee(e.name(), (int) (e.salary() * 1.1))))
              .yield();

      List<Employee> employees = IdKindHelper.ID.unwrap(result);
      assertThat(employees).containsExactly(new Employee("Alice", 1100), new Employee("Bob", 2200));
    }

    @Test
    @DisplayName("traverseOver with Maybe monad succeeds when all elements pass")
    void traverseOverWithMaybe_allSucceed() {
      // Validate that all salaries are positive; wrap each in Just
      Kind<MaybeKind.Witness, List<Employee>> result =
          ForState.withState(
                  maybeMonad,
                  MAYBE.just(List.of(new Employee("Alice", 1000), new Employee("Bob", 2000))))
              .traverseOver(
                  employeesTraversal, e -> e.salary() > 0 ? MAYBE.just(e) : MAYBE.nothing())
              .yield();

      assertThat(MAYBE.narrow(result))
          .isEqualTo(Maybe.just(List.of(new Employee("Alice", 1000), new Employee("Bob", 2000))));
    }

    @Test
    @DisplayName("traverseOver with Maybe monad short-circuits when one element fails")
    void traverseOverWithMaybe_oneFailsShortCircuits() {
      // Reject employees with salary below 1500
      Kind<MaybeKind.Witness, List<Employee>> result =
          ForState.withState(
                  maybeMonad,
                  MAYBE.just(List.of(new Employee("Alice", 1000), new Employee("Bob", 2000))))
              .traverseOver(
                  employeesTraversal, e -> e.salary() >= 1500 ? MAYBE.just(e) : MAYBE.nothing())
              .yield();

      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("traverseOver throws NullPointerException when traversal is null")
    void traverseOverNullTraversalThrows() {
      assertThatThrownBy(
              () ->
                  ForState.withState(idMonad, Id.of(List.of(new Employee("Alice", 100))))
                      .traverseOver(null, e -> Id.of(e)))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("traversal");
    }

    @Test
    @DisplayName("traverseOver throws NullPointerException when function is null")
    void traverseOverNullFunctionThrows() {
      assertThatThrownBy(
              () ->
                  ForState.withState(idMonad, Id.of(List.of(new Employee("Alice", 100))))
                      .traverseOver(employeesTraversal, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("function");
    }

    @Test
    @DisplayName("traverseOver on FilterableSteps preserves filterability")
    void traverseOverPreservesFilterability() {
      // Using MaybeMonad returns FilterableSteps; chaining traverseOver then when() must compile
      Kind<MaybeKind.Witness, List<Employee>> result =
          ForState.withState(
                  maybeMonad,
                  MAYBE.just(List.of(new Employee("Alice", 1000), new Employee("Bob", 2000))))
              .traverseOver(employeesTraversal, e -> MAYBE.just(e))
              .when(employees -> employees.size() == 2)
              .yield();

      assertThat(MAYBE.narrow(result))
          .isEqualTo(Maybe.just(List.of(new Employee("Alice", 1000), new Employee("Bob", 2000))));
    }
  }

  // --- modifyThrough Tests ---

  @Nested
  @DisplayName("modifyThrough Tests")
  class ModifyThroughTests {

    @Test
    @DisplayName("modifyThrough applies pure modification to all focused elements")
    void modifyThroughPureModification() {
      // Uppercase all employee names
      Kind<IdKind.Witness, List<Employee>> result =
          ForState.withState(
                  idMonad, Id.of(List.of(new Employee("Alice", 100), new Employee("Bob", 200))))
              .modifyThrough(
                  employeesTraversal, e -> new Employee(e.name().toUpperCase(), e.salary()))
              .yield();

      List<Employee> employees = IdKindHelper.ID.unwrap(result);
      assertThat(employees).containsExactly(new Employee("ALICE", 100), new Employee("BOB", 200));
    }

    @Test
    @DisplayName("modifyThrough three-arg composes traversal and lens to modify nested field")
    void modifyThroughComposedWithLens() {
      // Increase salary by 50 for each employee via the three-arg overload
      Kind<IdKind.Witness, List<Employee>> result =
          ForState.withState(
                  idMonad, Id.of(List.of(new Employee("Alice", 100), new Employee("Bob", 200))))
              .modifyThrough(employeesTraversal, salaryLens, s -> s + 50)
              .yield();

      List<Employee> employees = IdKindHelper.ID.unwrap(result);
      assertThat(employees).containsExactly(new Employee("Alice", 150), new Employee("Bob", 250));
    }

    @Test
    @DisplayName("modifyThrough on an empty collection produces no changes")
    void modifyThroughEmptyTraversal() {
      Kind<IdKind.Witness, List<Employee>> result =
          ForState.withState(idMonad, Id.of(List.<Employee>of()))
              .modifyThrough(employeesTraversal, e -> new Employee("CHANGED", 999))
              .yield();

      List<Employee> employees = IdKindHelper.ID.unwrap(result);
      assertThat(employees).isEmpty();
    }

    @Test
    @DisplayName("modifyThrough two-arg throws NullPointerException when traversal is null")
    void modifyThroughNullTraversalThrows() {
      assertThatThrownBy(
              () ->
                  ForState.withState(idMonad, Id.of(List.of(new Employee("Alice", 100))))
                      .modifyThrough(null, e -> e))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("traversal");
    }

    @Test
    @DisplayName("modifyThrough two-arg throws NullPointerException when modifier is null")
    void modifyThroughNullModifierThrows() {
      assertThatThrownBy(
              () ->
                  ForState.withState(idMonad, Id.of(List.of(new Employee("Alice", 100))))
                      .modifyThrough(employeesTraversal, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("modifier");
    }

    @Test
    @DisplayName("modifyThrough three-arg throws NullPointerException when traversal is null")
    void modifyThroughThreeArgNullTraversalThrows() {
      assertThatThrownBy(
              () ->
                  ForState.withState(idMonad, Id.of(List.of(new Employee("Alice", 100))))
                      .modifyThrough(null, salaryLens, s -> s + 1))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("traversal");
    }

    @Test
    @DisplayName("modifyThrough three-arg throws NullPointerException when lens is null")
    void modifyThroughThreeArgNullLensThrows() {
      assertThatThrownBy(
              () ->
                  ForState.withState(idMonad, Id.of(List.of(new Employee("Alice", 100))))
                      .<Employee, Integer>modifyThrough(employeesTraversal, null, s -> s + 1))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("lens");
    }

    @Test
    @DisplayName("modifyThrough three-arg throws NullPointerException when modifier is null")
    void modifyThroughThreeArgNullModifierThrows() {
      assertThatThrownBy(
              () ->
                  ForState.withState(idMonad, Id.of(List.of(new Employee("Alice", 100))))
                      .<Employee, Integer>modifyThrough(employeesTraversal, salaryLens, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("modifier");
    }

    @Test
    @DisplayName("modifyThrough on FilterableSteps preserves filterability")
    void modifyThroughPreservesFilterability() {
      // Chain modifyThrough then when() to verify FilterableSteps is returned
      Kind<MaybeKind.Witness, List<Employee>> result =
          ForState.withState(
                  maybeMonad,
                  MAYBE.just(List.of(new Employee("Alice", 100), new Employee("Bob", 200))))
              .modifyThrough(
                  employeesTraversal, e -> new Employee(e.name().toUpperCase(), e.salary()))
              .when(employees -> employees.stream().allMatch(e -> e.salary() > 0))
              .yield();

      assertThat(MAYBE.narrow(result))
          .isEqualTo(Maybe.just(List.of(new Employee("ALICE", 100), new Employee("BOB", 200))));
    }

    @Test
    @DisplayName("modifyThrough two-arg on FilterableSteps applies pure modification")
    void modifyThroughFilterableAppliesModification() {
      Kind<MaybeKind.Witness, List<Employee>> result =
          ForState.withState(
                  maybeMonad,
                  MAYBE.just(List.of(new Employee("Alice", 100), new Employee("Bob", 200))))
              .modifyThrough(employeesTraversal, e -> new Employee(e.name() + "!", e.salary() * 2))
              .yield();

      assertThat(MAYBE.narrow(result))
          .isEqualTo(Maybe.just(List.of(new Employee("Alice!", 200), new Employee("Bob!", 400))));
    }

    @Test
    @DisplayName("modifyThrough two-arg on FilterableSteps with Nothing propagates Nothing")
    void modifyThroughFilterableWithNothingPropagatesNothing() {
      Kind<MaybeKind.Witness, List<Employee>> result =
          ForState.withState(maybeMonad, MAYBE.<List<Employee>>nothing())
              .modifyThrough(
                  employeesTraversal, e -> new Employee(e.name().toUpperCase(), e.salary()))
              .yield();

      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("modifyThrough two-arg on FilterableSteps with empty collection")
    void modifyThroughFilterableEmptyCollection() {
      Kind<MaybeKind.Witness, List<Employee>> result =
          ForState.withState(maybeMonad, MAYBE.just(List.<Employee>of()))
              .modifyThrough(employeesTraversal, e -> new Employee("CHANGED", 999))
              .yield();

      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.just(List.of()));
    }

    @Test
    @DisplayName("modifyThrough two-arg on FilterableSteps throws when traversal is null")
    void modifyThroughFilterableNullTraversalThrows() {
      assertThatThrownBy(
              () ->
                  ForState.withState(maybeMonad, MAYBE.just(List.of(new Employee("Alice", 100))))
                      .modifyThrough(null, e -> e))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("traversal");
    }

    @Test
    @DisplayName("modifyThrough two-arg on FilterableSteps throws when modifier is null")
    void modifyThroughFilterableNullModifierThrows() {
      assertThatThrownBy(
              () ->
                  ForState.withState(maybeMonad, MAYBE.just(List.of(new Employee("Alice", 100))))
                      .modifyThrough(employeesTraversal, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("modifier");
    }

    @Test
    @DisplayName("modifyThrough three-arg on FilterableSteps composes traversal and lens")
    void modifyThroughFilterableThreeArgComposesTraversalAndLens() {
      Kind<MaybeKind.Witness, List<Employee>> result =
          ForState.withState(
                  maybeMonad,
                  MAYBE.just(List.of(new Employee("Alice", 100), new Employee("Bob", 200))))
              .modifyThrough(employeesTraversal, salaryLens, s -> s + 50)
              .yield();

      assertThat(MAYBE.narrow(result))
          .isEqualTo(Maybe.just(List.of(new Employee("Alice", 150), new Employee("Bob", 250))));
    }

    @Test
    @DisplayName("modifyThrough three-arg on FilterableSteps preserves filterability")
    void modifyThroughFilterableThreeArgPreservesFilterability() {
      Kind<MaybeKind.Witness, List<Employee>> result =
          ForState.withState(
                  maybeMonad,
                  MAYBE.just(List.of(new Employee("Alice", 100), new Employee("Bob", 200))))
              .modifyThrough(employeesTraversal, salaryLens, s -> s + 50)
              .when(employees -> employees.stream().allMatch(e -> e.salary() >= 150))
              .yield();

      assertThat(MAYBE.narrow(result))
          .isEqualTo(Maybe.just(List.of(new Employee("Alice", 150), new Employee("Bob", 250))));
    }

    @Test
    @DisplayName("modifyThrough three-arg on FilterableSteps with Nothing propagates Nothing")
    void modifyThroughFilterableThreeArgWithNothingPropagatesNothing() {
      Kind<MaybeKind.Witness, List<Employee>> result =
          ForState.withState(maybeMonad, MAYBE.<List<Employee>>nothing())
              .modifyThrough(employeesTraversal, salaryLens, s -> s + 50)
              .yield();

      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("modifyThrough three-arg on FilterableSteps throws when traversal is null")
    void modifyThroughFilterableThreeArgNullTraversalThrows() {
      assertThatThrownBy(
              () ->
                  ForState.withState(maybeMonad, MAYBE.just(List.of(new Employee("Alice", 100))))
                      .modifyThrough(null, salaryLens, s -> s + 1))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("traversal");
    }

    @Test
    @DisplayName("modifyThrough three-arg on FilterableSteps throws when lens is null")
    void modifyThroughFilterableThreeArgNullLensThrows() {
      assertThatThrownBy(
              () ->
                  ForState.withState(maybeMonad, MAYBE.just(List.of(new Employee("Alice", 100))))
                      .<Employee, Integer>modifyThrough(employeesTraversal, null, s -> s + 1))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("lens");
    }

    @Test
    @DisplayName("modifyThrough three-arg on FilterableSteps throws when modifier is null")
    void modifyThroughFilterableThreeArgNullModifierThrows() {
      assertThatThrownBy(
              () ->
                  ForState.withState(maybeMonad, MAYBE.just(List.of(new Employee("Alice", 100))))
                      .<Employee, Integer>modifyThrough(employeesTraversal, salaryLens, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("modifier");
    }
  }

  // --- modifyVia Tests ---

  @Nested
  @DisplayName("modifyVia Tests")
  class ModifyViaTests {

    @Test
    @DisplayName("modifyVia applies modification through an Iso conversion")
    void modifyViaBasic() {
      // Budget is 10000 cents. Convert to dollars (100.0), add 50.0, convert back to cents.
      Kind<IdKind.Witness, Department> result =
          ForState.withState(idMonad, Id.of(new Department("Sales", List.of(), 10000)))
              .modifyVia(budgetLens, centsToDollarsIso, dollars -> dollars + 50.0)
              .yield();

      Department dept = IdKindHelper.ID.unwrap(result);
      assertThat(dept.budget()).isEqualTo(15000);
    }

    @Test
    @DisplayName("modifyVia round-trip preserves original value when modifier is identity")
    void modifyViaRoundTrip() {
      // Apply identity modifier through the Iso; budget should remain unchanged
      Kind<IdKind.Witness, Department> result =
          ForState.withState(idMonad, Id.of(engineering))
              .modifyVia(budgetLens, centsToDollarsIso, d -> d)
              .yield();

      Department dept = IdKindHelper.ID.unwrap(result);
      assertThat(dept.budget()).isEqualTo(engineering.budget());
    }

    @Test
    @DisplayName("modifyVia throws NullPointerException when lens is null")
    void modifyViaNullLensThrows() {
      assertThatThrownBy(
              () ->
                  ForState.withState(idMonad, Id.of(engineering))
                      .modifyVia(null, centsToDollarsIso, d -> d + 1.0))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("lens");
    }

    @Test
    @DisplayName("modifyVia throws NullPointerException when iso is null")
    void modifyViaNullIsoThrows() {
      assertThatThrownBy(
              () ->
                  ForState.withState(idMonad, Id.of(engineering))
                      .<Integer, Double>modifyVia(budgetLens, null, d -> d + 1.0))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("iso");
    }

    @Test
    @DisplayName("modifyVia throws NullPointerException when modifier is null")
    void modifyViaNullModifierThrows() {
      assertThatThrownBy(
              () ->
                  ForState.withState(idMonad, Id.of(engineering))
                      .<Integer, Double>modifyVia(budgetLens, centsToDollarsIso, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("modifier");
    }

    @Test
    @DisplayName("modifyVia on FilterableSteps preserves filterability")
    void modifyViaPreservesFilterability() {
      // Use MaybeMonad, chain modifyVia then when() to verify FilterableSteps is returned
      Kind<MaybeKind.Witness, Department> result =
          ForState.withState(maybeMonad, MAYBE.just(new Department("Sales", List.of(), 10000)))
              .modifyVia(budgetLens, centsToDollarsIso, dollars -> dollars + 50.0)
              .when(d -> d.budget() > 0)
              .yield();

      assertThat(MAYBE.narrow(result))
          .isEqualTo(Maybe.just(new Department("Sales", List.of(), 15000)));
    }
  }

  // --- updateVia Tests ---

  @Nested
  @DisplayName("updateVia Tests")
  class UpdateViaTests {

    @Test
    @DisplayName("updateVia sets a field through an Iso conversion")
    void updateViaBasic() {
      // Set budget to 250.0 dollars, which converts to 25000 cents
      Kind<IdKind.Witness, Department> result =
          ForState.withState(idMonad, Id.of(engineering))
              .updateVia(budgetLens, centsToDollarsIso, 250.0)
              .yield();

      Department dept = IdKindHelper.ID.unwrap(result);
      assertThat(dept.budget()).isEqualTo(25000);
    }

    @Test
    @DisplayName("updateVia throws NullPointerException when lens is null")
    void updateViaNullLensThrows() {
      assertThatThrownBy(
              () ->
                  ForState.withState(idMonad, Id.of(engineering))
                      .updateVia(null, centsToDollarsIso, 100.0))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("lens");
    }

    @Test
    @DisplayName("updateVia throws NullPointerException when iso is null")
    void updateViaNullIsoThrows() {
      assertThatThrownBy(
              () ->
                  ForState.withState(idMonad, Id.of(engineering))
                      .updateVia(budgetLens, null, 100.0))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("iso");
    }

    @Test
    @DisplayName("updateVia on FilterableSteps preserves filterability")
    void updateViaPreservesFilterability() {
      // Use MaybeMonad, chain updateVia then when() to verify FilterableSteps is returned
      Kind<MaybeKind.Witness, Department> result =
          ForState.withState(maybeMonad, MAYBE.just(engineering))
              .updateVia(budgetLens, centsToDollarsIso, 500.0)
              .when(d -> d.budget() == 50000)
              .yield();

      assertThat(MAYBE.narrow(result))
          .isEqualTo(Maybe.just(new Department(engineering.name(), engineering.staff(), 50000)));
    }
  }

  // --- Combined Workflow Tests ---

  @Nested
  @DisplayName("Combined Workflow Tests")
  class CombinedWorkflowTests {

    @Test
    @DisplayName("traverseOver and modifyThrough can be chained together")
    void combinedTraverseOverAndModifyThrough() {
      // First validate all employees via traverseOver, then uppercase names via modifyThrough
      Kind<IdKind.Witness, List<Employee>> result =
          ForState.withState(
                  idMonad, Id.of(List.of(new Employee("Alice", 1000), new Employee("Bob", 2000))))
              .traverseOver(
                  employeesTraversal, e -> Id.of(new Employee(e.name(), e.salary() + 500)))
              .modifyThrough(employeesTraversal, empNameLens, n -> n.toUpperCase())
              .yield();

      List<Employee> employees = IdKindHelper.ID.unwrap(result);
      assertThat(employees).containsExactly(new Employee("ALICE", 1500), new Employee("BOB", 2500));
    }

    @Test
    @DisplayName("traverseOver, modifyVia, and existing modify can be mixed in a workflow")
    void combinedWithExistingForStateFeatures() {
      // Complex workflow on a Department:
      // 1. Give each employee a raise via traverse (lens + traversal)
      // 2. Convert budget from cents to dollars, apply a discount, convert back via modifyVia
      // 3. Update department name via modify
      Kind<IdKind.Witness, Department> result =
          ForState.withState(idMonad, Id.of(engineering))
              .traverse(
                  staffLens,
                  employeesTraversal,
                  e -> Id.of(new Employee(e.name(), e.salary() + 1000)))
              .modifyVia(budgetLens, centsToDollarsIso, dollars -> dollars * 0.9)
              .modify(deptNameLens, name -> name + " (Updated)")
              .yield();

      Department dept = IdKindHelper.ID.unwrap(result);
      assertThat(dept.name()).isEqualTo("Engineering (Updated)");
      assertThat(dept.staff())
          .containsExactly(new Employee("Alice", 6000), new Employee("Bob", 7000));
      // 100000 cents = 1000.0 dollars * 0.9 = 900.0 dollars = 90000 cents
      assertThat(dept.budget()).isEqualTo(90000);
    }
  }
}
