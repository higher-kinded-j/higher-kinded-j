// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.extensions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.optics.extensions.GetterExtensions.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.optics.Getter;
import org.higherkindedj.optics.Lens;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("GetterExtensions Tests")
class GetterExtensionsTest {

  // Test Data Structures with nullable fields
  record User(String name, @Nullable String email, int age) {}

  record Person(String firstName, String lastName, @Nullable Address address) {}

  record Address(String city, @Nullable String zipCode) {}

  record Product(String name, int price, @Nullable String description) {}

  @Nested
  @DisplayName("Constructor")
  class ConstructorTests {

    @Test
    @DisplayName("should throw UnsupportedOperationException when attempting to instantiate")
    void cannotInstantiate() {
      assertThatThrownBy(
              () -> {
                var constructor = GetterExtensions.class.getDeclaredConstructor();
                constructor.setAccessible(true);
                constructor.newInstance();
              })
          .hasCauseInstanceOf(UnsupportedOperationException.class)
          .getCause()
          .hasMessage("Utility class - do not instantiate");
    }
  }

  @Nested
  @DisplayName("getMaybe() with non-null values")
  class GetMaybeNonNullTests {

    @Test
    @DisplayName("should return Just when value is non-null")
    void getMaybeNonNull() {
      Getter<User, String> nameGetter = Getter.of(User::name);
      User user = new User("Alice", "alice@example.com", 30);

      Maybe<String> name = getMaybe(nameGetter, user);

      assertThat(name.isJust()).isTrue();
      assertThat(name.get()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("should work with primitive wrapper types")
    void getMaybeWithPrimitives() {
      Getter<User, Integer> ageGetter = Getter.of(u -> (Integer) u.age());
      User user = new User("Alice", null, 30);

      Maybe<Integer> age = getMaybe(ageGetter, user);

      assertThat(age.isJust()).isTrue();
      assertThat(age.get()).isEqualTo(30);
    }

    @Test
    @DisplayName("should work with complex objects")
    void getMaybeWithComplexObjects() {
      Getter<Person, Address> addressGetter =
          Getter.of(p -> p.address() != null ? p.address() : null);
      Address address = new Address("New York", "10001");
      Person person = new Person("John", "Doe", address);

      Maybe<Address> maybeAddress = getMaybe(addressGetter, person);

      assertThat(maybeAddress.isJust()).isTrue();
      assertThat(maybeAddress.get()).isEqualTo(address);
    }
  }

  @Nested
  @DisplayName("getMaybe() with null values")
  class GetMaybeNullTests {

    @Test
    @DisplayName("should return Nothing when value is null")
    void getMaybeNull() {
      Getter<User, String> emailGetter = Getter.of(User::email);
      User user = new User("Bob", null, 25);

      Maybe<String> email = getMaybe(emailGetter, user);

      assertThat(email.isNothing()).isTrue();
    }

    @Test
    @DisplayName("should return Nothing for null complex objects")
    void getMaybeNullComplexObject() {
      Getter<Person, Address> addressGetter = Getter.of(Person::address);
      Person person = new Person("John", "Doe", null);

      Maybe<Address> address = getMaybe(addressGetter, person);

      assertThat(address.isNothing()).isTrue();
    }

    @Test
    @DisplayName("should handle null strings")
    void getMaybeNullString() {
      Getter<Product, String> descriptionGetter = Getter.of(Product::description);
      Product product = new Product("Laptop", 1000, null);

      Maybe<String> description = getMaybe(descriptionGetter, product);

      assertThat(description.isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("Chaining Operations")
  class ChainingTests {

    @Test
    @DisplayName("should allow chaining with map")
    void getMaybeWithMap() {
      Getter<User, String> nameGetter = Getter.of(User::name);
      User user = new User("Alice", "alice@example.com", 30);

      Maybe<String> uppercaseName = getMaybe(nameGetter, user).map(String::toUpperCase);

      assertThat(uppercaseName.isJust()).isTrue();
      assertThat(uppercaseName.get()).isEqualTo("ALICE");
    }

    @Test
    @DisplayName("should short-circuit map when value is null")
    void getMaybeNullWithMap() {
      Getter<User, String> emailGetter = Getter.of(User::email);
      User user = new User("Bob", null, 25);

      Maybe<String> uppercaseEmail = getMaybe(emailGetter, user).map(String::toUpperCase);

      assertThat(uppercaseEmail.isNothing()).isTrue();
    }

    @Test
    @DisplayName("should allow chaining with flatMap")
    void getMaybeWithFlatMap() {
      Getter<Person, Address> addressGetter = Getter.of(Person::address);
      Getter<Address, String> zipCodeGetter = Getter.of(Address::zipCode);

      Address address = new Address("New York", "10001");
      Person person = new Person("John", "Doe", address);

      Maybe<String> zipCode =
          getMaybe(addressGetter, person).flatMap(addr -> getMaybe(zipCodeGetter, addr));

      assertThat(zipCode.isJust()).isTrue();
      assertThat(zipCode.get()).isEqualTo("10001");
    }

    @Test
    @DisplayName("should short-circuit flatMap when intermediate value is null")
    void getMaybeNullWithFlatMap() {
      Getter<Person, Address> addressGetter = Getter.of(Person::address);
      Getter<Address, String> zipCodeGetter = Getter.of(Address::zipCode);

      Person person = new Person("John", "Doe", null); // null address

      Maybe<String> zipCode =
          getMaybe(addressGetter, person).flatMap(addr -> getMaybe(zipCodeGetter, addr));

      assertThat(zipCode.isNothing()).isTrue();
    }

    @Test
    @DisplayName("should short-circuit flatMap when final value is null")
    void getMaybeWithFlatMapEndingInNull() {
      Getter<Person, Address> addressGetter = Getter.of(Person::address);
      Getter<Address, String> zipCodeGetter = Getter.of(Address::zipCode);

      Address address = new Address("New York", null); // null zipCode
      Person person = new Person("John", "Doe", address);

      Maybe<String> zipCode =
          getMaybe(addressGetter, person).flatMap(addr -> getMaybe(zipCodeGetter, addr));

      assertThat(zipCode.isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("Default Value Handling")
  class DefaultValueTests {

    @Test
    @DisplayName("should provide default value with orElse when value is null")
    void getMaybeWithOrElse() {
      Getter<User, String> emailGetter = Getter.of(User::email);
      User user = new User("Bob", null, 25);

      String email = getMaybe(emailGetter, user).orElse("no-email@example.com");

      assertThat(email).isEqualTo("no-email@example.com");
    }

    @Test
    @DisplayName("should not use default value when value is present")
    void getMaybeNonNullWithOrElse() {
      Getter<User, String> emailGetter = Getter.of(User::email);
      User user = new User("Alice", "alice@example.com", 30);

      String email = getMaybe(emailGetter, user).orElse("no-email@example.com");

      assertThat(email).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("should provide default value with orElseGet using supplier")
    void getMaybeWithOrElseGet() {
      Getter<User, String> emailGetter = Getter.of(User::email);
      User user = new User("Bob", null, 25);

      String email =
          getMaybe(emailGetter, user).orElseGet(() -> user.name().toLowerCase() + "@default.com");

      assertThat(email).isEqualTo("bob@default.com");
    }
  }

  @Nested
  @DisplayName("Integration with Lens")
  class LensIntegrationTests {

    @Test
    @DisplayName("should work with Lens as Getter (non-null case)")
    void lensAsGetterNonNull() {
      // Create a lens (in real code this would be generated)
      Lens<User, String> nameLens =
          new Lens<>() {
            @Override
            public String get(User user) {
              return user.name();
            }

            @Override
            public User set(String newName, User user) {
              return new User(newName, user.email(), user.age());
            }

            @Override
            public <F extends WitnessArity<TypeArity.Unary>> Kind<F, User> modifyF(
                Function<String, Kind<F, String>> f, User user, Functor<F> functor) {
              return functor.map(newName -> set(newName, user), f.apply(get(user)));
            }
          };

      User user = new User("Alice", "alice@example.com", 30);

      // Create a Getter from the Lens
      Getter<User, String> nameGetter = Getter.of(nameLens::get);
      Maybe<String> name = getMaybe(nameGetter, user);

      assertThat(name.isJust()).isTrue();
      assertThat(name.get()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("should work with Lens as Getter (null case)")
    void lensAsGetterNull() {
      Lens<User, String> emailLens =
          new Lens<>() {
            @Override
            public String get(User user) {
              return user.email();
            }

            @Override
            public User set(String newEmail, User user) {
              return new User(user.name(), newEmail, user.age());
            }

            @Override
            public <F extends WitnessArity<TypeArity.Unary>> Kind<F, User> modifyF(
                Function<String, Kind<F, String>> f, User user, Functor<F> functor) {
              return functor.map(newEmail -> set(newEmail, user), f.apply(get(user)));
            }
          };

      User user = new User("Bob", null, 25);

      // Create a Getter from the Lens
      Getter<User, String> emailGetter = Getter.of(emailLens::get);
      Maybe<String> email = getMaybe(emailGetter, user);

      assertThat(email.isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("Composition")
  class CompositionTests {

    @Test
    @DisplayName("should compose getters and use with getMaybe")
    void composeGetters() {
      Getter<Person, Address> addressGetter = Getter.of(Person::address);
      Getter<Address, String> cityGetter = Getter.of(Address::city);

      Getter<Person, String> personCityGetter = addressGetter.andThen(cityGetter);

      Address address = new Address("New York", "10001");
      Person person = new Person("John", "Doe", address);

      // Direct composition returns String (potentially null)
      String city = personCityGetter.get(person);
      assertThat(city).isEqualTo("New York");

      // For nullable intermediate values, use flatMap to chain safely
      Person personWithNullAddress = new Person("Jane", "Doe", null);
      Maybe<String> maybeCity =
          getMaybe(addressGetter, personWithNullAddress)
              .flatMap(addr -> getMaybe(cityGetter, addr));
      assertThat(maybeCity.isNothing()).isTrue();
    }

    @Test
    @DisplayName("should compose with nullable intermediate values")
    void composeWithNullableIntermediates() {
      Getter<Person, String> fullNameGetter = Getter.of(p -> p.firstName() + " " + p.lastName());

      Person person = new Person("John", "Doe", null);

      Maybe<String> fullName = getMaybe(fullNameGetter, person);

      assertThat(fullName.isJust()).isTrue();
      assertThat(fullName.get()).isEqualTo("John Doe");
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCases {

    @Test
    @DisplayName("should handle empty strings as non-null")
    void emptyStringIsNotNull() {
      Getter<User, String> nameGetter = Getter.of(User::name);
      User user = new User("", "alice@example.com", 30);

      Maybe<String> name = getMaybe(nameGetter, user);

      assertThat(name.isJust()).isTrue();
      assertThat(name.get()).isEqualTo("");
    }

    @Test
    @DisplayName("should handle zero as non-null for primitive wrappers")
    void zeroIsNotNull() {
      Getter<User, Integer> ageGetter = Getter.of(u -> (Integer) u.age());
      User user = new User("Bob", null, 0);

      Maybe<Integer> age = getMaybe(ageGetter, user);

      assertThat(age.isJust()).isTrue();
      assertThat(age.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("should handle derived null values correctly")
    void derivedNullValue() {
      // Getter that returns null based on logic
      Getter<User, String> conditionalGetter = Getter.of(u -> u.age() < 18 ? null : u.name());

      User minor = new User("Child", "child@example.com", 10);
      User adult = new User("Adult", "adult@example.com", 25);

      Maybe<String> minorName = getMaybe(conditionalGetter, minor);
      Maybe<String> adultName = getMaybe(conditionalGetter, adult);

      assertThat(minorName.isNothing()).isTrue();
      assertThat(adultName.isJust()).isTrue();
      assertThat(adultName.get()).isEqualTo("Adult");
    }
  }

  @Nested
  @DisplayName("Real-World Scenarios")
  class RealWorldScenarios {

    @Test
    @DisplayName("should safely navigate nested nullable structures")
    void nestedNullableNavigation() {
      record Company(String name, @Nullable Person ceo) {}

      Getter<Company, Person> ceoGetter = Getter.of(Company::ceo);
      Getter<Person, Address> addressGetter = Getter.of(Person::address);
      Getter<Address, String> zipCodeGetter = Getter.of(Address::zipCode);

      // Company with full data
      Address address = new Address("Seattle", "98101");
      Person ceo = new Person("John", "Doe", address);
      Company company1 = new Company("TechCorp", ceo);

      Maybe<String> zipCode1 =
          getMaybe(ceoGetter, company1)
              .flatMap(c -> getMaybe(addressGetter, c))
              .flatMap(a -> getMaybe(zipCodeGetter, a));

      assertThat(zipCode1.isJust()).isTrue();
      assertThat(zipCode1.get()).isEqualTo("98101");

      // Company with no CEO
      Company company2 = new Company("StartupCo", null);

      Maybe<String> zipCode2 =
          getMaybe(ceoGetter, company2)
              .flatMap(c -> getMaybe(addressGetter, c))
              .flatMap(a -> getMaybe(zipCodeGetter, a));

      assertThat(zipCode2.isNothing()).isTrue();

      // Company with CEO but no address
      Person ceoNoAddress = new Person("Jane", "Smith", null);
      Company company3 = new Company("RemoteCo", ceoNoAddress);

      Maybe<String> zipCode3 =
          getMaybe(ceoGetter, company3)
              .flatMap(c -> getMaybe(addressGetter, c))
              .flatMap(a -> getMaybe(zipCodeGetter, a));

      assertThat(zipCode3.isNothing()).isTrue();
    }

    @Test
    @DisplayName("should provide fallback chain for multiple getters")
    void fallbackChain() {
      record UserProfile(
          String username, @Nullable String email, @Nullable String phone, @Nullable String fax) {}

      Getter<UserProfile, String> emailGetter = Getter.of(UserProfile::email);
      Getter<UserProfile, String> phoneGetter = Getter.of(UserProfile::phone);
      Getter<UserProfile, String> faxGetter = Getter.of(UserProfile::fax);

      UserProfile profileWithEmail = new UserProfile("alice", "alice@example.com", null, null);
      UserProfile profileWithPhone = new UserProfile("bob", null, "123-456-7890", null);
      UserProfile profileWithFax = new UserProfile("charlie", null, null, "098-765-4321");
      UserProfile profileEmpty = new UserProfile("dave", null, null, null);

      // Try email, then phone, then fax, then default
      String contact1 =
          getMaybe(emailGetter, profileWithEmail)
              .orElseGet(
                  () ->
                      getMaybe(phoneGetter, profileWithEmail)
                          .orElseGet(
                              () -> getMaybe(faxGetter, profileWithEmail).orElse("No contact")));

      String contact2 =
          getMaybe(emailGetter, profileWithPhone)
              .orElseGet(
                  () ->
                      getMaybe(phoneGetter, profileWithPhone)
                          .orElseGet(
                              () -> getMaybe(faxGetter, profileWithPhone).orElse("No contact")));

      String contact3 =
          getMaybe(emailGetter, profileWithFax)
              .orElseGet(
                  () ->
                      getMaybe(phoneGetter, profileWithFax)
                          .orElseGet(
                              () -> getMaybe(faxGetter, profileWithFax).orElse("No contact")));

      String contact4 =
          getMaybe(emailGetter, profileEmpty)
              .orElseGet(
                  () ->
                      getMaybe(phoneGetter, profileEmpty)
                          .orElseGet(() -> getMaybe(faxGetter, profileEmpty).orElse("No contact")));

      assertThat(contact1).isEqualTo("alice@example.com");
      assertThat(contact2).isEqualTo("123-456-7890");
      assertThat(contact3).isEqualTo("098-765-4321");
      assertThat(contact4).isEqualTo("No contact");
    }
  }
}
