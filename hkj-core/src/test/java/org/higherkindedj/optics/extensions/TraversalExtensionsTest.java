// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.extensions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.optics.extensions.TraversalExtensions.*;

import java.util.List;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TraversalExtensions Utility Class Tests")
class TraversalExtensionsTest {

  // Test data structure
  record Person(String name, int age) {}

  private static final Lens<Person, String> nameLens =
      Lens.of(Person::name, (p, n) -> new Person(n, p.age));

  private static final Lens<Person, Integer> ageLens =
      Lens.of(Person::age, (p, a) -> new Person(p.name, a));

  private static final Traversal<List<Person>, String> allNames =
      Traversals.<Person>forList().andThen(nameLens.asTraversal());

  private static final Traversal<List<Person>, Integer> allAges =
      Traversals.<Person>forList().andThen(ageLens.asTraversal());

  @Nested
  @DisplayName("getAllMaybe() - Get all targets as Maybe")
  class GetAllMaybeMethod {
    @Test
    @DisplayName("should return Just with non-empty list")
    void shouldReturnJustForNonEmpty() {
      List<Person> people =
          List.of(new Person("Alice", 30), new Person("Bob", 25), new Person("Charlie", 35));

      Maybe<List<String>> names = getAllMaybe(allNames, people);
      assertThat(names.isJust()).isTrue();
      assertThat(names.get()).containsExactly("Alice", "Bob", "Charlie");
    }

    @Test
    @DisplayName("should return Nothing for empty list")
    void shouldReturnNothingForEmpty() {
      List<Person> people = List.of();
      Maybe<List<String>> names = getAllMaybe(allNames, people);
      assertThat(names.isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("modifyAllMaybe() - Modify all with Maybe")
  class ModifyAllMaybeMethod {
    @Test
    @DisplayName("should modify all when all succeed")
    void shouldModifyAllWhenAllSucceed() {
      List<Person> people =
          List.of(new Person("Alice", 30), new Person("Bob", 25), new Person("Charlie", 35));

      Maybe<List<Person>> result =
          modifyAllMaybe(allNames, name -> Maybe.just(name.toUpperCase()), people);

      assertThat(result.isJust()).isTrue();
      assertThat(result.get()).hasSize(3);
      assertThat(result.get().get(0).name).isEqualTo("ALICE");
      assertThat(result.get().get(1).name).isEqualTo("BOB");
      assertThat(result.get().get(2).name).isEqualTo("CHARLIE");
    }

    @Test
    @DisplayName("should return Nothing when any fails")
    void shouldReturnNothingWhenAnyFails() {
      List<Person> people =
          List.of(new Person("Alice", 30), new Person("", 25), new Person("Charlie", 35));

      Maybe<List<Person>> result =
          modifyAllMaybe(
              allNames,
              name -> name.isEmpty() ? Maybe.nothing() : Maybe.just(name.toUpperCase()),
              people);

      assertThat(result.isNothing()).isTrue();
    }

    @Test
    @DisplayName("should handle empty list")
    void shouldHandleEmptyList() {
      List<Person> people = List.of();
      Maybe<List<Person>> result =
          modifyAllMaybe(allNames, name -> Maybe.just(name.toUpperCase()), people);
      assertThat(result.isJust()).isTrue();
      assertThat(result.get()).isEmpty();
    }
  }

  @Nested
  @DisplayName("modifyAllEither() - Modify all with Either (fail-fast)")
  class ModifyAllEitherMethod {
    @Test
    @DisplayName("should modify all when all succeed")
    void shouldModifyAllWhenAllSucceed() {
      List<Person> people =
          List.of(new Person("Alice", 30), new Person("Bob", 25), new Person("Charlie", 35));

      Either<String, List<Person>> result =
          modifyAllEither(
              allAges,
              age -> age >= 18 ? Either.right(age + 1) : Either.left("Too young: " + age),
              people);

      assertThat(result.isRight()).isTrue();
      assertThat(result.getRight()).hasSize(3);
      assertThat(result.getRight().get(0).age).isEqualTo(31);
      assertThat(result.getRight().get(1).age).isEqualTo(26);
      assertThat(result.getRight().get(2).age).isEqualTo(36);
    }

    @Test
    @DisplayName("should return first error in fail-fast mode")
    void shouldReturnFirstError() {
      List<Person> people =
          List.of(new Person("Alice", 30), new Person("Bob", 15), new Person("Charlie", 10));

      Either<String, List<Person>> result =
          modifyAllEither(
              allAges,
              age -> age >= 18 ? Either.right(age + 1) : Either.left("Too young: " + age),
              people);

      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft()).isEqualTo("Too young: 15");
    }

    @Test
    @DisplayName("should handle empty list")
    void shouldHandleEmptyList() {
      List<Person> people = List.of();
      Either<String, List<Person>> result =
          modifyAllEither(allAges, age -> Either.right(age + 1), people);
      assertThat(result.isRight()).isTrue();
      assertThat(result.getRight()).isEmpty();
    }
  }

  @Nested
  @DisplayName("modifyAllValidated() - Modify all with Validated (error accumulation)")
  class ModifyAllValidatedMethod {
    @Test
    @DisplayName("should modify all when all succeed")
    void shouldModifyAllWhenAllSucceed() {
      List<Person> people =
          List.of(new Person("Alice", 30), new Person("Bob", 25), new Person("Charlie", 35));

      Validated<List<String>, List<Person>> result =
          modifyAllValidated(
              allNames,
              name ->
                  name.length() >= 3
                      ? Validated.valid(name.toLowerCase())
                      : Validated.invalid("Name too short: " + name),
              people);

      assertThat(result.isValid()).isTrue();
      assertThat(result.get()).hasSize(3);
      assertThat(result.get().get(0).name).isEqualTo("alice");
      assertThat(result.get().get(1).name).isEqualTo("bob");
      assertThat(result.get().get(2).name).isEqualTo("charlie");
    }

    @Test
    @DisplayName("should accumulate all errors")
    void shouldAccumulateAllErrors() {
      List<Person> people =
          List.of(new Person("Al", 30), new Person("Bob", 25), new Person("Ed", 35));

      Validated<List<String>, List<Person>> result =
          modifyAllValidated(
              allNames,
              name ->
                  name.length() >= 3
                      ? Validated.valid(name.toLowerCase())
                      : Validated.invalid("Name too short: " + name),
              people);

      assertThat(result.isInvalid()).isTrue();
      assertThat(result.getError()).containsExactly("Name too short: Al", "Name too short: Ed");
    }

    @Test
    @DisplayName("should handle empty list")
    void shouldHandleEmptyList() {
      List<Person> people = List.of();
      Validated<List<String>, List<Person>> result =
          modifyAllValidated(allNames, name -> Validated.valid(name.toLowerCase()), people);
      assertThat(result.isValid()).isTrue();
      assertThat(result.get()).isEmpty();
    }

    @Test
    @DisplayName("should collect multiple errors from different targets")
    void shouldCollectMultipleErrors() {
      List<Person> people =
          List.of(
              new Person("Alice", -5),
              new Person("Bob", -10),
              new Person("Charlie", 30),
              new Person("Dave", -3));

      Validated<List<String>, List<Person>> result =
          modifyAllValidated(
              allAges,
              age -> age >= 0 ? Validated.valid(age) : Validated.invalid("Invalid age: " + age),
              people);

      assertThat(result.isInvalid()).isTrue();
      assertThat(result.getError())
          .containsExactly("Invalid age: -5", "Invalid age: -10", "Invalid age: -3");
    }
  }

  @Nested
  @DisplayName("modifyWherePossible() - Selective modification")
  class ModifyWherePossibleMethod {
    @Test
    @DisplayName("should modify only where function returns Just")
    void shouldModifyOnlyWhereJust() {
      List<Person> people =
          List.of(new Person("Alice", 30), new Person("Bob", 25), new Person("Charlie", 35));

      List<Person> result =
          modifyWherePossible(
              allAges, age -> age >= 30 ? Maybe.just(age + 10) : Maybe.nothing(), people);

      assertThat(result).hasSize(3);
      assertThat(result.get(0).age).isEqualTo(40); // Modified (30 -> 40)
      assertThat(result.get(1).age).isEqualTo(25); // Unchanged
      assertThat(result.get(2).age).isEqualTo(45); // Modified (35 -> 45)
    }

    @Test
    @DisplayName("should leave all unchanged when all return Nothing")
    void shouldLeaveAllUnchangedWhenAllNothing() {
      List<Person> people =
          List.of(new Person("Alice", 20), new Person("Bob", 25), new Person("Charlie", 28));

      List<Person> result =
          modifyWherePossible(
              allAges, age -> age >= 30 ? Maybe.just(age + 10) : Maybe.nothing(), people);

      assertThat(result).hasSize(3);
      assertThat(result.get(0).age).isEqualTo(20);
      assertThat(result.get(1).age).isEqualTo(25);
      assertThat(result.get(2).age).isEqualTo(28);
    }

    @Test
    @DisplayName("should modify all when all return Just")
    void shouldModifyAllWhenAllJust() {
      List<Person> people =
          List.of(new Person("Alice", 30), new Person("Bob", 35), new Person("Charlie", 40));

      List<Person> result =
          modifyWherePossible(
              allAges, age -> age >= 30 ? Maybe.just(age + 10) : Maybe.nothing(), people);

      assertThat(result).hasSize(3);
      assertThat(result.get(0).age).isEqualTo(40);
      assertThat(result.get(1).age).isEqualTo(45);
      assertThat(result.get(2).age).isEqualTo(50);
    }
  }

  @Nested
  @DisplayName("countValid() - Count valid targets")
  class CountValidMethod {
    @Test
    @DisplayName("should count targets that pass validation")
    void shouldCountValidTargets() {
      List<Person> people =
          List.of(new Person("Alice", 30), new Person("Bob", 15), new Person("Charlie", 25));

      int count =
          countValid(
              allAges, age -> age >= 18 ? Either.right(age) : Either.left("Too young"), people);

      assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("should return zero when none pass")
    void shouldReturnZeroWhenNonePass() {
      List<Person> people =
          List.of(new Person("Alice", 10), new Person("Bob", 15), new Person("Charlie", 12));

      int count =
          countValid(
              allAges, age -> age >= 18 ? Either.right(age) : Either.left("Too young"), people);

      assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("should return count when all pass")
    void shouldReturnCountWhenAllPass() {
      List<Person> people =
          List.of(new Person("Alice", 30), new Person("Bob", 25), new Person("Charlie", 35));

      int count =
          countValid(
              allAges, age -> age >= 18 ? Either.right(age) : Either.left("Too young"), people);

      assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("should handle empty list")
    void shouldHandleEmptyList() {
      List<Person> people = List.of();
      int count =
          countValid(
              allAges, age -> age >= 18 ? Either.right(age) : Either.left("Too young"), people);
      assertThat(count).isEqualTo(0);
    }
  }

  @Nested
  @DisplayName("collectErrors() - Collect validation errors")
  class CollectErrorsMethod {
    @Test
    @DisplayName("should collect errors from failed validations")
    void shouldCollectErrors() {
      List<Person> people =
          List.of(new Person("Alice", 30), new Person("Bob", 15), new Person("Charlie", 10));

      List<String> errors =
          collectErrors(
              allAges,
              age -> age >= 18 ? Either.right(age) : Either.left("Too young: " + age),
              people);

      assertThat(errors).containsExactly("Too young: 15", "Too young: 10");
    }

    @Test
    @DisplayName("should return empty list when all pass")
    void shouldReturnEmptyWhenAllPass() {
      List<Person> people =
          List.of(new Person("Alice", 30), new Person("Bob", 25), new Person("Charlie", 35));

      List<String> errors =
          collectErrors(
              allAges,
              age -> age >= 18 ? Either.right(age) : Either.left("Too young: " + age),
              people);

      assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("should collect all errors when all fail")
    void shouldCollectAllErrorsWhenAllFail() {
      List<Person> people =
          List.of(new Person("Alice", 10), new Person("Bob", 15), new Person("Charlie", 12));

      List<String> errors =
          collectErrors(
              allAges,
              age -> age >= 18 ? Either.right(age) : Either.left("Too young: " + age),
              people);

      assertThat(errors).containsExactly("Too young: 10", "Too young: 15", "Too young: 12");
    }

    @Test
    @DisplayName("should handle empty list")
    void shouldHandleEmptyList() {
      List<Person> people = List.of();
      List<String> errors =
          collectErrors(
              allAges, age -> age >= 18 ? Either.right(age) : Either.left("Too young"), people);
      assertThat(errors).isEmpty();
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {
    @Test
    @DisplayName("should combine modifyAllValidated with error analysis")
    void shouldCombineModifyAllValidatedWithErrorAnalysis() {
      List<Person> people =
          List.of(
              new Person("Alice", 30),
              new Person("", 25),
              new Person("Bob", -5),
              new Person("Charlie", 35));

      // Validate names
      Validated<List<String>, List<Person>> nameValidation =
          modifyAllValidated(
              allNames,
              name -> name.length() >= 1 ? Validated.valid(name) : Validated.invalid("Empty name"),
              people);

      // Validate ages
      Validated<List<String>, List<Person>> ageValidation =
          modifyAllValidated(
              allAges,
              age -> age >= 0 ? Validated.valid(age) : Validated.invalid("Negative age: " + age),
              people);

      assertThat(nameValidation.isInvalid()).isTrue();
      assertThat(nameValidation.getError()).contains("Empty name");

      assertThat(ageValidation.isInvalid()).isTrue();
      assertThat(ageValidation.getError()).contains("Negative age: -5");
    }

    @Test
    @DisplayName("should use countValid and collectErrors together for reporting")
    void shouldUseCountValidAndCollectErrorsTogether() {
      List<Person> people =
          List.of(
              new Person("Alice", 30),
              new Person("Bob", 15),
              new Person("Charlie", 25),
              new Person("Dave", 10));

      int validCount =
          countValid(
              allAges,
              age -> age >= 18 ? Either.right(age) : Either.left("Too young: " + age),
              people);

      List<String> errors =
          collectErrors(
              allAges,
              age -> age >= 18 ? Either.right(age) : Either.left("Too young: " + age),
              people);

      assertThat(validCount).isEqualTo(2);
      assertThat(errors).hasSize(2);
      assertThat(validCount + errors.size()).isEqualTo(people.size());
    }

    @Test
    @DisplayName("should chain modifyWherePossible with getAllMaybe")
    void shouldChainModifyWherePossibleWithGetAllMaybe() {
      List<Person> people =
          List.of(new Person("Alice", 30), new Person("Bob", 25), new Person("Charlie", 35));

      List<Person> modified =
          modifyWherePossible(
              allAges, age -> age >= 30 ? Maybe.just(age + 5) : Maybe.nothing(), people);

      Maybe<List<Integer>> ages = getAllMaybe(allAges, modified);

      assertThat(ages.isJust()).isTrue();
      assertThat(ages.get()).containsExactly(35, 25, 40);
    }

    @Test
    @DisplayName("should handle complex nested modifications")
    void shouldHandleComplexNestedModifications() {
      List<Person> people =
          List.of(new Person("alice", 30), new Person("bob", 25), new Person("charlie", 35));

      // Step 1: Capitalize names
      Either<String, List<Person>> step1 =
          modifyAllEither(
              allNames,
              name -> Either.right(name.substring(0, 1).toUpperCase() + name.substring(1)),
              people);

      assertThat(step1.isRight()).isTrue();

      // Step 2: Increment ages for those >= 30
      List<Person> step2 =
          modifyWherePossible(
              allAges, age -> age >= 30 ? Maybe.just(age + 1) : Maybe.nothing(), step1.getRight());

      assertThat(step2.get(0).name).isEqualTo("Alice");
      assertThat(step2.get(0).age).isEqualTo(31);
      assertThat(step2.get(1).name).isEqualTo("Bob");
      assertThat(step2.get(1).age).isEqualTo(25);
      assertThat(step2.get(2).name).isEqualTo("Charlie");
      assertThat(step2.get(2).age).isEqualTo(36);
    }
  }
}
