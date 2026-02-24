// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.focus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the traverseWith() bridge method across all Focus DSL path types.
 *
 * <p>Tests cover FocusPath.traverseWith(), AffinePath.traverseWith(), and
 * TraversalPath.traverseWith() — verifying the optics-to-VTaskPath parallel effectful traversal
 * bridge.
 */
@DisplayName("traverseWith() - Optics to VTaskPath Bridge")
class TraverseWithTest {

  // Test Data Structures
  record Team(String name, List<String> members) {}

  record Config(String apiKey, Optional<String> optionalEndpoint) {}

  // Optics
  private Lens<Team, String> nameLens;
  private Lens<Team, List<String>> memberListLens;
  private Traversal<List<String>, String> listTraversal;

  @BeforeEach
  void setUp() {
    nameLens = Lens.of(Team::name, (t, n) -> new Team(n, t.members()));
    memberListLens = Lens.of(Team::members, (t, m) -> new Team(t.name(), m));
    listTraversal = Traversals.forList();
  }

  @Nested
  @DisplayName("FocusPath.traverseWith()")
  class FocusPathTraverseWith {

    @Test
    @DisplayName("applies effectful function to focused element")
    void appliesEffectfulFunctionToFocusedElement() {
      FocusPath<Team, String> path = FocusPath.of(nameLens);
      Team team = new Team("Engineering", List.of("Alice"));

      VTaskPath<String> result =
          path.traverseWith(name -> Path.vtask(() -> name.toUpperCase()), team);

      assertThat(result.unsafeRun()).isEqualTo("ENGINEERING");
    }

    @Test
    @DisplayName("executes deferred computation on virtual thread")
    void executesDeferredComputation() {
      FocusPath<Team, String> path = FocusPath.of(nameLens);
      Team team = new Team("DevOps", List.of());

      VTaskPath<Integer> result = path.traverseWith(name -> Path.vtask(() -> name.length()), team);

      assertThat(result.unsafeRun()).isEqualTo(6);
    }

    @Test
    @DisplayName("propagates exceptions from effectful function")
    void propagatesExceptions() {
      FocusPath<Team, String> path = FocusPath.of(nameLens);
      Team team = new Team("QA", List.of());

      VTaskPath<String> result =
          path.traverseWith(
              _ ->
                  Path.vtask(
                      () -> {
                        throw new RuntimeException("test error");
                      }),
              team);

      assertThat(result.runSafe().isFailure()).isTrue();
    }
  }

  @Nested
  @DisplayName("AffinePath.traverseWith()")
  class AffinePathTraverseWith {

    @Test
    @DisplayName("returns Optional.of(result) when affine matches")
    void returnsOptionalOfResultWhenMatches() {
      FocusPath<Team, List<String>> membersPath = FocusPath.of(memberListLens);
      AffinePath<Team, String> firstMemberPath = membersPath.<String>head();
      Team team = new Team("Dev", List.of("Alice", "Bob"));

      VTaskPath<Optional<String>> result =
          firstMemberPath.traverseWith(name -> Path.vtask(() -> "Hello, " + name + "!"), team);

      assertThat(result.unsafeRun()).contains("Hello, Alice!");
    }

    @Test
    @DisplayName("returns Optional.empty() when affine does not match")
    void returnsOptionalEmptyWhenNotMatches() {
      FocusPath<Team, List<String>> membersPath = FocusPath.of(memberListLens);
      AffinePath<Team, String> firstMemberPath = membersPath.<String>head();
      Team team = new Team("Empty", List.of());

      VTaskPath<Optional<String>> result =
          firstMemberPath.traverseWith(name -> Path.vtask(() -> "Hello, " + name + "!"), team);

      assertThat(result.unsafeRun()).isEmpty();
    }

    @Test
    @DisplayName("does not execute task when affine does not match")
    void doesNotExecuteTaskWhenNotMatches() {
      FocusPath<Team, List<String>> membersPath = FocusPath.of(memberListLens);
      AffinePath<Team, String> firstMemberPath = membersPath.<String>head();
      Team team = new Team("Empty", List.of());

      // If the task were executed, it would throw
      VTaskPath<Optional<String>> result =
          firstMemberPath.traverseWith(
              _ ->
                  Path.vtask(
                      () -> {
                        throw new RuntimeException("should not execute");
                      }),
              team);

      assertThat(result.unsafeRun()).isEmpty();
    }
  }

  @Nested
  @DisplayName("TraversalPath.traverseWith()")
  class TraversalPathTraverseWith {

    @Test
    @DisplayName("applies effectful function to all elements in parallel")
    void appliesEffectfulFunctionToAllElements() {
      TraversalPath<Team, String> path = FocusPath.of(memberListLens).via(listTraversal);
      Team team = new Team("Dev", List.of("alice", "bob", "charlie"));

      VTaskPath<List<String>> result =
          path.traverseWith(name -> Path.vtask(() -> name.toUpperCase()), team);

      assertThat(result.unsafeRun()).containsExactly("ALICE", "BOB", "CHARLIE");
    }

    @Test
    @DisplayName("returns empty list for empty traversal")
    void returnsEmptyListForEmptyTraversal() {
      TraversalPath<Team, String> path = FocusPath.of(memberListLens).via(listTraversal);
      Team team = new Team("Empty", List.of());

      VTaskPath<List<String>> result =
          path.traverseWith(name -> Path.vtask(() -> name.toUpperCase()), team);

      assertThat(result.unsafeRun()).isEmpty();
    }

    @Test
    @DisplayName("preserves element order")
    void preservesElementOrder() {
      TraversalPath<Team, String> path = FocusPath.of(memberListLens).via(listTraversal);
      Team team = new Team("Dev", List.of("first", "second", "third"));

      VTaskPath<List<Integer>> result =
          path.traverseWith(
              name ->
                  Path.vtask(
                      () -> {
                        // Simulate varying task durations
                        Thread.sleep((long) (Math.random() * 10));
                        return name.length();
                      }),
              team);

      assertThat(result.unsafeRun()).containsExactly(5, 6, 5);
    }

    @Test
    @DisplayName("executes tasks concurrently via structured concurrency")
    void executesTasksConcurrently() {
      TraversalPath<Team, String> path = FocusPath.of(memberListLens).via(listTraversal);
      Team team = new Team("Dev", List.of("a", "b", "c", "d", "e"));

      VTaskPath<List<String>> result =
          path.traverseWith(
              name ->
                  Path.vtask(
                      () -> {
                        // Record the thread name to verify virtual threads are used
                        return name + "@" + Thread.currentThread().isVirtual();
                      }),
              team);

      List<String> values = result.unsafeRun();
      assertThat(values).hasSize(5);
      // All tasks should execute on virtual threads
      assertThat(values).allMatch(v -> v.endsWith("@true"));
    }

    @Test
    @DisplayName("propagates first failure from parallel tasks")
    void propagatesFirstFailure() {
      TraversalPath<Team, String> path = FocusPath.of(memberListLens).via(listTraversal);
      Team team = new Team("Dev", List.of("ok", "fail", "ok2"));

      VTaskPath<List<String>> result =
          path.traverseWith(
              name -> {
                if (name.equals("fail")) {
                  return Path.vtaskFail(new RuntimeException("task failed"));
                }
                return Path.vtask(() -> name.toUpperCase());
              },
              team);

      assertThat(result.runSafe().isFailure()).isTrue();
    }

    @Test
    @DisplayName("works with filtered traversal")
    void worksWithFilteredTraversal() {
      TraversalPath<Team, String> path =
          FocusPath.of(memberListLens).via(listTraversal).filter(name -> name.startsWith("a"));
      Team team = new Team("Dev", List.of("alice", "bob", "anna", "charlie"));

      VTaskPath<List<String>> result =
          path.traverseWith(name -> Path.vtask(() -> name.toUpperCase()), team);

      assertThat(result.unsafeRun()).containsExactly("ALICE", "ANNA");
    }

    @Test
    @DisplayName("handles single element traversal")
    void handlesSingleElementTraversal() {
      TraversalPath<Team, String> path = FocusPath.of(memberListLens).via(listTraversal);
      Team team = new Team("Solo", List.of("only-one"));

      VTaskPath<List<String>> result =
          path.traverseWith(name -> Path.vtask(() -> name.toUpperCase()), team);

      assertThat(result.unsafeRun()).containsExactly("ONLY-ONE");
    }
  }
}
