// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.optics;

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
 * Tutorial 18: Fold Combination
 *
 * <p>Learn how to combine multiple Fold optics using {@code plus}, {@code empty}, and {@code sum}
 * to build queries that extract results from several different paths through a data structure.
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>{@code Fold.plus(Fold)} combines two folds into one that returns results from both
 *   <li>{@code Fold.empty()} is the identity element (returns no results)
 *   <li>{@code Fold.sum(first, rest...)} combines multiple folds at once
 *   <li>Combined folds preserve ordering: results from the first fold appear first
 *   <li>Any optic can participate via {@code asFold()} (Lens, Prism, Affine)
 * </ul>
 *
 * <p>Prerequisites: Complete Tutorials 1-6 (especially Lens, Prism, and composition basics).
 *
 * <p>Estimated time: ~10 minutes
 *
 * <p>Replace each placeholder with the correct code to make the tests pass.
 */
@DisplayName("Tutorial 18: Fold Combination")
public class Tutorial18_FoldCombination {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required - replace answerRequired() with your solution");
  }

  // --- Domain models for exercises ---

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

  // ===========================================================================
  // Part 1: Basic Fold Combination
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Basic Fold Combination")
  class BasicCombination {

    /**
     * Exercise 1: Combine two Lens-derived folds with plus
     *
     * <p>Use {@code Lens.asFold()} to convert lenses to folds, then combine them with {@code
     * plus()} to get both the first name and last name from a Person.
     *
     * <p>Task: Create a combined fold that extracts both names.
     */
    @Test
    @DisplayName("Exercise 1: Combine two field folds with plus")
    void exercise1_combineFieldFolds() {
      Person person = new Person("Alice", "Smith", 30);

      // TODO: Create a fold that returns both firstName and lastName
      // Hint: firstNameLens.asFold().plus(lastNameLens.asFold())
      Fold<Person, String> allNames = answerRequired();

      assertThat(allNames.getAll(person)).containsExactly("Alice", "Smith");
      assertThat(allNames.length(person)).isEqualTo(2);
    }

    /**
     * Exercise 2: Identity with Fold.empty()
     *
     * <p>{@code Fold.empty()} returns no results and serves as the identity for {@code plus()}.
     * Combining any fold with {@code Fold.empty()} should return the same results as the original
     * fold.
     *
     * <p>Task: Verify the identity property by combining a fold with Fold.empty().
     */
    @Test
    @DisplayName("Exercise 2: Identity with Fold.empty()")
    void exercise2_identityWithEmpty() {
      Person person = new Person("Alice", "Smith", 30);
      Fold<Person, String> nameFold = firstNameLens.asFold();

      // TODO: Create a fold equivalent to nameFold by combining it with Fold.empty()
      // Hint: nameFold.plus(Fold.empty())
      Fold<Person, String> combined = answerRequired();

      assertThat(combined.getAll(person)).isEqualTo(nameFold.getAll(person));
      assertThat(combined.length(person)).isEqualTo(1);
    }
  }

  // ===========================================================================
  // Part 2: Combining Different Optic Types
  // ===========================================================================

  @Nested
  @DisplayName("Part 2: Combining Different Optic Types")
  class DifferentOpticTypes {

    /**
     * Exercise 3: Combine a Lens fold with a list Fold
     *
     * <p>You can combine a fold derived from a Lens (which focuses on exactly one element) with a
     * Fold that focuses on multiple elements from a list.
     *
     * <p>Task: Create a fold that returns the team lead's name followed by all member names.
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

      // TODO: Combine to get the lead name followed by all member names
      // Hint: leadNameFold.plus(memberNamesFold)
      Fold<Team, String> allNames = answerRequired();

      assertThat(allNames.getAll(team)).containsExactly("Alice", "Bob", "Carol");
    }

    /**
     * Exercise 4: Combine folds from sealed interface branches
     *
     * <p>When working with sealed interfaces, you can use Prism-derived folds for each variant and
     * combine them to extract values regardless of which variant is present.
     *
     * <p>Task: Create a fold that extracts a descriptive string from any Shape variant.
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

      // TODO: Combine to get a description from either variant
      // Hint: circleDesc.plus(rectDesc)
      Fold<Shape, String> shapeDesc = answerRequired();

      assertThat(shapeDesc.getAll(new Circle(5.0))).containsExactly("circle(r=5.0)");
      assertThat(shapeDesc.getAll(new Rectangle(3.0, 4.0))).containsExactly("rect(3.0x4.0)");
    }
  }

  // ===========================================================================
  // Part 3: Fold.sum() and Advanced Patterns
  // ===========================================================================

  @Nested
  @DisplayName("Part 3: Fold.sum() and Advanced Patterns")
  class SumAndAdvanced {

    /**
     * Exercise 5: Using Fold.sum() for multiple paths
     *
     * <p>{@code Fold.sum(first, rest...)} is a convenience for combining three or more folds. It
     * behaves identically to chaining {@code plus()} calls.
     *
     * <p>Task: Use Fold.sum() to combine three field folds from a Person.
     */
    @Test
    @DisplayName("Exercise 5: Fold.sum() for multiple paths")
    void exercise5_foldSum() {
      Person person = new Person("Alice", "Smith", 30);

      Fold<Person, String> firstFold = firstNameLens.asFold();
      Fold<Person, String> lastFold = lastNameLens.asFold();
      Fold<Person, String> ageFold =
          ageLens.asFold().andThen(Fold.of(age -> List.of(String.valueOf(age))));

      // TODO: Use Fold.sum() to combine all three folds
      // Hint: Fold.sum(firstFold, lastFold, ageFold)
      Fold<Person, String> allInfo = answerRequired();

      assertThat(allInfo.getAll(person)).containsExactly("Alice", "Smith", "30");
    }

    /**
     * Exercise 6: Building a real-world extractor
     *
     * <p>Combine folds from multiple paths through a structure to build a comprehensive data
     * extractor. This simulates extracting all relevant strings from a complex nested structure.
     *
     * <p>Task: Extract the team name, lead's full name, and all member first names.
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

      // TODO: Use Fold.sum() to combine all four folds
      // Hint: Fold.sum(teamNameFold, leadFirstNameFold, leadLastNameFold, memberFirstNamesFold)
      Fold<Team, String> extractor = answerRequired();

      assertThat(extractor.getAll(team))
          .containsExactly("Backend", "Alice", "Smith", "Bob", "Carol");
    }

    /**
     * Exercise 7: foldMap with custom monoid across combined folds
     *
     * <p>Combined folds work with {@code foldMap} just like regular folds. The monoid combines
     * results from all constituent folds.
     *
     * <p>Task: Use foldMap with a sum monoid to add ages from both a single person fold and a list
     * fold.
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

      // TODO: Combine the age folds and use foldMap to sum all ages
      // Hint: Create combined fold with plus(), then call foldMap(sumMonoid, age -> age, team)
      int totalAge = answerRequired();

      assertThat(totalAge).isEqualTo(95); // 35 + 28 + 32
    }

    /**
     * Exercise 8: Combining filtered folds
     *
     * <p>You can filter folds first, then combine them. This allows you to combine results from
     * different filtered subsets.
     *
     * <p>Task: Combine a fold of senior members (age >= 30) with a fold of junior members (age <
     * 30), each prefixed accordingly.
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

      // TODO: Combine the two filtered folds
      // Hint: seniorNames.plus(juniorNames)
      Fold<Team, String> categorisedNames = answerRequired();

      assertThat(categorisedNames.getAll(team))
          .containsExactly("Senior: Carol", "Junior: Bob", "Junior: Dave");
    }
  }

  /**
   * Congratulations! You've completed Tutorial 18: Fold Combination
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>How to use {@code Fold.plus()} to combine two folds into one
   *   <li>That {@code Fold.empty()} is the identity element for {@code plus}
   *   <li>How to use {@code Fold.sum()} for combining multiple folds at once
   *   <li>How to combine folds from different optic types (Lens, Prism, Affine)
   *   <li>How to combine folds across sealed interface branches
   *   <li>That combined folds work with foldMap and custom monoids
   *   <li>How to combine filtered folds for categorised results
   * </ul>
   *
   * <p>Key Takeaways:
   *
   * <ul>
   *   <li>{@code plus} forms a monoid on folds (associative with {@code empty} as identity)
   *   <li>Results from the first fold always appear before results from the second
   *   <li>Any optic can participate via {@code asFold()}
   * </ul>
   */
  @Test
  @DisplayName("All exercises completed")
  void allCompleted() {
    // This test passes when all exercises are correctly implemented
  }
}
