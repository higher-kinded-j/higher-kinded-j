// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.focus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.OptionalPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.TryPath;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for AffinePath bridge methods to EffectPath types.
 *
 * <p>Tests cover the bridge methods: toMaybePath, toEitherPath, toTryPath, and toOptionalPath.
 */
@DisplayName("AffinePath Bridge Methods Tests")
class AffinePathBridgeTest {

  // Test Data Structures
  record Config(Optional<String> apiKey, String name) {}

  sealed interface Json permits JsonString, JsonNumber {}

  record JsonString(String value) implements Json {}

  record JsonNumber(int value) implements Json {}

  record Profile(Json data) {}

  // Affines and Lenses
  private Lens<Config, Optional<String>> apiKeyLens;
  private Affine<Optional<String>, String> optionalSome;
  private Lens<Profile, Json> profileDataLens;
  private Prism<Json, String> jsonStringPrism;

  @BeforeEach
  void setUp() {
    apiKeyLens = Lens.of(Config::apiKey, (c, k) -> new Config(k, c.name()));
    optionalSome = FocusPaths.optionalSome();
    profileDataLens = Lens.of(Profile::data, (p, d) -> new Profile(d));
    jsonStringPrism =
        Prism.of(
            json -> json instanceof JsonString s ? Optional.of(s.value()) : Optional.empty(),
            JsonString::new);
  }

  @Nested
  @DisplayName("toMaybePath()")
  class ToMaybePathTests {

    @Test
    @DisplayName("returns Just when affine matches")
    void returnsJustWhenAffineMatches() {
      AffinePath<Config, String> path = FocusPath.of(apiKeyLens).via(optionalSome);
      Config config = new Config(Optional.of("secret-key"), "app");

      MaybePath<String> result = path.toMaybePath(config);

      assertThat(result.run().isJust()).isTrue();
      assertThat(result.run().get()).isEqualTo("secret-key");
    }

    @Test
    @DisplayName("returns Nothing when affine doesn't match")
    void returnsNothingWhenAffineDoesntMatch() {
      AffinePath<Config, String> path = FocusPath.of(apiKeyLens).via(optionalSome);
      Config config = new Config(Optional.empty(), "app");

      MaybePath<String> result = path.toMaybePath(config);

      assertThat(result.run().isNothing()).isTrue();
    }

    @Test
    @DisplayName("allows chaining with effect operations when present")
    void allowsChainingWithEffectOperationsWhenPresent() {
      AffinePath<Config, String> path = FocusPath.of(apiKeyLens).via(optionalSome);
      Config config = new Config(Optional.of("my-key"), "app");

      String result =
          path.toMaybePath(config)
              .map(String::toUpperCase)
              .filter(key -> key.startsWith("MY"))
              .getOrElse("DEFAULT");

      assertThat(result).isEqualTo("MY-KEY");
    }

    @Test
    @DisplayName("returns default when chained on empty")
    void returnsDefaultWhenChainedOnEmpty() {
      AffinePath<Config, String> path = FocusPath.of(apiKeyLens).via(optionalSome);
      Config config = new Config(Optional.empty(), "app");

      String result = path.toMaybePath(config).map(String::toUpperCase).getOrElse("DEFAULT");

      assertThat(result).isEqualTo("DEFAULT");
    }
  }

  @Nested
  @DisplayName("toEitherPath()")
  class ToEitherPathTests {

    @Test
    @DisplayName("returns Right when affine matches")
    void returnsRightWhenAffineMatches() {
      AffinePath<Config, String> path = FocusPath.of(apiKeyLens).via(optionalSome);
      Config config = new Config(Optional.of("secret"), "app");

      EitherPath<String, String> result = path.toEitherPath(config, "API key required");

      assertThat(result.run().isRight()).isTrue();
      assertThat(result.run().getRight()).isEqualTo("secret");
    }

    @Test
    @DisplayName("returns Left with error when affine doesn't match")
    void returnsLeftWithErrorWhenAffineDoesntMatch() {
      AffinePath<Config, String> path = FocusPath.of(apiKeyLens).via(optionalSome);
      Config config = new Config(Optional.empty(), "app");

      EitherPath<String, String> result = path.toEitherPath(config, "API key required");

      assertThat(result.run().isLeft()).isTrue();
      assertThat(result.run().getLeft()).isEqualTo("API key required");
    }

    @Test
    @DisplayName("allows error recovery")
    void allowsErrorRecovery() {
      AffinePath<Config, String> path = FocusPath.of(apiKeyLens).via(optionalSome);
      Config config = new Config(Optional.empty(), "app");

      String result =
          path.toEitherPath(config, "Missing").recover(err -> "default-key").run().getRight();

      assertThat(result).isEqualTo("default-key");
    }

    @Test
    @DisplayName("supports via chaining after extraction")
    void supportsViaChainingAfterExtraction() {
      AffinePath<Config, String> path = FocusPath.of(apiKeyLens).via(optionalSome);
      Config validConfig = new Config(Optional.of("valid-key"), "app");

      EitherPath<String, Integer> result =
          path.toEitherPath(validConfig, "Missing")
              .via(key -> key.length() < 5 ? Path.left("Key too short") : Path.right(key.length()));

      assertThat(result.run().isRight()).isTrue();
      assertThat(result.run().getRight()).isEqualTo(9); // "valid-key".length()
    }
  }

  @Nested
  @DisplayName("toTryPath()")
  class ToTryPathTests {

    @Test
    @DisplayName("returns Success when affine matches")
    void returnsSuccessWhenAffineMatches() {
      AffinePath<Config, String> path = FocusPath.of(apiKeyLens).via(optionalSome);
      Config config = new Config(Optional.of("key"), "app");

      TryPath<String> result = path.toTryPath(config, IllegalStateException::new);

      assertThat(result.run().isSuccess()).isTrue();
      result
          .run()
          .fold(
              value -> {
                assertThat(value).isEqualTo("key");
                return null;
              },
              ex -> null);
    }

    @Test
    @DisplayName("returns Failure with exception when affine doesn't match")
    void returnsFailureWithExceptionWhenAffineDoesntMatch() {
      AffinePath<Config, String> path = FocusPath.of(apiKeyLens).via(optionalSome);
      Config config = new Config(Optional.empty(), "app");

      TryPath<String> result =
          path.toTryPath(config, () -> new IllegalStateException("API key not configured"));

      assertThat(result.run().isFailure()).isTrue();
      result
          .run()
          .fold(
              value -> null,
              ex -> {
                assertThat(ex).isInstanceOf(IllegalStateException.class);
                assertThat(ex.getMessage()).isEqualTo("API key not configured");
                return null;
              });
    }

    @Test
    @DisplayName("supports exception recovery")
    void supportsExceptionRecovery() {
      AffinePath<Config, String> path = FocusPath.of(apiKeyLens).via(optionalSome);
      Config config = new Config(Optional.empty(), "app");

      String result =
          path.toTryPath(config, IllegalStateException::new)
              .recover(ex -> "default-key")
              .getOrElse("fallback");

      assertThat(result).isEqualTo("default-key");
    }
  }

  @Nested
  @DisplayName("toOptionalPath()")
  class ToOptionalPathTests {

    @Test
    @DisplayName("returns present Optional when affine matches")
    void returnsPresentOptionalWhenAffineMatches() {
      AffinePath<Config, String> path = FocusPath.of(apiKeyLens).via(optionalSome);
      Config config = new Config(Optional.of("secret"), "app");

      OptionalPath<String> result = path.toOptionalPath(config);

      assertThat(result.run().isPresent()).isTrue();
      assertThat(result.run().get()).isEqualTo("secret");
    }

    @Test
    @DisplayName("returns empty Optional when affine doesn't match")
    void returnsEmptyOptionalWhenAffineDoesntMatch() {
      AffinePath<Config, String> path = FocusPath.of(apiKeyLens).via(optionalSome);
      Config config = new Config(Optional.empty(), "app");

      OptionalPath<String> result = path.toOptionalPath(config);

      assertThat(result.run().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("allows map transformation when present")
    void allowsMapTransformationWhenPresent() {
      AffinePath<Config, String> path = FocusPath.of(apiKeyLens).via(optionalSome);
      Config config = new Config(Optional.of("key"), "app");

      OptionalPath<Integer> result = path.toOptionalPath(config).map(String::length);

      assertThat(result.run().isPresent()).isTrue();
      assertThat(result.run().get()).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("Prism-based AffinePath")
  class PrismBasedAffinePathTests {

    @Test
    @DisplayName("toMaybePath returns Just when prism matches")
    void toMaybePathReturnsJustWhenPrismMatches() {
      AffinePath<Profile, String> path = FocusPath.of(profileDataLens).via(jsonStringPrism);
      Profile profile = new Profile(new JsonString("hello"));

      MaybePath<String> result = path.toMaybePath(profile);

      assertThat(result.run().isJust()).isTrue();
      assertThat(result.run().get()).isEqualTo("hello");
    }

    @Test
    @DisplayName("toMaybePath returns Nothing when prism doesn't match")
    void toMaybePathReturnsNothingWhenPrismDoesntMatch() {
      AffinePath<Profile, String> path = FocusPath.of(profileDataLens).via(jsonStringPrism);
      Profile profile = new Profile(new JsonNumber(42));

      MaybePath<String> result = path.toMaybePath(profile);

      assertThat(result.run().isNothing()).isTrue();
    }

    @Test
    @DisplayName("toEitherPath returns Left with error when prism doesn't match")
    void toEitherPathReturnsLeftWhenPrismDoesntMatch() {
      AffinePath<Profile, String> path = FocusPath.of(profileDataLens).via(jsonStringPrism);
      Profile profile = new Profile(new JsonNumber(42));

      EitherPath<String, String> result = path.toEitherPath(profile, "Expected JSON string");

      assertThat(result.run().isLeft()).isTrue();
      assertThat(result.run().getLeft()).isEqualTo("Expected JSON string");
    }
  }
}
