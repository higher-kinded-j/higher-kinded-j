// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.extensions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.optics.extensions.LensExtensions.*;

import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Lens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("LensExtensions Utility Class Tests")
class LensExtensionsTest {

  // Test data structure
  record Person(String name, Integer age) {}

  private static final Lens<Person, String> nameLens =
      Lens.of(Person::name, (p, n) -> new Person(n, p.age));

  private static final Lens<Person, Integer> ageLens =
      Lens.of(Person::age, (p, a) -> new Person(p.name, a));

  @Nested
  @DisplayName("getMaybe() - Get value as Maybe")
  class GetMaybeMethod {
    @Test
    @DisplayName("should return Just when value is non-null")
    void shouldReturnJustForNonNull() {
      Person person = new Person("Alice", 30);
      Maybe<String> result = getMaybe(nameLens, person);
      assertThat(result.isJust()).isTrue();
      assertThat(result.get()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("should return Nothing when value is null")
    void shouldReturnNothingForNull() {
      Person person = new Person(null, 30);
      Maybe<String> result = getMaybe(nameLens, person);
      assertThat(result.isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("getEither() - Get value as Either")
  class GetEitherMethod {
    @Test
    @DisplayName("should return Right when value is non-null")
    void shouldReturnRightForNonNull() {
      Person person = new Person("Bob", 25);
      Either<String, String> result = getEither(nameLens, "No name", person);
      assertThat(result.isRight()).isTrue();
      assertThat(result.getRight()).isEqualTo("Bob");
    }

    @Test
    @DisplayName("should return Left with error when value is null")
    void shouldReturnLeftForNull() {
      Person person = new Person(null, 25);
      Either<String, String> result = getEither(nameLens, "No name", person);
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft()).isEqualTo("No name");
    }
  }

  @Nested
  @DisplayName("getValidated() - Get value as Validated")
  class GetValidatedMethod {
    @Test
    @DisplayName("should return Valid when value is non-null")
    void shouldReturnValidForNonNull() {
      Person person = new Person("Charlie", 35);
      Validated<String, String> result = getValidated(nameLens, "Missing name", person);
      assertThat(result.isValid()).isTrue();
      assertThat(result.get()).isEqualTo("Charlie");
    }

    @Test
    @DisplayName("should return Invalid with error when value is null")
    void shouldReturnInvalidForNull() {
      Person person = new Person(null, 35);
      Validated<String, String> result = getValidated(nameLens, "Missing name", person);
      assertThat(result.isInvalid()).isTrue();
      assertThat(result.getError()).isEqualTo("Missing name");
    }
  }

  @Nested
  @DisplayName("modifyMaybe() - Modify with Maybe-returning function")
  class ModifyMaybeMethod {
    @Test
    @DisplayName("should modify when function returns Just")
    void shouldModifyWhenJust() {
      Person person = new Person("Dave", 40);
      Maybe<Person> result = modifyMaybe(nameLens, name -> Maybe.just(name.toUpperCase()), person);
      assertThat(result.isJust()).isTrue();
      assertThat(result.get().name).isEqualTo("DAVE");
      assertThat(result.get().age).isEqualTo(40);
    }

    @Test
    @DisplayName("should return Nothing when function returns Nothing")
    void shouldReturnNothingWhenFunctionReturnsNothing() {
      Person person = new Person("Eve", 28);
      Maybe<Person> result = modifyMaybe(nameLens, name -> Maybe.nothing(), person);
      assertThat(result.isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("modifyEither() - Modify with Either-returning function")
  class ModifyEitherMethod {
    @Test
    @DisplayName("should modify when function returns Right")
    void shouldModifyWhenRight() {
      Person person = new Person("Frank", 45);
      Either<String, Person> result =
          modifyEither(
              ageLens,
              age -> age >= 0 ? Either.right(age + 1) : Either.left("Invalid age"),
              person);
      assertThat(result.isRight()).isTrue();
      assertThat(result.getRight().name).isEqualTo("Frank");
      assertThat(result.getRight().age).isEqualTo(46);
    }

    @Test
    @DisplayName("should return Left when function returns Left")
    void shouldReturnLeftWhenFunctionReturnsLeft() {
      Person person = new Person("Grace", -5);
      Either<String, Person> result =
          modifyEither(
              ageLens,
              age -> age >= 0 ? Either.right(age + 1) : Either.left("Invalid age"),
              person);
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft()).isEqualTo("Invalid age");
    }
  }

  @Nested
  @DisplayName("modifyValidated() - Modify with Validated-returning function")
  class ModifyValidatedMethod {
    @Test
    @DisplayName("should modify when function returns Valid")
    void shouldModifyWhenValid() {
      Person person = new Person("Henry", 50);
      Validated<String, Person> result =
          modifyValidated(
              nameLens,
              name ->
                  name.length() > 0
                      ? Validated.valid(name.toLowerCase())
                      : Validated.invalid("Name too short"),
              person);
      assertThat(result.isValid()).isTrue();
      assertThat(result.get().name).isEqualTo("henry");
      assertThat(result.get().age).isEqualTo(50);
    }

    @Test
    @DisplayName("should return Invalid when function returns Invalid")
    void shouldReturnInvalidWhenFunctionReturnsInvalid() {
      Person person = new Person("", 50);
      Validated<String, Person> result =
          modifyValidated(
              nameLens,
              name ->
                  name.length() > 0
                      ? Validated.valid(name.toLowerCase())
                      : Validated.invalid("Name too short"),
              person);
      assertThat(result.isInvalid()).isTrue();
      assertThat(result.getError()).isEqualTo("Name too short");
    }
  }

  @Nested
  @DisplayName("modifyTry() - Modify with Try-returning function")
  class ModifyTryMethod {
    @Test
    @DisplayName("should modify when function returns Success")
    void shouldModifyWhenSuccess() {
      Person person = new Person("Ivy", 22);
      Try<Person> result = modifyTry(ageLens, age -> Try.success(age * 2), person);
      assertThat(result.isSuccess()).isTrue();
      Person modifiedPerson =
          result.fold(
              v -> v,
              error -> {
                throw new AssertionError("Expected success", error);
              });
      assertThat(modifiedPerson.name).isEqualTo("Ivy");
      assertThat(modifiedPerson.age).isEqualTo(44);
    }

    @Test
    @DisplayName("should return Failure when function returns Failure")
    void shouldReturnFailureWhenFunctionReturnsFailure() {
      Person person = new Person("Jack", -10);
      Exception error = new IllegalArgumentException("Age cannot be negative");
      Try<Person> result =
          modifyTry(ageLens, age -> age >= 0 ? Try.success(age) : Try.failure(error), person);
      assertThat(result.isFailure()).isTrue();
      Throwable actual =
          result.fold(
              success -> {
                throw new AssertionError();
              },
              failure -> failure);
      assertThat(actual).isEqualTo(error);
    }
  }

  @Nested
  @DisplayName("setIfValid() - Set value with validation")
  class SetIfValidMethod {
    @Test
    @DisplayName("should set value when validation passes")
    void shouldSetWhenValid() {
      Person person = new Person("Kate", 30);
      Either<String, Person> result =
          setIfValid(
              nameLens,
              name ->
                  name.matches("[A-Z][a-z]+")
                      ? Either.right(name)
                      : Either.left("Invalid name format"),
              "Lisa",
              person);
      assertThat(result.isRight()).isTrue();
      assertThat(result.getRight().name).isEqualTo("Lisa");
      assertThat(result.getRight().age).isEqualTo(30);
    }

    @Test
    @DisplayName("should return error when validation fails")
    void shouldReturnErrorWhenInvalid() {
      Person person = new Person("Mike", 30);
      Either<String, Person> result =
          setIfValid(
              nameLens,
              name ->
                  name.matches("[A-Z][a-z]+")
                      ? Either.right(name)
                      : Either.left("Invalid name format"),
              "invalid123",
              person);
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft()).isEqualTo("Invalid name format");
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {
    @Test
    @DisplayName("should chain getMaybe with orElse for null-safe access")
    void shouldChainGetMaybeWithOrElse() {
      Person person1 = new Person("Nancy", 28);
      Person person2 = new Person(null, 28);

      String name1 = getMaybe(nameLens, person1).orElse("Unknown");
      String name2 = getMaybe(nameLens, person2).orElse("Unknown");

      assertThat(name1).isEqualTo("Nancy");
      assertThat(name2).isEqualTo("Unknown");
    }

    @Test
    @DisplayName("should compose multiple validations")
    void shouldComposeMultipleValidations() {
      Person person = new Person("Oscar", 25);

      // Validate and modify both fields
      Either<String, Person> nameUpdate =
          modifyEither(
              nameLens,
              name ->
                  name.length() >= 3
                      ? Either.right(name.toUpperCase())
                      : Either.left("Name too short"),
              person);

      Either<String, Person> finalUpdate =
          nameUpdate.flatMap(
              p ->
                  modifyEither(
                      ageLens,
                      age -> age >= 18 ? Either.right(age + 1) : Either.left("Too young"),
                      p));

      assertThat(finalUpdate.isRight()).isTrue();
      assertThat(finalUpdate.getRight().name).isEqualTo("OSCAR");
      assertThat(finalUpdate.getRight().age).isEqualTo(26);
    }

    @Test
    @DisplayName("should handle validation failure in composition")
    void shouldHandleValidationFailureInComposition() {
      Person person = new Person("Al", 15);

      Either<String, Person> nameUpdate =
          modifyEither(
              nameLens,
              name ->
                  name.length() >= 3
                      ? Either.right(name.toUpperCase())
                      : Either.left("Name too short"),
              person);

      assertThat(nameUpdate.isLeft()).isTrue();
      assertThat(nameUpdate.getLeft()).isEqualTo("Name too short");
    }
  }
}
