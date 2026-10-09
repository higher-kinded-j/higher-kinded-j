// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.focus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;
import static org.higherkindedj.hkt.assertions.IdAssert.assertThatId;
import static org.higherkindedj.hkt.assertions.TryAssert.assertThatTry;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.IdPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.TryPath;
import org.higherkindedj.optics.Lens;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for FocusPath bridge methods to EffectPath types.
 *
 * <p>Tests cover the bridge methods: toMaybePath, toEitherPath, toTryPath, and toIdPath, and the
 * forms of toEitherPath and toTryPath that say what a null focus becomes.
 */
@DisplayName("FocusPath Bridge Methods Tests")
class FocusPathBridgeTest {

  // Test Data Structures
  record Street(String name) {}

  record Address(Street street, String city) {}

  record Person(String name, Address address) {}

  record Profile(@Nullable String nickname) {}

  private static final FocusPath<Profile, @Nullable String> NICKNAME =
      FocusPath.of(Lens.of(Profile::nickname, (_, n) -> new Profile(n)));

  private static final Profile NAMED = new Profile("Al");

  private static final Profile UNNAMED = new Profile(null);

  // Lenses
  private Lens<Street, String> streetNameLens;
  private Lens<Address, Street> addressStreetLens;
  private Lens<Person, String> personNameLens;
  private Lens<Person, Address> personAddressLens;

  @BeforeEach
  void setUp() {
    streetNameLens = Lens.of(Street::name, (s, n) -> new Street(n));
    addressStreetLens = Lens.of(Address::street, (a, s) -> new Address(s, a.city()));
    personNameLens = Lens.of(Person::name, (p, n) -> new Person(n, p.address()));
    personAddressLens = Lens.of(Person::address, (p, a) -> new Person(p.name(), a));
  }

  @Nested
  @DisplayName("toMaybePath()")
  class ToMaybePathTests {

    @Test
    @DisplayName("extracts value and wraps in Just")
    void extractsValueAndWrapsInJust() {
      FocusPath<Street, String> path = FocusPath.of(streetNameLens);
      Street street = new Street("Main St");

      MaybePath<String> result = path.toMaybePath(street);

      assertThat(result.run().isJust()).isTrue();
      assertThat(result.run().get()).isEqualTo("Main St");
    }

    @Test
    @DisplayName("preserves value through composition")
    void preservesValueThroughComposition() {
      FocusPath<Person, String> namePath = FocusPath.of(personNameLens);
      Person person = new Person("Alice", new Address(new Street("Oak Ave"), "London"));

      MaybePath<String> result = namePath.toMaybePath(person);

      assertThat(result.run().isJust()).isTrue();
      assertThat(result.run().get()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("allows chaining with effect operations")
    void allowsChainingWithEffectOperations() {
      FocusPath<Street, String> path = FocusPath.of(streetNameLens);
      Street street = new Street("main st");

      String result =
          path.toMaybePath(street)
              .map(String::toUpperCase)
              .filter(name -> name.startsWith("MAIN"))
              .getOrElse("DEFAULT");

      assertThat(result).isEqualTo("MAIN ST");
    }

    @Test
    @DisplayName("deep path extracts nested value")
    void deepPathExtractsNestedValue() {
      FocusPath<Person, String> streetNamePath =
          FocusPath.of(personAddressLens).via(addressStreetLens).via(streetNameLens);
      Person person = new Person("Bob", new Address(new Street("High St"), "Oxford"));

      MaybePath<String> result = streetNamePath.toMaybePath(person);

      assertThat(result.run().get()).isEqualTo("High St");
    }
  }

  @Nested
  @DisplayName("toEitherPath()")
  class ToEitherPathTests {

    @Test
    @DisplayName("extracts value and wraps in Right")
    void extractsValueAndWrapsInRight() {
      FocusPath<Street, String> path = FocusPath.of(streetNameLens);
      Street street = new Street("Broadway");

      EitherPath<String, String> result = path.toEitherPath(street);

      assertThat(result.run().isRight()).isTrue();
      assertThat(result.run().getRight()).isEqualTo("Broadway");
    }

    @Test
    @DisplayName("allows chaining with via for validation")
    void allowsChainingWithViaForValidation() {
      FocusPath<Street, String> path = FocusPath.of(streetNameLens);
      Street validStreet = new Street("Main St");
      Street invalidStreet = new Street("");

      // Validate non-empty
      EitherPath<String, String> validResult =
          path.<String>toEitherPath(validStreet)
              .via(name -> name.isEmpty() ? Path.left("Name cannot be empty") : Path.right(name));

      EitherPath<String, String> invalidResult =
          path.<String>toEitherPath(invalidStreet)
              .via(name -> name.isEmpty() ? Path.left("Name cannot be empty") : Path.right(name));

      assertThat(validResult.run().isRight()).isTrue();
      assertThat(invalidResult.run().isLeft()).isTrue();
      assertThat(invalidResult.run().getLeft()).isEqualTo("Name cannot be empty");
    }

    @Test
    @DisplayName("supports error recovery")
    void supportsErrorRecovery() {
      FocusPath<Street, String> path = FocusPath.of(streetNameLens);
      Street street = new Street("");

      String result =
          path.<String>toEitherPath(street)
              .via(name -> name.isEmpty() ? Path.left("Error") : Path.right(name))
              .recover(err -> "Unknown Street")
              .run()
              .getRight();

      assertThat(result).isEqualTo("Unknown Street");
    }
  }

  @Nested
  @DisplayName("toTryPath()")
  class ToTryPathTests {

    @Test
    @DisplayName("extracts value and wraps in Success")
    void extractsValueAndWrapsInSuccess() {
      FocusPath<Street, String> path = FocusPath.of(streetNameLens);
      Street street = new Street("Elm St");

      TryPath<String> result = path.toTryPath(street);

      assertThat(result.run().isSuccess()).isTrue();
      result
          .run()
          .foldFailureFirst(
              ex -> null,
              value -> {
                assertThat(value).isEqualTo("Elm St");
                return null;
              });
    }

    @Test
    @DisplayName("allows chaining with potentially failing operations")
    void allowsChainingWithPotentiallyFailingOperations() {
      FocusPath<Street, String> path = FocusPath.of(streetNameLens);
      Street street = new Street("123");

      TryPath<Integer> result =
          path.toTryPath(street).via(name -> Path.tryOf(() -> Integer.parseInt(name)));

      assertThat(result.run().isSuccess()).isTrue();
      result
          .run()
          .foldFailureFirst(
              ex -> null,
              value -> {
                assertThat(value).isEqualTo(123);
                return null;
              });
    }

    @Test
    @DisplayName("handles exceptions gracefully")
    void handlesExceptionsGracefully() {
      FocusPath<Street, String> path = FocusPath.of(streetNameLens);
      Street street = new Street("not-a-number");

      TryPath<Integer> result =
          path.toTryPath(street).via(name -> Path.tryOf(() -> Integer.parseInt(name)));

      assertThat(result.run().isFailure()).isTrue();
      result
          .run()
          .foldFailureFirst(
              ex -> {
                assertThat(ex).isInstanceOf(NumberFormatException.class);
                return null;
              },
              value -> null);
    }

    @Test
    @DisplayName("supports exception recovery")
    void supportsExceptionRecovery() {
      FocusPath<Street, String> path = FocusPath.of(streetNameLens);
      Street street = new Street("not-a-number");

      Integer result =
          path.toTryPath(street)
              .via(name -> Path.tryOf(() -> Integer.parseInt(name)))
              .recover(ex -> -1)
              .getOrElse(0);

      assertThat(result).isEqualTo(-1);
    }
  }

  @Nested
  @DisplayName("toIdPath()")
  class ToIdPathTests {

    @Test
    @DisplayName("extracts value and wraps in Id")
    void extractsValueAndWrapsInId() {
      FocusPath<Street, String> path = FocusPath.of(streetNameLens);
      Street street = new Street("Park Ave");

      IdPath<String> result = path.toIdPath(street);

      assertThat(result.run().value()).isEqualTo("Park Ave");
    }

    @Test
    @DisplayName("allows transformation with map")
    void allowsTransformationWithMap() {
      FocusPath<Street, String> path = FocusPath.of(streetNameLens);
      Street street = new Street("park ave");

      IdPath<String> result = path.toIdPath(street).map(String::toUpperCase);

      assertThat(result.run().value()).isEqualTo("PARK AVE");
    }
  }

  @Nested
  @DisplayName("toEitherPath() and toTryPath() over a focus that may be null")
  class OverANullableFocus {

    @Test
    @DisplayName("toEitherPath gives a Right for a present focus and the error for a null one")
    void toEitherPathGivesTheErrorForANullFocus() {
      EitherPath<String, String> named = NICKNAME.toEitherPath(NAMED, "no nickname");
      EitherPath<String, String> unnamed = NICKNAME.toEitherPath(UNNAMED, "no nickname");

      assertThatEither(named.run()).hasRight("Al");
      assertThatEither(unnamed.run()).hasLeft("no nickname");
    }

    @Test
    @DisplayName("toEitherPath rejects a null error even when the focus is present")
    void toEitherPathRejectsANullError() {
      assertThatNullPointerException()
          .isThrownBy(() -> NICKNAME.toEitherPath(NAMED, (String) null))
          .withMessage("errorIfNull must not be null");
    }

    @Test
    @DisplayName("toEitherPath calls the supplier only for a null focus, and once")
    void toEitherPathCallsTheSupplierOnlyForANullFocus() {
      AtomicInteger calls = new AtomicInteger();
      Supplier<String> error =
          () -> {
            calls.incrementAndGet();
            return "no nickname";
          };

      EitherPath<String, String> named = NICKNAME.toEitherPath(NAMED, error);
      assertThat(calls).hasValue(0);
      EitherPath<String, String> unnamed = NICKNAME.toEitherPath(UNNAMED, error);

      assertThatEither(named.run()).hasRight("Al");
      assertThatEither(unnamed.run()).hasLeft("no nickname");
      assertThat(calls).hasValue(1);
    }

    @Test
    @DisplayName("toEitherPath rejects a null supplier, and a supplier that returns null")
    void toEitherPathRejectsANullSuppliedError() {
      assertThatNullPointerException()
          .isThrownBy(() -> NICKNAME.toEitherPath(NAMED, (Supplier<String>) null))
          .withMessage("errorSupplier must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> NICKNAME.<String>toEitherPath(UNNAMED, () -> null))
          .withMessage("errorSupplier must not return null");
    }

    @Test
    @DisplayName("toTryPath gives a Success for a present focus and the exception for a null one")
    void toTryPathGivesTheExceptionForANullFocus() {
      AtomicInteger calls = new AtomicInteger();
      IllegalStateException missing = new IllegalStateException("no nickname");
      Supplier<Throwable> exception =
          () -> {
            calls.incrementAndGet();
            return missing;
          };

      TryPath<String> named = NICKNAME.toTryPath(NAMED, exception);
      assertThat(calls).hasValue(0);
      TryPath<String> unnamed = NICKNAME.toTryPath(UNNAMED, exception);

      assertThatTry(named.run()).hasValue("Al");
      assertThatTry(unnamed.run()).hasException(missing);
      assertThat(calls).hasValue(1);
    }

    @Test
    @DisplayName("toTryPath rejects a null supplier, and a supplier that returns null")
    void toTryPathRejectsANullSuppliedException() {
      assertThatNullPointerException()
          .isThrownBy(() -> NICKNAME.toTryPath(NAMED, null))
          .withMessage("exceptionIfNull must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> NICKNAME.toTryPath(UNNAMED, () -> null))
          .withMessage("exceptionIfNull must not return null");
    }

    @Test
    @DisplayName("the one-argument forms hold a null focus as it is")
    void oneArgumentFormsHoldTheFocus() {
      assertThatEither(NICKNAME.<String>toEitherPath(UNNAMED).run()).hasRightNull();
      assertThatId(NICKNAME.toIdPath(UNNAMED).run()).hasNullValue();
    }
  }

  @Nested
  @DisplayName("Integration Scenarios")
  class IntegrationScenarios {

    @Test
    @DisplayName("combines multiple paths with effect composition")
    void combinesMultiplePathsWithEffectComposition() {
      FocusPath<Person, String> namePath = FocusPath.of(personNameLens);
      FocusPath<Person, String> streetPath =
          FocusPath.of(personAddressLens).via(addressStreetLens).via(streetNameLens);

      Person person = new Person("Alice", new Address(new Street("Main St"), "London"));

      // Combine name and street using zipWith
      String combined =
          namePath
              .toMaybePath(person)
              .zipWith(
                  streetPath.toMaybePath(person), (name, street) -> name + " lives on " + street)
              .getOrElse("Unknown");

      assertThat(combined).isEqualTo("Alice lives on Main St");
    }

    @Test
    @DisplayName("chains extract -> validate -> transform pattern")
    void chainsExtractValidateTransformPattern() {
      FocusPath<Street, String> path = FocusPath.of(streetNameLens);
      Street street = new Street("valid-name");

      EitherPath<String, Integer> result =
          path.<String>toEitherPath(street)
              .via(name -> name.length() < 3 ? Path.left("Name too short") : Path.right(name))
              .map(String::toUpperCase)
              .map(String::length);

      assertThat(result.run().isRight()).isTrue();
      assertThat(result.run().getRight()).isEqualTo(10); // "VALID-NAME".length()
    }
  }
}
