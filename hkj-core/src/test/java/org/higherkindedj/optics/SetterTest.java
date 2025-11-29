// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Setter<S, A> Tests")
class SetterTest {

  // Test Data Structures
  record Person(String name, int age) {}

  record Address(String street, String city) {}

  record Employee(Person person, Address address, String department) {}

  record Container(List<Integer> values) {}

  @Nested
  @DisplayName("Core Functionality")
  class CoreFunctionality {

    @Test
    @DisplayName("modify() should apply function to focused element")
    void modifyAppliesFunction() {
      Setter<Person, String> nameSetter =
          Setter.of(f -> person -> new Person(f.apply(person.name()), person.age()));

      Person person = new Person("john", 30);
      Person updated = nameSetter.modify(String::toUpperCase, person);

      assertThat(updated.name()).isEqualTo("JOHN");
      assertThat(updated.age()).isEqualTo(30);
    }

    @Test
    @DisplayName("set() should replace focused element with new value")
    void setReplacesValue() {
      Setter<Person, String> nameSetter =
          Setter.of(f -> person -> new Person(f.apply(person.name()), person.age()));

      Person person = new Person("John", 30);
      Person updated = nameSetter.set("Jane", person);

      assertThat(updated.name()).isEqualTo("Jane");
      assertThat(updated.age()).isEqualTo(30);
    }

    @Test
    @DisplayName("modify() should handle multiple modifications")
    void modifyMultipleTimes() {
      Setter<Person, Integer> ageSetter =
          Setter.of(f -> person -> new Person(person.name(), f.apply(person.age())));

      Person person = new Person("John", 30);
      Person older = ageSetter.modify(age -> age + 1, person);
      Person evenOlder = ageSetter.modify(age -> age + 1, older);

      assertThat(evenOlder.age()).isEqualTo(32);
    }

    @Test
    @DisplayName("fromGetSet() should create a Setter from getter/setter pair")
    void fromGetSetCreation() {
      Setter<Person, String> nameSetter =
          Setter.fromGetSet(Person::name, (p, name) -> new Person(name, p.age()));

      Person person = new Person("John", 30);
      Person updated = nameSetter.modify(String::toLowerCase, person);

      assertThat(updated.name()).isEqualTo("john");
    }

    @Test
    @DisplayName("identity() should apply function directly to source")
    void identitySetter() {
      Setter<String, String> idSetter = Setter.identity();

      String result = idSetter.modify(String::toUpperCase, "hello");
      assertThat(result).isEqualTo("HELLO");

      String setResult = idSetter.set("world", "hello");
      assertThat(setResult).isEqualTo("world");
    }

    @Test
    @DisplayName("of() modifyF should throw UnsupportedOperationException")
    void ofSetterModifyFThrowsException() {
      Setter<Person, String> nameSetter =
          Setter.of(f -> person -> new Person(f.apply(person.name()), person.age()));

      Person person = new Person("john", 30);

      Function<String, Kind<OptionalKind.Witness, String>> toUpper =
          s -> OptionalKindHelper.OPTIONAL.widen(Optional.of(s.toUpperCase()));

      assertThatThrownBy(() -> nameSetter.modifyF(toUpper, person, OptionalMonad.INSTANCE))
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessageContaining("modifyF is not supported for Setters created via of()");
    }

    @Test
    @DisplayName("identity() modifyF should apply effectful function directly")
    void identitySetterModifyF() {
      Setter<String, String> idSetter = Setter.identity();

      Function<String, Kind<OptionalKind.Witness, String>> toUpper =
          s -> OptionalKindHelper.OPTIONAL.widen(Optional.of(s.toUpperCase()));

      Kind<OptionalKind.Witness, String> result =
          idSetter.modifyF(toUpper, "hello", OptionalMonad.INSTANCE);

      Optional<String> optResult = OptionalKindHelper.OPTIONAL.narrow(result);
      assertThat(optResult).isPresent();
      assertThat(optResult.get()).isEqualTo("HELLO");
    }

    @Test
    @DisplayName("identity() modifyF should propagate failure")
    void identitySetterModifyFPropagatesFailure() {
      Setter<String, String> idSetter = Setter.identity();

      Function<String, Kind<OptionalKind.Witness, String>> failIfShort =
          s -> {
            if (s.length() > 10) {
              return OptionalKindHelper.OPTIONAL.widen(Optional.of(s.toUpperCase()));
            } else {
              return OptionalKindHelper.OPTIONAL.widen(Optional.empty());
            }
          };

      Kind<OptionalKind.Witness, String> result =
          idSetter.modifyF(failIfShort, "hello", OptionalMonad.INSTANCE);

      Optional<String> optResult = OptionalKindHelper.OPTIONAL.narrow(result);
      assertThat(optResult).isEmpty();
    }
  }

  @Nested
  @DisplayName("Collection Setters")
  class CollectionSetters {

    @Test
    @DisplayName("forList() should modify all elements in a list")
    void forListModifiesAll() {
      Setter<List<Integer>, Integer> listSetter = Setter.forList();

      List<Integer> numbers = List.of(1, 2, 3, 4, 5);
      List<Integer> doubled = listSetter.modify(x -> x * 2, numbers);

      assertThat(doubled).containsExactly(2, 4, 6, 8, 10);
    }

    @Test
    @DisplayName("forList() should set all elements to same value")
    void forListSetsAll() {
      Setter<List<String>, String> listSetter = Setter.forList();

      List<String> strings = List.of("a", "b", "c");
      List<String> allX = listSetter.set("x", strings);

      assertThat(allX).containsExactly("x", "x", "x");
    }

    @Test
    @DisplayName("forList() should handle empty list")
    void forListEmptyList() {
      Setter<List<Integer>, Integer> listSetter = Setter.forList();

      List<Integer> empty = List.of();
      List<Integer> result = listSetter.modify(x -> x * 2, empty);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("forMapValues() should modify all values in a map")
    void forMapValuesModifiesAll() {
      Setter<Map<String, Integer>, Integer> mapSetter = Setter.forMapValues();

      Map<String, Integer> scores = Map.of("Alice", 85, "Bob", 90, "Charlie", 78);
      Map<String, Integer> curved = mapSetter.modify(score -> score + 5, scores);

      assertThat(curved)
          .containsEntry("Alice", 90)
          .containsEntry("Bob", 95)
          .containsEntry("Charlie", 83);
    }

    @Test
    @DisplayName("forMapValues() should set all values to same value")
    void forMapValuesSetsAll() {
      Setter<Map<String, Integer>, Integer> mapSetter = Setter.forMapValues();

      Map<String, Integer> scores = Map.of("Alice", 85, "Bob", 90);
      Map<String, Integer> reset = mapSetter.set(0, scores);

      assertThat(reset).containsEntry("Alice", 0).containsEntry("Bob", 0);
    }

    @Test
    @DisplayName("forMapValues() should handle empty map")
    void forMapValuesEmptyMap() {
      Setter<Map<String, Integer>, Integer> mapSetter = Setter.forMapValues();

      Map<String, Integer> empty = Map.of();
      Map<String, Integer> result = mapSetter.modify(x -> x * 2, empty);

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("Composition")
  class Composition {

    @Test
    @DisplayName("andThen() should compose two Setters")
    void composeSetters() {
      Setter<Employee, Person> personSetter =
          Setter.fromGetSet(
              Employee::person, (e, p) -> new Employee(p, e.address(), e.department()));

      Setter<Person, String> nameSetter =
          Setter.fromGetSet(Person::name, (p, name) -> new Person(name, p.age()));

      Setter<Employee, String> employeeNameSetter = personSetter.andThen(nameSetter);

      Employee employee =
          new Employee(new Person("John", 30), new Address("123 Main St", "NYC"), "Engineering");

      Employee updated = employeeNameSetter.modify(String::toUpperCase, employee);

      assertThat(updated.person().name()).isEqualTo("JOHN");
      assertThat(updated.person().age()).isEqualTo(30);
      assertThat(updated.address()).isEqualTo(employee.address());
    }

    @Test
    @DisplayName("andThen() should compose multiple Setters in chain")
    void composeMultipleSetters() {
      Setter<Employee, Address> addressSetter =
          Setter.fromGetSet(
              Employee::address, (e, a) -> new Employee(e.person(), a, e.department()));

      Setter<Address, String> citySetter =
          Setter.fromGetSet(Address::city, (a, city) -> new Address(a.street(), city));

      Setter<Employee, String> employeeCitySetter = addressSetter.andThen(citySetter);

      Employee employee =
          new Employee(new Person("Jane", 25), new Address("456 Oak Ave", "Boston"), "Marketing");

      Employee relocated = employeeCitySetter.set("San Francisco", employee);

      assertThat(relocated.address().city()).isEqualTo("San Francisco");
      assertThat(relocated.address().street()).isEqualTo("456 Oak Ave");
    }

    @Test
    @DisplayName("composed Setter should work with collections")
    void composedSetterWithCollections() {
      Setter<Container, List<Integer>> valuesSetter =
          Setter.fromGetSet(Container::values, (c, v) -> new Container(v));

      Setter<List<Integer>, Integer> listSetter = Setter.forList();

      Setter<Container, Integer> containerElementsSetter = valuesSetter.andThen(listSetter);

      Container container = new Container(List.of(1, 2, 3));
      Container squared = containerElementsSetter.modify(x -> x * x, container);

      assertThat(squared.values()).containsExactly(1, 4, 9);
    }
  }

  @Nested
  @DisplayName("Effectful Modification (modifyF)")
  class EffectfulModification {

    @Test
    @DisplayName("modifyF should work with Optional applicative")
    void modifyFWithOptional() {
      Setter<Person, Integer> ageSetter =
          Setter.fromGetSet(Person::age, (p, age) -> new Person(p.name(), age));

      Person person = new Person("John", 30);

      // Increment age if it's less than 100
      Function<Integer, Kind<OptionalKind.Witness, Integer>> incrementIfValid =
          age -> {
            if (age < 100) {
              return OptionalKindHelper.OPTIONAL.widen(Optional.of(age + 1));
            } else {
              return OptionalKindHelper.OPTIONAL.widen(Optional.empty());
            }
          };

      Kind<OptionalKind.Witness, Person> result =
          ageSetter.modifyF(incrementIfValid, person, OptionalMonad.INSTANCE);

      Optional<Person> optResult = OptionalKindHelper.OPTIONAL.narrow(result);
      assertThat(optResult).isPresent();
      assertThat(optResult.get().age()).isEqualTo(31);
    }

    @Test
    @DisplayName("modifyF with forList() should sequence effects")
    void modifyFListSequencesEffects() {
      Setter<List<Integer>, Integer> listSetter = Setter.forList();

      List<Integer> numbers = List.of(1, 2, 3);

      // Parse each number as string, failing if negative
      Function<Integer, Kind<OptionalKind.Witness, Integer>> doubleIfPositive =
          n -> {
            if (n > 0) {
              return OptionalKindHelper.OPTIONAL.widen(Optional.of(n * 2));
            } else {
              return OptionalKindHelper.OPTIONAL.widen(Optional.empty());
            }
          };

      Kind<OptionalKind.Witness, List<Integer>> result =
          listSetter.modifyF(doubleIfPositive, numbers, OptionalMonad.INSTANCE);

      Optional<List<Integer>> optResult = OptionalKindHelper.OPTIONAL.narrow(result);
      assertThat(optResult).isPresent();
      assertThat(optResult.get()).containsExactly(2, 4, 6);
    }

    @Test
    @DisplayName("modifyF should fail fast when effect fails")
    void modifyFFailsOnFirstFailure() {
      Setter<List<Integer>, Integer> listSetter = Setter.forList();

      List<Integer> numbers = List.of(1, -2, 3); // -2 will cause failure

      Function<Integer, Kind<OptionalKind.Witness, Integer>> doubleIfPositive =
          n -> {
            if (n > 0) {
              return OptionalKindHelper.OPTIONAL.widen(Optional.of(n * 2));
            } else {
              return OptionalKindHelper.OPTIONAL.widen(Optional.empty());
            }
          };

      Kind<OptionalKind.Witness, List<Integer>> result =
          listSetter.modifyF(doubleIfPositive, numbers, OptionalMonad.INSTANCE);

      Optional<List<Integer>> optResult = OptionalKindHelper.OPTIONAL.narrow(result);
      assertThat(optResult).isEmpty();
    }

    @Test
    @DisplayName("modifyF with forMapValues() should sequence effects")
    void modifyFMapValuesSequencesEffects() {
      Setter<Map<String, Integer>, Integer> mapSetter = Setter.forMapValues();

      Map<String, Integer> scores = Map.of("Alice", 85, "Bob", 90, "Charlie", 78);

      // Add bonus if score is above 70
      Function<Integer, Kind<OptionalKind.Witness, Integer>> addBonusIfPassing =
          score -> {
            if (score > 70) {
              return OptionalKindHelper.OPTIONAL.widen(Optional.of(score + 10));
            } else {
              return OptionalKindHelper.OPTIONAL.widen(Optional.empty());
            }
          };

      Kind<OptionalKind.Witness, Map<String, Integer>> result =
          mapSetter.modifyF(addBonusIfPassing, scores, OptionalMonad.INSTANCE);

      Optional<Map<String, Integer>> optResult = OptionalKindHelper.OPTIONAL.narrow(result);
      assertThat(optResult).isPresent();
      assertThat(optResult.get())
          .containsEntry("Alice", 95)
          .containsEntry("Bob", 100)
          .containsEntry("Charlie", 88);
    }

    @Test
    @DisplayName("modifyF with forMapValues() should fail when any value fails")
    void modifyFMapValuesFailsOnFailure() {
      Setter<Map<String, Integer>, Integer> mapSetter = Setter.forMapValues();

      Map<String, Integer> scores = Map.of("Alice", 85, "Bob", 50, "Charlie", 78);

      // Add bonus only if score is above 70
      Function<Integer, Kind<OptionalKind.Witness, Integer>> addBonusIfPassing =
          score -> {
            if (score > 70) {
              return OptionalKindHelper.OPTIONAL.widen(Optional.of(score + 10));
            } else {
              return OptionalKindHelper.OPTIONAL.widen(Optional.empty());
            }
          };

      Kind<OptionalKind.Witness, Map<String, Integer>> result =
          mapSetter.modifyF(addBonusIfPassing, scores, OptionalMonad.INSTANCE);

      Optional<Map<String, Integer>> optResult = OptionalKindHelper.OPTIONAL.narrow(result);
      assertThat(optResult).isEmpty();
    }

    @Test
    @DisplayName("modifyF with forMapValues() should handle empty map")
    void modifyFMapValuesEmptyMap() {
      Setter<Map<String, Integer>, Integer> mapSetter = Setter.forMapValues();

      Map<String, Integer> empty = Map.of();

      Function<Integer, Kind<OptionalKind.Witness, Integer>> double_ =
          n -> OptionalKindHelper.OPTIONAL.widen(Optional.of(n * 2));

      Kind<OptionalKind.Witness, Map<String, Integer>> result =
          mapSetter.modifyF(double_, empty, OptionalMonad.INSTANCE);

      Optional<Map<String, Integer>> optResult = OptionalKindHelper.OPTIONAL.narrow(result);
      assertThat(optResult).isPresent();
      assertThat(optResult.get()).isEmpty();
    }

    @Test
    @DisplayName("composed Setter modifyF should thread effects through composition")
    void composedModifyF() {
      Setter<Employee, Person> personSetter =
          Setter.fromGetSet(
              Employee::person, (e, p) -> new Employee(p, e.address(), e.department()));

      Setter<Person, Integer> ageSetter =
          Setter.fromGetSet(Person::age, (p, age) -> new Person(p.name(), age));

      Setter<Employee, Integer> employeeAgeSetter = personSetter.andThen(ageSetter);

      Employee employee =
          new Employee(new Person("John", 30), new Address("123 Main St", "NYC"), "Engineering");

      Function<Integer, Kind<OptionalKind.Witness, Integer>> incrementIfValid =
          age -> {
            if (age < 100) {
              return OptionalKindHelper.OPTIONAL.widen(Optional.of(age + 1));
            } else {
              return OptionalKindHelper.OPTIONAL.widen(Optional.empty());
            }
          };

      Kind<OptionalKind.Witness, Employee> result =
          employeeAgeSetter.modifyF(incrementIfValid, employee, OptionalMonad.INSTANCE);

      Optional<Employee> optResult = OptionalKindHelper.OPTIONAL.narrow(result);
      assertThat(optResult).isPresent();
      assertThat(optResult.get().person().age()).isEqualTo(31);
    }
  }

  @Nested
  @DisplayName("Conversion to Other Optics")
  class ConversionToOtherOptics {

    @Test
    @DisplayName("asTraversal() should create a valid Traversal")
    void asTraversalCreatesTraversal() {
      Setter<Person, String> nameSetter =
          Setter.fromGetSet(Person::name, (p, name) -> new Person(name, p.age()));

      Traversal<Person, String> nameTraversal = nameSetter.asTraversal();

      Person person = new Person("John", 30);

      Function<String, Kind<OptionalKind.Witness, String>> toUpper =
          s -> OptionalKindHelper.OPTIONAL.widen(Optional.of(s.toUpperCase()));

      Kind<OptionalKind.Witness, Person> result =
          nameTraversal.modifyF(toUpper, person, OptionalMonad.INSTANCE);

      Optional<Person> optResult = OptionalKindHelper.OPTIONAL.narrow(result);
      assertThat(optResult).isPresent();
      assertThat(optResult.get().name()).isEqualTo("JOHN");
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCases {

    @Test
    @DisplayName("Setter should preserve unchanged parts of structure")
    void preserveUnchangedParts() {
      Setter<Employee, String> departmentSetter =
          Setter.fromGetSet(
              Employee::department, (e, dept) -> new Employee(e.person(), e.address(), dept));

      Employee original =
          new Employee(new Person("John", 30), new Address("123 Main St", "NYC"), "Engineering");

      Employee updated = departmentSetter.set("Marketing", original);

      assertThat(updated.person()).isEqualTo(original.person());
      assertThat(updated.address()).isEqualTo(original.address());
      assertThat(updated.department()).isEqualTo("Marketing");
    }

    @Test
    @DisplayName("Setter should handle identity modification")
    void identityModification() {
      Setter<Person, String> nameSetter =
          Setter.fromGetSet(Person::name, (p, name) -> new Person(name, p.age()));

      Person person = new Person("John", 30);
      Person same = nameSetter.modify(Function.identity(), person);

      assertThat(same).isEqualTo(person);
    }

    @Test
    @DisplayName("Setter should work with nullable values")
    void handleNullableValues() {
      record NullablePerson(String name, Integer age) {}

      Setter<NullablePerson, Integer> ageSetter =
          Setter.fromGetSet(NullablePerson::age, (p, age) -> new NullablePerson(p.name(), age));

      NullablePerson withNull = new NullablePerson("John", null);
      NullablePerson updated = ageSetter.set(25, withNull);

      assertThat(updated.age()).isEqualTo(25);
    }
  }
}
