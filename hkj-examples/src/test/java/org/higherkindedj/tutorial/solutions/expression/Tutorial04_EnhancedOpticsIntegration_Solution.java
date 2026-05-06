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
 * Solution for Tutorial04 EnhancedOpticsIntegration — teaching-solution format.
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

  /**
   * Why this is idiomatic: {@code traverseOver(traversal, fn)} runs an effectful function over
   * every element a traversal exposes. With {@code IdMonad} the effect is trivial; the shape
   * generalises directly to {@code Maybe}, {@code Either}, or {@code VTask}.
   *
   * <p>Alternative: {@code list.stream().map(...).toList()}. Same answer for {@code Id}; loses the
   * ability to short-circuit or collect errors when the function gains a real effect channel later.
   *
   * <p>Common wrong attempt: mutate the {@code Employee} records in place. Records are immutable;
   * the lens-driven rebuild is what produces a new list with the new salaries.
   */
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

  /**
   * Why this is idiomatic: with {@code MaybeMonad} a single rejected element collapses the whole
   * list — the traversal preserves the monad's failure semantics. The valid list comes out
   * unchanged; the mixed list comes out {@code Nothing}.
   *
   * <p>Alternative: filter rejected employees and emit the remaining ones. Different intent —
   * useful when partial results are acceptable; this {@code traverseOver} answer says "all or
   * nothing".
   *
   * <p>Common wrong attempt: bake the validation predicate into a stream filter and call {@code
   * traverseOver} on the filtered list. The list is now shorter, so the caller cannot tell whether
   * validation rejected anyone — the typed {@code Maybe} makes the failure visible.
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

  /**
   * Why this is idiomatic: {@code modifyThrough(traversal, fn)} is the pure-function variant of
   * {@code traverseOver} — no monad, no failure. Each element is rebuilt by the supplied function
   * and the traversal handles the list-walking.
   *
   * <p>Alternative: {@code traverseOver(traversal, e -> Id.of(fn(e)))}. Equivalent; {@code
   * modifyThrough} is the named, monad-free spelling for purely synchronous rewrites.
   *
   * <p>Common wrong attempt: change the {@code String} in place via reflection or a setter. The
   * {@code Employee} record exposes neither; the lens-driven rebuild is the only way.
   */
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

  /**
   * Why this is idiomatic: the three-argument form composes a traversal with a lens — "for every
   * element, focus on this field, transform it". The function only sees the leaf; the
   * traversal+lens pair handles the navigation.
   *
   * <p>Alternative: write a single lambda that destructures and rebuilds the {@code Employee} with
   * the doubled salary. Same answer; the lens form names the field and stays stable when more
   * fields are added to {@code Employee}.
   *
   * <p>Common wrong attempt: pass {@code salaryLens.modify(s -> s * 2, e)} as the per-element
   * function. The signature is wrong — {@code modify} on a lens already takes the source as its
   * second argument; {@code modifyThrough} expects only the leaf transformation.
   */
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

  /**
   * Why this is idiomatic: {@code through(iso)} converts the comprehension's binding through the
   * isomorphism and yields a tuple of (original, converted). The {@code yield} lambda sees both
   * ends of the conversion at once.
   *
   * <p>Alternative: capture the {@code Celsius} value, call the conversion explicitly, then format.
   * Same numbers; the comprehension keeps the optic-driven conversion declarative.
   *
   * <p>Common wrong attempt: invert the {@code Iso} by hand to produce the {@code Fahrenheit}. The
   * isomorphism already supplies both directions; reversing it manually duplicates code the optic
   * owns.
   */
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

  /**
   * Why this is idiomatic: cast {@code MaybeMonad} to {@code MonadZero} so {@code when} has an
   * empty branch, then chain {@code through} with a guard. The wrap stays paired with its unwrap;
   * the predicate runs against the inspected wrapper.
   *
   * <p>Alternative: split the workflow into two comprehensions — one that wraps and one that
   * filters. Same answer; the chained version keeps both decisions in one expression.
   *
   * <p>Common wrong attempt: pass plain {@code MaybeMonad} where {@code MonadZero} is wanted. The
   * compiler may type-check (depending on overloads), but the {@code when} guard has nowhere to
   * fail to without the zero element.
   */
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

  /**
   * Why this is idiomatic: {@code modifyVia(lens, iso, fn)} focuses on a field, converts through
   * the iso, applies the function in the converted units, and reverses the iso to write back. The
   * integer-cents storage and the dollar-denominated arithmetic stay separate.
   *
   * <p>Alternative: read the cents, divide by 100, add 50, multiply by 100, write back. Same
   * answer; the iso captures the round-trip once and the comprehension reuses it.
   *
   * <p>Common wrong attempt: skip the iso and add 5000 directly in cents. Works for this single
   * transformation but spreads the unit conversion through every call site that touches money.
   */
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

  /**
   * Why this is idiomatic: {@code updateVia(lens, iso, value)} writes a constant in the iso's
   * converted units, then reverses the iso to store. The new value is "750 dollars", not "75000
   * cents" — the call site speaks the user's units.
   *
   * <p>Alternative: {@code modifyVia(lens, iso, _ -> 750.0)}. Equivalent runtime; {@code updateVia}
   * is the named "constant write" version that signals there is no dependency on the previous
   * value.
   *
   * <p>Common wrong attempt: write {@code lens.set(75000, alice)} directly. The integer is the
   * storage form; the moment the storage units change (e.g. milli-cents) every literal has to be
   * rediscovered. Keep the iso in the path.
   */
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

  /**
   * Why this is idiomatic: validate first ({@code traverseOver}), normalise names ({@code
   * modifyThrough}), then award a bonus through the salary lens ({@code modifyThrough}+lens). Each
   * step does one job and the comprehension threads the {@code Maybe} through.
   *
   * <p>Alternative: collapse the three transformations into one {@code map} that does everything
   * per element. Equivalent in this example; the staged form keeps each concern inspectable in
   * isolation and short-circuits at the validation step before any reformatting happens.
   *
   * <p>Common wrong attempt: change the step order so the bonus is awarded before validation.
   * Invalid employees would be paid before being rejected — a workflow bug a reviewer would not
   * catch by skimming.
   */
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

  /**
   * Why this is idiomatic: {@code traverse(lens, traversal, fn)} validates the staff list focused
   * by a lens; {@code modifyVia(lens, iso, fn)} bumps the budget through the cents iso. Two named
   * steps, two clear effects, one resulting {@code Department}.
   *
   * <p>Alternative: split into two comprehensions, one per concern. Tidier in production; the
   * single-comprehension form here keeps every transformation visible in one window.
   *
   * <p>Common wrong attempt: validate inside the budget update by mixing the two effects. The
   * combined function becomes a maintenance hazard; keep validation and accounting as distinct
   * steps so each can be tested in isolation.
   */
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
