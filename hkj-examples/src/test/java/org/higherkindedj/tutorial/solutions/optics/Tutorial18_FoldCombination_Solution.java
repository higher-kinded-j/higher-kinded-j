// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Solution for Tutorial18 FoldCombination — teaching-solution format.
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
@DisplayName("Tutorial Solution: Fold Combination")
public class Tutorial18_FoldCombination_Solution {

  // --- Domain models ---

  record Person(String firstName, String lastName, int age) {}

  record Team(String name, Person lead, List<Person> members) {}

  sealed interface Shape permits Circle, Rectangle {}

  record Circle(double radius) implements Shape {}

  record Rectangle(double width, double height) implements Shape {}

  record Config(Optional<String> host, Optional<String> port, String name) {}

  // --- Lenses ---

  static final Lens<Person, String> firstNameLens =
      Lens.of(Person::firstName, (p, n) -> new Person(n, p.lastName(), p.age()));

  static final Lens<Person, String> lastNameLens =
      Lens.of(Person::lastName, (p, n) -> new Person(p.firstName(), n, p.age()));

  static final Lens<Person, Integer> ageLens =
      Lens.of(Person::age, (p, a) -> new Person(p.firstName(), p.lastName(), a));

  static final Lens<Team, Person> teamLeadLens =
      Lens.of(Team::lead, (t, l) -> new Team(t.name(), l, t.members()));

  @Nested
  @DisplayName("Part 1: Basic Fold Combination")
  class BasicCombination {

    /**
     * Why this is idiomatic: {@code fold1.plus(fold2)} produces a fold that yields every focus of
     * both — lens-as-fold combined gives "all the names". The result still has {@code length},
     * {@code getAll}, and the rest of the fold API.
     *
     * <p>Alternative: read each lens separately and concatenate the lists. Same answer; {@code
     * plus} keeps the operation as a single fold for further composition.
     *
     * <p>Common wrong attempt: assume {@code plus} interleaves the focuses. It concatenates — left
     * fold's focuses first, right fold's after.
     */
    @Test
    @DisplayName("Exercise 1: Combine two field folds with plus")
    void exercise1_combineFieldFolds() {
      Person person = new Person("Alice", "Smith", 30);

      // SOLUTION: Use asFold() on each lens and combine with plus()
      Fold<Person, String> allNames = firstNameLens.asFold().plus(lastNameLens.asFold());

      assertThat(allNames.getAll(person)).containsExactly("Alice", "Smith");
      assertThat(allNames.length(person)).isEqualTo(2);
    }

    /**
     * Why this is idiomatic: {@code Fold.empty()} is the monoid identity for {@code plus}.
     * Combining anything with {@code empty} returns the original — useful as a starting accumulator
     * in folds-of-folds.
     *
     * <p>Alternative: special-case the first iteration to skip {@code empty}. Equivalent; the
     * identity element is the cleaner answer.
     *
     * <p>Common wrong attempt: assume {@code Fold.empty()} returns {@code null}. It is a proper
     * fold that yields zero focuses on any source.
     */
    @Test
    @DisplayName("Exercise 2: Identity with Fold.empty()")
    void exercise2_identityWithEmpty() {
      Person person = new Person("Alice", "Smith", 30);
      Fold<Person, String> nameFold = firstNameLens.asFold();

      // SOLUTION: Fold.empty() is the identity for plus()
      Fold<Person, String> combined = nameFold.plus(Fold.empty());

      assertThat(combined.getAll(person)).isEqualTo(nameFold.getAll(person));
      assertThat(combined.length(person)).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("Part 2: Combining Different Optic Types")
  class DifferentOpticTypes {

    /**
     * Why this is idiomatic: a single-focus lens fold and a multi-focus list fold both have the
     * same {@code Fold} type, so {@code plus} composes them. The lead's name comes first, then
     * every member's name.
     *
     * <p>Alternative: collect the lead's name into a singleton list, then concatenate. Same answer;
     * the {@code plus} approach treats both folds uniformly.
     *
     * <p>Common wrong attempt: use {@code andThen} when {@code plus} is wanted. {@code andThen}
     * composes paths sequentially; {@code plus} runs them in parallel.
     */
    @Test
    @DisplayName("Exercise 3: Lens fold plus list Fold")
    void exercise3_lensFoldPlusListFold() {
      Team team =
          new Team(
              "Platform",
              new Person("Alice", "Smith", 35),
              List.of(new Person("Bob", "Jones", 28), new Person("Carol", "Lee", 32)));

      Fold<Team, String> leadNameFold = teamLeadLens.asFold().andThen(firstNameLens.asFold());
      Fold<Team, String> memberNamesFold =
          Fold.<Team, Person>of(Team::members).andThen(firstNameLens.asFold());

      // SOLUTION: Combine lead name fold with member names fold
      Fold<Team, String> allNames = leadNameFold.plus(memberNamesFold);

      assertThat(allNames.getAll(team)).containsExactly("Alice", "Bob", "Carol");
    }

    /**
     * Why this is idiomatic: each variant gets a prism-driven fold; {@code plus} merges them into
     * one description fold. A {@code Circle} fires the circle branch only; a {@code Rectangle}
     * fires the rectangle branch only.
     *
     * <p>Alternative: a {@code switch} on the sealed interface. Same answer; the fold form keeps
     * the dispatch as data and survives a new variant addition.
     *
     * <p>Common wrong attempt: combine the branches with {@code andThen}. The result would chain
     * prism + prism, which never matches anything because the prisms are disjoint. {@code plus} is
     * the additive operation.
     */
    @Test
    @DisplayName("Exercise 4: Combine folds from sealed interface branches")
    void exercise4_sealedInterfaceBranches() {
      Prism<Shape, Circle> circlePrism =
          Prism.of(s -> s instanceof Circle c ? Optional.of(c) : Optional.empty(), c -> c);
      Prism<Shape, Rectangle> rectPrism =
          Prism.of(s -> s instanceof Rectangle r ? Optional.of(r) : Optional.empty(), r -> r);

      Fold<Shape, String> circleDesc =
          circlePrism.asFold().andThen(Fold.of(c -> List.of("circle(r=" + c.radius() + ")")));
      Fold<Shape, String> rectDesc =
          rectPrism
              .asFold()
              .andThen(Fold.of(r -> List.of("rect(" + r.width() + "x" + r.height() + ")")));

      // SOLUTION: Combine the two variant folds with plus()
      Fold<Shape, String> shapeDesc = circleDesc.plus(rectDesc);

      assertThat(shapeDesc.getAll(new Circle(5.0))).containsExactly("circle(r=5.0)");
      assertThat(shapeDesc.getAll(new Rectangle(3.0, 4.0))).containsExactly("rect(3.0x4.0)");
    }
  }

  @Nested
  @DisplayName("Part 3: Fold.sum() and Advanced Patterns")
  class SumAndAdvanced {

    /**
     * Why this is idiomatic: {@code Fold.sum(f1, f2, f3)} is a multi-arg version of {@code plus}
     * chains. The result yields focuses from every fold in argument order.
     *
     * <p>Alternative: chain {@code .plus(...)} calls. Same answer; {@code sum} is the named, vararg
     * variant.
     *
     * <p>Common wrong attempt: pass folds with mismatched element types. The combined fold's
     * element type is the common supertype; pick folds that already agree.
     */
    @Test
    @DisplayName("Exercise 5: Fold.sum() for multiple paths")
    void exercise5_foldSum() {
      Person person = new Person("Alice", "Smith", 30);

      Fold<Person, String> firstFold = firstNameLens.asFold();
      Fold<Person, String> lastFold = lastNameLens.asFold();
      Fold<Person, String> ageFold =
          ageLens.asFold().andThen(Fold.of(age -> List.of(String.valueOf(age))));

      // SOLUTION: Use Fold.sum() to combine all three folds
      Fold<Person, String> allInfo = Fold.sum(firstFold, lastFold, ageFold);

      assertThat(allInfo.getAll(person)).containsExactly("Alice", "Smith", "30");
    }

    /**
     * Why this is idiomatic: a real extractor pulls four pieces from a {@code Team} — the team
     * name, the lead's first and last names, and every member's first name. {@code Fold.sum}
     * composes them into one named extractor.
     *
     * <p>Alternative: write a bespoke method that returns a list of strings. Works once; adding a
     * fifth piece means rewriting; the fold-sum scales by adding one argument.
     *
     * <p>Common wrong attempt: try to combine extractors with different result types inside {@code
     * Fold.sum}. The vararg requires a uniform element type; convert to {@code String} (or another
     * shared type) inside each fold first.
     */
    @Test
    @DisplayName("Exercise 6: Real-world extractor")
    void exercise6_realWorldExtractor() {
      Team team =
          new Team(
              "Backend",
              new Person("Alice", "Smith", 35),
              List.of(new Person("Bob", "Jones", 28), new Person("Carol", "Lee", 32)));

      Fold<Team, String> teamNameFold = Fold.of(t -> List.of(t.name()));
      Fold<Team, String> leadFirstNameFold = teamLeadLens.asFold().andThen(firstNameLens.asFold());
      Fold<Team, String> leadLastNameFold = teamLeadLens.asFold().andThen(lastNameLens.asFold());
      Fold<Team, String> memberFirstNamesFold =
          Fold.<Team, Person>of(Team::members).andThen(firstNameLens.asFold());

      // SOLUTION: Use Fold.sum() to combine all four extraction paths
      Fold<Team, String> extractor =
          Fold.sum(teamNameFold, leadFirstNameFold, leadLastNameFold, memberFirstNamesFold);

      assertThat(extractor.getAll(team))
          .containsExactly("Backend", "Alice", "Smith", "Bob", "Carol");
    }

    /**
     * Why this is idiomatic: combine the lead-age and member-ages folds with {@code plus}, then
     * reduce with {@code foldMap} and a sum {@code Monoid}. One number falls out the bottom — the
     * team's total age.
     *
     * <p>Alternative: collect both lists, concatenate, and sum the result. Same answer; the {@code
     * foldMap} version stays in the optic vocabulary.
     *
     * <p>Common wrong attempt: write the {@code Monoid} with {@code combine} that subtracts. The
     * fold reduces in arbitrary order for some optics; pick a commutative monoid for safety.
     */
    @Test
    @DisplayName("Exercise 7: foldMap with custom monoid")
    void exercise7_foldMapWithMonoid() {
      Team team =
          new Team(
              "Platform",
              new Person("Alice", "Smith", 35),
              List.of(new Person("Bob", "Jones", 28), new Person("Carol", "Lee", 32)));

      Monoid<Integer> sumMonoid =
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

      Fold<Team, Integer> leadAgeFold = teamLeadLens.asFold().andThen(ageLens.asFold());
      Fold<Team, Integer> memberAgesFold =
          Fold.<Team, Person>of(Team::members).andThen(ageLens.asFold());

      // SOLUTION: Combine age folds and use foldMap to sum
      int totalAge = leadAgeFold.plus(memberAgesFold).foldMap(sumMonoid, age -> age, team);

      assertThat(totalAge).isEqualTo(95);
    }

    /**
     * Why this is idiomatic: each filtered fold tags its names with a label, then {@code plus}
     * combines the two streams. Seniors come first, juniors after — the {@code plus} order matches
     * the {@code getAll} order.
     *
     * <p>Alternative: a stream pipeline with a single map that branches on age. Same answer; the
     * fold-based version composes with other path operators.
     *
     * <p>Common wrong attempt: rely on the union to be order-independent. {@code plus} is
     * concatenation, not set union — order is preserved.
     */
    @Test
    @DisplayName("Exercise 8: Combining filtered folds")
    void exercise8_combiningFilteredFolds() {
      Team team =
          new Team(
              "Platform",
              new Person("Alice", "Smith", 35),
              List.of(
                  new Person("Bob", "Jones", 28),
                  new Person("Carol", "Lee", 32),
                  new Person("Dave", "Kim", 25)));

      Fold<Team, Person> membersFold = Fold.of(Team::members);

      Fold<Team, String> seniorNames =
          membersFold
              .filtered(p -> p.age() >= 30)
              .andThen(firstNameLens.asFold())
              .andThen(Fold.of(name -> List.of("Senior: " + name)));

      Fold<Team, String> juniorNames =
          membersFold
              .filtered(p -> p.age() < 30)
              .andThen(firstNameLens.asFold())
              .andThen(Fold.of(name -> List.of("Junior: " + name)));

      // SOLUTION: Combine the two filtered folds with plus()
      Fold<Team, String> categorisedNames = seniorNames.plus(juniorNames);

      assertThat(categorisedNames.getAll(team))
          .containsExactly("Senior: Carol", "Junior: Bob", "Junior: Dave");
    }
  }

  @Test
  @DisplayName("All exercises completed")
  void allCompleted() {
    // This test passes when all exercises are correctly implemented
  }
}
