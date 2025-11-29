// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Optic<S, T, A, B> Tests")
class OpticTest {

  // Test data structures
  record Person(String name, int age) {}

  record Employee(Person person, String department) {}

  record Wrapper<T>(T value) {}

  private Optic<Person, Person, String, String> nameOptic;
  private Optic<Person, Person, Integer, Integer> ageOptic;

  @BeforeEach
  void setUp() {
    // Create a simple optic for person name using Lens
    Lens<Person, String> nameLens = Lens.of(Person::name, (p, n) -> new Person(n, p.age()));
    nameOptic = nameLens;

    // Create a simple optic for person age using Lens
    Lens<Person, Integer> ageLens = Lens.of(Person::age, (p, a) -> new Person(p.name(), a));
    ageOptic = ageLens;
  }

  @Nested
  @DisplayName("andThen Composition")
  class AndThenComposition {

    @Test
    @DisplayName("andThen should compose two optics")
    void andThenComposesOptics() {
      Lens<Employee, Person> personLens =
          Lens.of(Employee::person, (e, p) -> new Employee(p, e.department()));

      // Compose using Optic.andThen
      Optic<Employee, Employee, String, String> employeeNameOptic = personLens.andThen(nameOptic);

      Employee employee = new Employee(new Person("Alice", 30), "Engineering");

      // Test modifyF
      Function<String, Kind<OptionalKind.Witness, String>> modifier =
          s -> OptionalKindHelper.OPTIONAL.widen(Optional.of(s.toUpperCase()));

      Kind<OptionalKind.Witness, Employee> result =
          employeeNameOptic.modifyF(modifier, employee, OptionalMonad.INSTANCE);

      assertThat(OptionalKindHelper.OPTIONAL.narrow(result))
          .isPresent()
          .hasValueSatisfying(e -> assertThat(e.person().name()).isEqualTo("ALICE"));
    }

    @Test
    @DisplayName("andThen should propagate empty through composition")
    void andThenPropagatesEmpty() {
      Lens<Employee, Person> personLens =
          Lens.of(Employee::person, (e, p) -> new Employee(p, e.department()));

      Optic<Employee, Employee, String, String> employeeNameOptic = personLens.andThen(nameOptic);

      Employee employee = new Employee(new Person("Alice", 30), "Engineering");

      // Modifier that returns empty
      Function<String, Kind<OptionalKind.Witness, String>> emptyModifier =
          s -> OptionalKindHelper.OPTIONAL.widen(Optional.empty());

      Kind<OptionalKind.Witness, Employee> result =
          employeeNameOptic.modifyF(emptyModifier, employee, OptionalMonad.INSTANCE);

      assertThat(OptionalKindHelper.OPTIONAL.narrow(result)).isEmpty();
    }
  }

  @Nested
  @DisplayName("contramap Operation")
  class ContramapOperation {

    @Test
    @DisplayName("contramap should pre-compose with a function on the source type")
    void contramapPreComposesSource() {
      // Create an optic that works on Employee by first extracting Person
      Optic<Employee, Person, String, String> employeeNameOptic =
          nameOptic.contramap(Employee::person);

      Employee employee = new Employee(new Person("Bob", 25), "Sales");

      Function<String, Kind<OptionalKind.Witness, String>> modifier =
          s -> OptionalKindHelper.OPTIONAL.widen(Optional.of("Mr. " + s));

      Kind<OptionalKind.Witness, Person> result =
          employeeNameOptic.modifyF(modifier, employee, OptionalMonad.INSTANCE);

      assertThat(OptionalKindHelper.OPTIONAL.narrow(result))
          .isPresent()
          .hasValueSatisfying(p -> assertThat(p.name()).isEqualTo("Mr. Bob"));
    }

    @Test
    @DisplayName("contramap should work with wrapper types")
    void contramapWithWrapper() {
      Wrapper<Person> wrappedPerson = new Wrapper<>(new Person("Charlie", 35));

      Optic<Wrapper<Person>, Person, String, String> unwrappedNameOptic =
          nameOptic.contramap(Wrapper::value);

      Function<String, Kind<OptionalKind.Witness, String>> modifier =
          s -> OptionalKindHelper.OPTIONAL.widen(Optional.of(s + " Jr."));

      Kind<OptionalKind.Witness, Person> result =
          unwrappedNameOptic.modifyF(modifier, wrappedPerson, OptionalMonad.INSTANCE);

      assertThat(OptionalKindHelper.OPTIONAL.narrow(result))
          .isPresent()
          .hasValueSatisfying(p -> assertThat(p.name()).isEqualTo("Charlie Jr."));
    }
  }

  @Nested
  @DisplayName("map Operation")
  class MapOperation {

    @Test
    @DisplayName("map should post-compose with a function on the target type")
    void mapPostComposesTarget() {
      // Create an optic that wraps the result in Wrapper
      Optic<Person, Wrapper<Person>, String, String> wrappingNameOptic =
          nameOptic.map(Wrapper::new);

      Person person = new Person("Diana", 28);

      Function<String, Kind<OptionalKind.Witness, String>> modifier =
          s -> OptionalKindHelper.OPTIONAL.widen(Optional.of(s.toLowerCase()));

      Kind<OptionalKind.Witness, Wrapper<Person>> result =
          wrappingNameOptic.modifyF(modifier, person, OptionalMonad.INSTANCE);

      assertThat(OptionalKindHelper.OPTIONAL.narrow(result))
          .isPresent()
          .hasValueSatisfying(w -> assertThat(w.value().name()).isEqualTo("diana"));
    }

    @Test
    @DisplayName("map should work with toString conversion")
    void mapWithToString() {
      Optic<Person, String, Integer, Integer> ageToStringOptic =
          ageOptic.map(p -> "Age: " + p.age());

      Person person = new Person("Eve", 40);

      Function<Integer, Kind<OptionalKind.Witness, Integer>> modifier =
          a -> OptionalKindHelper.OPTIONAL.widen(Optional.of(a + 1));

      Kind<OptionalKind.Witness, String> result =
          ageToStringOptic.modifyF(modifier, person, OptionalMonad.INSTANCE);

      assertThat(OptionalKindHelper.OPTIONAL.narrow(result)).isPresent().contains("Age: 41");
    }
  }

  @Nested
  @DisplayName("dimap Operation")
  class DimapOperation {

    @Test
    @DisplayName("dimap should apply both contravariant and covariant transformations")
    void dimapAppliesBothTransformations() {
      // Transform Employee -> Person on input, Person -> String on output
      Optic<Employee, String, String, String> employeeToDescriptionOptic =
          nameOptic.dimap(
              Employee::person, person -> person.name() + " (" + person.age() + " years old)");

      Employee employee = new Employee(new Person("Frank", 45), "HR");

      Function<String, Kind<OptionalKind.Witness, String>> modifier =
          s -> OptionalKindHelper.OPTIONAL.widen(Optional.of(s.toUpperCase()));

      Kind<OptionalKind.Witness, String> result =
          employeeToDescriptionOptic.modifyF(modifier, employee, OptionalMonad.INSTANCE);

      assertThat(OptionalKindHelper.OPTIONAL.narrow(result))
          .isPresent()
          .contains("FRANK (45 years old)");
    }

    @Test
    @DisplayName("dimap with identity functions should be equivalent to original")
    void dimapWithIdentities() {
      Optic<Person, Person, String, String> identityDimapped =
          nameOptic.dimap(Function.identity(), Function.identity());

      Person person = new Person("Grace", 33);

      Function<String, Kind<OptionalKind.Witness, String>> modifier =
          s -> OptionalKindHelper.OPTIONAL.widen(Optional.of("Ms. " + s));

      Kind<OptionalKind.Witness, Person> originalResult =
          nameOptic.modifyF(modifier, person, OptionalMonad.INSTANCE);

      Kind<OptionalKind.Witness, Person> dimappedResult =
          identityDimapped.modifyF(modifier, person, OptionalMonad.INSTANCE);

      assertThat(OptionalKindHelper.OPTIONAL.narrow(dimappedResult))
          .isEqualTo(OptionalKindHelper.OPTIONAL.narrow(originalResult));
    }

    @Test
    @DisplayName("dimap should work with complex transformations")
    void dimapComplexTransformations() {
      record Input(Employee employee) {}
      record Output(String description) {}

      Optic<Input, Output, String, String> complexOptic =
          nameOptic.dimap(
              input -> input.employee().person(),
              person -> new Output("Employee: " + person.name()));

      Input input = new Input(new Employee(new Person("Henry", 50), "Finance"));

      Function<String, Kind<OptionalKind.Witness, String>> modifier =
          s -> OptionalKindHelper.OPTIONAL.widen(Optional.of(s + " (verified)"));

      Kind<OptionalKind.Witness, Output> result =
          complexOptic.modifyF(modifier, input, OptionalMonad.INSTANCE);

      assertThat(OptionalKindHelper.OPTIONAL.narrow(result))
          .isPresent()
          .hasValueSatisfying(
              o -> assertThat(o.description()).isEqualTo("Employee: Henry (verified)"));
    }
  }

  @Nested
  @DisplayName("Composition Laws")
  class CompositionLaws {

    @Test
    @DisplayName("contramap should satisfy: contramap id = id")
    void contramapIdentityLaw() {
      Person person = new Person("Ivy", 29);

      Function<String, Kind<OptionalKind.Witness, String>> modifier =
          s -> OptionalKindHelper.OPTIONAL.widen(Optional.of(s + "!"));

      Kind<OptionalKind.Witness, Person> original =
          nameOptic.modifyF(modifier, person, OptionalMonad.INSTANCE);

      Optic<Person, Person, String, String> contramappedWithIdentity =
          nameOptic.contramap(Function.identity());

      Kind<OptionalKind.Witness, Person> contramapped =
          contramappedWithIdentity.modifyF(modifier, person, OptionalMonad.INSTANCE);

      assertThat(OptionalKindHelper.OPTIONAL.narrow(contramapped))
          .isEqualTo(OptionalKindHelper.OPTIONAL.narrow(original));
    }

    @Test
    @DisplayName("map should satisfy: map id = id")
    void mapIdentityLaw() {
      Person person = new Person("Jack", 31);

      Function<String, Kind<OptionalKind.Witness, String>> modifier =
          s -> OptionalKindHelper.OPTIONAL.widen(Optional.of(s + "?"));

      Kind<OptionalKind.Witness, Person> original =
          nameOptic.modifyF(modifier, person, OptionalMonad.INSTANCE);

      Optic<Person, Person, String, String> mappedWithIdentity = nameOptic.map(Function.identity());

      Kind<OptionalKind.Witness, Person> mapped =
          mappedWithIdentity.modifyF(modifier, person, OptionalMonad.INSTANCE);

      assertThat(OptionalKindHelper.OPTIONAL.narrow(mapped))
          .isEqualTo(OptionalKindHelper.OPTIONAL.narrow(original));
    }

    @Test
    @DisplayName("dimap(id, id) should be equivalent to the original optic")
    void dimapIdentityLaw() {
      Person person = new Person("Kate", 27);

      Function<String, Kind<OptionalKind.Witness, String>> modifier =
          s -> OptionalKindHelper.OPTIONAL.widen(Optional.of("[" + s + "]"));

      Kind<OptionalKind.Witness, Person> original =
          nameOptic.modifyF(modifier, person, OptionalMonad.INSTANCE);

      Optic<Person, Person, String, String> dimappedWithIdentities =
          nameOptic.dimap(Function.identity(), Function.identity());

      Kind<OptionalKind.Witness, Person> dimapped =
          dimappedWithIdentities.modifyF(modifier, person, OptionalMonad.INSTANCE);

      assertThat(OptionalKindHelper.OPTIONAL.narrow(dimapped))
          .isEqualTo(OptionalKindHelper.OPTIONAL.narrow(original));
    }
  }
}
