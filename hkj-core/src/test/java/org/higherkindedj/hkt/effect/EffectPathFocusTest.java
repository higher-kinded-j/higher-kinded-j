// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.FocusPaths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for EffectPath focus() bridge methods.
 *
 * <p>Tests cover the focus() methods on MaybePath, EitherPath, and TryPath that allow structural
 * navigation within effect contexts using FocusPath and AffinePath.
 */
@DisplayName("EffectPath focus() Bridge Methods Tests")
class EffectPathFocusTest {

  // Test Data Structures
  record User(String name, Optional<String> email) {}

  record Address(String street, String city) {}

  record Person(String name, Address address) {}

  // Lenses and Affines
  private Lens<User, String> userNameLens;
  private Lens<User, Optional<String>> userEmailLens;
  private Affine<Optional<String>, String> optionalSome;
  private Lens<Person, String> personNameLens;
  private Lens<Person, Address> personAddressLens;
  private Lens<Address, String> addressStreetLens;

  @BeforeEach
  void setUp() {
    userNameLens = Lens.of(User::name, (u, n) -> new User(n, u.email()));
    userEmailLens = Lens.of(User::email, (u, e) -> new User(u.name(), e));
    optionalSome = FocusPaths.optionalSome();
    personNameLens = Lens.of(Person::name, (p, n) -> new Person(n, p.address()));
    personAddressLens = Lens.of(Person::address, (p, a) -> new Person(p.name(), a));
    addressStreetLens = Lens.of(Address::street, (a, s) -> new Address(s, a.city()));
  }

  @Nested
  @DisplayName("MaybePath.focus()")
  class MaybePathFocusTests {

    @Nested
    @DisplayName("focus(FocusPath)")
    class FocusPathOnMaybePathTests {

      @Test
      @DisplayName("extracts focused value when Just")
      void extractsFocusedValueWhenJust() {
        FocusPath<User, String> namePath = FocusPath.of(userNameLens);
        User user = new User("Alice", Optional.of("alice@example.com"));
        MaybePath<User> userPath = Path.just(user);

        MaybePath<String> result = userPath.focus(namePath);

        assertThat(result.run().isJust()).isTrue();
        assertThat(result.run().get()).isEqualTo("Alice");
      }

      @Test
      @DisplayName("returns Nothing when MaybePath is Nothing")
      void returnsNothingWhenMaybePathIsNothing() {
        FocusPath<User, String> namePath = FocusPath.of(userNameLens);
        MaybePath<User> userPath = Path.nothing();

        MaybePath<String> result = userPath.focus(namePath);

        assertThat(result.run().isNothing()).isTrue();
      }

      @Test
      @DisplayName("supports deep navigation")
      void supportsDeepNavigation() {
        FocusPath<Person, String> streetPath =
            FocusPath.of(personAddressLens).via(addressStreetLens);
        Person person = new Person("Bob", new Address("Main St", "London"));
        MaybePath<Person> personPath = Path.just(person);

        MaybePath<String> result = personPath.focus(streetPath);

        assertThat(result.run().get()).isEqualTo("Main St");
      }

      @Test
      @DisplayName("validates null path")
      void validatesNullPath() {
        MaybePath<User> userPath = Path.just(new User("Alice", Optional.empty()));

        assertThatNullPointerException()
            .isThrownBy(() -> userPath.focus((FocusPath<User, String>) null))
            .withMessageContaining("path must not be null");
      }
    }

    @Nested
    @DisplayName("focus(AffinePath)")
    class AffinePathOnMaybePathTests {

      @Test
      @DisplayName("extracts value when both MaybePath and AffinePath succeed")
      void extractsValueWhenBothSucceed() {
        AffinePath<User, String> emailPath = FocusPath.of(userEmailLens).via(optionalSome);
        User user = new User("Alice", Optional.of("alice@example.com"));
        MaybePath<User> userPath = Path.just(user);

        MaybePath<String> result = userPath.focus(emailPath);

        assertThat(result.run().isJust()).isTrue();
        assertThat(result.run().get()).isEqualTo("alice@example.com");
      }

      @Test
      @DisplayName("returns Nothing when MaybePath is Nothing")
      void returnsNothingWhenMaybePathIsNothing() {
        AffinePath<User, String> emailPath = FocusPath.of(userEmailLens).via(optionalSome);
        MaybePath<User> userPath = Path.nothing();

        MaybePath<String> result = userPath.focus(emailPath);

        assertThat(result.run().isNothing()).isTrue();
      }

      @Test
      @DisplayName("returns Nothing when AffinePath doesn't match")
      void returnsNothingWhenAffinePathDoesntMatch() {
        AffinePath<User, String> emailPath = FocusPath.of(userEmailLens).via(optionalSome);
        User user = new User("Bob", Optional.empty());
        MaybePath<User> userPath = Path.just(user);

        MaybePath<String> result = userPath.focus(emailPath);

        assertThat(result.run().isNothing()).isTrue();
      }

      @Test
      @DisplayName("validates null path")
      void validatesNullPath() {
        MaybePath<User> userPath = Path.just(new User("Alice", Optional.empty()));

        assertThatNullPointerException()
            .isThrownBy(() -> userPath.focus((AffinePath<User, String>) null))
            .withMessageContaining("path must not be null");
      }
    }
  }

  @Nested
  @DisplayName("EitherPath.focus()")
  class EitherPathFocusTests {

    @Nested
    @DisplayName("focus(FocusPath)")
    class FocusPathOnEitherPathTests {

      @Test
      @DisplayName("extracts focused value when Right")
      void extractsFocusedValueWhenRight() {
        FocusPath<User, String> namePath = FocusPath.of(userNameLens);
        User user = new User("Alice", Optional.of("alice@example.com"));
        EitherPath<String, User> userPath = Path.right(user);

        EitherPath<String, String> result = userPath.focus(namePath);

        assertThat(result.run().isRight()).isTrue();
        assertThat(result.run().getRight()).isEqualTo("Alice");
      }

      @Test
      @DisplayName("preserves Left when EitherPath is Left")
      void preservesLeftWhenEitherPathIsLeft() {
        FocusPath<User, String> namePath = FocusPath.of(userNameLens);
        EitherPath<String, User> userPath = Path.left("User not found");

        EitherPath<String, String> result = userPath.focus(namePath);

        assertThat(result.run().isLeft()).isTrue();
        assertThat(result.run().getLeft()).isEqualTo("User not found");
      }

      @Test
      @DisplayName("supports chaining with further operations")
      void supportsChainingWithFurtherOperations() {
        FocusPath<User, String> namePath = FocusPath.of(userNameLens);
        User user = new User("alice", Optional.empty());
        EitherPath<String, User> userPath = Path.right(user);

        String result = userPath.focus(namePath).map(String::toUpperCase).getOrElse("UNKNOWN");

        assertThat(result).isEqualTo("ALICE");
      }

      @Test
      @DisplayName("validates null path")
      void validatesNullPath() {
        EitherPath<String, User> userPath = Path.right(new User("Alice", Optional.empty()));

        assertThatNullPointerException()
            .isThrownBy(() -> userPath.focus((FocusPath<User, String>) null))
            .withMessageContaining("path must not be null");
      }
    }

    @Nested
    @DisplayName("focus(AffinePath, error)")
    class AffinePathOnEitherPathTests {

      @Test
      @DisplayName("extracts value when both EitherPath and AffinePath succeed")
      void extractsValueWhenBothSucceed() {
        AffinePath<User, String> emailPath = FocusPath.of(userEmailLens).via(optionalSome);
        User user = new User("Alice", Optional.of("alice@example.com"));
        EitherPath<String, User> userPath = Path.right(user);

        EitherPath<String, String> result = userPath.focus(emailPath, "Email not found");

        assertThat(result.run().isRight()).isTrue();
        assertThat(result.run().getRight()).isEqualTo("alice@example.com");
      }

      @Test
      @DisplayName("preserves original Left when EitherPath is Left")
      void preservesOriginalLeftWhenEitherPathIsLeft() {
        AffinePath<User, String> emailPath = FocusPath.of(userEmailLens).via(optionalSome);
        EitherPath<String, User> userPath = Path.left("User not found");

        EitherPath<String, String> result = userPath.focus(emailPath, "Email not found");

        assertThat(result.run().isLeft()).isTrue();
        assertThat(result.run().getLeft()).isEqualTo("User not found");
      }

      @Test
      @DisplayName("returns Left with error when AffinePath doesn't match")
      void returnsLeftWithErrorWhenAffinePathDoesntMatch() {
        AffinePath<User, String> emailPath = FocusPath.of(userEmailLens).via(optionalSome);
        User user = new User("Bob", Optional.empty());
        EitherPath<String, User> userPath = Path.right(user);

        EitherPath<String, String> result = userPath.focus(emailPath, "Email not configured");

        assertThat(result.run().isLeft()).isTrue();
        assertThat(result.run().getLeft()).isEqualTo("Email not configured");
      }

      @Test
      @DisplayName("validates null path")
      void validatesNullPath() {
        EitherPath<String, User> userPath = Path.right(new User("Alice", Optional.empty()));

        assertThatNullPointerException()
            .isThrownBy(() -> userPath.focus(null, "error"))
            .withMessageContaining("path must not be null");
      }
    }
  }

  @Nested
  @DisplayName("TryPath.focus()")
  class TryPathFocusTests {

    @Nested
    @DisplayName("focus(FocusPath)")
    class FocusPathOnTryPathTests {

      @Test
      @DisplayName("extracts focused value when Success")
      void extractsFocusedValueWhenSuccess() {
        FocusPath<User, String> namePath = FocusPath.of(userNameLens);
        User user = new User("Alice", Optional.empty());
        TryPath<User> userPath = Path.success(user);

        TryPath<String> result = userPath.focus(namePath);

        assertThat(result.run().isSuccess()).isTrue();
        result
            .run()
            .fold(
                value -> {
                  assertThat(value).isEqualTo("Alice");
                  return null;
                },
                ex -> null);
      }

      @Test
      @DisplayName("preserves Failure when TryPath is Failure")
      void preservesFailureWhenTryPathIsFailure() {
        FocusPath<User, String> namePath = FocusPath.of(userNameLens);
        RuntimeException exception = new RuntimeException("Failed to load user");
        TryPath<User> userPath = Path.failure(exception);

        TryPath<String> result = userPath.focus(namePath);

        assertThat(result.run().isFailure()).isTrue();
        result
            .run()
            .fold(
                value -> null,
                ex -> {
                  assertThat(ex).isSameAs(exception);
                  return null;
                });
      }

      @Test
      @DisplayName("supports chaining with further operations")
      void supportsChainingWithFurtherOperations() {
        FocusPath<User, String> namePath = FocusPath.of(userNameLens);
        User user = new User("alice", Optional.empty());
        TryPath<User> userPath = Path.success(user);

        String result = userPath.focus(namePath).map(String::toUpperCase).getOrElse("UNKNOWN");

        assertThat(result).isEqualTo("ALICE");
      }

      @Test
      @DisplayName("validates null path")
      void validatesNullPath() {
        TryPath<User> userPath = Path.success(new User("Alice", Optional.empty()));

        assertThatNullPointerException()
            .isThrownBy(() -> userPath.focus((FocusPath<User, String>) null))
            .withMessageContaining("path must not be null");
      }
    }

    @Nested
    @DisplayName("focus(AffinePath, exceptionSupplier)")
    class AffinePathOnTryPathTests {

      @Test
      @DisplayName("extracts value when both TryPath and AffinePath succeed")
      void extractsValueWhenBothSucceed() {
        AffinePath<User, String> emailPath = FocusPath.of(userEmailLens).via(optionalSome);
        User user = new User("Alice", Optional.of("alice@example.com"));
        TryPath<User> userPath = Path.success(user);

        TryPath<String> result =
            userPath.focus(emailPath, () -> new IllegalStateException("Email not found"));

        assertThat(result.run().isSuccess()).isTrue();
        result
            .run()
            .fold(
                value -> {
                  assertThat(value).isEqualTo("alice@example.com");
                  return null;
                },
                ex -> null);
      }

      @Test
      @DisplayName("preserves original Failure when TryPath is Failure")
      void preservesOriginalFailureWhenTryPathIsFailure() {
        AffinePath<User, String> emailPath = FocusPath.of(userEmailLens).via(optionalSome);
        RuntimeException originalException = new RuntimeException("Load failed");
        TryPath<User> userPath = Path.failure(originalException);

        TryPath<String> result =
            userPath.focus(emailPath, () -> new IllegalStateException("Email not found"));

        assertThat(result.run().isFailure()).isTrue();
        result
            .run()
            .fold(
                value -> null,
                ex -> {
                  assertThat(ex).isSameAs(originalException);
                  return null;
                });
      }

      @Test
      @DisplayName("returns Failure with supplied exception when AffinePath doesn't match")
      void returnsFailureWithSuppliedException() {
        AffinePath<User, String> emailPath = FocusPath.of(userEmailLens).via(optionalSome);
        User user = new User("Bob", Optional.empty());
        TryPath<User> userPath = Path.success(user);

        TryPath<String> result =
            userPath.focus(emailPath, () -> new IllegalStateException("Email not configured"));

        assertThat(result.run().isFailure()).isTrue();
        result
            .run()
            .fold(
                value -> null,
                ex -> {
                  assertThat(ex).isInstanceOf(IllegalStateException.class);
                  assertThat(ex.getMessage()).isEqualTo("Email not configured");
                  return null;
                });
      }

      @Test
      @DisplayName("validates null path")
      void validatesNullPath() {
        TryPath<User> userPath = Path.success(new User("Alice", Optional.empty()));

        assertThatNullPointerException()
            .isThrownBy(() -> userPath.focus(null, IllegalStateException::new))
            .withMessageContaining("path must not be null");
      }

      @Test
      @DisplayName("validates null exception supplier")
      void validatesNullExceptionSupplier() {
        AffinePath<User, String> emailPath = FocusPath.of(userEmailLens).via(optionalSome);
        TryPath<User> userPath = Path.success(new User("Alice", Optional.empty()));

        assertThatNullPointerException()
            .isThrownBy(() -> userPath.focus(emailPath, null))
            .withMessageContaining("exceptionIfAbsent must not be null");
      }
    }
  }

  @Nested
  @DisplayName("ValidationPath.focus()")
  class ValidationPathFocusTests {

    @Nested
    @DisplayName("focus(FocusPath)")
    class FocusPathOnValidationPathTests {

      @Test
      @DisplayName("extracts focused value when Valid")
      void extractsFocusedValueWhenValid() {
        FocusPath<User, String> namePath = FocusPath.of(userNameLens);
        User user = new User("Alice", Optional.empty());
        ValidationPath<List<String>, User> userPath = Path.valid(user, Semigroups.list());

        ValidationPath<List<String>, String> result = userPath.focus(namePath);

        assertThat(result.run().isValid()).isTrue();
        assertThat(result.run().get()).isEqualTo("Alice");
      }

      @Test
      @DisplayName("preserves Invalid when ValidationPath is Invalid")
      void preservesInvalidWhenValidationPathIsInvalid() {
        FocusPath<User, String> namePath = FocusPath.of(userNameLens);
        List<String> errors = List.of("Error 1", "Error 2");
        ValidationPath<List<String>, User> userPath = Path.invalid(errors, Semigroups.list());

        ValidationPath<List<String>, String> result = userPath.focus(namePath);

        assertThat(result.run().isInvalid()).isTrue();
        assertThat(result.run().getError()).isEqualTo(errors);
      }

      @Test
      @DisplayName("validates null path")
      void validatesNullPath() {
        ValidationPath<List<String>, User> userPath =
            Path.valid(new User("Alice", Optional.empty()), Semigroups.list());

        assertThatNullPointerException()
            .isThrownBy(() -> userPath.focus((FocusPath<User, String>) null))
            .withMessageContaining("path must not be null");
      }
    }

    @Nested
    @DisplayName("focus(AffinePath, E)")
    class AffinePathOnValidationPathTests {

      @Test
      @DisplayName("extracts focused value when AffinePath matches")
      void extractsFocusedValueWhenAffinePathMatches() {
        AffinePath<User, String> emailPath = FocusPath.of(userEmailLens).via(optionalSome);
        User user = new User("Alice", Optional.of("alice@example.com"));
        ValidationPath<List<String>, User> userPath = Path.valid(user, Semigroups.list());

        ValidationPath<List<String>, String> result =
            userPath.focus(emailPath, List.of("Email not found"));

        assertThat(result.run().isValid()).isTrue();
        assertThat(result.run().get()).isEqualTo("alice@example.com");
      }

      @Test
      @DisplayName("returns Invalid with error when AffinePath doesn't match")
      void returnsInvalidWhenAffinePathDoesNotMatch() {
        AffinePath<User, String> emailPath = FocusPath.of(userEmailLens).via(optionalSome);
        User user = new User("Bob", Optional.empty());
        ValidationPath<List<String>, User> userPath = Path.valid(user, Semigroups.list());

        ValidationPath<List<String>, String> result =
            userPath.focus(emailPath, List.of("Email not configured"));

        assertThat(result.run().isInvalid()).isTrue();
        assertThat(result.run().getError()).isEqualTo(List.of("Email not configured"));
      }

      @Test
      @DisplayName("preserves original Invalid when ValidationPath is Invalid")
      void preservesOriginalInvalidWhenValidationPathIsInvalid() {
        AffinePath<User, String> emailPath = FocusPath.of(userEmailLens).via(optionalSome);
        List<String> originalErrors = List.of("Load failed");
        ValidationPath<List<String>, User> userPath =
            Path.invalid(originalErrors, Semigroups.list());

        ValidationPath<List<String>, String> result =
            userPath.focus(emailPath, List.of("Email not found"));

        assertThat(result.run().isInvalid()).isTrue();
        assertThat(result.run().getError()).isEqualTo(originalErrors);
      }

      @Test
      @DisplayName("validates null path")
      void validatesNullPath() {
        ValidationPath<List<String>, User> userPath =
            Path.valid(new User("Alice", Optional.empty()), Semigroups.list());

        assertThatNullPointerException()
            .isThrownBy(() -> userPath.focus((AffinePath<User, String>) null, List.of("Error")))
            .withMessageContaining("path must not be null");
      }

      @Test
      @DisplayName("validates null errorIfAbsent")
      void validatesNullErrorIfAbsent() {
        AffinePath<User, String> emailPath = FocusPath.of(userEmailLens).via(optionalSome);
        ValidationPath<List<String>, User> userPath =
            Path.valid(new User("Alice", Optional.empty()), Semigroups.list());

        assertThatNullPointerException()
            .isThrownBy(() -> userPath.focus(emailPath, null))
            .withMessageContaining("errorIfAbsent must not be null");
      }
    }
  }

  @Nested
  @DisplayName("IOPath.focus()")
  class IOPathFocusTests {

    @Nested
    @DisplayName("focus(FocusPath)")
    class FocusPathOnIOPathTests {

      @Test
      @DisplayName("extracts focused value from IO computation")
      void extractsFocusedValueFromIOComputation() {
        FocusPath<User, String> namePath = FocusPath.of(userNameLens);
        User user = new User("Alice", Optional.empty());
        IOPath<User> userPath = Path.ioPure(user);

        IOPath<String> result = userPath.focus(namePath);

        assertThat(result.unsafeRun()).isEqualTo("Alice");
      }

      @Test
      @DisplayName("propagates exception from IO computation")
      void propagatesExceptionFromIOComputation() {
        FocusPath<User, String> namePath = FocusPath.of(userNameLens);
        RuntimeException exception = new RuntimeException("IO failed");
        IOPath<User> userPath =
            Path.io(
                () -> {
                  throw exception;
                });

        IOPath<String> result = userPath.focus(namePath);

        assertThatThrownBy(result::unsafeRun).isSameAs(exception);
      }

      @Test
      @DisplayName("validates null path")
      void validatesNullPath() {
        IOPath<User> userPath = Path.ioPure(new User("Alice", Optional.empty()));

        assertThatNullPointerException()
            .isThrownBy(() -> userPath.focus((FocusPath<User, String>) null))
            .withMessageContaining("path must not be null");
      }
    }

    @Nested
    @DisplayName("focus(AffinePath, Supplier)")
    class AffinePathOnIOPathTests {

      @Test
      @DisplayName("extracts focused value when AffinePath matches")
      void extractsFocusedValueWhenAffinePathMatches() {
        AffinePath<User, String> emailPath = FocusPath.of(userEmailLens).via(optionalSome);
        User user = new User("Alice", Optional.of("alice@example.com"));
        IOPath<User> userPath = Path.ioPure(user);

        IOPath<String> result =
            userPath.focus(emailPath, () -> new IllegalStateException("Email not found"));

        assertThat(result.unsafeRun()).isEqualTo("alice@example.com");
      }

      @Test
      @DisplayName("throws supplied exception when AffinePath doesn't match")
      void throwsSuppliedException() {
        AffinePath<User, String> emailPath = FocusPath.of(userEmailLens).via(optionalSome);
        User user = new User("Bob", Optional.empty());
        IOPath<User> userPath = Path.ioPure(user);

        IOPath<String> result =
            userPath.focus(emailPath, () -> new IllegalStateException("Email not configured"));

        assertThatThrownBy(result::unsafeRun)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Email not configured");
      }

      @Test
      @DisplayName("propagates exception from IO computation before focus")
      void propagatesExceptionFromIOComputation() {
        AffinePath<User, String> emailPath = FocusPath.of(userEmailLens).via(optionalSome);
        RuntimeException exception = new RuntimeException("IO failed");
        IOPath<User> userPath =
            Path.io(
                () -> {
                  throw exception;
                });

        IOPath<String> result =
            userPath.focus(emailPath, () -> new IllegalStateException("Email not found"));

        assertThatThrownBy(result::unsafeRun).isSameAs(exception);
      }

      @Test
      @DisplayName("validates null path")
      void validatesNullPath() {
        IOPath<User> userPath = Path.ioPure(new User("Alice", Optional.empty()));

        assertThatNullPointerException()
            .isThrownBy(
                () ->
                    userPath.focus(
                        (AffinePath<User, String>) null, () -> new IllegalStateException("Error")))
            .withMessageContaining("path must not be null");
      }

      @Test
      @DisplayName("validates null exceptionIfAbsent")
      void validatesNullExceptionIfAbsent() {
        AffinePath<User, String> emailPath = FocusPath.of(userEmailLens).via(optionalSome);
        IOPath<User> userPath = Path.ioPure(new User("Alice", Optional.empty()));

        assertThatNullPointerException()
            .isThrownBy(() -> userPath.focus(emailPath, null))
            .withMessageContaining("exceptionIfAbsent must not be null");
      }
    }
  }

  @Nested
  @DisplayName("Integration Scenarios")
  class IntegrationScenarios {

    @Test
    @DisplayName("chains multiple focus operations")
    void chainsMultipleFocusOperations() {
      FocusPath<Person, Address> addressPath = FocusPath.of(personAddressLens);
      FocusPath<Address, String> streetPath = FocusPath.of(addressStreetLens);

      Person person = new Person("Alice", new Address("Oak Ave", "Paris"));

      String result =
          Path.<String, Person>right(person)
              .focus(addressPath)
              .focus(streetPath)
              .map(String::toUpperCase)
              .getOrElse("UNKNOWN");

      assertThat(result).isEqualTo("OAK AVE");
    }

    @Test
    @DisplayName("combines focus with via for validation")
    void combinesFocusWithViaForValidation() {
      FocusPath<User, String> namePath = FocusPath.of(userNameLens);
      User user = new User("ab", Optional.empty()); // Name too short

      EitherPath<String, String> result =
          Path.<String, User>right(user)
              .focus(namePath)
              .via(
                  name ->
                      name.length() < 3
                          ? Path.left("Name must be at least 3 characters")
                          : Path.right(name));

      assertThat(result.run().isLeft()).isTrue();
      assertThat(result.run().getLeft()).isEqualTo("Name must be at least 3 characters");
    }

    @Test
    @DisplayName("uses focus to navigate then transforms with effect operations")
    void usesFocusToNavigateThenTransformsWithEffectOperations() {
      FocusPath<Person, String> namePath = FocusPath.of(personNameLens);
      AffinePath<Person, String> streetPath =
          FocusPath.of(personAddressLens).via(addressStreetLens).asAffine();

      Person person = new Person("Alice", new Address("Main St", "London"));

      MaybePath<Person> personPath = Path.just(person);

      // Extract name and street, combine them
      String combined =
          personPath
              .focus(namePath)
              .zipWith(personPath.focus(streetPath), (name, street) -> name + " lives on " + street)
              .getOrElse("Unknown");

      assertThat(combined).isEqualTo("Alice lives on Main St");
    }
  }
}
