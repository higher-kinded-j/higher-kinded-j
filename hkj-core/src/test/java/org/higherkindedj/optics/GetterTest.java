// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Monoid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Getter<S, A> Tests")
class GetterTest {

  // Test Data Structures
  record Person(String firstName, String lastName, int age) {}

  record Address(String street, String city, String zipCode) {}

  record Employee(Person person, Address address, String department) {}

  // Simple Monoid for testing
  private static final Monoid<Integer> SUM_MONOID =
      new Monoid<>() {
        @Override
        public Integer empty() {
          return 0;
        }

        @Override
        public Integer combine(Integer a, Integer b) {
          return a + b;
        }
      };

  private static final Monoid<String> CONCAT_MONOID =
      new Monoid<>() {
        @Override
        public String empty() {
          return "";
        }

        @Override
        public String combine(String a, String b) {
          return a + b;
        }
      };

  @Nested
  @DisplayName("Core Functionality")
  class CoreFunctionality {

    @Test
    @DisplayName("of() should create a Getter from a function")
    void ofCreation() {
      Getter<Person, String> firstNameGetter = Getter.of(Person::firstName);

      Person person = new Person("John", "Doe", 30);
      String firstName = firstNameGetter.get(person);

      assertThat(firstName).isEqualTo("John");
    }

    @Test
    @DisplayName("to() should create a Getter from a function (alias for of)")
    void toCreation() {
      Getter<String, Integer> lengthGetter = Getter.to(String::length);

      int length = lengthGetter.get("Hello");
      assertThat(length).isEqualTo(5);
    }

    @Test
    @DisplayName("get() should extract the focused value")
    void getExtraction() {
      Getter<Person, Integer> ageGetter = Getter.of(Person::age);

      Person person = new Person("Jane", "Smith", 25);
      int age = ageGetter.get(person);

      assertThat(age).isEqualTo(25);
    }

    @Test
    @DisplayName("get() should work with computed values")
    void getComputedValue() {
      Getter<Person, String> fullNameGetter = Getter.of(p -> p.firstName() + " " + p.lastName());

      Person person = new Person("John", "Doe", 30);
      String fullName = fullNameGetter.get(person);

      assertThat(fullName).isEqualTo("John Doe");
    }

    @Test
    @DisplayName("constant() should create a Getter that always returns the same value")
    void constantGetter() {
      Getter<String, Integer> always42 = Getter.constant(42);

      assertThat(always42.get("anything")).isEqualTo(42);
      assertThat(always42.get("something else")).isEqualTo(42);
      assertThat(always42.get("")).isEqualTo(42);
    }

    @Test
    @DisplayName("identity() should create a Getter that returns the source itself")
    void identityGetter() {
      Getter<String, String> id = Getter.identity();

      assertThat(id.get("hello")).isEqualTo("hello");
      assertThat(id.get("world")).isEqualTo("world");
    }
  }

  @Nested
  @DisplayName("Fold Inheritance")
  class FoldInheritance {

    @Test
    @DisplayName("foldMap() should work correctly for single element")
    void foldMapSingleElement() {
      Getter<Person, Integer> ageGetter = Getter.of(Person::age);

      Person person = new Person("John", "Doe", 30);
      int result = ageGetter.foldMap(SUM_MONOID, age -> age * 2, person);

      assertThat(result).isEqualTo(60);
    }

    @Test
    @DisplayName("getAll() should return a list with exactly one element")
    void getAllSingleElement() {
      Getter<Person, String> firstNameGetter = Getter.of(Person::firstName);

      Person person = new Person("Jane", "Smith", 25);
      List<String> names = firstNameGetter.getAll(person);

      assertThat(names).containsExactly("Jane");
    }

    @Test
    @DisplayName("preview() should return Optional with the value")
    void previewReturnsValue() {
      Getter<Person, String> lastNameGetter = Getter.of(Person::lastName);

      Person person = new Person("John", "Doe", 30);
      Optional<String> lastName = lastNameGetter.preview(person);

      assertThat(lastName).contains("Doe");
    }

    @Test
    @DisplayName("length() should return 1 for Getter")
    void lengthIsOne() {
      Getter<Person, Integer> ageGetter = Getter.of(Person::age);

      Person person = new Person("Jane", "Smith", 25);
      int count = ageGetter.length(person);

      assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("isEmpty() should return false for Getter")
    void isEmptyIsFalse() {
      Getter<Person, String> firstNameGetter = Getter.of(Person::firstName);

      Person person = new Person("John", "Doe", 30);
      boolean empty = firstNameGetter.isEmpty(person);

      assertThat(empty).isFalse();
    }

    @Test
    @DisplayName("exists() should check if the single value matches predicate")
    void existsChecksPredicate() {
      Getter<Person, Integer> ageGetter = Getter.of(Person::age);

      Person adult = new Person("John", "Doe", 30);
      Person minor = new Person("Jane", "Doe", 15);

      assertThat(ageGetter.exists(age -> age >= 18, adult)).isTrue();
      assertThat(ageGetter.exists(age -> age >= 18, minor)).isFalse();
    }

    @Test
    @DisplayName("all() should check if the single value matches predicate")
    void allChecksPredicate() {
      Getter<Person, String> firstNameGetter = Getter.of(Person::firstName);

      Person person = new Person("John", "Doe", 30);

      assertThat(firstNameGetter.all(name -> name.length() > 3, person)).isTrue();
      assertThat(firstNameGetter.all(name -> name.length() > 10, person)).isFalse();
    }

    @Test
    @DisplayName("find() should return the value if it matches")
    void findReturnsMatching() {
      Getter<Person, Integer> ageGetter = Getter.of(Person::age);

      Person person = new Person("John", "Doe", 30);

      Optional<Integer> found = ageGetter.find(age -> age > 25, person);
      Optional<Integer> notFound = ageGetter.find(age -> age > 35, person);

      assertThat(found).contains(30);
      assertThat(notFound).isEmpty();
    }

    @Test
    @DisplayName("asFold() should return itself")
    void asFoldReturnsItself() {
      Getter<Person, String> getter = Getter.of(Person::firstName);
      Fold<Person, String> fold = getter.asFold();

      assertThat(fold).isSameAs(getter);
    }
  }

  @Nested
  @DisplayName("Composition")
  class Composition {

    @Test
    @DisplayName("andThen() should compose two Getters")
    void composeGetters() {
      Getter<Employee, Person> personGetter = Getter.of(Employee::person);
      Getter<Person, String> firstNameGetter = Getter.of(Person::firstName);

      Getter<Employee, String> employeeFirstName = personGetter.andThen(firstNameGetter);

      Employee employee =
          new Employee(
              new Person("John", "Doe", 30),
              new Address("123 Main St", "New York", "10001"),
              "Engineering");

      assertThat(employeeFirstName.get(employee)).isEqualTo("John");
    }

    @Test
    @DisplayName("andThen() should compose multiple Getters in chain")
    void composeMultipleGetters() {
      Getter<Employee, Address> addressGetter = Getter.of(Employee::address);
      Getter<Address, String> cityGetter = Getter.of(Address::city);
      Getter<String, Integer> lengthGetter = Getter.of(String::length);

      Getter<Employee, Integer> cityNameLength =
          addressGetter.andThen(cityGetter).andThen(lengthGetter);

      Employee employee =
          new Employee(
              new Person("Jane", "Smith", 25),
              new Address("456 Oak Ave", "Los Angeles", "90001"),
              "Marketing");

      assertThat(cityNameLength.get(employee)).isEqualTo(11); // "Los Angeles".length()
    }

    @Test
    @DisplayName("composed Getter should inherit Fold operations")
    void composedGetterFoldOperations() {
      Getter<Employee, Person> personGetter = Getter.of(Employee::person);
      Getter<Person, Integer> ageGetter = Getter.of(Person::age);

      Getter<Employee, Integer> employeeAge = personGetter.andThen(ageGetter);

      Employee employee =
          new Employee(
              new Person("John", "Doe", 30),
              new Address("123 Main St", "New York", "10001"),
              "Engineering");

      // Test Fold operations on composed Getter
      assertThat(employeeAge.length(employee)).isEqualTo(1);
      assertThat(employeeAge.exists(age -> age > 25, employee)).isTrue();
      assertThat(employeeAge.preview(employee)).contains(30);
    }
  }

  @Nested
  @DisplayName("Pair Operations")
  class PairOperations {

    @Test
    @DisplayName("first() should extract the first element of a pair")
    void firstElementOfPair() {
      Getter<Map.Entry<String, Integer>, String> firstGetter = Getter.first();

      Map.Entry<String, Integer> pair = new AbstractMap.SimpleEntry<>("hello", 42);
      assertThat(firstGetter.get(pair)).isEqualTo("hello");
    }

    @Test
    @DisplayName("second() should extract the second element of a pair")
    void secondElementOfPair() {
      Getter<Map.Entry<String, Integer>, Integer> secondGetter = Getter.second();

      Map.Entry<String, Integer> pair = new AbstractMap.SimpleEntry<>("hello", 42);
      assertThat(secondGetter.get(pair)).isEqualTo(42);
    }

    @Test
    @DisplayName("first() and second() should compose with other Getters")
    void pairGettersCompose() {
      Getter<Map.Entry<Person, Address>, Person> firstGetter = Getter.first();
      Getter<Person, String> firstNameGetter = Getter.of(Person::firstName);

      Getter<Map.Entry<Person, Address>, String> getName = firstGetter.andThen(firstNameGetter);

      Map.Entry<Person, Address> pair =
          new AbstractMap.SimpleEntry<>(
              new Person("John", "Doe", 30), new Address("123 Main St", "NYC", "10001"));

      assertThat(getName.get(pair)).isEqualTo("John");
    }
  }

  @Nested
  @DisplayName("Integration with Other Optics")
  class IntegrationWithOtherOptics {

    @Test
    @DisplayName("Getter should compose with Fold (via andThen on Fold)")
    void composeWithFold() {
      Getter<Employee, Person> personGetter = Getter.of(Employee::person);

      // Use the Getter's inherited andThen(Fold) from Fold interface
      Fold<Person, String> bothNames =
          new Fold<>() {
            @Override
            public <M> M foldMap(
                Monoid<M> monoid, Function<? super String, ? extends M> f, Person source) {
              return monoid.combine(f.apply(source.firstName()), f.apply(source.lastName()));
            }
          };

      Fold<Employee, String> employeeNames = personGetter.andThen(bothNames);

      Employee employee =
          new Employee(
              new Person("John", "Doe", 30),
              new Address("123 Main St", "New York", "10001"),
              "Engineering");

      List<String> names = employeeNames.getAll(employee);
      assertThat(names).containsExactly("John", "Doe");
    }

    @Test
    @DisplayName("Getter from Lens should work correctly")
    void getterFromLens() {
      // Create a Getter that's effectively derived from a Lens pattern
      Getter<Person, String> firstNameGetter = Getter.of(Person::firstName);

      // This is equivalent to what Lens.asFold().preview() would do
      Person person = new Person("Jane", "Smith", 25);
      assertThat(firstNameGetter.preview(person)).contains("Jane");
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCases {

    @Test
    @DisplayName("Getter should handle null values in computed results")
    void handleNullValues() {
      record NullableData(String value) {}

      Getter<NullableData, String> valueGetter = Getter.of(NullableData::value);

      NullableData nullData = new NullableData(null);
      assertThat(valueGetter.get(nullData)).isNull();
    }

    @Test
    @DisplayName("Getter should work with complex transformations")
    void complexTransformations() {
      Getter<List<Integer>, Integer> sumGetter =
          Getter.of(list -> list.stream().mapToInt(Integer::intValue).sum());

      List<Integer> numbers = List.of(1, 2, 3, 4, 5);
      assertThat(sumGetter.get(numbers)).isEqualTo(15);
    }

    @Test
    @DisplayName("Getter with foldMap should correctly apply monoid")
    void foldMapMonoidCorrectness() {
      Getter<Person, String> fullNameGetter = Getter.of(p -> p.firstName() + " " + p.lastName());

      Person person = new Person("John", "Doe", 30);

      // foldMap with identity should return the transformed value directly
      String result = fullNameGetter.foldMap(CONCAT_MONOID, Function.identity(), person);
      assertThat(result).isEqualTo("John Doe");

      // foldMap with transformation
      String upperResult = fullNameGetter.foldMap(CONCAT_MONOID, String::toUpperCase, person);
      assertThat(upperResult).isEqualTo("JOHN DOE");
    }
  }
}
