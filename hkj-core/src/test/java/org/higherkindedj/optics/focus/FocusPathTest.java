// Copyright (c) 2025 Magnus Smith
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("FocusPath<S, A> Tests")
class FocusPathTest {

  // Test Data Structures
  record Street(String name) {}

  record Address(Street street, String city) {}

  record Person(String name, Address address) {}

  record NameWrapper(String value) {}

  sealed interface Json permits JsonString, JsonNumber {}

  record JsonString(String value) implements Json {}

  record JsonNumber(int value) implements Json {}

  // Lenses
  private Lens<Street, String> streetNameLens;
  private Lens<Address, Street> addressStreetLens;
  private Lens<Address, String> addressCityLens;
  private Lens<Person, Address> personAddressLens;

  @BeforeEach
  void setUp() {
    streetNameLens = Lens.of(Street::name, (s, n) -> new Street(n));
    addressStreetLens = Lens.of(Address::street, (a, s) -> new Address(s, a.city()));
    addressCityLens = Lens.of(Address::city, (a, c) -> new Address(a.street(), c));
    personAddressLens = Lens.of(Person::address, (p, a) -> new Person(p.name(), a));
  }

  @Nested
  @DisplayName("Core Functionality")
  class CoreFunctionality {

    @Test
    @DisplayName("of should create a FocusPath from a Lens")
    void ofShouldCreateFocusPath() {
      FocusPath<Street, String> path = FocusPath.of(streetNameLens);
      assertThat(path).isNotNull();
      assertThat(path.toLens()).isSameAs(streetNameLens);
    }

    @Test
    @DisplayName("get should extract the focused value")
    void getShouldExtractValue() {
      FocusPath<Street, String> path = FocusPath.of(streetNameLens);
      Street street = new Street("Main St");

      assertThat(path.get(street)).isEqualTo("Main St");
    }

    @Test
    @DisplayName("set should create a new structure with updated value")
    void setShouldCreateNewStructure() {
      FocusPath<Street, String> path = FocusPath.of(streetNameLens);
      Street street = new Street("Main St");

      Street updated = path.set("Broadway", street);

      assertThat(updated.name()).isEqualTo("Broadway");
      assertThat(street.name()).isEqualTo("Main St"); // Original unchanged
    }

    @Test
    @DisplayName("modify should transform the focused value")
    void modifyShouldTransformValue() {
      FocusPath<Street, String> path = FocusPath.of(streetNameLens);
      Street street = new Street("main st");

      Street modified = path.modify(String::toUpperCase, street);

      assertThat(modified.name()).isEqualTo("MAIN ST");
    }

    @Test
    @DisplayName("identity should focus on the source itself")
    void identityShouldFocusOnSource() {
      FocusPath<String, String> path = FocusPath.identity();

      assertThat(path.get("hello")).isEqualTo("hello");
      assertThat(path.set("world", "hello")).isEqualTo("world");
      assertThat(path.modify(String::toUpperCase, "hello")).isEqualTo("HELLO");
    }
  }

  @Nested
  @DisplayName("Composition with Lens")
  class CompositionWithLens {

    @Test
    @DisplayName("via(Lens) should compose two paths")
    void viaShouldComposePaths() {
      FocusPath<Address, Street> addressPath = FocusPath.of(addressStreetLens);
      FocusPath<Address, String> composedPath = addressPath.via(streetNameLens);

      Address address = new Address(new Street("Oak Ave"), "London");

      assertThat(composedPath.get(address)).isEqualTo("Oak Ave");
      Address updated = composedPath.set("Elm St", address);
      assertThat(updated.street().name()).isEqualTo("Elm St");
      assertThat(updated.city()).isEqualTo("London");
    }

    @Test
    @DisplayName("deep composition should work through multiple levels")
    void deepCompositionShouldWork() {
      FocusPath<Person, String> streetNamePath =
          FocusPath.of(personAddressLens).via(addressStreetLens).via(streetNameLens);

      Person person = new Person("Alice", new Address(new Street("High St"), "Oxford"));

      assertThat(streetNamePath.get(person)).isEqualTo("High St");

      Person updated = streetNamePath.set("Low St", person);
      assertThat(updated.address().street().name()).isEqualTo("Low St");
      assertThat(updated.name()).isEqualTo("Alice");
    }
  }

  @Nested
  @DisplayName("Composition with Prism")
  class CompositionWithPrism {

    @Test
    @DisplayName("via(Prism) should produce an AffinePath")
    void viaPrismShouldProduceAffinePath() {
      record Profile(Json data) {}

      Lens<Profile, Json> profileDataLens = Lens.of(Profile::data, (p, d) -> new Profile(d));
      Prism<Json, String> jsonStringPrism =
          Prism.of(
              json -> json instanceof JsonString s ? Optional.of(s.value()) : Optional.empty(),
              JsonString::new);

      FocusPath<Profile, Json> dataPath = FocusPath.of(profileDataLens);
      AffinePath<Profile, String> stringPath = dataPath.via(jsonStringPrism);

      Profile stringProfile = new Profile(new JsonString("hello"));
      Profile numberProfile = new Profile(new JsonNumber(42));

      assertThat(stringPath.getOptional(stringProfile)).contains("hello");
      assertThat(stringPath.getOptional(numberProfile)).isEmpty();

      Profile updated = stringPath.set("world", stringProfile);
      assertThat(((JsonString) updated.data()).value()).isEqualTo("world");
    }
  }

  @Nested
  @DisplayName("Composition with Affine")
  class CompositionWithAffine {

    @Test
    @DisplayName("via(Affine) should produce an AffinePath")
    void viaAffineShouldProduceAffinePath() {
      record Config(Optional<String> setting) {}

      Lens<Config, Optional<String>> settingLens =
          Lens.of(Config::setting, (c, s) -> new Config(s));
      Affine<Optional<String>, String> someAffine = FocusPaths.optionalSome();

      FocusPath<Config, Optional<String>> configPath = FocusPath.of(settingLens);
      AffinePath<Config, String> valuePath = configPath.via(someAffine);

      Config present = new Config(Optional.of("value"));
      Config absent = new Config(Optional.empty());

      assertThat(valuePath.getOptional(present)).contains("value");
      assertThat(valuePath.getOptional(absent)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Composition with Traversal")
  class CompositionWithTraversal {

    @Test
    @DisplayName("via(Traversal) should produce a TraversalPath")
    void viaTraversalShouldProduceTraversalPath() {
      record Team(List<String> members) {}

      Lens<Team, List<String>> membersLens = Lens.of(Team::members, (t, m) -> new Team(m));
      Traversal<List<String>, String> listTraversal = Traversals.forList();

      FocusPath<Team, List<String>> teamPath = FocusPath.of(membersLens);
      TraversalPath<Team, String> membersPath = teamPath.via(listTraversal);

      Team team = new Team(List.of("Alice", "Bob", "Charlie"));

      assertThat(membersPath.getAll(team)).containsExactly("Alice", "Bob", "Charlie");

      Team updated = membersPath.modifyAll(String::toUpperCase, team);
      assertThat(updated.members()).containsExactly("ALICE", "BOB", "CHARLIE");
    }
  }

  @Nested
  @DisplayName("Composition with Iso")
  class CompositionWithIso {

    @Test
    @DisplayName("via(Iso) should produce a FocusPath")
    void viaIsoShouldProduceFocusPath() {
      Iso<String, NameWrapper> wrapperIso = Iso.of(NameWrapper::new, NameWrapper::value);

      FocusPath<Street, String> namePath = FocusPath.of(streetNameLens);
      FocusPath<Street, NameWrapper> wrappedPath = namePath.via(wrapperIso);

      Street street = new Street("Main St");

      assertThat(wrappedPath.get(street).value()).isEqualTo("Main St");

      Street updated = wrappedPath.set(new NameWrapper("Broadway"), street);
      assertThat(updated.name()).isEqualTo("Broadway");
    }
  }

  @Nested
  @DisplayName("Collection Navigation")
  class CollectionNavigation {

    @Test
    @DisplayName("each() should traverse list elements")
    void eachShouldTraverseListElements() {
      record Container(List<String> items) {}

      Lens<Container, List<String>> itemsLens =
          Lens.of(Container::items, (c, i) -> new Container(i));
      FocusPath<Container, List<String>> containerPath = FocusPath.of(itemsLens);

      TraversalPath<Container, String> itemsPath = containerPath.each();

      Container container = new Container(List.of("a", "b", "c"));

      assertThat(itemsPath.getAll(container)).containsExactly("a", "b", "c");
      Container modified = itemsPath.modifyAll(String::toUpperCase, container);
      assertThat(modified.items()).containsExactly("A", "B", "C");
    }

    @Test
    @DisplayName("each(Each) should traverse using provided Each instance")
    void eachWithInstanceShouldTraverseElements() {
      record MapContainer(Map<String, Integer> values) {}

      Lens<MapContainer, Map<String, Integer>> valuesLens =
          Lens.of(MapContainer::values, (c, v) -> new MapContainer(v));
      FocusPath<MapContainer, Map<String, Integer>> containerPath = FocusPath.of(valuesLens);

      Each<Map<String, Integer>, Integer> mapEach = EachInstances.mapValuesEach();
      TraversalPath<MapContainer, Integer> valuesPath = containerPath.each(mapEach);

      MapContainer container = new MapContainer(Map.of("a", 1, "b", 2, "c", 3));

      assertThat(valuesPath.getAll(container)).containsExactlyInAnyOrder(1, 2, 3);
    }

    @Test
    @DisplayName("at(index) should focus on specific element")
    void atShouldFocusOnSpecificElement() {
      record Container(List<String> items) {}

      Lens<Container, List<String>> itemsLens =
          Lens.of(Container::items, (c, i) -> new Container(i));
      FocusPath<Container, List<String>> containerPath = FocusPath.of(itemsLens);

      AffinePath<Container, String> secondItem = containerPath.at(1);

      Container container = new Container(List.of("a", "b", "c"));

      assertThat(secondItem.getOptional(container)).contains("b");
      Container updated = secondItem.set("X", container);
      assertThat(updated.items()).containsExactly("a", "X", "c");

      // Out of bounds
      Container small = new Container(List.of("only"));
      assertThat(secondItem.getOptional(small)).isEmpty();
    }

    @Test
    @DisplayName("some() should unwrap Optional")
    void someShouldUnwrapOptional() {
      record Box(Optional<String> content) {}

      Lens<Box, Optional<String>> contentLens = Lens.of(Box::content, (b, c) -> new Box(c));
      FocusPath<Box, Optional<String>> boxPath = FocusPath.of(contentLens);

      AffinePath<Box, String> valuePath = boxPath.some();

      Box full = new Box(Optional.of("treasure"));
      Box empty = new Box(Optional.empty());

      assertThat(valuePath.getOptional(full)).contains("treasure");
      assertThat(valuePath.getOptional(empty)).isEmpty();

      Box updated = valuePath.set("gold", full);
      assertThat(updated.content()).contains("gold");
    }

    @Test
    @DisplayName("atKey() should focus on map value by key")
    void atKeyShouldFocusOnMapValue() {
      record Settings(Map<String, String> values) {}

      Lens<Settings, Map<String, String>> valuesLens =
          Lens.of(Settings::values, (s, v) -> new Settings(v));
      FocusPath<Settings, Map<String, String>> settingsPath = FocusPath.of(valuesLens);

      AffinePath<Settings, String> themePath = settingsPath.atKey("theme");

      Settings settings = new Settings(Map.of("theme", "dark", "language", "en"));

      assertThat(themePath.getOptional(settings)).contains("dark");

      // Missing key
      assertThat(settingsPath.atKey("missing").getOptional(settings)).isEmpty();
    }
  }

  @Nested
  @DisplayName("List Decomposition Navigation")
  class ListDecompositionNavigation {

    record Container(List<String> items) {}

    Lens<Container, List<String>> itemsLens = Lens.of(Container::items, (c, i) -> new Container(i));

    @Test
    @DisplayName("cons() should decompose list into head and tail")
    void consShouldDecomposeList() {
      FocusPath<Container, List<String>> containerPath = FocusPath.of(itemsLens);
      AffinePath<Container, Pair<String, List<String>>> consPath = containerPath.cons();

      Container container = new Container(List.of("a", "b", "c"));

      Optional<Pair<String, List<String>>> result = consPath.getOptional(container);
      assertThat(result).isPresent();
      assertThat(result.get().first()).isEqualTo("a");
      assertThat(result.get().second()).containsExactly("b", "c");
    }

    @Test
    @DisplayName("cons() should return empty for empty list")
    void consShouldReturnEmptyForEmptyList() {
      FocusPath<Container, List<String>> containerPath = FocusPath.of(itemsLens);
      AffinePath<Container, Pair<String, List<String>>> consPath = containerPath.cons();

      Container empty = new Container(List.of());
      assertThat(consPath.getOptional(empty)).isEmpty();
    }

    @Test
    @DisplayName("headTail() should be alias for cons()")
    void headTailShouldBeAliasForCons() {
      FocusPath<Container, List<String>> containerPath = FocusPath.of(itemsLens);
      AffinePath<Container, Pair<String, List<String>>> headTailPath = containerPath.headTail();

      Container container = new Container(List.of("a", "b", "c"));

      Optional<Pair<String, List<String>>> result = headTailPath.getOptional(container);
      assertThat(result).isPresent();
      assertThat(result.get().first()).isEqualTo("a");
    }

    @Test
    @DisplayName("snoc() should decompose list into init and last")
    void snocShouldDecomposeList() {
      FocusPath<Container, List<String>> containerPath = FocusPath.of(itemsLens);
      AffinePath<Container, Pair<List<String>, String>> snocPath = containerPath.snoc();

      Container container = new Container(List.of("a", "b", "c"));

      Optional<Pair<List<String>, String>> result = snocPath.getOptional(container);
      assertThat(result).isPresent();
      assertThat(result.get().first()).containsExactly("a", "b");
      assertThat(result.get().second()).isEqualTo("c");
    }

    @Test
    @DisplayName("snoc() should return empty for empty list")
    void snocShouldReturnEmptyForEmptyList() {
      FocusPath<Container, List<String>> containerPath = FocusPath.of(itemsLens);
      AffinePath<Container, Pair<List<String>, String>> snocPath = containerPath.snoc();

      Container empty = new Container(List.of());
      assertThat(snocPath.getOptional(empty)).isEmpty();
    }

    @Test
    @DisplayName("initLast() should be alias for snoc()")
    void initLastShouldBeAliasForSnoc() {
      FocusPath<Container, List<String>> containerPath = FocusPath.of(itemsLens);
      AffinePath<Container, Pair<List<String>, String>> initLastPath = containerPath.initLast();

      Container container = new Container(List.of("a", "b", "c"));

      Optional<Pair<List<String>, String>> result = initLastPath.getOptional(container);
      assertThat(result).isPresent();
      assertThat(result.get().second()).isEqualTo("c");
    }

    @Test
    @DisplayName("head() should focus on first element")
    void headShouldFocusOnFirstElement() {
      FocusPath<Container, List<String>> containerPath = FocusPath.of(itemsLens);
      AffinePath<Container, String> headPath = containerPath.head();

      Container container = new Container(List.of("a", "b", "c"));
      Container empty = new Container(List.of());

      assertThat(headPath.getOptional(container)).contains("a");
      assertThat(headPath.getOptional(empty)).isEmpty();
    }

    @Test
    @DisplayName("last() should focus on last element")
    void lastShouldFocusOnLastElement() {
      FocusPath<Container, List<String>> containerPath = FocusPath.of(itemsLens);
      AffinePath<Container, String> lastPath = containerPath.last();

      Container container = new Container(List.of("a", "b", "c"));
      Container empty = new Container(List.of());

      assertThat(lastPath.getOptional(container)).contains("c");
      assertThat(lastPath.getOptional(empty)).isEmpty();
    }

    @Test
    @DisplayName("tail() should focus on tail of list")
    void tailShouldFocusOnTail() {
      FocusPath<Container, List<String>> containerPath = FocusPath.of(itemsLens);
      AffinePath<Container, List<String>> tailPath = containerPath.tail();

      Container container = new Container(List.of("a", "b", "c"));
      Container empty = new Container(List.of());

      assertThat(tailPath.getOptional(container)).contains(List.of("b", "c"));
      assertThat(tailPath.getOptional(empty)).isEmpty();
    }

    @Test
    @DisplayName("init() should focus on init of list")
    void initShouldFocusOnInit() {
      FocusPath<Container, List<String>> containerPath = FocusPath.of(itemsLens);
      AffinePath<Container, List<String>> initPath = containerPath.init();

      Container container = new Container(List.of("a", "b", "c"));
      Container empty = new Container(List.of());

      assertThat(initPath.getOptional(container)).contains(List.of("a", "b"));
      assertThat(initPath.getOptional(empty)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Conversion Methods")
  class ConversionMethods {

    @Test
    @DisplayName("asAffine should return an AffinePath view")
    void asAffineShouldReturnAffinePathView() {
      FocusPath<Street, String> path = FocusPath.of(streetNameLens);
      AffinePath<Street, String> affinePath = path.asAffine();

      Street street = new Street("Main St");

      assertThat(affinePath.getOptional(street)).contains("Main St");
      assertThat(affinePath.matches(street)).isTrue();

      // Test setter functionality
      Street updated = affinePath.set("Oak Ave", street);
      assertThat(updated.name()).isEqualTo("Oak Ave");
    }

    @Test
    @DisplayName("asTraversal should return a TraversalPath view")
    void asTraversalShouldReturnTraversalPathView() {
      FocusPath<Street, String> path = FocusPath.of(streetNameLens);
      TraversalPath<Street, String> traversalPath = path.asTraversal();

      Street street = new Street("Main St");

      assertThat(traversalPath.getAll(street)).containsExactly("Main St");
      assertThat(traversalPath.count(street)).isEqualTo(1);
    }

    @Test
    @DisplayName("toLens should return the underlying Lens")
    void toLensShouldReturnUnderlyingLens() {
      FocusPath<Street, String> path = FocusPath.of(streetNameLens);

      assertThat(path.toLens()).isSameAs(streetNameLens);
    }

    @Test
    @DisplayName("asFold should return a Fold view")
    void asFoldShouldReturnFoldView() {
      FocusPath<Street, String> path = FocusPath.of(streetNameLens);
      Fold<Street, String> fold = path.asFold();

      Street street = new Street("Main St");

      // Use foldMap to verify the fold works
      String result = fold.foldMap(Monoids.string(), s -> s, street);
      assertThat(result).isEqualTo("Main St");

      // Preview should also work
      assertThat(fold.preview(street)).contains("Main St");
    }
  }
}
