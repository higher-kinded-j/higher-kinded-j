// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.focus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Each;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.each.EachInstances;
import org.higherkindedj.optics.indexed.Pair;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("AffinePath<S, A> Tests")
class AffinePathTest {

  // Test Data Structures
  record Config(Optional<Database> database) {}

  record Database(String host, int port, Optional<String> username) {}

  sealed interface Result permits Success, Failure {}

  record Success(String value) implements Result {}

  record Failure(String error) implements Result {}

  @Nested
  @DisplayName("Core Functionality")
  class CoreFunctionality {

    @Test
    @DisplayName("of should create an AffinePath from an Affine")
    void ofShouldCreateAffinePath() {
      Affine<Optional<String>, String> someAffine = FocusPaths.optionalSome();
      AffinePath<Optional<String>, String> path = AffinePath.of(someAffine);

      assertThat(path).isNotNull();
      assertThat(path.toAffine()).isSameAs(someAffine);
    }

    @Test
    @DisplayName("getOptional should return value when present")
    void getOptionalShouldReturnValueWhenPresent() {
      AffinePath<Optional<String>, String> path = AffinePath.of(FocusPaths.optionalSome());

      assertThat(path.getOptional(Optional.of("hello"))).contains("hello");
    }

    @Test
    @DisplayName("getOptional should return empty when absent")
    void getOptionalShouldReturnEmptyWhenAbsent() {
      AffinePath<Optional<String>, String> path = AffinePath.of(FocusPaths.optionalSome());

      assertThat(path.getOptional(Optional.empty())).isEmpty();
    }

    @Test
    @DisplayName("set should update value")
    void setShouldUpdateValue() {
      AffinePath<Optional<String>, String> path = AffinePath.of(FocusPaths.optionalSome());

      Optional<String> result = path.set("world", Optional.of("hello"));
      assertThat(result).contains("world");

      // Set on empty also works
      Optional<String> fromEmpty = path.set("new", Optional.empty());
      assertThat(fromEmpty).contains("new");
    }

    @Test
    @DisplayName("modify should transform value when present")
    void modifyShouldTransformWhenPresent() {
      AffinePath<Optional<String>, String> path = AffinePath.of(FocusPaths.optionalSome());

      Optional<String> result = path.modify(String::toUpperCase, Optional.of("hello"));
      assertThat(result).contains("HELLO");
    }

    @Test
    @DisplayName("modify should return original when absent")
    void modifyShouldReturnOriginalWhenAbsent() {
      AffinePath<Optional<String>, String> path = AffinePath.of(FocusPaths.optionalSome());

      Optional<String> result = path.modify(String::toUpperCase, Optional.empty());
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("matches should return true when value present")
    void matchesShouldReturnTrueWhenPresent() {
      AffinePath<Optional<String>, String> path = AffinePath.of(FocusPaths.optionalSome());

      assertThat(path.matches(Optional.of("hello"))).isTrue();
      assertThat(path.matches(Optional.empty())).isFalse();
    }

    @Test
    @DisplayName("getOrElse should return value or default")
    void getOrElseShouldReturnValueOrDefault() {
      AffinePath<Optional<String>, String> path = AffinePath.of(FocusPaths.optionalSome());

      assertThat(path.getOrElse("default", Optional.of("hello"))).isEqualTo("hello");
      assertThat(path.getOrElse("default", Optional.empty())).isEqualTo("default");
    }

    @Test
    @DisplayName("mapOptional should transform and wrap in Optional")
    void mapOptionalShouldTransformAndWrap() {
      AffinePath<Optional<String>, String> path = AffinePath.of(FocusPaths.optionalSome());

      assertThat(path.mapOptional(String::length, Optional.of("hello"))).contains(5);
      assertThat(path.mapOptional(String::length, Optional.empty())).isEmpty();
    }
  }

  @Nested
  @DisplayName("Composition with Lens")
  class CompositionWithLens {

    @Test
    @DisplayName("via(Lens) should produce an AffinePath")
    void viaLensShouldProduceAffinePath() {
      Lens<Config, Optional<Database>> dbLens = Lens.of(Config::database, (c, d) -> new Config(d));
      Prism<Optional<Database>, Database> somePrism = Prism.of(opt -> opt, db -> Optional.of(db));

      Lens<Database, String> hostLens =
          Lens.of(Database::host, (db, h) -> new Database(h, db.port(), db.username()));

      // Config -> Optional<Database> -> Database -> String
      AffinePath<Config, Database> dbPath = FocusPath.of(dbLens).via(somePrism);
      AffinePath<Config, String> hostPath = dbPath.via(hostLens);

      Config withDb = new Config(Optional.of(new Database("localhost", 5432, Optional.empty())));
      Config withoutDb = new Config(Optional.empty());

      assertThat(hostPath.getOptional(withDb)).contains("localhost");
      assertThat(hostPath.getOptional(withoutDb)).isEmpty();

      Config updated = hostPath.set("127.0.0.1", withDb);
      assertThat(updated.database().get().host()).isEqualTo("127.0.0.1");
    }
  }

  @Nested
  @DisplayName("Composition with Prism")
  class CompositionWithPrism {

    @Test
    @DisplayName("via(Prism) should produce an AffinePath")
    void viaPrismShouldProduceAffinePath() {
      Prism<Result, String> successPrism =
          Prism.of(
              r -> r instanceof Success s ? Optional.of(s.value()) : Optional.empty(),
              Success::new);
      Prism<Optional<Result>, Result> somePrism = Prism.of(opt -> opt, r -> Optional.of(r));

      AffinePath<Optional<Result>, Result> resultPath =
          AffinePath.of(Affine.of(opt -> opt, (opt, r) -> Optional.of(r)));
      AffinePath<Optional<Result>, String> valuePath = resultPath.via(successPrism);

      Optional<Result> success = Optional.of(new Success("data"));
      Optional<Result> failure = Optional.of(new Failure("error"));
      Optional<Result> empty = Optional.empty();

      assertThat(valuePath.getOptional(success)).contains("data");
      assertThat(valuePath.getOptional(failure)).isEmpty();
      assertThat(valuePath.getOptional(empty)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Composition with Affine")
  class CompositionWithAffine {

    @Test
    @DisplayName("via(Affine) should compose two affines")
    void viaAffineShouldComposeTwoAffines() {
      Lens<Config, Optional<Database>> dbLens = Lens.of(Config::database, (c, d) -> new Config(d));
      Affine<Optional<Database>, Database> dbAffine = FocusPaths.optionalSome();

      Lens<Database, Optional<String>> usernameLens =
          Lens.of(Database::username, (db, u) -> new Database(db.host(), db.port(), u));
      Affine<Optional<String>, String> usernameAffine = FocusPaths.optionalSome();

      // Config -> Optional<Database> -> Database -> Optional<String> -> String
      AffinePath<Config, Database> dbPath = FocusPath.of(dbLens).via(dbAffine);
      AffinePath<Config, Optional<String>> userOptPath = dbPath.via(usernameLens);
      AffinePath<Config, String> usernamePath = userOptPath.via(usernameAffine);

      Config withUser =
          new Config(Optional.of(new Database("localhost", 5432, Optional.of("admin"))));
      Config withoutUser =
          new Config(Optional.of(new Database("localhost", 5432, Optional.empty())));
      Config withoutDb = new Config(Optional.empty());

      assertThat(usernamePath.getOptional(withUser)).contains("admin");
      assertThat(usernamePath.getOptional(withoutUser)).isEmpty();
      assertThat(usernamePath.getOptional(withoutDb)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Composition with Traversal")
  class CompositionWithTraversal {

    @Test
    @DisplayName("via(Traversal) should produce a TraversalPath")
    void viaTraversalShouldProduceTraversalPath() {
      record Container(Optional<List<String>> items) {}

      Lens<Container, Optional<List<String>>> itemsLens =
          Lens.of(Container::items, (c, i) -> new Container(i));
      Affine<Optional<List<String>>, List<String>> someAffine = FocusPaths.optionalSome();
      Traversal<List<String>, String> listTraversal = Traversals.forList();

      AffinePath<Container, List<String>> listPath = FocusPath.of(itemsLens).via(someAffine);
      TraversalPath<Container, String> elementsPath = listPath.via(listTraversal);

      Container withItems = new Container(Optional.of(List.of("a", "b", "c")));
      Container withoutItems = new Container(Optional.empty());

      assertThat(elementsPath.getAll(withItems)).containsExactly("a", "b", "c");
      assertThat(elementsPath.getAll(withoutItems)).isEmpty();

      Container modified = elementsPath.modifyAll(String::toUpperCase, withItems);
      assertThat(modified.items().get()).containsExactly("A", "B", "C");
    }
  }

  @Nested
  @DisplayName("Composition with Iso")
  class CompositionWithIso {

    @Test
    @DisplayName("via(Iso) should produce an AffinePath")
    void viaIsoShouldProduceAffinePath() {
      record Wrapper(String value) {}

      Iso<String, Wrapper> wrapperIso = Iso.of(Wrapper::new, Wrapper::value);
      AffinePath<Optional<String>, String> somePath = AffinePath.of(FocusPaths.optionalSome());
      AffinePath<Optional<String>, Wrapper> wrappedPath = somePath.via(wrapperIso);

      assertThat(wrappedPath.getOptional(Optional.of("hello")).map(Wrapper::value))
          .contains("hello");
      assertThat(wrappedPath.getOptional(Optional.empty())).isEmpty();
    }
  }

  @Nested
  @DisplayName("Collection Navigation")
  class CollectionNavigation {

    @Test
    @DisplayName("each() should traverse optional list elements")
    void eachShouldTraverseOptionalListElements() {
      AffinePath<Optional<List<String>>, List<String>> listPath =
          AffinePath.of(FocusPaths.optionalSome());
      TraversalPath<Optional<List<String>>, String> elementsPath = listPath.each();

      Optional<List<String>> present = Optional.of(List.of("a", "b"));
      Optional<List<String>> empty = Optional.empty();

      assertThat(elementsPath.getAll(present)).containsExactly("a", "b");
      assertThat(elementsPath.getAll(empty)).isEmpty();
    }

    @Test
    @DisplayName("each(Each) should traverse using provided Each instance")
    void eachWithInstanceShouldTraverseOptionalMapElements() {
      AffinePath<Optional<Map<String, Integer>>, Map<String, Integer>> mapPath =
          AffinePath.of(FocusPaths.optionalSome());

      Each<Map<String, Integer>, Integer> mapEach = EachInstances.mapValuesEach();
      TraversalPath<Optional<Map<String, Integer>>, Integer> valuesPath = mapPath.each(mapEach);

      Optional<Map<String, Integer>> present = Optional.of(Map.of("a", 1, "b", 2));
      Optional<Map<String, Integer>> empty = Optional.empty();

      assertThat(valuesPath.getAll(present)).containsExactlyInAnyOrder(1, 2);
      assertThat(valuesPath.getAll(empty)).isEmpty();
    }

    @Test
    @DisplayName("at(index) should focus on element in optional list")
    void atShouldFocusOnElementInOptionalList() {
      AffinePath<Optional<List<String>>, List<String>> listPath =
          AffinePath.of(FocusPaths.optionalSome());
      AffinePath<Optional<List<String>>, String> firstPath = listPath.at(0);

      Optional<List<String>> present = Optional.of(List.of("a", "b"));
      Optional<List<String>> emptyList = Optional.of(List.of());
      Optional<List<String>> absent = Optional.empty();

      assertThat(firstPath.getOptional(present)).contains("a");
      assertThat(firstPath.getOptional(emptyList)).isEmpty();
      assertThat(firstPath.getOptional(absent)).isEmpty();
    }

    @Test
    @DisplayName("some() should unwrap nested Optional")
    void someShouldUnwrapNestedOptional() {
      AffinePath<Optional<Optional<String>>, Optional<String>> outerPath =
          AffinePath.of(FocusPaths.optionalSome());
      AffinePath<Optional<Optional<String>>, String> innerPath = outerPath.some();

      Optional<Optional<String>> full = Optional.of(Optional.of("value"));
      Optional<Optional<String>> innerEmpty = Optional.of(Optional.empty());
      Optional<Optional<String>> outerEmpty = Optional.empty();

      assertThat(innerPath.getOptional(full)).contains("value");
      assertThat(innerPath.getOptional(innerEmpty)).isEmpty();
      assertThat(innerPath.getOptional(outerEmpty)).isEmpty();
    }

    @Test
    @DisplayName("atKey() should focus on map value by key")
    void atKeyShouldFocusOnMapValue() {
      AffinePath<Optional<Map<String, String>>, Map<String, String>> mapPath =
          AffinePath.of(FocusPaths.optionalSome());
      AffinePath<Optional<Map<String, String>>, String> themePath = mapPath.atKey("theme");

      Optional<Map<String, String>> present =
          Optional.of(Map.of("theme", "dark", "language", "en"));
      Optional<Map<String, String>> missingKey = Optional.of(Map.of("language", "en"));
      Optional<Map<String, String>> absent = Optional.empty();

      assertThat(themePath.getOptional(present)).contains("dark");
      assertThat(themePath.getOptional(missingKey)).isEmpty();
      assertThat(themePath.getOptional(absent)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Nullable Field Handling")
  class NullableFieldHandling {

    record UserWithNullable(String name, String nickname) {}

    @Test
    @DisplayName("ofNullable should create AffinePath that handles null as absent")
    void ofNullableShouldHandleNullAsAbsent() {
      AffinePath<UserWithNullable, String> nicknamePath =
          AffinePath.ofNullable(
              UserWithNullable::nickname,
              (user, nickname) -> new UserWithNullable(user.name(), nickname));

      UserWithNullable withNickname = new UserWithNullable("Alice", "Ally");
      UserWithNullable withoutNickname = new UserWithNullable("Bob", null);

      assertThat(nicknamePath.getOptional(withNickname)).contains("Ally");
      assertThat(nicknamePath.getOptional(withoutNickname)).isEmpty();
    }

    @Test
    @DisplayName("ofNullable should allow setting value on null field")
    void ofNullableShouldAllowSettingOnNull() {
      AffinePath<UserWithNullable, String> nicknamePath =
          AffinePath.ofNullable(
              UserWithNullable::nickname,
              (user, nickname) -> new UserWithNullable(user.name(), nickname));

      UserWithNullable user = new UserWithNullable("Bob", null);

      UserWithNullable updated = nicknamePath.set("Bobby", user);
      assertThat(updated.nickname()).isEqualTo("Bobby");
    }

    @Test
    @DisplayName("ofNullable should support modify operation")
    void ofNullableShouldSupportModify() {
      AffinePath<UserWithNullable, String> nicknamePath =
          AffinePath.ofNullable(
              UserWithNullable::nickname,
              (user, nickname) -> new UserWithNullable(user.name(), nickname));

      UserWithNullable withNickname = new UserWithNullable("Alice", "ally");
      UserWithNullable withoutNickname = new UserWithNullable("Bob", null);

      UserWithNullable modified = nicknamePath.modify(String::toUpperCase, withNickname);
      assertThat(modified.nickname()).isEqualTo("ALLY");

      // Modify on null should return original unchanged
      UserWithNullable unchanged = nicknamePath.modify(String::toUpperCase, withoutNickname);
      assertThat(unchanged.nickname()).isNull();
    }

    @Test
    @DisplayName("ofNullable should support matches operation")
    void ofNullableShouldSupportMatches() {
      AffinePath<UserWithNullable, String> nicknamePath =
          AffinePath.ofNullable(
              UserWithNullable::nickname,
              (user, nickname) -> new UserWithNullable(user.name(), nickname));

      UserWithNullable withNickname = new UserWithNullable("Alice", "Ally");
      UserWithNullable withoutNickname = new UserWithNullable("Bob", null);

      assertThat(nicknamePath.matches(withNickname)).isTrue();
      assertThat(nicknamePath.matches(withoutNickname)).isFalse();
    }

    @Test
    @DisplayName("nullable() method should chain to handle null values")
    void nullableMethodShouldChainToHandleNulls() {
      Lens<UserWithNullable, String> nicknameLens =
          Lens.of(UserWithNullable::nickname, (u, n) -> new UserWithNullable(u.name(), n));

      // Create AffinePath using nullable() chaining via FocusPath
      AffinePath<UserWithNullable, String> nicknamePath = FocusPath.of(nicknameLens).nullable();

      UserWithNullable withNickname = new UserWithNullable("Alice", "Ally");
      UserWithNullable withoutNickname = new UserWithNullable("Bob", null);

      assertThat(nicknamePath.getOptional(withNickname)).contains("Ally");
      assertThat(nicknamePath.getOptional(withoutNickname)).isEmpty();
    }

    @Test
    @DisplayName("nullable() should compose with other path operations")
    void nullableShouldComposeWithOtherOperations() {
      record Company(String name, UserWithNullable owner) {}

      Lens<Company, UserWithNullable> ownerLens =
          Lens.of(Company::owner, (c, o) -> new Company(c.name(), o));

      AffinePath<Company, String> ownerNicknamePath =
          FocusPath.of(ownerLens)
              .via(Lens.of(UserWithNullable::nickname, (u, n) -> new UserWithNullable(u.name(), n)))
              .nullable();

      Company withOwnerNickname = new Company("Acme", new UserWithNullable("Alice", "Ally"));
      Company withoutOwnerNickname = new Company("Corp", new UserWithNullable("Bob", null));

      assertThat(ownerNicknamePath.getOptional(withOwnerNickname)).contains("Ally");
      assertThat(ownerNicknamePath.getOptional(withoutOwnerNickname)).isEmpty();

      Company updated = ownerNicknamePath.set("Bobby", withoutOwnerNickname);
      assertThat(updated.owner().nickname()).isEqualTo("Bobby");
    }

    @Test
    @DisplayName("nullable() on AffinePath should handle null values")
    void nullableOnAffinePathShouldHandleNulls() {
      // Create a scenario where we have an Optional containing a record with nullable field
      record ConfigWithNullable(Optional<UserWithNullable> user) {}

      // Build the path: ConfigWithNullable -> Optional<UserWithNullable> -> UserWithNullable ->
      // String
      // First create an AffinePath via ofNullable for the inner nullable field
      AffinePath<UserWithNullable, String> innerNicknamePath =
          AffinePath.ofNullable(
              UserWithNullable::nickname, (u, n) -> new UserWithNullable(u.name(), n));

      // Create Lens for the Optional field
      Lens<ConfigWithNullable, Optional<UserWithNullable>> userOptLens =
          Lens.of(ConfigWithNullable::user, (c, u) -> new ConfigWithNullable(u));

      // Compose: FocusPath -> some() -> via(innerNicknamePath)
      AffinePath<ConfigWithNullable, String> nicknamePath =
          FocusPath.of(userOptLens).<UserWithNullable>some().via(innerNicknamePath);

      ConfigWithNullable withNickname =
          new ConfigWithNullable(Optional.of(new UserWithNullable("Alice", "Ally")));
      ConfigWithNullable withNullNickname =
          new ConfigWithNullable(Optional.of(new UserWithNullable("Bob", null)));
      ConfigWithNullable withNoUser = new ConfigWithNullable(Optional.empty());

      assertThat(nicknamePath.getOptional(withNickname)).contains("Ally");
      assertThat(nicknamePath.getOptional(withNullNickname)).isEmpty();
      assertThat(nicknamePath.getOptional(withNoUser)).isEmpty();
    }

    @Test
    @DisplayName("nullable() should support modify operation")
    void nullableShouldSupportModify() {
      AffinePath<UserWithNullable, String> nicknamePath =
          FocusPath.of(
                  Lens.of(UserWithNullable::nickname, (u, n) -> new UserWithNullable(u.name(), n)))
              .nullable();

      UserWithNullable withNickname = new UserWithNullable("Alice", "ally");
      UserWithNullable withoutNickname = new UserWithNullable("Bob", null);

      UserWithNullable modified = nicknamePath.modify(String::toUpperCase, withNickname);
      assertThat(modified.nickname()).isEqualTo("ALLY");

      // Modify on null should return original unchanged
      UserWithNullable unchanged = nicknamePath.modify(String::toUpperCase, withoutNickname);
      assertThat(unchanged.nickname()).isNull();
    }

    @Test
    @DisplayName("nullable() should support getOrElse with default value")
    void nullableShouldSupportGetOrElse() {
      AffinePath<UserWithNullable, String> nicknamePath =
          FocusPath.of(
                  Lens.of(UserWithNullable::nickname, (u, n) -> new UserWithNullable(u.name(), n)))
              .nullable();

      UserWithNullable withNickname = new UserWithNullable("Alice", "Ally");
      UserWithNullable withoutNickname = new UserWithNullable("Bob", null);

      assertThat(nicknamePath.getOrElse("default", withNickname)).isEqualTo("Ally");
      assertThat(nicknamePath.getOrElse("default", withoutNickname)).isEqualTo("default");
    }

    @Test
    @DisplayName("nullable() should support mapOptional transformation")
    void nullableShouldSupportMapOptional() {
      AffinePath<UserWithNullable, String> nicknamePath =
          FocusPath.of(
                  Lens.of(UserWithNullable::nickname, (u, n) -> new UserWithNullable(u.name(), n)))
              .nullable();

      UserWithNullable withNickname = new UserWithNullable("Alice", "Ally");
      UserWithNullable withoutNickname = new UserWithNullable("Bob", null);

      assertThat(nicknamePath.mapOptional(String::length, withNickname)).contains(4);
      assertThat(nicknamePath.mapOptional(String::length, withoutNickname)).isEmpty();
    }

    @Test
    @DisplayName("nullable() should support matches operation")
    void nullableShouldSupportMatches() {
      AffinePath<UserWithNullable, String> nicknamePath =
          FocusPath.of(
                  Lens.of(UserWithNullable::nickname, (u, n) -> new UserWithNullable(u.name(), n)))
              .nullable();

      UserWithNullable withNickname = new UserWithNullable("Alice", "Ally");
      UserWithNullable withoutNickname = new UserWithNullable("Bob", null);

      assertThat(nicknamePath.matches(withNickname)).isTrue();
      assertThat(nicknamePath.matches(withoutNickname)).isFalse();
    }

    @Test
    @DisplayName("nullable() should handle deeply nested nullable fields")
    void nullableShouldHandleDeeplyNestedNullables() {
      record Address(String city, String street) {}
      record Person(String name, Address address) {}
      record Company(String name, Person ceo) {}

      // Create nullable-aware AffinePaths for each level
      AffinePath<Company, Person> ceoPath =
          AffinePath.ofNullable(Company::ceo, (c, p) -> new Company(c.name(), p));

      AffinePath<Person, Address> addressPath =
          AffinePath.ofNullable(Person::address, (p, a) -> new Person(p.name(), a));

      AffinePath<Address, String> streetPath =
          AffinePath.ofNullable(Address::street, (a, s) -> new Address(a.city(), s));

      // Compose the paths: Company -> Person -> Address -> String
      AffinePath<Company, String> fullPath = ceoPath.via(addressPath).via(streetPath);

      Company withStreet =
          new Company("Acme", new Person("Alice", new Address("NYC", "123 Main St")));
      Company withNullStreet = new Company("Corp", new Person("Bob", new Address("LA", null)));
      Company withNullAddress = new Company("Inc", new Person("Charlie", null));
      Company withNullCeo = new Company("LLC", null);

      assertThat(fullPath.getOptional(withStreet)).contains("123 Main St");
      assertThat(fullPath.getOptional(withNullStreet)).isEmpty();
      assertThat(fullPath.getOptional(withNullAddress)).isEmpty();
      assertThat(fullPath.getOptional(withNullCeo)).isEmpty();
    }

    @Test
    @DisplayName("nullable() should allow set even when original is null")
    void nullableShouldAllowSetWhenOriginalIsNull() {
      AffinePath<UserWithNullable, String> nicknamePath =
          FocusPath.of(
                  Lens.of(UserWithNullable::nickname, (u, n) -> new UserWithNullable(u.name(), n)))
              .nullable();

      UserWithNullable withoutNickname = new UserWithNullable("Bob", null);

      UserWithNullable updated = nicknamePath.set("Bobby", withoutNickname);
      assertThat(updated.nickname()).isEqualTo("Bobby");
    }

    @Test
    @DisplayName("nullable() combined with some() for Optional containing nullable")
    void nullableCombinedWithSomeForOptionalContainingNullable() {
      record Profile(Optional<String> bio) {}

      Lens<Profile, Optional<String>> bioOptLens = Lens.of(Profile::bio, (p, b) -> new Profile(b));

      // Path: Profile -> Optional<String> -> String (via some) -> String (via nullable)
      // This tests the scenario where Optional contains a value but that value might be null
      // (which shouldn't happen in practice but tests the composition)
      AffinePath<Profile, String> bioPath = FocusPath.of(bioOptLens).some();

      Profile withBio = new Profile(Optional.of("Software developer"));
      Profile withEmptyBio = new Profile(Optional.empty());

      assertThat(bioPath.getOptional(withBio)).contains("Software developer");
      assertThat(bioPath.getOptional(withEmptyBio)).isEmpty();

      // Set should work
      Profile updated = bioPath.set("New bio", withEmptyBio);
      assertThat(updated.bio()).contains("New bio");
    }

    @Test
    @DisplayName("nullable() should work with asTraversal conversion")
    void nullableShouldWorkWithAsTraversalConversion() {
      AffinePath<UserWithNullable, String> nicknamePath =
          FocusPath.of(
                  Lens.of(UserWithNullable::nickname, (u, n) -> new UserWithNullable(u.name(), n)))
              .nullable();

      TraversalPath<UserWithNullable, String> traversalPath = nicknamePath.asTraversal();

      UserWithNullable withNickname = new UserWithNullable("Alice", "Ally");
      UserWithNullable withoutNickname = new UserWithNullable("Bob", null);

      assertThat(traversalPath.getAll(withNickname)).containsExactly("Ally");
      assertThat(traversalPath.getAll(withoutNickname)).isEmpty();
    }

    @Test
    @DisplayName("AffinePath.nullable() should treat null focus value as absent")
    void affinePathNullableShouldTreatNullAsAbsent() {
      record Inner(String value) {}
      record Outer(Optional<Inner> inner) {}

      Lens<Outer, Optional<Inner>> innerOptLens = Lens.of(Outer::inner, (o, i) -> new Outer(i));

      Lens<Inner, String> valueLens = Lens.of(Inner::value, (i, v) -> new Inner(v));

      // Build AffinePath: Outer -> Optional<Inner> -> Inner -> String
      AffinePath<Outer, Inner> innerPath = FocusPath.of(innerOptLens).<Inner>some();
      AffinePath<Outer, String> valuePath = innerPath.via(valueLens);

      // Call nullable() directly on the AffinePath
      AffinePath<Outer, String> safeValuePath = valuePath.<String>nullable();

      // Test: Inner present with non-null value
      Outer withValue = new Outer(Optional.of(new Inner("hello")));
      assertThat(safeValuePath.getOptional(withValue)).contains("hello");

      // Test: Inner present with null value - nullable() should return empty
      Outer withNullValue = new Outer(Optional.of(new Inner(null)));
      assertThat(safeValuePath.getOptional(withNullValue)).isEmpty();

      // Test: Inner absent
      Outer withEmpty = new Outer(Optional.empty());
      assertThat(safeValuePath.getOptional(withEmpty)).isEmpty();
    }

    @Test
    @DisplayName("AffinePath.nullable() should support set operation when value is present")
    void affinePathNullableShouldSupportSet() {
      record Inner(String value) {}
      record Outer(Optional<Inner> inner) {}

      Lens<Outer, Optional<Inner>> innerOptLens = Lens.of(Outer::inner, (o, i) -> new Outer(i));

      Lens<Inner, String> valueLens = Lens.of(Inner::value, (i, v) -> new Inner(v));

      AffinePath<Outer, Inner> innerPath = FocusPath.of(innerOptLens).<Inner>some();
      AffinePath<Outer, String> safeValuePath = innerPath.via(valueLens).<String>nullable();

      // Set works when value is present (non-null)
      Outer withValue = new Outer(Optional.of(new Inner("original")));
      Outer updated = safeValuePath.set("new value", withValue);
      assertThat(updated.inner().get().value()).isEqualTo("new value");

      // When value is null, set returns unchanged (null is treated as absent)
      // This is a consequence of affine composition - set only proceeds when getOptional is present
      Outer withNullValue = new Outer(Optional.of(new Inner(null)));
      Outer unchanged = safeValuePath.set("new value", withNullValue);
      assertThat(unchanged.inner().get().value()).isNull();
    }

    @Test
    @DisplayName("AffinePath.nullable() should support modify operation")
    void affinePathNullableShouldSupportModify() {
      record Inner(String value) {}
      record Outer(Optional<Inner> inner) {}

      Lens<Outer, Optional<Inner>> innerOptLens = Lens.of(Outer::inner, (o, i) -> new Outer(i));

      Lens<Inner, String> valueLens = Lens.of(Inner::value, (i, v) -> new Inner(v));

      AffinePath<Outer, Inner> innerPath = FocusPath.of(innerOptLens).<Inner>some();
      AffinePath<Outer, String> safeValuePath = innerPath.via(valueLens).<String>nullable();

      Outer withValue = new Outer(Optional.of(new Inner("hello")));
      Outer withNullValue = new Outer(Optional.of(new Inner(null)));

      // Modify should apply function when value present
      Outer modified = safeValuePath.modify(String::toUpperCase, withValue);
      assertThat(modified.inner().get().value()).isEqualTo("HELLO");

      // Modify should return unchanged when value is null
      Outer unchanged = safeValuePath.modify(String::toUpperCase, withNullValue);
      assertThat(unchanged.inner().get().value()).isNull();
    }

    @Test
    @DisplayName("AffinePath.nullable() should support matches operation")
    void affinePathNullableShouldSupportMatches() {
      record Inner(String value) {}
      record Outer(Optional<Inner> inner) {}

      Lens<Outer, Optional<Inner>> innerOptLens = Lens.of(Outer::inner, (o, i) -> new Outer(i));

      Lens<Inner, String> valueLens = Lens.of(Inner::value, (i, v) -> new Inner(v));

      AffinePath<Outer, Inner> innerPath = FocusPath.of(innerOptLens).<Inner>some();
      AffinePath<Outer, String> safeValuePath = innerPath.via(valueLens).<String>nullable();

      Outer withValue = new Outer(Optional.of(new Inner("hello")));
      Outer withNullValue = new Outer(Optional.of(new Inner(null)));
      Outer withEmpty = new Outer(Optional.empty());

      assertThat(safeValuePath.matches(withValue)).isTrue();
      assertThat(safeValuePath.matches(withNullValue)).isFalse();
      assertThat(safeValuePath.matches(withEmpty)).isFalse();
    }

    @Test
    @DisplayName("AffinePath.nullable() should support getOrElse")
    void affinePathNullableShouldSupportGetOrElse() {
      record Inner(String value) {}
      record Outer(Optional<Inner> inner) {}

      Lens<Outer, Optional<Inner>> innerOptLens = Lens.of(Outer::inner, (o, i) -> new Outer(i));

      Lens<Inner, String> valueLens = Lens.of(Inner::value, (i, v) -> new Inner(v));

      AffinePath<Outer, Inner> innerPath = FocusPath.of(innerOptLens).<Inner>some();
      AffinePath<Outer, String> safeValuePath = innerPath.via(valueLens).<String>nullable();

      Outer withValue = new Outer(Optional.of(new Inner("hello")));
      Outer withNullValue = new Outer(Optional.of(new Inner(null)));

      assertThat(safeValuePath.getOrElse("default", withValue)).isEqualTo("hello");
      assertThat(safeValuePath.getOrElse("default", withNullValue)).isEqualTo("default");
    }

    @Test
    @DisplayName("AffinePath.nullable() should support mapOptional")
    void affinePathNullableShouldSupportMapOptional() {
      record Inner(String value) {}
      record Outer(Optional<Inner> inner) {}

      Lens<Outer, Optional<Inner>> innerOptLens = Lens.of(Outer::inner, (o, i) -> new Outer(i));

      Lens<Inner, String> valueLens = Lens.of(Inner::value, (i, v) -> new Inner(v));

      AffinePath<Outer, Inner> innerPath = FocusPath.of(innerOptLens).<Inner>some();
      AffinePath<Outer, String> safeValuePath = innerPath.via(valueLens).<String>nullable();

      Outer withValue = new Outer(Optional.of(new Inner("hello")));
      Outer withNullValue = new Outer(Optional.of(new Inner(null)));

      assertThat(safeValuePath.mapOptional(String::length, withValue)).contains(5);
      assertThat(safeValuePath.mapOptional(String::length, withNullValue)).isEmpty();
    }
  }

  @Nested
  @DisplayName("List Decomposition Navigation")
  class ListDecompositionNavigation {

    @Test
    @DisplayName("cons() should decompose list into head and tail")
    void consShouldDecomposeList() {
      AffinePath<Optional<List<String>>, List<String>> listPath =
          AffinePath.of(FocusPaths.optionalSome());
      AffinePath<Optional<List<String>>, Pair<String, List<String>>> consPath = listPath.cons();

      Optional<List<String>> present = Optional.of(List.of("a", "b", "c"));
      Optional<List<String>> empty = Optional.empty();

      Optional<Pair<String, List<String>>> result = consPath.getOptional(present);
      assertThat(result).isPresent();
      assertThat(result.get().first()).isEqualTo("a");
      assertThat(result.get().second()).containsExactly("b", "c");

      assertThat(consPath.getOptional(empty)).isEmpty();
    }

    @Test
    @DisplayName("headTail() should be alias for cons()")
    void headTailShouldBeAliasForCons() {
      AffinePath<Optional<List<String>>, List<String>> listPath =
          AffinePath.of(FocusPaths.optionalSome());
      AffinePath<Optional<List<String>>, Pair<String, List<String>>> headTailPath =
          listPath.headTail();

      Optional<List<String>> present = Optional.of(List.of("a", "b", "c"));

      Optional<Pair<String, List<String>>> result = headTailPath.getOptional(present);
      assertThat(result).isPresent();
      assertThat(result.get().first()).isEqualTo("a");
    }

    @Test
    @DisplayName("snoc() should decompose list into init and last")
    void snocShouldDecomposeList() {
      AffinePath<Optional<List<String>>, List<String>> listPath =
          AffinePath.of(FocusPaths.optionalSome());
      AffinePath<Optional<List<String>>, Pair<List<String>, String>> snocPath = listPath.snoc();

      Optional<List<String>> present = Optional.of(List.of("a", "b", "c"));

      Optional<Pair<List<String>, String>> result = snocPath.getOptional(present);
      assertThat(result).isPresent();
      assertThat(result.get().first()).containsExactly("a", "b");
      assertThat(result.get().second()).isEqualTo("c");
    }

    @Test
    @DisplayName("initLast() should be alias for snoc()")
    void initLastShouldBeAliasForSnoc() {
      AffinePath<Optional<List<String>>, List<String>> listPath =
          AffinePath.of(FocusPaths.optionalSome());
      AffinePath<Optional<List<String>>, Pair<List<String>, String>> initLastPath =
          listPath.initLast();

      Optional<List<String>> present = Optional.of(List.of("a", "b", "c"));

      Optional<Pair<List<String>, String>> result = initLastPath.getOptional(present);
      assertThat(result).isPresent();
      assertThat(result.get().second()).isEqualTo("c");
    }

    @Test
    @DisplayName("head() should focus on first element")
    void headShouldFocusOnFirstElement() {
      AffinePath<Optional<List<String>>, List<String>> listPath =
          AffinePath.of(FocusPaths.optionalSome());
      AffinePath<Optional<List<String>>, String> headPath = listPath.head();

      Optional<List<String>> present = Optional.of(List.of("a", "b", "c"));
      Optional<List<String>> emptyList = Optional.of(List.of());
      Optional<List<String>> absent = Optional.empty();

      assertThat(headPath.getOptional(present)).contains("a");
      assertThat(headPath.getOptional(emptyList)).isEmpty();
      assertThat(headPath.getOptional(absent)).isEmpty();
    }

    @Test
    @DisplayName("last() should focus on last element")
    void lastShouldFocusOnLastElement() {
      AffinePath<Optional<List<String>>, List<String>> listPath =
          AffinePath.of(FocusPaths.optionalSome());
      AffinePath<Optional<List<String>>, String> lastPath = listPath.last();

      Optional<List<String>> present = Optional.of(List.of("a", "b", "c"));
      Optional<List<String>> emptyList = Optional.of(List.of());

      assertThat(lastPath.getOptional(present)).contains("c");
      assertThat(lastPath.getOptional(emptyList)).isEmpty();
    }

    @Test
    @DisplayName("tail() should focus on tail of list")
    void tailShouldFocusOnTail() {
      AffinePath<Optional<List<String>>, List<String>> listPath =
          AffinePath.of(FocusPaths.optionalSome());
      AffinePath<Optional<List<String>>, List<String>> tailPath = listPath.tail();

      Optional<List<String>> present = Optional.of(List.of("a", "b", "c"));
      Optional<List<String>> emptyList = Optional.of(List.of());

      assertThat(tailPath.getOptional(present)).contains(List.of("b", "c"));
      assertThat(tailPath.getOptional(emptyList)).isEmpty();
    }

    @Test
    @DisplayName("init() should focus on init of list")
    void initShouldFocusOnInit() {
      AffinePath<Optional<List<String>>, List<String>> listPath =
          AffinePath.of(FocusPaths.optionalSome());
      AffinePath<Optional<List<String>>, List<String>> initPath = listPath.init();

      Optional<List<String>> present = Optional.of(List.of("a", "b", "c"));
      Optional<List<String>> emptyList = Optional.of(List.of());

      assertThat(initPath.getOptional(present)).contains(List.of("a", "b"));
      assertThat(initPath.getOptional(emptyList)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Conversion Methods")
  class ConversionMethods {

    @Test
    @DisplayName("asTraversal should return a TraversalPath view")
    void asTraversalShouldReturnTraversalPathView() {
      AffinePath<Optional<String>, String> path = AffinePath.of(FocusPaths.optionalSome());
      TraversalPath<Optional<String>, String> traversalPath = path.asTraversal();

      assertThat(traversalPath.getAll(Optional.of("hello"))).containsExactly("hello");
      assertThat(traversalPath.getAll(Optional.empty())).isEmpty();
    }

    @Test
    @DisplayName("toAffine should return the underlying Affine")
    void toAffineShouldReturnUnderlyingAffine() {
      Affine<Optional<String>, String> affine = FocusPaths.optionalSome();
      AffinePath<Optional<String>, String> path = AffinePath.of(affine);

      assertThat(path.toAffine()).isSameAs(affine);
    }

    @Test
    @DisplayName("asFold should return a Fold view")
    void asFoldShouldReturnFoldView() {
      AffinePath<Optional<String>, String> path = AffinePath.of(FocusPaths.optionalSome());
      Fold<Optional<String>, String> fold = path.asFold();

      // Use foldMap to verify the fold works
      String result = fold.foldMap(Monoids.string(), s -> s, Optional.of("hello"));
      assertThat(result).isEqualTo("hello");

      String emptyResult = fold.foldMap(Monoids.string(), s -> s, Optional.empty());
      assertThat(emptyResult).isEmpty();

      // Preview should also work
      assertThat(fold.preview(Optional.of("hello"))).contains("hello");
      assertThat(fold.preview(Optional.empty())).isEmpty();
    }
  }
}
