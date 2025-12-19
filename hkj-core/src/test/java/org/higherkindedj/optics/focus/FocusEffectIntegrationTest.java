// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.focus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.IdPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.OptionalPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.TryPath;
import org.higherkindedj.hkt.effect.ValidationPath;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Lens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for FocusPath and EffectPath bridge operations.
 *
 * <p>Tests realistic scenarios combining optics-based navigation with effect-based computations.
 */
@DisplayName("FocusPath-EffectPath Integration Tests")
class FocusEffectIntegrationTest {

  // Domain model
  record User(String id, String name, Profile profile) {}

  record Profile(String bio, Optional<Address> address, List<String> interests) {}

  record Address(String street, String city, String country) {}

  // Lenses
  private Lens<User, String> userIdLens;
  private Lens<User, String> userNameLens;
  private Lens<User, Profile> userProfileLens;
  private Lens<Profile, String> profileBioLens;
  private Lens<Profile, Optional<Address>> profileAddressLens;
  private Lens<Address, String> addressCityLens;
  private Lens<Address, String> addressStreetLens;

  // Affines
  private Affine<Optional<Address>, Address> optionalAddressSome;

  // Test data
  private User userWithAddress;
  private User userWithoutAddress;

  @BeforeEach
  void setUp() {
    // Initialize lenses
    userIdLens = Lens.of(User::id, (u, id) -> new User(id, u.name(), u.profile()));
    userNameLens = Lens.of(User::name, (u, name) -> new User(u.id(), name, u.profile()));
    userProfileLens = Lens.of(User::profile, (u, p) -> new User(u.id(), u.name(), p));
    profileBioLens =
        Lens.of(Profile::bio, (p, bio) -> new Profile(bio, p.address(), p.interests()));
    profileAddressLens =
        Lens.of(Profile::address, (p, addr) -> new Profile(p.bio(), addr, p.interests()));
    addressCityLens =
        Lens.of(Address::city, (a, city) -> new Address(a.street(), city, a.country()));
    addressStreetLens =
        Lens.of(Address::street, (a, street) -> new Address(street, a.city(), a.country()));

    // Initialize affines
    optionalAddressSome = FocusPaths.optionalSome();

    // Initialize test data
    Address address = new Address("123 Main St", "London", "UK");
    Profile profileWithAddress =
        new Profile("Developer", Optional.of(address), List.of("coding", "music"));
    Profile profileWithoutAddress =
        new Profile("Designer", Optional.empty(), List.of("art", "photography"));

    userWithAddress = new User("user1", "Alice", profileWithAddress);
    userWithoutAddress = new User("user2", "Bob", profileWithoutAddress);
  }

  @Nested
  @DisplayName("Deep Navigation Scenarios")
  class DeepNavigationScenarios {

    @Test
    @DisplayName("navigate through nested objects using focus composition")
    void navigateThroughNestedObjectsUsingFocusComposition() {
      // Build path: User -> Profile -> Address -> City
      FocusPath<User, Profile> profilePath = FocusPath.of(userProfileLens);
      FocusPath<Profile, Optional<Address>> addressOptPath = FocusPath.of(profileAddressLens);
      AffinePath<Profile, Address> addressPath = addressOptPath.via(optionalAddressSome);
      AffinePath<Profile, String> cityPath = addressPath.via(addressCityLens);

      // Use with MaybePath
      MaybePath<User> userPath = Path.just(userWithAddress);
      MaybePath<String> cityResult = userPath.focus(profilePath).focus(cityPath);

      assertThat(cityResult.run().isJust()).isTrue();
      assertThat(cityResult.run().get()).isEqualTo("London");
    }

    @Test
    @DisplayName("handle missing nested values gracefully")
    void handleMissingNestedValuesGracefully() {
      FocusPath<User, Profile> profilePath = FocusPath.of(userProfileLens);
      AffinePath<Profile, Address> addressPath =
          FocusPath.of(profileAddressLens).via(optionalAddressSome);

      // User without address
      MaybePath<User> userPath = Path.just(userWithoutAddress);
      MaybePath<Address> addressResult = userPath.focus(profilePath).focus(addressPath);

      assertThat(addressResult.run().isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("Validation Pipelines")
  class ValidationPipelines {

    @Test
    @DisplayName("validate user fields using focus and effects")
    void validateUserFieldsUsingFocusAndEffects() {
      FocusPath<User, String> namePath = FocusPath.of(userNameLens);

      // Validate that name is not empty
      EitherPath<String, String> validated =
          Path.<String, User>right(userWithAddress)
              .focus(namePath)
              .via(
                  name -> {
                    if (name.isEmpty()) {
                      return Path.left("Name cannot be empty");
                    }
                    if (name.length() < 2) {
                      return Path.left("Name must be at least 2 characters");
                    }
                    return Path.right(name);
                  });

      assertThat(validated.run().isRight()).isTrue();
      assertThat(validated.run().getRight()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("accumulate validation errors using ValidationPath")
    void accumulateValidationErrorsUsingValidationPath() {
      FocusPath<User, String> namePath = FocusPath.of(userNameLens);
      FocusPath<User, String> idPath = FocusPath.of(userIdLens);

      ValidationPath<List<String>, User> userValidation =
          Path.valid(userWithAddress, Semigroups.list());

      // Validate name length
      ValidationPath<List<String>, String> nameValidation =
          userValidation
              .focus(namePath)
              .via(
                  name -> {
                    if (name.length() < 2) {
                      return Path.invalid(List.of("Name too short"), Semigroups.list());
                    }
                    return Path.valid(name, Semigroups.list());
                  });

      assertThat(nameValidation.run().isValid()).isTrue();
    }
  }

  @Nested
  @DisplayName("Effect Type Conversions")
  class EffectTypeConversions {

    @Test
    @DisplayName("convert FocusPath extraction through different effect types")
    void convertFocusPathExtractionThroughDifferentEffectTypes() {
      FocusPath<User, String> namePath = FocusPath.of(userNameLens);

      // Same extraction through different effect types
      MaybePath<String> asMaybe = namePath.toMaybePath(userWithAddress);
      EitherPath<String, String> asEither = namePath.toEitherPath(userWithAddress);
      TryPath<String> asTry = namePath.toTryPath(userWithAddress);
      IdPath<String> asId = namePath.toIdPath(userWithAddress);

      // All should produce the same value
      assertThat(asMaybe.run().get()).isEqualTo("Alice");
      assertThat(asEither.run().getRight()).isEqualTo("Alice");
      assertThat(asTry.getOrElse("fallback")).isEqualTo("Alice");
      assertThat(asId.run().value()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("AffinePath bridges correctly to different effect types")
    void affinePathBridgesCorrectlyToDifferentEffectTypes() {
      AffinePath<User, Address> addressPath =
          FocusPath.of(userProfileLens).via(profileAddressLens).via(optionalAddressSome);

      // With address present
      MaybePath<Address> asMaybe = addressPath.toMaybePath(userWithAddress);
      EitherPath<String, Address> asEither =
          addressPath.toEitherPath(userWithAddress, "No address");
      TryPath<Address> asTry =
          addressPath.toTryPath(userWithAddress, () -> new RuntimeException("No address"));

      assertThat(asMaybe.run().isJust()).isTrue();
      assertThat(asEither.run().isRight()).isTrue();
      assertThat(asTry.run().isSuccess()).isTrue();

      // Without address
      MaybePath<Address> maybeEmpty = addressPath.toMaybePath(userWithoutAddress);
      EitherPath<String, Address> eitherEmpty =
          addressPath.toEitherPath(userWithoutAddress, "No address");
      TryPath<Address> tryEmpty =
          addressPath.toTryPath(userWithoutAddress, () -> new RuntimeException("No address"));

      assertThat(maybeEmpty.run().isNothing()).isTrue();
      assertThat(eitherEmpty.run().isLeft()).isTrue();
      assertThat(eitherEmpty.run().getLeft()).isEqualTo("No address");
      assertThat(tryEmpty.run().isFailure()).isTrue();
    }
  }

  @Nested
  @DisplayName("IOPath Integration")
  class IOPathIntegration {

    @Test
    @DisplayName("use focus within deferred IO computation")
    void useFocusWithinDeferredIOComputation() {
      FocusPath<User, String> namePath = FocusPath.of(userNameLens);

      // Simulate loading user from "database"
      IOPath<User> loadUser = Path.io(() -> userWithAddress);

      // Focus on name within IO context
      IOPath<String> nameIO = loadUser.focus(namePath);

      // Nothing executed yet
      String result = nameIO.unsafeRun();

      assertThat(result).isEqualTo("Alice");
    }

    @Test
    @DisplayName("chain focus with IO transformations")
    void chainFocusWithIOTransformations() {
      FocusPath<User, String> namePath = FocusPath.of(userNameLens);

      IOPath<String> pipeline =
          Path.io(() -> userWithAddress)
              .focus(namePath)
              .map(String::toUpperCase)
              .map(name -> "Hello, " + name);

      String result = pipeline.unsafeRun();

      assertThat(result).isEqualTo("Hello, ALICE");
    }
  }

  @Nested
  @DisplayName("OptionalPath Integration")
  class OptionalPathIntegration {

    @Test
    @DisplayName("focus works with OptionalPath")
    void focusWorksWithOptionalPath() {
      FocusPath<User, String> namePath = FocusPath.of(userNameLens);

      OptionalPath<User> userOptional = Path.present(userWithAddress);
      OptionalPath<String> nameResult = userOptional.focus(namePath);

      assertThat(nameResult.run().isPresent()).isTrue();
      assertThat(nameResult.run().get()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("focus with AffinePath flattens optionals correctly")
    void focusWithAffinePathFlattensOptionalsCorrectly() {
      AffinePath<User, Address> addressPath =
          FocusPath.of(userProfileLens).via(profileAddressLens).via(optionalAddressSome);

      OptionalPath<User> userOptional = Path.present(userWithoutAddress);
      OptionalPath<Address> addressResult = userOptional.focus(addressPath);

      assertThat(addressResult.run().isEmpty()).isTrue();
    }
  }

  @Nested
  @DisplayName("IdPath Integration")
  class IdPathIntegration {

    @Test
    @DisplayName("focus works with IdPath for pure transformations")
    void focusWorksWithIdPathForPureTransformations() {
      FocusPath<User, String> namePath = FocusPath.of(userNameLens);

      IdPath<User> userIdPath = Path.id(userWithAddress);
      IdPath<String> nameResult = userIdPath.focus(namePath);

      assertThat(nameResult.run().value()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("focus with AffinePath returns MaybePath from IdPath")
    void focusWithAffinePathReturnsMaybePathFromIdPath() {
      AffinePath<User, Address> addressPath =
          FocusPath.of(userProfileLens).via(profileAddressLens).via(optionalAddressSome);

      IdPath<User> userIdPath = Path.id(userWithAddress);
      MaybePath<Address> addressResult = userIdPath.focus(addressPath);

      assertThat(addressResult.run().isJust()).isTrue();
      assertThat(addressResult.run().get().city()).isEqualTo("London");
    }
  }

  @Nested
  @DisplayName("Complex Composition Scenarios")
  class ComplexCompositionScenarios {

    @Test
    @DisplayName("combine multiple effect paths with focus operations")
    void combineMultipleEffectPathsWithFocusOperations() {
      FocusPath<User, String> namePath = FocusPath.of(userNameLens);
      AffinePath<User, String> cityPath =
          FocusPath.of(userProfileLens)
              .via(profileAddressLens)
              .via(optionalAddressSome)
              .via(addressCityLens);

      MaybePath<User> userPath = Path.just(userWithAddress);

      // Combine name and city
      String result =
          userPath
              .focus(namePath)
              .zipWith(userPath.focus(cityPath), (name, city) -> name + " from " + city)
              .getOrElse("Unknown");

      assertThat(result).isEqualTo("Alice from London");
    }

    @Test
    @DisplayName("transform effect type after focus operation")
    void transformEffectTypeAfterFocusOperation() {
      FocusPath<User, String> namePath = FocusPath.of(userNameLens);

      // Start with MaybePath, transform through focus, convert to Either
      EitherPath<String, String> result =
          Path.just(userWithAddress).focus(namePath).toEitherPath("User not found");

      assertThat(result.run().isRight()).isTrue();
      assertThat(result.run().getRight()).isEqualTo("Alice");
    }
  }
}
