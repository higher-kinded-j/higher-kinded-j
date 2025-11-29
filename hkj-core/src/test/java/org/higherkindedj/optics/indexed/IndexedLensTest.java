// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.indexed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.util.IndexedTraversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("IndexedLens<I, S, A> Tests")
class IndexedLensTest {

  // Test Data Structures
  record User(String name, int age, String email) {
    User withName(String newName) {
      return new User(newName, age, email);
    }

    User withAge(int newAge) {
      return new User(name, newAge, email);
    }

    User withEmail(String newEmail) {
      return new User(name, age, newEmail);
    }
  }

  record Address(String street, String city, String zip) {
    Address withStreet(String newStreet) {
      return new Address(newStreet, city, zip);
    }

    Address withCity(String newCity) {
      return new Address(street, newCity, zip);
    }
  }

  record Person(String id, User user, Address address) {
    Person withUser(User newUser) {
      return new Person(id, newUser, address);
    }

    Person withAddress(Address newAddress) {
      return new Person(id, user, newAddress);
    }
  }

  @Nested
  @DisplayName("of() - Factory method")
  class OfTests {

    @Test
    @DisplayName("should create indexed lens with correct index")
    void createWithIndex() {
      IndexedLens<String, User, String> nameLens =
          IndexedLens.of("name", User::name, User::withName);

      assertThat(nameLens.index()).isEqualTo("name");
    }

    @Test
    @DisplayName("should get value correctly")
    void getValue() {
      IndexedLens<String, User, String> nameLens =
          IndexedLens.of("name", User::name, User::withName);
      User user = new User("Alice", 30, "alice@example.com");

      String name = nameLens.get(user);

      assertThat(name).isEqualTo("Alice");
    }

    @Test
    @DisplayName("should set value correctly")
    void setValue() {
      IndexedLens<String, User, String> nameLens =
          IndexedLens.of("name", User::name, User::withName);
      User user = new User("Alice", 30, "alice@example.com");

      User updated = nameLens.set("Bob", user);

      assertThat(updated.name()).isEqualTo("Bob");
      assertThat(updated.age()).isEqualTo(30);
      assertThat(updated.email()).isEqualTo("alice@example.com");
    }
  }

  @Nested
  @DisplayName("iget() - Get with index")
  class IGetTests {

    @Test
    @DisplayName("should return index-value pair")
    void getIndexValuePair() {
      IndexedLens<String, User, Integer> ageLens = IndexedLens.of("age", User::age, User::withAge);
      User user = new User("Alice", 30, "alice@example.com");

      Pair<String, Integer> indexed = ageLens.iget(user);

      assertThat(indexed.first()).isEqualTo("age");
      assertThat(indexed.second()).isEqualTo(30);
    }
  }

  @Nested
  @DisplayName("imodify() - Modify with index")
  class IModifyTests {

    @Test
    @DisplayName("should modify using both index and value")
    void modifyWithIndex() {
      IndexedLens<String, User, String> nameLens =
          IndexedLens.of("name", User::name, User::withName);
      User user = new User("Alice", 30, "alice@example.com");

      User updated = nameLens.imodify((fieldName, value) -> value + " [" + fieldName + "]", user);

      assertThat(updated.name()).isEqualTo("Alice [name]");
    }

    @Test
    @DisplayName("should use index for conditional logic")
    void conditionalModifyByIndex() {
      IndexedLens<String, User, String> emailLens =
          IndexedLens.of("email", User::email, User::withEmail);
      User user = new User("Alice", 30, "alice@example.com");

      User updated =
          emailLens.imodify(
              (field, value) -> {
                if (field.equals("email")) {
                  return value.toUpperCase();
                }
                return value;
              },
              user);

      assertThat(updated.email()).isEqualTo("ALICE@EXAMPLE.COM");
    }
  }

  @Nested
  @DisplayName("modify() - Modify without index")
  class ModifyTests {

    @Test
    @DisplayName("should modify value ignoring index")
    void modifyWithoutIndex() {
      IndexedLens<String, User, Integer> ageLens = IndexedLens.of("age", User::age, User::withAge);
      User user = new User("Alice", 30, "alice@example.com");

      User updated = ageLens.modify(age -> age + 1, user);

      assertThat(updated.age()).isEqualTo(31);
    }
  }

  @Nested
  @DisplayName("imodifyF() - Effectful modification with index")
  class IModifyFTests {

    @Test
    @DisplayName("should modify with effectful function using index")
    void effectfulModifyWithIndex() {
      IndexedLens<String, User, Integer> ageLens = IndexedLens.of("age", User::age, User::withAge);
      User user = new User("Alice", 30, "alice@example.com");

      // Use Optional as effect - fail if field is "age" and value > 100
      Kind<OptionalKind.Witness, User> result =
          ageLens.imodifyF(
              (field, age) -> {
                if (field.equals("age") && age > 100) {
                  return OptionalKindHelper.OPTIONAL.widen(Optional.empty());
                }
                return OptionalKindHelper.OPTIONAL.widen(Optional.of(age + 1));
              },
              user,
              OptionalMonad.INSTANCE);

      Optional<User> unwrapped = OptionalKindHelper.OPTIONAL.narrow(result);
      assertThat(unwrapped).isPresent();
      assertThat(unwrapped.get().age()).isEqualTo(31);
    }

    @Test
    @DisplayName("should propagate effect failure")
    void effectFailure() {
      IndexedLens<String, User, Integer> ageLens = IndexedLens.of("age", User::age, User::withAge);
      User user = new User("Alice", 150, "alice@example.com");

      Kind<OptionalKind.Witness, User> result =
          ageLens.imodifyF(
              (field, age) -> {
                if (age > 100) {
                  return OptionalKindHelper.OPTIONAL.widen(Optional.empty());
                }
                return OptionalKindHelper.OPTIONAL.widen(Optional.of(age + 1));
              },
              user,
              OptionalMonad.INSTANCE);

      Optional<User> unwrapped = OptionalKindHelper.OPTIONAL.narrow(result);
      assertThat(unwrapped).isEmpty();
    }
  }

  @Nested
  @DisplayName("from() - Create from existing Lens")
  class FromTests {

    @Test
    @DisplayName("should wrap existing lens with index")
    void wrapExistingLens() {
      Lens<User, String> regularNameLens = Lens.of(User::name, User::withName);
      IndexedLens<String, User, String> indexedNameLens =
          IndexedLens.from("userName", regularNameLens);

      User user = new User("Alice", 30, "alice@example.com");

      assertThat(indexedNameLens.index()).isEqualTo("userName");
      assertThat(indexedNameLens.get(user)).isEqualTo("Alice");
      assertThat(indexedNameLens.set("Bob", user).name()).isEqualTo("Bob");
    }

    @Test
    @DisplayName("modifyF should delegate to underlying lens")
    void modifyFDelegatesToUnderlyingLens() {
      Lens<User, Integer> regularAgeLens = Lens.of(User::age, User::withAge);
      IndexedLens<String, User, Integer> indexedAgeLens =
          IndexedLens.from("userAge", regularAgeLens);

      User user = new User("Alice", 30, "alice@example.com");

      // Use modifyF with Optional
      Function<Integer, Kind<OptionalKind.Witness, Integer>> incrementIfValid =
          age -> {
            if (age < 100) {
              return OptionalKindHelper.OPTIONAL.widen(Optional.of(age + 1));
            } else {
              return OptionalKindHelper.OPTIONAL.widen(Optional.empty());
            }
          };

      Kind<OptionalKind.Witness, User> result =
          indexedAgeLens.modifyF(incrementIfValid, user, OptionalMonad.INSTANCE);

      Optional<User> optResult = OptionalKindHelper.OPTIONAL.narrow(result);
      assertThat(optResult).isPresent();
      assertThat(optResult.get().age()).isEqualTo(31);
    }

    @Test
    @DisplayName("modifyF should propagate failure from underlying lens")
    void modifyFPropagatesFailure() {
      Lens<User, Integer> regularAgeLens = Lens.of(User::age, User::withAge);
      IndexedLens<String, User, Integer> indexedAgeLens =
          IndexedLens.from("userAge", regularAgeLens);

      User user = new User("Alice", 150, "alice@example.com");

      Function<Integer, Kind<OptionalKind.Witness, Integer>> failOnOldAge =
          age -> {
            if (age < 100) {
              return OptionalKindHelper.OPTIONAL.widen(Optional.of(age + 1));
            } else {
              return OptionalKindHelper.OPTIONAL.widen(Optional.empty());
            }
          };

      Kind<OptionalKind.Witness, User> result =
          indexedAgeLens.modifyF(failOnOldAge, user, OptionalMonad.INSTANCE);

      Optional<User> optResult = OptionalKindHelper.OPTIONAL.narrow(result);
      assertThat(optResult).isEmpty();
    }
  }

  @Nested
  @DisplayName("iandThen() - Composition with paired indices")
  class IAndThenTests {

    @Test
    @DisplayName("should compose indexed lenses with paired indices")
    void composeWithPairedIndices() {
      IndexedLens<String, Person, User> userLens =
          IndexedLens.of("user", Person::user, Person::withUser);
      IndexedLens<String, User, String> nameLens =
          IndexedLens.of("name", User::name, User::withName);

      IndexedLens<Pair<String, String>, Person, String> composed = userLens.iandThen(nameLens);

      Person person =
          new Person(
              "p1",
              new User("Alice", 30, "alice@example.com"),
              new Address("Main St", "NYC", "10001"));

      assertThat(composed.index()).isEqualTo(new Pair<>("user", "name"));
      assertThat(composed.get(person)).isEqualTo("Alice");

      Person updated = composed.set("Bob", person);
      assertThat(updated.user().name()).isEqualTo("Bob");
    }

    @Test
    @DisplayName("should preserve full path in paired index")
    void preserveFullPath() {
      IndexedLens<String, Person, Address> addressLens =
          IndexedLens.of("address", Person::address, Person::withAddress);
      IndexedLens<String, Address, String> cityLens =
          IndexedLens.of("city", Address::city, Address::withCity);

      IndexedLens<Pair<String, String>, Person, String> personCityLens =
          addressLens.iandThen(cityLens);

      Pair<String, String> fullPath = personCityLens.index();
      assertThat(fullPath.first()).isEqualTo("address");
      assertThat(fullPath.second()).isEqualTo("city");
    }
  }

  @Nested
  @DisplayName("andThen() - Composition with regular lens")
  class AndThenTests {

    @Test
    @DisplayName("should compose with regular lens preserving outer index")
    void composeWithRegularLens() {
      IndexedLens<String, Person, User> userLens =
          IndexedLens.of("user", Person::user, Person::withUser);
      Lens<User, String> nameLens = Lens.of(User::name, User::withName);

      IndexedLens<String, Person, String> composed = userLens.andThen(nameLens);

      Person person =
          new Person(
              "p1",
              new User("Alice", 30, "alice@example.com"),
              new Address("Main St", "NYC", "10001"));

      assertThat(composed.index()).isEqualTo("user");
      assertThat(composed.get(person)).isEqualTo("Alice");

      Person updated = composed.set("Carol", person);
      assertThat(updated.user().name()).isEqualTo("Carol");
    }
  }

  @Nested
  @DisplayName("modifyF() - Effectful modification without index")
  class ModifyFTests {

    @Test
    @DisplayName("should modify with effectful function ignoring index")
    void modifyFOnOfLens() {
      IndexedLens<String, User, Integer> ageLens = IndexedLens.of("age", User::age, User::withAge);
      User user = new User("Alice", 30, "alice@example.com");

      Function<Integer, Kind<OptionalKind.Witness, Integer>> incrementIfValid =
          age -> {
            if (age < 100) {
              return OptionalKindHelper.OPTIONAL.widen(Optional.of(age + 1));
            } else {
              return OptionalKindHelper.OPTIONAL.widen(Optional.empty());
            }
          };

      Kind<OptionalKind.Witness, User> result =
          ageLens.modifyF(incrementIfValid, user, OptionalMonad.INSTANCE);

      Optional<User> optResult = OptionalKindHelper.OPTIONAL.narrow(result);
      assertThat(optResult).isPresent();
      assertThat(optResult.get().age()).isEqualTo(31);
    }

    @Test
    @DisplayName("modifyF should propagate failure")
    void modifyFPropagatesFailure() {
      IndexedLens<String, User, Integer> ageLens = IndexedLens.of("age", User::age, User::withAge);
      User user = new User("Alice", 150, "alice@example.com");

      Function<Integer, Kind<OptionalKind.Witness, Integer>> failOnOldAge =
          age -> {
            if (age < 100) {
              return OptionalKindHelper.OPTIONAL.widen(Optional.of(age + 1));
            } else {
              return OptionalKindHelper.OPTIONAL.widen(Optional.empty());
            }
          };

      Kind<OptionalKind.Witness, User> result =
          ageLens.modifyF(failOnOldAge, user, OptionalMonad.INSTANCE);

      Optional<User> optResult = OptionalKindHelper.OPTIONAL.narrow(result);
      assertThat(optResult).isEmpty();
    }
  }

  @Nested
  @DisplayName("asLens() - Conversion to regular lens")
  class AsLensTests {

    @Test
    @DisplayName("should convert to regular lens ignoring index")
    void convertToLens() {
      IndexedLens<String, User, Integer> indexedAgeLens =
          IndexedLens.of("age", User::age, User::withAge);
      Lens<User, Integer> regularLens = indexedAgeLens.asLens();

      User user = new User("Alice", 30, "alice@example.com");

      assertThat(regularLens.get(user)).isEqualTo(30);

      User updated = regularLens.modify(age -> age * 2, user);
      assertThat(updated.age()).isEqualTo(60);
    }

    @Test
    @DisplayName("asLens modifyF should delegate to indexed lens modifyF")
    void asLensModifyF() {
      IndexedLens<String, User, Integer> indexedAgeLens =
          IndexedLens.of("age", User::age, User::withAge);
      Lens<User, Integer> regularLens = indexedAgeLens.asLens();

      User user = new User("Alice", 30, "alice@example.com");

      Function<Integer, Kind<OptionalKind.Witness, Integer>> incrementIfValid =
          age -> {
            if (age < 100) {
              return OptionalKindHelper.OPTIONAL.widen(Optional.of(age + 1));
            } else {
              return OptionalKindHelper.OPTIONAL.widen(Optional.empty());
            }
          };

      Kind<OptionalKind.Witness, User> result =
          regularLens.modifyF(incrementIfValid, user, OptionalMonad.INSTANCE);

      Optional<User> optResult = OptionalKindHelper.OPTIONAL.narrow(result);
      assertThat(optResult).isPresent();
      assertThat(optResult.get().age()).isEqualTo(31);
    }

    @Test
    @DisplayName("asLens modifyF should propagate failure")
    void asLensModifyFPropagatesFailure() {
      IndexedLens<String, User, Integer> indexedAgeLens =
          IndexedLens.of("age", User::age, User::withAge);
      Lens<User, Integer> regularLens = indexedAgeLens.asLens();

      User user = new User("Alice", 150, "alice@example.com");

      Function<Integer, Kind<OptionalKind.Witness, Integer>> failOnOldAge =
          age -> {
            if (age < 100) {
              return OptionalKindHelper.OPTIONAL.widen(Optional.of(age + 1));
            } else {
              return OptionalKindHelper.OPTIONAL.widen(Optional.empty());
            }
          };

      Kind<OptionalKind.Witness, User> result =
          regularLens.modifyF(failOnOldAge, user, OptionalMonad.INSTANCE);

      Optional<User> optResult = OptionalKindHelper.OPTIONAL.narrow(result);
      assertThat(optResult).isEmpty();
    }
  }

  @Nested
  @DisplayName("asIndexedTraversal() - Conversion to indexed traversal")
  class AsIndexedTraversalTests {

    @Test
    @DisplayName("should convert to indexed traversal")
    void convertToTraversal() {
      IndexedLens<String, User, String> nameLens =
          IndexedLens.of("name", User::name, User::withName);
      IndexedTraversal<String, User, String> traversal = nameLens.asIndexedTraversal();

      User user = new User("Alice", 30, "alice@example.com");

      User updated =
          IndexedTraversals.imodify(traversal, (field, value) -> value + " (" + field + ")", user);

      assertThat(updated.name()).isEqualTo("Alice (name)");
    }
  }

  @Nested
  @DisplayName("asIndexedFold() - Conversion to indexed fold")
  class AsIndexedFoldTests {

    @Test
    @DisplayName("should convert to indexed fold")
    void convertToFold() {
      IndexedLens<String, User, String> emailLens =
          IndexedLens.of("email", User::email, User::withEmail);
      IndexedFold<String, User, String> fold = emailLens.asIndexedFold();

      User user = new User("Alice", 30, "alice@example.com");

      Pair<String, String> indexed = fold.toIndexedList(user).get(0);
      assertThat(indexed.first()).isEqualTo("email");
      assertThat(indexed.second()).isEqualTo("alice@example.com");
    }
  }

  @Nested
  @DisplayName("Practical use cases")
  class PracticalUseCasesTests {

    @Test
    @DisplayName("should track field modifications for audit")
    void auditFieldModifications() {
      IndexedLens<String, User, String> nameLens =
          IndexedLens.of("name", User::name, User::withName);
      IndexedLens<String, User, Integer> ageLens = IndexedLens.of("age", User::age, User::withAge);

      User user = new User("Alice", 30, "alice@example.com");

      // Audit log
      StringBuilder audit = new StringBuilder();

      Function<IndexedLens<String, User, ?>, User> auditingModifier =
          lens -> {
            Object oldValue = lens.get(user);
            User modified = lens.imodify((field, value) -> value, user);
            audit
                .append("Modified ")
                .append(lens.index())
                .append(": ")
                .append(oldValue)
                .append("\n");
            return modified;
          };

      auditingModifier.apply(nameLens);
      auditingModifier.apply(ageLens);

      assertThat(audit.toString()).contains("Modified name: Alice");
      assertThat(audit.toString()).contains("Modified age: 30");
    }

    @Test
    @DisplayName("should support generic field processing with identification")
    void genericFieldProcessing() {
      IndexedLens<String, User, String> nameLens =
          IndexedLens.of("name", User::name, User::withName);
      IndexedLens<String, User, String> emailLens =
          IndexedLens.of("email", User::email, User::withEmail);

      User user = new User("Alice", 30, "alice@example.com");

      // Process all string fields the same way but with field-specific labeling
      User updated = nameLens.imodify((field, value) -> "[" + field + "=" + value + "]", user);
      updated = emailLens.imodify((field, value) -> "[" + field + "=" + value + "]", updated);

      assertThat(updated.name()).isEqualTo("[name=Alice]");
      assertThat(updated.email()).isEqualTo("[email=alice@example.com]");
    }
  }
}
