// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.typeclass;

import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.at.AtInstances;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dynamic test factory for Lens laws using JUnit 6's @TestFactory.
 *
 * <p>This class demonstrates how to use {@code @TestFactory} to generate tests dynamically at
 * runtime, providing comprehensive law testing across all lens implementations with minimal
 * boilerplate.
 *
 * <p>Lens laws tested:
 *
 * <ul>
 *   <li><b>Get-Put:</b> {@code set(get(s), s) == s} - Setting what you get doesn't change anything
 *   <li><b>Put-Get:</b> {@code get(set(a, s)) == a} - Getting what you set returns what you set
 *   <li><b>Put-Put:</b> {@code set(b, set(a, s)) == set(b, s)} - Second set wins
 * </ul>
 *
 * <p>Benefits of @TestFactory approach:
 *
 * <ul>
 *   <li>Tests are generated at runtime based on actual lens implementations
 *   <li>Adding new lenses automatically adds test coverage
 *   <li>Clear, structured test output showing which lens/law combination passed/failed
 *   <li>Each test runs independently with proper isolation
 * </ul>
 */
@DisplayName("Lens Laws - Dynamic Test Factory")
class LensLawsTestFactory {

  // Test data structures
  record Street(String name) {}

  record Address(Street street, String city) {}

  record User(String name, Address address, int age) {}

  record Config(Map<String, String> settings) {}

  /**
   * Test data record containing all information needed to test a lens.
   *
   * @param <S> the source type
   * @param <A> the focus type
   */
  record LensTestData<S, A>(
      String name, Lens<S, A> lens, S testValue, A newValue, A alternateValue) {

    static <S, A> LensTestData<S, A> of(
        String name, Lens<S, A> lens, S testValue, A newValue, A alternateValue) {
      return new LensTestData<>(name, lens, testValue, newValue, alternateValue);
    }
  }

  /**
   * Provides test data for all lens implementations.
   *
   * <p>This is a centralized source of test data. Adding a new lens implementation requires only
   * adding one line here, and all law tests will automatically cover it.
   */
  private static Stream<LensTestData<?, ?>> allLenses() {
    // Street name lens
    Lens<Street, String> streetNameLens = Lens.of(Street::name, (street, name) -> new Street(name));

    // Address street lens
    Lens<Address, Street> addressStreetLens =
        Lens.of(Address::street, (address, street) -> new Address(street, address.city()));

    // Address city lens
    Lens<Address, String> addressCityLens =
        Lens.of(Address::city, (address, city) -> new Address(address.street(), city));

    // User name lens
    Lens<User, String> userNameLens =
        Lens.of(User::name, (user, name) -> new User(name, user.address(), user.age()));

    // User age lens
    Lens<User, Integer> userAgeLens =
        Lens.of(User::age, (user, age) -> new User(user.name(), user.address(), age));

    // User address lens
    Lens<User, Address> userAddressLens =
        Lens.of(User::address, (user, address) -> new User(user.name(), address, user.age()));

    // Composed lens: User -> Address -> Street -> String
    Lens<User, String> userStreetNameLens =
        userAddressLens.andThen(addressStreetLens).andThen(streetNameLens);

    // Config settings lens (Map)
    Lens<Config, Map<String, String>> configSettingsLens =
        Lens.of(Config::settings, (config, settings) -> new Config(settings));

    // At lens for Map access
    var mapAt = AtInstances.<String, String>mapAt();
    Lens<Config, Optional<String>> configDebugLens = configSettingsLens.andThen(mapAt.at("debug"));

    return Stream.of(
        LensTestData.of(
            "Street.name", streetNameLens, new Street("Main St"), "Broadway", "Oak Avenue"),
        LensTestData.of(
            "Address.street",
            addressStreetLens,
            new Address(new Street("Elm St"), "Boston"),
            new Street("Maple Ave"),
            new Street("Pine Rd")),
        LensTestData.of(
            "Address.city", addressCityLens, new Address(new Street("1st Ave"), "NYC"), "LA", "SF"),
        LensTestData.of("User.name", userNameLens, createTestUser(), "Bob", "Charlie"),
        LensTestData.of("User.age", userAgeLens, createTestUser(), 35, 40),
        LensTestData.of(
            "User.address",
            userAddressLens,
            createTestUser(),
            new Address(new Street("New St"), "Seattle"),
            new Address(new Street("Old St"), "Portland")),
        LensTestData.of(
            "User.streetName (composed)",
            userStreetNameLens,
            createTestUser(),
            "Sunset Blvd",
            "Hollywood Ave"),
        LensTestData.of(
            "Config.settings",
            configSettingsLens,
            new Config(new HashMap<>(Map.of("debug", "true"))),
            new HashMap<>(Map.of("debug", "false", "verbose", "true")),
            new HashMap<>(Map.of("production", "true"))),
        LensTestData.of(
            "Config.debug (At lens)",
            configDebugLens,
            new Config(new HashMap<>(Map.of("debug", "true", "mode", "dev"))),
            Optional.of("false"),
            Optional.empty()));
  }

  private static User createTestUser() {
    return new User("Alice", new Address(new Street("Main St"), "Boston"), 30);
  }

  /**
   * Helper method to test Get-Put law for a specific lens.
   *
   * <p>Law: {@code set(get(s), s) == s}
   *
   * <p>Setting what you get doesn't change anything.
   */
  private <S, A> void testGetPutLaw(LensTestData<S, A> data) {
    Lens<S, A> lens = data.lens();
    S testValue = data.testValue();

    // Get the current value
    A currentValue = lens.get(testValue);

    // Set it back to the same value
    S result = lens.set(currentValue, testValue);

    // Should be equal to original
    assertThat(result).isEqualTo(testValue);
  }

  /**
   * Dynamically generates tests for the Get-Put law: {@code set(get(s), s) == s}
   *
   * <p>This test factory creates one test per lens implementation, each verifying that setting the
   * value you just got doesn't change the structure.
   */
  @TestFactory
  @DisplayName("Get-Put Law: set(get(s), s) = s")
  Stream<DynamicTest> getPutLaw() {
    return allLenses()
        .map(
            data ->
                DynamicTest.dynamicTest(
                    data.name() + " satisfies get-put law", () -> testGetPutLaw(data)));
  }

  /**
   * Helper method to test Put-Get law for a specific lens.
   *
   * <p>Law: {@code get(set(a, s)) == a}
   *
   * <p>Getting what you set returns what you set.
   */
  private <S, A> void testPutGetLaw(LensTestData<S, A> data) {
    Lens<S, A> lens = data.lens();
    S testValue = data.testValue();
    A newValue = data.newValue();

    // Set a new value
    S updated = lens.set(newValue, testValue);

    // Get it back
    A retrieved = lens.get(updated);

    // Should equal what we set
    assertThat(retrieved).isEqualTo(newValue);
  }

  /**
   * Dynamically generates tests for the Put-Get law: {@code get(set(a, s)) == a}
   *
   * <p>This test factory creates one test per lens implementation, each verifying that getting a
   * value after setting it returns the value that was set.
   */
  @TestFactory
  @DisplayName("Put-Get Law: get(set(a, s)) = a")
  Stream<DynamicTest> putGetLaw() {
    return allLenses()
        .map(
            data ->
                DynamicTest.dynamicTest(
                    data.name() + " satisfies put-get law", () -> testPutGetLaw(data)));
  }

  /**
   * Helper method to test Put-Put law for a specific lens.
   *
   * <p>Law: {@code set(b, set(a, s)) == set(b, s)}
   *
   * <p>Second set wins - setting twice is the same as setting once with the second value.
   */
  private <S, A> void testPutPutLaw(LensTestData<S, A> data) {
    Lens<S, A> lens = data.lens();
    S testValue = data.testValue();
    A newValue = data.newValue();
    A alternateValue = data.alternateValue();

    // Left side: set(b, set(a, s))
    S setTwice = lens.set(alternateValue, lens.set(newValue, testValue));

    // Right side: set(b, s)
    S setOnce = lens.set(alternateValue, testValue);

    // Should be equal
    assertThat(setTwice).isEqualTo(setOnce);
  }

  /**
   * Dynamically generates tests for the Put-Put law: {@code set(b, set(a, s)) == set(b, s)}
   *
   * <p>This test factory creates one test per lens implementation, each verifying that setting
   * twice in succession is equivalent to just setting the second value.
   */
  @TestFactory
  @DisplayName("Put-Put Law: set(b, set(a, s)) = set(b, s)")
  Stream<DynamicTest> putPutLaw() {
    return allLenses()
        .map(
            data ->
                DynamicTest.dynamicTest(
                    data.name() + " satisfies put-put law", () -> testPutPutLaw(data)));
  }

  /**
   * Provides test data for lenses with String focus type.
   *
   * <p>Used for testing modify operations that require String-specific transformations.
   */
  private static Stream<LensTestData<?, String>> stringLenses() {
    // Street name lens
    Lens<Street, String> streetNameLens = Lens.of(Street::name, (street, name) -> new Street(name));

    // Address city lens
    Lens<Address, String> addressCityLens =
        Lens.of(Address::city, (address, city) -> new Address(address.street(), city));

    // User name lens
    Lens<User, String> userNameLens =
        Lens.of(User::name, (user, name) -> new User(name, user.address(), user.age()));

    // User address lens
    Lens<User, Address> userAddressLens =
        Lens.of(User::address, (user, address) -> new User(user.name(), address, user.age()));

    // Address street lens
    Lens<Address, Street> addressStreetLens =
        Lens.of(Address::street, (address, street) -> new Address(street, address.city()));

    // Composed lens: User -> Address -> Street -> String
    Lens<User, String> userStreetNameLens =
        userAddressLens.andThen(addressStreetLens).andThen(streetNameLens);

    return Stream.of(
        LensTestData.of(
            "Street.name", streetNameLens, new Street("Main St"), "Broadway", "Oak Avenue"),
        LensTestData.of(
            "Address.city", addressCityLens, new Address(new Street("1st Ave"), "NYC"), "LA", "SF"),
        LensTestData.of("User.name", userNameLens, createTestUser(), "Bob", "Charlie"),
        LensTestData.of(
            "User.streetName (composed)",
            userStreetNameLens,
            createTestUser(),
            "Sunset Blvd",
            "Hollywood Ave"));
  }

  /**
   * Helper method to test that modify applies function correctly.
   *
   * <p>Derived property: {@code modify(f, s) == set(f(get(s)), s)}
   */
  private <S> void testModifyConsistency(LensTestData<S, String> data) {
    Lens<S, String> lens = data.lens();
    S testValue = data.testValue();

    // Apply modification
    S modified = lens.modify(String::toUpperCase, testValue);

    // Should be equivalent to set(f(get(s)), s)
    String currentValue = lens.get(testValue);
    S expected = lens.set(currentValue.toUpperCase(), testValue);

    assertThat(modified).isEqualTo(expected);
  }

  /**
   * Dynamically generates tests verifying that modify is consistent with get/set.
   *
   * <p>This is a derived property that helps verify correct lens implementation.
   */
  @TestFactory
  @DisplayName("modify is consistent with get/set")
  Stream<DynamicTest> modifyConsistency() {
    return stringLenses()
        .map(
            data ->
                DynamicTest.dynamicTest(
                    data.name() + " modify is consistent", () -> testModifyConsistency(data)));
  }
}
