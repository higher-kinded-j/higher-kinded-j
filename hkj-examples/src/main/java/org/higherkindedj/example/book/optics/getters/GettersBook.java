// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.getters;

import static org.higherkindedj.optics.extensions.GetterExtensions.getMaybe;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.example.book.optics.cast.Address;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Getter;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/getters.html">Getters</a> page. The page
 * {@code {{#include}}}s the anchored regions, and {@code GettersBookTest} holds the claims the page
 * makes about this code.
 *
 * <p>Each method returns the values its region's comments show, in the order they appear.
 */
public final class GettersBook {

  private GettersBook() {}

  static List<Object> get(Address address) {
    // ANCHOR: get
    Person person = new Person("Jane", "Smith", 45, address);

    Getter<Person, String> fullName = Getter.of(p -> p.firstName() + " " + p.lastName());
    String name = fullName.get(person);
    // "Jane Smith"

    Getter<Person, Integer> age = Getter.of(Person::age);
    int years = age.get(person);
    // 45
    // ANCHOR_END: get
    return List.of(name, years);
  }

  static String compose() {
    // ANCHOR: compose
    Getter<Person, Address> addressGetter = Getter.of(Person::address);
    Getter<Address, String> cityGetter = Getter.of(Address::city);

    // Compose: Person → Address → String
    Getter<Person, String> personCity = addressGetter.andThen(cityGetter);

    Person person =
        new Person("Jane", "Smith", 45, new Address("123 Main St", "London", "EC1A 1BB"));

    String city = personCity.get(person);
    // "London"
    // ANCHOR_END: compose
    return city;
  }

  static int deepChain(Person ceo, List<Person> employees, Address headquarters) {
    // ANCHOR: deep_chain
    Getter<Company, Person> ceoGetter = Getter.of(Company::ceo);
    Getter<Person, String> fullNameGetter = Getter.of(p -> p.firstName() + " " + p.lastName());
    Getter<String, Integer> lengthGetter = Getter.of(String::length);

    // Compose: Company → Person → String → Integer
    Getter<Company, Integer> ceoNameLength =
        ceoGetter.andThen(fullNameGetter).andThen(lengthGetter);

    Company company = new Company("TechCorp", ceo, employees, headquarters);
    int length = ceoNameLength.get(company);
    // 10, the length of "Jane Smith"
    // ANCHOR_END: deep_chain
    return length;
  }

  static List<Object> asFold(Address address) {
    // ANCHOR: as_fold
    Getter<Person, Integer> ageGetter = Getter.of(Person::age);
    Person person = new Person("Jane", "Smith", 45, address);

    // preview() returns Optional with the single value
    Optional<Integer> age = ageGetter.preview(person);
    // Optional[45]

    // getAll() returns a single-element list
    List<Integer> ages = ageGetter.getAll(person);
    // [45]

    // exists() checks if the single value matches
    boolean isExperienced = ageGetter.exists(a -> a > 40, person);
    // true

    // all() checks the single value (always same as exists for Getter)
    boolean isSenior = ageGetter.all(a -> a >= 65, person);
    // false

    // find() returns the value if it matches
    Optional<Integer> foundAge = ageGetter.find(a -> a > 30, person);
    // Optional[45]

    // length() always returns 1 for Getter
    int count = ageGetter.length(person);
    // 1

    // isEmpty() always returns false for Getter
    boolean empty = ageGetter.isEmpty(person);
    // false
    // ANCHOR_END: as_fold
    return List.of(age, ages, isExperienced, isSenior, foundAge, count, empty);
  }

  static List<Object> withFolds(Company company, List<Person> employees) {
    // ANCHOR: with_folds
    Getter<Company, List<Person>> employeesGetter = Getter.of(Company::employees);
    Fold<List<Person>, Person> listFold = Fold.of(list -> list);
    Getter<Person, String> fullNameGetter = Getter.of(p -> p.firstName() + " " + p.lastName());

    // Company → List<Person> → Person (multiple) → String
    Fold<Company, String> allEmployeeNames =
        employeesGetter
            .asFold() // Convert Getter to Fold
            .andThen(listFold)
            .andThen(fullNameGetter.asFold());

    List<String> names = allEmployeeNames.getAll(company);
    // ["John Doe", "Alice Johnson", "Bob Williams"]

    boolean hasExperienced =
        listFold.andThen(Getter.of(Person::age).asFold()).exists(age -> age > 40, employees);
    // false: the oldest of them is 35
    // ANCHOR_END: with_folds
    return List.of(names, hasExperienced);
  }

  static List<Maybe<?>> getMaybeBasics() {
    // ANCHOR: get_maybe
    Getter<Person, String> firstNameGetter = Getter.of(Person::firstName);
    Getter<Person, Address> addressGetter = Getter.of(Person::address);

    Person person =
        new Person("Jane", "Smith", 45, new Address("123 Main St", "London", "NW1 4AB"));

    // Extract non-null value
    Maybe<String> name = getMaybe(firstNameGetter, person);
    // Just(Jane)

    // Extract nullable value
    Person personWithNullAddress = new Person("Bob", "Jones", 34, null);
    Maybe<Address> missingAddress = getMaybe(addressGetter, personWithNullAddress);
    // Nothing
    // ANCHOR_END: get_maybe
    return List.of(name, missingAddress);
  }

  static List<Maybe<String>> safeNavigation() {
    // ANCHOR: safe_navigation
    Getter<Person, Address> addressGetter = Getter.of(Person::address);
    Getter<Address, String> cityGetter = Getter.of(Address::city);

    // Safe navigation: Person → Maybe<Address> → Maybe<String>
    Person personWithAddress =
        new Person("Jane", "Smith", 45, new Address("123 Main St", "London", "NW1 4AB"));

    Maybe<String> city =
        getMaybe(addressGetter, personWithAddress).flatMap(addr -> getMaybe(cityGetter, addr));
    // Just(London)

    // Safe with null intermediate
    Person personWithNullAddress = new Person("Bob", "Jones", 34, null);

    Maybe<String> noCity =
        getMaybe(addressGetter, personWithNullAddress).flatMap(addr -> getMaybe(cityGetter, addr));
    // Nothing: the null address is handled safely
    // ANCHOR_END: safe_navigation
    return List.of(city, noCity);
  }

  static List<Object> maybeOperations() {
    // ANCHOR: maybe_operations
    Getter<Person, Address> addressGetter = Getter.of(Person::address);
    Getter<Address, String> cityGetter = Getter.of(Address::city);

    Person person =
        new Person("Jane", "Smith", 45, new Address("123 Main St", "London", "NW1 4AB"));

    // Extract and transform
    Maybe<String> uppercaseCity =
        getMaybe(addressGetter, person)
            .flatMap(addr -> getMaybe(cityGetter, addr))
            .map(String::toUpperCase);
    // Just(LONDON)

    // Extract with default
    String cityOrDefault =
        getMaybe(addressGetter, person)
            .flatMap(addr -> getMaybe(cityGetter, addr))
            .orElse("Unknown");
    // "London"

    // Extract and keep only values passing a test (Maybe has no filter; use flatMap)
    Maybe<String> longCityName =
        getMaybe(addressGetter, person)
            .flatMap(addr -> getMaybe(cityGetter, addr))
            .flatMap(name -> name.length() > 5 ? Maybe.just(name) : Maybe.nothing());
    // Just(London): its length is 6

    // Chain multiple operations
    String report =
        getMaybe(addressGetter, person)
            .flatMap(addr -> getMaybe(cityGetter, addr))
            .map(city -> "Person lives in " + city)
            .orElse("Address unknown");
    // "Person lives in London"
    // ANCHOR_END: maybe_operations
    return List.of(uppercaseCity, cityOrDefault, longCityName, report);
  }

  static String identity() {
    // ANCHOR: identity
    Getter<String, String> id = Getter.identity();
    String result = id.get("Hello");
    // "Hello"
    // ANCHOR_END: identity
    return result;
  }

  static int constant() {
    // ANCHOR: constant
    Getter<String, Integer> always42 = Getter.constant(42);
    int result = always42.get("anything");
    // 42
    // ANCHOR_END: constant
    return result;
  }
}

/** The page's company, around the package's {@code Person} and the cast's {@code Address}. */
record Company(String name, Person ceo, List<Person> employees, Address headquarters) {}
