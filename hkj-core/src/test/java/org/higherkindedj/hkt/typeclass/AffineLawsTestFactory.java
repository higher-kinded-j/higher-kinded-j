// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.typeclass;

import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.util.Prisms;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dynamic test factory for Affine laws using JUnit 6's @TestFactory.
 *
 * <p>This class demonstrates how to use {@code @TestFactory} to generate tests dynamically at
 * runtime, providing comprehensive law testing across all affine implementations with minimal
 * boilerplate.
 *
 * <p>Affine laws tested:
 *
 * <ul>
 *   <li><b>Get-Set:</b> {@code getOptional(set(a, s))} = {@code Optional.of(a)} &mdash; Setting a
 *       value then getting it returns what was set
 *   <li><b>Set-Set:</b> {@code set(b, set(a, s))} = {@code set(b, s)} &mdash; Second set wins
 *   <li><b>GetOptional-Set:</b> If {@code getOptional(s)} = {@code Optional.of(a)}, then {@code
 *       set(a, s)} = {@code s} &mdash; Setting the current value changes nothing
 * </ul>
 *
 * <p>Benefits of @TestFactory approach:
 *
 * <ul>
 *   <li>Tests are generated at runtime based on actual affine implementations
 *   <li>Adding new affines automatically adds test coverage
 *   <li>Clear, structured test output showing which affine/law combination passed/failed
 *   <li>Each test runs independently with proper isolation
 * </ul>
 */
@DisplayName("Affine Laws - Dynamic Test Factory")
class AffineLawsTestFactory {

  // Test data structures
  record UserProfile(String name, Optional<String> email) {}

  record Config(Optional<DatabaseSettings> database) {}

  record DatabaseSettings(String host, int port) {}

  sealed interface Json permits JsonString, JsonNumber {}

  record JsonString(String value) implements Json {}

  record JsonNumber(int value) implements Json {}

  record Container(Json data) {}

  sealed interface ApiResponse permits Success, Failure {}

  record Success(String message, int code) implements ApiResponse {}

  record Failure(String error) implements ApiResponse {}

  /**
   * Test data record containing all information needed to test an affine.
   *
   * @param <S> the source type
   * @param <A> the focus type
   */
  record AffineTestData<S, A>(
      String name,
      Affine<S, A> affine,
      S presentValue,
      S absentValue,
      A testValue,
      A alternateValue) {

    static <S, A> AffineTestData<S, A> of(
        String name,
        Affine<S, A> affine,
        S presentValue,
        S absentValue,
        A testValue,
        A alternateValue) {
      return new AffineTestData<>(
          name, affine, presentValue, absentValue, testValue, alternateValue);
    }
  }

  /**
   * Provides test data for all affine implementations.
   *
   * <p>This is a centralised source of test data. Adding a new affine implementation requires only
   * adding one line here, and all law tests will automatically cover it.
   */
  private static Stream<AffineTestData<?, ?>> allAffines() {
    // Basic Optional field affine
    Affine<UserProfile, String> emailAffine =
        Affine.of(
            UserProfile::email,
            (profile, email) -> new UserProfile(profile.name(), Optional.of(email)));

    // Nested Optional affine
    Affine<Config, DatabaseSettings> dbAffine =
        Affine.of(Config::database, (config, db) -> new Config(Optional.of(db)));

    // Composed: Config -> DatabaseSettings -> host
    Lens<DatabaseSettings, String> hostLens =
        Lens.of(DatabaseSettings::host, (db, host) -> new DatabaseSettings(host, db.port()));
    Affine<Config, String> configHostAffine = dbAffine.andThen(hostLens);

    // Lens + Prism composition (now returns Affine)
    Lens<Container, Json> dataLens = Lens.of(Container::data, (c, json) -> new Container(json));
    Prism<Json, String> jsonStringPrism =
        Prism.of(
            json -> json instanceof JsonString js ? Optional.of(js.value()) : Optional.empty(),
            JsonString::new);
    Affine<Container, String> containerStringAffine = dataLens.andThen(jsonStringPrism);

    // Prism + Lens composition (now returns Affine)
    Prism<ApiResponse, Success> successPrism =
        Prism.of(r -> r instanceof Success s ? Optional.of(s) : Optional.empty(), s -> s);
    Lens<Success, String> messageLens =
        Lens.of(Success::message, (s, msg) -> new Success(msg, s.code()));
    Affine<ApiResponse, String> responseMessageAffine = successPrism.andThen(messageLens);

    // Using Prisms.some() for Optional
    Prism<Optional<String>, String> somePrism = Prisms.some();
    Lens<UserProfile, Optional<String>> rawEmailLens =
        Lens.of(UserProfile::email, (p, e) -> new UserProfile(p.name(), e));
    Affine<UserProfile, String> emailViaPrismAffine = rawEmailLens.andThen(somePrism);

    return Stream.of(
        AffineTestData.of(
            "UserProfile.email",
            emailAffine,
            new UserProfile("Alice", Optional.of("alice@example.com")),
            new UserProfile("Bob", Optional.empty()),
            "test@example.com",
            "alternate@example.com"),
        AffineTestData.of(
            "Config.database",
            dbAffine,
            new Config(Optional.of(new DatabaseSettings("localhost", 5432))),
            new Config(Optional.empty()),
            new DatabaseSettings("newhost", 3306),
            new DatabaseSettings("althost", 1433)),
        AffineTestData.of(
            "Config.host (composed)",
            configHostAffine,
            new Config(Optional.of(new DatabaseSettings("localhost", 5432))),
            new Config(Optional.empty()),
            "newhost",
            "althost"),
        AffineTestData.of(
            "Container.jsonString (Lens+Prism)",
            containerStringAffine,
            new Container(new JsonString("hello")),
            new Container(new JsonNumber(42)),
            "world",
            "test"),
        AffineTestData.of(
            "ApiResponse.message (Prism+Lens)",
            responseMessageAffine,
            new Success("OK", 200),
            new Failure("Not Found"),
            "Updated",
            "Changed"),
        AffineTestData.of(
            "UserProfile.email (via Prisms.some)",
            emailViaPrismAffine,
            new UserProfile("Alice", Optional.of("alice@example.com")),
            new UserProfile("Bob", Optional.empty()),
            "test@example.com",
            "alternate@example.com"));
  }

  /**
   * Helper method to test Get-Set law for a specific affine.
   *
   * <p>Law: {@code getOptional(set(a, s))} = {@code Optional.of(a)}
   *
   * <p>Setting a value then getting it returns what was set.
   */
  private <S, A> void testGetSetLaw(AffineTestData<S, A> data) {
    Affine<S, A> affine = data.affine();
    S presentValue = data.presentValue();
    A testValue = data.testValue();

    // Set a value
    S updated = affine.set(testValue, presentValue);

    // Get it back
    Optional<A> retrieved = affine.getOptional(updated);

    // Should equal what we set
    assertThat(retrieved).isPresent().contains(testValue);
  }

  /**
   * Dynamically generates tests for the Get-Set law: {@code getOptional(set(a, s)) =
   * Optional.of(a)}
   *
   * <p>This test factory creates one test per affine implementation, each verifying that setting a
   * value and then getting it returns the value that was set.
   */
  @TestFactory
  @DisplayName("Get-Set Law: getOptional(set(a, s)) = Optional.of(a)")
  Stream<DynamicTest> getSetLaw() {
    return allAffines()
        .map(
            data ->
                DynamicTest.dynamicTest(
                    data.name() + " satisfies get-set law", () -> testGetSetLaw(data)));
  }

  /**
   * Helper method to test Set-Set law for a specific affine.
   *
   * <p>Law: {@code set(b, set(a, s))} = {@code set(b, s)}
   *
   * <p>Second set wins - setting twice is the same as setting once with the second value.
   */
  private <S, A> void testSetSetLaw(AffineTestData<S, A> data) {
    Affine<S, A> affine = data.affine();
    S presentValue = data.presentValue();
    A testValue = data.testValue();
    A alternateValue = data.alternateValue();

    // Left side: set(b, set(a, s))
    S setTwice = affine.set(alternateValue, affine.set(testValue, presentValue));

    // Right side: set(b, s)
    S setOnce = affine.set(alternateValue, presentValue);

    // Should be equal
    assertThat(setTwice).isEqualTo(setOnce);
  }

  /**
   * Dynamically generates tests for the Set-Set law: {@code set(b, set(a, s)) = set(b, s)}
   *
   * <p>This test factory creates one test per affine implementation, each verifying that setting
   * twice in succession is equivalent to just setting the second value.
   */
  @TestFactory
  @DisplayName("Set-Set Law: set(b, set(a, s)) = set(b, s)")
  Stream<DynamicTest> setSetLaw() {
    return allAffines()
        .map(
            data ->
                DynamicTest.dynamicTest(
                    data.name() + " satisfies set-set law", () -> testSetSetLaw(data)));
  }

  /**
   * Helper method to test GetOptional-Set law for a specific affine.
   *
   * <p>Law: If {@code getOptional(s)} = {@code Optional.of(a)}, then {@code set(a, s)} = {@code s}
   *
   * <p>Setting the current value changes nothing.
   */
  private <S, A> void testGetOptionalSetLaw(AffineTestData<S, A> data) {
    Affine<S, A> affine = data.affine();
    S presentValue = data.presentValue();

    // Get the current value
    Optional<A> current = affine.getOptional(presentValue);

    // Only test if value is present
    if (current.isPresent()) {
      // Set it back to the same value
      S result = affine.set(current.get(), presentValue);

      // Should be equal to original
      assertThat(result).isEqualTo(presentValue);
    }
  }

  /**
   * Dynamically generates tests for the GetOptional-Set law.
   *
   * <p>This test factory creates one test per affine implementation, each verifying that setting
   * the value you just got doesn't change the structure.
   */
  @TestFactory
  @DisplayName("GetOptional-Set Law: set(getOptional(s).get(), s) = s (when present)")
  Stream<DynamicTest> getOptionalSetLaw() {
    return allAffines()
        .map(
            data ->
                DynamicTest.dynamicTest(
                    data.name() + " satisfies getOptional-set law",
                    () -> testGetOptionalSetLaw(data)));
  }

  /**
   * Helper method to test that getOptional returns empty for absent values.
   *
   * <p>Consistency property: {@code getOptional} should return empty for structures where the focus
   * is absent.
   */
  private <S, A> void testGetOptionalAbsent(AffineTestData<S, A> data) {
    Affine<S, A> affine = data.affine();
    S absentValue = data.absentValue();

    Optional<A> result = affine.getOptional(absentValue);

    assertThat(result).isEmpty();
  }

  /** Dynamically generates tests verifying that getOptional returns empty for absent values. */
  @TestFactory
  @DisplayName("getOptional returns empty for absent values")
  Stream<DynamicTest> getOptionalAbsent() {
    return allAffines()
        .map(
            data ->
                DynamicTest.dynamicTest(
                    data.name() + " returns empty for absent", () -> testGetOptionalAbsent(data)));
  }

  /**
   * Helper method to test that modify is consistent with getOptional/set.
   *
   * <p>Derived property: {@code modify(f, s)} = {@code getOptional(s).map(a -> set(f(a),
   * s)).orElse(s)}
   */
  private <S, A> void testModifyConsistency(AffineTestData<S, A> data) {
    Affine<S, A> affine = data.affine();
    S presentValue = data.presentValue();

    // Apply modification using modify
    @SuppressWarnings("unchecked")
    S modified = affine.modify(a -> data.alternateValue(), presentValue);

    // Calculate expected using getOptional/set
    S expected =
        affine
            .getOptional(presentValue)
            .map(a -> affine.set(data.alternateValue(), presentValue))
            .orElse(presentValue);

    assertThat(modified).isEqualTo(expected);
  }

  /** Dynamically generates tests verifying that modify is consistent with getOptional/set. */
  @TestFactory
  @DisplayName("modify is consistent with getOptional/set")
  Stream<DynamicTest> modifyConsistency() {
    return allAffines()
        .map(
            data ->
                DynamicTest.dynamicTest(
                    data.name() + " modify is consistent", () -> testModifyConsistency(data)));
  }

  /**
   * Helper method to test that matches is consistent with getOptional.
   *
   * <p>Consistency property: {@code matches(s)} = {@code getOptional(s).isPresent()}
   */
  private <S, A> void testMatchesConsistency(AffineTestData<S, A> data) {
    Affine<S, A> affine = data.affine();
    S presentValue = data.presentValue();
    S absentValue = data.absentValue();

    assertThat(affine.matches(presentValue))
        .isEqualTo(affine.getOptional(presentValue).isPresent());
    assertThat(affine.matches(absentValue)).isEqualTo(affine.getOptional(absentValue).isPresent());
  }

  /** Dynamically generates tests verifying that matches is consistent with getOptional. */
  @TestFactory
  @DisplayName("matches is consistent with getOptional")
  Stream<DynamicTest> matchesConsistency() {
    return allAffines()
        .map(
            data ->
                DynamicTest.dynamicTest(
                    data.name() + " matches is consistent", () -> testMatchesConsistency(data)));
  }
}
