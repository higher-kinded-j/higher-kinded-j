// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.focus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.higherkindedj.hkt.effect.ListPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.NonDetPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.StreamPath;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for TraversalPath bridge methods to EffectPath types.
 *
 * <p>Tests cover the bridge methods: toListPath, toNonDetPath, toStreamPath, and toMaybePath.
 */
@DisplayName("TraversalPath Bridge Methods Tests")
class TraversalPathBridgeTest {

  // Test Data Structures
  record Team(String name, List<String> members) {}

  record Company(String name, List<Team> teams) {}

  // Lenses and Traversals
  private Lens<Team, List<String>> memberListLens;
  private Traversal<List<String>, String> listTraversal;
  private Lens<Company, List<Team>> teamsLens;

  @BeforeEach
  void setUp() {
    memberListLens = Lens.of(Team::members, (t, m) -> new Team(t.name(), m));
    listTraversal = Traversals.forList();
    teamsLens = Lens.of(Company::teams, (c, t) -> new Company(c.name(), t));
  }

  @Nested
  @DisplayName("toListPath()")
  class ToListPathTests {

    @Test
    @DisplayName("extracts all values into ListPath")
    void extractsAllValuesIntoListPath() {
      TraversalPath<Team, String> path = FocusPath.of(memberListLens).via(listTraversal);
      Team team = new Team("Dev", List.of("Alice", "Bob", "Charlie"));

      ListPath<String> result = path.toListPath(team);

      assertThat(result.run()).containsExactly("Alice", "Bob", "Charlie");
    }

    @Test
    @DisplayName("returns empty ListPath for empty traversal")
    void returnsEmptyListPathForEmptyTraversal() {
      TraversalPath<Team, String> path = FocusPath.of(memberListLens).via(listTraversal);
      Team team = new Team("Empty", List.of());

      ListPath<String> result = path.toListPath(team);

      assertThat(result.run()).isEmpty();
    }

    @Test
    @DisplayName("allows map transformation")
    void allowsMapTransformation() {
      TraversalPath<Team, String> path = FocusPath.of(memberListLens).via(listTraversal);
      Team team = new Team("Dev", List.of("alice", "bob"));

      ListPath<String> result = path.toListPath(team).map(String::toUpperCase);

      assertThat(result.run()).containsExactly("ALICE", "BOB");
    }

    @Test
    @DisplayName("supports positional zipWith")
    void supportsPositionalZipWith() {
      TraversalPath<Team, String> path = FocusPath.of(memberListLens).via(listTraversal);
      Team team = new Team("Dev", List.of("Alice", "Bob", "Charlie"));
      ListPath<Integer> indices = Path.listPath(List.of(1, 2, 3));

      ListPath<String> result =
          path.toListPath(team).zipWith(indices, (name, idx) -> idx + "." + name);

      assertThat(result.run()).containsExactly("1.Alice", "2.Bob", "3.Charlie");
    }
  }

  @Nested
  @DisplayName("toNonDetPath()")
  class ToNonDetPathTests {

    @Test
    @DisplayName("extracts all values into NonDetPath")
    void extractsAllValuesIntoNonDetPath() {
      TraversalPath<Team, String> path = FocusPath.of(memberListLens).via(listTraversal);
      Team team = new Team("Dev", List.of("Alice", "Bob"));

      NonDetPath<String> result = path.toNonDetPath(team);

      assertThat(result.run()).containsExactly("Alice", "Bob");
    }

    @Test
    @DisplayName("supports Cartesian product zipWith")
    void supportsCartesianProductZipWith() {
      TraversalPath<Team, String> path = FocusPath.of(memberListLens).via(listTraversal);
      Team team = new Team("Dev", List.of("A", "B"));
      NonDetPath<Integer> numbers = Path.list(List.of(1, 2));

      NonDetPath<String> result = path.toNonDetPath(team).zipWith(numbers, (name, n) -> name + n);

      // Cartesian product: A1, A2, B1, B2
      assertThat(result.run()).containsExactly("A1", "A2", "B1", "B2");
    }

    @Test
    @DisplayName("returns empty NonDetPath for empty traversal")
    void returnsEmptyNonDetPathForEmptyTraversal() {
      TraversalPath<Team, String> path = FocusPath.of(memberListLens).via(listTraversal);
      Team team = new Team("Empty", List.of());

      NonDetPath<String> result = path.toNonDetPath(team);

      assertThat(result.run()).isEmpty();
    }
  }

  @Nested
  @DisplayName("toStreamPath()")
  class ToStreamPathTests {

    @Test
    @DisplayName("extracts all values into StreamPath")
    void extractsAllValuesIntoStreamPath() {
      TraversalPath<Team, String> path = FocusPath.of(memberListLens).via(listTraversal);
      Team team = new Team("Dev", List.of("Alice", "Bob", "Charlie"));

      StreamPath<String> result = path.toStreamPath(team);

      assertThat(result.run().toList()).containsExactly("Alice", "Bob", "Charlie");
    }

    @Test
    @DisplayName("allows lazy stream transformations")
    void allowsLazyStreamTransformations() {
      TraversalPath<Team, String> path = FocusPath.of(memberListLens).via(listTraversal);
      Team team = new Team("Dev", List.of("alice", "bob", "charlie", "dave"));

      StreamPath<String> result =
          path.toStreamPath(team)
              .map(String::toUpperCase)
              .filter(name -> name.startsWith("A") || name.startsWith("C"));

      assertThat(result.run().toList()).containsExactly("ALICE", "CHARLIE");
    }

    @Test
    @DisplayName("returns empty StreamPath for empty traversal")
    void returnsEmptyStreamPathForEmptyTraversal() {
      TraversalPath<Team, String> path = FocusPath.of(memberListLens).via(listTraversal);
      Team team = new Team("Empty", List.of());

      StreamPath<String> result = path.toStreamPath(team);

      assertThat(result.run().toList()).isEmpty();
    }
  }

  @Nested
  @DisplayName("toMaybePath()")
  class ToMaybePathTests {

    @Test
    @DisplayName("returns Just with first value when traversal has elements")
    void returnsJustWithFirstValueWhenTraversalHasElements() {
      TraversalPath<Team, String> path = FocusPath.of(memberListLens).via(listTraversal);
      Team team = new Team("Dev", List.of("First", "Second", "Third"));

      MaybePath<String> result = path.toMaybePath(team);

      assertThat(result.run().isJust()).isTrue();
      assertThat(result.run().get()).isEqualTo("First");
    }

    @Test
    @DisplayName("returns Nothing when traversal is empty")
    void returnsNothingWhenTraversalIsEmpty() {
      TraversalPath<Team, String> path = FocusPath.of(memberListLens).via(listTraversal);
      Team team = new Team("Empty", List.of());

      MaybePath<String> result = path.toMaybePath(team);

      assertThat(result.run().isNothing()).isTrue();
    }

    @Test
    @DisplayName("allows chaining operations on first element")
    void allowsChainingOperationsOnFirstElement() {
      TraversalPath<Team, String> path = FocusPath.of(memberListLens).via(listTraversal);
      Team team = new Team("Dev", List.of("alice", "bob"));

      String result = path.toMaybePath(team).map(String::toUpperCase).getOrElse("NONE");

      assertThat(result).isEqualTo("ALICE");
    }
  }

  @Nested
  @DisplayName("Integration with each() helper")
  class EachHelperIntegrationTests {

    @Test
    @DisplayName("each() composed with toListPath")
    void eachComposedWithToListPath() {
      FocusPath<Team, List<String>> membersPath = FocusPath.of(memberListLens);
      Team team = new Team("QA", List.of("Tester1", "Tester2"));

      // Using each() to get TraversalPath, then bridge to ListPath
      TraversalPath<Team, String> eachMember = membersPath.<String>each();
      ListPath<String> result = eachMember.toListPath(team);

      assertThat(result.run()).containsExactly("Tester1", "Tester2");
    }

    @Test
    @DisplayName("deep traversal to ListPath")
    void deepTraversalToListPath() {
      // Build the traversal step by step for type safety
      TraversalPath<Company, Team> allTeams = FocusPath.of(teamsLens).<Team>each();
      TraversalPath<Company, List<String>> allMemberLists = allTeams.via(memberListLens);
      TraversalPath<Company, String> allMembersPath = allMemberLists.<String>each();

      Company company =
          new Company(
              "Tech Corp",
              List.of(
                  new Team("Dev", List.of("Alice", "Bob")),
                  new Team("QA", List.of("Charlie", "Dave"))));

      ListPath<String> result = allMembersPath.toListPath(company);

      assertThat(result.run()).containsExactly("Alice", "Bob", "Charlie", "Dave");
    }
  }
}
